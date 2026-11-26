<?php

use App\Services\Indicadores\Agrupamento;
use App\Services\IndicadoresService;
use CodeIgniter\Test\CIUnitTestCase;

/**
 * Cálculos puros dos indicadores que o IndicadoresTest não cobre:
 * comparação, períodos, corte de grupos e rótulos.
 *
 * @internal
 */
final class IndicadoresCalculoTest extends CIUnitTestCase
{
    public function testCompararJuntaAtualAnteriorEVariacao(): void
    {
        $this->assertSame(['atual' => 3, 'anterior' => 2, 'variacao' => 50.0], IndicadoresService::comparar(3, 2));
        $this->assertSame(['atual' => 1.5, 'anterior' => 0, 'variacao' => null], IndicadoresService::comparar(1.5, 0));
        // Anterior negativo: a variação usa o valor absoluto da base.
        $this->assertSame(50.0, IndicadoresService::variacao(-2, -4));
        $this->assertSame(33.3, IndicadoresService::variacao(4, 3));
    }

    public function testDescreverPeriodoMostraOFimInclusivo(): void
    {
        $this->assertSame(
            ['inicio' => '2026-10-01', 'fim' => '2026-10-31'],
            IndicadoresService::descreverPeriodo(new DateTimeImmutable('2026-10-01'), new DateTimeImmutable('2026-11-01'))
        );
    }

    public function testPeriodoAnteriorDeUmMes(): void
    {
        [$inicio, $fim] = IndicadoresService::periodoAnterior(new DateTimeImmutable('2026-10-01'), new DateTimeImmutable('2026-11-01'));
        $this->assertSame('2026-08-31', $inicio->format('Y-m-d'));
        $this->assertSame('2026-10-01', $fim->format('Y-m-d'));
    }

    public function testMontarGruposCortaEmCinquenta(): void
    {
        $linhas = [];
        for ($i = 1; $i <= 55; $i++) {
            $linhas[] = ['grupo' => "g{$i}", 'total' => $i, 'soma' => null, 'media' => null];
        }
        $r = IndicadoresService::montarGrupos($linhas, 'contar', 'texto');

        $this->assertCount(50, $r['grupos']);
        $this->assertSame(5, $r['cortados']);
        $this->assertSame('g55', $r['grupos'][0]['rotulo']);
        // O total conta todos os itens, inclusive os dos grupos cortados.
        $this->assertSame(array_sum(range(1, 55)), $r['total_itens']);
        $this->assertArrayNotHasKey('chave', $r['grupos'][0]);
    }

    public function testMediaSemValorEPessoaRemovida(): void
    {
        $uuid = '0190a000-0000-7000-8000-000000000009';
        $r    = IndicadoresService::montarGrupos([
            ['grupo' => $uuid, 'total' => 2, 'soma' => '10', 'media' => null],
            ['grupo' => null, 'total' => 1, 'soma' => '3', 'media' => '3.456'],
        ], 'media', 'responsavel');

        $this->assertSame(['Pessoa removida', 'Sem responsável'], array_column($r['grupos'], 'rotulo'));
        $this->assertSame([null, 3.46], array_column($r['grupos'], 'valor'));
        $this->assertSame([2, 1], array_column($r['grupos'], 'itens'));
    }

    public function testDataDoPeriodoDoRelatorioMontado(): void
    {
        $this->assertSame('2026-02-28', Agrupamento::dataValida('2026-02-28'));
        $this->assertNull(Agrupamento::dataValida('2026-02-30'));
        $this->assertNull(Agrupamento::dataValida('2026-2-1'));
        $this->assertNull(Agrupamento::dataValida(' 2026-01-01'));
        $this->assertNull(Agrupamento::dataValida(null));
        $this->assertNull(Agrupamento::dataValida(20260101));
    }

    public function testEmpateOrdenaPeloRotulo(): void
    {
        $r = IndicadoresService::montarGrupos([
            ['grupo' => 'b', 'total' => 2, 'soma' => null, 'media' => null],
            ['grupo' => 'a', 'total' => 2, 'soma' => null, 'media' => null],
            ['grupo' => '', 'total' => 9, 'soma' => null, 'media' => null],
        ], 'contar', 'selecao');

        // Vazio ('') vira "Sem valor", mas só o nulo vai para o fim.
        $this->assertSame(['Sem valor', 'a', 'b'], array_column($r['grupos'], 'rotulo'));
    }
}
