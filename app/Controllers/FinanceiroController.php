<?php

namespace App\Controllers;

use App\Exceptions\AcessoNegadoException;
use App\Exceptions\NaoEncontradoException;
use App\Services\FinanceiroService;
use CodeIgniter\HTTP\ResponseInterface;

class FinanceiroController extends BaseApiController
{
    protected FinanceiroService $financeiroService;

    public function __construct()
    {
        $this->financeiroService = new FinanceiroService();
    }

    // ==========================================================
    // Categorias
    // ==========================================================

    public function listarCategorias(): ResponseInterface
    {
        $categorias = $this->financeiroService->listarCategorias(service('authenticatedUser')->empresaId);

        return $this->respondSuccess($categorias, 200);
    }

    public function criarCategoria(): ResponseInterface
    {
        $dados = $this->request->getJSON(true) ?? [];

        $rules = [
            'nome' => ['required', 'min_length[2]', 'max_length[50]'],
        ];

        if (! $this->validateData($dados, $rules)) {
            return $this->respondError('Dados inválidos.', 422, $this->validator->getErrors());
        }

        try {
            $categoria = $this->financeiroService->criarCategoria(service('authenticatedUser')->empresaId, $dados['nome']);
        } catch (AcessoNegadoException $e) {
            return $this->respondError($e->getMessage(), 403);
        } catch (\DomainException $e) {
            return $this->respondError($e->getMessage(), 422);
        }

        return $this->respondSuccess($categoria, 201);
    }

    public function excluirCategoria(string $categoriaId): ResponseInterface
    {
        try {
            $this->financeiroService->excluirCategoria(service('authenticatedUser')->empresaId, $categoriaId);
        } catch (NaoEncontradoException $e) {
            return $this->respondError($e->getMessage(), 404);
        } catch (AcessoNegadoException $e) {
            return $this->respondError($e->getMessage(), 403);
        }

        return $this->respondSuccess(null, 200);
    }

    // ==========================================================
    // Lançamentos
    // ==========================================================

    public function listarLancamentos(string $moduloId): ResponseInterface
    {
        $user        = service('authenticatedUser');
        $categoriaId = $this->request->getGet('categoria_id');

        try {
            $resultado = $this->financeiroService->listarLancamentos($moduloId, $user->empresaId, $user->cargoId, $user->acessoTotal, $categoriaId);
        } catch (NaoEncontradoException $e) {
            return $this->respondError($e->getMessage(), 404);
        } catch (AcessoNegadoException $e) {
            return $this->respondError($e->getMessage(), 403);
        }

        return $this->respondSuccess($resultado, 200);
    }

    public function criarLancamento(string $moduloId): ResponseInterface
    {
        $dados = $this->request->getJSON(true) ?? [];
        $user  = service('authenticatedUser');

        try {
            $lancamento = $this->financeiroService->criarLancamento($moduloId, $user->empresaId, $user->cargoId, $user->acessoTotal, $user->id, $dados);
        } catch (NaoEncontradoException $e) {
            return $this->respondError($e->getMessage(), 404);
        } catch (AcessoNegadoException $e) {
            return $this->respondError($e->getMessage(), 403);
        } catch (\DomainException $e) {
            return $this->respondError($e->getMessage(), 422);
        }

        return $this->respondSuccess($lancamento, 201);
    }

    public function atualizarLancamento(string $moduloId, string $lancamentoId): ResponseInterface
    {
        $dados = $this->request->getJSON(true) ?? [];
        $user  = service('authenticatedUser');

        try {
            $lancamento = $this->financeiroService->atualizarLancamento($lancamentoId, $moduloId, $user->empresaId, $user->cargoId, $user->acessoTotal, $user->id, $dados);
        } catch (NaoEncontradoException $e) {
            return $this->respondError($e->getMessage(), 404);
        } catch (AcessoNegadoException $e) {
            return $this->respondError($e->getMessage(), 403);
        } catch (\DomainException $e) {
            return $this->respondError($e->getMessage(), 422);
        }

        return $this->respondSuccess($lancamento, 200);
    }

    public function excluirLancamento(string $moduloId, string $lancamentoId): ResponseInterface
    {
        $user = service('authenticatedUser');

        try {
            $this->financeiroService->excluirLancamento($lancamentoId, $moduloId, $user->empresaId, $user->cargoId, $user->acessoTotal);
        } catch (NaoEncontradoException $e) {
            return $this->respondError($e->getMessage(), 404);
        } catch (AcessoNegadoException $e) {
            return $this->respondError($e->getMessage(), 403);
        }

        return $this->respondSuccess(null, 200);
    }
}