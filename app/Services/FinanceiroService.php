<?php

namespace App\Services;

use App\Exceptions\NaoEncontradoException;
use App\Models\CategoriaFinanceiraModel;
use App\Models\LancamentoFinanceiroModel;
use App\Models\ModuloModel;
use App\Services\Financeiro\Categorias;
use App\Services\Financeiro\Extrato;
use App\Services\Financeiro\ValidadorDeLancamento;

/**
 * Módulos do tipo "financeiro": categorias da empresa e lançamentos de cada
 * módulo, com campos extras guardados num registro vinculado.
 *
 * As categorias ficam em Financeiro\Categorias, a validação dos campos fixos
 * em Financeiro\ValidadorDeLancamento e o saldo em Financeiro\Extrato.
 */
class FinanceiroService
{
    protected ModuloModel $moduloModel;
    protected CategoriaFinanceiraModel $categoriaModel;
    protected LancamentoFinanceiroModel $lancamentoModel;
    protected RegistroService $registroService;
    protected AutorizacaoModuloService $autorizacaoModuloService;

    public function __construct()
    {
        $this->moduloModel              = new ModuloModel();
        $this->categoriaModel           = new CategoriaFinanceiraModel();
        $this->lancamentoModel          = new LancamentoFinanceiroModel();
        $this->registroService          = new RegistroService();
        $this->autorizacaoModuloService = new AutorizacaoModuloService();
    }

    // ==========================================================
    // Categorias — escopo: empresa (compartilhadas entre todos os
    // módulos financeiros da mesma empresa, não por módulo individual)
    // ==========================================================

    public function listarCategorias(string $empresaId): array
    {
        return $this->categorias()->listar($empresaId);
    }

    /**
     * @throws \DomainException se já existir categoria com esse nome na empresa
     */
    public function criarCategoria(string $empresaId, string $nome): array
    {
        return $this->categorias()->criar($empresaId, $nome);
    }

    /**
     * Não bloqueia a exclusão se houver lançamentos usando a categoria (ver
     * Financeiro\Categorias::excluir).
     *
     * @throws NaoEncontradoException se a categoria não existir/pertencer à empresa
     */
    public function excluirCategoria(string $empresaId, string $categoriaId): void
    {
        $this->categorias()->excluir($empresaId, $categoriaId);
    }

    // ==========================================================
    // Lançamentos — escopo: um módulo específico do tipo 'financeiro'
    // ==========================================================

    /**
     * Retorna os lançamentos ordenados por data, com o saldo acumulado
     * calculado linha a linha (respeitando o filtro de categoria, se
     * houver) e um resumo de totais do conjunto filtrado.
     *
     * @throws NaoEncontradoException se o módulo não existir/pertencer à
     *         empresa, ou não for do tipo 'financeiro'
     */
    public function listarLancamentos(string $moduloId, string $empresaId, string $cargoId, bool $acessoTotal, ?string $categoriaId = null): array
    {
        $this->confirmarModuloFinanceiro($moduloId, $empresaId);
        $this->autorizacaoModuloService->exigirNivel($acessoTotal, $cargoId, $moduloId, 'visualizar');

        $query = $this->lancamentoModel
            ->select('lancamento_financeiro.*, categoria_financeira.nome as categoria_nome')
            ->join('categoria_financeira', 'categoria_financeira.id = lancamento_financeiro.categoria_id', 'left')
            ->where('lancamento_financeiro.modulo_id', $moduloId);

        if ($categoriaId !== null) {
            $query->where('lancamento_financeiro.categoria_id', $categoriaId);
        }

        $lancamentos = $query
            ->orderBy('lancamento_financeiro.data_lancamento', 'ASC')
            ->orderBy('lancamento_financeiro.criado_em', 'ASC')
            ->findAll();

        return Extrato::comSaldo($lancamentos);
    }

    /**
     * Campos extras (definidos pela empresa via /modulos/{id}/campos, o
     * mesmo mecanismo do módulo "dados") são passados em
     * $dados['campos_extras'] e viram um registro vinculado — reaproveita
     * 100% a validação e o armazenamento que o RegistroService já tem.
     *
     * @throws NaoEncontradoException se o módulo não existir/pertencer à
     *         empresa, ou não for do tipo 'financeiro'
     * @throws \DomainException se os campos fixos do lançamento forem inválidos
     */
    public function criarLancamento(string $moduloId, string $empresaId, string $cargoId, bool $acessoTotal, string $usuarioId, array $dados): array
    {
        $this->confirmarModuloFinanceiro($moduloId, $empresaId);
        $this->autorizacaoModuloService->exigirNivel($acessoTotal, $cargoId, $moduloId, 'editar');

        $camposValidados = $this->validarCamposLancamento($empresaId, $dados, false);

        $db = db_connect();
        $db->transStart();

        $registro = $this->registroService->criarRegistro(
            $moduloId,
            $empresaId,
            $cargoId,
            $acessoTotal,
            $usuarioId,
            ['dados' => $dados['campos_extras'] ?? []]
        );

        $lancamentoId = $this->lancamentoModel->insert(array_merge($camposValidados, [
            'modulo_id'   => $moduloId,
            'registro_id' => $registro['id'],
        ]));

        $db->transComplete();

        if ($db->transStatus() === false) {
            throw new \RuntimeException('Não foi possível criar o lançamento. Tente novamente.');
        }

        return $this->buscarLancamentoCompleto($lancamentoId, $moduloId, $empresaId);
    }

    /**
     * Atualização parcial: só os campos presentes em $dados são alterados
     * (mesma semântica do PUT /equipe/{id}).
     *
     * @throws NaoEncontradoException se módulo ou lançamento não existirem/
     *         pertencerem à empresa
     * @throws \DomainException se os campos fixos informados forem inválidos
     */
    public function atualizarLancamento(string $lancamentoId, string $moduloId, string $empresaId, string $cargoId, bool $acessoTotal, string $usuarioId, array $dados): array
    {
        $this->confirmarModuloFinanceiro($moduloId, $empresaId);
        $lancamento = $this->buscarLancamento($lancamentoId, $moduloId);
        $this->autorizacaoModuloService->exigirNivel($acessoTotal, $cargoId, $moduloId, 'editar');

        $camposValidados = $this->validarCamposLancamento($empresaId, $dados, true);

        $db = db_connect();
        $db->transStart();

        if (! empty($camposValidados)) {
            $this->lancamentoModel->update($lancamentoId, $camposValidados);
        }

        if (isset($dados['campos_extras'])) {
            $this->registroService->atualizarRegistro(
                $lancamento['registro_id'],
                $moduloId,
                $empresaId,
                $cargoId,
                $acessoTotal,
                $usuarioId,
                ['dados' => $dados['campos_extras']]
            );
        }

        $db->transComplete();

        if ($db->transStatus() === false) {
            throw new \RuntimeException('Não foi possível atualizar o lançamento. Tente novamente.');
        }

        return $this->buscarLancamentoCompleto($lancamentoId, $moduloId, $empresaId);
    }

    /**
     * Exclui pelo registro vinculado (mesmo padrão do ArquivoService): a
     * FK registro_id → registro é CASCADE, então a linha de
     * lancamento_financeiro some junto, numa única operação atômica.
     *
     * @throws NaoEncontradoException se módulo ou lançamento não existirem/
     *         pertencerem à empresa
     */
    public function excluirLancamento(string $lancamentoId, string $moduloId, string $empresaId, string $cargoId, bool $acessoTotal): void
    {
        $this->confirmarModuloFinanceiro($moduloId, $empresaId);
        $lancamento = $this->buscarLancamento($lancamentoId, $moduloId);
        $this->autorizacaoModuloService->exigirNivel($acessoTotal, $cargoId, $moduloId, 'editar');

        $this->registroService->excluirRegistro($lancamento['registro_id'], $moduloId, $empresaId, $cargoId, $acessoTotal);
    }

    // ==========================================================
    // Helpers privados
    // ==========================================================

    /**
     * @throws NaoEncontradoException se o módulo não existir, não
     *         pertencer à empresa, ou não for do tipo 'financeiro'
     */
    private function confirmarModuloFinanceiro(string $moduloId, string $empresaId): void
    {
        $modulo = $this->moduloModel
            ->where('id', $moduloId)
            ->where('empresa_id', $empresaId)
            ->where('tipo', 'financeiro')
            ->first();

        if (! $modulo) {
            throw new NaoEncontradoException('Financeiro não encontrado.');
        }
    }

    /**
     * @throws NaoEncontradoException se o lançamento não existir/pertencer ao módulo
     */
    private function buscarLancamento(string $lancamentoId, string $moduloId): array
    {
        $lancamento = $this->lancamentoModel
            ->where('id', $lancamentoId)
            ->where('modulo_id', $moduloId)
            ->first();

        if (! $lancamento) {
            throw new NaoEncontradoException('Lançamento não encontrado.');
        }

        return $lancamento;
    }

    private function buscarLancamentoCompleto(string $lancamentoId, string $moduloId, string $empresaId): array
    {
        $lancamento = $this->lancamentoModel
            ->select('lancamento_financeiro.*, categoria_financeira.nome as categoria_nome')
            ->join('categoria_financeira', 'categoria_financeira.id = lancamento_financeiro.categoria_id', 'left')
            ->where('lancamento_financeiro.id', $lancamentoId)
            ->first();

        $registro                    = $this->registroService->buscarRegistro($lancamento['registro_id'], $moduloId, $empresaId);
        $lancamento['campos_extras'] = $registro['dados'];

        return $lancamento;
    }

    /**
     * Valida e monta só os campos FIXOS do lançamento (ver
     * Financeiro\ValidadorDeLancamento); a categoria tem de ser da empresa.
     *
     * @throws \DomainException
     */
    private function validarCamposLancamento(string $empresaId, array $dados, bool $parcial): array
    {
        $categorias = $this->categorias();

        return ValidadorDeLancamento::validar($dados, $parcial, static fn (mixed $categoriaId): bool => $categorias->daEmpresa($empresaId, $categoriaId));
    }

    private function categorias(): Categorias
    {
        return new Categorias($this->categoriaModel);
    }
}
