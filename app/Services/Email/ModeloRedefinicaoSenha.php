<?php

namespace App\Services\Email;

/**
 * E-mail de redefinição de senha. Mesma identidade visual do convite (topo
 * escuro, botão em célula de tabela, estilos inline) e o nome escapado.
 */
final class ModeloRedefinicaoSenha
{
    public const ASSUNTO = 'Redefina sua senha do Levit';

    /** @param array{nome: string, link: string, minutos_validade: int} $dados */
    public static function html(array $dados): string
    {
        $e       = static fn (?string $t) => htmlspecialchars((string) $t, ENT_QUOTES | ENT_HTML5, 'UTF-8');
        $nome    = $e(explode(' ', trim($dados['nome']))[0] ?: 'olá');
        $link    = $e($dados['link']);
        $minutos = (int) $dados['minutos_validade'];
        $fonte   = "'Archivo', 'Segoe UI', Arial, Helvetica, sans-serif";

        return <<<HTML
<!doctype html>
<html lang="pt-BR">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <meta name="color-scheme" content="light">
  <title>Redefina sua senha do Levit</title>
</head>
<body style="margin:0;padding:0;background:#F2F1F8">
  <div style="display:none;max-height:0;overflow:hidden;opacity:0">Use o botão para criar uma senha nova. O link vale por {$minutos} minutos.</div>
  <table role="presentation" width="100%" cellpadding="0" cellspacing="0" style="background:#F2F1F8">
    <tr><td align="center" style="padding:32px 12px">
      <table role="presentation" width="100%" cellpadding="0" cellspacing="0" style="max-width:560px">
        <tr><td style="background:#15132A;border-radius:20px 20px 0 0;padding:26px 36px">
          <p style="margin:0;font-family:{$fonte};font-size:20px;font-weight:800;color:#FFFFFF">Levit</p>
          <p style="margin:22px 0 0;font-family:{$fonte};font-size:28px;line-height:1.15;font-weight:800;color:#FFFFFF">Vamos criar uma senha nova.</p>
        </td></tr>
        <tr><td style="background:#FFFFFF;border-radius:0 0 20px 20px;border:1px solid #E6E4F0;border-top:0;padding:30px 36px">
          <p style="margin:0 0 22px;font-family:{$fonte};font-size:16px;line-height:1.6;color:#4A4860">
            Oi, {$nome}! Recebemos um pedido para redefinir a senha da sua conta. Clique no botão abaixo para escolher uma senha nova.
          </p>
          <table role="presentation" width="100%" cellpadding="0" cellspacing="0"><tr>
            <td align="center" bgcolor="#534BAF" style="border-radius:12px;background:#534BAF">
              <a href="{$link}" style="display:block;padding:16px 24px;font-family:{$fonte};font-size:16px;font-weight:bold;color:#FFFFFF;text-decoration:none;border-radius:12px">Criar senha nova</a>
            </td>
          </tr></table>
          <p style="margin:22px 0 0;font-family:{$fonte};font-size:13px;line-height:1.6;color:#6B6880">
            O link vale por {$minutos} minutos e só funciona uma vez. Se não foi você que pediu, ignore este e-mail: sua senha continua a mesma.
          </p>
          <p style="margin:16px 0 0;font-family:{$fonte};font-size:12px;line-height:1.6;color:#6B6880">
            Se o botão não funcionar, copie este endereço no navegador:<br>
            <a href="{$link}" style="color:#534BAF;word-break:break-all">{$link}</a>
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
