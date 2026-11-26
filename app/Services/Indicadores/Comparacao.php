<?php

namespace App\Services\Indicadores;

/**
 * Comparação com o período anterior (cálculo puro): todo indicador com
 * período vem junto do valor do período anterior de mesmo tamanho.
 *
 * Períodos: `$inicio` inclusivo e `$fim` exclusivo, no fuso local (Relogio).
 */
final class Comparacao
{
    /** Período imediatamente anterior, com o mesmo número de dias. */
    public static function periodoAnterior(\DateTimeImmutable $inicio, \DateTimeImmutable $fim): array
    {
        $dias = (int) $inicio->diff($fim)->days;

        return [$inicio->modify("-{$dias} days"), $inicio];
    }

    /** Variação percentual; nula quando o anterior é zero e o atual não (não dá para calcular). */
    public static function variacao(float|int $atual, float|int $anterior): ?float
    {
        if ((float) $anterior === 0.0) {
            return (float) $atual === 0.0 ? 0.0 : null;
        }

        return round(($atual - $anterior) / abs($anterior) * 100, 1);
    }

    public static function comparar(float|int $atual, float|int $anterior): array
    {
        return ['atual' => $atual, 'anterior' => $anterior, 'variacao' => self::variacao($atual, $anterior)];
    }

    /** Datas inclusivas para exibir (o fim é exclusivo: mostra o dia anterior). */
    public static function descreverPeriodo(\DateTimeImmutable $inicio, \DateTimeImmutable $fim): array
    {
        return ['inicio' => $inicio->format('Y-m-d'), 'fim' => $fim->modify('-1 day')->format('Y-m-d')];
    }

    /** Data e hora em UTC, no formato do banco. */
    public static function utc(\DateTimeImmutable $d): string
    {
        return $d->setTimezone(new \DateTimeZone('UTC'))->format('Y-m-d H:i:s');
    }
}
