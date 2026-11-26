<?php

use App\Exceptions\NaoEncontradoException;
use App\Models\CandidatoModel;
use App\Models\FaseRecrutamentoModel;
use App\Models\HistoricoFaseModel;
use App\Models\ModuloModel;
use App\Services\AutorizacaoModuloService;
use App\Services\CandidatoService;
use App\Services\Storage\StorageServiceInterface;
use CodeIgniter\Events\Events;
use CodeIgniter\HTTP\Files\UploadedFile;
use CodeIgniter\Test\CIUnitTestCase;
use Config\Services;
use Tests\Support\Models\ModeloFalso;

/**
 * Recrutamento sem o banco: models em memória, permissão e storage falsos.
 * Cobre o kanban (por vaga e geral), a apresentação do candidato, as regras
 * do currículo e o que acontece antes da transação de mudar de etapa.
 *
 * @internal
 */
final class CandidatoServiceTest extends CIUnitTestCase
{
    private const EMPRESA = 'empresa-1';
    private const VAGA    = 'vaga-a';

    private CandidatoService $servico;

    /** @var ModuloModel&object{respostas: array, consultas: array} */
    private ModuloModel $modulos;

    /** @var CandidatoModel&object{respostas: array, consultas: array} */
    private CandidatoModel $candidatos;

    /** @var FaseRecrutamentoModel&object{respostas: array, consultas: array} */
    private FaseRecrutamentoModel $fases;

    private AutorizacaoModuloService $autorizacao;

    private StorageServiceInterface $storage;

    /** @var list<array> payloads de candidato_criado */
    private array $criados = [];

    /** @var array<string, list<callable>> */
    private array $ouvintesOriginais = [];

    /** @var list<string> arquivos criados para os uploads */
    private array $temporarios = [];

    protected function setUp(): void
    {
        parent::setUp();

        $this->modulos    = new class () extends ModuloModel { use ModeloFalso; };
        $this->candidatos = new class () extends CandidatoModel { use ModeloFalso; };
        $this->fases      = new class () extends FaseRecrutamentoModel { use ModeloFalso; };
        $historico        = new class () extends HistoricoFaseModel { use ModeloFalso; };

        $this->autorizacao = new class () extends AutorizacaoModuloService {
            /** @var list<array> */
            public array $exigidos = [];

            /** @var list<string>|null */
            public ?array $permitidos = null;

            public function __construct()
            {
            }

            public function exigirNivel(bool $acessoTotal, string $cargoId, string $moduloId, string $nivelMinimo): void
            {
                $this->exigidos[] = [$acessoTotal, $cargoId, $moduloId, $nivelMinimo];
            }

            public function filtrarModulosComNivel(array $moduloIds, bool $acessoTotal, string $cargoId, string $nivelMinimo): array
            {
                $this->exigidos[] = [$acessoTotal, $cargoId, $moduloIds, $nivelMinimo];

                return $this->permitidos ?? $moduloIds;
            }
        };

        $this->storage = new class () implements StorageServiceInterface {
            /** @var list<array> */
            public array $chamadas = [];

            public ?\Throwable $falha = null;

            public function salvar(string $caminhoTemporario, string $extensao): string
            {
                $this->chamadas[] = ['salvar', $caminhoTemporario, $extensao];

                return 'curriculos/chave.' . $extensao;
            }

            public function obter(string $chaveArmazenamento): string
            {
                $this->chamadas[] = ['obter', $chaveArmazenamento];

                return 'conteudo';
            }

            public function excluir(string $chaveArmazenamento): void
            {
                $this->chamadas[] = ['excluir', $chaveArmazenamento];
            }
        };
        Services::injectMock('storage', $this->storage);

        $this->servico = new CandidatoService();
        $trocas        = [
            'moduloModel'              => $this->modulos,
            'candidatoModel'           => $this->candidatos,
            'faseRecrutamentoModel'    => $this->fases,
            'historicoFaseModel'       => $historico,
            'autorizacaoModuloService' => $this->autorizacao,
        ];
        (function () use ($trocas): void {
            foreach ($trocas as $propriedade => $valor) {
                $this->{$propriedade} = $valor;
            }
        })->call($this->servico);

        // O evento roda automações e o histórico (banco): aqui só é guardado.
        Events::initialize();
        foreach (['candidato_criado', 'candidato_mudou_etapa'] as $evento) {
            $this->ouvintesOriginais[$evento] = Events::listeners($evento);
            Events::removeAllListeners($evento);
        }
        Events::on('candidato_criado', function (array $payload): void {
            $this->criados[] = $payload;
        });
    }

    protected function tearDown(): void
    {
        array_map('unlink', array_filter($this->temporarios, 'is_file'));
        foreach ($this->ouvintesOriginais as $evento => $ouvintes) {
            Events::removeAllListeners($evento);
            foreach ($ouvintes as $ouvinte) {
                Events::on($evento, $ouvinte);
            }
        }
        parent::tearDown();
    }

    private function vagaExiste(): void
    {
        $this->modulos->respostas['first'] = [['id' => self::VAGA, 'empresa_id' => self::EMPRESA, 'nome' => 'Vaga A', 'tipo' => 'recrutamento']];
    }

    /** Arquivo de verdade no disco, aceito como upload válido. */
    private function curriculo(string $conteudo, string $nome, int $erro = UPLOAD_ERR_OK, ?int $tamanho = null, bool $valido = true): UploadedFile
    {
        $caminho             = tempnam(sys_get_temp_dir(), 'cv');
        $this->temporarios[] = $caminho;
        file_put_contents($caminho, $conteudo);

        $arquivo = new class ($caminho, $nome, null, $tamanho ?? strlen($conteudo), $erro) extends UploadedFile {
            public bool $valido = true;

            public function isValid(): bool
            {
                return $this->valido && $this->getError() === UPLOAD_ERR_OK;
            }
        };
        $arquivo->valido = $valido;

        return $arquivo;
    }

    public function testKanbanDaVagaSeparaPorEtapaNaOrdemEEscondeAChaveDoCurriculo(): void
    {
        $this->vagaExiste();
        $this->fases->respostas['findAll'] = [[
            ['id' => 'f1', 'nome' => 'Triagem', 'ordem' => 1],
            ['id' => 'f2', 'nome' => 'Entrevista', 'ordem' => 2],
        ]];
        $this->candidatos->respostas['findAll'] = [[
            ['id' => 'c1', 'fase_atual_id' => 'f2', 'nome' => 'Ana', 'curriculo_chave' => 'k1', 'curriculo_mime' => 'application/pdf', 'curriculo_nome' => 'ana.pdf', 'cargo_desejado' => 'Dev'],
            ['id' => 'c2', 'fase_atual_id' => 'f1', 'nome' => 'Bia', 'curriculo_chave' => null, 'curriculo_mime' => null, 'curriculo_nome' => null, 'cargo_desejado' => null],
            ['id' => 'c3', 'fase_atual_id' => 'f-apagada', 'nome' => 'Caio'],
        ]];

        $kanban = $this->servico->listarKanban(self::VAGA, self::EMPRESA, 'cargo-1', false);

        $this->assertSame([
            'f1' => ['fase' => 'Triagem', 'total' => 1, 'candidatos' => [
                ['id' => 'c2', 'fase_atual_id' => 'f1', 'nome' => 'Bia', 'curriculo_nome' => null, 'tem_curriculo' => false],
            ]],
            'f2' => ['fase' => 'Entrevista', 'total' => 1, 'candidatos' => [
                ['id' => 'c1', 'fase_atual_id' => 'f2', 'nome' => 'Ana', 'curriculo_nome' => 'ana.pdf', 'tem_curriculo' => true],
            ]],
        ], $kanban);
        $this->assertSame([[false, 'cargo-1', self::VAGA, 'visualizar']], $this->autorizacao->exigidos);
        $this->assertSame([['where', ['modulo_id', self::VAGA]], ['orderBy', ['ordem', 'ASC']]], $this->fases->consulta('findAll'));
        $this->assertSame([['where', ['modulo_id', self::VAGA]], ['orderBy', ['criado_em', 'ASC']]], $this->candidatos->consulta('findAll'));
    }

    public function testKanbanDeVagaDeOutraEmpresaNaoAparece(): void
    {
        try {
            $this->servico->listarKanban(self::VAGA, 'outra', 'cargo-1', false);
            $this->fail('Deveria recusar a vaga.');
        } catch (NaoEncontradoException $e) {
            $this->assertSame('Vaga não encontrada.', $e->getMessage());
        }

        $this->assertSame([['where', ['id', self::VAGA]], ['where', ['empresa_id', 'outra']], ['where', ['tipo', 'recrutamento']]], $this->modulos->consulta('first'));
        $this->assertSame([], $this->autorizacao->exigidos);
        $this->assertSame([], $this->candidatos->consultas);
    }

    public function testKanbanGeralJuntaEtapasDeMesmoNomeSoDasVagasPermitidas(): void
    {
        $this->modulos->respostas['findAll'] = [[
            ['id' => 'a', 'nome' => 'Dev'],
            ['id' => 'b', 'nome' => 'Design'],
            ['id' => 'c', 'nome' => 'Sem acesso'],
        ]];
        $this->autorizacao->permitidos = ['a', 'b'];
        $this->fases->respostas['findAll'] = [[
            ['id' => 'a1', 'modulo_id' => 'a', 'nome' => 'Triagem'],
            ['id' => 'b1', 'modulo_id' => 'b', 'nome' => ' triagem '],
            ['id' => 'a2', 'modulo_id' => 'a', 'nome' => 'Entrevista'],
            ['id' => 'b2', 'modulo_id' => 'b', 'nome' => 'Proposta'],
        ]];
        $this->candidatos->respostas['findAll'] = [[
            ['id' => 'c1', 'modulo_id' => 'b', 'fase_atual_id' => 'b1', 'curriculo_chave' => 'k'],
            ['id' => 'c2', 'modulo_id' => 'a', 'fase_atual_id' => 'a1'],
            ['id' => 'c3', 'modulo_id' => 'b', 'fase_atual_id' => 'b2'],
            ['id' => 'c4', 'modulo_id' => 'a', 'fase_atual_id' => 'sumiu'],
        ]];

        $kanban = $this->servico->listarKanbanGlobal(self::EMPRESA, 'cargo-1', false);

        $this->assertSame([
            'triagem' => ['fase' => 'Triagem', 'total' => 2, 'candidatos' => [
                ['id' => 'c1', 'modulo_id' => 'b', 'fase_atual_id' => 'b1', 'vaga' => 'Design', 'tem_curriculo' => true],
                ['id' => 'c2', 'modulo_id' => 'a', 'fase_atual_id' => 'a1', 'vaga' => 'Dev', 'tem_curriculo' => false],
            ]],
            'entrevista' => ['fase' => 'Entrevista', 'total' => 0, 'candidatos' => []],
            'proposta'   => ['fase' => 'Proposta', 'total' => 1, 'candidatos' => [
                ['id' => 'c3', 'modulo_id' => 'b', 'fase_atual_id' => 'b2', 'vaga' => 'Design', 'tem_curriculo' => false],
            ]],
            'outros' => ['fase' => 'Outros', 'total' => 1, 'candidatos' => [
                ['id' => 'c4', 'modulo_id' => 'a', 'fase_atual_id' => 'sumiu', 'vaga' => 'Dev', 'tem_curriculo' => false],
            ]],
        ], $kanban);
        $this->assertSame([[false, 'cargo-1', ['a', 'b', 'c'], 'visualizar']], $this->autorizacao->exigidos);
        $this->assertSame([['whereIn', ['modulo_id', ['a', 'b']]], ['orderBy', ['ordem', 'ASC']]], $this->fases->consulta('findAll'));
        $this->assertSame([['whereIn', ['modulo_id', ['a', 'b']]], ['orderBy', ['criado_em', 'ASC']]], $this->candidatos->consulta('findAll'));
    }

    public function testKanbanGeralVazioSemVagasOuSemAcesso(): void
    {
        $this->assertSame([], $this->servico->listarKanbanGlobal(self::EMPRESA, 'cargo-1', false));

        $this->modulos->respostas['findAll'] = [[['id' => 'a', 'nome' => 'Dev']]];
        $this->autorizacao->permitidos       = [];
        $this->assertSame([], $this->servico->listarKanbanGlobal(self::EMPRESA, 'cargo-1', false));
        $this->assertSame([], $this->fases->consultas);
    }

    public function testCandidaturaParaVagaInexistenteOuRepetida(): void
    {
        try {
            $this->servico->criarCandidatura(self::VAGA, ['nome' => 'Ana', 'email' => 'ana@x.com']);
            $this->fail('Deveria recusar a vaga.');
        } catch (NaoEncontradoException $e) {
            $this->assertSame('Vaga não encontrada.', $e->getMessage());
        }

        $this->vagaExiste();
        $this->candidatos->respostas['first'] = [['id' => 'c1']];
        $this->expectException(\DomainException::class);
        $this->expectExceptionMessage('Você já se candidatou para esta vaga anteriormente.');
        $this->servico->criarCandidatura(self::VAGA, ['nome' => 'Ana', 'email' => 'ana@x.com']);
    }

    public function testEquipeNaoAdicionaEmailRepetidoNemEmVagaSemEtapa(): void
    {
        $this->vagaExiste();
        $this->modulos->respostas['find'] = [['id' => self::VAGA, 'empresa_id' => self::EMPRESA, 'nome' => 'Vaga A']];
        $this->candidatos->respostas['first'] = [['id' => 'c1']];

        try {
            $this->servico->adicionarCandidato(self::VAGA, self::EMPRESA, 'cargo-1', false, ['nome' => 'Ana', 'email' => 'ana@x.com']);
            $this->fail('Deveria recusar o e-mail repetido.');
        } catch (\DomainException $e) {
            $this->assertSame('Já existe um candidato com esse e-mail nesta vaga.', $e->getMessage());
        }
        $this->assertSame([[false, 'cargo-1', self::VAGA, 'editar']], $this->autorizacao->exigidos);
        $this->assertSame([['where', ['modulo_id', self::VAGA]], ['where', ['email', 'ana@x.com']]], $this->candidatos->consulta('first'));

        $this->vagaExiste();
        $this->modulos->respostas['find'] = [['id' => self::VAGA, 'empresa_id' => self::EMPRESA, 'nome' => 'Vaga A']];
        $this->expectException(\RuntimeException::class);
        $this->expectExceptionMessage('Esta vaga ainda não tem nenhuma etapa configurada.');
        $this->servico->adicionarCandidato(self::VAGA, self::EMPRESA, 'cargo-1', false, ['nome' => 'Ana', 'email' => 'ana@x.com']);
    }

    public function testRegrasDoCurriculo(): void
    {
        $casos = [
            ['O currículo passa do tamanho permitido. Envie um arquivo menor.', $this->curriculo('%PDF-1.4', 'cv.pdf', UPLOAD_ERR_INI_SIZE)],
            ['O currículo passa do tamanho permitido. Envie um arquivo menor.', $this->curriculo('%PDF-1.4', 'cv.pdf', UPLOAD_ERR_FORM_SIZE)],
            ['O currículo não chegou corretamente. Tente enviar de novo.', $this->curriculo('%PDF-1.4', 'cv.pdf', UPLOAD_ERR_PARTIAL)],
            ['O currículo não chegou corretamente. Tente enviar de novo.', $this->curriculo('%PDF-1.4', 'cv.pdf', UPLOAD_ERR_OK, null, false)],
            ['Envie o currículo em PDF ou Word (.doc ou .docx).', $this->curriculo('só texto', 'cv.txt')],
            ['O currículo passa de 5 MB. Envie um arquivo menor.', $this->curriculo('%PDF-1.4', 'cv.pdf', UPLOAD_ERR_OK, 5 * 1024 * 1024 + 1)],
        ];

        foreach ($casos as [$mensagem, $arquivo]) {
            $this->vagaExiste();
            $this->modulos->respostas['find'] = [['id' => self::VAGA, 'empresa_id' => self::EMPRESA, 'nome' => 'Vaga A']];
            $this->fases->respostas['first']  = [['id' => 'f1', 'nome' => 'Triagem']];

            try {
                $this->servico->adicionarCandidato(self::VAGA, self::EMPRESA, 'cargo-1', true, ['nome' => 'Ana', 'email' => 'ana@x.com'], $arquivo);
                $this->fail('Deveria recusar: ' . $mensagem);
            } catch (\DomainException $e) {
                $this->assertSame($mensagem, $e->getMessage());
            }
        }

        $this->assertSame([], $this->storage->chamadas);
        $this->assertSame([], array_filter($this->candidatos->consultas, static fn ($c) => $c[0] === 'insert'));
    }

    public function testSemArquivoEscolhidoSegueSemCurriculoEAvisaAsAutomacoes(): void
    {
        $this->vagaExiste();
        $this->modulos->respostas['find']      = [['id' => self::VAGA, 'empresa_id' => self::EMPRESA, 'nome' => 'Vaga A']];
        $this->fases->respostas['first']       = [['id' => 'f1', 'nome' => 'Triagem']];
        $this->candidatos->respostas['insert'] = ['c-novo'];
        $this->candidatos->respostas['first']  = [null, ['id' => 'c-novo', 'nome' => 'Ana', 'curriculo_chave' => null, 'curriculo_mime' => null]];

        $candidato = $this->servico->adicionarCandidato(
            self::VAGA,
            self::EMPRESA,
            'cargo-1',
            true,
            ['nome' => 'Ana', 'email' => 'ana@x.com', 'telefone' => '', 'mensagem' => 'Oi'],
            $this->curriculo('', '', UPLOAD_ERR_NO_FILE)
        );

        $this->assertSame(['id' => 'c-novo', 'nome' => 'Ana', 'tem_curriculo' => false], $candidato);
        $this->assertSame([], $this->storage->chamadas);
        $this->assertSame([[
            'modulo_id'     => self::VAGA,
            'fase_atual_id' => 'f1',
            'nome'          => 'Ana',
            'email'         => 'ana@x.com',
            'telefone'      => null,
            'mensagem'      => 'Oi',
        ]], array_values(array_map(static fn ($c) => $c[2][0], array_filter($this->candidatos->consultas, static fn ($c) => $c[0] === 'insert'))));
        $this->assertSame([['where', ['modulo_id', self::VAGA]], ['orderBy', ['ordem', 'ASC']]], $this->fases->consulta('first'));
        $this->assertSame([['select', ['candidato.*']], ['join', ['modulo', 'modulo.id = candidato.modulo_id']], ['where', ['candidato.id', 'c-novo']], ['where', ['candidato.modulo_id', self::VAGA]], ['where', ['modulo.empresa_id', self::EMPRESA]]], $this->candidatos->consulta('first', 1));

        $this->assertCount(1, $this->criados);
        $this->assertSame(self::VAGA, $this->criados[0]['modulo_id']);
        $this->assertNull($this->criados[0]['registro_id']);
        $this->assertSame(
            ['nome' => 'Ana', 'email' => 'ana@x.com', 'telefone' => '', 'mensagem' => 'Oi', 'etapa' => 'Triagem', 'etapa_anterior' => ''],
            $this->criados[0]['dados_registro']
        );
    }

    public function testCurriculoSobeAntesEEApagadoSeOBancoFalhar(): void
    {
        $this->vagaExiste();
        $this->modulos->respostas['find']      = [['id' => self::VAGA, 'empresa_id' => self::EMPRESA, 'nome' => 'Vaga A']];
        $this->fases->respostas['first']       = [['id' => 'f1', 'nome' => 'Triagem']];
        $this->candidatos->respostas['insert'] = static fn () => throw new \RuntimeException('banco caiu');
        $arquivo                               = $this->curriculo("%PDF-1.4\n%âãÏÓ\n1 0 obj\n<<>>\nendobj\ntrailer\n<<>>\n%%EOF\n", 'Meu currículo.pdf');

        try {
            $this->servico->adicionarCandidato(self::VAGA, self::EMPRESA, 'cargo-1', true, ['nome' => 'Ana', 'email' => 'ana@x.com'], $arquivo);
            $this->fail('Deveria repassar a falha do banco.');
        } catch (\RuntimeException $e) {
            $this->assertSame('banco caiu', $e->getMessage());
        }

        $this->assertSame([
            ['salvar', $arquivo->getTempName(), 'pdf'],
            ['excluir', 'curriculos/chave.pdf'],
        ], $this->storage->chamadas);

        $linha = array_values(array_filter($this->candidatos->consultas, static fn ($c) => $c[0] === 'insert'))[0][2][0];
        $this->assertSame('curriculos/chave.pdf', $linha['curriculo_chave']);
        $this->assertSame('Meu currículo.pdf', $linha['curriculo_nome']);
        $this->assertSame('application/pdf', $linha['curriculo_mime']);
        $this->assertSame($arquivo->getSize(), $linha['curriculo_tamanho']);
        $this->assertSame([], $this->criados);
    }

    public function testBaixarCurriculoPedeVisualizarEUsaONomeOriginal(): void
    {
        $this->candidatos->respostas['first'] = [
            ['id' => 'c1', 'curriculo_chave' => 'k1', 'curriculo_nome' => '', 'curriculo_mime' => ''],
            ['id' => 'c2', 'curriculo_chave' => null],
        ];

        $this->assertSame(
            ['conteudo' => 'conteudo', 'nome_original' => 'curriculo', 'tipo_mime' => 'application/octet-stream'],
            $this->servico->baixarCurriculo('c1', self::VAGA, self::EMPRESA, 'cargo-1', false)
        );
        $this->assertSame([[false, 'cargo-1', self::VAGA, 'visualizar']], $this->autorizacao->exigidos);
        $this->assertSame([['obter', 'k1']], $this->storage->chamadas);

        $this->expectException(NaoEncontradoException::class);
        $this->expectExceptionMessage('Este candidato não enviou currículo.');
        $this->servico->baixarCurriculo('c2', self::VAGA, self::EMPRESA, 'cargo-1', false);
    }

    public function testExcluirApagaOCurriculoDepoisDoCandidato(): void
    {
        $this->candidatos->respostas['first'] = [['id' => 'c1', 'curriculo_chave' => 'k1']];

        $this->servico->excluirCandidato('c1', self::VAGA, self::EMPRESA, 'cargo-1', false);

        $this->assertSame([[false, 'cargo-1', self::VAGA, 'editar']], $this->autorizacao->exigidos);
        $this->assertSame([['excluir', 'k1']], $this->storage->chamadas);
        $this->assertSame(['c1'], array_values(array_map(static fn ($c) => $c[2][0], array_filter($this->candidatos->consultas, static fn ($c) => $c[0] === 'delete'))));
    }

    public function testMoverDeEtapaConfereCandidatoEtapaEMudancaAntesDeGravar(): void
    {
        $mover = fn () => $this->servico->moverFase('c1', self::VAGA, self::EMPRESA, 'cargo-1', false, 'f2', 'u1');

        foreach ([
            [null, null, NaoEncontradoException::class, 'Candidato não encontrado.'],
            [['id' => 'c1', 'fase_atual_id' => 'f1'], null, NaoEncontradoException::class, 'Fase não encontrada nesta vaga.'],
            [['id' => 'c1', 'fase_atual_id' => 'f2'], ['id' => 'f2'], \DomainException::class, 'O candidato já está nessa fase.'],
        ] as [$candidato, $fase, $classe, $mensagem]) {
            $this->vagaExiste();
            $this->candidatos->respostas['first'] = [$candidato];
            $this->fases->respostas['first']      = [$fase];

            try {
                $mover();
                $this->fail('Deveria recusar: ' . $mensagem);
            } catch (\Exception $e) {
                $this->assertInstanceOf($classe, $e);
                $this->assertSame($mensagem, $e->getMessage());
            }
        }

        $this->assertSame([['where', ['id', 'f2']], ['where', ['modulo_id', self::VAGA]]], $this->fases->consulta('first'));
        $this->assertSame([[false, 'cargo-1', self::VAGA, 'editar'], [false, 'cargo-1', self::VAGA, 'editar']], $this->autorizacao->exigidos);
    }

    public function testMoverPeloNomeDaEtapaIgnoraMaiusculasEEspacos(): void
    {
        $this->candidatos->respostas['first'] = [
            ['id' => 'c1', 'modulo_id' => self::VAGA, 'fase_atual_id' => 'f2'],
            ['id' => 'c1', 'modulo_id' => self::VAGA, 'fase_atual_id' => 'f2'],
        ];
        $this->fases->respostas['findAll'] = [[['id' => 'f1', 'nome' => 'Triagem'], ['id' => 'f2', 'nome' => ' Entrevista ']]];
        $this->fases->respostas['first']   = [['id' => 'f2']];
        $this->vagaExiste();

        try {
            $this->servico->moverFaseGlobalPorNome('c1', self::EMPRESA, 'cargo-1', false, 'ENTREVISTA', 'u1');
            $this->fail('Deveria chegar à etapa atual.');
        } catch (\DomainException $e) {
            $this->assertSame('O candidato já está nessa fase.', $e->getMessage());
        }
        $this->assertSame([['where', ['id', 'f2']], ['where', ['modulo_id', self::VAGA]]], $this->fases->consulta('first'));

        $this->candidatos->respostas['first'] = [['id' => 'c1', 'modulo_id' => self::VAGA, 'fase_atual_id' => 'f1']];
        $this->fases->respostas['findAll']    = [[['id' => 'f1', 'nome' => 'Triagem']]];
        $this->expectException(NaoEncontradoException::class);
        $this->expectExceptionMessage("A vaga deste candidato não possui uma fase chamada 'Contratado'.");
        $this->servico->moverFaseGlobalPorNome('c1', self::EMPRESA, 'cargo-1', false, 'Contratado', 'u1');
    }
}
