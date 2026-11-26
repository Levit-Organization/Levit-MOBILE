<?php

namespace App\Controllers;

use App\Exceptions\NaoEncontradoException;
use App\Libraries\Relogio;
use App\Services\IndicadoresService;
use App\Services\RelatorioService;
use CodeIgniter\HTTP\ResponseInterface;

/**
 * Relatórios agendados e PDFs sob demanda. Só para quem tem acesso total:
 * o relatório mostra tudo o que aconteceu na empresa, em todas as tabelas.
 */
class RelatorioController extends BaseApiController
{
    protected RelatorioService $relatorios;

    public function __construct()
    {
        $this->relatorios = new RelatorioService();
    }

    private function semAcesso(): ?ResponseInterface
    {
        return service('authenticatedUser')->acessoTotal
            ? null
            : $this->respondError('Só quem tem acesso total pode ver e agendar relatórios.', 403);
    }

    public function listar(): ResponseInterface
    {
        return $this->semAcesso() ?? $this->respondSuccess($this->relatorios->listar(service('authenticatedUser')->empresaId));
    }

    public function criar(): ResponseInterface
    {
        if ($r = $this->semAcesso()) {
            return $r;
        }
        $u = service('authenticatedUser');
        try {
            return $this->respondSuccess($this->relatorios->criar($u->empresaId, $u->id, $this->request->getJSON(true) ?? []), 201);
        } catch (\DomainException $e) {
            return $this->respondError($e->getMessage(), 422);
        }
    }

    public function atualizar(string $id): ResponseInterface
    {
        if ($r = $this->semAcesso()) {
            return $r;
        }
        try {
            return $this->respondSuccess($this->relatorios->atualizar(service('authenticatedUser')->empresaId, $id, $this->request->getJSON(true) ?? []));
        } catch (NaoEncontradoException $e) {
            return $this->respondError($e->getMessage(), 404);
        } catch (\DomainException $e) {
            return $this->respondError($e->getMessage(), 422);
        }
    }

    public function excluir(string $id): ResponseInterface
    {
        if ($r = $this->semAcesso()) {
            return $r;
        }
        try {
            $this->relatorios->excluir(service('authenticatedUser')->empresaId, $id);
        } catch (NaoEncontradoException $e) {
            return $this->respondError($e->getMessage(), 404);
        }

        return $this->respondSuccess(null);
    }

    public function envios(string $id): ResponseInterface
    {
        if ($r = $this->semAcesso()) {
            return $r;
        }
        try {
            return $this->respondSuccess($this->relatorios->envios(service('authenticatedUser')->empresaId, $id));
        } catch (NaoEncontradoException $e) {
            return $this->respondError($e->getMessage(), 404);
        }
    }

    /** Envia agora para os destinatários, com o período que o próximo envio cobriria. */
    public function enviarAgora(string $id): ResponseInterface
    {
        if ($r = $this->semAcesso()) {
            return $r;
        }
        try {
            $relatorio = $this->relatorios->buscar(service('authenticatedUser')->empresaId, $id);
        } catch (NaoEncontradoException $e) {
            return $this->respondError($e->getMessage(), 404);
        }

        $envio = $this->relatorios->enviar($relatorio, Relogio::hoje(1));

        return $envio['status'] === 'enviado'
            ? $this->respondSuccess($envio)
            : $this->respondError('Não foi possível enviar: ' . ($envio['erro'] ?? 'erro desconhecido'), 502);
    }

    /**
     * Lê `inicio` e `fim` (inclusivos, AAAA-MM-DD) da query string.
     *
     * @return array{0: \DateTimeImmutable, 1: \DateTimeImmutable}|string período (fim exclusivo) ou a mensagem de erro
     */
    private function periodoDaRequisicao(): array|string
    {
        $inicio = (string) $this->request->getGet('inicio');
        $fim    = (string) $this->request->getGet('fim');
        $valida = static fn ($d) => preg_match('/^\d{4}-\d{2}-\d{2}$/', $d) && (\DateTimeImmutable::createFromFormat('Y-m-d', $d) ?: null)?->format('Y-m-d') === $d;
        if (! $valida($inicio) || ! $valida($fim) || $inicio > $fim) {
            return 'Escolha um período válido (início antes do fim).';
        }

        $de  = new \DateTimeImmutable($inicio . ' 00:00:00', Relogio::fuso());
        $ate = (new \DateTimeImmutable($fim . ' 00:00:00', Relogio::fuso()))->modify('+1 day');
        if ($de->diff($ate)->days > 366) {
            return 'O período pode ter no máximo um ano.';
        }

        return [$de, $ate];
    }

    /** Indicadores do período (recrutamento, financeiro, prazos), com o período anterior para comparar. */
    public function indicadores(): ResponseInterface
    {
        if ($r = $this->semAcesso()) {
            return $r;
        }
        $periodo = $this->periodoDaRequisicao();
        if (is_string($periodo)) {
            return $this->respondError($periodo, 422);
        }

        return $this->respondSuccess((new IndicadoresService())->painel(service('authenticatedUser')->empresaId, ...$periodo));
    }

    /** Tabelas e colunas disponíveis para montar um relatório. */
    public function tabelas(): ResponseInterface
    {
        return $this->semAcesso() ?? $this->respondSuccess((new IndicadoresService())->tabelas(service('authenticatedUser')->empresaId));
    }

    /** Relatório montado: uma tabela agrupada por uma coluna. */
    public function agrupar(): ResponseInterface
    {
        if ($r = $this->semAcesso()) {
            return $r;
        }
        try {
            return $this->respondSuccess((new IndicadoresService())->agrupar(service('authenticatedUser')->empresaId, $this->request->getGet() ?? []));
        } catch (NaoEncontradoException $e) {
            return $this->respondError($e->getMessage(), 404);
        } catch (\DomainException $e) {
            return $this->respondError($e->getMessage(), 422);
        }
    }

    /** PDF do relatório montado: mesmos parâmetros de `agrupar`, mais `titulo` (opcional). */
    public function agruparPdf(): ResponseInterface
    {
        if ($r = $this->semAcesso()) {
            return $r;
        }

        $params = $this->request->getGet() ?? [];
        try {
            $pdf = $this->relatorios->pdfDoAgrupamento(
                service('authenticatedUser')->empresaId,
                $params,
                mb_substr(trim((string) ($params['titulo'] ?? '')), 0, 100)
            );
        } catch (NaoEncontradoException $e) {
            return $this->respondError($e->getMessage(), 404);
        } catch (\DomainException $e) {
            return $this->respondError($e->getMessage(), 422);
        }

        return $this->response
            ->setContentType('application/pdf')
            ->setHeader('Content-Disposition', $this->cabecalhoDeAnexo($pdf['arquivo']))
            ->setBody($pdf['conteudo']);
    }

    /** PDF de um período qualquer (`inicio` e `fim` inclusivos, AAAA-MM-DD). */
    public function pdf(): ResponseInterface
    {
        if ($r = $this->semAcesso()) {
            return $r;
        }

        $periodo = $this->periodoDaRequisicao();
        if (is_string($periodo)) {
            return $this->respondError($periodo, 422);
        }
        [$de, $ate] = $periodo;

        $titulo = (string) ($this->request->getGet('titulo') ?: 'Relatório de atividades');
        $secoes = RelatorioService::secoesValidas(explode(',', (string) $this->request->getGet('secoes')));
        $pdf    = $this->relatorios->gerarPdf(service('authenticatedUser')->empresaId, $de, $ate, mb_substr($titulo, 0, 100), $secoes);

        return $this->response
            ->setContentType('application/pdf')
            ->setHeader('Content-Disposition', $this->cabecalhoDeAnexo(RelatorioService::nomeDoArquivo($titulo, $ate)))
            ->setBody($pdf['conteudo']);
    }
}
