<?php

namespace App\Libraries;

use App\Services\JwtService;
use CodeIgniter\Cookie\Cookie;
use CodeIgniter\HTTP\IncomingRequest;
use CodeIgniter\HTTP\RequestInterface;
use CodeIgniter\HTTP\ResponseInterface;

/**
 * Sessão do navegador em cookie HttpOnly.
 *
 * O JWT fica num cookie que o JavaScript da página não consegue ler: um XSS
 * pode até fazer chamadas enquanto a aba está aberta, mas não leva o token
 * embora. Como o navegador manda o cookie sozinho, toda escrita autenticada
 * por cookie exige também o cabeçalho X-CSRF-Token, derivado do jti do token.
 * Um site de terceiros não consegue ler esse valor nem mandar o cabeçalho (a
 * política de CORS só libera as origens do FRONTEND_URL).
 *
 * Clientes sem navegador (o app Android) continuam com "Authorization: Bearer"
 * e recebem o token no corpo, como antes. O navegador pede o modo cookie com o
 * cabeçalho X-Levit-Sessao: cookie.
 */
class SessaoWeb
{
    public const COOKIE         = 'levit_sessao';
    public const CABECALHO      = 'X-Levit-Sessao';
    public const CABECALHO_CSRF = 'X-CSRF-Token';

    /** O navegador pediu sessão por cookie, ou já está usando uma. */
    public static function modoCookie(RequestInterface $request): bool
    {
        return strtolower($request->getHeaderLine(self::CABECALHO)) === 'cookie'
            || service('authenticatedUser')->viaCookie;
    }

    public static function tokenDoCookie(RequestInterface $request): ?string
    {
        $valor = $request instanceof IncomingRequest ? $request->getCookie(self::COOKIE) : null;

        return is_string($valor) && $valor !== '' ? $valor : null;
    }

    public static function csrfPara(string $jti): string
    {
        return hash_hmac('sha256', 'csrf|' . $jti, (string) env('JWT_SECRET_KEY'));
    }

    public static function csrfValido(RequestInterface $request, string $jti): bool
    {
        return hash_equals(self::csrfPara($jti), $request->getHeaderLine(self::CABECALHO_CSRF));
    }

    /**
     * Entrega a sessão no formato do cliente. No modo cookie, o token sai do
     * corpo e entra no cookie; o corpo leva só o valor de CSRF.
     *
     * @param array<string, mixed> $resposta saída de AuthService::montarRespostaAutenticacao()
     *
     * @return array<string, mixed> o corpo a devolver
     */
    public static function entregar(RequestInterface $request, ResponseInterface $response, array $resposta): array
    {
        if (! self::modoCookie($request)) {
            return $resposta;
        }

        $token  = (string) $resposta['token'];
        $claims = (new JwtService())->validar($token);
        unset($resposta['token']);
        $resposta['csrf'] = self::csrfPara((string) $claims->jti);

        $response->setCookie(self::cookie($token, (int) $claims->exp));

        return $resposta;
    }

    public static function encerrar(ResponseInterface $response): void
    {
        $response->setCookie(self::cookie('', 1));
    }

    private static function cookie(string $valor, int $expira): Cookie
    {
        // SameSite=Lax serve quando front e API estão no mesmo site (ex.:
        // app.levit.com e api.levit.com). Em domínios diferentes, use None
        // (o navegador exige Secure junto).
        $sameSite = (string) env('SESSAO_COOKIE_SAMESITE', 'Lax');
        $seguro   = filter_var(env('SESSAO_COOKIE_SECURE', ENVIRONMENT === 'production'), FILTER_VALIDATE_BOOLEAN)
            || strcasecmp($sameSite, 'None') === 0;

        return new Cookie(self::COOKIE, $valor, [
            'expires'  => $expira,
            'path'     => '/',
            'domain'   => (string) env('SESSAO_COOKIE_DOMINIO', ''),
            'secure'   => $seguro,
            'httponly' => true,
            'samesite' => $sameSite,
            'raw'      => true,
            'prefix'   => '',
        ]);
    }
}
