<?php

use App\Services\Automacao\ValidadorDeAutomacao;
use CodeIgniter\Test\CIUnitTestCase;

/**
 * @internal
 */
final class AutomacaoGatilhosTest extends CIUnitTestCase
{
    private function normalizar(array $dados): array
    {
        return ValidadorDeAutomacao::normalizarGatilhos($dados);
    }

    public function testAceitaVariosGatilhos(): void
    {
        $this->assertSame(
            ['criacao', 'atualizacao', 'exclusao'],
            $this->normalizar(['gatilhos' => ['criacao', 'atualizacao', 'exclusao']])
        );
    }

    public function testRemoveRepetidosMantendoAOrdem(): void
    {
        $this->assertSame(['exclusao', 'criacao'], $this->normalizar(['gatilhos' => ['exclusao', 'criacao', 'exclusao']]));
    }

    public function testFormatoAntigoComGatilhoUnico(): void
    {
        $this->assertSame(['atualizacao'], $this->normalizar(['gatilho' => 'atualizacao']));
    }

    public function testExigePeloMenosUmGatilho(): void
    {
        $this->expectException(DomainException::class);
        $this->normalizar(['gatilhos' => []]);
    }

    public function testRejeitaGatilhoInvalido(): void
    {
        $this->expectException(DomainException::class);
        $this->normalizar(['gatilhos' => ['criacao', 'inexistente']]);
    }

    /** Validação de condições em vagas (campos do candidato: não precisa de banco). */
    private function condicoesDeVaga(array $condicoes, array $gatilhos = ['candidato_mudou_etapa']): ?array
    {
        return (new ValidadorDeAutomacao())->normalizarCondicoes(['condicoes' => $condicoes], 'vaga', true, $gatilhos);
    }

    public function testCondicoesValidasSaoNormalizadas(): void
    {
        $this->assertSame(
            ['logica' => 'ou', 'regras' => [
                ['campo' => 'etapa', 'operador' => 'mudou_para', 'valor' => 'Entrevista', 'valor_de' => null],
                ['campo' => 'email', 'operador' => 'preenchido', 'valor' => null, 'valor_de' => null],
            ]],
            $this->condicoesDeVaga(['logica' => 'ou', 'regras' => [
                ['campo' => 'etapa', 'operador' => 'mudou_para', 'valor' => 'Entrevista', 'valor_de' => 'lixo'],
                ['campo' => 'email', 'operador' => 'preenchido', 'valor' => 'ignorado'],
            ]])
        );
    }

    public function testSemRegrasViraNull(): void
    {
        $this->assertNull($this->condicoesDeVaga(['logica' => 'e', 'regras' => []]));
    }

    public function testMudancaSemGatilhoDeMudancaEhRecusada(): void
    {
        $this->expectException(DomainException::class);
        $this->expectExceptionMessage('Mudou de etapa');
        $this->condicoesDeVaga(['logica' => 'e', 'regras' => [['campo' => 'etapa', 'operador' => 'mudou', 'valor' => null]]], ['candidato_criado']);
    }

    public function testCampoInexistenteEhRecusado(): void
    {
        $this->expectException(DomainException::class);
        $this->condicoesDeVaga(['logica' => 'e', 'regras' => [['campo' => 'salario', 'operador' => 'igual', 'valor' => '1']]]);
    }

    public function testOperadorQueExigeValorSemValorEhRecusado(): void
    {
        $this->expectException(DomainException::class);
        $this->condicoesDeVaga(['logica' => 'e', 'regras' => [['campo' => 'nome', 'operador' => 'contem', 'valor' => '']]]);
    }

    public function testLimiteDeRegras(): void
    {
        $this->expectException(DomainException::class);
        $this->condicoesDeVaga(['logica' => 'e', 'regras' => array_fill(0, 11, ['campo' => 'nome', 'operador' => 'preenchido'])]);
    }
}
