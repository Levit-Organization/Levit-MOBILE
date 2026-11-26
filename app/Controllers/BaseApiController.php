<?php

namespace App\Controllers;

use App\Libraries\SessaoWeb;
use CodeIgniter\Database\Exceptions\DatabaseException;
use CodeIgniter\HTTP\ResponseInterface;

class BaseApiController extends BaseController
{
    /**
     * Resposta de sucesso padronizada.
     */
    protected function respondSuccess(mixed $data = null, int $statusCode = 200): ResponseInterface
    {
        return $this->response
            ->setStatusCode($statusCode)
            ->setJSON([
                'status' => 'success',
                'data'   => $data,
            ]);
    }

    /**
     * Resposta de login/troca de equipe: no navegador o token vai em cookie
     * HttpOnly (ver SessaoWeb); no app, no corpo.
     *
     * @param array<string, mixed> $resposta
     */
    protected function respondSessao(array $resposta, int $statusCode = 200): ResponseInterface
    {
        return $this->respondSuccess(SessaoWeb::entregar($this->request, $this->response, $resposta), $statusCode);
    }

    /**
     * Resposta de erro padronizada.
     */
    protected function respondError(string $message, int $statusCode = 400, ?array $errors = null): ResponseInterface
    {
        $body = [
            'status'  => 'error',
            'message' => $message,
        ];

        if ($errors !== null) {
            $body['errors'] = $errors;
        }

        return $this->response
            ->setStatusCode($statusCode)
            ->setJSON($body);
    }

    /**
     * Content-Disposition de download. O nome vem do usuário (arquivo enviado,
     * nome da tabela): aspas e barras quebrariam o cabeçalho, então a versão
     * simples fica só com caracteres seguros e o nome real vai em filename*.
     */
    protected function cabecalhoDeAnexo(string $nome): string
    {
        $simples = preg_replace('/[^A-Za-z0-9._ -]/', '_', $nome) ?: 'arquivo';

        return "attachment; filename=\"{$simples}\"; filename*=UTF-8''" . rawurlencode($nome);
    }

    /**
     * Erro inesperado. As RuntimeException lançadas pelos services trazem uma
     * mensagem pensada para o usuário; as do banco (DatabaseException herda de
     * RuntimeException) trazem SQL, nomes de tabela e coluna. Essas vão só para
     * o log e o usuário recebe uma mensagem genérica.
     */
    protected function respondFalha(\Throwable $e, int $statusCode = 500): ResponseInterface
    {
        if ($e instanceof DatabaseException || ! $e instanceof \RuntimeException) {
            log_message('error', '[{classe}] {mensagem} em {arquivo}:{linha}', [
                'classe'   => $e::class,
                'mensagem' => $e->getMessage(),
                'arquivo'  => $e->getFile(),
                'linha'    => $e->getLine(),
            ]);

            return $this->respondError('Algo deu errado do nosso lado. Tente de novo em instantes.', 500);
        }

        if ($e instanceof \App\Exceptions\AcessoNegadoException) {
            return $this->respondError($e->getMessage(), 403);
        }

        return $this->respondError($e->getMessage(), $statusCode);
    }
}