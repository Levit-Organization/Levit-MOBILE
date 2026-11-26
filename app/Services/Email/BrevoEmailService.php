<?php

namespace App\Services\Email;

use App\Exceptions\FalhaPermanenteException;
use GuzzleHttp\Client;
use GuzzleHttp\Exception\ClientException;
use GuzzleHttp\Exception\GuzzleException;

class BrevoEmailService implements EmailServiceInterface
{
    private Client $client;
    private string $apiKey;
    private string $remetenteEmail;
    private string $remetenteNome;

    public function __construct()
    {
        $this->apiKey         = env('BREVO_API_KEY');
        $this->remetenteEmail = env('BREVO_REMETENTE_EMAIL');
        $this->remetenteNome  = env('BREVO_REMETENTE_NOME', 'Levit');
        $this->client         = new Client();
    }

    public function enviar(string $destinatarioEmail, string $assunto, string $corpoHtml, array $anexos = []): void
    {
        $extras = $anexos === [] ? [] : ['attachment' => array_map(
            static fn (array $a) => ['name' => $a['nome'], 'content' => base64_encode($a['conteudo'])],
            $anexos
        )];

        try {
            $this->client->post('https://api.brevo.com/v3/smtp/email', [
                'headers' => [
                    'accept'       => 'application/json',
                    'api-key'      => $this->apiKey,
                    'content-type' => 'application/json',
                ],
                'json' => [
                    'sender' => [
                        'name'  => $this->remetenteNome,
                        'email' => $this->remetenteEmail,
                    ],
                    'to' => [
                        ['email' => $destinatarioEmail],
                    ],
                    'subject'     => $assunto,
                    'htmlContent' => $corpoHtml,
                ] + $extras,
            ]);
        } catch (ClientException $e) {
            // 4xx (exceto limite de taxa) não melhora repetindo: destinatário ou conta inválidos.
            $status = $e->getResponse()->getStatusCode();
            $classe = $status === 429 ? \RuntimeException::class : FalhaPermanenteException::class;
            throw new $classe('Não foi possível enviar o e-mail: ' . $e->getMessage(), $status, $e);
        } catch (GuzzleException $e) {
            throw new \RuntimeException('Não foi possível enviar o e-mail: ' . $e->getMessage(), 0, $e);
        }
    }
}