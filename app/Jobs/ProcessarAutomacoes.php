<?php

namespace App\Jobs;

use App\Models\AutomacaoAcaoModel;
use App\Models\AutomacaoLogModel;
use App\Models\AutomacaoModel;
use App\Models\ModuloModel;
use App\Services\NotificacaoService;
use App\Services\Automacao\AutomacaoExecutor;
use CodeIgniter\Queue\BaseJob;

/**
 * Dois formatos de payload:
 * - disparo de evento: { modulo_id, gatilho, registro_id, dados_registro } → todas as
 *   automações ativas do módulo que escutam o gatilho;
 * - retentativa/reexecução: o mesmo + { automacao_id, a_partir_da_acao, tentativa }
 *   → só aquela automação, continuando da ação que falhou.
 */
class ProcessarAutomacoes extends BaseJob
{
    public const MAX_TENTATIVAS = 3;

    /** Espera antes da 2ª e da 3ª tentativa, em segundos. */
    private const ESPERAS = [1 => 60, 2 => 300];

    /** Falhas definitivas seguidas que pausam a automação. */
    public const FALHAS_PARA_PAUSAR = 5;

    /**
     * Uma automação pode alterar ou criar registros e, com isso, disparar outras.
     * A cadeia guarda quem já rodou: a mesma automação não roda duas vezes na
     * mesma cadeia, e cadeias longas param aqui.
     */
    public const MAX_CADEIA = 3;

    private AutomacaoExecutor $executor;

    public function process(): void
    {
        $this->executor = new AutomacaoExecutor();

        $moduloId   = $this->data['modulo_id'];
        $gatilho    = $this->data['gatilho'];
        $dados      = $this->data['dados_registro'] ?? [];
        // Só existe em atualizações: é o que permite as condições "mudou".
        $anteriores = $this->data['dados_anteriores'] ?? null;
        $cadeia     = $this->data['cadeia'] ?? [];

        if (count($cadeia) >= self::MAX_CADEIA) {
            log_message('warning', 'Automações: cadeia interrompida no limite ({cadeia}).', ['cadeia' => implode(' > ', $cadeia)]);

            return;
        }

        $automacaoModel = new AutomacaoModel();

        if (! empty($this->data['automacao_id'])) {
            $automacao = $automacaoModel->where('id', $this->data['automacao_id'])->where('ativo', true)->first();
            if ($automacao) {
                $this->processarUma($automacao, $dados, $anteriores, (int) ($this->data['a_partir_da_acao'] ?? 0), (int) ($this->data['tentativa'] ?? 1), false);
            }

            return;
        }

        // O filtro por gatilho é feito aqui (e não no SQL) porque `gatilhos` é uma
        // lista em JSON; um módulo tem poucas automações, então o custo é irrelevante.
        $automacoes = array_filter(
            $automacaoModel->where('modulo_id', $moduloId)->where('ativo', true)->findAll(),
            static fn (array $a) => in_array($gatilho, $a['gatilhos'] ?: [$a['gatilho']], true) && ! in_array($a['id'], $cadeia, true)
        );

        foreach ($automacoes as $automacao) {
            $this->processarUma($automacao, $dados, $anteriores, 0, 1, true);
        }
    }

    private function processarUma(array $automacao, array $dados, ?array $anteriores, int $aPartirDe, int $tentativa, bool $checarCondicao): void
    {
        // Condição não atendida não é falha: só não era para disparar.
        if ($checarCondicao && ! $this->executor->condicaoAtendida($automacao, $dados, $anteriores)) {
            return;
        }

        $acoes = (new AutomacaoAcaoModel())
            ->where('automacao_id', $automacao['id'])
            ->orderBy('ordem', 'ASC')
            ->findAll();

        try {
            $resultado = $this->executor->executar($automacao, $acoes, $dados, $this->data['gatilho'], $aPartirDe, [
                'registro_id' => $this->data['registro_id'] ?? null,
                'cadeia'      => [...($this->data['cadeia'] ?? []), $automacao['id']],
            ]);
        } catch (\Throwable $e) {
            $resultado = ['status' => 'erro', 'passos' => [], 'falhou_em' => $aPartirDe, 'retentavel' => true, 'erro' => $e->getMessage(), 'duracao_ms' => null];
        }

        $vaiRetentar = $resultado['status'] === 'erro' && $resultado['retentavel'] && $tentativa < self::MAX_TENTATIVAS;

        (new AutomacaoLogModel())->insert([
            'automacao_id' => $automacao['id'],
            'registro_id'  => $this->data['registro_id'] ?? null,
            'status'       => $vaiRetentar ? 'retentando' : $resultado['status'],
            'detalhes'     => $resultado['erro'],
            'gatilho'      => $this->data['gatilho'],
            'tentativa'    => $tentativa,
            'duracao_ms'   => $resultado['duracao_ms'],
            'passos'       => $resultado['passos'],
            'dados'        => $dados,
        ]);

        if ($vaiRetentar) {
            service('queue')
                ->setDelay(self::ESPERAS[$tentativa] ?? 300)
                ->push('automacoes', 'processar-automacoes', [
                    'modulo_id'        => $automacao['modulo_id'],
                    'gatilho'          => $this->data['gatilho'],
                    'registro_id'      => $this->data['registro_id'] ?? null,
                    'dados_registro'   => $dados,
                    'dados_anteriores' => $anteriores,
                    'cadeia'           => $this->data['cadeia'] ?? [],
                    'automacao_id'     => $automacao['id'],
                    'a_partir_da_acao' => $resultado['falhou_em'],
                    'tentativa'        => $tentativa + 1,
                ]);

            return;
        }

        if ($resultado['status'] === 'erro') {
            $this->pausarSeFalhandoSempre($automacao);
        }
    }

    /** Evita que uma automação quebrada continue disparando (e falhando) indefinidamente. */
    private function pausarSeFalhandoSempre(array $automacao): void
    {
        $automacaoId = $automacao['id'];

        $ultimas = (new AutomacaoLogModel())
            ->select('status')
            ->where('automacao_id', $automacaoId)
            ->whereIn('status', ['sucesso', 'erro'])
            ->orderBy('executado_em', 'DESC')
            ->limit(self::FALHAS_PARA_PAUSAR)
            ->findAll();

        if (count($ultimas) < self::FALHAS_PARA_PAUSAR || array_filter($ultimas, static fn ($l) => $l['status'] !== 'erro')) {
            return;
        }

        (new AutomacaoModel())->update($automacaoId, [
            'ativo'                 => false,
            'pausada_por_falhas_em' => date('Y-m-d H:i:s'),
        ]);

        \App\Services\Automacao\ModulosComAutomacao::esquecer($automacao['modulo_id']);
        \App\Services\AtividadeService::registrar(
            'automacao_pausada',
            "A automação “{$automacao['nome']}” foi pausada depois de " . self::FALHAS_PARA_PAUSAR . ' falhas seguidas',
            $automacao['modulo_id']
        );

        $this->avisarPausa($automacao);
    }

    /** Avisa quem criou a automação (ou os administradores, se não houver autor). */
    private function avisarPausa(array $automacao): void
    {
        $modulo = (new ModuloModel())->select('empresa_id')->find($automacao['modulo_id']);
        if (! $modulo) {
            return;
        }

        $notificacoes = new NotificacaoService();
        $destino      = $automacao['criado_por'] ? [$automacao['criado_por']] : $notificacoes->administradores($modulo['empresa_id']);

        $notificacoes->notificarSemFalhar($destino, $modulo['empresa_id'], [
            'tipo'     => 'automacao_pausada',
            'titulo'   => "Automação pausada: {$automacao['nome']}",
            'mensagem' => 'Ela falhou ' . self::FALHAS_PARA_PAUSAR . ' vezes seguidas e foi desativada. Veja o histórico, corrija e reative.',
            'link'     => '/automacoes',
        ]);
    }
}
