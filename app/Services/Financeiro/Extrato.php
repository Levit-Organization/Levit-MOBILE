<?php

namespace App\Services\Financeiro;

/** Saldo e totais de uma lista de lançamentos (cálculo puro, testável sem banco). */
final class Extrato
{
    /**
     * Cada lançamento ganha o saldo acumulado até ele (na ordem recebida);
     * o resumo traz os totais do conjunto. Valores arredondados em 2 casas.
     *
     * @param list<array> $lancamentos com `tipo` ('entrada' ou não) e `valor`
     *
     * @return array{lancamentos: list<array>, resumo: array{total_entradas: float, total_saidas: float, saldo_periodo: float}}
     */
    public static function comSaldo(array $lancamentos): array
    {
        $saldoAcumulado = 0.0;
        $totalEntradas  = 0.0;
        $totalSaidas    = 0.0;

        foreach ($lancamentos as &$lancamento) {
            $valor = (float) $lancamento['valor'];

            if ($lancamento['tipo'] === 'entrada') {
                $saldoAcumulado += $valor;
                $totalEntradas  += $valor;
            } else {
                $saldoAcumulado -= $valor;
                $totalSaidas    += $valor;
            }

            $lancamento['saldo_acumulado'] = round($saldoAcumulado, 2);
        }
        unset($lancamento);

        return [
            'lancamentos' => $lancamentos,
            'resumo'      => [
                'total_entradas' => round($totalEntradas, 2),
                'total_saidas'   => round($totalSaidas, 2),
                'saldo_periodo'  => round($totalEntradas - $totalSaidas, 2),
            ],
        ];
    }
}
