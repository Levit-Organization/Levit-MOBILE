<?php

use App\Exceptions\AcessoNegadoException;
use App\Services\Pesquisa\Filtros;
use CodeIgniter\Test\CIUnitTestCase;

/**
 * @internal
 */
final class FiltrosTest extends CIUnitTestCase
{
    // Pedidos (P) → Clientes (C) pela coluna "Cliente"; Pedidos → Produtos (vários).
    private const P = '00000000-0000-7000-8000-00000000000a';
    private const C = '00000000-0000-7000-8000-00000000000b';
    private const X = '00000000-0000-7000-8000-00000000000c'; // tabela sem acesso

    private const ITEM = '0192f0a0-0000-7000-8000-000000000001';

    private function campos(): array
    {
        return [
            'nome'    => ['id' => 'nome', 'modulo_id' => self::P, 'nome' => 'Pedido', 'tipo' => 'texto', 'modulo_vinculado_id' => null],
            'valor'   => ['id' => 'valor', 'modulo_id' => self::P, 'nome' => 'Valor', 'tipo' => 'numero', 'modulo_vinculado_id' => null],
            'entrega' => ['id' => 'entrega', 'modulo_id' => self::P, 'nome' => 'Entrega', 'tipo' => 'data', 'modulo_vinculado_id' => null],
            'status'  => ['id' => 'status', 'modulo_id' => self::P, 'nome' => 'Status', 'tipo' => 'selecao', 'modulo_vinculado_id' => null],
            'cliente' => ['id' => 'cliente', 'modulo_id' => self::P, 'nome' => 'Cliente', 'tipo' => 'vinculo', 'modulo_vinculado_id' => self::C],
            'secreto' => ['id' => 'secreto', 'modulo_id' => self::P, 'nome' => 'Secreto', 'tipo' => 'vinculo', 'modulo_vinculado_id' => self::X],
            'cidade'  => ['id' => 'cidade', 'modulo_id' => self::C, 'nome' => 'Cidade', 'tipo' => 'texto', 'modulo_vinculado_id' => null],
        ];
    }

    private function filtros(): Filtros
    {
        return new Filtros($this->campos(), static fn (string $id): bool => $id !== self::X);
    }

    /** SQL com os parâmetros já no lugar, para ler a condição inteira. */
    private function sql(array $filtros, string $modulo = self::P): string
    {
        [$sql, $binds] = $this->filtros()->paraSql($filtros, $modulo);
        $troca         = [];
        foreach ($binds as $nome => $valor) {
            $troca[":{$nome}:"] = "'" . str_replace("'", "''", $valor) . "'";
        }

        return strtr($sql, $troca);
    }

    private function regra(string $campo, string $operador, ?string $valor = null, array $extra = []): array
    {
        return ['logica' => 'e', 'regras' => [['campo' => $campo, 'operador' => $operador, 'valor' => $valor] + $extra]];
    }

    public function testSemRegrasNaoFiltraNada(): void
    {
        $this->assertSame(['TRUE', []], $this->filtros()->paraSql(['logica' => 'e', 'regras' => []], self::P));
    }

    public function testValoresVaoComoParametrosNomeados(): void
    {
        [$sql, $binds] = $this->filtros()->paraSql($this->regra('nome', 'contem', "O'Brien"), self::P);

        $this->assertStringNotContainsString('Brien', $sql);
        $this->assertSame(['f1' => 'nome', 'f2' => "%O'Brien%"], $binds);
        $this->assertSame('(r.dados->>:f1:) ILIKE :f2:', $sql);
    }

    public function testContemTrataCuringasComoTexto(): void
    {
        $this->assertStringContainsString("ILIKE '%50\\%\\_off%'", $this->sql($this->regra('nome', 'contem', '50%_off')));
    }

    public function testNumeroComparaComoNumeroEIgnoraTextoGuardado(): void
    {
        $sql = $this->sql($this->regra('valor', 'maior', '1000'));

        $this->assertStringContainsString("jsonb_typeof(r.dados->'valor') = 'number'", $sql);
        $this->assertStringContainsString("> '1000'::numeric", $sql);
    }

    public function testEntreAceitaOsLimitesEmQualquerOrdem(): void
    {
        $sql = $this->sql($this->regra('entrega', 'entre', '2026-12-31', ['valor_ate' => '2026-01-01']));

        $this->assertStringContainsString("BETWEEN SYMMETRIC '2026-12-31' AND '2026-01-01'", $sql);
    }

    public function testColunasDoSistemaComparamPeloDia(): void
    {
        $this->assertSame("r.criado_em::date < '2026-10-01'::date", $this->sql($this->regra('criado_em', 'menor', '2026-10-01')));
    }

    public function testDiferenteEVazioIncluemQuemNaoTemValor(): void
    {
        $this->assertStringContainsString('IS NULL OR', $this->sql($this->regra('status', 'diferente', 'Fechado')));
        $this->assertSame("((r.dados->>'nome') IS NULL OR btrim((r.dados->>'nome')) = '')", $this->sql($this->regra('nome', 'vazio')));
        $this->assertStringStartsWith('NOT ', $this->sql($this->regra('nome', 'preenchido')));
    }

    public function testGruposAninhadosComEOu(): void
    {
        $sql = $this->sql([
            'logica' => 'ou',
            'regras' => [
                ['campo' => 'status', 'operador' => 'igual', 'valor' => 'Fechado'],
                ['logica' => 'e', 'regras' => [
                    ['campo' => 'valor', 'operador' => 'maior', 'valor' => '10'],
                    ['campo' => 'nome', 'operador' => 'preenchido'],
                ]],
            ],
        ]);

        $this->assertMatchesRegularExpression('/^\(.+ OR \(.+ AND .+\)\)$/', $sql);
    }

    public function testLigacaoDeIdaOlhaAColunaDoItemLigado(): void
    {
        $sql = $this->sql($this->regra('cidade', 'igual', 'Recife', ['via' => [['campo' => 'cliente', 'sentido' => 'ida']]]));

        $this->assertStringStartsWith('EXISTS (SELECT 1 FROM registro v1 WHERE v1.modulo_id = \'' . self::C . "'", $sql);
        $this->assertStringContainsString("v1.id IN (SELECT e::uuid FROM jsonb_array_elements_text(CASE jsonb_typeof(r.dados->'cliente')", $sql);
        $this->assertStringContainsString("lower((v1.dados->>'cidade')) = lower('Recife')", $sql);
    }

    public function testLigacaoDeVoltaProcuraQuemApontaParaOItem(): void
    {
        // Clientes com algum pedido de valor > 500.
        $sql = $this->sql($this->regra('valor', 'maior', '500', ['via' => [['campo' => 'cliente', 'sentido' => 'volta']]]), self::C);

        $this->assertStringContainsString("v1.modulo_id = '" . self::P . "'", $sql);
        $this->assertStringContainsString("v1.dados @> jsonb_build_object('cliente'::text, r.id::text)", $sql);
        $this->assertStringContainsString("jsonb_build_array(r.id::text)", $sql);
    }

    public function testSemColunaPerguntaSeHaItemLigadoENenhumNega(): void
    {
        $semPedido = $this->sql(['logica' => 'e', 'regras' => [['operador' => 'vazio', 'via' => [['campo' => 'cliente', 'sentido' => 'volta']]]]], self::C);
        $this->assertStringStartsWith('NOT EXISTS', $semPedido);

        $nenhum = $this->sql($this->regra('cidade', 'igual', 'Recife', ['via' => [['campo' => 'cliente']], 'quantificador' => 'nenhum']));
        $this->assertStringStartsWith('NOT EXISTS', $nenhum);
    }

    public function testIgualEmColunaDeLigacaoProcuraOItemNaListaOuSozinho(): void
    {
        $sql = $this->sql($this->regra('cliente', 'igual', self::ITEM));

        $this->assertStringContainsString("r.dados @> jsonb_build_object('cliente'::text, '" . self::ITEM . "'::text)", $sql);
        $this->assertStringContainsString("jsonb_build_array('" . self::ITEM . "'::text)", $sql);
    }

    public function testRecusaColunaDeOutraTabelaSemLigacao(): void
    {
        $this->expectException(\DomainException::class);
        $this->expectExceptionMessage('não é desta tabela');
        $this->filtros()->paraSql($this->regra('cidade', 'igual', 'Recife'), self::P);
    }

    public function testRecusaLigacaoQueNaoParteDaTabela(): void
    {
        $this->expectException(\DomainException::class);
        $this->filtros()->paraSql($this->regra('cidade', 'igual', 'Recife', ['via' => [['campo' => 'cliente', 'sentido' => 'ida']]]), self::C);
    }

    public function testRecusaTabelaLigadaSemAcesso(): void
    {
        $this->expectException(AcessoNegadoException::class);
        $this->filtros()->paraSql(['logica' => 'e', 'regras' => [['operador' => 'preenchido', 'via' => [['campo' => 'secreto']]]]], self::P);
    }

    public function testRecusaColunaDesconhecidaEOperadorQueNaoCombina(): void
    {
        foreach ([
            $this->regra("nome'); DROP TABLE registro; --", 'igual', 'x'),
            $this->regra('nome', 'maior', '10'),
            $this->regra('status', 'contem', 'Fe'),
            $this->regra('valor', 'igual', 'dez'),
            $this->regra('entrega', 'maior', '31/12/2026'),
            $this->regra('cliente', 'igual', 'não é um id'),
            $this->regra('nome', 'igual', ''),
            ['logica' => 'xor', 'regras' => []],
        ] as $filtro) {
            try {
                $this->filtros()->paraSql($filtro, self::P);
                $this->fail('Deveria recusar: ' . json_encode($filtro));
            } catch (\DomainException) {
                $this->addToAssertionCount(1);
            }
        }
    }

    public function testLimitaQuantidadeDeRegras(): void
    {
        $regras = array_fill(0, Filtros::MAX_REGRAS + 1, ['campo' => 'nome', 'operador' => 'preenchido']);

        $this->expectException(\DomainException::class);
        $this->filtros()->paraSql(['logica' => 'e', 'regras' => $regras], self::P);
    }

    public function testDescreveOsFiltrosEmPortugues(): void
    {
        $filtros = new Filtros($this->campos(), static fn () => true, [self::P => 'Pedidos', self::C => 'Clientes']);
        $nome    = static fn (array $campo, string $valor) => $campo['tipo'] === 'vinculo' ? '“Padaria Sol”' : $valor;

        $this->assertSame([
            'Cliente › Cidade contém “Recife”',
            'Valor entre 10 e 1.500,5',
            'Entrega antes de 31/12/2026',
            'Criado em depois de 01/10/2026',
            'Cliente é “Padaria Sol”',
            '(Status é igual a “Fechado” ou Pedido está vazio)',
        ], $filtros->descrever(['logica' => 'e', 'regras' => [
            ['campo' => 'cidade', 'operador' => 'contem', 'valor' => 'Recife', 'via' => [['campo' => 'cliente']]],
            ['campo' => 'valor', 'operador' => 'entre', 'valor' => '10', 'valor_ate' => '1500.5'],
            ['campo' => 'entrega', 'operador' => 'menor', 'valor' => '2026-12-31'],
            ['campo' => 'criado_em', 'operador' => 'maior', 'valor' => '2026-10-01'],
            ['campo' => 'cliente', 'operador' => 'igual', 'valor' => self::ITEM],
            ['logica' => 'ou', 'regras' => [
                ['campo' => 'status', 'operador' => 'igual', 'valor' => 'Fechado'],
                ['campo' => 'nome', 'operador' => 'vazio'],
            ]],
        ]], $nome));

        // De volta: o caminho passa pela tabela de quem aponta.
        $this->assertSame(
            ['Algum de Pedidos › Valor maior que 500', 'Pedidos não tem nenhum item', 'Nenhum de Pedidos › Status é igual a “Aberto”'],
            $filtros->descrever(['logica' => 'e', 'regras' => [
                ['campo' => 'valor', 'operador' => 'maior', 'valor' => '500', 'via' => [['campo' => 'cliente', 'sentido' => 'volta']]],
                ['operador' => 'vazio', 'via' => [['campo' => 'cliente', 'sentido' => 'volta']]],
                ['campo' => 'status', 'operador' => 'igual', 'valor' => 'Aberto', 'via' => [['campo' => 'cliente', 'sentido' => 'volta']], 'quantificador' => 'nenhum'],
            ]], $nome)
        );
    }
}
