<?php

namespace App\Services;

use App\Exceptions\AcessoNegadoException;
use App\Libraries\AuthenticatedUser;

/**
 * Regra única contra escalada de privilégio dentro da empresa: ninguém
 * concede mais poder do que já tem, nem mexe no próprio cargo.
 *
 * Sem isso, quem tinha só "gerenciar equipe" podia se colocar no cargo Admin,
 * e quem tinha só "gerenciar cargos" podia acrescentar ao próprio cargo
 * qualquer permissão. Quem tem acesso total continua podendo tudo.
 *
 * Fora de uma requisição autenticada (seeds, comandos), não restringe.
 */
class LimiteDeAcesso
{
    private const NIVEIS = ['visualizar' => 1, 'editar' => 2, 'gerenciar' => 3];

    public function __construct(private ?AuthenticatedUser $usuario = null)
    {
        $this->usuario ??= service('authenticatedUser');
    }

    private function restringe(): bool
    {
        return $this->usuario->autenticado() && ! $this->usuario->acessoTotal;
    }

    /** Atribuir, alterar ou remover alguém deste cargo, ou editar o cargo em si. */
    public function exigirPodeAtuarNoCargo(string $cargoId): void
    {
        if (! $this->restringe()) {
            return;
        }

        $linhas = db_connect()->query(
            'SELECT c.acesso_total, p.codigo
               FROM cargo c
          LEFT JOIN cargo_permissao cp ON cp.cargo_id = c.id
          LEFT JOIN permissao p ON p.id = cp.permissao_id
              WHERE c.id = ?',
            [$cargoId]
        )->getResultArray();

        if ($linhas === []) {
            return; // quem chamou já confirmou que o cargo existe na empresa
        }

        if (in_array($linhas[0]['acesso_total'], [true, 1, '1', 't', 'true'], true)) {
            throw new AcessoNegadoException('Só quem tem acesso total pode atribuir ou alterar um cargo com acesso total.');
        }

        $this->exigirPodeConcederPermissoes(array_filter(array_column($linhas, 'codigo')));
    }

    /** @param string[] $codigos */
    public function exigirPodeConcederPermissoes(array $codigos): void
    {
        if (! $this->restringe()) {
            return;
        }

        $faltando = array_diff($codigos, $this->usuario->permissoes);
        if ($faltando !== []) {
            throw new AcessoNegadoException('Você não pode conceder permissões que você mesmo não tem.');
        }
    }

    public function exigirQueNaoSejaOProprioCargo(string $cargoId): void
    {
        if ($this->restringe() && $cargoId === $this->usuario->cargoId) {
            throw new AcessoNegadoException('Você não pode alterar o seu próprio cargo. Peça a alguém com acesso total.');
        }
    }

    public function exigirQueNaoSejaVoceMesmo(string $usuarioId, string $mensagem): void
    {
        if ($this->usuario->autenticado() && $usuarioId === $this->usuario->id) {
            throw new \DomainException($mensagem);
        }
    }

    /** Conceder um nível numa tabela exige ter, nela, pelo menos o mesmo nível. */
    public function exigirPodeConcederNivel(string $moduloId, string $nivel): void
    {
        if (! $this->restringe()) {
            return;
        }

        $meu = (new AutorizacaoModuloService())->nivelDeAcesso($this->usuario->cargoId, $moduloId);
        if ($meu === null || self::NIVEIS[$meu] < (self::NIVEIS[$nivel] ?? PHP_INT_MAX)) {
            throw new AcessoNegadoException('Você não pode conceder um nível maior do que o seu nesta tabela.');
        }
    }
}
