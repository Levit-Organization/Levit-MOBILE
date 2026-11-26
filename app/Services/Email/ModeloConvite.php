<?php

namespace App\Services\Email;

/**
 * E-mail do convite para a equipe.
 *
 * Feito para clientes de e-mail, não para navegador: layout em tabelas,
 * estilos inline (Gmail e Outlook ignoram <style> e CSS externo), botão como
 * célula de tabela (funciona no Outlook) e nenhuma imagem, já que o logo por
 * URL não carregaria em desenvolvimento e muitos clientes bloqueiam imagens.
 * Tudo que vem do usuário é escapado: nome, empresa, cargo e principalmente a
 * mensagem livre de quem convidou.
 */
final class ModeloConvite
{
    /** Paleta da marca (mesma da landing), em hex: clientes de e-mail não entendem oklch. */
    private const NOITE   = '#15132A';
    private const MARCA   = '#534BAF';
    private const MARCA_C = '#B9B4EA';
    private const FUNDO   = '#F2F1F8';
    private const TINTA   = '#1B1930';
    private const SUAVE   = '#4A4860';
    private const SUTIL   = '#6B6880';
    private const LINHA   = '#E6E4F0';

    private const MESES = ['janeiro', 'fevereiro', 'março', 'abril', 'maio', 'junho', 'julho', 'agosto', 'setembro', 'outubro', 'novembro', 'dezembro'];

    /**
     * @param array{convidado_por: string, empresa: string, cargo: string, mensagem: ?string, link: string, dias_validade: int} $dados
     */
    public static function assunto(array $dados): string
    {
        return "{$dados['convidado_por']} convidou você para a equipe {$dados['empresa']} no Levit";
    }

    public static function html(array $dados): string
    {
        $e = static fn (?string $texto) => htmlspecialchars((string) $texto, ENT_QUOTES | ENT_HTML5, 'UTF-8');

        $convidadoPor = $e($dados['convidado_por']);
        $empresa      = $e($dados['empresa']);
        $cargo        = $e($dados['cargo']);
        $link         = $e($dados['link']);
        $dias         = (int) $dados['dias_validade'];
        $validoAte    = self::dataPorExtenso(time() + $dias * 86400);
        $iniciais     = $e(self::iniciais($dados['convidado_por']));
        $previa       = $e("{$dados['convidado_por']} quer você na equipe {$dados['empresa']}. Aceite em um clique e crie sua senha.");

        [$noite, $marca, $marcaC, $fundo, $tinta, $suave, $sutil, $linha] =
            [self::NOITE, self::MARCA, self::MARCA_C, self::FUNDO, self::TINTA, self::SUAVE, self::SUTIL, self::LINHA];
        $fonte = "'Archivo', 'Segoe UI', Arial, Helvetica, sans-serif";

        $mensagem = '';
        if (trim((string) ($dados['mensagem'] ?? '')) !== '') {
            $texto    = nl2br($e(trim($dados['mensagem'])));
            $mensagem = <<<HTML
            <tr><td style="padding:0 36px 28px">
              <table role="presentation" width="100%" cellpadding="0" cellspacing="0" style="background:#F6F5FD;border-radius:14px">
                <tr>
                  <td width="4" style="background:{$marca};border-radius:14px 0 0 14px;font-size:0;line-height:0">&nbsp;</td>
                  <td style="padding:18px 20px">
                    <p style="margin:0 0 8px;font-family:{$fonte};font-size:13px;font-weight:bold;color:{$marca}">Mensagem de {$convidadoPor}</p>
                    <p style="margin:0;font-family:{$fonte};font-size:15px;line-height:1.65;color:{$tinta}">{$texto}</p>
                  </td>
                </tr>
              </table>
            </td></tr>
HTML;
        }

        $detalhes = '';
        foreach ([['Equipe', $empresa], ['Seu cargo', $cargo], ['Válido até', $validoAte]] as $i => [$rotulo, $valor]) {
            $borda     = $i > 0 ? "border-top:1px solid {$linha};" : '';
            $detalhes .= "<tr><td style=\"{$borda}padding:12px 0;font-family:{$fonte};font-size:13px;color:{$sutil}\">{$rotulo}</td>"
                . "<td align=\"right\" style=\"{$borda}padding:12px 0;font-family:{$fonte};font-size:14px;font-weight:bold;color:{$tinta}\">{$valor}</td></tr>";
        }

        $recursos = '';
        foreach ([
            ['Tabelas', 'no lugar das planilhas espalhadas'],
            ['Quadros', 'de tarefas e de contratação, etapa por etapa'],
            ['Avisos', 'automáticos para a pessoa certa, na hora certa'],
        ] as [$titulo, $texto]) {
            $recursos .= <<<HTML
                <tr>
                  <td width="22" valign="top" style="padding:6px 0">
                    <div style="width:10px;height:10px;margin-top:5px;border-radius:3px;background:{$marca}"></div>
                  </td>
                  <td style="padding:6px 0;font-family:{$fonte};font-size:14px;line-height:1.5;color:{$suave}">
                    <strong style="color:{$tinta}">{$titulo}</strong> {$texto}
                  </td>
                </tr>
HTML;
        }

        return <<<HTML
<!doctype html>
<html lang="pt-BR">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <meta name="color-scheme" content="light">
  <meta name="supported-color-schemes" content="light">
  <title>Convite para a equipe {$empresa}</title>
  <link href="https://fonts.googleapis.com/css2?family=Archivo:wght@400;600;800&display=swap" rel="stylesheet">
</head>
<body style="margin:0;padding:0;background:{$fundo};-webkit-text-size-adjust:100%">
  <div style="display:none;max-height:0;overflow:hidden;opacity:0;color:{$fundo}">{$previa}</div>

  <table role="presentation" width="100%" cellpadding="0" cellspacing="0" style="background:{$fundo}">
    <tr><td align="center" style="padding:32px 12px">
      <table role="presentation" width="100%" cellpadding="0" cellspacing="0" style="max-width:560px">

        <!-- Topo escuro com a marca -->
        <tr><td style="background:{$noite};border-radius:20px 20px 0 0;padding:26px 36px">
          <table role="presentation" cellpadding="0" cellspacing="0"><tr>
            <td style="background:{$marca};border-radius:9px;padding:7px" valign="middle">
              <table role="presentation" cellpadding="0" cellspacing="2"><tr>
                <td style="width:7px;height:7px;border:2px solid #FFFFFF;border-radius:2px;font-size:0;line-height:0">&nbsp;</td>
                <td style="width:7px;height:7px;border:2px solid #FFFFFF;border-radius:2px;font-size:0;line-height:0">&nbsp;</td>
              </tr><tr>
                <td style="width:7px;height:7px;border:2px solid #FFFFFF;border-radius:2px;font-size:0;line-height:0">&nbsp;</td>
                <td style="width:7px;height:7px;border:2px solid #FFFFFF;border-radius:2px;font-size:0;line-height:0">&nbsp;</td>
              </tr></table>
            </td>
            <td style="padding-left:12px;font-family:{$fonte};font-size:20px;font-weight:800;letter-spacing:-.01em;color:#FFFFFF" valign="middle">Levit</td>
          </tr></table>
          <p style="margin:26px 0 0;font-family:{$fonte};font-size:14px;font-weight:bold;color:{$marcaC}">Convite para a equipe</p>
          <p style="margin:8px 0 0;font-family:{$fonte};font-size:30px;line-height:1.15;font-weight:800;letter-spacing:-.02em;color:#FFFFFF">Sua vaga na equipe {$empresa} está esperando.</p>
        </td></tr>

        <!-- Corpo -->
        <tr><td style="background:#FFFFFF;border-radius:0 0 20px 20px;border:1px solid {$linha};border-top:0">
          <table role="presentation" width="100%" cellpadding="0" cellspacing="0">

            <tr><td style="padding:30px 36px 22px">
              <table role="presentation" cellpadding="0" cellspacing="0"><tr>
                <td width="48" height="48" align="center" valign="middle" style="width:48px;height:48px;border-radius:24px;background:#EDEBFA;font-family:{$fonte};font-size:16px;font-weight:bold;color:{$marca}">{$iniciais}</td>
                <td style="padding-left:14px;font-family:{$fonte};font-size:16px;line-height:1.5;color:{$suave}" valign="middle">
                  <strong style="color:{$tinta}">{$convidadoPor}</strong> convidou você para entrar na equipe.
                </td>
              </tr></table>
            </td></tr>

            {$mensagem}

            <tr><td style="padding:0 36px 8px">
              <table role="presentation" width="100%" cellpadding="0" cellspacing="0">{$detalhes}</table>
            </td></tr>

            <!-- Botão (célula de tabela: funciona também no Outlook) -->
            <tr><td style="padding:22px 36px 10px">
              <table role="presentation" width="100%" cellpadding="0" cellspacing="0"><tr>
                <td align="center" bgcolor="{$marca}" style="border-radius:12px;background:{$marca}">
                  <a href="{$link}" style="display:block;padding:16px 24px;font-family:{$fonte};font-size:16px;font-weight:bold;color:#FFFFFF;text-decoration:none;border-radius:12px">Aceitar convite</a>
                </td>
              </tr></table>
            </td></tr>
            <tr><td align="center" style="padding:6px 36px 30px;font-family:{$fonte};font-size:13px;color:{$sutil}">
              Você só precisa escolher seu nome e uma senha.
            </td></tr>

            <!-- O que é o Levit -->
            <tr><td style="padding:24px 36px;border-top:1px solid {$linha}">
              <p style="margin:0 0 10px;font-family:{$fonte};font-size:14px;font-weight:bold;color:{$tinta}">No Levit, a equipe tem tudo num lugar só:</p>
              <table role="presentation" width="100%" cellpadding="0" cellspacing="0">{$recursos}</table>
            </td></tr>

            <tr><td style="padding:20px 36px 28px;border-top:1px solid {$linha};font-family:{$fonte};font-size:12px;line-height:1.6;color:{$sutil}">
              O convite vale por {$dias} dias. Se o botão não funcionar, copie este endereço no navegador:<br>
              <a href="{$link}" style="color:{$marca};word-break:break-all;text-decoration:underline">{$link}</a>
            </td></tr>
          </table>
        </td></tr>

        <tr><td align="center" style="padding:20px 24px 0;font-family:{$fonte};font-size:12px;line-height:1.6;color:{$sutil}">
          Você recebeu este e-mail porque alguém da equipe {$empresa} informou este endereço.<br>Se não esperava este convite, é só ignorar.
        </td></tr>
      </table>
    </td></tr>
  </table>
</body>
</html>
HTML;
    }

    private static function iniciais(string $nome): string
    {
        $partes = array_values(array_filter(preg_split('/\s+/', trim($nome)) ?: []));
        if ($partes === []) {
            return '?';
        }
        $primeira = mb_substr($partes[0], 0, 1);
        $ultima   = count($partes) > 1 ? mb_substr($partes[count($partes) - 1], 0, 1) : '';

        return mb_strtoupper($primeira . $ultima);
    }

    private static function dataPorExtenso(int $timestamp): string
    {
        return date('j', $timestamp) . ' de ' . self::MESES[(int) date('n', $timestamp) - 1];
    }
}
