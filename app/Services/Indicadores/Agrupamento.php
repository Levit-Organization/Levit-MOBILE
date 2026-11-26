<?php

namespace App\Services\Indicadores;

/**
 * Regras do relatório montado que não dependem do banco: que coluna agrupa,
 * como os grupos são rotulados, ordenados e cortados.
 */
final class Agrupamento
{
    /** Colunas pelas quais dá para agrupar no relatório montado. */
    public const TIPOS_AGRUPAVEIS = ['selecao', 'responsavel', 'texto', 'data', 'prazo', 'recorrencia', 'vinculo'];

    public const OPERACOES = ['contar', 'somar', 'media'];

    private const MAX_GRUPOS = 50;

    /**
     * Coluna escolhida para agrupar: desta tabela, ou "ligacao>coluna" para uma
     * coluna da tabela ligada (uma ligação só; a coluna de lá não pode ser outra ligação).
     *
     * @return array{0: array, 1: ?array} a coluna (com o nome já composto) e a ligação usada
     */
    public static function colunaDeAgrupamento(string $chave, string $moduloId, array $campos): array
    {
        $erro = new \DomainException('Escolha uma coluna para agrupar (lista, responsável, texto, data ou ligação).');

        if (str_contains($chave, '>')) {
            [$ligacaoId, $colunaId] = explode('>', $chave, 2);
            $ligacao = $campos[$ligacaoId] ?? null;
            $coluna  = $campos[$colunaId] ?? null;
            if (! $ligacao || ! $coluna || $ligacao['tipo'] !== 'vinculo' || $ligacao['modulo_id'] !== $moduloId
                || $coluna['modulo_id'] !== $ligacao['modulo_vinculado_id'] || $coluna['tipo'] === 'vinculo'
                || ! in_array($coluna['tipo'], self::TIPOS_AGRUPAVEIS, true)) {
                throw $erro;
            }

            return [['nome' => "{$ligacao['nome']} › {$coluna['nome']}"] + $coluna, $ligacao];
        }

        $coluna = $campos[$chave] ?? null;
        if (! $coluna || $coluna['modulo_id'] !== $moduloId || ! in_array($coluna['tipo'], self::TIPOS_AGRUPAVEIS, true)) {
            throw $erro;
        }

        return [$coluna, null];
    }

    /**
     * Formata e ordena os grupos (cálculo puro, testável sem banco).
     *
     * @param array<string, ?string> $nomes      id => nome, para responsável (pessoas) e ligação (itens)
     * @param int|null               $totalItens itens distintos, quando um item pode cair em vários grupos
     */
    public static function montarGrupos(array $linhas, string $operacao, string $tipoGrupo, array $nomes = [], ?int $totalItens = null): array
    {
        $porMes = in_array($tipoGrupo, ['data', 'prazo'], true);
        $grupos = array_map(static function ($l) use ($operacao, $tipoGrupo, $nomes) {
            $chave = $l['grupo'];
            $rotulo = match (true) {
                $chave === null || $chave === '' => match ($tipoGrupo) {
                    'responsavel' => 'Sem responsável',
                    'vinculo'     => 'Sem item ligado',
                    default       => 'Sem valor',
                },
                $tipoGrupo === 'responsavel' => $nomes[$chave] ?? 'Pessoa removida',
                $tipoGrupo === 'vinculo'     => array_key_exists($chave, $nomes)
                    ? (($nomes[$chave] ?? '') === '' ? 'Item sem nome' : $nomes[$chave])
                    : 'Item excluído',
                default => (string) $chave,
            };
            $valor = match ($operacao) {
                'somar' => round((float) ($l['soma'] ?? 0), 2),
                'media' => $l['media'] === null ? null : round((float) $l['media'], 2),
                default => (int) $l['total'],
            };

            return ['chave' => $chave, 'rotulo' => $rotulo, 'valor' => $valor, 'itens' => (int) $l['total']];
        }, $linhas);

        // Datas em ordem cronológica (sem valor no fim); o resto do maior para o menor.
        usort($grupos, $porMes
            ? static fn ($a, $b) => [$a['chave'] === null, $a['chave']] <=> [$b['chave'] === null, $b['chave']]
            : static fn ($a, $b) => [$a['chave'] === null, -($a['valor'] ?? 0), $a['rotulo']] <=> [$b['chave'] === null, -($b['valor'] ?? 0), $b['rotulo']]);

        $cortados = max(0, count($grupos) - self::MAX_GRUPOS);
        $grupos   = array_slice($grupos, 0, self::MAX_GRUPOS);

        return [
            'grupos'      => array_map(static fn ($g) => array_diff_key($g, ['chave' => 1]), $grupos),
            'total_itens' => $totalItens ?? array_sum(array_column($linhas, 'total')),
            'cortados'    => $cortados,
        ];
    }

    /** AAAA-MM-DD de uma data que existe; qualquer outra coisa vira null. */
    public static function dataValida(mixed $d): ?string
    {
        return is_string($d) && preg_match('/^\d{4}-\d{2}-\d{2}$/', $d) && (\DateTimeImmutable::createFromFormat('Y-m-d', $d) ?: null)?->format('Y-m-d') === $d
            ? $d
            : null;
    }
}
