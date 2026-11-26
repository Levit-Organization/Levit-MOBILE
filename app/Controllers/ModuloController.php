<?php

namespace App\Controllers;

use App\Exceptions\AcessoNegadoException;
use App\Exceptions\NaoEncontradoException;
use App\Services\ModuloService;
use CodeIgniter\HTTP\ResponseInterface;

class ModuloController extends BaseApiController
{
    protected ModuloService $moduloService;

    public function __construct()
    {
        $this->moduloService = new ModuloService();
    }

    public function criar(): ResponseInterface
    {
        $dados = $this->request->getJSON(true) ?? [];

        $rules = [
            'nome'   => ['required', 'min_length[2]', 'max_length[100]'],
            'icone'  => ['permit_empty', 'max_length[50]'],
            'tipo'   => ['permit_empty', 'in_list[dados,arquivo,recrutamento,financeiro]'],
            'campos' => ['permit_empty'],
            'fases'  => ['permit_empty'],
        ];

        if (! $this->validateData($dados, $rules)) {
            return $this->respondError('Dados inválidos.', 422, $this->validator->getErrors());
        }

        $user = service('authenticatedUser');

        try {
            $modulo = $this->moduloService->criarModulo($user->empresaId, $user->id, $user->cargoId, $dados, $user->acessoTotal);
        } catch (NaoEncontradoException $e) {
            return $this->respondError($e->getMessage(), 404);
        } catch (\DomainException $e) {
            return $this->respondError($e->getMessage(), 422);
        } catch (\RuntimeException $e) {
            return $this->respondFalha($e, 500);
        }

        return $this->respondSuccess($modulo, 201);
    }

    public function listar(): ResponseInterface
    {
        $user = service('authenticatedUser');

        $modulos = $this->moduloService->listarModulos($user->empresaId, $user->cargoId, $user->acessoTotal);

        return $this->respondSuccess($modulos, 200);
    }

    public function detalhes(string $moduloId): ResponseInterface
    {
        $user = service('authenticatedUser');

        try {
            $modulo = $this->moduloService->buscarModuloComCampos($moduloId, $user->empresaId, $user->cargoId, $user->acessoTotal);
        } catch (NaoEncontradoException $e) {
            return $this->respondError($e->getMessage(), 404);
        } catch (AcessoNegadoException $e) {
            return $this->respondError($e->getMessage(), 403);
        }

        return $this->respondSuccess($modulo, 200);
    }

    public function atualizar(string $moduloId): ResponseInterface
    {
        $dados = $this->request->getJSON(true) ?? [];

        $rules = [
            'nome'  => ['permit_empty', 'min_length[2]', 'max_length[100]'],
            'icone' => ['permit_empty', 'max_length[50]'],
        ];

        if (! $this->validateData($dados, $rules)) {
            return $this->respondError('Dados inválidos.', 422, $this->validator->getErrors());
        }

        $user = service('authenticatedUser');

        try {
            $modulo = $this->moduloService->atualizarModulo($moduloId, $user->empresaId, $user->cargoId, $user->acessoTotal, $dados);
        } catch (NaoEncontradoException $e) {
            return $this->respondError($e->getMessage(), 404);
        } catch (AcessoNegadoException $e) {
            return $this->respondError($e->getMessage(), 403);
        }

        return $this->respondSuccess($modulo, 200);
    }

    public function adicionarCampo(string $moduloId): ResponseInterface
    {
        $campo = $this->request->getJSON(true) ?? [];
        $user  = service('authenticatedUser');

        try {
            $modulo = $this->moduloService->adicionarCampo($moduloId, $user->empresaId, $user->cargoId, $user->acessoTotal, $campo);
        } catch (NaoEncontradoException $e) {
            return $this->respondError($e->getMessage(), 404);
        } catch (AcessoNegadoException $e) {
            return $this->respondError($e->getMessage(), 403);
        } catch (\DomainException $e) {
            return $this->respondError($e->getMessage(), 422);
        }

        return $this->respondSuccess($modulo, 201);
    }

    public function atualizarCampo(string $moduloId, string $campoId): ResponseInterface
    {
        $dados = $this->request->getJSON(true) ?? [];
        $user  = service('authenticatedUser');

        try {
            $modulo = $this->moduloService->atualizarCampo($campoId, $moduloId, $user->empresaId, $user->cargoId, $user->acessoTotal, $dados);
        } catch (NaoEncontradoException $e) {
            return $this->respondError($e->getMessage(), 404);
        } catch (AcessoNegadoException $e) {
            return $this->respondError($e->getMessage(), 403);
        }

        return $this->respondSuccess($modulo, 200);
    }

    public function reordenarCampos(string $moduloId): ResponseInterface
    {
        $dados = $this->request->getJSON(true) ?? [];
        $user  = service('authenticatedUser');

        try {
            $modulo = $this->moduloService->reordenarCampos($moduloId, $user->empresaId, $user->cargoId, $user->acessoTotal, $dados['ordem'] ?? []);
        } catch (NaoEncontradoException $e) {
            return $this->respondError($e->getMessage(), 404);
        } catch (AcessoNegadoException $e) {
            return $this->respondError($e->getMessage(), 403);
        } catch (\DomainException $e) {
            return $this->respondError($e->getMessage(), 422);
        } catch (\RuntimeException $e) {
            return $this->respondFalha($e, 500);
        }

        return $this->respondSuccess($modulo, 200);
    }

    public function excluirCampo(string $moduloId, string $campoId): ResponseInterface
    {
        $user = service('authenticatedUser');

        try {
            $this->moduloService->excluirCampo($campoId, $moduloId, $user->empresaId, $user->cargoId, $user->acessoTotal);
        } catch (NaoEncontradoException $e) {
            return $this->respondError($e->getMessage(), 404);
        } catch (AcessoNegadoException $e) {
            return $this->respondError($e->getMessage(), 403);
        } catch (\DomainException $e) {
            return $this->respondError($e->getMessage(), 409);
        }

        return $this->respondSuccess(null, 200);
    }

    public function excluir(string $moduloId): ResponseInterface
    {
        $user = service('authenticatedUser');

        try {
            $this->moduloService->excluirModulo($moduloId, $user->empresaId, $user->cargoId, $user->acessoTotal);
        } catch (NaoEncontradoException $e) {
            return $this->respondError($e->getMessage(), 404);
        } catch (AcessoNegadoException $e) {
            return $this->respondError($e->getMessage(), 403);
        } catch (\DomainException $e) {
            return $this->respondError($e->getMessage(), 409);
        }

        return $this->respondSuccess(null, 200);
    }

    public function adicionarFase(string $moduloId): ResponseInterface
    {
        $dados = $this->request->getJSON(true) ?? [];
        $user  = service('authenticatedUser');

        try {
            $modulo = $this->moduloService->adicionarFase($moduloId, $user->empresaId, $user->cargoId, $user->acessoTotal, $dados['nome'] ?? '');
        } catch (NaoEncontradoException $e) {
            return $this->respondError($e->getMessage(), 404);
        } catch (AcessoNegadoException $e) {
            return $this->respondError($e->getMessage(), 403);
        } catch (\DomainException $e) {
            return $this->respondError($e->getMessage(), 422);
        }

        return $this->respondSuccess($modulo, 201);
    }

    public function atualizarFase(string $moduloId, string $faseId): ResponseInterface
    {
        $dados = $this->request->getJSON(true) ?? [];
        $user  = service('authenticatedUser');

        try {
            $modulo = $this->moduloService->atualizarFase($faseId, $moduloId, $user->empresaId, $user->cargoId, $user->acessoTotal, $dados['nome'] ?? '');
        } catch (NaoEncontradoException $e) {
            return $this->respondError($e->getMessage(), 404);
        } catch (AcessoNegadoException $e) {
            return $this->respondError($e->getMessage(), 403);
        } catch (\DomainException $e) {
            return $this->respondError($e->getMessage(), 422);
        }

        return $this->respondSuccess($modulo, 200);
    }

    public function reordenarFases(string $moduloId): ResponseInterface
    {
        $dados = $this->request->getJSON(true) ?? [];
        $user  = service('authenticatedUser');

        try {
            $modulo = $this->moduloService->reordenarFases($moduloId, $user->empresaId, $user->cargoId, $user->acessoTotal, $dados['ordem'] ?? []);
        } catch (NaoEncontradoException $e) {
            return $this->respondError($e->getMessage(), 404);
        } catch (AcessoNegadoException $e) {
            return $this->respondError($e->getMessage(), 403);
        } catch (\DomainException $e) {
            return $this->respondError($e->getMessage(), 422);
        } catch (\RuntimeException $e) {
            return $this->respondFalha($e, 500);
        }

        return $this->respondSuccess($modulo, 200);
    }

    public function excluirFase(string $moduloId, string $faseId): ResponseInterface
    {
        $user = service('authenticatedUser');

        try {
            $this->moduloService->excluirFase($faseId, $moduloId, $user->empresaId, $user->cargoId, $user->acessoTotal);
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