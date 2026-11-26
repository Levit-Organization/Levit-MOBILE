<?php

namespace App\Services;

/**
 * Histórico do que foi feito na plataforma e por quem (base dos relatórios).
 *
 * Registrar não custa nada à requisição: as entradas ficam numa fila em
 * memória e são gravadas de uma vez depois que a resposta sai (com PHP-FPM a
 * conexão já fechou). A descrição dos itens (com o que mudou, coluna por
 * coluna) também é montada nessa hora, porque precisa de consultas.
 *
 * Em linha de comando (worker da fila, rotinas) grava na hora, já que o
 * processo pode durar horas. Uma falha ao gravar nunca derruba a ação: vai
 * para o log.
 */
final class AtividadeService
{
    /** @var array<int, array<string, mixed>> */
    private static array $fila = [];

    private static bool $agendado = false;

    private const MAX_MUDANCAS = 3;

    /**
     * Ação com descrição pronta. Quem fez sai do usuário autenticado; sem
     * usuário (candidatura pública, automação), aparece como `$autorSistema`.
     */
    public static function registrar(
        string $acao,
        string $descricao,
        ?string $moduloId = null,
        ?string $moduloNome = null,
        ?string $empresaId = null,
        string $autorSistema = 'Sistema'
    ): void {
        $usuario = service('authenticatedUser');
        $empresaId ??= $usuario->empresaId;
        if ($empresaId === null && $moduloId === null) {
            return; // sem empresa nem tabela para descobrir a empresa
        }

        self::enfileirar([
            'empresa_id'   => $empresaId,
            'usuario_id'   => $usuario->autenticado() ? $usuario->id : null,
            'usuario_nome' => $usuario->autenticado() ? null : $autorSistema,
            'acao'         => $acao,
            'modulo_id'    => $moduloId,
            'modulo_nome'  => $moduloNome,
            'descricao'    => mb_substr($descricao, 0, 2000),
        ]);
    }

    /**
     * Item criado, alterado ou excluído (vem dos eventos registro_*). A
     * descrição é montada na gravação, com os nomes das colunas.
     *
     * @param 'item_criado'|'item_alterado'|'item_excluido' $acao
     */
    public static function registrarItem(string $acao, array $payload): void
    {
        $usuario = service('authenticatedUser');

        self::enfileirar([
            'empresa_id'   => null, // vem da tabela, na gravação
            'usuario_id'   => $usuario->autenticado() ? $usuario->id : null,
            'usuario_nome' => $usuario->autenticado() ? null : 'Automação',
            'acao'         => $acao,
            'modulo_id'    => $payload['modulo_id'],
            'modulo_nome'  => null,
            'descricao'    => null,
            '_dados'       => $payload['dados_registro'] ?? [],
            '_anteriores'  => $payload['dados_anteriores'] ?? null,
        ]);
    }

    /** Candidatura recebida ou candidato movido de etapa (eventos candidato_*). */
    public static function registrarCandidato(string $acao, array $payload): void
    {
        $d    = $payload['dados_registro'] ?? [];
        $nome = trim((string) ($d['nome'] ?? '')) ?: 'candidato';

        $descricao = $acao === 'candidatura_recebida'
            ? "Nova candidatura de {$nome}" . (($d['etapa'] ?? '') !== '' ? " (etapa {$d['etapa']})" : '')
            : "Moveu {$nome} de {$d['etapa_anterior']} para {$d['etapa']}";

        $usuario = service('authenticatedUser');
        self::enfileirar([
            'empresa_id'   => null,
            'usuario_id'   => $usuario->autenticado() ? $usuario->id : null,
            'usuario_nome' => $usuario->autenticado() ? null : ($acao === 'candidatura_recebida' ? 'Formulário de candidatura' : 'Automação'),
            'acao'         => $acao,
            'modulo_id'    => $payload['modulo_id'],
            'modulo_nome'  => null,
            'descricao'    => $descricao,
        ]);
    }

    private static function enfileirar(array $entrada): void
    {
        if (ENVIRONMENT === 'testing') {
            return;
        }

        // Com microssegundos: várias ações no mesmo segundo mantêm a ordem em que aconteceram.
        $entrada['criado_em'] = (new \DateTimeImmutable('now', new \DateTimeZone('UTC')))->format('Y-m-d H:i:s.u');
        self::$fila[]         = $entrada;

        if (is_cli()) {
            self::gravar();

            return;
        }

        if (! self::$agendado) {
            self::$agendado = true;
            register_shutdown_function(static function () {
                if (function_exists('fastcgi_finish_request')) {
                    fastcgi_finish_request();
                }
                self::gravar();
            });
        }
    }

    /** Grava tudo o que está na fila. Público para as rotinas forçarem a gravação. */
    public static function gravar(): void
    {
        if (self::$fila === []) {
            return;
        }
        $fila       = self::$fila;
        self::$fila = [];

        try {
            $db = db_connect();

            // Tabelas envolvidas: empresa, nome e colunas, numa consulta só.
            $moduloIds = array_values(array_unique(array_filter(array_column($fila, 'modulo_id'))));
            $modulos   = [];
            $campos    = [];
            if ($moduloIds !== []) {
                foreach ($db->table('modulo')->select('id, empresa_id, nome')->whereIn('id', $moduloIds)->get()->getResultArray() as $m) {
                    $modulos[$m['id']] = $m;
                }
                if (array_filter($fila, static fn ($e) => $e['descricao'] === null)) {
                    foreach ($db->table('campo_modulo')->select('id, modulo_id, nome, tipo')->whereIn('modulo_id', $moduloIds)->orderBy('ordem', 'ASC')->get()->getResultArray() as $c) {
                        $campos[$c['modulo_id']][$c['id']] = $c;
                    }
                }
            }

            // Nomes das pessoas: autores e responsáveis citados nas mudanças.
            $pessoaIds = array_filter(array_column($fila, 'usuario_id'));
            foreach ($fila as $e) {
                foreach ([$e['_dados'] ?? [], $e['_anteriores'] ?? []] as $dados) {
                    foreach ($campos[$e['modulo_id']] ?? [] as $c) {
                        if ($c['tipo'] === 'responsavel' && ! empty($dados[$c['id']])) {
                            $pessoaIds[] = $dados[$c['id']];
                        }
                    }
                }
            }
            $pessoaIds = array_values(array_unique(array_filter($pessoaIds, static fn ($id) => is_string($id) && preg_match('/^[0-9a-f-]{36}$/i', $id))));
            $pessoas   = $pessoaIds === [] ? [] : array_column(
                $db->table('usuario')->select('id, nome')->whereIn('id', $pessoaIds)->get()->getResultArray(),
                'nome',
                'id'
            );

            $linhas = [];
            foreach ($fila as $e) {
                $modulo = $e['modulo_id'] !== null ? ($modulos[$e['modulo_id']] ?? null) : null;
                $empresaId = $e['empresa_id'] ?? $modulo['empresa_id'] ?? null;
                if ($empresaId === null) {
                    continue;
                }

                $linhas[] = [
                    'empresa_id'   => $empresaId,
                    'usuario_id'   => $e['usuario_id'],
                    'usuario_nome' => $e['usuario_nome'] ?? ($pessoas[$e['usuario_id']] ?? null),
                    'acao'         => $e['acao'],
                    'modulo_id'    => $e['modulo_id'],
                    'modulo_nome'  => $e['modulo_nome'] ?? $modulo['nome'] ?? null,
                    'descricao'    => $e['descricao'] ?? self::descreverItem($e, $campos[$e['modulo_id']] ?? [], $pessoas),
                    'criado_em'    => $e['criado_em'],
                ];
            }

            if ($linhas !== []) {
                $db->table('atividade')->insertBatch($linhas);
            }
        } catch (\Throwable $ex) {
            log_message('error', 'Falha ao gravar o histórico de atividades: {erro}', ['erro' => $ex->getMessage()]);
        }
    }

    /**
     * "Criou o item “Padaria”", "Excluiu o item “Padaria”" ou
     * "Alterou “Padaria”: Status: A fazer → Entregue; Responsável: — → Ana".
     *
     * @param array<string, array{id: string, nome: string, tipo: string}> $campos
     * @param array<string, string>                                        $pessoas
     */
    public static function descreverItem(array $e, array $campos, array $pessoas): string
    {
        $dados  = $e['_dados'] ?? [];
        $titulo = null;
        foreach ($campos as $c) {
            if ($c['tipo'] === 'texto' && ! empty($dados[$c['id']])) {
                $titulo = (string) $dados[$c['id']];
                break;
            }
        }
        $nome = $titulo !== null ? "“{$titulo}”" : 'um item';

        if ($e['acao'] === 'item_criado') {
            return "Criou {$nome}";
        }
        if ($e['acao'] === 'item_excluido') {
            return "Excluiu {$nome}";
        }

        $anteriores = $e['_anteriores'] ?? [];
        $mudancas   = [];
        foreach ($campos as $c) {
            $antes  = $anteriores[$c['id']] ?? null;
            $depois = $dados[$c['id']] ?? null;
            if ($antes == $depois) {
                continue;
            }
            $mudancas[] = "{$c['nome']}: " . self::valor($antes, $c['tipo'], $pessoas) . ' → ' . self::valor($depois, $c['tipo'], $pessoas);
        }

        if ($mudancas === []) {
            return "Salvou {$nome} sem mudanças";
        }
        $extra = count($mudancas) > self::MAX_MUDANCAS ? ' e mais ' . (count($mudancas) - self::MAX_MUDANCAS) : '';

        return "Alterou {$nome}: " . implode('; ', array_slice($mudancas, 0, self::MAX_MUDANCAS)) . $extra;
    }

    private static function valor(mixed $valor, string $tipo, array $pessoas): string
    {
        if ($valor === null || $valor === '') {
            return '—';
        }
        if ($tipo === 'responsavel') {
            return $pessoas[$valor] ?? 'pessoa removida';
        }
        if (in_array($tipo, ['data', 'prazo'], true) && preg_match('/^(\d{4})-(\d{2})-(\d{2})$/', (string) $valor, $m)) {
            return "{$m[3]}/{$m[2]}/{$m[1]}";
        }
        $texto = is_array($valor) ? implode(', ', $valor) : (string) $valor;

        return mb_strlen($texto) > 60 ? mb_substr($texto, 0, 57) . '…' : $texto;
    }
}
