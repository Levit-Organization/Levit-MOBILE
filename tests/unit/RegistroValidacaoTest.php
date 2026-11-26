<?php

use App\Services\RegistroService;
use App\Services\Registros\ValidadorDeDados;
use CodeIgniter\Test\CIUnitTestCase;

/**
 * Validação dos dados de um item: tipos simples, opções, repetição, o
 * formato das colunas de ligação e (com o banco trocado por listas em
 * memória) a conferência dos itens ligados e das pessoas.
 *
 * @internal
 */
final class RegistroValidacaoTest extends CIUnitTestCase
{
    private const CAMPOS = [
        ['id' => 'nome', 'nome' => 'Nome', 'tipo' => 'texto'],
        ['id' => 'valor', 'nome' => 'Valor', 'tipo' => 'numero'],
        ['id' => 'data', 'nome' => 'Data', 'tipo' => 'data'],
        ['id' => 'prazo', 'nome' => 'Prazo', 'tipo' => 'prazo'],
        ['id' => 'status', 'nome' => 'Status', 'tipo' => 'selecao', 'opcoes' => ['A fazer', 'Feito']],
        ['id' => 'repete', 'nome' => 'Repete', 'tipo' => 'recorrencia'],
        ['id' => 'dono', 'nome' => 'Dono', 'tipo' => 'responsavel'],
        ['id' => 'cliente', 'nome' => 'Cliente', 'tipo' => 'vinculo', 'modulo_vinculado_id' => 'cli', 'opcoes' => []],
        ['id' => 'tags', 'nome' => 'Tags', 'tipo' => 'vinculo', 'modulo_vinculado_id' => 'tag', 'opcoes' => ['multiplo' => true]],
    ];

    private const ANA = '0190a000-0000-7000-8000-0000000000aa';
    private const CLIENTE = '0190a000-0000-7000-8000-0000000000c1';
    private const TAG_1 = '0190a000-0000-7000-8000-0000000000f1';
    private const TAG_2 = '0190a000-0000-7000-8000-0000000000f2';

    /** Validador com o banco trocado por listas em memória; guarda as consultas feitas. */
    private function validador(): ValidadorDeDados
    {
        return new class () extends ValidadorDeDados {
            /** @var list<array> */
            public array $consultas = [];

            protected function idsExistentes(string $moduloId, array $ids): array
            {
                $this->consultas[] = ['itens', $moduloId, $ids];
                $existentes = ['cli' => [RegistroValidacaoTest::itens()[0]], 'tag' => array_slice(RegistroValidacaoTest::itens(), 1)];

                return array_values(array_intersect($ids, $existentes[$moduloId] ?? []));
            }

            protected function pessoaExiste(string $usuarioId, string $moduloId): bool
            {
                $this->consultas[] = ['pessoa', $usuarioId, $moduloId];

                return $usuarioId === RegistroValidacaoTest::itens()[3];
            }
        };
    }

    /** @return list<string> cliente, tag 1, tag 2 e a pessoa da equipe */
    public static function itens(): array
    {
        return [self::CLIENTE, self::TAG_1, self::TAG_2, self::ANA];
    }

    private function validar(array $dados, array $campos = self::CAMPOS, ?ValidadorDeDados $validador = null): array
    {
        return ($validador ?? $this->validador())->validar($campos, ['dados' => $dados], 'mod');
    }

    private function assertRecusa(string $mensagem, array $dados): void
    {
        try {
            $this->validar($dados);
            $this->fail('Deveria recusar: ' . json_encode($dados));
        } catch (DomainException $e) {
            $this->assertStringContainsString($mensagem, $e->getMessage());
        }
    }

    public function testConverteOsTiposEDescartaVazios(): void
    {
        $this->assertSame(
            ['nome' => '123', 'valor' => 10.5, 'data' => '2026-02-28', 'prazo' => '2026-12-31', 'status' => 'Feito', 'repete' => 'mensal'],
            $this->validar([
                'nome' => 123, 'valor' => '10.5', 'data' => '2026-02-28', 'prazo' => '2026-12-31', 'status' => 'Feito',
                'repete' => 'mensal', 'dono' => '', 'cliente' => null, 'tags' => [], 'desconhecida' => 'x',
            ])
        );
        $this->assertSame(['valor' => 7], $this->validar(['valor' => '7']));
    }

    public function testSemDadosDevolveVazio(): void
    {
        $this->assertSame([], $this->validador()->validar(self::CAMPOS, [], 'mod'));
    }

    public function testLigacoesEPessoaQueExistem(): void
    {
        $validador = $this->validador();

        $this->assertSame(
            ['dono' => self::ANA, 'cliente' => self::CLIENTE, 'tags' => [self::TAG_1, self::TAG_2]],
            $this->validar(['dono' => self::ANA, 'cliente' => [strtoupper(self::CLIENTE)], 'tags' => [self::TAG_1, self::TAG_2, ' ' . self::TAG_1]], self::CAMPOS, $validador)
        );

        // Conferidos uma vez por requisição: validar de novo não consulta o banco.
        $this->validar(['dono' => self::ANA, 'cliente' => self::CLIENTE], self::CAMPOS, $validador);
        $this->assertSame([
            ['pessoa', self::ANA, 'mod'],
            ['itens', 'cli', [self::CLIENTE]],
            ['itens', 'tag', [self::TAG_1, self::TAG_2]],
        ], $validador->consultas);
    }

    public function testLigacaoParaItemQueNaoExisteMais(): void
    {
        $this->expectException(DomainException::class);
        $this->expectExceptionMessage("A coluna 'Tags' aponta para um item que não existe mais na tabela ligada.");
        $this->validar(['tags' => [self::TAG_1, self::CLIENTE]]);
    }

    public function testPessoaDeOutraEmpresa(): void
    {
        $this->expectException(DomainException::class);
        $this->expectExceptionMessage("A coluna 'Dono' precisa ser uma pessoa da equipe.");
        $this->validar(['dono' => self::CLIENTE]);
    }

    public function testImportacaoConfereAsLigacoesDeUmaVez(): void
    {
        $validador = $this->validador();
        $validador->preconferirLigacoes(self::CAMPOS, [
            ['dados' => ['cliente' => self::CLIENTE, 'tags' => [self::TAG_1]]],
            ['dados' => ['cliente' => 'lixo', 'tags' => [strtoupper(self::TAG_2), self::TAG_1]]],
            ['dados' => 'sem dados'],
        ]);
        $this->assertSame([['itens', 'cli', [self::CLIENTE]], ['itens', 'tag', [self::TAG_1, self::TAG_2]]], $validador->consultas);

        // Depois disso, validar cada linha não consulta mais os itens.
        $this->validar(['cliente' => self::CLIENTE, 'tags' => [self::TAG_2]], self::CAMPOS, $validador);
        $this->assertCount(2, $validador->consultas);
    }

    public function testRecusaValoresInvalidos(): void
    {
        $this->assertRecusa("A coluna 'Valor' precisa ser um número.", ['valor' => '10,5']);
        $this->assertRecusa("A coluna 'Data' precisa ser uma data válida", ['data' => '2026-02-30']);
        $this->assertRecusa("A coluna 'Prazo' precisa ser uma data válida", ['prazo' => '31/12/2026']);
        $this->assertRecusa("A coluna 'Status' precisa ser uma das opções válidas.", ['status' => 'feito']);
        $this->assertRecusa("A coluna 'Repete' precisa ser uma das repetições", ['repete' => 'diaria']);
        // Responsável que nem é um identificador válido: recusado sem consultar o banco.
        $this->assertRecusa("A coluna 'Dono' precisa ser uma pessoa da equipe.", ['dono' => 'ana']);
    }

    public function testFormatoDasLigacoes(): void
    {
        $a = '0190a000-0000-7000-8000-00000000000a';
        $b = '0190a000-0000-7000-8000-00000000000b';

        $this->assertRecusa("A coluna 'Cliente' liga um item só.", ['cliente' => [$a, $b]]);
        $this->assertRecusa("A coluna 'Cliente' precisa apontar para itens da tabela ligada.", ['cliente' => 'abc']);
        $this->assertRecusa("A coluna 'Tags' precisa apontar para itens da tabela ligada.", ['tags' => [$a, 'nao-e-uuid']]);

        $muitos = [];
        for ($i = 0; $i <= RegistroService::MAX_LIGADOS; $i++) {
            $muitos[] = sprintf('0190a000-0000-7000-8000-%012d', $i);
        }
        $this->assertRecusa("A coluna 'Tags' liga no máximo " . RegistroService::MAX_LIGADOS . ' itens.', ['tags' => $muitos]);

        // Só espaços e repetidos: nada ligado.
        $this->assertSame([], $this->validar(['tags' => [' ', ''], 'cliente' => '  ']));
    }

    public function testCampoDeTitulo(): void
    {
        $this->assertSame('b', RegistroService::campoDeTitulo([['id' => 'a', 'tipo' => 'numero'], ['id' => 'b', 'tipo' => 'texto'], ['id' => 'c', 'tipo' => 'texto']]));
        $this->assertSame('a', RegistroService::campoDeTitulo([['id' => 'a', 'tipo' => 'numero'], ['id' => 'b', 'tipo' => 'data']]));
        $this->assertNull(RegistroService::campoDeTitulo([]));
    }
}
