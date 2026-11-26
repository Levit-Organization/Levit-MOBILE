<?php

namespace App\Services;

use App\Exceptions\NaoEncontradoException;
use App\Models\CargoModel;
use App\Models\CargoPermissaoModel;
use App\Models\PermissaoModel;

class CargoService
{
    protected CargoModel $cargoModel;
    protected PermissaoModel $permissaoModel;
    protected CargoPermissaoModel $cargoPermissaoModel;

    public function __construct()
    {
        $this->cargoModel          = new CargoModel();
        $this->permissaoModel      = new PermissaoModel();
        $this->cargoPermissaoModel = new CargoPermissaoModel();
    }

    /**
     * Lista os cargos da empresa, cada um já com suas permissões —
     * duas consultas ao todo, não importa quantos cargos existam.
     */
    public function listarCargos(string $empresaId): array
    {
        $cargos = $this->cargoModel->where('empresa_id', $empresaId)->findAll();

        if (empty($cargos)) {
            return [];
        }

        $todasPermissoes = $this->cargoPermissaoModel
            ->select('cargo_permissao.cargo_id, permissao.codigo')
            ->join('permissao', 'permissao.id = cargo_permissao.permissao_id')
            ->whereIn('cargo_permissao.cargo_id', array_column($cargos, 'id'))
            ->findAll();

        $permissoesPorCargo = [];
        foreach ($todasPermissoes as $linha) {
            $permissoesPorCargo[$linha['cargo_id']][] = $linha['codigo'];
        }

        foreach ($cargos as &$cargo) {
            $cargo['permissoes'] = $permissoesPorCargo[$cargo['id']] ?? [];
        }
        unset($cargo);

        return $cargos;
    }

    /**
     * @throws \DomainException se o nome já existir na empresa, ou algum
     *         código de permissão informado não existir no catálogo
     */
    public function criarCargo(string $empresaId, string $nome, array $codigosPermissoes): array
    {
        if ($this->cargoModel->where('empresa_id', $empresaId)->where('nome', $nome)->first()) {
            throw new \DomainException('Já existe um cargo com esse nome nesta empresa.');
        }

        $permissoesValidas = empty($codigosPermissoes)
            ? []
            : $this->permissaoModel->whereIn('codigo', $codigosPermissoes)->findAll();

        if (count($permissoesValidas) !== count(array_unique($codigosPermissoes))) {
            throw new \DomainException('Uma ou mais permissões informadas não existem.');
        }

        (new LimiteDeAcesso())->exigirPodeConcederPermissoes($codigosPermissoes);

        $db = db_connect();
        $db->transStart();

        $cargoId = $this->cargoModel->insert([
            'empresa_id' => $empresaId,
            'nome'       => $nome,
        ]);

        foreach ($permissoesValidas as $permissao) {
            $this->cargoPermissaoModel->insert([
                'cargo_id'     => $cargoId,
                'permissao_id' => $permissao['id'],
            ]);
        }

        $db->transComplete();

        if ($db->transStatus() === false) {
            throw new \RuntimeException('Não foi possível criar o cargo. Tente novamente.');
        }

        $cargo = $this->cargoModel->find($cargoId);
        $cargo['permissoes'] = array_column($permissoesValidas, 'codigo');

        AtividadeService::registrar('cargo_criado', "Criou o cargo “{$nome}”");

        return $cargo;
    }

    /**
     * Atualização é uma substituição completa (semântica de PUT): o nome
     * e a lista de permissões enviados passam a ser o estado final do
     * cargo — não é um merge com o que já existia antes.
     *
     * @throws NaoEncontradoException se o cargo não existir/pertencer à empresa
     * @throws \DomainException se o novo nome colidir com outro cargo da
     *         empresa, ou algum código de permissão não existir no catálogo
     */
    public function atualizarCargo(string $empresaId, string $cargoId, string $nome, array $codigosPermissoes): array
    {
        $cargo = $this->cargoModel
            ->where('id', $cargoId)
            ->where('empresa_id', $empresaId)
            ->first();

        if (! $cargo) {
            throw new NaoEncontradoException('Cargo não encontrado.');
        }

        $limite = new LimiteDeAcesso();
        $limite->exigirQueNaoSejaOProprioCargo($cargoId);
        $limite->exigirPodeAtuarNoCargo($cargoId);
        $limite->exigirPodeConcederPermissoes($codigosPermissoes);

        $conflito = $this->cargoModel
            ->where('empresa_id', $empresaId)
            ->where('nome', $nome)
            ->where('id !=', $cargoId)
            ->first();

        if ($conflito) {
            throw new \DomainException('Já existe um cargo com esse nome nesta empresa.');
        }

        $permissoesValidas = empty($codigosPermissoes)
            ? []
            : $this->permissaoModel->whereIn('codigo', $codigosPermissoes)->findAll();

        if (count($permissoesValidas) !== count(array_unique($codigosPermissoes))) {
            throw new \DomainException('Uma ou mais permissões informadas não existem.');
        }

        $db = db_connect();
        $db->transStart();

        $this->cargoModel->update($cargoId, ['nome' => $nome]);

        $this->cargoPermissaoModel->where('cargo_id', $cargoId)->delete();

        foreach ($permissoesValidas as $permissao) {
            $this->cargoPermissaoModel->insert([
                'cargo_id'     => $cargoId,
                'permissao_id' => $permissao['id'],
            ]);
        }

        $db->transComplete();

        if ($db->transStatus() === false) {
            throw new \RuntimeException('Não foi possível atualizar o cargo. Tente novamente.');
        }

        $cargoAtualizado = $this->cargoModel->find($cargoId);
        $cargoAtualizado['permissoes'] = array_column($permissoesValidas, 'codigo');

        AtividadeService::registrar('cargo_alterado', "Alterou o cargo “{$nome}”");

        return $cargoAtualizado;
    }

    /**
     * @throws NaoEncontradoException se o cargo não existir/pertencer à empresa
     * @throws \DomainException se ainda existirem membros vinculados ao cargo
     */
    public function excluirCargo(string $empresaId, string $cargoId): void
    {
        $cargo = $this->cargoModel
            ->where('id', $cargoId)
            ->where('empresa_id', $empresaId)
            ->first();

        if (! $cargo) {
            throw new NaoEncontradoException('Cargo não encontrado.');
        }

        (new LimiteDeAcesso())->exigirPodeAtuarNoCargo($cargoId);

        $totalMembros = db_connect()->table('membro')->where('cargo_id', $cargoId)->countAllResults();

        if ($totalMembros > 0) {
            throw new \DomainException('Não é possível excluir um cargo que possui membros vinculados.');
        }

        // cargo_permissao e cargo_modulo_permissao têm ON DELETE CASCADE
        // para cargo_id — não precisam de limpeza manual aqui.
        $this->cargoModel->delete($cargoId);
        AtividadeService::registrar('cargo_excluido', "Excluiu o cargo “{$cargo['nome']}”");
    }
}