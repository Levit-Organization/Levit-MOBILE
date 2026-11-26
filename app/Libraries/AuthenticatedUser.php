<?php

namespace App\Libraries;

class AuthenticatedUser
{
    public ?string $id = null;
    public ?string $empresaId = null;
    public ?string $cargoId = null;
    public ?string $jti = null;
    public ?int $expiraEm = null;
    public array $permissoes = [];
    public bool $acessoTotal = false;

    /** Autenticado pelo cookie do navegador (e não por Authorization: Bearer). */
    public bool $viaCookie = false;

    /**
     * Nível do cargo em cada tabela (id => visualizar|editar|gerenciar),
     * carregado junto com a autenticação. `null` = não carregado (fora de
     * requisição autenticada): aí quem precisa consulta o banco.
     *
     * @var array<string, string>|null
     */
    public ?array $niveisPorModulo = null;

    public function autenticado(): bool
    {
        return $this->id !== null;
    }

    public function preencher(string $id, string $empresaId, string $cargoId, string $jti, int $expiraEm, array $permissoes, bool $acessoTotal, ?array $niveisPorModulo = null): void
    {
        $this->niveisPorModulo = $niveisPorModulo;
        $this->id          = $id;
        $this->empresaId   = $empresaId;
        $this->cargoId     = $cargoId;
        $this->jti         = $jti;
        $this->expiraEm    = $expiraEm;
        $this->permissoes  = $permissoes;
        $this->acessoTotal = $acessoTotal;
    }

    public function temPermissao(string $codigo): bool
    {
        return $this->acessoTotal || in_array($codigo, $this->permissoes, true);
    }
}