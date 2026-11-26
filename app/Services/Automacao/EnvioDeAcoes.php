<?php

namespace App\Services\Automacao;

use App\Exceptions\FalhaPermanenteException;
use App\Exceptions\NaoEncontradoException;
use App\Services\NotificacaoService;
use App\Services\RegistroService;
use GuzzleHttp\Client;
use GuzzleHttp\Exception\ClientException;
use GuzzleHttp\Exception\GuzzleException;

/**
 * Executa de fato uma ação já preparada (ver PreparacaoDeAcoes): manda o
 * e-mail, grava a notificação, altera ou cria o item, ou chama o webhook.
 * Erro que não melhora repetindo vira FalhaPermanenteException.
 */
class EnvioDeAcoes
{
    /**
     * @param \Closure(): NotificacaoService $notificacoes
     * @param \Closure(): RegistroService    $registros
     */
    public function __construct(
        private DestinoWebhook $destinos,
        private \Closure $notificacoes,
        private \Closure $registros,
        private ?Client $http = null,
    ) {
    }

    /** @return ?string mensagem do passo, quando difere do resumo padrão */
    public function enviar(array $preparada, string $gatilho, string $moduloId, array $contexto): ?string
    {
        $cadeia = $contexto['cadeia'] ?? [];

        switch ($preparada['tipo']) {
            case 'enviar_email':
                service('email')->enviar(
                    $preparada['destinatario'],
                    trim(preg_replace('/\s+/', ' ', $preparada['assunto'])),
                    self::corpoDoEmail($preparada['corpo'])
                );

                return null;

            case 'notificacao':
                ($this->notificacoes)()->notificar($preparada['usuario_ids'], $preparada['empresa_id'], [
                    'tipo'     => 'automacao',
                    'titulo'   => $preparada['titulo'],
                    'mensagem' => $preparada['mensagem'],
                    'link'     => $preparada['link'],
                ]);

                return null;

            case 'atualizar_campo':
                if (empty($contexto['registro_id'])) {
                    throw new FalhaPermanenteException('Não há item para atualizar (a ação só funciona em criação e atualização).');
                }

                $mudou = $this->comDadosValidos(fn () => ($this->registros)()->atualizarPorAutomacao(
                    $contexto['registro_id'],
                    $moduloId,
                    [$preparada['campo_id'] => $preparada['valor']],
                    $cadeia
                ));

                return $mudou ? null : "A coluna {$preparada['campo']} já estava com esse valor.";

            case 'criar_registro':
                $valores = $preparada['valores'];
                if (! empty($preparada['ligar_campo_id'])) {
                    if (empty($contexto['registro_id'])) {
                        throw new FalhaPermanenteException('Não há item para ligar ao novo item (a ação só liga em criação e atualização).');
                    }
                    $valores[$preparada['ligar_campo_id']] = $contexto['registro_id'];
                }
                $this->comDadosValidos(fn () => ($this->registros)()->criarPorAutomacao($preparada['modulo_destino_id'], $valores, $cadeia));

                return null;
        }

        $this->chamarWebhook($preparada, $gatilho);

        return null;
    }

    private function chamarWebhook(array $preparada, string $gatilho): void
    {
        // Resolve e fixa o IP aqui, na hora de chamar (ver DestinoWebhook).
        $resolucao = $this->destinos->resolver($preparada['url']);

        try {
            ($this->http ??= new Client())->post($preparada['url'], [
                'json'            => $preparada['payload'],
                'timeout'         => 5,
                'connect_timeout' => 3,
                // Redirecionar poderia levar para a rede interna depois da checagem.
                'allow_redirects' => false,
                'curl'            => [
                    CURLOPT_RESOLVE   => [$resolucao],
                    CURLOPT_PROTOCOLS => CURLPROTO_HTTP | CURLPROTO_HTTPS,
                ],
                'headers'         => [
                    'User-Agent'     => 'Levit-Automacoes/1.0',
                    'X-Levit-Evento' => $gatilho,
                ],
            ]);
        } catch (ClientException $e) {
            $status = $e->getResponse()->getStatusCode();
            // 408 e 429 são temporários; o resto de 4xx é erro de configuração do destino.
            if (! in_array($status, [408, 429], true)) {
                throw new FalhaPermanenteException("O webhook respondeu {$status}.", $status, $e);
            }
            throw new \RuntimeException("O webhook respondeu {$status}.", $status, $e);
        } catch (GuzzleException $e) {
            // A mensagem do cURL pode citar IPs e portas; o histórico mostra só o essencial.
            log_message('warning', 'Webhook falhou: {erro}', ['erro' => $e->getMessage()]);

            throw new \RuntimeException('O outro sistema não respondeu a tempo.', 0, $e);
        }
    }

    /**
     * O corpo é texto: tanto o que a automação escreveu quanto os valores das
     * colunas (que podem vir do formulário público de candidatura) são
     * escapados, para ninguém injetar links, imagens ou formulários falsos
     * num e-mail que sai com o remetente da plataforma.
     */
    public static function corpoDoEmail(string $texto): string
    {
        $html = nl2br(htmlspecialchars($texto, ENT_QUOTES | ENT_SUBSTITUTE, 'UTF-8'), false);

        return '<div style="font-family:Arial,Helvetica,sans-serif;font-size:15px;line-height:1.6;color:#1f1d2b">' . $html . '</div>';
    }

    /** Dado inválido ou registro sumido não melhora repetindo: falha permanente. */
    private function comDadosValidos(callable $acao): mixed
    {
        try {
            return $acao();
        } catch (\DomainException|NaoEncontradoException $e) {
            throw new FalhaPermanenteException($e->getMessage(), 0, $e);
        }
    }
}
