<?php

namespace App\Services\Automacao;

/**
 * Condições de uma automação: uma lista de regras unidas por E (todas) ou OU
 * (qualquer uma).
 *
 * Formato: { "logica": "e" | "ou", "regras": [ { "campo", "operador", "valor"?, "valor_de"? } ] }
 */
final class Condicoes
{
    public const MAX_REGRAS = 10;

    public const OPERADORES = [
        'igual', 'diferente', 'contem', 'nao_contem', 'vazio', 'preenchido',
        'maior', 'menor', 'mudou', 'mudou_para', 'mudou_de_para',
    ];

    /** Comparam com o valor anterior: só fazem sentido quando ele existe. */
    public const OPERADORES_DE_MUDANCA = ['mudou', 'mudou_para', 'mudou_de_para'];

    /** Dispensam o campo "valor". */
    private const SEM_VALOR = ['vazio', 'preenchido', 'mudou'];

    /** Sem regras, a automação sempre dispara. */
    public static function avaliar(?array $condicoes, array $dados, ?array $anteriores = null): bool
    {
        $regras = $condicoes['regras'] ?? [];
        if ($regras === []) {
            return true;
        }

        $resultados = array_map(static fn (array $r) => self::regra($r, $dados, $anteriores), $regras);

        return ($condicoes['logica'] ?? 'e') === 'ou'
            ? in_array(true, $resultados, true)
            : ! in_array(false, $resultados, true);
    }

    /** Converte a condição única do formato antigo (colunas campo_condicao_*). */
    public static function doFormatoAntigo(array $automacao): ?array
    {
        if (empty($automacao['campo_condicao_id'])) {
            return null;
        }

        return [
            'logica' => 'e',
            'regras' => [[
                'campo'    => $automacao['campo_condicao_id'],
                'operador' => $automacao['condicao_operador'] ?? 'igual',
                'valor'    => $automacao['condicao_valor'] ?? null,
                'valor_de' => $automacao['condicao_valor_de'] ?? null,
            ]],
        ];
    }

    /** Condições efetivas da automação, venham do formato novo ou do antigo. */
    public static function daAutomacao(array $automacao): ?array
    {
        return ! empty($automacao['condicoes']) ? $automacao['condicoes'] : self::doFormatoAntigo($automacao);
    }

    public static function exigeValor(string $operador): bool
    {
        return ! in_array($operador, self::SEM_VALOR, true);
    }

    public static function usaMudanca(?array $condicoes): bool
    {
        foreach ($condicoes['regras'] ?? [] as $regra) {
            if (in_array($regra['operador'] ?? null, self::OPERADORES_DE_MUDANCA, true)) {
                return true;
            }
        }

        return false;
    }

    private static function regra(array $regra, array $dados, ?array $anteriores): bool
    {
        $campo = $regra['campo'] ?? '';

        // Coluna com vários valores (ligação a vários itens): "igual" = está entre eles.
        if (is_array($dados[$campo] ?? null) && in_array($regra['operador'] ?? null, ['igual', 'diferente'], true)) {
            $tem = in_array((string) ($regra['valor'] ?? ''), array_map('strval', $dados[$campo]), true);

            return $regra['operador'] === 'igual' ? $tem : ! $tem;
        }

        $atual = self::texto($dados[$campo] ?? null);
        $alvo  = (string) ($regra['valor'] ?? '');
        $antes = $anteriores === null ? null : self::texto($anteriores[$campo] ?? null);
        $mudou = $antes !== null && $antes !== $atual;

        return match ($regra['operador'] ?? null) {
            'igual'         => $atual === $alvo,
            'diferente'     => $atual !== $alvo,
            'contem'        => $alvo !== '' && mb_stripos($atual, $alvo) !== false,
            'nao_contem'    => $alvo === '' || mb_stripos($atual, $alvo) === false,
            'vazio'         => trim($atual) === '',
            'preenchido'    => trim($atual) !== '',
            'maior'         => self::comparar($atual, $alvo) > 0,
            'menor'         => self::comparar($atual, $alvo) < 0,
            'mudou'         => $mudou,
            'mudou_para'    => $mudou && $atual === $alvo,
            'mudou_de_para' => $mudou && $antes === (string) ($regra['valor_de'] ?? '') && $atual === $alvo,
            default         => false,
        };
    }

    /**
     * Números comparam como números; datas AAAA-MM-DD comparam bem como texto.
     * Valor vazio nunca é "maior" nem "menor" que nada.
     */
    private static function comparar(string $a, string $b): int
    {
        if ($a === '' || $b === '') {
            return 0;
        }
        if (is_numeric($a) && is_numeric($b)) {
            return $a + 0 <=> $b + 0;
        }

        return strcmp($a, $b);
    }

    private static function texto(mixed $valor): string
    {
        return is_array($valor) ? implode(', ', $valor) : (string) $valor;
    }
}
