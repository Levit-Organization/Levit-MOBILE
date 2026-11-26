<?php

namespace App\Controllers;

use App\Exceptions\AcessoNegadoException;
use App\Exceptions\NaoEncontradoException;
use App\Services\ArquivoService;
use CodeIgniter\HTTP\ResponseInterface;

class ArquivoController extends BaseApiController
{
    protected ArquivoService $arquivoService;

    public function __construct()
    {
        $this->arquivoService = new ArquivoService();
    }

    public function enviar(string $moduloId): ResponseInterface
    {
        $arquivo = $this->request->getFile('arquivo');
        $user    = service('authenticatedUser');

        // Acima do post_max_size o PHP descarta o corpo inteiro e nada chega.
        if (! $arquivo) {
            return $this->respondError('Nenhum arquivo chegou. Se ele tem mais de 10 MB, envie um menor.', 422);
        }

        try {
            $resultado = $this->arquivoService->enviarArquivo($moduloId, $user->empresaId, $user->cargoId, $user->acessoTotal, $user->id, $arquivo);
        } catch (NaoEncontradoException $e) {
            return $this->respondError($e->getMessage(), 404);
        } catch (AcessoNegadoException $e) {
            return $this->respondError($e->getMessage(), 403);
        } catch (\DomainException $e) {
            return $this->respondError($e->getMessage(), 422);
        } catch (\RuntimeException $e) {
            return $this->respondFalha($e, 500);
        }

        return $this->respondSuccess($resultado, 201);
    }

    public function baixar(string $moduloId, string $registroId): ResponseInterface
    {
        $user = service('authenticatedUser');

        try {
            $arquivo = $this->arquivoService->baixarArquivo($registroId, $moduloId, $user->empresaId, $user->cargoId, $user->acessoTotal);
        } catch (NaoEncontradoException $e) {
            return $this->respondError($e->getMessage(), 404);
        } catch (AcessoNegadoException $e) {
            return $this->respondError($e->getMessage(), 403);
        } catch (\RuntimeException $e) {
            return $this->respondFalha($e, 502);
        }

        return $this->response
            ->setContentType($arquivo['tipo_mime'])
            ->setHeader('Content-Disposition', $this->cabecalhoDeAnexo($arquivo['nome_original']))
            ->setHeader('X-Content-Type-Options', 'nosniff')
            ->setBody($arquivo['conteudo']);
    }

    public function excluir(string $moduloId, string $registroId): ResponseInterface
    {
        $user = service('authenticatedUser');

        try {
            $this->arquivoService->excluirArquivo($registroId, $moduloId, $user->empresaId, $user->cargoId, $user->acessoTotal);
        } catch (NaoEncontradoException $e) {
            return $this->respondError($e->getMessage(), 404);
        } catch (AcessoNegadoException $e) {
            return $this->respondError($e->getMessage(), 403);
        } catch (\RuntimeException $e) {
            return $this->respondFalha($e, 500);
        }

        return $this->respondSuccess(null, 200);
    }
}