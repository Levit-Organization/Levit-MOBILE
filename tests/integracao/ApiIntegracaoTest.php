<?php

use App\Libraries\SessaoWeb;
use CodeIgniter\Test\CIUnitTestCase;
use CodeIgniter\Test\DatabaseTestTrait;
use CodeIgniter\Test\FeatureTestTrait;
use CodeIgniter\Test\TestResponse;
use Config\Services;

/**
 * Testes de ponta a ponta da API contra um PostgreSQL 18 real: rotas,
 * filtros, controllers, services e banco juntos.
 *
 * Rodam no CI (serviço postgres:18). Localmente, só quando o grupo de banco
 * 'tests' aponta para um PostgreSQL; caso contrário são pulados:
 *
 *   database.tests.DBDriver=Postgre database.tests.hostname=127.0.0.1 \
 *   database.tests.database=levit_tests database.tests.username=... \
 *   database.tests.password=... vendor/bin/phpunit tests/integracao
 *
 * @internal
 */
final class ApiIntegracaoTest extends CIUnitTestCase
{
    use DatabaseTestTrait;
    use FeatureTestTrait;

    protected $DBGroup     = 'tests';
    protected $migrate     = true;
    protected $migrateOnce = true;
    protected $refresh     = false;
    protected $namespace   = null; // App e CodeIgniter\Queue

    protected function setUp(): void
    {
        if (env('database.tests.DBDriver') !== 'Postgre') {
            $this->markTestSkipped('Precisa de PostgreSQL 18 no grupo de banco "tests".');
        }

        parent::setUp();

        putenv('EMAIL_PROVIDER=log');
        $_ENV['EMAIL_PROVIDER'] = 'log';
        service('superglobals')->setCookieArray([]);
        cache()->clean(); // o rate limit (cadastro: 10/hora por IP) não pode vazar entre testes
        service('authenticatedUser')->viaCookie = false;
    }

    // ------------------------------------------------------------------
    // Auxiliares
    // ------------------------------------------------------------------

    /** @param array<string, mixed> $corpo */
    private function chamar(string $metodo, string $uri, array $corpo = [], array $cabecalhos = []): TestResponse
    {
        // Cada chamada é uma requisição nova: zera o usuário autenticado da anterior.
        Services::injectMock('authenticatedUser', new App\Libraries\AuthenticatedUser());

        return $this->withHeaders($cabecalhos)
            ->withBodyFormat('json')
            ->call($metodo, 'api/v1/' . $uri, $corpo);
    }

    /** @return array<string, mixed> */
    private function dados(TestResponse $r): array
    {
        return json_decode($r->getJSON(), true)['data'] ?? [];
    }

    /**
     * Cria uma empresa com seu fundador e devolve o token Bearer (modo app).
     *
     * @return array{token: string, empresa_id: string, email: string}
     */
    private function novaEmpresa(string $nome): array
    {
        $email = 'fundador.' . bin2hex(random_bytes(4)) . '@exemplo.com';
        $r     = $this->chamar('POST', 'auth/registrar', [
            'nome'         => 'Pessoa ' . $nome,
            'email'        => $email,
            'senha'        => 'senha12345',
            'nome_empresa' => $nome,
        ]);
        $r->assertStatus(201);
        $d = $this->dados($r);

        return ['token' => $d['token'], 'empresa_id' => $d['empresa']['id'], 'email' => $email];
    }

    /** @return array<string, string> */
    private function bearer(string $token): array
    {
        return ['Authorization' => 'Bearer ' . $token];
    }

    // ------------------------------------------------------------------
    // Autenticação
    // ------------------------------------------------------------------

    public function testRotaProtegidaSemCredencialRecusa(): void
    {
        $this->chamar('GET', 'modulos')->assertStatus(401);
    }

    public function testAppRecebeTokenNoCorpoEUsaBearer(): void
    {
        $empresa = $this->novaEmpresa('Acme App');

        $this->chamar('GET', 'modulos', [], $this->bearer($empresa['token']))->assertStatus(200);
    }

    public function testSenhaErradaNaoAutentica(): void
    {
        $empresa = $this->novaEmpresa('Acme Senha');

        $this->chamar('POST', 'auth/login', ['email' => $empresa['email'], 'senha' => 'errada123'])->assertStatus(401);
    }

    public function testNavegadorRecebeCookieEPrecisaDeCsrfParaEscrever(): void
    {
        $empresa = $this->novaEmpresa('Acme Web');

        $login = $this->chamar('POST', 'auth/login', ['email' => $empresa['email'], 'senha' => 'senha12345'], [SessaoWeb::CABECALHO => 'cookie']);
        $login->assertStatus(200);
        $corpo = $this->dados($login);
        $this->assertArrayNotHasKey('token', $corpo, 'no navegador o token não pode vir no corpo');

        $cookie = $login->response()->getCookie(SessaoWeb::COOKIE);
        $this->assertNotNull($cookie);
        $this->assertTrue($cookie->isHTTPOnly());
        service('superglobals')->setCookie(SessaoWeb::COOKIE, $cookie->getValue());

        // Leitura só com o cookie.
        $this->chamar('GET', 'modulos')->assertStatus(200);

        // Escrita sem CSRF: recusada.
        $this->chamar('POST', 'modulos', ['nome' => 'Clientes'])->assertStatus(403);

        // Escrita com CSRF: aceita.
        $this->chamar('POST', 'modulos', ['nome' => 'Clientes'], [SessaoWeb::CABECALHO_CSRF => $corpo['csrf']])->assertStatus(201);
    }

    public function testLogoutRevogaOToken(): void
    {
        $empresa = $this->novaEmpresa('Acme Logout');

        $this->chamar('POST', 'auth/logout', [], $this->bearer($empresa['token']))->assertStatus(200);
        $this->chamar('GET', 'modulos', [], $this->bearer($empresa['token']))->assertStatus(401);
    }

    // ------------------------------------------------------------------
    // Isolamento entre empresas
    // ------------------------------------------------------------------

    public function testEmpresaNaoVeNemAlteraModuloDeOutra(): void
    {
        $a = $this->novaEmpresa('Empresa A');
        $b = $this->novaEmpresa('Empresa B');

        $criado = $this->chamar('POST', 'modulos', ['nome' => 'Segredos de A'], $this->bearer($a['token']));
        $criado->assertStatus(201);
        $moduloId = $this->dados($criado)['id'];

        $this->chamar('GET', "modulos/{$moduloId}", [], $this->bearer($b['token']))->assertStatus(404);
        $this->chamar('PUT', "modulos/{$moduloId}", ['nome' => 'Invadido'], $this->bearer($b['token']))->assertStatus(404);
        $this->chamar('DELETE', "modulos/{$moduloId}", [], $this->bearer($b['token']))->assertStatus(404);
        $this->chamar('GET', "modulos/{$moduloId}/registros", [], $this->bearer($b['token']))->assertStatus(404);

        $listaDeB = $this->dados($this->chamar('GET', 'modulos', [], $this->bearer($b['token'])));
        $this->assertNotContains($moduloId, array_column($listaDeB, 'id'));

        // A dona continua vendo.
        $this->chamar('GET', "modulos/{$moduloId}", [], $this->bearer($a['token']))->assertStatus(200);
    }

    public function testRegistrosCrudDentroDaEmpresa(): void
    {
        $a = $this->novaEmpresa('Empresa Registros');

        $moduloId = $this->dados($this->chamar('POST', 'modulos', [
            'nome'   => 'Clientes',
            'campos' => [['nome' => 'Nome', 'tipo' => 'texto']],
        ], $this->bearer($a['token'])))['id'];

        $campos  = $this->dados($this->chamar('GET', "modulos/{$moduloId}", [], $this->bearer($a['token'])))['campos'];
        $campoId = $campos[0]['id'];

        $criado = $this->chamar('POST', "modulos/{$moduloId}/registros", ['dados' => [$campoId => 'Maria']], $this->bearer($a['token']));
        $criado->assertStatus(201);

        $lista = $this->chamar('GET', "modulos/{$moduloId}/registros", [], $this->bearer($a['token']));
        $lista->assertStatus(200);
        $this->assertStringContainsString('Maria', $lista->getJSON());
    }

    // ------------------------------------------------------------------
    // Permissões
    // ------------------------------------------------------------------

    public function testRotaComPermissaoExigeCargo(): void
    {
        // Sem token nenhum, rotas com permissão também respondem 401 (e não 403).
        $this->chamar('GET', 'cargos')->assertStatus(401);

        $a = $this->novaEmpresa('Empresa Cargos');
        $this->chamar('GET', 'cargos', [], $this->bearer($a['token']))->assertStatus(200);
    }
}
