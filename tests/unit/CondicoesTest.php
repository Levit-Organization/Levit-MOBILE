<?php

use App\Services\Automacao\CamposCandidato;
use App\Services\Automacao\Condicoes;
use CodeIgniter\Test\CIUnitTestCase;

/**
 * @internal
 */
final class CondicoesTest extends CIUnitTestCase
{
    private function regra(string $operador, ?string $valor = null, ?string $de = null, string $campo = 'status'): array
    {
        return ['campo' => $campo, 'operador' => $operador, 'valor' => $valor, 'valor_de' => $de];
    }

    private function avaliar(array $regras, array $dados, ?array $anteriores = null, string $logica = 'e'): bool
    {
        return Condicoes::avaliar(['logica' => $logica, 'regras' => $regras], $dados, $anteriores);
    }

    public function testSemRegrasSempreDispara(): void
    {
        $this->assertTrue(Condicoes::avaliar(null, []));
        $this->assertTrue(Condicoes::avaliar(['logica' => 'e', 'regras' => []], []));
    }

    public function testEExigeTodasEOuBastaUma(): void
    {
        $regras = [$this->regra('igual', 'Fechado'), $this->regra('maior', '1000', null, 'valor')];
        $dados  = ['status' => 'Fechado', 'valor' => '500'];

        $this->assertFalse($this->avaliar($regras, $dados));
        $this->assertTrue($this->avaliar($regras, $dados, null, 'ou'));
        $this->assertTrue($this->avaliar($regras, ['status' => 'Fechado', 'valor' => '1500']));
    }

    public function testContemIgnoraMaiusculas(): void
    {
        $this->assertTrue($this->avaliar([$this->regra('contem', 'gmail', null, 'email')], ['email' => 'Ana@GMAIL.com']));
        $this->assertFalse($this->avaliar([$this->regra('nao_contem', 'gmail', null, 'email')], ['email' => 'ana@gmail.com']));
        $this->assertTrue($this->avaliar([$this->regra('nao_contem', 'gmail', null, 'email')], ['email' => 'ana@empresa.com']));
    }

    public function testVazioEPreenchido(): void
    {
        $this->assertTrue($this->avaliar([$this->regra('vazio', null, null, 'tel')], ['tel' => '  ']));
        $this->assertTrue($this->avaliar([$this->regra('vazio', null, null, 'tel')], []));
        $this->assertTrue($this->avaliar([$this->regra('preenchido', null, null, 'tel')], ['tel' => '1199']));
    }

    public function testMaiorMenorComNumerosEDatas(): void
    {
        $this->assertTrue($this->avaliar([$this->regra('maior', '9', null, 'n')], ['n' => '10']), '10 > 9 como número, não como texto');
        $this->assertTrue($this->avaliar([$this->regra('menor', '2026-10-15', null, 'd')], ['d' => '2026-10-01']));
        $this->assertFalse($this->avaliar([$this->regra('maior', '5', null, 'n')], ['n' => '']), 'vazio nunca é maior');
    }

    public function testIgualEmColunaComVariosValoresProcuraNaLista(): void
    {
        $dados = ['produtos' => ['id-1', 'id-2']];

        $this->assertTrue($this->avaliar([$this->regra('igual', 'id-2', null, 'produtos')], $dados));
        $this->assertFalse($this->avaliar([$this->regra('igual', 'id-3', null, 'produtos')], $dados));
        $this->assertTrue($this->avaliar([$this->regra('diferente', 'id-3', null, 'produtos')], $dados));
    }

    public function testMudancasPrecisamDoValorAnterior(): void
    {
        $this->assertTrue($this->avaliar([$this->regra('mudou_para', 'Fechado')], ['status' => 'Fechado'], ['status' => 'Proposta']));
        $this->assertFalse($this->avaliar([$this->regra('mudou_para', 'Fechado')], ['status' => 'Fechado'], null));
        $this->assertTrue($this->avaliar([$this->regra('mudou_de_para', 'Fechado', 'Proposta')], ['status' => 'Fechado'], ['status' => 'Proposta']));
    }

    public function testConverteCondicaoUnicaAntiga(): void
    {
        $this->assertSame(
            ['logica' => 'e', 'regras' => [['campo' => 'c1', 'operador' => 'igual', 'valor' => 'Sim', 'valor_de' => null]]],
            Condicoes::doFormatoAntigo(['campo_condicao_id' => 'c1', 'condicao_operador' => 'igual', 'condicao_valor' => 'Sim'])
        );
        $this->assertNull(Condicoes::doFormatoAntigo(['campo_condicao_id' => null]));
    }

    public function testEtapaDoCandidatoMudouPara(): void
    {
        $antes  = CamposCandidato::dados(['nome' => 'Ana'], 'Triagem');
        $depois = CamposCandidato::dados(['nome' => 'Ana'], 'Entrevista', 'Triagem');

        $this->assertTrue($this->avaliar([$this->regra('mudou_para', 'Entrevista', null, 'etapa')], $depois, $antes));
        $this->assertFalse($this->avaliar([$this->regra('mudou_para', 'Contratado', null, 'etapa')], $depois, $antes));
    }
}
