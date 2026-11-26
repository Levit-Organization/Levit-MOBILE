<?php

use App\Services\ArquivoService;
use CodeIgniter\Test\CIUnitTestCase;

/**
 * @internal
 */
final class ArquivoTipoTest extends CIUnitTestCase
{
    public function testTipoAceitoFicaComAExtensaoDoConteudo(): void
    {
        $this->assertSame('pdf', ArquivoService::extensaoParaGuardar('pdf', 'pdf'));
        $this->assertSame('png', ArquivoService::extensaoParaGuardar('PNG', 'png'));
        // Imagem PNG com nome .jpg: vale o que o conteúdo é.
        $this->assertSame('png', ArquivoService::extensaoParaGuardar('jpg', 'png'));
    }

    public function testFormatosQueParecemOutroPeloConteudo(): void
    {
        $this->assertSame('docx', ArquivoService::extensaoParaGuardar('docx', 'zip'));
        $this->assertSame('xlsx', ArquivoService::extensaoParaGuardar('xlsx', 'zip'));
        $this->assertSame('csv', ArquivoService::extensaoParaGuardar('csv', 'txt'));
        $this->assertSame('xls', ArquivoService::extensaoParaGuardar('xls', 'doc'));
    }

    public function testConteudoPerigosoNaoPassaNemComNomeAceito(): void
    {
        $this->assertNull(ArquivoService::extensaoParaGuardar('pdf', 'exe'));
        $this->assertNull(ArquivoService::extensaoParaGuardar('txt', 'html'));
        $this->assertNull(ArquivoService::extensaoParaGuardar('png', 'svg'));
        $this->assertNull(ArquivoService::extensaoParaGuardar('zip', 'zip'));
        $this->assertNull(ArquivoService::extensaoParaGuardar('pdf', ''));
    }
}
