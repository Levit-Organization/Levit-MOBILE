<?php

namespace App\Services;

use App\Libraries\Relogio;
use App\Services\Email\ModeloResumoDiario;

/**
 * O que roda uma vez por dia (pelo cron, de manhã):
 *
 * 1. Avisos de prazo: cada responsável recebe uma notificação na plataforma e
 *    um resumo por e-mail com o que atrasou, o que vence hoje e o que vence
 *    nos próximos dias. Quem tem acesso total também vê os itens sem
 *    responsável que já atrasaram ou vencem hoje.
 * 2. Relatórios agendados para o dia.
 *
 * Nunca repete: os avisos ficam marcados em `rotina_execucao` (um por dia) e
 * cada relatório guarda o último envio. Rodar o comando de novo é seguro.
 */
class RotinaDiariaService
{
    /** Dias à frente que entram no resumo ("vence amanhã", "vence em 3 dias"). */
    public const DIAS_A_FRENTE = 3;

    /** @return array{avisos: ?array, relatorios: array} */
    public function executar(?string $hoje = null, bool $forcar = false): array
    {
        $hoje = $hoje ?? Relogio::hoje();

        return [
            'avisos'     => $this->jaExecutou('avisos_prazo', $hoje) && ! $forcar ? null : $this->avisarPrazos($hoje),
            'relatorios' => $this->enviarRelatoriosDevidos($hoje),
        ];
    }

    private function jaExecutou(string $rotina, string $referencia): bool
    {
        return (bool) db_connect()->table('rotina_execucao')->where('rotina', $rotina)->where('referencia', $referencia)->countAllResults();
    }

    private function marcar(string $rotina, string $referencia): void
    {
        db_connect()->query(
            'INSERT INTO rotina_execucao (rotina, referencia) VALUES (?, ?) ON CONFLICT DO NOTHING',
            [$rotina, $referencia]
        );
    }

    // ------------------------------------------------------------------
    // Avisos de prazo
    // ------------------------------------------------------------------

    /** @return array{empresas: int, pessoas: int, emails: int} */
    public function avisarPrazos(string $hoje): array
    {
        $db       = db_connect();
        $empresas = array_column($db->query(
            "SELECT DISTINCT m.empresa_id FROM campo_modulo c JOIN modulo m ON m.id = c.modulo_id
              WHERE c.tipo = 'prazo' AND m.tipo = 'dados'"
        )->getResultArray(), 'empresa_id');

        $total = ['empresas' => 0, 'pessoas' => 0, 'emails' => 0];
        foreach ($empresas as $empresaId) {
            try {
                [$pessoas, $emails] = $this->avisarEmpresa($empresaId, $hoje);
                $total['empresas']++;
                $total['pessoas'] += $pessoas;
                $total['emails']  += $emails;
            } catch (\Throwable $e) {
                // Uma empresa com problema não impede os avisos das outras.
                log_message('error', 'Avisos de prazo da empresa {id} falharam: {erro}', ['id' => $empresaId, 'erro' => $e->getMessage()]);
            }
        }

        $this->marcar('avisos_prazo', $hoje);

        return $total;
    }

    /** @return array{0: int, 1: int} pessoas avisadas e e-mails enviados */
    private function avisarEmpresa(string $empresaId, string $hoje): array
    {
        $prazos  = new PrazoService();
        $modulos = $prazos->modulosDeDados($empresaId);
        $itens   = $prazos->itensEmAberto($modulos, array_column($modulos, 'id'), $hoje, self::DIAS_A_FRENTE);
        if ($itens === []) {
            return [0, 0];
        }

        $pessoas = $this->pessoasComAcesso($empresaId);
        $enviarEmail = filter_var(env('RESUMO_DIARIO_EMAIL', true), FILTER_VALIDATE_BOOLEAN);
        $link        = rtrim((string) env('FRONTEND_URL', 'http://localhost:5173'), '/') . '/dashboard';
        $notificacoes = new NotificacaoService();
        $avisadas = 0;
        $emails   = 0;

        foreach ($pessoas as $pessoa) {
            $grupos = ['atrasados' => [], 'hoje' => [], 'proximos' => []];
            $semResponsavel = [];

            foreach ($itens as $item) {
                $podeVer = $pessoa['acesso_total'] || isset($pessoa['modulos'][$item['modulo_id']]);
                if (! $podeVer) {
                    continue;
                }
                $item['rotulo'] = self::rotulo($item['prazo'], $hoje);
                if ($item['responsavel_id'] === $pessoa['id']) {
                    $grupos[PrazoService::grupo($item['prazo'], $hoje)][] = $item;
                } elseif ($item['responsavel_id'] === null && $pessoa['acesso_total'] && $item['prazo'] <= $hoje) {
                    $semResponsavel[] = $item;
                }
            }

            $quantos = count($grupos['atrasados']) + count($grupos['hoje']) + count($grupos['proximos']) + count($semResponsavel);
            if ($quantos === 0) {
                continue;
            }
            $avisadas++;

            $notificacoes->notificarSemFalhar([$pessoa['id']], $empresaId, [
                'tipo'      => 'prazos',
                'titulo'    => self::tituloDaNotificacao($grupos, count($semResponsavel)),
                'mensagem'  => self::mensagemDaNotificacao($grupos),
                'link'      => '/dashboard',
                // Um aviso por dia: se a rotina rodar de novo, atualiza o mesmo.
                'agrupador' => 'prazos-' . $hoje,
            ]);

            if ($enviarEmail && $pessoa['email']) {
                try {
                    service('email')->enviar($pessoa['email'], ModeloResumoDiario::assunto($grupos), ModeloResumoDiario::html([
                        'nome'            => $pessoa['nome'],
                        'link'            => $link,
                        'grupos'          => $grupos,
                        'sem_responsavel' => $semResponsavel,
                    ]));
                    $emails++;
                } catch (\Throwable $e) {
                    log_message('error', 'Resumo diário para {email} falhou: {erro}', ['email' => $pessoa['email'], 'erro' => $e->getMessage()]);
                }
            }
        }

        return [$avisadas, $emails];
    }

    /**
     * Pessoas da empresa com as tabelas que cada uma enxerga.
     *
     * @return array<int, array{id: string, nome: string, email: string, acesso_total: bool, modulos: array<string, true>}>
     */
    private function pessoasComAcesso(string $empresaId): array
    {
        $linhas = db_connect()->query(
            'SELECT u.id, u.nome, u.email, (c.acesso_total OR e.administrador_principal_id = u.id) AS acesso_total, cmp.modulo_id
               FROM usuario_equipe u
               JOIN cargo c ON c.id = u.cargo_id
               JOIN empresa e ON e.id = u.empresa_id
          LEFT JOIN cargo_modulo_permissao cmp ON cmp.cargo_id = u.cargo_id
              WHERE u.empresa_id = ?',
            [$empresaId]
        )->getResultArray();

        $pessoas = [];
        foreach ($linhas as $l) {
            $pessoas[$l['id']] ??= [
                'id'           => $l['id'],
                'nome'         => $l['nome'],
                'email'        => $l['email'],
                'acesso_total' => in_array($l['acesso_total'], [true, 1, '1', 't', 'true'], true),
                'modulos'      => [],
            ];
            if ($l['modulo_id']) {
                $pessoas[$l['id']]['modulos'][$l['modulo_id']] = true;
            }
        }

        return array_values($pessoas);
    }

    public static function rotulo(string $prazo, string $hoje): string
    {
        $dias = (int) (new \DateTimeImmutable($hoje))->diff(new \DateTimeImmutable($prazo))->format('%r%a');

        return match (true) {
            $dias < -1  => 'Atrasado há ' . (-$dias) . ' dias',
            $dias === -1 => 'Atrasado desde ontem',
            $dias === 0  => 'Vence hoje',
            $dias === 1  => 'Vence amanhã',
            default      => "Vence em {$dias} dias",
        };
    }

    public static function tituloDaNotificacao(array $grupos, int $semResponsavel = 0): string
    {
        $partes = [];
        if ($n = count($grupos['atrasados'])) {
            $partes[] = $n === 1 ? '1 atrasado' : "{$n} atrasados";
        }
        if ($n = count($grupos['hoje'])) {
            $partes[] = $n === 1 ? '1 vence hoje' : "{$n} vencem hoje";
        }
        if ($n = count($grupos['proximos'])) {
            $partes[] = $n === 1 ? '1 vence nos próximos dias' : "{$n} vencem nos próximos dias";
        }
        if ($partes === [] && $semResponsavel > 0) {
            return $semResponsavel === 1 ? '1 item atrasado está sem responsável' : "{$semResponsavel} itens atrasados estão sem responsável";
        }

        return 'Seus prazos: ' . implode(', ', $partes);
    }

    private static function mensagemDaNotificacao(array $grupos): ?string
    {
        $primeiro = $grupos['atrasados'][0] ?? $grupos['hoje'][0] ?? $grupos['proximos'][0] ?? null;

        return $primeiro ? ($primeiro['titulo'] ?: 'Item sem título') . " ({$primeiro['modulo_nome']}): {$primeiro['rotulo']}" : null;
    }

    // ------------------------------------------------------------------
    // Relatórios
    // ------------------------------------------------------------------

    /** @return array<int, array{relatorio: string, status: string}> */
    public function enviarRelatoriosDevidos(string $hoje): array
    {
        $servico    = new RelatorioService();
        $resultados = [];
        $ativos     = db_connect()->table('relatorio_agendado')->where('ativo', true)->get()->getResultArray();

        foreach ($ativos as $relatorio) {
            if (! RelatorioService::estaDevido($relatorio['frequencia'], (int) $relatorio['dia'], $hoje, $relatorio['ultimo_envio_em'], $relatorio['criado_em'])) {
                continue;
            }
            $envio        = $servico->enviar($relatorio, $hoje);
            $resultados[] = ['relatorio' => $relatorio['nome'], 'status' => $envio['status']];
        }

        return $resultados;
    }
}
