<?php

use App\Libraries\SessaoWeb;
use App\Services\JwtService;
use CodeIgniter\HTTP\IncomingRequest;
use CodeIgniter\HTTP\Response;
use CodeIgniter\HTTP\SiteURI;
use CodeIgniter\HTTP\UserAgent;
use CodeIgniter\Test\CIUnitTestCase;
use Config\App;
use Config\Services;

/**
 * @internal
 */
final class SessaoWebTest extends CIUnitTestCase
{
    private string $segredoAnterior;

    protected function setUp(): void
    {
        parent::setUp();
        $this->segredoAnterior = (string) getenv('JWT_SECRET_KEY');
        putenv('JWT_SECRET_KEY=segredo-de-teste-com-tamanho-suficiente-para-hs256');
        $_ENV['JWT_SECRET_KEY'] = 'segredo-de-teste-com-tamanho-suficiente-para-hs256';
        Services::authenticatedUser()->viaCookie = false;
    }

    protected function tearDown(): void
    {
        putenv('JWT_SECRET_KEY=' . $this->segredoAnterior);
        $_ENV['JWT_SECRET_KEY'] = $this->segredoAnterior;
        Services::authenticatedUser()->viaCookie = false;
        parent::tearDown();
    }

    /** @param array<string, string> $cabecalhos */
    private function requisicao(array $cabecalhos = []): IncomingRequest
    {
        $config  = config(App::class);
        $request = new IncomingRequest($config, new SiteURI($config), null, new UserAgent());
        foreach ($cabecalhos as $nome => $valor) {
            $request->setHeader($nome, $valor);
        }

        return $request;
    }

    /** @return array<string, mixed> */
    private function respostaDeLogin(): array
    {
        return [
            'token'   => (new JwtService())->gerar(['sub' => 'u1', 'empresa_id' => 'e1']),
            'usuario' => ['id' => 'u1'],
        ];
    }

    public function testAppRecebeOTokenNoCorpoESemCookie(): void
    {
        $response = new Response(config(App::class));
        $corpo    = SessaoWeb::entregar($this->requisicao(), $response, $this->respostaDeLogin());

        $this->assertArrayHasKey('token', $corpo);
        $this->assertArrayNotHasKey('csrf', $corpo);
        $this->assertFalse($response->hasCookie(SessaoWeb::COOKIE));
    }

    public function testNavegadorRecebeCookieHttpOnlyESemTokenNoCorpo(): void
    {
        $response = new Response(config(App::class));
        $login    = $this->respostaDeLogin();
        $corpo    = SessaoWeb::entregar($this->requisicao([SessaoWeb::CABECALHO => 'cookie']), $response, $login);

        $this->assertArrayNotHasKey('token', $corpo);
        $this->assertNotEmpty($corpo['csrf']);

        $cookie = $response->getCookie(SessaoWeb::COOKIE);
        $this->assertNotNull($cookie);
        $this->assertSame($login['token'], $cookie->getValue());
        $this->assertTrue($cookie->isHTTPOnly());
    }

    public function testQuemJaUsaCookieContinuaNoModoCookie(): void
    {
        Services::authenticatedUser()->viaCookie = true;
        $corpo = SessaoWeb::entregar($this->requisicao(), new Response(config(App::class)), $this->respostaDeLogin());

        $this->assertArrayNotHasKey('token', $corpo, 'trocar de equipe pelo navegador não pode expor o token');
    }

    public function testCsrfConfereComOJtiDoToken(): void
    {
        $csrf = SessaoWeb::csrfPara('jti-1');

        $this->assertTrue(SessaoWeb::csrfValido($this->requisicao([SessaoWeb::CABECALHO_CSRF => $csrf]), 'jti-1'));
        $this->assertFalse(SessaoWeb::csrfValido($this->requisicao([SessaoWeb::CABECALHO_CSRF => $csrf]), 'jti-2'));
        $this->assertFalse(SessaoWeb::csrfValido($this->requisicao(), 'jti-1'));
    }

    public function testEncerrarExpiraOCookie(): void
    {
        $response = new Response(config(App::class));
        SessaoWeb::encerrar($response);

        $cookie = $response->getCookie(SessaoWeb::COOKIE);
        $this->assertNotNull($cookie);
        $this->assertTrue($cookie->isExpired());
    }
}
