<?php

namespace App\Services\Email;

/**
 * Resumo diário dos prazos de uma pessoa: o que atrasou, o que vence hoje e o
 * que vence nos próximos dias. Mesma identidade dos outros e-mails; todo texto
 * vindo das tabelas é escapado.
 */
final class ModeloResumoDiario
{
    /** @param array{atrasados: array, hoje: array, proximos: array} $grupos */
    public static function assunto(array $grupos): string
    {
        $a = count($grupos['atrasados']);
        $h = count($grupos['hoje']);
        if ($a > 0) {
            return $a === 1 ? 'Você tem 1 item atrasado no Levit' : "Você tem {$a} itens atrasados no Levit";
        }
        if ($h > 0) {
            return $h === 1 ? '1 item vence hoje no Levit' : "{$h} itens vencem hoje no Levit";
        }

        return 'Seus prazos dos próximos dias no Levit';
    }

    /**
     * @param array{nome: string, link: string, grupos: array{atrasados: array, hoje: array, proximos: array}, sem_responsavel?: array} $dados
     *        cada item: titulo, modulo_nome, prazo (AAAA-MM-DD), rotulo (ex.: "Atrasado há 3 dias")
     */
    public static function html(array $dados): string
    {
        $e     = static fn (?string $t) => htmlspecialchars((string) $t, ENT_QUOTES | ENT_HTML5, 'UTF-8');
        $nome  = $e(explode(' ', trim($dados['nome']))[0] ?: 'olá');
        $link  = $e($dados['link']);
        $fonte = "'Archivo', 'Segoe UI', Arial, Helvetica, sans-serif";

        $secoes = '';
        $blocos = [
            ['Atrasados', '#B42318', $dados['grupos']['atrasados']],
            ['Vencem hoje', '#9A6700', $dados['grupos']['hoje']],
            ['Próximos dias', '#534BAF', $dados['grupos']['proximos']],
            ['Sem responsável (atrasados ou vencendo)', '#6B6880', $dados['sem_responsavel'] ?? []],
        ];
        foreach ($blocos as [$titulo, $cor, $itens]) {
            if ($itens === []) {
                continue;
            }
            $linhas = '';
            foreach (array_slice($itens, 0, 15) as $item) {
                $linhas .= '<tr><td style="padding:10px 0;border-top:1px solid #EEEDF5;font-family:' . $fonte . '">'
                    . '<p style="margin:0;font-size:15px;font-weight:bold;color:#15132A">' . $e($item['titulo'] ?: 'Item sem título') . '</p>'
                    . '<p style="margin:2px 0 0;font-size:13px;color:#6B6880">' . $e($item['modulo_nome']) . ' · <span style="color:' . $cor . ';font-weight:bold">' . $e($item['rotulo']) . '</span></p>'
                    . '</td></tr>';
            }
            $resto = count($itens) > 15 ? '<p style="margin:6px 0 0;font-family:' . $fonte . ';font-size:13px;color:#6B6880">e mais ' . (count($itens) - 15) . '</p>' : '';
            $secoes .= '<p style="margin:22px 0 4px;font-family:' . $fonte . ';font-size:13px;font-weight:bold;color:' . $cor . ';text-transform:uppercase;letter-spacing:.04em">'
                . $e($titulo) . ' · ' . count($itens) . '</p>'
                . '<table role="presentation" width="100%" cellpadding="0" cellspacing="0">' . $linhas . '</table>' . $resto;
        }

        return <<<HTML
<!doctype html>
<html lang="pt-BR">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <meta name="color-scheme" content="light">
  <title>Seus prazos no Levit</title>
</head>
<body style="margin:0;padding:0;background:#F2F1F8">
  <table role="presentation" width="100%" cellpadding="0" cellspacing="0" style="background:#F2F1F8">
    <tr><td align="center" style="padding:32px 12px">
      <table role="presentation" width="100%" cellpadding="0" cellspacing="0" style="max-width:560px">
        <tr><td style="background:#15132A;border-radius:20px 20px 0 0;padding:26px 36px">
          <p style="margin:0;font-family:{$fonte};font-size:20px;font-weight:800;color:#FFFFFF">Levit</p>
          <p style="margin:22px 0 0;font-family:{$fonte};font-size:26px;line-height:1.15;font-weight:800;color:#FFFFFF">Bom dia, {$nome}. Seus prazos de hoje.</p>
        </td></tr>
        <tr><td style="background:#FFFFFF;border-radius:0 0 20px 20px;border:1px solid #E6E4F0;border-top:0;padding:8px 36px 30px">
          {$secoes}
          <table role="presentation" width="100%" cellpadding="0" cellspacing="0" style="margin-top:26px"><tr>
            <td align="center" bgcolor="#534BAF" style="border-radius:12px;background:#534BAF">
              <a href="{$link}" style="display:block;padding:15px 24px;font-family:{$fonte};font-size:16px;font-weight:bold;color:#FFFFFF;text-decoration:none;border-radius:12px">Abrir o Levit</a>
            </td>
          </tr></table>
          <p style="margin:18px 0 0;font-family:{$fonte};font-size:12px;line-height:1.6;color:#6B6880">
            Você recebe este resumo porque é responsável por itens com prazo. Itens marcados como concluídos, entregues ou pagos não aparecem aqui.
          </p>
        </td></tr>
      </table>
    </td></tr>
  </table>
</body>
</html>
HTML;
    }
}
