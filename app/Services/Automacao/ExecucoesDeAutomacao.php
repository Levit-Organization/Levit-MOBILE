<?php

namespace App\Services\Automacao;

use App\Exceptions\NaoEncontradoException;
use App\Models\AutomacaoLogModel;
use App\Models\RegistroModel;

/**
 * O lado de execução de uma automação já cadastrada: histórico de
 * execuções, reexecução de uma que falhou e simulação (o que faria, sem
 * enviar nada). Quem chama já conferiu a automação e o acesso.
 */
class ExecucoesDeAutomacao
{
    public function __construct(
        protected AutomacaoLogModel $automacaoLogModel = new AutomacaoLogModel(),
        protected RegistroModel $registroModel = new RegistroModel(),
    ) {
    }

    /**
     * Execuções mais recentes primeiro, paginadas por cursor (`antes_de` = data
     * da última execução da página anterior).
     */
    public function listar(string $automacaoId, ?string $antesDe, int $limite): array
    {
        $limite = max(1, min($limite, 100));
        $query  = $this->automacaoLogModel
            ->select('id, registro_id, status, detalhes, gatilho, tentativa, duracao_ms, passos, executado_em')
            ->where('automacao_id', $automacaoId);

        if ($antesDe) {
            $query->where('executado_em <', $antesDe);
        }

        // Busca um a mais para saber se existe próxima página sem um COUNT.
        $linhas    = $query->orderBy('executado_em', 'DESC')->orderBy('id', 'DESC')->limit($limite + 1)->findAll();
        $temMais   = count($linhas) > $limite;
        $execucoes = array_slice($linhas, 0, $limite);
        $ultima    = end($execucoes);

        return [
            'execucoes' => $execucoes,
            'proximo'   => $temMais && $ultima ? $ultima['executado_em'] : null,
        ];
    }

    /** Coloca de novo na fila uma execução que falhou, com os mesmos dados. */
    public function reexecutar(array $automacao, string $execucaoId, string $automacaoId, string $moduloId): void
    {
        $execucao = $this->automacaoLogModel->where('id', $execucaoId)->where('automacao_id', $automacaoId)->first();
        if (! $execucao) {
            throw new NaoEncontradoException('Execução não encontrada.');
        }
        if ($execucao['status'] !== 'erro') {
            throw new \DomainException('Só execuções com erro podem ser reexecutadas.');
        }
        if (! $automacao['ativo']) {
            throw new \DomainException('Ative a automação antes de reexecutar.');
        }

        service('queue')->push('automacoes', 'processar-automacoes', [
            'modulo_id'        => $moduloId,
            'gatilho'          => $execucao['gatilho'] ?? $automacao['gatilho'],
            'registro_id'      => $execucao['registro_id'],
            'dados_registro'   => $execucao['dados'] ?? [],
            'automacao_id'     => $automacaoId,
            'a_partir_da_acao' => 0,
            'tentativa'        => 1,
        ]);
    }

    /**
     * Mostra o que a automação faria, sem enviar nada, com a definição já
     * validada (`campos` e `acoes`). Usa um registro real do módulo, dados
     * avulsos, ou — sem nenhum dos dois — o registro mais recente. Em vagas,
     * o candidato mais recente.
     */
    public function simular(string $moduloId, array $definicao, array $entrada, bool $ehVaga): array
    {
        if ($ehVaga) {
            $dados = $this->candidatoDeExemplo($moduloId);

            return ['registro_id' => null, 'dados' => $dados]
                + (new AutomacaoExecutor())->simular(['modulo_id' => $moduloId] + $definicao['campos'], $definicao['acoes'], $dados, []);
        }

        $registro = null;
        if (! empty($entrada['registro_id'])) {
            $registro = $this->registroModel->where('id', $entrada['registro_id'])->where('modulo_id', $moduloId)->first();
            if (! $registro) {
                throw new NaoEncontradoException('Item não encontrado nesta tabela.');
            }
        } elseif (! isset($entrada['dados'])) {
            $registro = $this->registroModel->where('modulo_id', $moduloId)->orderBy('criado_em', 'DESC')->first();
        }

        $dados     = $registro['dados'] ?? (is_array($entrada['dados'] ?? null) ? $entrada['dados'] : []);
        $automacao = ['modulo_id' => $moduloId] + $definicao['campos'];

        // Condições de mudança precisam do valor anterior. Sem ele, o teste
        // considera que o campo estava vazio antes.
        $anteriores = is_array($entrada['dados_anteriores'] ?? null) ? $entrada['dados_anteriores'] : [];

        return [
            'registro_id' => $registro['id'] ?? null,
            'dados'       => $dados,
        ] + (new AutomacaoExecutor())->simular($automacao, $definicao['acoes'], $dados, $anteriores);
    }

    /** Candidato mais recente da vaga, no formato do motor (ou campos vazios). */
    private function candidatoDeExemplo(string $moduloId): array
    {
        $db        = db_connect();
        $candidato = $db->table('candidato')->where('modulo_id', $moduloId)->orderBy('criado_em', 'DESC')->limit(1)->get()->getRowArray();
        if (! $candidato) {
            return CamposCandidato::dados([], null);
        }

        $fase = $db->table('fase_recrutamento')->select('nome')->where('id', $candidato['fase_atual_id'])->get()->getRowArray();

        return CamposCandidato::dados($candidato, $fase['nome'] ?? null);
    }
}
