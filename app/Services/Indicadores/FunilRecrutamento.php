<?php

namespace App\Services\Indicadores;

use App\Services\PrazoService;

/** Funil de recrutamento de uma vaga (cálculo puro, testável sem banco). */
final class FunilRecrutamento
{
    /** Fases de quem saiu do processo: ficam fora do funil e contam como saída. */
    public const FASES_DE_SAIDA = [
        'reprovado', 'reprovada', 'recusado', 'recusada', 'desistiu', 'desistente', 'desistencia',
        'eliminado', 'eliminada', 'descartado', 'descartada', 'perdido', 'perdida', 'cancelado',
        'cancelada', 'arquivado', 'arquivada', 'nao aprovado', 'nao aprovada', 'nao selecionado',
        'nao selecionada',
    ];

    /** Candidato parado na mesma fase há esse tanto de dias pede atenção. */
    public const DIAS_PARADO = 14;

    public static function faseDeSaida(string $nome): bool
    {
        return in_array(PrazoService::normalizar($nome), self::FASES_DE_SAIDA, true);
    }

    /**
     * Funil de uma vaga (cálculo puro, testável sem banco). Timestamps em UTC.
     *
     * - `fases[].atual`: quantos estão na fase agora.
     * - `fases[].chegaram`: dos candidatos que se inscreveram no período, quantos
     *   chegaram até essa fase (ou passaram dela). É o funil de conversão.
     * - `fases[].dias_medios`: tempo médio que ficaram na fase, contando quem
     *   saiu dela no período.
     * - Fases de saída (Reprovado, Desistiu...) ficam fora do funil.
     *
     * @param array<int, array{id: string, nome: string, ordem: int|string}>                                 $fases     ordenadas
     * @param array<int, array{id: string, fase_atual_id: string, criado_em: string}>                         $candidatos
     * @param array<int, array{candidato_id: string, fase_anterior_id: ?string, fase_nova_id: ?string, criado_em: string}> $historico ordenado por data
     * @param array{atual: array{0: string, 1: string}, anterior: array{0: string, 1: string}}               $periodos
     */
    public static function funil(array $fases, array $candidatos, array $historico, array $periodos, string $agora): array
    {
        $noFunil = array_values(array_filter($fases, static fn ($f) => ! self::faseDeSaida($f['nome'])));
        $posicao = [];
        foreach ($noFunil as $i => $f) {
            $posicao[$f['id']] = $i;
        }
        $ultima = $noFunil === [] ? null : $noFunil[count($noFunil) - 1]['id'];
        $saida  = array_column(array_filter($fases, static fn ($f) => self::faseDeSaida($f['nome'])), 'id');

        $porCandidato = [];
        foreach ($historico as $h) {
            $porCandidato[$h['candidato_id']][] = $h;
        }

        $noPeriodo = static fn (string $t, string $p) => $t >= $periodos[$p][0] && $t < $periodos[$p][1];

        $atual       = array_fill_keys(array_column($fases, 'id'), 0);
        $chegaram    = array_fill_keys(array_keys($posicao), 0);
        $permanencia = [];
        $inscritos   = ['atual' => 0, 'anterior' => 0];
        $concluidos  = ['atual' => 0, 'anterior' => 0];
        $sairam      = ['atual' => 0, 'anterior' => 0];
        $parados     = 0;
        $limiteParado = (new \DateTimeImmutable($agora))->modify('-' . self::DIAS_PARADO . ' days')->format('Y-m-d H:i:s');

        foreach ($candidatos as $c) {
            if (array_key_exists($c['fase_atual_id'], $atual)) {
                $atual[$c['fase_atual_id']]++;
            }
            $movimentos = $porCandidato[$c['id']] ?? [];

            // Linha do tempo: em que fase entrou e quando.
            $faseInicial = $movimentos[0]['fase_anterior_id'] ?? $c['fase_atual_id'];
            $entradas    = [[$faseInicial, $c['criado_em']]];
            foreach ($movimentos as $m) {
                $entradas[] = [$m['fase_nova_id'], $m['criado_em']];
            }

            // Quanto tempo ficou em cada fase (só o que terminou no período).
            for ($i = 0; $i < count($entradas) - 1; $i++) {
                [$fase, $desde] = $entradas[$i];
                $ate            = $entradas[$i + 1][1];
                if ($fase !== null && isset($posicao[$fase]) && $noPeriodo($ate, 'atual')) {
                    $permanencia[$fase][] = (strtotime($ate) - strtotime($desde)) / 86400;
                }
            }

            foreach (['atual', 'anterior'] as $p) {
                if ($noPeriodo($c['criado_em'], $p)) {
                    $inscritos[$p]++;
                }
                foreach ($movimentos as $m) {
                    if (! $noPeriodo($m['criado_em'], $p)) {
                        continue;
                    }
                    if ($m['fase_nova_id'] === $ultima) {
                        $concluidos[$p]++;
                    } elseif (in_array($m['fase_nova_id'], $saida, true)) {
                        $sairam[$p]++;
                    }
                }
            }

            // Funil: até onde chegou quem se inscreveu no período.
            if ($noPeriodo($c['criado_em'], 'atual')) {
                $maisLonge = -1;
                foreach ($entradas as [$fase]) {
                    if ($fase !== null && isset($posicao[$fase])) {
                        $maisLonge = max($maisLonge, $posicao[$fase]);
                    }
                }
                foreach ($posicao as $id => $pos) {
                    if ($pos <= $maisLonge) {
                        $chegaram[$id]++;
                    }
                }
            }

            $ultimoMovimento = $entradas[count($entradas) - 1][1];
            $emAndamento     = isset($posicao[$c['fase_atual_id']]) && $c['fase_atual_id'] !== $ultima;
            if ($emAndamento && $ultimoMovimento < $limiteParado) {
                $parados++;
            }
        }

        return [
            'fases' => array_map(static fn ($f) => [
                'id'          => $f['id'],
                'nome'        => $f['nome'],
                'saida'       => ! isset($posicao[$f['id']]),
                'atual'       => $atual[$f['id']] ?? 0,
                'chegaram'    => $chegaram[$f['id']] ?? null,
                'dias_medios' => isset($permanencia[$f['id']])
                    ? round(array_sum($permanencia[$f['id']]) / count($permanencia[$f['id']]), 1)
                    : null,
            ], $fases),
            'inscritos'  => Comparacao::comparar($inscritos['atual'], $inscritos['anterior']),
            'concluidos' => Comparacao::comparar($concluidos['atual'], $concluidos['anterior']),
            'sairam'     => Comparacao::comparar($sairam['atual'], $sairam['anterior']),
            'parados'    => $parados,
            'total'      => count($candidatos),
        ];
    }
}
