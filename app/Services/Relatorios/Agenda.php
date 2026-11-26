<?php

namespace App\Services\Relatorios;

use App\Libraries\Relogio;

/**
 * Agenda dos relatórios (cálculo puro, testável sem banco): quando cada
 * relatório vence, que período cobre, como se chama o arquivo e quais seções
 * entram no PDF.
 */
final class Agenda
{
    public const FREQUENCIAS = ['diario', 'semanal', 'mensal'];

    /**
     * Partes que podem entrar no PDF, na ordem em que aparecem. As quatro
     * primeiras vêm do IndicadoresService (dados das tabelas); as outras, do
     * histórico de atividades.
     */
    public const SECOES = ['resumo', 'recrutamento', 'financeiro', 'prazos', 'atrasados', 'ranking', 'atividades'];

    /** Relatórios novos: tudo menos o ranking por pessoa (que soa como vigilância). */
    public const SECOES_PADRAO = ['resumo', 'recrutamento', 'financeiro', 'prazos', 'atrasados', 'atividades'];

    /** Relatórios criados antes da escolha de seções continuam como eram. */
    public const SECOES_LEGADO = ['atrasados', 'ranking', 'atividades'];

    private const DIAS_SEMANA = [1 => 'segunda-feira', 'terça-feira', 'quarta-feira', 'quinta-feira', 'sexta-feira', 'sábado', 'domingo'];

    /** Filtra e ordena as seções pedidas; lista vazia ou inválida vira o padrão. */
    public static function secoesValidas(mixed $secoes): array
    {
        $pedidas = array_filter((array) $secoes, 'is_string');
        $validas = array_values(array_filter(self::SECOES, static fn ($s) => in_array($s, $pedidas, true)));

        return $validas === [] ? self::SECOES_PADRAO : $validas;
    }

    /** Seções gravadas no relatório agendado (nulo = criado antes da escolha de seções). */
    public static function secoesDoRelatorio(array $r): array
    {
        $gravadas = is_string($r['secoes'] ?? null) ? json_decode($r['secoes'], true) : ($r['secoes'] ?? null);

        return is_array($gravadas) && $gravadas !== [] ? self::secoesValidas($gravadas) : self::SECOES_LEGADO;
    }

    public static function descreverAgenda(string $frequencia, int $dia): string
    {
        return match ($frequencia) {
            'diario'  => 'Todo dia',
            'semanal' => 'Toda ' . self::DIAS_SEMANA[$dia],
            default   => "Todo dia {$dia} do mês",
        };
    }

    /** Última data agendada que já chegou (hoje incluso). */
    public static function ultimaDataAgendada(string $frequencia, int $dia, string $hoje): string
    {
        $d = new \DateTimeImmutable($hoje);
        if ($frequencia === 'diario') {
            return $hoje;
        }
        if ($frequencia === 'semanal') {
            $diff = ((int) $d->format('N') - $dia + 7) % 7;

            return $d->modify("-{$diff} days")->format('Y-m-d');
        }
        $esteMes = $d->setDate((int) $d->format('Y'), (int) $d->format('m'), $dia);

        return ($esteMes <= $d ? $esteMes : $esteMes->modify('-1 month'))->format('Y-m-d');
    }

    /**
     * Devido quando a data agendada mais recente já chegou e ainda não houve
     * envio desde ela (se o cron falhar um dia, envia no seguinte).
     */
    public static function estaDevido(string $frequencia, int $dia, string $hoje, ?string $ultimoEnvioUtc, string $criadoEmUtc): bool
    {
        $agendada = self::ultimaDataAgendada($frequencia, $dia, $hoje);
        $desde    = $ultimoEnvioUtc ?? $criadoEmUtc;

        return Relogio::local($desde)->format('Y-m-d') < $agendada;
    }

    public static function proximaData(string $frequencia, int $dia, string $hoje, ?string $ultimoEnvioUtc): string
    {
        $ultima = self::ultimaDataAgendada($frequencia, $dia, $hoje);
        $jaFoi  = $ultimoEnvioUtc !== null && Relogio::local($ultimoEnvioUtc)->format('Y-m-d') >= $ultima;
        if ($ultima === $hoje && ! $jaFoi) {
            return $hoje;
        }

        $passo = ['diario' => '+1 day', 'semanal' => '+7 days'][$frequencia] ?? '+1 month';

        return (new \DateTimeImmutable($ultima))->modify($passo)->format('Y-m-d');
    }

    /** Período coberto por um envio na data `$hoje`: o dia, os 7 dias ou o mês anteriores. */
    public static function periodo(string $frequencia, string $hoje): array
    {
        $fim    = new \DateTimeImmutable($hoje . ' 00:00:00', Relogio::fuso());
        $inicio = $fim->modify(['diario' => '-1 day', 'semanal' => '-7 days'][$frequencia] ?? '-1 month');

        return [$inicio, $fim];
    }

    public static function textoDoPeriodo(\DateTimeImmutable $inicio, \DateTimeImmutable $fim): string
    {
        // O fim é exclusivo (meia-noite do dia do envio): mostra o dia anterior.
        if ($inicio->diff($fim)->days === 1) {
            return $inicio->format('d/m/Y');
        }

        return $inicio->format('d/m/Y') . ' a ' . $fim->modify('-1 day')->format('d/m/Y');
    }

    public static function nomeDoArquivo(string $nome, \DateTimeImmutable $fim): string
    {
        $base = trim(preg_replace('/[^A-Za-z0-9]+/', '-', iconv('UTF-8', 'ASCII//TRANSLIT//IGNORE', $nome) ?: 'relatorio'), '-') ?: 'relatorio';

        return strtolower($base) . '-' . $fim->modify('-1 day')->format('Y-m-d') . '.pdf';
    }
}
