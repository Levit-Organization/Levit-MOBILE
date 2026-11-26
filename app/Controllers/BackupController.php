<?php

namespace App\Controllers;

use App\Exceptions\AcessoNegadoException;
use App\Exceptions\NaoEncontradoException;
use App\Services\AutorizacaoModuloService;
use App\Services\BackupService;
use CodeIgniter\HTTP\ResponseInterface;

class BackupController extends BaseApiController
{
    protected BackupService $backupService;

    public function __construct()
    {
        $this->backupService = new BackupService();
    }

    public function exportarJson(): ResponseInterface
    {
        // O backup completo leva todas as tabelas, candidatos e e-mails da
        // equipe, inclusive das tabelas que o cargo de quem pede não enxerga.
        if (! service('authenticatedUser')->acessoTotal) {
            return $this->respondError('Só quem tem acesso total pode baixar o backup completo da empresa.', 403);
        }

        $dados = $this->backupService->exportarJson(service('authenticatedUser')->empresaId);

        $nomeArquivo = 'backup_levit_' . date('Y-m-d_His') . '.json';

        return $this->response
            ->setContentType('application/json')
            ->setHeader('Content-Disposition', $this->cabecalhoDeAnexo($nomeArquivo))
            ->setBody(json_encode($dados, JSON_PRETTY_PRINT | JSON_UNESCAPED_UNICODE));
    }

    public function exportarCsv(string $moduloId): ResponseInterface
    {
        $usuario = service('authenticatedUser');

        try {
            (new AutorizacaoModuloService())->exigirNivel($usuario->acessoTotal, $usuario->cargoId, $moduloId, 'visualizar');
            $resultado = $this->backupService->exportarCsvModulo($moduloId, $usuario->empresaId);
        } catch (NaoEncontradoException $e) {
            return $this->respondError($e->getMessage(), 404);
        } catch (AcessoNegadoException $e) {
            return $this->respondError($e->getMessage(), 403);
        }

        $fluxo = fopen('php://temp', 'r+');
        fputcsv($fluxo, $resultado['cabecalho']);
        foreach ($resultado['linhas'] as $linha) {
            fputcsv($fluxo, $linha);
        }
        rewind($fluxo);
        $conteudoCsv = stream_get_contents($fluxo);
        fclose($fluxo);

        $nomeArquivo = $resultado['nome_modulo'] . '_' . date('Y-m-d') . '.csv';

        return $this->response
            ->setContentType('text/csv')
            ->setHeader('Content-Disposition', $this->cabecalhoDeAnexo($nomeArquivo))
            ->setBody($conteudoCsv);
    }

    public function resetarFabrica(): ResponseInterface
    {
        $user = service('authenticatedUser');

        if (! $this->backupService->podeResetar($user->empresaId, $user->id)) {
            return $this->respondError('Somente o administrador principal pode resetar os dados da empresa.', 403);
        }

        $this->backupService->resetarFabrica($user->empresaId);

        return $this->respondSuccess(null, 200);
    }
}