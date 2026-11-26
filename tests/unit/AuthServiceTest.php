<?php

use App\Services\AuthService;
use CodeIgniter\Test\CIUnitTestCase;

/**
 * Partes da autenticação que não dependem do banco.
 *
 * @internal
 */
final class AuthServiceTest extends CIUnitTestCase
{
    private function paraBooleano(mixed $valor): bool
    {
        return (new ReflectionMethod(\App\Services\Auth\ContextoDeAutenticacao::class, 'paraBooleano'))->invoke(null, $valor);
    }

    public function testDocumentoFicaSoComDigitos(): void
    {
        $this->assertSame('12345678000190', AuthService::documentoOuNulo('12.345.678/0001-90'));
        $this->assertSame('12345678900', AuthService::documentoOuNulo(' 123.456.789-00 '));
    }

    public function testDocumentoVazioViraNulo(): void
    {
        $this->assertNull(AuthService::documentoOuNulo(null));
        $this->assertNull(AuthService::documentoOuNulo(''));
        $this->assertNull(AuthService::documentoOuNulo(' ./- '));
    }

    public function testBooleanoDoBancoEmTodosOsFormatos(): void
    {
        foreach ([true, 1, '1', 't', 'true'] as $verdadeiro) {
            $this->assertTrue($this->paraBooleano($verdadeiro), var_export($verdadeiro, true));
        }
        foreach ([false, 0, '0', 'f', 'false', null, '', 'TRUE', 2] as $falso) {
            $this->assertFalse($this->paraBooleano($falso), var_export($falso, true));
        }
    }

    public function testContextoComIdsQueNaoSaoUuidNemChegaAoBanco(): void
    {
        $servico = new AuthService();
        $uuid    = '0190a000-0000-7000-8000-000000000001';

        $this->assertNull($servico->contextoDeAutenticacao('nao-e-uuid', $uuid, 'jti'));
        $this->assertNull($servico->contextoDeAutenticacao($uuid, '', 'jti'));
        $this->assertNull($servico->contextoDeAutenticacao($uuid . 'x', $uuid, 'jti'));
    }
}
