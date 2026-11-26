<?php

namespace App\Services\Email;

/**
 * Envio simulado (EMAIL_PROVIDER=log), para desenvolvimento: nada sai da
 * máquina. Cada e-mail vai para writable/logs/emails-simulados.log e o último
 * fica em writable/logs/ultimo-email.html, para abrir no navegador.
 *
 * Usa arquivo próprio em vez de log_message(): em produção o logger só grava
 * erros e o e-mail simulado sumiria sem aviso.
 */
class LogEmailService implements EmailServiceInterface
{
    public function enviar(string $destinatarioEmail, string $assunto, string $corpoHtml, array $anexos = []): void
    {
        $pasta = WRITEPATH . 'logs' . DIRECTORY_SEPARATOR;

        // Anexos (ex.: o PDF do relatório) ficam salvos ao lado do log para conferir.
        foreach ($anexos as $anexo) {
            file_put_contents($pasta . 'ultimo-anexo-' . preg_replace('/[^A-Za-z0-9._-]/', '_', $anexo['nome']), $anexo['conteudo'], LOCK_EX);
        }

        // Links do corpo em texto puro: é por aqui que se copia o link do convite.
        preg_match_all('/href="([^"]+)"/', $corpoHtml, $links);
        $listaLinks = implode(PHP_EOL, array_map(
            static fn (string $l) => '  ' . html_entity_decode($l, ENT_QUOTES | ENT_HTML5, 'UTF-8'),
            array_unique($links[1])
        ));

        $registro = sprintf(
            "[%s] Para: %s%sAssunto: %s%sLinks:%s%s%s" . ($anexos !== [] ? 'Anexos: ' . implode(', ', array_column($anexos, 'nome')) . PHP_EOL : '') . '%s',
            date('Y-m-d H:i:s'),
            $destinatarioEmail,
            PHP_EOL,
            $assunto,
            PHP_EOL,
            PHP_EOL,
            $listaLinks !== '' ? $listaLinks : '  (nenhum)',
            PHP_EOL,
            str_repeat('-', 60) . PHP_EOL
        );

        file_put_contents($pasta . 'emails-simulados.log', $registro, FILE_APPEND | LOCK_EX);
        file_put_contents($pasta . 'ultimo-email.html', $corpoHtml, LOCK_EX);
    }
}
