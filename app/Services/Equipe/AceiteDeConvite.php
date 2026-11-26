<?php

namespace App\Services\Equipe;

use App\Models\ConviteModel;
use App\Models\EmpresaModel;
use App\Models\MembroModel;
use App\Models\UsuarioModel;
use App\Services\AtividadeService;
use App\Services\NotificacaoService;

/**
 * Aceite de um convite pela pessoa convidada (sem sessão: só com o token
 * do e-mail). Quem convidou fica sabendo por notificação.
 */
class AceiteDeConvite
{
    public function __construct(
        protected ConviteModel $conviteModel,
        protected UsuarioModel $usuarioModel,
        protected MembroModel $membroModel,
        protected EmpresaModel $empresaModel,
        protected MembrosDaEquipe $membros,
    ) {
    }

    /**
     * Aceita um convite: a pessoa entra na equipe com o cargo do convite.
     * Sem conta, ela é criada com o nome e a senha informados; com conta (de
     * outra equipe), basta a senha dela. O convite fica marcado como usado.
     *
     * @throws \DomainException se o token for inválido, expirado, já usado,
     *         se a senha não conferir ou se os dados da conta nova forem inválidos
     */
    public function aceitar(string $tokenTexto, string $nome, string $senha): array
    {
        $convite = $this->conviteModel
            ->where('token_hash', hash('sha256', $tokenTexto))
            ->first();

        if (! $convite) {
            throw new \DomainException('Convite inválido.');
        }

        if ($convite['aceito_em'] !== null) {
            throw new \DomainException('Este convite já foi utilizado.');
        }

        if (strtotime($convite['expira_em']) < time()) {
            throw new \DomainException('Este convite expirou.');
        }

        $email   = mb_strtolower($convite['email_destinatario']);
        $usuario = $this->usuarioModel->where('LOWER(email)', $email)->first();

        if ($usuario) {
            if (! password_verify($senha, $usuario['senha_hash'])) {
                throw new \DomainException('Senha incorreta. Use a senha da sua conta no Levit.');
            }

            if ($this->membros->porEmail($convite['empresa_id'], $email)) {
                throw new \DomainException('Você já faz parte desta equipe. É só entrar.');
            }
        } else {
            if (mb_strlen($nome) < 3) {
                throw new \DomainException('Informe seu nome (pelo menos 3 letras).');
            }

            if (strlen($senha) < 8 || ! preg_match('/^(?=.*[A-Za-z])(?=.*\d).+$/', $senha)) {
                throw new \DomainException('A senha precisa ter pelo menos 8 caracteres, com letras e números.');
            }
        }

        $db = db_connect();
        $db->transStart();

        $usuarioId = $usuario['id'] ?? $this->usuarioModel->insert([
            'nome'       => $nome,
            'email'      => $convite['email_destinatario'],
            'senha_hash' => password_hash($senha, PASSWORD_BCRYPT, ['cost' => 10]),
        ]);

        $this->membroModel->insert([
            'usuario_id' => $usuarioId,
            'empresa_id' => $convite['empresa_id'],
            'cargo_id'   => $convite['cargo_id'],
        ]);

        $this->conviteModel->update($convite['id'], [
            'aceito_em' => date('Y-m-d H:i:s'),
        ]);

        $db->transComplete();

        if ($db->transStatus() === false) {
            throw new \RuntimeException('Não foi possível aceitar o convite. Tente novamente.');
        }

        $nome = $usuario['nome'] ?? $nome;

        $notificacoes = new NotificacaoService();
        $notificacoes->notificarSemFalhar(
            $convite['convidado_por'] ? [$convite['convidado_por']] : $notificacoes->administradores($convite['empresa_id']),
            $convite['empresa_id'],
            [
                'tipo'     => 'convite_aceito',
                'titulo'   => "{$nome} entrou na equipe",
                'mensagem' => "Convite aceito por {$convite['email_destinatario']}.",
                'link'     => '/team',
            ]
        );

        // Ainda sem sessão: a própria pessoa aparece como autora.
        AtividadeService::registrar('membro_entrou', "Entrou na equipe pelo convite ({$convite['email_destinatario']})", null, null, $convite['empresa_id'], $nome);

        return [
            'usuario' => $this->usuarioModel->find($usuarioId),
            'empresa' => $this->empresaModel->find($convite['empresa_id']),
        ];
    }
}
