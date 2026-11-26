<?php

namespace App\Services\Financeiro;

use App\Exceptions\AcessoNegadoException;
use App\Exceptions\NaoEncontradoException;
use App\Models\CategoriaFinanceiraModel;

/**
 * Categorias de lançamento — escopo: empresa (compartilhadas entre todos os
 * módulos financeiros da mesma empresa, não por módulo individual).
 */
final class Categorias
{
    public function __construct(private readonly CategoriaFinanceiraModel $categoriaModel)
    {
    }

    public function listar(string $empresaId): array
    {
        return $this->categoriaModel
            ->where('empresa_id', $empresaId)
            ->orderBy('nome', 'ASC')
            ->findAll();
    }

    /**
     * @throws \DomainException se já existir categoria com esse nome na empresa
     */
    public function criar(string $empresaId, string $nome): array
    {
        $nome = trim($nome);

        if ($nome === '') {
            throw new \DomainException('O nome da categoria não pode ficar vazio.');
        }

        $this->exigirQuemEditaFinanceiro($empresaId);

        $existente = $this->categoriaModel
            ->where('empresa_id', $empresaId)
            ->where('nome', $nome)
            ->first();

        if ($existente) {
            throw new \DomainException('Já existe uma categoria com esse nome nesta empresa.');
        }

        $categoriaId = $this->categoriaModel->insert([
            'empresa_id' => $empresaId,
            'nome'       => $nome,
        ]);

        return $this->categoriaModel->find($categoriaId);
    }

    /**
     * Não bloqueia a exclusão se houver lançamentos usando a categoria:
     * a FK é ON DELETE SET NULL, então os lançamentos continuam válidos,
     * só perdem o rótulo de categoria. Diferente da exclusão de cargo
     * (onde perder o vínculo quebraria acesso de alguém), aqui é uma
     * perda de rótulo, reversível e de baixo risco — não precisa da
     * mesma checagem proativa.
     *
     * @throws NaoEncontradoException se a categoria não existir/pertencer à empresa
     */
    public function excluir(string $empresaId, string $categoriaId): void
    {
        $categoria = $this->categoriaModel
            ->where('id', $categoriaId)
            ->where('empresa_id', $empresaId)
            ->first();

        if (! $categoria) {
            throw new NaoEncontradoException('Categoria não encontrada.');
        }

        $this->exigirQuemEditaFinanceiro($empresaId);

        $this->categoriaModel->delete($categoriaId);
    }

    /** A categoria existe e é desta empresa (usado ao validar um lançamento). */
    public function daEmpresa(string $empresaId, mixed $categoriaId): bool
    {
        $categoria = $this->categoriaModel
            ->where('id', $categoriaId)
            ->where('empresa_id', $empresaId)
            ->first();

        return (bool) $categoria;
    }

    /**
     * Categorias valem para a empresa toda: só quem pode editar alguma tabela
     * financeira (ou tem acesso total) cria ou exclui.
     *
     * @throws AcessoNegadoException
     */
    private function exigirQuemEditaFinanceiro(string $empresaId): void
    {
        $usuario = service('authenticatedUser');
        if (! $usuario->autenticado() || $usuario->acessoTotal) {
            return;
        }

        $pode = db_connect()->query(
            "SELECT 1 FROM cargo_modulo_permissao cmp
               JOIN modulo m ON m.id = cmp.modulo_id
              WHERE cmp.cargo_id = ? AND m.empresa_id = ? AND m.tipo = 'financeiro'
                AND cmp.nivel IN ('editar', 'gerenciar')
              LIMIT 1",
            [$usuario->cargoId, $empresaId]
        )->getRow();

        if (! $pode) {
            throw new AcessoNegadoException('Só quem pode editar uma tabela financeira cria ou exclui categorias.');
        }
    }
}
