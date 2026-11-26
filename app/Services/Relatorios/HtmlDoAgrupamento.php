<?php

namespace App\Services\Relatorios;

/**
 * HTML do PDF do relatório montado: os grupos de IndicadoresService::agrupar
 * com os critérios (tabela, coluna, cálculo, período e filtros) por extenso.
 */
final class HtmlDoAgrupamento
{
    private const MESES = ['janeiro', 'fevereiro', 'março', 'abril', 'maio', 'junho', 'julho', 'agosto', 'setembro', 'outubro', 'novembro', 'dezembro'];

    /** HTML do PDF do relatório montado (público para testar sem gerar o arquivo). */
    public static function montar(array $d): string
    {
        $e      = static fn ($t) => htmlspecialchars((string) $t, ENT_QUOTES | ENT_HTML5, 'UTF-8');
        $r      = $d['resultado'];
        $grupos = $r['grupos'];
        $porMes = in_array($r['tipo_grupo'] ?? null, ['data', 'prazo'], true);

        $rotulo = static fn (string $g) => $porMes && preg_match('/^(\\d{4})-(\\d{2})$/', $g, $m)
            ? ucfirst(self::MESES[(int) $m[2] - 1]) . " de {$m[1]}"
            : $g;
        $formatar = static fn ($v) => $v === null ? '—' : Pdf::numero($v, fmod((float) $v, 1.0) === 0.0 ? 0 : 2);

        $calculo = match ($r['operacao']) {
            'somar' => "Soma de {$r['coluna_valor']}",
            'media' => "Média de {$r['coluna_valor']}",
            default => 'Quantidade de itens',
        };
        // Percentual só faz sentido para contagens e somas de valores positivos.
        $somaGrupos = array_sum(array_map(static fn ($g) => (float) ($g['valor'] ?? 0), $grupos));
        $comPercent = $r['operacao'] !== 'media' && $somaGrupos > 0;
        $maximo     = max(array_map(static fn ($g) => abs((float) ($g['valor'] ?? 0)), $grupos) ?: [0]) ?: 1;

        $linhas = '';
        foreach ($grupos as $g) {
            $largura = max(2, (int) round(abs((float) ($g['valor'] ?? 0)) / $maximo * 100));
            $linhas .= '<tr><td>' . $e($rotulo((string) $g['rotulo'])) . '</td>'
                . '<td class="barra"><div style="width:' . $largura . '%"></div></td>'
                . '<td class="num">' . $e($formatar($g['valor'])) . '</td>'
                . ($r['operacao'] !== 'contar' ? '<td class="num">' . $e(Pdf::numero($g['itens'])) . '</td>' : '')
                . ($comPercent ? '<td class="num">' . $e(Pdf::numero((float) ($g['valor'] ?? 0) / $somaGrupos * 100, 1)) . '%</td>' : '')
                . '</tr>';
        }
        $cabecalho = '<tr><th>' . $e($r['agrupado_por']) . '</th><th></th><th class="num">' . $e($calculo) . '</th>'
            . ($r['operacao'] !== 'contar' ? '<th class="num">Itens</th>' : '')
            . ($comPercent ? '<th class="num">% do total</th>' : '') . '</tr>';
        $rodape = $r['operacao'] === 'media' ? '' : '<tfoot><tr><td>Total dos grupos</td><td></td><td class="num">' . $e($formatar($somaGrupos)) . '</td>'
            . ($r['operacao'] !== 'contar' ? '<td class="num"></td>' : '') . ($comPercent ? '<td class="num">100%</td>' : '') . '</tr></tfoot>';

        $tabela = $grupos === []
            ? '<p class="muted">Nenhum item atende aos critérios deste relatório.</p>'
            : '<table class="grupos"><thead>' . $cabecalho . '</thead><tbody>' . $linhas . '</tbody>' . $rodape . '</table>'
                . ($r['cortados'] > 0 ? '<p class="aviso">Mostrando os ' . count($grupos) . ' maiores grupos; ' . $r['cortados'] . ' ficaram de fora.</p>' : '');

        $maior = $grupos === [] ? null : array_reduce($grupos, static fn ($a, $g) => $a === null || ($g['valor'] ?? 0) > ($a['valor'] ?? 0) ? $g : $a);

        $filtros = $d['filtros'] === []
            ? 'Nenhum: entram todos os itens.'
            : (count($d['filtros']) > 1 ? 'Itens que atendem a ' . ($d['logica'] === 'ou' ? 'qualquer um' : 'todos') . ' destes:' : '')
                . '<ul class="filtros"><li>' . implode('</li><li>', array_map($e, $d['filtros'])) . '</li></ul>';

        // Ligação a vários itens põe o mesmo item em mais de um grupo.
        $nota = $somaGrupos > 0 && $r['operacao'] === 'contar' && (int) $somaGrupos > (int) $r['total_itens']
            ? '<p class="muted">Itens ligados a mais de um grupo aparecem em cada um deles; o total de itens conta cada um uma vez.</p>'
            : '';

        return '<!doctype html><html lang="pt-BR"><head><meta charset="utf-8"><style>
' . Pdf::estilo() . '</style></head><body>
<div class="rodape">' . $e($d['empresa']) . ' · ' . $e($d['titulo']) . ' · gerado em ' . $e($d['gerado_em']) . '</div>
<div class="marca">LEVIT</div>
<h1>' . $e($d['titulo']) . '</h1>
<p class="sub">' . $e($d['empresa']) . ' · ' . $e($d['periodo']) . '</p>
<table class="numeros"><tr>
  <td><span class="n">' . $e(Pdf::numero($r['total_itens'])) . '</span><span class="r">' . ($r['total_itens'] === 1 ? 'item no relatório' : 'itens no relatório') . '</span></td>
  <td><span class="n">' . count($grupos) . '</span><span class="r">' . (count($grupos) === 1 ? 'grupo' : 'grupos') . '</span></td>
  <td><span class="n">' . $e($maior ? $formatar($maior['valor']) : '—') . '</span><span class="r">' . ($maior ? 'maior grupo: ' . $e(mb_strimwidth($rotulo((string) $maior['rotulo']), 0, 34, '…')) : 'sem grupos') . '</span></td>
</tr></table>
<h2>Critérios</h2>
<table class="criterios">
  <tr><td class="rotulo">Tabela</td><td>' . $e($r['tabela']) . '</td></tr>
  <tr><td class="rotulo">Agrupado por</td><td>' . $e($r['agrupado_por']) . ($porMes ? ' (por mês)' : '') . '</td></tr>
  <tr><td class="rotulo">Cálculo</td><td>' . $e($calculo) . '</td></tr>
  <tr><td class="rotulo">Período</td><td>' . $e($d['periodo']) . '</td></tr>
  <tr><td class="rotulo">Filtros</td><td>' . $filtros . '</td></tr>
</table>
<h2>' . $e($calculo) . ' por ' . $e($r['agrupado_por']) . '</h2>
' . $tabela . $nota . '
</body></html>';
    }
}
