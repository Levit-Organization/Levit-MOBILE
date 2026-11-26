<?php

namespace App\Services;

use App\Exceptions\NaoEncontradoException;
use App\Models\CandidatoModel;
use App\Models\EmpresaModel;
use App\Models\FaseRecrutamentoModel;
use App\Models\HistoricoFaseModel;
use App\Models\ModuloModel;
use App\Services\Recrutamento\Curriculo;
use App\Services\Recrutamento\Inscricao;
use App\Services\Recrutamento\Kanban;
use App\Services\Recrutamento\MudancaDeEtapa;
use CodeIgniter\HTTP\Files\UploadedFile;

/**
 * Candidatos das vagas (módulos de recrutamento): candidatura pública,
 * cadastro pela equipe, kanban por vaga e geral, mudança de etapa e currículo.
 *
 * A entrada do candidato fica em Recrutamento\Inscricao, a mudança de etapa em
 * Recrutamento\MudancaDeEtapa, o kanban em Recrutamento\Kanban e o arquivo
 * em Recrutamento\Curriculo.
 */
class CandidatoService
{
    protected EmpresaModel $empresaModel;
    protected ModuloModel $moduloModel;
    protected CandidatoModel $candidatoModel;
    protected FaseRecrutamentoModel $faseRecrutamentoModel;
    protected HistoricoFaseModel $historicoFaseModel;
    protected AutorizacaoModuloService $autorizacaoModuloService;

    public function __construct()
    {
        $this->empresaModel             = new EmpresaModel();
        $this->moduloModel              = new ModuloModel();
        $this->candidatoModel           = new CandidatoModel();
        $this->faseRecrutamentoModel    = new FaseRecrutamentoModel();
        $this->historicoFaseModel       = new HistoricoFaseModel();
        $this->autorizacaoModuloService = new AutorizacaoModuloService();
    }

    /**
     * O que a página pública de candidatura mostra: o nome da vaga e da empresa.
     * Vaga sem etapa ainda não recebe candidatos, então também não aparece.
     */
    public function vagaPublica(string $moduloId): array
    {
        $modulo = $this->moduloModel->where('id', $moduloId)->where('tipo', 'recrutamento')->first();

        if (! $modulo || ! $this->faseRecrutamentoModel->where('modulo_id', $moduloId)->first()) {
            throw new NaoEncontradoException('Vaga não encontrada ou não está recebendo candidaturas.');
        }

        $empresa = $this->empresaModel->select('nome')->find($modulo['empresa_id']);

        return [
            'vaga'    => $modulo['nome'],
            'empresa' => $empresa['nome'] ?? null,
        ];
    }

    /**
     * Candidatura pública — sem autenticação, sem cargo, sem checagem
     * de nível (não se aplica: quem chama isso não está logado).
     */
    public function criarCandidatura(string $moduloId, array $dados, ?UploadedFile $curriculo = null): array
    {
        $modulo = $this->moduloModel->where('id', $moduloId)->where('tipo', 'recrutamento')->first();

        if (! $modulo) {
            throw new NaoEncontradoException('Vaga não encontrada.');
        }

        $candidato = $this->inserirCandidato($modulo, $dados, $curriculo, 'Você já se candidatou para esta vaga anteriormente.');

        // Candidaturas seguidas na mesma vaga viram uma notificação só.
        $notificacoes = new NotificacaoService();
        $notificacoes->notificarSemFalhar(
            $notificacoes->usuariosComAcessoAoModulo($moduloId, $modulo['empresa_id']),
            $modulo['empresa_id'],
            [
                'tipo'            => 'candidatura',
                'titulo'          => "Nova candidatura em {$modulo['nome']}",
                'titulo_agrupado' => "{n} novas candidaturas em {$modulo['nome']}",
                'mensagem'        => "{$dados['nome']} se candidatou.",
                'link'            => '/recrutamento',
                'agrupador'       => "candidatura:{$moduloId}",
            ]
        );

        return $candidato;
    }

    /** Candidato registrado por alguém da equipe (quem tem "editar" na vaga). */
    public function adicionarCandidato(string $moduloId, string $empresaId, string $cargoId, bool $acessoTotal, array $dados, ?UploadedFile $curriculo = null): array
    {
        $this->confirmarVagaDaEmpresa($moduloId, $empresaId);
        $this->autorizacaoModuloService->exigirNivel($acessoTotal, $cargoId, $moduloId, 'editar');

        $modulo    = $this->moduloModel->find($moduloId);
        $candidato = $this->inserirCandidato($modulo, $dados, $curriculo, 'Já existe um candidato com esse e-mail nesta vaga.');

        AtividadeService::registrar('candidato_adicionado', 'Adicionou o candidato “' . $dados['nome'] . '”', $moduloId);

        return $candidato;
    }

    public function baixarCurriculo(string $candidatoId, string $moduloId, string $empresaId, string $cargoId, bool $acessoTotal): array
    {
        $candidato = $this->buscarCandidatoBruto($candidatoId, $moduloId, $empresaId);
        $this->autorizacaoModuloService->exigirNivel($acessoTotal, $cargoId, $moduloId, 'visualizar');

        if (empty($candidato['curriculo_chave'])) {
            throw new NaoEncontradoException('Este candidato não enviou currículo.');
        }

        return [
            'conteudo'      => Curriculo::obter($candidato['curriculo_chave']),
            'nome_original' => $candidato['curriculo_nome'] ?: 'curriculo',
            'tipo_mime'     => $candidato['curriculo_mime'] ?: 'application/octet-stream',
        ];
    }

    /** Grava o candidato na primeira etapa da vaga (ver Recrutamento\Inscricao). */
    private function inserirCandidato(array $modulo, array $dados, ?UploadedFile $curriculo, string $mensagemDuplicado): array
    {
        $candidatoId = (new Inscricao($this->candidatoModel, $this->faseRecrutamentoModel))
            ->inserir($modulo, $dados, $curriculo, $mensagemDuplicado);

        return $this->buscarCandidato($candidatoId, $modulo['id'], $modulo['empresa_id']);
    }

    public function listarKanban(string $moduloId, string $empresaId, string $cargoId, bool $acessoTotal): array
    {
        $this->confirmarVagaDaEmpresa($moduloId, $empresaId);
        $this->autorizacaoModuloService->exigirNivel($acessoTotal, $cargoId, $moduloId, 'visualizar');

        $fases      = $this->faseRecrutamentoModel->where('modulo_id', $moduloId)->orderBy('ordem', 'ASC')->findAll();
        $candidatos = $this->candidatoModel->where('modulo_id', $moduloId)->orderBy('criado_em', 'ASC')->findAll();

        return Kanban::porEtapa($fases, $candidatos);
    }

    /**
     * Kanban de todas as vagas, agrupado por nome de fase — mas só
     * considerando vagas que o cargo tem pelo menos "visualizar".
     */
    public function listarKanbanGlobal(string $empresaId, string $cargoId, bool $acessoTotal): array
    {
        $vagas = $this->moduloModel->where('empresa_id', $empresaId)->where('tipo', 'recrutamento')->findAll();

        if (empty($vagas)) {
            return [];
        }

        $vagaIdsPermitidas = $this->autorizacaoModuloService->filtrarModulosComNivel(
            array_column($vagas, 'id'),
            $acessoTotal,
            $cargoId,
            'visualizar'
        );

        $vagas = array_values(array_filter($vagas, static fn ($v) => in_array($v['id'], $vagaIdsPermitidas, true)));

        if (empty($vagas)) {
            return [];
        }

        $vagaIds       = array_column($vagas, 'id');
        $nomeVagaPorId = array_column($vagas, 'nome', 'id');

        $fases      = $this->faseRecrutamentoModel->whereIn('modulo_id', $vagaIds)->orderBy('ordem', 'ASC')->findAll();
        $candidatos = $this->candidatoModel->whereIn('modulo_id', $vagaIds)->orderBy('criado_em', 'ASC')->findAll();

        return Kanban::porNomeDaEtapa($fases, $candidatos, $nomeVagaPorId);
    }

    public function moverFase(string $candidatoId, string $moduloId, string $empresaId, string $cargoId, bool $acessoTotal, string $novaFaseId, string $usuarioId): array
    {
        $this->confirmarVagaDaEmpresa($moduloId, $empresaId);

        $candidato = $this->candidatoModel->where('id', $candidatoId)->where('modulo_id', $moduloId)->first();

        if (! $candidato) {
            throw new NaoEncontradoException('Candidato não encontrado.');
        }

        $this->autorizacaoModuloService->exigirNivel($acessoTotal, $cargoId, $moduloId, 'editar');

        if (! $this->faseRecrutamentoModel->where('id', $novaFaseId)->where('modulo_id', $moduloId)->first()) {
            throw new NaoEncontradoException('Fase não encontrada nesta vaga.');
        }

        if ($candidato['fase_atual_id'] === $novaFaseId) {
            throw new \DomainException('O candidato já está nessa fase.');
        }

        $this->mudancaDeEtapa()->gravar($candidatoId, $candidato, $moduloId, $novaFaseId, $usuarioId);

        return $this->buscarCandidato($candidatoId, $moduloId, $empresaId);
    }

    public function moverFaseGlobalPorNome(string $candidatoId, string $empresaId, string $cargoId, bool $acessoTotal, string $nomeFase, string $usuarioId): array
    {
        $candidato = $this->candidatoModel
            ->select('candidato.*')
            ->join('modulo', 'modulo.id = candidato.modulo_id')
            ->where('candidato.id', $candidatoId)
            ->where('modulo.empresa_id', $empresaId)
            ->first();

        if (! $candidato) {
            throw new NaoEncontradoException('Candidato não encontrado.');
        }

        $faseAlvo = $this->mudancaDeEtapa()->etapaPeloNome($candidato['modulo_id'], $nomeFase);

        return $this->moverFase($candidatoId, $candidato['modulo_id'], $empresaId, $cargoId, $acessoTotal, $faseAlvo['id'], $usuarioId);
    }

    public function buscarCandidato(string $candidatoId, string $moduloId, string $empresaId): array
    {
        return Kanban::apresentar($this->buscarCandidatoBruto($candidatoId, $moduloId, $empresaId));
    }

    /** Linha do banco como está, com a chave do currículo (uso interno). */
    private function buscarCandidatoBruto(string $candidatoId, string $moduloId, string $empresaId): array
    {
        $candidato = $this->candidatoModel
            ->select('candidato.*')
            ->join('modulo', 'modulo.id = candidato.modulo_id')
            ->where('candidato.id', $candidatoId)
            ->where('candidato.modulo_id', $moduloId)
            ->where('modulo.empresa_id', $empresaId)
            ->first();

        if (! $candidato) {
            throw new NaoEncontradoException('Candidato não encontrado.');
        }

        return $candidato;
    }

    public function buscarCandidatoComPermissao(string $candidatoId, string $moduloId, string $empresaId, string $cargoId, bool $acessoTotal): array
    {
        $candidato = $this->buscarCandidato($candidatoId, $moduloId, $empresaId);
        $this->autorizacaoModuloService->exigirNivel($acessoTotal, $cargoId, $moduloId, 'visualizar');

        return $candidato;
    }

    public function excluirCandidato(string $candidatoId, string $moduloId, string $empresaId, string $cargoId, bool $acessoTotal): void
    {
        $candidato = $this->buscarCandidatoBruto($candidatoId, $moduloId, $empresaId);
        $this->autorizacaoModuloService->exigirNivel($acessoTotal, $cargoId, $moduloId, 'editar');

        $this->candidatoModel->delete($candidatoId);

        if (! empty($candidato['curriculo_chave'])) {
            Curriculo::excluir($candidato['curriculo_chave']);
        }
    }

    private function confirmarVagaDaEmpresa(string $moduloId, string $empresaId): void
    {
        $existe = $this->moduloModel
            ->where('id', $moduloId)
            ->where('empresa_id', $empresaId)
            ->where('tipo', 'recrutamento')
            ->first();

        if (! $existe) {
            throw new NaoEncontradoException('Vaga não encontrada.');
        }
    }

    private function mudancaDeEtapa(): MudancaDeEtapa
    {
        return new MudancaDeEtapa($this->candidatoModel, $this->faseRecrutamentoModel, $this->historicoFaseModel);
    }
}