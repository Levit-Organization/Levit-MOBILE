<?php

namespace App\Controllers;

use App\Exceptions\AcessoNegadoException;
use App\Exceptions\NaoEncontradoException;
use App\Services\RegistroService;
use CodeIgniter\HTTP\ResponseInterface;

class RegistroController extends BaseApiController
{
    protected RegistroService $registroService;

    public function __construct()
    {
        $this->registroService = new RegistroService();
    }

    /** `busca`: texto livre. `filtros`: pesquisa avançada em JSON ({ logica, regras }). */
    public function listar(string $moduloId): ResponseInterface
    {
        $user    = service('authenticatedUser');
        $busca   = $this->request->getGet('busca');
        $filtros = null;

        $filtrosJson = $this->request->getGet('filtros');
        if (is_string($filtrosJson) && $filtrosJson !== '') {
            $filtros = json_decode($filtrosJson, true);
            if (! is_array($filtros)) {
                return $this->respondError('Filtro inválido. Refaça o filtro.', 422);
            }
        }

        try {
            $registros = $this->registroService->listarRegistros($moduloId, $user->empresaId, $user->cargoId, $user->acessoTotal, $busca, $filtros);
        } catch (NaoEncontradoException $e) {
            return $this->respondError($e->getMessage(), 404);
        } catch (AcessoNegadoException $e) {
            return $this->respondError($e->getMessage(), 403);
        } catch (\DomainException $e) {
            return $this->respondError($e->getMessage(), 422);
        }

        return $this->respondSuccess($registros, 200);
    }

    /** Itens de outras tabelas que apontam para este (o outro lado das ligações). */
    public function ligados(string $moduloId, string $registroId): ResponseInterface
    {
        $user = service('authenticatedUser');

        try {
            $grupos = $this->registroService->itensQueApontam($registroId, $moduloId, $user->empresaId, $user->cargoId, $user->acessoTotal);
        } catch (NaoEncontradoException $e) {
            return $this->respondError($e->getMessage(), 404);
        } catch (AcessoNegadoException $e) {
            return $this->respondError($e->getMessage(), 403);
        }

        return $this->respondSuccess($grupos, 200);
    }

    /** Nome de cada item ({ id, titulo }), para escolher e mostrar itens ligados. */
    public function titulos(string $moduloId): ResponseInterface
    {
        $user = service('authenticatedUser');

        try {
            $titulos = $this->registroService->listarTitulos($moduloId, $user->empresaId, $user->cargoId, $user->acessoTotal);
        } catch (NaoEncontradoException $e) {
            return $this->respondError($e->getMessage(), 404);
        } catch (AcessoNegadoException $e) {
            return $this->respondError($e->getMessage(), 403);
        }

        return $this->respondSuccess($titulos, 200);
    }

    public function criar(string $moduloId): ResponseInterface
    {
        $dados = $this->request->getJSON(true) ?? [];
        $user  = service('authenticatedUser');

        try {
            $registro = $this->registroService->criarRegistro($moduloId, $user->empresaId, $user->cargoId, $user->acessoTotal, $user->id, $dados);
        } catch (NaoEncontradoException $e) {
            return $this->respondError($e->getMessage(), 404);
        } catch (AcessoNegadoException $e) {
            return $this->respondError($e->getMessage(), 403);
        } catch (\DomainException $e) {
            return $this->respondError($e->getMessage(), 422);
        }

        return $this->respondSuccess($registro, 201);
    }

    /** Itens de uma planilha, já convertidos pelo frontend: { itens: [{ linha, dados }] }. */
    public function importar(string $moduloId): ResponseInterface
    {
        $itens = ($this->request->getJSON(true) ?? [])['itens'] ?? null;
        if (! is_array($itens)) {
            return $this->respondError('Envie os itens da planilha.', 422);
        }
        $user = service('authenticatedUser');

        try {
            $resultado = $this->registroService->importar($moduloId, $user->empresaId, $user->cargoId, $user->acessoTotal, $user->id, $itens);
        } catch (NaoEncontradoException $e) {
            return $this->respondError($e->getMessage(), 404);
        } catch (AcessoNegadoException $e) {
            return $this->respondError($e->getMessage(), 403);
        } catch (\DomainException $e) {
            return $this->respondError($e->getMessage(), 422);
        } catch (\RuntimeException $e) {
            return $this->respondFalha($e);
        }

        return $this->respondSuccess($resultado, 201);
    }

    public function atualizar(string $moduloId, string $registroId): ResponseInterface
    {
        $dados = $this->request->getJSON(true) ?? [];
        $user  = service('authenticatedUser');

        try {
            $registro = $this->registroService->atualizarRegistro($registroId, $moduloId, $user->empresaId, $user->cargoId, $user->acessoTotal, $user->id, $dados);
        } catch (NaoEncontradoException $e) {
            return $this->respondError($e->getMessage(), 404);
        } catch (AcessoNegadoException $e) {
            return $this->respondError($e->getMessage(), 403);
        } catch (\DomainException $e) {
            return $this->respondError($e->getMessage(), 422);
        }

        return $this->respondSuccess($registro, 200);
    }

    public function excluir(string $moduloId, string $registroId): ResponseInterface
    {
        $user = service('authenticatedUser');

        try {
            $this->registroService->excluirRegistro($registroId, $moduloId, $user->empresaId, $user->cargoId, $user->acessoTotal);
        } catch (NaoEncontradoException $e) {
            return $this->respondError($e->getMessage(), 404);
        } catch (AcessoNegadoException $e) {
            return $this->respondError($e->getMessage(), 403);
        } catch (\RuntimeException $e) {
            return $this->respondFalha($e);
        }

        return $this->respondSuccess(null, 200);
    }
}