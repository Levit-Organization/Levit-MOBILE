<?php

use App\Services\RecorrenciaService;
use CodeIgniter\Test\CIUnitTestCase;

/**
 * @internal
 */
final class RecorrenciaTest extends CIUnitTestCase
{
    private const CAMPOS = [
        ['id' => 'cli', 'tipo' => 'texto'],
        ['id' => 'resp', 'tipo' => 'responsavel'],
        ['id' => 'venc', 'tipo' => 'prazo'],
        ['id' => 'rep', 'tipo' => 'recorrencia'],
        ['id' => 'obr', 'tipo' => 'selecao', 'opcoes' => ['DAS', 'Folha']],
        ['id' => 'st', 'tipo' => 'selecao', 'opcoes' => ['A fazer', 'Aguardando cliente', 'Entregue']],
    ];

    public function testProximaDataPorPeriodo(): void
    {
        $this->assertSame('2026-10-27', RecorrenciaService::proximaData('2026-10-20', 'semanal'));
        $this->assertSame('2026-11-03', RecorrenciaService::proximaData('2026-10-20', 'quinzenal'));
        $this->assertSame('2026-11-20', RecorrenciaService::proximaData('2026-10-20', 'mensal'));
        $this->assertSame('2027-01-20', RecorrenciaService::proximaData('2026-10-20', 'trimestral'));
        $this->assertSame('2027-10-20', RecorrenciaService::proximaData('2026-10-20', 'anual'));
    }

    public function testFimDeMesNaoPulaParaOMesSeguinte(): void
    {
        $this->assertSame('2027-02-28', RecorrenciaService::proximaData('2027-01-31', 'mensal'));
        $this->assertSame('2028-02-29', RecorrenciaService::proximaData('2028-01-31', 'mensal')); // bissexto
        $this->assertSame('2027-01-31', RecorrenciaService::proximaData('2026-12-31', 'mensal')); // virada de ano
        $this->assertSame('2027-02-28', RecorrenciaService::proximaData('2026-11-30', 'trimestral'));
    }

    public function testConcluirItemQueSeRepeteGeraOProximo(): void
    {
        $antes  = ['cli' => 'Padaria', 'resp' => 'u1', 'venc' => '2026-10-20', 'rep' => 'mensal', 'obr' => 'DAS', 'st' => 'A fazer'];
        $depois = ['st' => 'Entregue'] + $antes;

        $this->assertEquals( // mesmas chaves e valores; a ordem não importa
            ['cli' => 'Padaria', 'resp' => 'u1', 'venc' => '2026-11-20', 'rep' => 'mensal', 'obr' => 'DAS', 'st' => 'A fazer'],
            RecorrenciaService::proximaOcorrencia(self::CAMPOS, $antes, $depois)
        );
    }

    public function testNaoGeraQuandoNaoDeve(): void
    {
        $base = ['cli' => 'Padaria', 'venc' => '2026-10-20', 'rep' => 'mensal', 'st' => 'A fazer'];
        // Não acabou de ser concluído (editou outra coisa).
        $this->assertNull(RecorrenciaService::proximaOcorrencia(self::CAMPOS, $base, ['cli' => 'Padaria Nova'] + $base));
        // Já estava concluído antes.
        $ja = ['st' => 'Entregue'] + $base;
        $this->assertNull(RecorrenciaService::proximaOcorrencia(self::CAMPOS, $ja, ['cli' => 'Outro'] + $ja));
        // Sem repetição.
        $sem = ['rep' => null] + $base;
        $this->assertNull(RecorrenciaService::proximaOcorrencia(self::CAMPOS, $sem, ['st' => 'Entregue'] + $sem));
        // Sem prazo.
        $semPrazo = ['venc' => null] + $base;
        $this->assertNull(RecorrenciaService::proximaOcorrencia(self::CAMPOS, $semPrazo, ['st' => 'Entregue'] + $semPrazo));
    }
}
