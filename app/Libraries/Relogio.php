<?php

namespace App\Libraries;

/**
 * Datas no fuso de quem usa o Levit. O servidor guarda tudo em UTC; "hoje",
 * "vence amanhã" e o dia do relatório precisam seguir o horário de Brasília
 * (senão, depois das 21h, o "hoje" já seria o dia seguinte).
 */
final class Relogio
{
    public static function fuso(): \DateTimeZone
    {
        return new \DateTimeZone((string) env('FUSO_HORARIO', 'America/Sao_Paulo'));
    }

    public static function agora(): \DateTimeImmutable
    {
        return new \DateTimeImmutable('now', self::fuso());
    }

    /** AAAA-MM-DD de hoje no fuso local, com deslocamento opcional em dias. */
    public static function hoje(int $dias = 0): string
    {
        return self::agora()->modify(($dias >= 0 ? '+' : '') . $dias . ' days')->format('Y-m-d');
    }

    /** Converte um timestamp UTC do banco para o horário local. */
    public static function local(string $utc): \DateTimeImmutable
    {
        return (new \DateTimeImmutable($utc, new \DateTimeZone('UTC')))->setTimezone(self::fuso());
    }
}
