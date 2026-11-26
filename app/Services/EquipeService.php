<?php

namespace App\Services;

use App\Exceptions\NaoEncontradoException;
use App\Models\CargoModel;
use App\Models\ConviteModel;
use App\Models\EmpresaModel;
use App\Models\MembroModel;
use App\Models\UsuarioModel;
use App\Services\Equipe\AceiteDeConvite;
use App\Services\Equipe\ConvitesDaEquipe;
use App\Services\Equipe\MembrosDaEquipe;

/**
 * A equipe de uma empresa: quem faz parte, com que cargo, e os convites.
 * Os convites ficam em Equipe\ConvitesDaEquipe, o aceite em
 * Equipe\AceiteDeConvite e as consultas dos membros em
 * Equipe\MembrosDaEquipe; aqui ficam as alterações nos membros.
 */
class EquipeService
{
    protected EmpresaModel $empresaModel;
    protected UsuarioModel $usuarioModel;
    protected CargoModel $cargoModel;
    protected ConviteModel $conviteModel;
    protected MembroModel $membroModel;
    protected MembrosDaEquipe $membros;
    protected ConvitesDaEquipe $convites;
    protected AceiteDeConvite $aceite;

    public function __construct()
    {
        $this->empresaModel = new EmpresaModel();
        $this->usuarioModel = new UsuarioModel();
        $this->cargoModel   = new CargoModel();
        $this->conviteModel = new ConviteModel();
        $this->membroModel  = new MembroModel();
        $this->membros      = new MembrosDaEquipe();
        $this->convites     = new ConvitesDaEquipe($this->cargoModel, $this->conviteModel, $this->usuarioModel, $this->membros);
        $this->aceite       = new AceiteDeConvite($this->conviteModel, $this->usuarioModel, $this->membroModel, $this->empresaModel, $this->membros);
    }

    /** Ver Equipe\ConvitesDaEquipe::convidar(). */
    public function convidar(string $empresaId, string $convidadoPorId, string $email, string $cargoId, ?string $mensagem = null): array
    {
        return $this->convites->convidar($empresaId, $convidadoPorId, $email, $cargoId, $mensagem);
    }

    /** Convites ainda não aceitos da empresa (inclui os expirados, marcados como tal). */
    public function listarConvitesPendentes(string $empresaId): array
    {
        return $this->convites->listarPendentes($empresaId);
    }

    /** Novo link e nova validade, e manda o e-mail de novo. */
    public function reenviarConvite(string $empresaId, string $conviteId, string $usuarioId): array
    {
        return $this->convites->reenviar($empresaId, $conviteId, $usuarioId);
    }

    /** O link deixa de funcionar na hora. */
    public function cancelarConvite(string $empresaId, string $conviteId): void
    {
        $this->convites->cancelar($empresaId, $conviteId);
    }

    /** Ver Equipe\ConvitesDaEquipe::detalhes(). */
    public function detalhesDoConvite(string $tokenTexto): array
    {
        return $this->convites->detalhes($tokenTexto);
    }

    /** Endereço da página de aceite no frontend (FRONTEND_URL no .env). */
    public static function linkDoConvite(string $tokenTexto): string
    {
        return ConvitesDaEquipe::linkDoConvite($tokenTexto);
    }

    /** Ver Equipe\AceiteDeConvite::aceitar(). */
    public function aceitarConvite(string $tokenTexto, string $nome, string $senha): array
    {
        return $this->aceite->aceitar($tokenTexto, $nome, $senha);
    }

    public function listarMembros(string $empresaId): array
    {
        return $this->membros->listar($empresaId);
    }

    /** Ver Equipe\MembrosDaEquipe::pessoas(). */
    public function listarPessoas(string $empresaId): array
    {
        return $this->membros->pessoas($empresaId);
    }

    /**
     * Atualização é parcial: só os campos presentes em $dados são
     * alterados.
     *
     * "status" (ativo/inativo) não é tratado aqui — a tabela usuario
     * não tem essa coluna hoje, e o requisito não está nos RFs
     * documentados. Decisão registrada em conversa com o time de
     * front: fica para uma issue própria caso confirmem a
     * necessidade (envolve migration e decidir o efeito sobre login).
     *
     * @throws NaoEncontradoException se o membro não existir/pertencer à empresa
     * @throws \DomainException se o novo cargo_id não existir/pertencer à
     *         empresa, ou se a alteração tentar tirar o cargo do
     *         administrador principal da empresa
     */
    public function atualizarMembro(string $empresaId, string $usuarioId, array $dados): array
    {
        $usuario = $this->membros->daEquipe($empresaId, $usuarioId);

        if (! $usuario) {
            throw new NaoEncontradoException('Membro não encontrado.');
        }

        $camposParaAtualizar = [];

        if (isset($dados['nome'])) {
            $camposParaAtualizar['nome'] = $dados['nome'];
        }

        if (isset($dados['cargo_id']) && $dados['cargo_id'] !== $usuario['cargo_id']) {
            $novoCargo = $this->cargoModel
                ->where('id', $dados['cargo_id'])
                ->where('empresa_id', $empresaId)
                ->first();

            if (! $novoCargo) {
                throw new \DomainException('Cargo inválido ou não pertence à empresa.');
            }

            $limite = new LimiteDeAcesso();
            $limite->exigirQueNaoSejaVoceMesmo($usuarioId, 'Você não pode mudar o seu próprio cargo. Peça a alguém com acesso total.');
            $limite->exigirPodeAtuarNoCargo($usuario['cargo_id']);
            $limite->exigirPodeAtuarNoCargo($novoCargo['id']);

            $empresa = $this->empresaModel->find($empresaId);

            if ($empresa['administrador_principal_id'] === $usuarioId) {
                throw new \DomainException('Não é possível alterar o cargo do administrador principal da empresa.');
            }

            $camposParaAtualizar['cargo_id'] = $dados['cargo_id'];
        }

        if (! empty($camposParaAtualizar)) {
            // O nome é da pessoa (vale em todas as equipes); o cargo é só desta.
            if (isset($camposParaAtualizar['nome'])) {
                $this->usuarioModel->update($usuarioId, ['nome' => $camposParaAtualizar['nome']]);
            }
            if (isset($camposParaAtualizar['cargo_id'])) {
                $this->membroModel->update($usuario['membro_id'], ['cargo_id' => $camposParaAtualizar['cargo_id']]);
            }
            AtividadeService::registrar('membro_alterado', isset($camposParaAtualizar['cargo_id'])
                ? "Mudou o cargo de {$usuario['nome']} para {$novoCargo['nome']}"
                : "Alterou os dados de {$usuario['nome']}");
        }

        return $this->membros->comCargo($empresaId, $usuarioId);
    }

    /**
     * @throws NaoEncontradoException se o membro não existir/pertencer à empresa
     * @throws \DomainException se for o administrador principal
     */
    public function removerMembro(string $usuarioId, string $empresaId): void
    {
        $usuario = $this->membros->daEquipe($empresaId, $usuarioId);

        if (! $usuario) {
            throw new NaoEncontradoException('Membro não encontrado.');
        }

        $empresa = $this->empresaModel->find($empresaId);

        if ($empresa['administrador_principal_id'] === $usuarioId) {
            throw new \DomainException('Não é possível remover o administrador principal da empresa.');
        }

        $limite = new LimiteDeAcesso();
        $limite->exigirQueNaoSejaVoceMesmo($usuarioId, 'Você não pode remover a si mesmo da equipe.');
        $limite->exigirPodeAtuarNoCargo($usuario['cargo_id']);

        // Sai só desta equipe: a conta e as outras equipes da pessoa continuam.
        // A sessão dela aqui cai na próxima requisição (o vínculo não existe mais).
        $this->membroModel->delete($usuario['membro_id']);
        AtividadeService::registrar('membro_removido', "Removeu {$usuario['nome']} da equipe");
    }
}
