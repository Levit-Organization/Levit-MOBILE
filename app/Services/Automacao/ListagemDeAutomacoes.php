<?php

namespace App\Services\Automacao;

use App\Models\AutomacaoModel;

/**
 * Automações no formato que a tela espera: gatilhos e condições no formato
 * novo, ações decodificadas e as estatísticas da semana.
 */
class ListagemDeAutomacoes
{
    public function __construct(protected AutomacaoModel $automacaoModel = new AutomacaoModel())
    {
    }

    /** Automações de um módulo, prontas para a tela. */
    public function doModulo(string $moduloId): array
    {
        return self::completar($this->consulta()->where('automacao.modulo_id', $moduloId)->findAll());
    }

    /** Automações de vários módulos, das mais antigas para as mais novas. */
    public function dosModulos(array $moduloIds): array
    {
        return self::completar(
            $this->consulta()->whereIn('automacao.modulo_id', $moduloIds)->orderBy('automacao.criado_em', 'ASC')->findAll()
        );
    }

    /**
     * Linhas antigas (sem `gatilhos` ou `condicoes`) são devolvidas no formato
     * novo: a tela só precisa conhecer um formato.
     */
    public static function comGatilhos(array $automacao): array
    {
        if (empty($automacao['gatilhos'])) {
            $automacao['gatilhos'] = [$automacao['gatilho']];
        }
        $automacao['condicoes'] = Condicoes::daAutomacao($automacao);

        return $automacao;
    }

    /**
     * Automações com as ações e as estatísticas da semana na mesma consulta
     * (antes eram três: automações, ações e estatísticas).
     */
    public function consulta(): AutomacaoModel
    {
        $desde = db_connect()->escape(date('Y-m-d H:i:s', strtotime('-7 days')));

        return $this->automacaoModel
            ->select('automacao.*')
            ->select(
                "(SELECT COALESCE(json_agg(ac ORDER BY ac.ordem), '[]') FROM automacao_acao ac WHERE ac.automacao_id = automacao.id) AS acoes_json",
                false
            )
            ->select('est.execucoes_7d, est.falhas_7d, est.ultima_execucao_em', false)
            ->join(
                "LATERAL (SELECT
                    COUNT(*) FILTER (WHERE l.executado_em >= {$desde} AND l.status IN ('sucesso', 'erro')) AS execucoes_7d,
                    COUNT(*) FILTER (WHERE l.executado_em >= {$desde} AND l.status = 'erro') AS falhas_7d,
                    MAX(l.executado_em) AS ultima_execucao_em
                  FROM automacao_log l WHERE l.automacao_id = automacao.id) est",
                // Precisa de um operador: só "TRUE" o CodeIgniter transforma em USING (TRUE).
                'TRUE = TRUE',
                'left',
                false
            );
    }

    /** Gatilhos/condições normalizados e o formato de ações e estatísticas que a tela espera. */
    public static function completar(array $automacoes): array
    {
        foreach ($automacoes as &$automacao) {
            $acoes = json_decode((string) ($automacao['acoes_json'] ?? '[]'), true) ?: [];
            foreach ($acoes as &$acao) {
                if (is_string($acao['configuracao'] ?? null)) {
                    $acao['configuracao'] = json_decode($acao['configuracao'], true);
                }
            }
            unset($acao);

            $automacao                 = self::comGatilhos($automacao);
            $automacao['acoes']        = $acoes;
            $automacao['estatisticas'] = [
                'execucoes_7d'       => (int) ($automacao['execucoes_7d'] ?? 0),
                'falhas_7d'          => (int) ($automacao['falhas_7d'] ?? 0),
                'ultima_execucao_em' => $automacao['ultima_execucao_em'] ?? null,
            ];
            unset($automacao['acoes_json'], $automacao['execucoes_7d'], $automacao['falhas_7d'], $automacao['ultima_execucao_em']);
        }
        unset($automacao);

        return $automacoes;
    }
}
