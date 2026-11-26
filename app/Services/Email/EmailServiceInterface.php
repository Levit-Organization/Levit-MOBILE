<?php

namespace App\Services\Email;

interface EmailServiceInterface
{
    /**
     * @param array<int, array{nome: string, conteudo: string}> $anexos conteúdo binário (ex.: PDF)
     */
    public function enviar(string $destinatarioEmail, string $assunto, string $corpoHtml, array $anexos = []): void;
}