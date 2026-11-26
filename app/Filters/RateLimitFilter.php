<?php

namespace App\Filters;

use CodeIgniter\Filters\FilterInterface;
use CodeIgniter\HTTP\IncomingRequest;
use CodeIgniter\HTTP\RequestInterface;
use CodeIgniter\HTTP\ResponseInterface;

class RateLimitFilter implements FilterInterface
{
    public function before(RequestInterface $request, $arguments = null)
    {
        // Argumentos opcionais: balde, limite por hora por IP e, se houver,
        // limite por hora por e-mail do corpo (ex.: 'ratelimit:login,30,10').
        // O limite por e-mail segura quem tenta senhas de uma conta a partir
        // de muitos IPs, ou enche a caixa de entrada de alguém.
        // Sem argumentos, mantém o balde e o limite de sempre.
        $balde       = $arguments[0] ?? 'candidatura';
        $limite      = (int) ($arguments[1] ?? 5);
        $limiteEmail = isset($arguments[2]) ? (int) $arguments[2] : null;

        $throttler = service('throttler');

        if ($throttler->check($balde . '_' . md5($request->getIPAddress()), $limite, HOUR) === false) {
            return $this->muitasTentativas();
        }

        if ($limiteEmail !== null) {
            $email = $this->emailDoCorpo($request);
            if ($email !== '' && $throttler->check($balde . '_email_' . md5($email), $limiteEmail, HOUR) === false) {
                return $this->muitasTentativas();
            }
        }

        return null;
    }

    private function emailDoCorpo(RequestInterface $request): string
    {
        if (! $request instanceof IncomingRequest) {
            return '';
        }

        try {
            $corpo = $request->getJSON(true);
        } catch (\Throwable) {
            $corpo = null;
        }
        $email = is_array($corpo) ? ($corpo['email'] ?? '') : $request->getPost('email');

        return is_string($email) ? mb_strtolower(trim($email)) : '';
    }

    private function muitasTentativas(): ResponseInterface
    {
        return service('response')
            ->setStatusCode(429)
            ->setJSON(['status' => 'error', 'message' => 'Muitas tentativas. Tente novamente mais tarde.']);
    }

    public function after(RequestInterface $request, ResponseInterface $response, $arguments = null)
    {
        // Nada a fazer depois da resposta, por enquanto.
        return null;
    }
}