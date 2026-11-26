<?php

namespace App\Services\Relatorios;

use App\Services\IndicadoresService;

/** Seções de indicadores (dados das tabelas) no PDF do relatório agendado. */
final class HtmlDosIndicadores
{
    /**
     * "+12%" em verde ou vermelho, conforme subir seja bom ou ruim. Sem base de
     * comparação (anterior zero), mostra "novo".
     */
    public static function variacao(array $comparacao, bool $subirEBom = true): string
    {
        $v = $comparacao['variacao'];
        if ($v === null) {
            return '<span class="muted">novo</span>';
        }
        if ((float) $v === 0.0) {
            return '<span class="muted">igual</span>';
        }
        $classe = ($v > 0) === $subirEBom ? 'bom' : 'ruim';

        return '<span class="' . $classe . '">' . ($v > 0 ? '+' : '') . Pdf::numero($v, abs($v) < 10 && floor($v) != $v ? 1 : 0) . '%</span>';
    }

    /** Uma seção de indicadores (dados das tabelas) no PDF. */
    public static function secao(string $secao, array $ind, callable $ranking): string
    {
        $e     = static fn ($t) => htmlspecialchars((string) $t, ENT_QUOTES | ENT_HTML5, 'UTF-8');
        $linha = static fn (string $rotulo, array $c, bool $bom = true, ?callable $f = null) => '<tr><td>' . $e($rotulo) . '</td><td class="num">'
            . $e($f ? $f($c['atual']) : Pdf::numero($c['atual'])) . '</td><td class="num muted">' . $e($f ? $f($c['anterior']) : Pdf::numero($c['anterior']))
            . '</td><td class="num">' . self::variacao($c, $bom) . '</td></tr>';
        $cabecalho = '<table class="lista"><thead><tr><th></th><th class="num">Neste período</th><th class="num">Anterior</th><th class="num">Variação</th></tr></thead><tbody>';
        $anterior  = (new \DateTimeImmutable($ind['periodo_anterior']['inicio']))->format('d/m/Y') . ' a ' . (new \DateTimeImmutable($ind['periodo_anterior']['fim']))->format('d/m/Y');

        switch ($secao) {
            case 'resumo':
                $r = $ind['resumo'];

                return '<h2>Comparado ao período anterior</h2><p class="muted">Período anterior: ' . $e($anterior) . '</p>' . $cabecalho
                    . $linha('Atividades na plataforma', $r['atividades'])
                    . $linha('Pessoas ativas', $r['pessoas_ativas'])
                    . $linha('Itens criados nas tabelas', $r['itens_criados'])
                    . $linha('Candidatos inscritos', $r['candidatos'])
                    . '</tbody></table>';

            case 'recrutamento':
                if ($ind['recrutamento'] === []) {
                    return '';
                }
                $html = '<h2>Recrutamento</h2>';
                foreach ($ind['recrutamento'] as $vaga) {
                    $max    = max(1, ...array_map(static fn ($f) => (int) ($f['chegaram'] ?? 0), $vaga['fases'] ?: [['chegaram' => 0]]));
                    $linhas = '';
                    foreach ($vaga['fases'] as $f) {
                        $barra = $f['saida'] ? '<span class="muted">saída</span>'
                            : '<div style="width:' . max(2, (int) round(($f['chegaram'] ?? 0) / $max * 100)) . '%"></div>';
                        $linhas .= '<tr><td>' . $e($f['nome']) . '</td><td class="num">' . $f['atual'] . '</td><td class="rank-barra" style="width:30%">' . $barra
                            . '</td><td class="num">' . ($f['saida'] ? '—' : (int) $f['chegaram']) . '</td><td class="num">'
                            . ($f['dias_medios'] === null ? '—' : $e(Pdf::numero($f['dias_medios'], 1) . ' dias')) . '</td></tr>';
                    }
                    $html .= '<h3>' . $e($vaga['nome']) . ' <span class="muted">· ' . $vaga['total'] . ' candidatos no total</span></h3>'
                        . $cabecalho
                        . $linha('Inscritos', $vaga['inscritos'])
                        . $linha('Chegaram à última etapa', $vaga['concluidos'])
                        . $linha('Saíram do processo', $vaga['sairam'], false)
                        . '</tbody></table>'
                        . ($vaga['parados'] > 0 ? '<p class="aviso">' . $vaga['parados'] . ' candidato(s) parado(s) na mesma etapa há mais de ' . IndicadoresService::DIAS_PARADO . ' dias.</p>' : '')
                        . '<table class="lista"><thead><tr><th>Etapa</th><th class="num">Agora</th><th colspan="2">Inscritos no período que chegaram até aqui</th><th class="num">Tempo médio</th></tr></thead><tbody>'
                        . $linhas . '</tbody></table>';
                }

                return $html;

            case 'financeiro':
                if ($ind['financeiro'] === []) {
                    return '';
                }
                $html = '<h2>Financeiro</h2>';
                foreach ($ind['financeiro'] as $fin) {
                    $html .= '<h3>' . $e($fin['nome']) . '</h3>' . $cabecalho
                        . $linha('Entradas', $fin['entradas'], true, [Pdf::class, 'moeda'])
                        . $linha('Saídas', $fin['saidas'], false, [Pdf::class, 'moeda'])
                        . $linha('Saldo', $fin['saldo'], true, [Pdf::class, 'moeda'])
                        . '</tbody></table>'
                        . '<h3>Saídas por categoria</h3>'
                        . $ranking(array_column($fin['saidas_por_categoria'], 'total', 'categoria'), 'Nenhuma saída no período.', [Pdf::class, 'moeda']);
                }

                return $html;

            case 'prazos':
                $p    = $ind['prazos'];
                $taxa = $p['taxa_conclusao'];

                return '<h2>Prazos</h2>' . $cabecalho
                    . $linha('Prazos que venceram no período', $p['vencendo'])
                    . $linha('Desses, já concluídos', $p['concluidos'])
                    . '</tbody></table>'
                    . '<p>Taxa de conclusão: <b>' . ($taxa['atual'] === null ? '—' : $taxa['atual'] . '%') . '</b>'
                    . ($taxa['anterior'] === null ? '' : ' <span class="muted">(período anterior: ' . $taxa['anterior'] . '%)</span>')
                    . ' · ' . $p['atrasados_agora'] . ' item(ns) atrasado(s) agora.</p>'
                    . '<h3>Atrasados por responsável</h3>'
                    . $ranking(array_column($p['atrasados_por_responsavel'], 'total', 'responsavel'), 'Nenhum item atrasado.');
        }

        return '';
    }
}
