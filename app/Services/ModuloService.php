<?php

namespace App\Services;

use App\Exceptions\NaoEncontradoException;
use App\Models\AutomacaoModel;
use App\Models\CampoModuloModel;
use App\Models\CandidatoModel;
use App\Models\FaseRecrutamentoModel;
use App\Models\ModuloModel;
use App\Services\Modulos\ColunasDoModulo;
use App\Services\Modulos\FasesDoModulo;
use App\Services\Modulos\LigacoesDoModulo;

/**
 * Cadastro das tabelas (módulos) e a conferência de acesso a elas. As
 * colunas ficam em Modulos\ColunasDoModulo, as fases do recrutamento em
 * Modulos\FasesDoModulo e as ligações entre tabelas em
 * Modulos\LigacoesDoModulo.
 */
class ModuloService
{
    protected ModuloModel $moduloModel;
    protected CampoModuloModel $campoModuloModel;
    protected FaseRecrutamentoModel $faseRecrutamentoModel;
    protected CandidatoModel $candidatoModel;
    protected AutomacaoModel $automacaoModel;
    protected AutorizacaoModuloService $autorizacaoModuloService;
    protected ColunasDoModulo $colunas;
    protected FasesDoModulo $fases;
    protected LigacoesDoModulo $ligacoes;

    public function __construct()
    {
        $this->moduloModel              = new ModuloModel();
        $this->campoModuloModel         = new CampoModuloModel();
        $this->faseRecrutamentoModel    = new FaseRecrutamentoModel();
        $this->candidatoModel           = new CandidatoModel();
        $this->automacaoModel           = new AutomacaoModel();
        $this->autorizacaoModuloService = new AutorizacaoModuloService();
        $this->colunas                  = new ColunasDoModulo($this->moduloModel, $this->campoModuloModel, $this->automacaoModel, $this->autorizacaoModuloService);
        $this->fases                    = new FasesDoModulo($this->faseRecrutamentoModel, $this->candidatoModel);
        $this->ligacoes                 = new LigacoesDoModulo($this->moduloModel, $this->campoModuloModel, $this->autorizacaoModuloService);
    }

    public function criarModulo(string $empresaId, string $usuarioId, string $cargoId, array $dados, bool $acessoTotal = false): array
    {
        $tipo   = $dados['tipo'] ?? 'dados';
        $campos = $dados['campos'] ?? [];
        $fases  = $dados['fases'] ?? [];

        if (! in_array($tipo, ['dados', 'arquivo', 'recrutamento', 'financeiro'], true)) {
            throw new \DomainException("Tipo de tabela inválida: '{$tipo}'.");
        }

        if ($tipo === 'dados' && empty($campos)) {
            throw new \DomainException('A tabela precisa de pelo menos uma coluna.');
        }

        if ($tipo === 'recrutamento' && empty($fases)) {
            throw new \DomainException('A tabela de recrutamento precisa de pelo menos uma fase.');
        }

        foreach ($campos as $campo) {
            $this->colunas->validar($campo, $empresaId, $cargoId, $acessoTotal);
        }

        foreach ($fases as $nomeFase) {
            if (! is_string($nomeFase) || trim($nomeFase) === '') {
                throw new \DomainException('Toda fase precisa de um nome.');
            }
        }

        $db = db_connect();
        $db->transStart();

        $moduloId = $this->moduloModel->insert([
            'empresa_id' => $empresaId,
            'nome'       => $dados['nome'],
            'icone'      => $dados['icone'] ?? null,
            'tipo'       => $tipo,
            'criado_por' => $usuarioId,
        ]);

        foreach ($campos as $ordem => $campo) {
            $this->campoModuloModel->insert($this->colunas->paraGravar($moduloId, $campo, $ordem));
        }

        foreach ($fases as $ordem => $nomeFase) {
            $this->faseRecrutamentoModel->insert([
                'modulo_id' => $moduloId,
                'nome'      => $nomeFase,
                'ordem'     => $ordem,
            ]);
        }

        // Quem criou já enxerga o que acabou de criar, mesmo sem acesso_total.
        $this->autorizacaoModuloService->concederGerenciar($cargoId, $moduloId);

        $db->transComplete();

        if ($db->transStatus() === false) {
            throw new \RuntimeException('Não foi possível criar a tabela. Tente novamente.');
        }

        AtividadeService::registrar('tabela_criada', "Criou a tabela “{$dados['nome']}”", $moduloId, $dados['nome']);

        return $this->buscarModuloComCampos($moduloId, $empresaId, $cargoId, true);
    }

    /**
     * Lista só os módulos que o cargo tem algum nível de acesso —
     * módulos sem nenhuma concessão ficam invisíveis (exceto acesso_total).
     */
    public function listarModulos(string $empresaId, string $cargoId, bool $acessoTotal): array
    {
        $builder = $this->moduloModel
            ->select('modulo.*, COUNT(registro.id) AS total_registros')
            ->join('registro', 'registro.modulo_id = modulo.id', 'left')
            ->where('modulo.empresa_id', $empresaId);

        if (! $acessoTotal) {
            $builder
                ->join('cargo_modulo_permissao', 'cargo_modulo_permissao.modulo_id = modulo.id')
                ->where('cargo_modulo_permissao.cargo_id', $cargoId);
        }

        return $builder
            ->groupBy('modulo.id')
            ->orderBy('modulo.criado_em', 'ASC')
            ->findAll();
    }

    public function buscarModuloComCampos(string $moduloId, string $empresaId, string $cargoId, bool $acessoTotal): array
    {
        $modulo = $this->moduloModel
            ->where('id', $moduloId)
            ->where('empresa_id', $empresaId)
            ->first();

        if (! $modulo) {
            throw new NaoEncontradoException('Tabela não encontrada.');
        }

        $this->autorizacaoModuloService->exigirNivel($acessoTotal, $cargoId, $moduloId, 'visualizar');

        $modulo['campos'] = $this->campoModuloModel
            ->where('modulo_id', $moduloId)
            ->orderBy('ordem', 'ASC')
            ->findAll();

        $modulo = $this->ligacoes->anexar($modulo, $cargoId, $acessoTotal);

        if ($modulo['tipo'] === 'recrutamento') {
            $modulo['fases'] = $this->faseRecrutamentoModel
                ->where('modulo_id', $moduloId)
                ->orderBy('ordem', 'ASC')
                ->findAll();
        }

        return $modulo;
    }

    public function atualizarModulo(string $moduloId, string $empresaId, string $cargoId, bool $acessoTotal, array $dados): array
    {
        $this->buscarModuloComCampos($moduloId, $empresaId, $cargoId, $acessoTotal); // confirma existência + visualizar
        $this->autorizacaoModuloService->exigirNivel($acessoTotal, $cargoId, $moduloId, 'gerenciar');

        $camposParaAtualizar = [];

        if (isset($dados['nome'])) {
            $camposParaAtualizar['nome'] = $dados['nome'];
        }

        if (array_key_exists('icone', $dados)) {
            $camposParaAtualizar['icone'] = $dados['icone'];
        }

        if (! empty($camposParaAtualizar)) {
            $this->moduloModel->update($moduloId, $camposParaAtualizar);
        }

        $modulo = $this->buscarModuloComCampos($moduloId, $empresaId, $cargoId, $acessoTotal);
        if (! empty($camposParaAtualizar)) {
            AtividadeService::registrar('tabela_alterada', "Alterou nome ou ícone da tabela “{$modulo['nome']}”", $moduloId, $modulo['nome']);
        }

        return $modulo;
    }

    public function excluirModulo(string $moduloId, string $empresaId, string $cargoId, bool $acessoTotal): void
    {
        $modulo = $this->buscarModuloComCampos($moduloId, $empresaId, $cargoId, $acessoTotal);
        $this->autorizacaoModuloService->exigirNivel($acessoTotal, $cargoId, $moduloId, 'gerenciar');

        // Ligações de outras tabelas para esta (as dela mesma somem junto).
        $dependentes = $this->campoModuloModel
            ->select('modulo.nome')
            ->join('modulo', 'modulo.id = campo_modulo.modulo_id')
            ->where('campo_modulo.modulo_vinculado_id', $moduloId)
            ->where('campo_modulo.modulo_id !=', $moduloId)
            ->findAll();

        if ($dependentes !== []) {
            $nomes = implode('”, “', array_unique(array_column($dependentes, 'nome')));
            throw new \DomainException("Não é possível excluir: a tabela “{$nomes}” tem uma coluna ligada a esta. Exclua essa coluna antes.");
        }

        $this->moduloModel->delete($moduloId);
        AtividadeService::registrar('tabela_excluida', "Excluiu a tabela “{$modulo['nome']}”", $moduloId, $modulo['nome']);
    }

    // Colunas (ver Modulos\ColunasDoModulo).

    public function adicionarCampo(string $moduloId, string $empresaId, string $cargoId, bool $acessoTotal, array $campo): array
    {
        $this->buscarModuloComCampos($moduloId, $empresaId, $cargoId, $acessoTotal);
        $this->autorizacaoModuloService->exigirNivel($acessoTotal, $cargoId, $moduloId, 'gerenciar');

        $this->colunas->adicionar($moduloId, $empresaId, $cargoId, $acessoTotal, $campo);

        return $this->buscarModuloComCampos($moduloId, $empresaId, $cargoId, $acessoTotal);
    }

    public function atualizarCampo(string $campoId, string $moduloId, string $empresaId, string $cargoId, bool $acessoTotal, array $dados): array
    {
        $this->autorizacaoModuloService->exigirNivel($acessoTotal, $cargoId, $moduloId, 'gerenciar');
        $this->colunas->atualizar($campoId, $moduloId, $empresaId, $dados);

        return $this->buscarModuloComCampos($moduloId, $empresaId, $cargoId, $acessoTotal);
    }

    public function reordenarCampos(string $moduloId, string $empresaId, string $cargoId, bool $acessoTotal, array $ordemCampoIds): array
    {
        $this->buscarModuloComCampos($moduloId, $empresaId, $cargoId, $acessoTotal);
        $this->autorizacaoModuloService->exigirNivel($acessoTotal, $cargoId, $moduloId, 'gerenciar');

        $this->colunas->reordenar($moduloId, $ordemCampoIds);

        return $this->buscarModuloComCampos($moduloId, $empresaId, $cargoId, $acessoTotal);
    }

    public function excluirCampo(string $campoId, string $moduloId, string $empresaId, string $cargoId, bool $acessoTotal): void
    {
        $this->autorizacaoModuloService->exigirNivel($acessoTotal, $cargoId, $moduloId, 'gerenciar');
        $this->colunas->excluir($campoId, $moduloId, $empresaId);
    }

    // Fases do recrutamento (ver Modulos\FasesDoModulo).

    public function adicionarFase(string $moduloId, string $empresaId, string $cargoId, bool $acessoTotal, string $nomeFase): array
    {
        $this->buscarModuloComCampos($moduloId, $empresaId, $cargoId, $acessoTotal);
        $this->autorizacaoModuloService->exigirNivel($acessoTotal, $cargoId, $moduloId, 'gerenciar');

        $this->fases->adicionar($moduloId, $nomeFase);

        return $this->buscarModuloComCampos($moduloId, $empresaId, $cargoId, $acessoTotal);
    }

    public function atualizarFase(string $faseId, string $moduloId, string $empresaId, string $cargoId, bool $acessoTotal, string $novoNome): array
    {
        $this->autorizacaoModuloService->exigirNivel($acessoTotal, $cargoId, $moduloId, 'gerenciar');
        $this->fases->renomear($faseId, $moduloId, $empresaId, $novoNome);

        return $this->buscarModuloComCampos($moduloId, $empresaId, $cargoId, $acessoTotal);
    }

    public function reordenarFases(string $moduloId, string $empresaId, string $cargoId, bool $acessoTotal, array $ordemFaseIds): array
    {
        $this->buscarModuloComCampos($moduloId, $empresaId, $cargoId, $acessoTotal);
        $this->autorizacaoModuloService->exigirNivel($acessoTotal, $cargoId, $moduloId, 'gerenciar');

        $this->fases->reordenar($moduloId, $ordemFaseIds);

        return $this->buscarModuloComCampos($moduloId, $empresaId, $cargoId, $acessoTotal);
    }

    public function excluirFase(string $faseId, string $moduloId, string $empresaId, string $cargoId, bool $acessoTotal): void
    {
        $this->autorizacaoModuloService->exigirNivel($acessoTotal, $cargoId, $moduloId, 'gerenciar');
        $this->fases->excluir($faseId, $moduloId, $empresaId);
    }
}
