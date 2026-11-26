<?php

namespace App\Services;

use App\Exceptions\NaoEncontradoException;
use App\Models\AutomacaoAcaoModel;
use App\Models\AutomacaoLogModel;
use App\Models\AutomacaoModel;
use App\Models\CampoModuloModel;
use App\Models\ModuloModel;
use App\Models\RegistroModel;
use App\Services\Automacao\ExecucoesDeAutomacao;
use App\Services\Automacao\ListagemDeAutomacoes;
use App\Services\Automacao\ModulosComAutomacao;
use App\Services\Automacao\ValidadorDeAutomacao;

/**
 * Cadastro de automações (criar, alterar, ligar, desligar, excluir e listar),
 * com a conferência de acesso de cada operação. A validação da definição fica
 * em Automacao\ValidadorDeAutomacao, o formato da listagem em
 * Automacao\ListagemDeAutomacoes e o histórico, a reexecução e a simulação
 * em Automacao\ExecucoesDeAutomacao.
 */
class AutomacaoService
{
    protected ModuloModel $moduloModel;
    protected CampoModuloModel $campoModuloModel;
    protected AutomacaoModel $automacaoModel;
    protected AutomacaoAcaoModel $automacaoAcaoModel;
    protected AutorizacaoModuloService $autorizacaoModuloService;
    protected AutomacaoLogModel $automacaoLogModel;
    protected RegistroModel $registroModel;
    protected ListagemDeAutomacoes $listagem;
    protected ExecucoesDeAutomacao $execucoes;
    protected ValidadorDeAutomacao $validador;

    public function __construct()
    {
        $this->moduloModel              = new ModuloModel();
        $this->campoModuloModel         = new CampoModuloModel();
        $this->automacaoModel           = new AutomacaoModel();
        $this->automacaoAcaoModel       = new AutomacaoAcaoModel();
        $this->autorizacaoModuloService = new AutorizacaoModuloService();
        $this->automacaoLogModel        = new AutomacaoLogModel();
        $this->registroModel            = new RegistroModel();
        $this->validador                = new ValidadorDeAutomacao($this->moduloModel, $this->campoModuloModel, $this->autorizacaoModuloService);
        $this->listagem                 = new ListagemDeAutomacoes($this->automacaoModel);
        $this->execucoes                = new ExecucoesDeAutomacao($this->automacaoLogModel, $this->registroModel);
    }

    public function criarAutomacao(string $moduloId, string $empresaId, string $cargoId, bool $acessoTotal, string $usuarioId, array $dados): array
    {
        $this->confirmarModuloDaEmpresa($moduloId, $empresaId);
        $this->autorizacaoModuloService->exigirNivel($acessoTotal, $cargoId, $moduloId, 'gerenciar');

        $definicao = $this->validador->validarDefinicao($moduloId, $empresaId, $dados);

        $db = db_connect();
        $db->transStart();

        ModulosComAutomacao::esquecer($moduloId);
        $automacaoId = $this->automacaoModel->insert([
            'modulo_id'  => $moduloId,
            'criado_por' => $usuarioId,
            'ativo'      => (bool) ($dados['ativo'] ?? true),
        ] + $definicao['campos']);

        $this->gravarAcoes($automacaoId, $definicao['acoes']);

        $db->transComplete();

        if ($db->transStatus() === false) {
            throw new \RuntimeException('Não foi possível criar a automação. Tente novamente.');
        }

        $automacao = $this->buscarAutomacao($automacaoId, $moduloId, $empresaId);
        AtividadeService::registrar('automacao_criada', "Criou a automação “{$automacao['nome']}”", $moduloId);

        return $automacao;
    }

    /**
     * Substitui a definição inteira (gatilhos, condição e ações) numa única
     * transação. O id e o histórico de execuções são preservados.
     */
    public function atualizarAutomacao(string $automacaoId, string $moduloId, string $empresaId, string $cargoId, bool $acessoTotal, array $dados): array
    {
        $this->buscarAutomacao($automacaoId, $moduloId, $empresaId);
        $this->autorizacaoModuloService->exigirNivel($acessoTotal, $cargoId, $moduloId, 'gerenciar');

        $definicao = $this->validador->validarDefinicao($moduloId, $empresaId, $dados);
        $campos    = $definicao['campos'];
        if (array_key_exists('ativo', $dados)) {
            $campos['ativo'] = (bool) $dados['ativo'];
            if ($campos['ativo']) {
                $campos['pausada_por_falhas_em'] = null;
            }
        }

        $db = db_connect();
        $db->transStart();

        ModulosComAutomacao::esquecer($moduloId);
        $this->automacaoModel->update($automacaoId, $campos);
        $this->automacaoAcaoModel->where('automacao_id', $automacaoId)->delete();
        $this->gravarAcoes($automacaoId, $definicao['acoes']);

        $db->transComplete();

        if ($db->transStatus() === false) {
            throw new \RuntimeException('Não foi possível salvar a automação. Tente novamente.');
        }

        $automacao = $this->buscarAutomacao($automacaoId, $moduloId, $empresaId);
        AtividadeService::registrar('automacao_alterada', "Alterou a automação “{$automacao['nome']}”", $moduloId);

        return $automacao;
    }

    private function gravarAcoes(string $automacaoId, array $acoes): void
    {
        foreach ($acoes as $ordem => $acao) {
            $this->automacaoAcaoModel->insert([
                'automacao_id' => $automacaoId,
                'tipo'         => $acao['tipo'],
                'configuracao' => $acao['configuracao'] ?? [],
                'ordem'        => $ordem,
            ]);
        }
    }

    public function listarAutomacoes(string $moduloId, string $empresaId, string $cargoId, bool $acessoTotal): array
    {
        $this->confirmarModuloDaEmpresa($moduloId, $empresaId);
        $this->autorizacaoModuloService->exigirNivel($acessoTotal, $cargoId, $moduloId, 'visualizar');

        return $this->listagem->doModulo($moduloId);
    }

    /**
     * Automações de todos os módulos que o cargo enxerga, numa só chamada.
     * A tela de automações fazia uma requisição por módulo.
     */
    public function listarTodas(string $empresaId, string $cargoId, bool $acessoTotal): array
    {
        $modulos  = $this->moduloModel->select('id, nome, icone, tipo')->where('empresa_id', $empresaId)->findAll();
        $visiveis = $this->autorizacaoModuloService->filtrarModulosComNivel(array_column($modulos, 'id'), $acessoTotal, $cargoId, 'visualizar');
        if ($visiveis === []) {
            return [];
        }

        $porId      = array_column($modulos, null, 'id');
        $automacoes = $this->listagem->dosModulos($visiveis);

        foreach ($automacoes as &$automacao) {
            $automacao['modulo_nome']  = $porId[$automacao['modulo_id']]['nome'] ?? null;
            $automacao['modulo_icone'] = $porId[$automacao['modulo_id']]['icone'] ?? null;
        }
        unset($automacao);

        return $automacoes;
    }

    public function buscarAutomacao(string $automacaoId, string $moduloId, string $empresaId): array
    {
        $automacao = $this->automacaoModel
            ->select('automacao.*')
            ->join('modulo', 'modulo.id = automacao.modulo_id')
            ->where('automacao.id', $automacaoId)
            ->where('automacao.modulo_id', $moduloId)
            ->where('modulo.empresa_id', $empresaId)
            ->first();

        if (! $automacao) {
            throw new NaoEncontradoException('Automação não encontrada.');
        }

        $automacao = ListagemDeAutomacoes::comGatilhos($automacao);
        $automacao['acoes'] = $this->automacaoAcaoModel
            ->where('automacao_id', $automacaoId)
            ->orderBy('ordem', 'ASC')
            ->findAll();

        return $automacao;
    }

    public function alternarAtivo(string $automacaoId, string $moduloId, string $empresaId, string $cargoId, bool $acessoTotal, bool $ativo): array
    {
        $this->buscarAutomacao($automacaoId, $moduloId, $empresaId);
        $this->autorizacaoModuloService->exigirNivel($acessoTotal, $cargoId, $moduloId, 'gerenciar');

        // Reativar manualmente limpa a marca de "pausada por falhas".
        ModulosComAutomacao::esquecer($moduloId);
        $this->automacaoModel->update($automacaoId, $ativo ? ['ativo' => true, 'pausada_por_falhas_em' => null] : ['ativo' => false]);

        $automacao = $this->buscarAutomacao($automacaoId, $moduloId, $empresaId);
        AtividadeService::registrar($ativo ? 'automacao_ligada' : 'automacao_desligada', ($ativo ? 'Ligou' : 'Desligou') . " a automação “{$automacao['nome']}”", $moduloId);

        return $automacao;
    }

    public function excluirAutomacao(string $automacaoId, string $moduloId, string $empresaId, string $cargoId, bool $acessoTotal): void
    {
        $automacao = $this->buscarAutomacao($automacaoId, $moduloId, $empresaId);
        $this->autorizacaoModuloService->exigirNivel($acessoTotal, $cargoId, $moduloId, 'gerenciar');

        ModulosComAutomacao::esquecer($moduloId);
        $this->automacaoModel->delete($automacaoId);
        AtividadeService::registrar('automacao_excluida', "Excluiu a automação “{$automacao['nome']}”", $moduloId);
    }

    /**
     * Execuções mais recentes primeiro, paginadas por cursor (`antes_de` = data
     * da última execução da página anterior).
     */
    public function listarExecucoes(string $automacaoId, string $moduloId, string $empresaId, string $cargoId, bool $acessoTotal, ?string $antesDe, int $limite): array
    {
        $this->buscarAutomacao($automacaoId, $moduloId, $empresaId);
        $this->autorizacaoModuloService->exigirNivel($acessoTotal, $cargoId, $moduloId, 'visualizar');

        return $this->execucoes->listar($automacaoId, $antesDe, $limite);
    }

    /**
     * Mostra o que a automação faria, sem enviar nada. Aceita a definição ainda
     * não salva (formulário) e um registro real do módulo, dados avulsos, ou —
     * sem nenhum dos dois — o registro mais recente.
     */
    public function simular(string $moduloId, string $empresaId, string $cargoId, bool $acessoTotal, array $entrada): array
    {
        $this->confirmarModuloDaEmpresa($moduloId, $empresaId);
        $this->autorizacaoModuloService->exigirNivel($acessoTotal, $cargoId, $moduloId, 'gerenciar');

        // O nome não importa no teste (e pode nem ter sido preenchido ainda).
        $definicao = $this->validador->validarDefinicao($moduloId, $empresaId, ['nome' => 'Teste'] + ($entrada['automacao'] ?? []));

        return $this->execucoes->simular($moduloId, $definicao, $entrada, $this->validador->tipoDoModulo($moduloId) === 'recrutamento');
    }

    /** Coloca de novo na fila uma execução que falhou, com os mesmos dados. */
    public function reexecutar(string $execucaoId, string $automacaoId, string $moduloId, string $empresaId, string $cargoId, bool $acessoTotal): void
    {
        $automacao = $this->buscarAutomacao($automacaoId, $moduloId, $empresaId);
        $this->autorizacaoModuloService->exigirNivel($acessoTotal, $cargoId, $moduloId, 'gerenciar');

        $this->execucoes->reexecutar($automacao, $execucaoId, $automacaoId, $moduloId);
    }

    private function confirmarModuloDaEmpresa(string $moduloId, string $empresaId): void
    {
        if (! $this->moduloModel->where('id', $moduloId)->where('empresa_id', $empresaId)->first()) {
            throw new NaoEncontradoException('Tabela não encontrada.');
        }
    }
}
