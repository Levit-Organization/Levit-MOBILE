<?php

namespace App\Controllers;

use App\Exceptions\AcessoNegadoException;
use App\Exceptions\NaoEncontradoException;
use App\Services\AuthService;
use App\Services\EquipeService;
use CodeIgniter\HTTP\ResponseInterface;

class EquipeController extends BaseApiController
{
    protected EquipeService $equipeService;
    protected AuthService $authService;

    public function __construct()
    {
        $this->equipeService = new EquipeService();
        $this->authService   = new AuthService();
    }

    public function convidar(): ResponseInterface
    {
        $dados = $this->request->getJSON(true) ?? [];

        $rules = [
            'email'    => ['required', 'valid_email'],
            'cargo_id' => ['required'],
            'mensagem' => ['permit_empty', 'max_length[500]'],
        ];

        if (! $this->validateData($dados, $rules)) {
            return $this->respondError('Dados inválidos.', 422, $this->validator->getErrors());
        }

        $user = service('authenticatedUser');

        try {
            $resultado = $this->equipeService->convidar($user->empresaId, $user->id, $dados['email'], $dados['cargo_id'], $dados['mensagem'] ?? null);
        } catch (NaoEncontradoException $e) {
            return $this->respondError($e->getMessage(), 404);
        } catch (AcessoNegadoException $e) {
            return $this->respondError($e->getMessage(), 403);
        } catch (\DomainException $e) {
            return $this->respondError($e->getMessage(), 409);
        } catch (\RuntimeException $e) {
            return $this->respondFalha($e, 502);
        }

        return $this->respondSuccess($resultado, 201);
    }

    public function listarConvites(): ResponseInterface
    {
        return $this->respondSuccess($this->equipeService->listarConvitesPendentes(service('authenticatedUser')->empresaId));
    }

    public function reenviarConvite(string $conviteId): ResponseInterface
    {
        $user = service('authenticatedUser');

        try {
            $convite = $this->equipeService->reenviarConvite($user->empresaId, $conviteId, $user->id);
        } catch (NaoEncontradoException $e) {
            return $this->respondError($e->getMessage(), 404);
        } catch (\RuntimeException $e) {
            return $this->respondFalha($e, 502);
        }

        return $this->respondSuccess($convite);
    }

    public function cancelarConvite(string $conviteId): ResponseInterface
    {
        try {
            $this->equipeService->cancelarConvite(service('authenticatedUser')->empresaId, $conviteId);
        } catch (NaoEncontradoException $e) {
            return $this->respondError($e->getMessage(), 404);
        }

        return $this->respondSuccess(null);
    }

    /** Público: o que a página do convite mostra antes de aceitar. */
    public function detalhesDoConvite(string $token): ResponseInterface
    {
        try {
            return $this->respondSuccess($this->equipeService->detalhesDoConvite($token));
        } catch (NaoEncontradoException $e) {
            return $this->respondError($e->getMessage(), 404);
        }
    }

    public function aceitarConvite(): ResponseInterface
    {
        $dados = $this->request->getJSON(true) ?? [];

        $rules = [
            'token' => ['required'],
            // Quem já tem conta só confirma a senha; nome e força da senha
            // nova são conferidos no service, para quem está criando a conta.
            'nome'  => ['permit_empty', 'max_length[150]'],
            'senha' => ['required'],
        ];

        if (! $this->validateData($dados, $rules)) {
            return $this->respondError('Dados inválidos.', 422, $this->validator->getErrors());
        }

        try {
            $resultado = $this->equipeService->aceitarConvite($dados['token'], trim((string) ($dados['nome'] ?? '')), $dados['senha']);
        } catch (AcessoNegadoException $e) {
            return $this->respondError($e->getMessage(), 403);
        } catch (\DomainException $e) {
            return $this->respondError($e->getMessage(), 422);
        } catch (\RuntimeException $e) {
            return $this->respondFalha($e, 500);
        }

        return $this->respondSessao(
            $this->authService->montarRespostaAutenticacao($resultado['usuario'], $resultado['empresa']),
            201
        );
    }

    public function listarPessoas(): ResponseInterface
    {
        return $this->respondSuccess($this->equipeService->listarPessoas(service('authenticatedUser')->empresaId));
    }

    public function listarMembros(): ResponseInterface
    {
        $membros = $this->equipeService->listarMembros(service('authenticatedUser')->empresaId);

        return $this->respondSuccess($membros, 200);
    }

    public function atualizarMembro(string $usuarioId): ResponseInterface
    {
        $dados = $this->request->getJSON(true) ?? [];

        $rules = [
            'nome'     => ['permit_empty', 'min_length[3]', 'max_length[150]'],
            'cargo_id' => ['permit_empty'],
        ];

        if (! $this->validateData($dados, $rules)) {
            return $this->respondError('Dados inválidos.', 422, $this->validator->getErrors());
        }

        try {
            $membro = $this->equipeService->atualizarMembro(service('authenticatedUser')->empresaId, $usuarioId, $dados);
        } catch (NaoEncontradoException $e) {
            return $this->respondError($e->getMessage(), 404);
        } catch (AcessoNegadoException $e) {
            return $this->respondError($e->getMessage(), 403);
        } catch (\DomainException $e) {
            return $this->respondError($e->getMessage(), 422);
        }

        return $this->respondSuccess($membro, 200);
    }

    public function removerMembro(string $usuarioId): ResponseInterface
    {
        try {
            $this->equipeService->removerMembro($usuarioId, service('authenticatedUser')->empresaId);
        } catch (NaoEncontradoException $e) {
            return $this->respondError($e->getMessage(), 404);
        } catch (AcessoNegadoException $e) {
            return $this->respondError($e->getMessage(), 403);
        } catch (\DomainException $e) {
            return $this->respondError($e->getMessage(), 409);
        }

        return $this->respondSuccess(null, 200);
    }
}