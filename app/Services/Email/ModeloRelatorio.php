<?php

namespace App\Services\Email;

/** E-mail que leva o PDF do relatório agendado em anexo. */
final class ModeloRelatorio
{
    public static function assunto(string $nomeRelatorio, string $periodo): string
    {
        return "Relatório “{$nomeRelatorio}” · {$periodo}";
    }

    /** @param array{nome_relatorio: string, empresa: string, periodo: string, total: int, pessoas: int, link: string} $dados */
    public static function html(array $dados): string
    {
        $e        = static fn (?string $t) => htmlspecialchars((string) $t, ENT_QUOTES | ENT_HTML5, 'UTF-8');
        $relat    = $e($dados['nome_relatorio']);
        $empresa  = $e($dados['empresa']);
        $periodo  = $e($dados['periodo']);
        $total    = (int) $dados['total'];
        $pessoas  = (int) $dados['pessoas'];
        $link     = $e($dados['link']);
        $fonte    = "'Archivo', 'Segoe UI', Arial, Helvetica, sans-serif";
        $resumo   = $total === 0
            ? 'Nada foi registrado na plataforma neste período.'
            : ($total === 1 ? '1 atividade' : "{$total} atividades") . ' de ' . ($pessoas === 1 ? '1 pessoa' : "{$pessoas} pessoas") . ' (e das automações).';

        return <<<HTML
<!doctype html>
<html lang="pt-BR">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <meta name="color-scheme" content="light">
  <title>{$relat}</title>
</head>
<body style="margin:0;padding:0;background:#F2F1F8">
  <table role="presentation" width="100%" cellpadding="0" cellspacing="0" style="background:#F2F1F8">
    <tr><td align="center" style="padding:32px 12px">
      <table role="presentation" width="100%" cellpadding="0" cellspacing="0" style="max-width:560px">
        <tr><td style="background:#15132A;border-radius:20px 20px 0 0;padding:26px 36px">
          <p style="margin:0;font-family:{$fonte};font-size:20px;font-weight:800;color:#FFFFFF">Levit</p>
          <p style="margin:22px 0 0;font-family:{$fonte};font-size:26px;line-height:1.15;font-weight:800;color:#FFFFFF">{$relat}</p>
          <p style="margin:8px 0 0;font-family:{$fonte};font-size:14px;color:#C9C6E0">{$empresa} · {$periodo}</p>
        </td></tr>
        <tr><td style="background:#FFFFFF;border-radius:0 0 20px 20px;border:1px solid #E6E4F0;border-top:0;padding:30px 36px">
          <p style="margin:0;font-family:{$fonte};font-size:16px;line-height:1.6;color:#4A4860">
            O relatório do período está em anexo, em PDF: o que foi feito na plataforma e por quem. {$resumo}
          </p>
          <table role="presentation" width="100%" cellpadding="0" cellspacing="0" style="margin-top:24px"><tr>
            <td align="center" bgcolor="#534BAF" style="border-radius:12px;background:#534BAF">
              <a href="{$link}" style="display:block;padding:15px 24px;font-family:{$fonte};font-size:16px;font-weight:bold;color:#FFFFFF;text-decoration:none;border-radius:12px">Ver relatórios no Levit</a>
            </td>
          </tr></table>
        </td></tr>
      </table>
    </td></tr>
  </table>
</body>
</html>
HTML;
    }
}
