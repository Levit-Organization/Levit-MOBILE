<?php

namespace App\Services;

use App\Exceptions\NaoEncontradoException;
use App\Libraries\Relogio;
use App\Services\Email\ModeloRelatorio;
use App\Services\Relatorios\Agenda;
use App\Services\Relatorios\GeradorDePdf;
use App\Services\Relatorios\HtmlDoAgrupamento;
use App\Services\Relatorios\HtmlDoRelatorio;
use App\Services\Relatorios\HtmlDosIndicadores;
use App\Services\Relatorios\Pdf;

/**
 * Relatórios agendados: num dia da semana ou do mês, gera um PDF com tudo o
 * que foi feito na plataforma no período (e por quem) e manda por e-mail.
 *
 * O PDF é montado a partir do histórico de atividades, então qualquer período
 * pode ser gerado de novo depois (o histórico de envios guarda só o período).
 *
 * Este service cuida do cadastro e do envio. A agenda (datas e seções) fica em
 * Relatorios\Agenda; a geração do PDF, em Relatorios\GeradorDePdf e nas
 * classes de HTML ao lado dela. Os métodos estáticos daqui continuam
 * existindo e só repassam para lá.
 */
class RelatorioService
{
    // Mesmos valores de Relatorios\Agenda (ver lá o que cada um significa).
    public const FREQUENCIAS   = Agenda::FREQUENCIAS;
    public const SECOES        = Agenda::SECOES;
    public const SECOES_PADRAO = Agenda::SECOES_PADRAO;

    // ------------------------------------------------------------------
    // Agenda
    // ------------------------------------------------------------------

    public function listar(string $empresaId): array
    {
        $db         = db_connect();
        $relatorios = $db->table('relatorio_agendado')->where('empresa_id', $empresaId)->orderBy('criado_em', 'ASC')->get()->getResultArray();
        if ($relatorios === []) {
            return [];
        }

        $ultimos = [];
        foreach ($db->query(
            'SELECT DISTINCT ON (relatorio_id) relatorio_id, status, enviado_em, total_atividades, erro
               FROM relatorio_envio WHERE relatorio_id IN ? ORDER BY relatorio_id, enviado_em DESC',
            [array_column($relatorios, 'id')]
        )->getResultArray() as $l) {
            $ultimos[$l['relatorio_id']] = $l;
        }

        return array_map(fn ($r) => $this->formatar($r, $ultimos[$r['id']] ?? null), $relatorios);
    }

    public function criar(string $empresaId, string $usuarioId, array $dados): array
    {
        $campos = $this->validar($empresaId, $dados);
        helper('uuid');
        $id = generate_uuid_v7();
        db_connect()->table('relatorio_agendado')->insert($campos + [
            'id'         => $id,
            'empresa_id' => $empresaId,
            'criado_por' => $usuarioId,
        ]);
        $linha = $this->buscar($empresaId, $id);

        AtividadeService::registrar('relatorio_criado', "Agendou o relatório “{$campos['nome']}”");

        return $this->formatar($linha, null);
    }

    public function atualizar(string $empresaId, string $relatorioId, array $dados): array
    {
        $this->buscar($empresaId, $relatorioId);
        $campos = $this->validar($empresaId, $dados);
        db_connect()->table('relatorio_agendado')->where('id', $relatorioId)->update($campos + ['atualizado_em' => gmdate('Y-m-d H:i:s')]);

        AtividadeService::registrar('relatorio_alterado', "Alterou o relatório “{$campos['nome']}”");

        return $this->formatar($this->buscar($empresaId, $relatorioId), null);
    }

    public function excluir(string $empresaId, string $relatorioId): void
    {
        $relatorio = $this->buscar($empresaId, $relatorioId);
        db_connect()->table('relatorio_agendado')->where('id', $relatorioId)->delete();
        AtividadeService::registrar('relatorio_excluido', "Excluiu o relatório “{$relatorio['nome']}”");
    }

    public function envios(string $empresaId, string $relatorioId): array
    {
        $this->buscar($empresaId, $relatorioId);

        return db_connect()->table('relatorio_envio')
            ->select('id, periodo_inicio, periodo_fim, total_atividades, status, erro, enviado_em')
            ->where('relatorio_id', $relatorioId)
            ->orderBy('enviado_em', 'DESC')
            ->limit(24)
            ->get()->getResultArray();
    }

    public function buscar(string $empresaId, string $relatorioId): array
    {
        $r = db_connect()->table('relatorio_agendado')->where('id', $relatorioId)->where('empresa_id', $empresaId)->get()->getRowArray();
        if (! $r) {
            throw new NaoEncontradoException('Relatório não encontrado.');
        }

        return $r;
    }

    private function validar(string $empresaId, array $dados): array
    {
        $nome = trim((string) ($dados['nome'] ?? ''));
        if ($nome === '' || mb_strlen($nome) > 100) {
            throw new \DomainException('Dê um nome ao relatório (até 100 caracteres).');
        }

        $frequencia = $dados['frequencia'] ?? '';
        if (! in_array($frequencia, Agenda::FREQUENCIAS, true)) {
            throw new \DomainException('Escolha se o relatório é diário, semanal ou mensal.');
        }

        $dia = $frequencia === 'diario' ? 1 : (int) ($dados['dia'] ?? 0);
        if ($frequencia === 'semanal' && ($dia < 1 || $dia > 7)) {
            throw new \DomainException('Escolha o dia da semana do envio.');
        }
        if ($frequencia === 'mensal' && ($dia < 1 || $dia > 28)) {
            throw new \DomainException('Escolha um dia do mês entre 1 e 28 (assim todo mês tem esse dia).');
        }

        $destinatarios = array_values(array_unique(array_filter((array) ($dados['destinatarios'] ?? []), 'is_string')));
        if ($destinatarios === []) {
            throw new \DomainException('Escolha quem recebe o relatório.');
        }
        $validos = db_connect()->table('usuario_equipe')->select('id')->where('empresa_id', $empresaId)->whereIn('id', array_filter(
            $destinatarios,
            static fn ($id) => preg_match('/^[0-9a-f-]{36}$/i', $id)
        ) ?: ['00000000-0000-0000-0000-000000000000'])->get()->getResultArray();
        if (count($validos) !== count($destinatarios)) {
            throw new \DomainException('Algum destinatário não é da equipe.');
        }

        if (array_key_exists('secoes', $dados) && array_filter((array) $dados['secoes'], static fn ($s) => in_array($s, Agenda::SECOES, true)) === []) {
            throw new \DomainException('Escolha ao menos uma parte para o relatório.');
        }
        $secoes = Agenda::secoesValidas($dados['secoes'] ?? Agenda::SECOES_PADRAO);

        return [
            'nome'          => $nome,
            'frequencia'    => $frequencia,
            'dia'           => $dia,
            'destinatarios' => json_encode($destinatarios),
            'secoes'        => json_encode($secoes),
            'ativo'         => ! array_key_exists('ativo', $dados) || (bool) $dados['ativo'],
        ];
    }

    private function formatar(array $r, ?array $ultimoEnvio): array
    {
        $r['destinatarios'] = is_string($r['destinatarios']) ? (json_decode($r['destinatarios'], true) ?: []) : ($r['destinatarios'] ?? []);
        $r['ativo']         = in_array($r['ativo'], [true, 1, '1', 't', 'true'], true);
        $r['dia']           = (int) $r['dia'];
        $r['secoes']        = Agenda::secoesDoRelatorio($r);
        $r['descricao_agenda'] = Agenda::descreverAgenda($r['frequencia'], $r['dia']);
        $r['proximo_envio']    = $r['ativo'] ? Agenda::proximaData($r['frequencia'], $r['dia'], Relogio::hoje(), $r['ultimo_envio_em'] ?? null) : null;
        $r['ultimo_envio']     = $ultimoEnvio;

        return $r;
    }

    // ------------------------------------------------------------------
    // Envio
    // ------------------------------------------------------------------

    /** Gera e envia um relatório agendado. Registra o envio (com sucesso ou erro). */
    public function enviar(array $relatorio, string $hoje): array
    {
        [$inicio, $fim] = Agenda::periodo($relatorio['frequencia'], $hoje);
        $db             = db_connect();
        $registro       = [
            'relatorio_id'   => $relatorio['id'],
            'periodo_inicio' => $inicio->setTimezone(new \DateTimeZone('UTC'))->format('Y-m-d H:i:s'),
            'periodo_fim'    => $fim->setTimezone(new \DateTimeZone('UTC'))->format('Y-m-d H:i:s'),
        ];

        try {
            $pdf     = $this->gerarPdf($relatorio['empresa_id'], $inicio, $fim, $relatorio['nome'], Agenda::secoesDoRelatorio($relatorio));
            $ids     = is_string($relatorio['destinatarios']) ? (json_decode($relatorio['destinatarios'], true) ?: []) : $relatorio['destinatarios'];
            $pessoas = $ids === [] ? [] : $db->table('usuario_equipe')->select('email')->where('empresa_id', $relatorio['empresa_id'])->whereIn('id', $ids)->get()->getResultArray();
            if ($pessoas === []) {
                throw new \DomainException('Nenhum destinatário continua na equipe.');
            }

            $periodo = Agenda::textoDoPeriodo($inicio, $fim);
            $corpo   = ModeloRelatorio::html([
                'nome_relatorio' => $relatorio['nome'],
                'empresa'        => $pdf['empresa'],
                'periodo'        => $periodo,
                'total'          => $pdf['total'],
                'pessoas'        => $pdf['pessoas'],
                'link'           => rtrim((string) env('FRONTEND_URL', 'http://localhost:5173'), '/') . '/relatorios',
            ]);
            $anexo = ['nome' => Agenda::nomeDoArquivo($relatorio['nome'], $fim), 'conteudo' => $pdf['conteudo']];

            foreach ($pessoas as $p) {
                service('email')->enviar($p['email'], ModeloRelatorio::assunto($relatorio['nome'], $periodo), $corpo, [$anexo]);
            }

            $registro += ['status' => 'enviado', 'total_atividades' => $pdf['total']];
        } catch (\Throwable $e) {
            log_message('error', 'Relatório {id} não foi enviado: {erro}', ['id' => $relatorio['id'], 'erro' => $e->getMessage()]);
            $registro += ['status' => 'erro', 'erro' => mb_substr($e->getMessage(), 0, 500), 'total_atividades' => 0];
        }

        $db->table('relatorio_envio')->insert($registro);
        // Mesmo com erro marca o envio: não tenta de novo a cada execução da rotina no mesmo dia.
        $db->table('relatorio_agendado')->where('id', $relatorio['id'])->update(['ultimo_envio_em' => gmdate('Y-m-d H:i:s')]);

        return $registro;
    }

    // ------------------------------------------------------------------
    // PDF
    // ------------------------------------------------------------------

    /**
     * @return array{conteudo: string, total: int, pessoas: int, empresa: string}
     */
    public function gerarPdf(string $empresaId, \DateTimeImmutable $inicio, \DateTimeImmutable $fim, string $titulo, ?array $secoes = null): array
    {
        return (new GeradorDePdf())->relatorio($empresaId, $inicio, $fim, $titulo, $secoes);
    }

    /**
     * PDF do relatório montado (ver GeradorDePdf::agrupamento).
     *
     * @return array{conteudo: string, arquivo: string}
     */
    public function pdfDoAgrupamento(string $empresaId, array $params, string $titulo = ''): array
    {
        return (new GeradorDePdf())->agrupamento($empresaId, $params, $titulo);
    }

    // ------------------------------------------------------------------
    // Atalhos estáticos (a implementação fica em App\Services\Relatorios)
    // ------------------------------------------------------------------

    public static function secoesValidas(mixed $secoes): array
    {
        return Agenda::secoesValidas($secoes);
    }

    public static function secoesDoRelatorio(array $r): array
    {
        return Agenda::secoesDoRelatorio($r);
    }

    public static function descreverAgenda(string $frequencia, int $dia): string
    {
        return Agenda::descreverAgenda($frequencia, $dia);
    }

    public static function ultimaDataAgendada(string $frequencia, int $dia, string $hoje): string
    {
        return Agenda::ultimaDataAgendada($frequencia, $dia, $hoje);
    }

    public static function estaDevido(string $frequencia, int $dia, string $hoje, ?string $ultimoEnvioUtc, string $criadoEmUtc): bool
    {
        return Agenda::estaDevido($frequencia, $dia, $hoje, $ultimoEnvioUtc, $criadoEmUtc);
    }

    public static function proximaData(string $frequencia, int $dia, string $hoje, ?string $ultimoEnvioUtc): string
    {
        return Agenda::proximaData($frequencia, $dia, $hoje, $ultimoEnvioUtc);
    }

    public static function periodo(string $frequencia, string $hoje): array
    {
        return Agenda::periodo($frequencia, $hoje);
    }

    public static function textoDoPeriodo(\DateTimeImmutable $inicio, \DateTimeImmutable $fim): string
    {
        return Agenda::textoDoPeriodo($inicio, $fim);
    }

    public static function nomeDoArquivo(string $nome, \DateTimeImmutable $fim): string
    {
        return Agenda::nomeDoArquivo($nome, $fim);
    }

    public static function renderizar(string $html): string
    {
        return Pdf::renderizar($html);
    }

    public static function htmlDoRelatorio(array $d): string
    {
        return HtmlDoRelatorio::montar($d);
    }

    public static function htmlDoAgrupamento(array $d): string
    {
        return HtmlDoAgrupamento::montar($d);
    }

    public static function htmlDosIndicadores(string $secao, array $ind, callable $ranking): string
    {
        return HtmlDosIndicadores::secao($secao, $ind, $ranking);
    }

    public static function htmlDaVariacao(array $comparacao, bool $subirEBom = true): string
    {
        return HtmlDosIndicadores::variacao($comparacao, $subirEBom);
    }

    public static function numero(float|int $n, int $casas = 0): string
    {
        return Pdf::numero($n, $casas);
    }

    public static function moeda(float|int $n): string
    {
        return Pdf::moeda($n);
    }
}
