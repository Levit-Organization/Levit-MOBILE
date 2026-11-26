<?php

namespace App\Controllers;

use App\Exceptions\AcessoNegadoException;
use App\Exceptions\NaoEncontradoException;
use App\Services\AutomacaoService;
use CodeIgniter\HTTP\ResponseInterface;

class AutomacaoController extends BaseApiController
{
    protected AutomacaoService $automacaoService;

    public function __construct()
    {
        $this->automacaoService = new AutomacaoService();
    }

    public function criar(string $moduloId): ResponseInterface
    {
        $dados = $this->request->getJSON(true) ?? [];

        $rules = [
            'nome'  => ['required', 'min_length[2]', 'max_length[100]'],
            'acoes' => ['required'],
        ];

        if (! $this->validateData($dados, $rules)) {
            return $this->respondError('Dados inválidos.', 422, $this->validator->getErrors());
        }

        $user = service('authenticatedUser');

        try {
            $automacao = $this->automacaoService->criarAutomacao($moduloId, $user->empresaId, $user->cargoId, $user->acessoTotal, $user->id, $dados);
        } catch (NaoEncontradoException $e) {
            return $this->respondError($e->getMessage(), 404);
        } catch (AcessoNegadoException $e) {
            return $this->respondError($e->getMessage(), 403);
        } catch (\DomainException $e) {
            return $this->respondError($e->getMessage(), 422);
        } catch (\RuntimeException $e) {
            return $this->respondFalha($e, 500);
        }

        return $this->respondSuccess($automacao, 201);
    }

    public function listar(string $moduloId): ResponseInterface
    {
        $user = service('authenticatedUser');

        try {
            $automacoes = $this->automacaoService->listarAutomacoes($moduloId, $user->empresaId, $user->cargoId, $user->acessoTotal);
        } catch (NaoEncontradoException $e) {
            return $this->respondError($e->getMessage(), 404);
        } catch (AcessoNegadoException $e) {
            return $this->respondError($e->getMessage(), 403);
        }

        return $this->respondSuccess($automacoes, 200);
    }

    public function alternarAtivo(string $moduloId, string $automacaoId): ResponseInterface
    {
        $dados = $this->request->getJSON(true) ?? [];
        $user  = service('authenticatedUser');

        try {
            $automacao = $this->automacaoService->alternarAtivo($automacaoId, $moduloId, $user->empresaId, $user->cargoId, $user->acessoTotal, (bool) ($dados['ativo'] ?? true));
        } catch (NaoEncontradoException $e) {
            return $this->respondError($e->getMessage(), 404);
        } catch (AcessoNegadoException $e) {
            return $this->respondError($e->getMessage(), 403);
        }

        return $this->respondSuccess($automacao, 200);
    }

    public function excluir(string $moduloId, string $automacaoId): ResponseInterface
    {
        $user = service('authenticatedUser');

        try {
            $this->automacaoService->excluirAutomacao($automacaoId, $moduloId, $user->empresaId, $user->cargoId, $user->acessoTotal);
        } catch (NaoEncontradoException $e) {
            return $this->respondError($e->getMessage(), 404);
        } catch (AcessoNegadoException $e) {
            return $this->respondError($e->getMessage(), 403);
        }

        return $this->respondSuccess(null, 200);
    }

    public function listarTodas(): ResponseInterface
    {
        $user = service('authenticatedUser');

        return $this->responder(fn () => $this->automacaoService->listarTodas($user->empresaId, $user->cargoId, $user->acessoTotal));
    }

    public function atualizar(string $moduloId, string $automacaoId): ResponseInterface
    {
        $dados = $this->request->getJSON(true) ?? [];
        $user  = service('authenticatedUser');

        return $this->responder(fn () => $this->automacaoService->atualizarAutomacao(
            $automacaoId, $moduloId, $user->empresaId, $user->cargoId, $user->acessoTotal, $dados
        ));
    }

    public function execucoes(string $moduloId, string $automacaoId): ResponseInterface
    {
        $user = service('authenticatedUser');

        return $this->responder(fn () => $this->automacaoService->listarExecucoes(
            $automacaoId,
            $moduloId,
            $user->empresaId,
            $user->cargoId,
            $user->acessoTotal,
            $this->request->getGet('antes_de') ?: null,
            (int) ($this->request->getGet('limite') ?: 20)
        ));
    }

    public function simular(string $moduloId): ResponseInterface
    {
        $dados = $this->request->getJSON(true) ?? [];
        $user  = service('authenticatedUser');

        return $this->responder(fn () => $this->automacaoService->simular(
            $moduloId, $user->empresaId, $user->cargoId, $user->acessoTotal, $dados
        ));
    }

    public function reexecutar(string $moduloId, string $automacaoId, string $execucaoId): ResponseInterface
    {
        $user = service('authenticatedUser');

        return $this->responder(function () use ($moduloId, $automacaoId, $execucaoId, $user) {
            $this->automacaoService->reexecutar($execucaoId, $automacaoId, $moduloId, $user->empresaId, $user->cargoId, $user->acessoTotal);

            return null;
        }, 202);
    }

    /** Traduz as exceções do service para as respostas HTTP padrão da API. */
    private function responder(callable $acao, int $status = 200): ResponseInterface
    {
        try {
            return $this->respondSuccess($acao(), $status);
        } catch (NaoEncontradoException $e) {
            return $this->respondError($e->getMessage(), 404);
        } catch (AcessoNegadoException $e) {
            return $this->respondError($e->getMessage(), 403);
        } catch (\DomainException $e) {
            return $this->respondError($e->getMessage(), 422);
        } catch (\RuntimeException $e) {
            return $this->respondFalha($e, 500);
        }
    }
}
