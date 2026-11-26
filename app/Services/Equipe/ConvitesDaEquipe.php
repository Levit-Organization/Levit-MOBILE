<?php

namespace App\Services\Equipe;

use App\Exceptions\NaoEncontradoException;
use App\Models\CargoModel;
use App\Models\ConviteModel;
use App\Models\UsuarioModel;
use App\Services\AtividadeService;
use App\Services\Email\ModeloConvite;
use App\Services\LimiteDeAcesso;

/**
 * Convites de quem administra a equipe: convidar, listar os pendentes,
 * reenviar, cancelar e o que a página pública do convite mostra. O token
 * só existe em texto puro dentro do e-mail: o banco guarda o hash e a API
 * não o devolve. O aceite fica em AceiteDeConvite.
 */
class ConvitesDaEquipe
{
    private const VALIDADE_CONVITE_SEGUNDOS = 7 * 24 * 60 * 60; // 7 dias

    public function __construct(
        protected CargoModel $cargoModel,
        protected ConviteModel $conviteModel,
        protected UsuarioModel $usuarioModel,
        protected MembrosDaEquipe $membros,
    ) {
    }

    /**
     * Convida alguém para a equipe e envia o e-mail com o link de aceite.
     *
     * Se a pessoa já tiver um convite pendente nesta empresa, ele é
     * reaproveitado (novo link, nova validade, cargo e mensagem atualizados)
     * em vez de criar outro.
     *
     * @throws NaoEncontradoException se o cargo não existir/pertencer à empresa
     * @throws \DomainException se a pessoa já fizer parte desta equipe
     *         (ter conta em outra equipe não impede: ela aceita com a senha dela)
     * @throws \RuntimeException se o e-mail não puder ser enviado (o convite não fica criado)
     */
    public function convidar(string $empresaId, string $convidadoPorId, string $email, string $cargoId, ?string $mensagem = null): array
    {
        $email    = mb_strtolower(trim($email));
        $mensagem = $mensagem !== null && trim($mensagem) !== '' ? trim($mensagem) : null;

        $cargo = $this->cargoModel
            ->where('id', $cargoId)
            ->where('empresa_id', $empresaId)
            ->first();

        if (! $cargo) {
            throw new NaoEncontradoException('Cargo não encontrado.');
        }

        (new LimiteDeAcesso())->exigirPodeAtuarNoCargo($cargoId);

        if ($this->membros->porEmail($empresaId, $email)) {
            throw new \DomainException('Essa pessoa já faz parte da equipe.');
        }

        $existente = $this->conviteModel
            ->where('empresa_id', $empresaId)
            ->where('email_destinatario', $email)
            ->where('aceito_em', null)
            ->first();

        $tokenTexto = bin2hex(random_bytes(32));
        $campos     = [
            'convidado_por' => $convidadoPorId,
            'cargo_id'      => $cargoId,
            'mensagem'      => $mensagem,
            'token_hash'    => hash('sha256', $tokenTexto),
            'expira_em'     => date('Y-m-d H:i:s', time() + self::VALIDADE_CONVITE_SEGUNDOS),
        ];

        if ($existente) {
            $conviteId = $existente['id'];
            $this->conviteModel->update($conviteId, $campos);
        } else {
            $conviteId = $this->conviteModel->insert($campos + [
                'empresa_id'         => $empresaId,
                'email_destinatario' => $email,
            ]);
        }

        try {
            $this->enviarEmailDoConvite($conviteId, $tokenTexto);
        } catch (\Throwable $e) {
            // Sem e-mail a pessoa não tem como aceitar: um convite novo é desfeito.
            if (! $existente) {
                $this->conviteModel->delete($conviteId);
            }
            log_message('error', 'Falha ao enviar convite: {erro}', ['erro' => $e->getMessage()]);

            throw new \RuntimeException('O serviço de e-mail não conseguiu enviar o convite agora. Tente de novo em instantes; se continuar, avise quem administra o Levit.', 0, $e);
        }

        AtividadeService::registrar('membro_convidado', "Convidou {$email} para a equipe (cargo {$cargo['nome']})");

        return $this->resumoDoConvite($this->conviteModel->find($conviteId));
    }

    /** Convites ainda não aceitos da empresa (inclui os expirados, marcados como tal). */
    public function listarPendentes(string $empresaId): array
    {
        $convites = $this->conviteModel
            ->select('convite.*, cargo.nome AS cargo_nome, usuario.nome AS convidado_por_nome')
            ->join('cargo', 'cargo.id = convite.cargo_id')
            ->join('usuario', 'usuario.id = convite.convidado_por', 'left')
            ->where('convite.empresa_id', $empresaId)
            ->where('convite.aceito_em', null)
            ->orderBy('convite.criado_em', 'DESC')
            ->findAll();

        return array_map(fn (array $c) => $this->resumoDoConvite($c), $convites);
    }

    /** Novo link e nova validade, e manda o e-mail de novo. */
    public function reenviar(string $empresaId, string $conviteId, string $usuarioId): array
    {
        $convite = $this->convitePendente($empresaId, $conviteId);

        $tokenTexto = bin2hex(random_bytes(32));
        $this->conviteModel->update($conviteId, [
            'convidado_por' => $usuarioId,
            'token_hash'    => hash('sha256', $tokenTexto),
            'expira_em'     => date('Y-m-d H:i:s', time() + self::VALIDADE_CONVITE_SEGUNDOS),
        ]);

        try {
            $this->enviarEmailDoConvite($conviteId, $tokenTexto);
        } catch (\Throwable $e) {
            log_message('error', 'Falha ao reenviar convite: {erro}', ['erro' => $e->getMessage()]);

            throw new \RuntimeException('Não foi possível reenviar o e-mail do convite. Tente de novo em instantes.', 0, $e);
        }

        return $this->resumoDoConvite($this->conviteModel->find($convite['id']));
    }

    /** O link deixa de funcionar na hora. */
    public function cancelar(string $empresaId, string $conviteId): void
    {
        $this->convitePendente($empresaId, $conviteId);
        $this->conviteModel->delete($conviteId);
        AtividadeService::registrar('convite_cancelado', 'Cancelou um convite pendente');
    }

    /**
     * O que a página pública do convite mostra antes de a pessoa aceitar.
     * Não revela nada além do necessário para ela decidir.
     *
     * @throws NaoEncontradoException se o token não existir
     */
    public function detalhes(string $tokenTexto): array
    {
        $convite = $this->conviteModel
            ->select('convite.*, cargo.nome AS cargo_nome, usuario.nome AS convidado_por_nome, empresa.nome AS empresa_nome')
            ->join('cargo', 'cargo.id = convite.cargo_id')
            ->join('empresa', 'empresa.id = convite.empresa_id')
            ->join('usuario', 'usuario.id = convite.convidado_por', 'left')
            ->where('convite.token_hash', hash('sha256', $tokenTexto))
            ->first();

        if (! $convite) {
            throw new NaoEncontradoException('Convite não encontrado. O link pode ter sido substituído por um mais novo.');
        }

        return [
            'email'         => $convite['email_destinatario'],
            'empresa'       => $convite['empresa_nome'],
            'cargo'         => $convite['cargo_nome'],
            'convidado_por' => $convite['convidado_por_nome'],
            'mensagem'      => $convite['mensagem'] ?? null,
            'expira_em'     => $convite['expira_em'],
            'status'        => $convite['aceito_em'] !== null ? 'aceito' : (strtotime($convite['expira_em']) < time() ? 'expirado' : 'valido'),
            // Com conta, a pessoa só confirma a senha para entrar em mais esta equipe.
            'ja_tem_conta'  => (bool) $this->usuarioModel->where('LOWER(email)', mb_strtolower($convite['email_destinatario']))->first(),
        ];
    }

    private function enviarEmailDoConvite(string $conviteId, string $tokenTexto): void
    {
        $convite = $this->conviteModel
            ->select('convite.*, cargo.nome AS cargo_nome, usuario.nome AS convidado_por_nome, empresa.nome AS empresa_nome')
            ->join('cargo', 'cargo.id = convite.cargo_id')
            ->join('empresa', 'empresa.id = convite.empresa_id')
            ->join('usuario', 'usuario.id = convite.convidado_por', 'left')
            ->where('convite.id', $conviteId)
            ->first();

        $dados = [
            'convidado_por' => $convite['convidado_por_nome'] ?: 'Alguém da equipe',
            'empresa'       => $convite['empresa_nome'],
            'cargo'         => $convite['cargo_nome'],
            'mensagem'      => $convite['mensagem'] ?? null,
            'link'          => self::linkDoConvite($tokenTexto),
            'dias_validade' => intdiv(self::VALIDADE_CONVITE_SEGUNDOS, 86400),
        ];

        service('email')->enviar($convite['email_destinatario'], ModeloConvite::assunto($dados), ModeloConvite::html($dados));
    }

    /** Endereço da página de aceite no frontend (FRONTEND_URL no .env). */
    public static function linkDoConvite(string $tokenTexto): string
    {
        return rtrim((string) env('FRONTEND_URL', 'http://localhost:5173'), '/') . '/convite/' . $tokenTexto;
    }

    private function convitePendente(string $empresaId, string $conviteId): array
    {
        $convite = $this->conviteModel
            ->where('id', $conviteId)
            ->where('empresa_id', $empresaId)
            ->where('aceito_em', null)
            ->first();

        if (! $convite) {
            throw new NaoEncontradoException('Convite não encontrado ou já aceito.');
        }

        return $convite;
    }

    /** Formato devolvido pela API: nunca inclui o token nem o hash. */
    private function resumoDoConvite(array $c): array
    {
        return [
            'id'                 => $c['id'],
            'email'              => $c['email_destinatario'],
            'cargo_id'           => $c['cargo_id'],
            'cargo_nome'         => $c['cargo_nome'] ?? null,
            'convidado_por_nome' => $c['convidado_por_nome'] ?? null,
            'mensagem'           => $c['mensagem'] ?? null,
            'criado_em'          => $c['criado_em'] ?? null,
            'expira_em'          => $c['expira_em'],
            'expirado'           => strtotime($c['expira_em']) < time(),
        ];
    }
}
