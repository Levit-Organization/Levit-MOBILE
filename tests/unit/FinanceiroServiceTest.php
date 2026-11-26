<?php

use App\Exceptions\NaoEncontradoException;
use App\Models\CategoriaFinanceiraModel;
use App\Models\LancamentoFinanceiroModel;
use App\Models\ModuloModel;
use App\Services\AutorizacaoModuloService;
use App\Services\FinanceiroService;
use App\Services\RegistroService;
use CodeIgniter\Test\CIUnitTestCase;
use Tests\Support\Models\ModeloFalso;

/**
 * Financeiro sem o banco: saldo acumulado e totais da listagem, validação
 * dos campos fixos do lançamento e as regras das categorias.
 *
 * @internal
 */
final class FinanceiroServiceTest extends CIUnitTestCase
{
    private const EMPRESA = 'empresa-1';
    private const MODULO  = 'fin-1';

    private FinanceiroService $servico;
    private ModuloModel $modulos;
    private LancamentoFinanceiroModel $lancamentos;
    private CategoriaFinanceiraModel $categorias;
    private AutorizacaoModuloService $autorizacao;
    private RegistroService $registros;

    protected function setUp(): void
    {
        parent::setUp();

        $this->modulos     = new class () extends ModuloModel { use ModeloFalso; };
        $this->lancamentos = new class () extends LancamentoFinanceiroModel { use ModeloFalso; };
        $this->categorias  = new class () extends CategoriaFinanceiraModel { use ModeloFalso; };

        $this->autorizacao = new class () extends AutorizacaoModuloService {
            /** @var list<array> */
            public array $exigidos = [];

            public function __construct()
            {
            }

            public function exigirNivel(bool $acessoTotal, string $cargoId, string $moduloId, string $nivelMinimo): void
            {
                $this->exigidos[] = [$acessoTotal, $cargoId, $moduloId, $nivelMinimo];
            }
        };

        $this->registros = new class () extends RegistroService {
            /** @var list<array> */
            public array $excluidos = [];

            public function __construct()
            {
            }

            public function excluirRegistro(string $registroId, string $moduloId, string $empresaId, string $cargoId, bool $acessoTotal): void
            {
                $this->excluidos[] = [$registroId, $moduloId, $empresaId, $cargoId, $acessoTotal];
            }
        };

        $this->servico = new FinanceiroService();
        $trocas        = [
            'moduloModel'              => $this->modulos,
            'lancamentoModel'          => $this->lancamentos,
            'categoriaModel'           => $this->categorias,
            'autorizacaoModuloService' => $this->autorizacao,
            'registroService'          => $this->registros,
        ];
        (function () use ($trocas): void {
            foreach ($trocas as $propriedade => $valor) {
                $this->{$propriedade} = $valor;
            }
        })->call($this->servico);
    }

    private function moduloExiste(): void
    {
        $this->modulos->respostas['first'] = [['id' => self::MODULO, 'tipo' => 'financeiro']];
    }

    /** Campos fixos validados (o método é privado: é o que vai para o banco). */
    private function validar(array $dados, bool $parcial = false): array
    {
        $empresa = self::EMPRESA;

        return (fn () => $this->validarCamposLancamento($empresa, $dados, $parcial))->call($this->servico);
    }

    public function testListagemCalculaSaldoAcumuladoETotais(): void
    {
        $this->moduloExiste();
        $this->lancamentos->respostas['findAll'] = [[
            ['id' => 'l1', 'tipo' => 'entrada', 'valor' => '1000.10'],
            ['id' => 'l2', 'tipo' => 'saida', 'valor' => '250.05'],
            ['id' => 'l3', 'tipo' => 'saida', 'valor' => '800'],
            ['id' => 'l4', 'tipo' => 'entrada', 'valor' => '0.333'],
        ]];

        $resultado = $this->servico->listarLancamentos(self::MODULO, self::EMPRESA, 'cargo-1', false);

        $this->assertSame([
            'lancamentos' => [
                ['id' => 'l1', 'tipo' => 'entrada', 'valor' => '1000.10', 'saldo_acumulado' => 1000.1],
                ['id' => 'l2', 'tipo' => 'saida', 'valor' => '250.05', 'saldo_acumulado' => 750.05],
                ['id' => 'l3', 'tipo' => 'saida', 'valor' => '800', 'saldo_acumulado' => -49.95],
                ['id' => 'l4', 'tipo' => 'entrada', 'valor' => '0.333', 'saldo_acumulado' => -49.62],
            ],
            'resumo' => ['total_entradas' => 1000.43, 'total_saidas' => 1050.05, 'saldo_periodo' => -49.62],
        ], $resultado);
        $this->assertSame([[false, 'cargo-1', self::MODULO, 'visualizar']], $this->autorizacao->exigidos);
        $this->assertSame([
            ['select', ['lancamento_financeiro.*, categoria_financeira.nome as categoria_nome']],
            ['join', ['categoria_financeira', 'categoria_financeira.id = lancamento_financeiro.categoria_id', 'left']],
            ['where', ['lancamento_financeiro.modulo_id', self::MODULO]],
            ['orderBy', ['lancamento_financeiro.data_lancamento', 'ASC']],
            ['orderBy', ['lancamento_financeiro.criado_em', 'ASC']],
        ], $this->lancamentos->consulta('findAll'));
        $this->assertSame([['where', ['id', self::MODULO]], ['where', ['empresa_id', self::EMPRESA]], ['where', ['tipo', 'financeiro']]], $this->modulos->consulta('first'));
    }

    public function testListagemVaziaEFiltradaPorCategoria(): void
    {
        $this->moduloExiste();

        $this->assertSame(
            ['lancamentos' => [], 'resumo' => ['total_entradas' => 0.0, 'total_saidas' => 0.0, 'saldo_periodo' => 0.0]],
            $this->servico->listarLancamentos(self::MODULO, self::EMPRESA, 'cargo-1', true, 'cat-1')
        );
        $this->assertContains(['where', ['lancamento_financeiro.categoria_id', 'cat-1']], $this->lancamentos->consulta('findAll'));
    }

    public function testModuloQueNaoEFinanceiroNaoAparece(): void
    {
        $this->expectException(NaoEncontradoException::class);
        $this->expectExceptionMessage('Financeiro não encontrado.');
        $this->servico->listarLancamentos(self::MODULO, self::EMPRESA, 'cargo-1', true);
    }

    public function testCamposFixosValidosNaCriacao(): void
    {
        $this->categorias->respostas['first'] = [['id' => 'cat-1']];

        $this->assertSame([
            'descricao'       => 'Aluguel',
            'data_lancamento' => '2026-03-31',
            'tipo'            => 'saida',
            'valor'           => 1500.5,
            'conciliado'      => true,
            'categoria_id'    => 'cat-1',
        ], $this->validar([
            'descricao'    => '  Aluguel ',
            'data'         => '2026-03-31',
            'tipo'         => 'saida',
            'valor'        => '1500.50',
            'conciliado'   => 1,
            'categoria_id' => 'cat-1',
            'outro'        => 'ignorado',
        ]));
        $this->assertSame([['where', ['id', 'cat-1']], ['where', ['empresa_id', self::EMPRESA]]], $this->categorias->consulta('first'));
        $this->assertSame(['valor' => 10], $this->validar(['valor' => '10'], true));
    }

    public function testAtualizacaoParcialSoLevaOQueVeio(): void
    {
        $this->assertSame([], $this->validar([], true));
        $this->assertSame(['conciliado' => false, 'categoria_id' => null], $this->validar(['conciliado' => '', 'categoria_id' => null], true));
        $this->assertSame([], $this->categorias->consultas);
    }

    public function testRecusaCamposFixosInvalidos(): void
    {
        $completo = ['descricao' => 'Venda', 'data' => '2026-01-10', 'tipo' => 'entrada', 'valor' => 10];

        foreach ([
            [['descricao' => '   '] + $completo, false, 'A descrição não pode ficar vazia.'],
            [array_diff_key($completo, ['descricao' => 1]), false, 'A descrição é obrigatória.'],
            [['data' => '10/01/2026'] + $completo, false, 'A data precisa estar no formato AAAA-MM-DD.'],
            [['data' => '2026-02-30'] + $completo, false, 'A data precisa estar no formato AAAA-MM-DD.'],
            [['data' => null] + $completo, true, 'A data precisa estar no formato AAAA-MM-DD.'],
            [array_diff_key($completo, ['data' => 1]), false, 'A data é obrigatória.'],
            [['tipo' => 'transferencia'] + $completo, false, "Tipo de lançamento inválido: 'transferencia'. Use 'entrada' ou 'saida'."],
            [array_diff_key($completo, ['tipo' => 1]), false, 'O tipo do lançamento é obrigatório.'],
            [['valor' => 0] + $completo, false, 'O valor precisa ser um número maior que zero.'],
            [['valor' => '-5'] + $completo, true, 'O valor precisa ser um número maior que zero.'],
            [['valor' => 'dez'] + $completo, false, 'O valor precisa ser um número maior que zero.'],
            [array_diff_key($completo, ['valor' => 1]), false, 'O valor é obrigatório.'],
            [['categoria_id' => 'de-outra-empresa'], true, 'Categoria inválida ou não pertence à empresa.'],
        ] as [$dados, $parcial, $mensagem]) {
            try {
                $this->validar($dados, $parcial);
                $this->fail('Deveria recusar: ' . $mensagem);
            } catch (\DomainException $e) {
                $this->assertSame($mensagem, $e->getMessage());
            }
        }
    }

    public function testCriarLancamentoConfereModuloEPermissaoAntesDosCampos(): void
    {
        $this->moduloExiste();

        try {
            $this->servico->criarLancamento(self::MODULO, self::EMPRESA, 'cargo-1', false, 'u1', ['descricao' => 'x']);
            $this->fail('Deveria recusar os campos.');
        } catch (\DomainException $e) {
            $this->assertSame('A data é obrigatória.', $e->getMessage());
        }
        $this->assertSame([[false, 'cargo-1', self::MODULO, 'editar']], $this->autorizacao->exigidos);
    }

    public function testExcluirLancamentoApagaPeloRegistroVinculado(): void
    {
        $this->moduloExiste();
        $this->lancamentos->respostas['first'] = [['id' => 'l1', 'registro_id' => 'r1'], null];

        $this->servico->excluirLancamento('l1', self::MODULO, self::EMPRESA, 'cargo-1', false);
        $this->assertSame([['r1', self::MODULO, self::EMPRESA, 'cargo-1', false]], $this->registros->excluidos);
        $this->assertSame([['where', ['id', 'l1']], ['where', ['modulo_id', self::MODULO]]], $this->lancamentos->consulta('first'));

        $this->moduloExiste();
        $this->expectException(NaoEncontradoException::class);
        $this->expectExceptionMessage('Lançamento não encontrado.');
        $this->servico->excluirLancamento('l2', self::MODULO, self::EMPRESA, 'cargo-1', false);
    }

    public function testCategorias(): void
    {
        $this->categorias->respostas['findAll'] = [[['id' => 'c1', 'nome' => 'Aluguel']]];
        $this->assertSame([['id' => 'c1', 'nome' => 'Aluguel']], $this->servico->listarCategorias(self::EMPRESA));
        $this->assertSame([['where', ['empresa_id', self::EMPRESA]], ['orderBy', ['nome', 'ASC']]], $this->categorias->consulta('findAll'));

        $this->categorias->respostas['first']  = [null];
        $this->categorias->respostas['insert'] = ['c2'];
        $this->categorias->respostas['find']   = static fn (array $consulta, array $argumentos) => ['id' => $argumentos[0], 'nome' => 'Luz'];
        $this->assertSame(['id' => 'c2', 'nome' => 'Luz'], $this->servico->criarCategoria(self::EMPRESA, '  Luz '));
        $this->assertSame([['where', ['empresa_id', self::EMPRESA]], ['where', ['nome', 'Luz']]], $this->categorias->consulta('first'));
        $this->assertSame([['empresa_id' => self::EMPRESA, 'nome' => 'Luz']], array_values(array_map(static fn ($c) => $c[2][0], array_filter($this->categorias->consultas, static fn ($c) => $c[0] === 'insert'))));

        $this->categorias->respostas['first'] = [['id' => 'c1'], null, ['id' => 'c1']];
        foreach ([
            fn () => $this->servico->criarCategoria(self::EMPRESA, '   '),
            fn () => $this->servico->criarCategoria(self::EMPRESA, 'Aluguel'),
        ] as $i => $criar) {
            try {
                $criar();
                $this->fail('Deveria recusar a categoria.');
            } catch (\DomainException $e) {
                $this->assertSame(['O nome da categoria não pode ficar vazio.', 'Já existe uma categoria com esse nome nesta empresa.'][$i], $e->getMessage());
            }
        }

        try {
            $this->servico->excluirCategoria(self::EMPRESA, 'c9');
            $this->fail('Deveria recusar a categoria.');
        } catch (NaoEncontradoException $e) {
            $this->assertSame('Categoria não encontrada.', $e->getMessage());
        }

        $this->servico->excluirCategoria(self::EMPRESA, 'c1');
        $this->assertSame([['where', ['id', 'c1']], ['where', ['empresa_id', self::EMPRESA]]], $this->categorias->consulta('first', 3));
        $this->assertSame(['c1'], array_values(array_map(static fn ($c) => $c[2][0], array_filter($this->categorias->consultas, static fn ($c) => $c[0] === 'delete'))));
    }
}
