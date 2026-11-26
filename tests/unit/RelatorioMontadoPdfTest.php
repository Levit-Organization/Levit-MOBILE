<?php

use App\Services\RelatorioService;
use CodeIgniter\Test\CIUnitTestCase;

/**
 * @internal
 */
final class RelatorioMontadoPdfTest extends CIUnitTestCase
{
    private function dados(array $resultado = [], array $extra = []): array
    {
        return $extra + [
            'titulo'    => 'Vendas por cidade',
            'empresa'   => 'Empresa <Teste>',
            'gerado_em' => '26/10/2026 às 18:00',
            'periodo'   => 'Itens criados de 01/10/2026 a 31/10/2026',
            'filtros'   => ['Cliente › Cidade contém “Recife”', 'Status é igual a “Aberto”'],
            'logica'    => 'e',
            'resultado' => $resultado + [
                'tabela'       => 'Pedidos',
                'agrupado_por' => 'Cliente › Cidade',
                'operacao'     => 'somar',
                'coluna_valor' => 'Valor',
                'tipo_grupo'   => 'texto',
                'grupos'       => [['rotulo' => 'Recife', 'valor' => 1550.0, 'itens' => 2], ['rotulo' => 'Olinda', 'valor' => 450.0, 'itens' => 1]],
                'total_itens'  => 3,
                'cortados'     => 0,
            ],
        ];
    }

    public function testMostraCriteriosGruposPercentuaisETotal(): void
    {
        $html = RelatorioService::htmlDoAgrupamento($this->dados());

        $this->assertStringContainsString('<h1>Vendas por cidade</h1>', $html);
        $this->assertStringContainsString('Empresa &lt;Teste&gt;', $html, 'texto do usuário escapado');
        $this->assertStringContainsString('Soma de Valor', $html);
        $this->assertStringContainsString('Itens que atendem a todos destes:', $html);
        $this->assertStringContainsString('<li>Cliente › Cidade contém “Recife”</li>', $html);
        $this->assertStringContainsString('1.550', $html);
        $this->assertStringContainsString('77,5%', $html);
        $this->assertStringContainsString('Total dos grupos', $html);
        $this->assertStringContainsString('2.000', $html);
    }

    public function testMesesPorExtensoEMediaSemPercentual(): void
    {
        $html = RelatorioService::htmlDoAgrupamento($this->dados([
            'operacao'   => 'media',
            'tipo_grupo' => 'prazo',
            'grupos'     => [['rotulo' => '2026-09', 'valor' => 10.25, 'itens' => 4], ['rotulo' => 'Sem valor', 'valor' => null, 'itens' => 1]],
        ], ['filtros' => []]));

        $this->assertStringContainsString('Setembro de 2026', $html);
        $this->assertStringContainsString('10,25', $html);
        $this->assertStringNotContainsString('% do total', $html);
        $this->assertStringNotContainsString('Total dos grupos', $html);
        $this->assertStringContainsString('Nenhum: entram todos os itens.', $html);
    }

    public function testSemGruposEGeraUmPdfDeVerdade(): void
    {
        $html = RelatorioService::htmlDoAgrupamento($this->dados(['grupos' => [], 'total_itens' => 0]));
        $this->assertStringContainsString('Nenhum item atende aos critérios', $html);

        $pdf = RelatorioService::renderizar(RelatorioService::htmlDoAgrupamento($this->dados()));
        $this->assertStringStartsWith('%PDF-', $pdf);
    }
}
