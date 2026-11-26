<?php

namespace App\Services\Relatorios;

use App\Libraries\Relogio;

/**
 * HTML do PDF do relatório agendado: números do período, seções de
 * indicadores, itens atrasados, ranking e tudo o que foi feito, dia a dia.
 */
final class HtmlDoRelatorio
{
    /** Teto de linhas no PDF; acima disso o relatório avisa que foi resumido. */
    public const MAX_ATIVIDADES = 3000;

    /** HTML do PDF do relatório agendado (público para testar sem gerar o arquivo). */
    public static function montar(array $d): string
    {
        $e = static fn ($t) => htmlspecialchars((string) $t, ENT_QUOTES | ENT_HTML5, 'UTF-8');

        $porPessoa = [];
        $porTabela = [];
        $porDia    = [];
        foreach ($d['atividades'] as $a) {
            $quem = $a['usuario_nome'] ?: 'Sistema';
            $porPessoa[$quem] = ($porPessoa[$quem] ?? 0) + 1;
            if ($a['modulo_nome']) {
                $porTabela[$a['modulo_nome']] = ($porTabela[$a['modulo_nome']] ?? 0) + 1;
            }
            $local = Relogio::local($a['criado_em']);
            $porDia[$local->format('Y-m-d')][] = $a + ['hora' => $local->format('H:i')];
        }
        arsort($porPessoa);
        arsort($porTabela);

        $total   = count($d['atividades']);
        $pessoas = count(array_filter(array_keys($porPessoa), static fn ($p) => ! in_array($p, ['Sistema', 'Automação', 'Formulário de candidatura'], true)));

        $ranking = static function (array $contagem, string $vazio, ?callable $formato = null) use ($e): string {
            if ($contagem === []) {
                return '<p class="muted">' . $e($vazio) . '</p>';
            }
            $max    = max($contagem) ?: 1;
            $linhas = '';
            foreach (array_slice($contagem, 0, 8, true) as $nome => $n) {
                $largura = max(4, (int) round($n / $max * 100));
                $linhas .= '<tr><td class="rank-nome">' . $e($nome) . '</td><td class="rank-barra"><div style="width:' . $largura . '%"></div></td><td class="rank-n">' . $e($formato ? $formato($n) : $n) . '</td></tr>';
            }

            return '<table class="rank">' . $linhas . '</table>';
        };

        $diasSemana = ['domingo', 'segunda', 'terça', 'quarta', 'quinta', 'sexta', 'sábado'];
        $secoesDias = '';
        foreach ($porDia as $data => $itens) {
            $dt = new \DateTimeImmutable($data);
            $linhas = '';
            foreach ($itens as $a) {
                $linhas .= '<tr><td class="hora">' . $e($a['hora']) . '</td><td class="quem">' . $e($a['usuario_nome'] ?: 'Sistema') . '</td><td>'
                    . $e($a['descricao']) . ($a['modulo_nome'] ? '<div class="onde">' . $e($a['modulo_nome']) . '</div>' : '') . '</td></tr>';
            }
            $secoesDias .= '<h3>' . $e(ucfirst($diasSemana[(int) $dt->format('w')]) . ', ' . $dt->format('d/m/Y')) . ' <span class="muted">· ' . count($itens) . '</span></h3>'
                . '<table class="lista"><thead><tr><th class="hora">Hora</th><th class="quem">Quem</th><th>O que foi feito</th></tr></thead><tbody>' . $linhas . '</tbody></table>';
        }
        if ($secoesDias === '') {
            $secoesDias = '<p class="muted">Nenhuma atividade registrada neste período.</p>';
        }

        $atrasados = '';
        foreach (array_slice($d['atrasados'], 0, 20) as $i) {
            $dias = (new \DateTimeImmutable($i['prazo']))->diff(new \DateTimeImmutable($d['hoje']))->days;
            $atrasados .= '<tr><td>' . $e($i['titulo'] ?: 'Item sem título') . '<div class="onde">' . $e($i['modulo_nome']) . '</div></td><td>'
                . $e($i['responsavel_nome'] ?: 'Sem responsável') . '</td><td class="atraso">' . ($dias === 1 ? '1 dia' : "{$dias} dias") . '</td></tr>';
        }
        $blocoPrazos = $d['atrasados'] === []
            ? '<p class="muted">Nenhum item atrasado no momento em que o relatório foi gerado.</p>'
            : '<table class="lista"><thead><tr><th>Item</th><th class="quem">Responsável</th><th class="hora">Atraso</th></tr></thead><tbody>' . $atrasados . '</tbody></table>'
                . (count($d['atrasados']) > 20 ? '<p class="muted">e mais ' . (count($d['atrasados']) - 20) . ' itens atrasados.</p>' : '');

        $aviso = $d['cortado'] ? '<p class="aviso">O período teve mais de ' . self::MAX_ATIVIDADES . ' atividades; o relatório mostra as primeiras.</p>' : '';

        $corpo = '';
        foreach ($d['secoes'] ?? Agenda::SECOES_LEGADO as $secao) {
            $corpo .= match ($secao) {
                'resumo', 'recrutamento', 'financeiro', 'prazos' => isset($d['indicadores']) ? HtmlDosIndicadores::secao($secao, $d['indicadores'], $ranking) : '',
                'atrasados'  => '<h2>Itens atrasados agora</h2>' . $blocoPrazos,
                'ranking'    => '<table class="colunas"><tr>
  <td><h2>Quem mais fez</h2>' . $ranking($porPessoa, 'Ninguém registrou atividades.') . '</td>
  <td><h2>Onde mais mexeram</h2>' . $ranking($porTabela, 'Nenhuma tabela movimentada.') . '</td>
</tr></table>',
                'atividades' => '<h2>Tudo o que foi feito</h2>' . $aviso . $secoesDias,
                default      => '',
            };
        }

        return '<!doctype html><html lang="pt-BR"><head><meta charset="utf-8"><style>
' . Pdf::estilo() . '</style></head><body>
<div class="rodape">' . $e($d['empresa']) . ' · ' . $e($d['titulo']) . ' · gerado em ' . $e($d['gerado_em']) . '</div>
<div class="marca">LEVIT</div>
<h1>' . $e($d['titulo']) . '</h1>
<p class="sub">' . $e($d['empresa']) . ' · ' . $e($d['periodo']) . '</p>
<table class="numeros"><tr>
  <td><span class="n">' . $total . '</span><span class="r">atividades no período</span></td>
  <td><span class="n">' . $pessoas . '</span><span class="r">' . ($pessoas === 1 ? 'pessoa ativa' : 'pessoas ativas') . '</span></td>
  <td><span class="n">' . count($porTabela) . '</span><span class="r">' . (count($porTabela) === 1 ? 'tabela movimentada' : 'tabelas movimentadas') . '</span></td>
  <td><span class="n">' . count($d['atrasados']) . '</span><span class="r">' . (count($d['atrasados']) === 1 ? 'item atrasado hoje' : 'itens atrasados hoje') . '</span></td>
</tr></table>
' . $corpo . '
</body></html>';
    }
}
