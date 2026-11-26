<?php

use App\Services\Email\ModeloRedefinicaoSenha;
use CodeIgniter\Test\CIUnitTestCase;

/**
 * @internal
 */
final class ModeloRedefinicaoSenhaTest extends CIUnitTestCase
{
    public function testTemLinkValidadeEPrimeiroNome(): void
    {
        $html = ModeloRedefinicaoSenha::html(['nome' => 'Ana Souza', 'link' => 'https://app/reset-password?token=abc', 'minutos_validade' => 60]);

        $this->assertStringContainsString('href="https://app/reset-password?token=abc"', $html);
        $this->assertStringContainsString('Oi, Ana!', $html);
        $this->assertStringContainsString('vale por 60 minutos', $html);
    }

    public function testNomeEhEscapado(): void
    {
        $html = ModeloRedefinicaoSenha::html(['nome' => '<script>x</script>', 'link' => 'https://app', 'minutos_validade' => 60]);

        $this->assertStringNotContainsString('<script>', $html);
        $this->assertStringContainsString('&lt;script&gt;', $html);
    }
}
