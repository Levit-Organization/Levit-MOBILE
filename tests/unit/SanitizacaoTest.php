<?php

use App\Services\Automacao\AutomacaoExecutor;
use App\Services\BackupService;
use CodeIgniter\Test\CIUnitTestCase;

/**
 * @internal
 */
final class SanitizacaoTest extends CIUnitTestCase
{
    public function testCelulaComFormulaViraTexto(): void
    {
        $this->assertSame("'=HYPERLINK(\"http://x\")", BackupService::celulaSegura('=HYPERLINK("http://x")'));
        $this->assertSame("'@SUM(A1)", BackupService::celulaSegura('@SUM(A1)'));
        $this->assertSame("'+cmd", BackupService::celulaSegura('+cmd'));
    }

    public function testNumerosETextoComumFicamIguais(): void
    {
        $this->assertSame('-50', BackupService::celulaSegura('-50'));
        $this->assertSame('3.5', BackupService::celulaSegura('3.5'));
        $this->assertSame('Ana Souza', BackupService::celulaSegura('Ana Souza'));
        $this->assertSame('', BackupService::celulaSegura(''));
    }

    public function testCorpoDoEmailEscapaHtmlEMantemQuebras(): void
    {
        $html = AutomacaoExecutor::corpoDoEmail("Olá <b>Ana</b>\n<a href=\"http://golpe\">clique</a>");
        $this->assertStringNotContainsString('<a href', $html);
        $this->assertStringNotContainsString('<b>', $html);
        $this->assertStringContainsString('&lt;b&gt;Ana&lt;/b&gt;<br>', $html);
    }
}
