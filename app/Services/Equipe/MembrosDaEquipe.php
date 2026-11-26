<?php

namespace App\Services\Equipe;

/**
 * Consultas das pessoas de uma equipe (view usuario_equipe: a conta da
 * pessoa com o vínculo e o cargo dela nesta equipe).
 */
class MembrosDaEquipe
{
    /** A pessoa (pelo e-mail) dentro desta equipe, ou null. */
    public function porEmail(string $empresaId, string $email): ?array
    {
        return db_connect()->table('usuario_equipe')
            ->where('empresa_id', $empresaId)
            ->where('LOWER(email)', mb_strtolower($email))
            ->get()->getRowArray();
    }

    /** A pessoa dentro desta equipe (com o cargo dela aqui), ou null. */
    public function daEquipe(string $empresaId, string $usuarioId): ?array
    {
        return db_connect()->table('usuario_equipe')
            ->where('empresa_id', $empresaId)
            ->where('id', $usuarioId)
            ->get()->getRowArray();
    }

    public function listar(string $empresaId): array
    {
        // criado_em = quando entrou nesta equipe (a conta pode ser mais antiga).
        return db_connect()->table('usuario_equipe ue')
            ->select('ue.id, ue.nome, ue.email, ue.criado_em, ue.cargo_id, cargo.nome AS cargo_nome')
            ->join('cargo', 'cargo.id = ue.cargo_id')
            ->where('ue.empresa_id', $empresaId)
            ->orderBy('ue.criado_em', 'ASC')
            ->get()->getResultArray();
    }

    /** Um membro no formato da listagem (com o nome do cargo). */
    public function comCargo(string $empresaId, string $usuarioId): ?array
    {
        return db_connect()->table('usuario_equipe ue')
            ->select('ue.id, ue.nome, ue.email, ue.criado_em, ue.cargo_id, cargo.nome AS cargo_nome')
            ->join('cargo', 'cargo.id = ue.cargo_id')
            ->where('ue.empresa_id', $empresaId)
            ->where('ue.id', $usuarioId)
            ->get()->getRowArray();
    }

    /**
     * Nome de todos da empresa, para escolher o responsável de um item. Não
     * exige permissão de ver a equipe (quem preenche uma tabela precisa
     * escolher alguém) e por isso não devolve e-mail nem cargo.
     */
    public function pessoas(string $empresaId): array
    {
        return db_connect()->table('usuario_equipe')
            ->select('id, nome')
            ->where('empresa_id', $empresaId)
            ->orderBy('nome', 'ASC')
            ->get()->getResultArray();
    }
}
