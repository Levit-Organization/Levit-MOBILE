<?php

use App\Services\ModuloService;
use CodeIgniter\Test\CIUnitTestCase;

/**
 * Validação e formato de gravação das colunas de uma tabela (sem banco).
 *
 * @internal
 */
final class ModuloColunasTest extends CIUnitTestCase
{
    private function validar(array $campo): void
    {
        $colunas = (new ReflectionProperty(ModuloService::class, 'colunas'))->getValue(new ModuloService());
        (new ReflectionMethod($colunas, 'validar'))->invoke($colunas, $campo, 'emp-1', 'cargo-1', true);
    }

    private function paraGravar(array $campo, int $ordem = 0): array
    {
        $colunas = (new ReflectionProperty(ModuloService::class, 'colunas'))->getValue(new ModuloService());

        return (new ReflectionMethod($colunas, 'paraGravar'))->invoke($colunas, 'mod-1', $campo, $ordem);
    }

    private function erroAoValidar(array $campo): string
    {
        try {
            $this->validar($campo);
        } catch (DomainException $e) {
            return $e->getMessage();
        }

        $this->fail('A coluna deveria ter sido recusada.');
    }

    public function testColunasValidasPassam(): void
    {
        $this->validar(['nome' => 'Cliente', 'tipo' => 'texto']);
        $this->validar(['nome' => 'Status', 'tipo' => 'selecao', 'opcoes' => ['Aberto']]);
        $this->assertTrue(true);
    }

    public function testColunaPrecisaDeNome(): void
    {
        $this->assertSame('Toda coluna precisa de um nome.', $this->erroAoValidar(['nome' => '', 'tipo' => 'texto']));
    }

    public function testTipoInvalidoOuAusente(): void
    {
        $this->assertSame("Tipo de coluna inválida: 'imagem'.", $this->erroAoValidar(['nome' => 'Foto', 'tipo' => 'imagem']));
        $this->assertSame("Tipo de coluna inválida: '(vazio)'.", $this->erroAoValidar(['nome' => 'Foto']));
    }

    public function testListaDeOpcoesPrecisaDeOpcao(): void
    {
        $this->assertSame(
            'Colunas do tipo lista de opções precisam de ao menos uma opção.',
            $this->erroAoValidar(['nome' => 'Status', 'tipo' => 'selecao', 'opcoes' => []])
        );
    }

    public function testLigacaoSemTabelaDeDestino(): void
    {
        $this->assertSame(
            'Escolha a tabela que a coluna “Cliente” vai ligar.',
            $this->erroAoValidar(['nome' => 'Cliente', 'tipo' => 'vinculo', 'modulo_vinculado_id' => ''])
        );
    }

    public function testColunaComumGuardaAsOpcoesComoVieram(): void
    {
        $this->assertSame(
            ['modulo_id' => 'mod-1', 'nome' => 'Status', 'tipo' => 'selecao', 'opcoes' => ['A', 'B'], 'ordem' => 2, 'modulo_vinculado_id' => null],
            $this->paraGravar(['nome' => 'Status', 'tipo' => 'selecao', 'opcoes' => ['A', 'B'], 'modulo_vinculado_id' => 'ignorado'], 2)
        );
        $this->assertNull($this->paraGravar(['nome' => 'Nome', 'tipo' => 'texto'])['opcoes']);
    }

    public function testLigacaoGuardaDestinoESeAceitaVarios(): void
    {
        $this->assertSame(
            ['modulo_id' => 'mod-1', 'nome' => 'Cliente', 'tipo' => 'vinculo', 'opcoes' => ['multiplo' => true], 'ordem' => 0, 'modulo_vinculado_id' => 'mod-2'],
            $this->paraGravar(['nome' => 'Cliente', 'tipo' => 'vinculo', 'opcoes' => ['multiplo' => 1, 'outra' => 'x'], 'modulo_vinculado_id' => 'mod-2'])
        );
        $this->assertSame(['multiplo' => false], $this->paraGravar(['nome' => 'Cliente', 'tipo' => 'vinculo', 'modulo_vinculado_id' => 'mod-2'])['opcoes']);
    }
}
