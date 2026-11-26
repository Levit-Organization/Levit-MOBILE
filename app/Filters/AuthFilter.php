<?php

namespace App\Filters;

use App\Libraries\SessaoWeb;
use App\Services\AuthService;
use App\Services\JwtService;
use CodeIgniter\Filters\FilterInterface;
use CodeIgniter\HTTP\RequestInterface;
use CodeIgniter\HTTP\ResponseInterface;

class AuthFilter implements FilterInterface
{
    public function before(RequestInterface $request, $arguments = null)
    {
        // App (Android) manda Authorization: Bearer; o navegador manda o cookie HttpOnly.
        $authHeader = $request->getHeaderLine('Authorization');
        $viaCookie  = ! str_starts_with($authHeader, 'Bearer ');
        $token      = $viaCookie ? SessaoWeb::tokenDoCookie($request) : substr($authHeader, 7);

        if ($token === null || $token === '') {
            return service('response')
                ->setStatusCode(401)
                ->setJSON(['status' => 'error', 'message' => 'Token de autenticação não informado.']);
        }

        try {
            $claims = (new JwtService())->validar($token);
        } catch (\Exception $e) {
            return service('response')
                ->setStatusCode(401)
                ->setJSON(['status' => 'error', 'message' => 'Token inválido ou expirado.']);
        }

        // Revogação, cargo e permissões numa única consulta.
        $dadosAutorizacao = (new AuthService())->contextoDeAutenticacao($claims->sub, (string) ($claims->empresa_id ?? ''), $claims->jti);

        // Senha trocada depois que o token foi emitido: sessão antiga não vale mais.
        $senhaTrocadaDepois = $dadosAutorizacao !== null
            && $dadosAutorizacao['senha_alterada_em'] !== null
            && (int) $claims->iat < strtotime($dadosAutorizacao['senha_alterada_em']);

        if ($dadosAutorizacao === null || $dadosAutorizacao['revogado'] || $senhaTrocadaDepois) {
            return service('response')
                ->setStatusCode(401)
                ->setJSON(['status' => 'error', 'message' => 'Token inválido ou expirado.']);
        }

        // Cookie vai sozinho em qualquer requisição do navegador: escrita só com o
        // cabeçalho de CSRF, que um site de terceiros não consegue montar.
        $escrita = ! in_array(strtoupper($request->getMethod()), ['GET', 'HEAD', 'OPTIONS'], true);
        if ($viaCookie && $escrita && ! SessaoWeb::csrfValido($request, (string) $claims->jti)) {
            return service('response')
                ->setStatusCode(403)
                ->setJSON(['status' => 'error', 'message' => 'Sessão inválida. Recarregue a página e tente novamente.']);
        }

        service('authenticatedUser')->viaCookie = $viaCookie;
        service('authenticatedUser')->preencher(
            $claims->sub,
            $claims->empresa_id,
            $dadosAutorizacao['cargo_id'],
            $claims->jti,
            $claims->exp,
            $dadosAutorizacao['permissoes'],
            $dadosAutorizacao['acesso_total'],
            $dadosAutorizacao['niveis']
        );

        if (! empty($arguments)) {
            $permissaoNecessaria = $arguments[0];

            if (! $dadosAutorizacao['acesso_total'] && ! in_array($permissaoNecessaria, $dadosAutorizacao['permissoes'], true)) {
                return service('response')
                    ->setStatusCode(403)
                    ->setJSON(['status' => 'error', 'message' => 'Você não tem permissão para realizar esta ação.']);
            }
        }

        return null;
    }

    public function after(RequestInterface $request, ResponseInterface $response, $arguments = null)
    {
        // Nada a fazer depois da resposta, por enquanto.
        return null;
    }
}