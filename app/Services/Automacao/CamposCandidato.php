<?php

namespace App\Services\Automacao;

/**
 * Em vagas (módulos de recrutamento) as automações trabalham com o candidato,
 * não com registros. Estes são os "campos" que condições e {{variáveis}} enxergam.
 */
final class CamposCandidato
{
    public const CAMPOS = [
        'nome'           => 'Nome',
        'email'          => 'E-mail',
        'telefone'       => 'Telefone',
        'mensagem'       => 'Mensagem',
        'etapa'          => 'Etapa',
        'etapa_anterior' => 'Etapa anterior',
    ];

    /** Dados do candidato no formato que o motor de automações usa. */
    public static function dados(array $candidato, ?string $etapa, ?string $etapaAnterior = null): array
    {
        return [
            'nome'           => $candidato['nome'] ?? '',
            'email'          => $candidato['email'] ?? '',
            'telefone'       => $candidato['telefone'] ?? '',
            'mensagem'       => $candidato['mensagem'] ?? '',
            'etapa'          => $etapa ?? '',
            'etapa_anterior' => $etapaAnterior ?? '',
        ];
    }

    /** Nome (minúsculo) e chave → chave, para resolver {{Nome}}, {{E-mail}} etc. */
    public static function mapaDeVariaveis(): array
    {
        $mapa = [];
        foreach (self::CAMPOS as $chave => $rotulo) {
            $mapa[mb_strtolower($rotulo)] = $chave;
            $mapa[$chave]                 = $chave;
        }

        return $mapa;
    }
}
