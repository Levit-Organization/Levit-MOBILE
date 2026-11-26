<?php

namespace App\Services\Auth;

use App\Models\UsuarioModel;
use App\Services\Email\ModeloRedefinicaoSenha;

/**
 * "Esqueceu a senha?": o link por e-mail e a troca da senha com o token
 * desse link. O banco guarda só o hash do token.
 */
class RedefinicaoDeSenha
{
    private const VALIDADE_REDEFINICAO_MINUTOS = 60;

    public function __construct(protected UsuarioModel $usuarioModel)
    {
    }

    /**
     * Gera um link de redefinição e manda por e-mail. Não diz se o e-mail
     * existe (quem chama sempre responde a mesma coisa): evita descobrir
     * contas pelo formulário.
     */
    public function solicitar(string $email): void
    {
        $usuario = $this->usuarioModel->where('LOWER(email)', mb_strtolower(trim($email)))->first();
        if (! $usuario) {
            return;
        }

        $token = bin2hex(random_bytes(32));
        $this->usuarioModel->update($usuario['id'], [
            'reset_hash'       => hash('sha256', $token),
            'reset_expires_at' => date('Y-m-d H:i:s', time() + self::VALIDADE_REDEFINICAO_MINUTOS * 60),
        ]);

        $html = ModeloRedefinicaoSenha::html([
            'nome'             => $usuario['nome'],
            'link'             => rtrim((string) env('FRONTEND_URL', 'http://localhost:5173'), '/') . '/reset-password?token=' . $token,
            'minutos_validade' => self::VALIDADE_REDEFINICAO_MINUTOS,
        ]);

        // O envio acontece depois da resposta: se fosse antes, a resposta para
        // um e-mail cadastrado demoraria mais que para um inexistente, e isso
        // denunciaria quem tem conta. Com PHP-FPM a conexão fecha antes do envio.
        $email = $usuario['email'];
        register_shutdown_function(static function () use ($email, $html) {
            if (function_exists('fastcgi_finish_request')) {
                fastcgi_finish_request();
            }

            try {
                service('email')->enviar($email, ModeloRedefinicaoSenha::ASSUNTO, $html);
            } catch (\Throwable $e) {
                // A resposta ao usuário é sempre a mesma; a falha fica registrada para quem administra.
                log_message('error', 'Falha ao enviar redefinição de senha: {erro}', ['erro' => $e->getMessage()]);
            }
        });
    }

    /**
     * Troca a senha usando o token do e-mail. O token vale uma vez só.
     *
     * @throws \DomainException se o link for inválido, já usado ou expirado
     */
    public function redefinir(string $token, string $senha): void
    {
        $usuario = $this->usuarioModel
            ->where('reset_hash', hash('sha256', $token))
            ->where('reset_expires_at >', date('Y-m-d H:i:s'))
            ->first();

        if (! $usuario) {
            throw new \DomainException('Este link não vale mais. Peça um novo em "Esqueceu a senha?".');
        }

        $campos = [
            'senha_hash'       => password_hash($senha, PASSWORD_BCRYPT, ['cost' => 10]),
            'reset_hash'       => null,
            'reset_expires_at' => null,
        ];
        // Derruba as sessões abertas: tokens emitidos antes disso deixam de valer.
        if (ContextoDeAutenticacao::temColunaSenhaAlteradaEm()) {
            $campos['senha_alterada_em'] = date('Y-m-d H:i:s');
        }
        $this->usuarioModel->update($usuario['id'], $campos);
    }
}
