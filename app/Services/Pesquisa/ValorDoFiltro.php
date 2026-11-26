<?php

namespace App\Services\Pesquisa;

/**
 * Valor digitado num filtro (cálculo puro): a conferência para o tipo da
 * coluna, usada ao montar o SQL, e o formato de data aceito, usado também na
 * descrição.
 */
final class ValorDoFiltro
{
    /** Tamanho máximo do valor de um filtro, em caracteres. */
    private const MAX_CARACTERES = 500;

    /**
     * Valor de comparação, conferido para o tipo da coluna.
     *
     * @throws \DomainException valor vazio, longo demais ou fora do formato do tipo
     */
    public static function conferir(mixed $valor, string $tipo, string $nome): string
    {
        $texto = is_scalar($valor) ? trim((string) $valor) : '';
        if ($texto === '') {
            throw new \DomainException("Informe o valor do filtro em “{$nome}”.");
        }
        if (mb_strlen($texto) > self::MAX_CARACTERES) {
            throw new \DomainException("O valor do filtro em “{$nome}” é longo demais.");
        }
        if ($tipo === 'numero' && ! is_numeric($texto)) {
            throw new \DomainException("O filtro em “{$nome}” precisa de um número.");
        }
        if (in_array($tipo, ['data', 'prazo', 'momento'], true) && ! self::dataValida($texto)) {
            throw new \DomainException("O filtro em “{$nome}” precisa de uma data (AAAA-MM-DD).");
        }

        return $texto;
    }

    /** Data no formato AAAA-MM-DD e que existe no calendário. */
    public static function dataValida(string $valor): bool
    {
        $data = \DateTime::createFromFormat('!Y-m-d', $valor);

        return $data !== false && $data->format('Y-m-d') === $valor;
    }
}
