<?php

namespace App\Services\Pesquisa;

use App\Exceptions\AcessoNegadoException;

/**
 * Traduz um filtro (formato em Filtros) num trecho de WHERE. Uma instância
 * por consulta: guarda os parâmetros nomeados (:f1:, :f2:...), os apelidos das
 * subconsultas (v1, v2...) e quantas regras já passaram.
 *
 * Confere tudo no caminho: colunas da tabela certa, operador que combina com
 * o tipo, ligações que partem da tabela e levam a uma que a pessoa vê.
 */
final class MontagemDoSql
{
    private const MAX_NIVEIS = 3;

    /** @var array<string, string> */
    private array $binds = [];

    private int $apelidos = 0;
    private int $totalRegras = 0;

    /**
     * @param array<string, array> $campos colunas de todas as tabelas da empresa, por id
     * @param \Closure(string): bool $podeVer se quem pesquisa enxerga a tabela
     */
    public function __construct(private readonly array $campos, private readonly \Closure $podeVer)
    {
    }

    /**
     * @return array{0: string, 1: array<string, string>} condição e parâmetros nomeados
     *
     * @throws \DomainException      filtro malformado ou com valor inválido
     * @throws AcessoNegadoException ligação para uma tabela que a pessoa não vê
     */
    public function montar(array $filtros, string $moduloId, string $apelido): array
    {
        $this->binds       = [];
        $this->apelidos    = 0;
        $this->totalRegras = 0;

        $sql = $this->grupo($filtros, $moduloId, $apelido, 1);

        return [$sql ?? 'TRUE', $this->binds];
    }

    private function grupo(array $grupo, string $moduloId, string $a, int $nivel): ?string
    {
        if ($nivel > self::MAX_NIVEIS) {
            throw new \DomainException('O filtro tem grupos dentro de grupos demais.');
        }

        $logica = $grupo['logica'] ?? 'e';
        $regras = $grupo['regras'] ?? [];
        if (! in_array($logica, ['e', 'ou'], true) || ! is_array($regras)) {
            throw new \DomainException('Filtro inválido. Refaça o filtro.');
        }

        $partes = [];
        foreach ($regras as $regra) {
            if (! is_array($regra)) {
                throw new \DomainException('Filtro inválido. Refaça o filtro.');
            }
            $partes[] = array_key_exists('regras', $regra)
                ? $this->grupo($regra, $moduloId, $a, $nivel + 1)
                : $this->regra($regra, $moduloId, $a);
        }

        $partes = array_values(array_filter($partes, static fn ($p) => $p !== null));
        if ($partes === []) {
            return null;
        }

        return count($partes) === 1 ? $partes[0] : '(' . implode($logica === 'ou' ? ' OR ' : ' AND ', $partes) . ')';
    }

    private function regra(array $regra, string $moduloId, string $a): string
    {
        if (++$this->totalRegras > Filtros::MAX_REGRAS) {
            throw new \DomainException('Use no máximo ' . Filtros::MAX_REGRAS . ' filtros de uma vez.');
        }

        $via = $regra['via'] ?? [];
        if (! is_array($via) || ! array_is_list($via)) {
            throw new \DomainException('Filtro inválido. Refaça o filtro.');
        }
        if (count($via) > Filtros::MAX_SALTOS) {
            throw new \DomainException('Um filtro passa por no máximo ' . Filtros::MAX_SALTOS . ' ligações entre tabelas.');
        }
        if ($via === []) {
            return $this->condicao($regra, $moduloId, $a);
        }

        $quantificador = $regra['quantificador'] ?? 'algum';
        if (! in_array($quantificador, ['algum', 'nenhum'], true)) {
            throw new \DomainException('Filtro inválido. Refaça o filtro.');
        }

        $sql = $this->saltar($via, $regra, $moduloId, $a);

        return $quantificador === 'nenhum' ? "NOT {$sql}" : $sql;
    }

    /** Uma subconsulta EXISTS por ligação; a condição fica na última tabela. */
    private function saltar(array $via, array $regra, string $moduloId, string $a): string
    {
        $salto = array_shift($via);
        if (! is_array($salto)) {
            throw new \DomainException('Filtro inválido. Refaça o filtro.');
        }

        $campo = $this->campo($salto['campo'] ?? null);
        if ($campo['tipo'] !== 'vinculo' || empty($campo['modulo_vinculado_id'])) {
            throw new \DomainException("A coluna “{$campo['nome']}” não liga a outra tabela.");
        }

        $volta   = ($salto['sentido'] ?? 'ida') === 'volta';
        $origem  = $volta ? $campo['modulo_vinculado_id'] : $campo['modulo_id'];
        $destino = $volta ? $campo['modulo_id'] : $campo['modulo_vinculado_id'];
        if ($origem !== $moduloId) {
            throw new \DomainException("A ligação “{$campo['nome']}” não parte desta tabela.");
        }
        if (! ($this->podeVer)($destino)) {
            throw new AcessoNegadoException("Você não tem acesso à tabela ligada pela coluna “{$campo['nome']}”.");
        }

        $b       = 'v' . ++$this->apelidos;
        $chave   = $this->param($campo['id']);
        $ligacao = $volta
            ? self::contem($b, $chave, "{$a}.id::text")
            : "{$b}.id IN (" . self::idsLigados($a, $chave) . ')';

        $existe = "EXISTS (SELECT 1 FROM registro {$b} WHERE {$b}.modulo_id = {$this->param($destino)} AND {$ligacao}";

        if ($via !== []) {
            return $existe . ' AND ' . $this->saltar($via, $regra, $destino, $b) . ')';
        }

        if ((string) ($regra['campo'] ?? '') === '') {
            return match ($regra['operador'] ?? null) {
                'preenchido' => $existe . ')',
                'vazio'      => "NOT {$existe})",
                default      => throw new \DomainException('Escolha a coluna da tabela ligada.'),
            };
        }

        return $existe . ' AND ' . $this->condicao($regra, $destino, $b) . ')';
    }

    private function condicao(array $regra, string $moduloId, string $a): string
    {
        $campoId  = (string) ($regra['campo'] ?? '');
        $operador = (string) ($regra['operador'] ?? '');

        if (isset(Filtros::COLUNAS_DO_SISTEMA[$campoId])) {
            $tipo = 'momento';
            $nome = Filtros::COLUNAS_DO_SISTEMA[$campoId];
            $expr = "{$a}.{$campoId}::date";
        } else {
            $campo = $this->campo($campoId);
            if ($campo['modulo_id'] !== $moduloId) {
                throw new \DomainException("A coluna “{$campo['nome']}” não é desta tabela.");
            }
            $tipo  = $campo['tipo'];
            $nome  = $campo['nome'];
            $chave = $this->param($campo['id']);
            $expr  = $tipo === 'numero'
                ? "(CASE WHEN jsonb_typeof({$a}.dados->{$chave}) = 'number' THEN ({$a}.dados->>{$chave})::numeric END)"
                : "({$a}.dados->>{$chave})";
        }

        if (! in_array($operador, Filtros::OPERADORES_POR_TIPO[$tipo] ?? [], true)) {
            throw new \DomainException("Essa comparação não vale para a coluna “{$nome}”.");
        }

        if ($tipo === 'vinculo') {
            return $this->condicaoDeVinculo($operador, $regra, $a, $chave, $nome);
        }

        if ($operador === 'vazio' || $operador === 'preenchido') {
            $vazio = $tipo === 'texto' ? "({$expr} IS NULL OR btrim({$expr}) = '')" : "({$expr} IS NULL)";

            return $operador === 'vazio' ? $vazio : "NOT {$vazio}";
        }

        $texto = ValorDoFiltro::conferir($regra['valor'] ?? null, $tipo, $nome);

        if ($operador === 'contem' || $operador === 'nao_contem') {
            $padrao = $this->param(self::padraoLike($texto));

            return $operador === 'contem' ? "{$expr} ILIKE {$padrao}" : "({$expr} IS NULL OR {$expr} NOT ILIKE {$padrao})";
        }

        $cast  = ['numero' => '::numeric', 'momento' => '::date'][$tipo] ?? '';
        $valor = $this->param($texto) . $cast;

        return match ($operador) {
            'igual'     => $tipo === 'texto' ? "lower({$expr}) = lower({$valor})" : "{$expr} = {$valor}",
            'diferente' => "({$expr} IS NULL OR " . ($tipo === 'texto' ? "lower({$expr}) <> lower({$valor})" : "{$expr} <> {$valor}") . ')',
            'maior'     => "{$expr} > {$valor}",
            'menor'     => "{$expr} < {$valor}",
            'entre'     => "{$expr} BETWEEN SYMMETRIC {$valor} AND "
                . $this->param(ValorDoFiltro::conferir($regra['valor_ate'] ?? null, $tipo, $nome)) . $cast,
        };
    }

    /** Coluna de ligação: "igual" = aponta para aquele item (entre os que tiver). */
    private function condicaoDeVinculo(string $operador, array $regra, string $a, string $chave, string $nome): string
    {
        if ($operador === 'vazio' || $operador === 'preenchido') {
            $vazio = "(COALESCE({$a}.dados->{$chave}, '[]'::jsonb) = '[]'::jsonb)";

            return $operador === 'vazio' ? $vazio : "NOT {$vazio}";
        }

        $id = trim((string) (is_scalar($regra['valor'] ?? null) ? $regra['valor'] : ''));
        if (! Filtros::uuidValido($id)) {
            throw new \DomainException("Escolha o item da coluna “{$nome}”.");
        }
        $contem = self::contem($a, $chave, $this->param($id) . '::text');

        return $operador === 'igual' ? $contem : "NOT {$contem}";
    }

    private function campo(mixed $id): array
    {
        $campo = is_string($id) ? ($this->campos[$id] ?? null) : null;
        if ($campo === null) {
            throw new \DomainException('Uma das colunas do filtro não existe mais. Refaça o filtro.');
        }

        return $campo;
    }

    private function param(string $valor): string
    {
        $nome               = 'f' . (count($this->binds) + 1);
        $this->binds[$nome] = $valor;

        return ":{$nome}:";
    }

    /**
     * A coluna de ligação do item `$a` contém o id `$id` (expressão SQL de texto).
     * Guarda um id (um item) ou uma lista (vários): as duas formas usam o índice GIN.
     */
    private static function contem(string $a, string $chave, string $id): string
    {
        return "({$a}.dados @> jsonb_build_object({$chave}::text, {$id}) OR {$a}.dados @> jsonb_build_object({$chave}::text, jsonb_build_array({$id})))";
    }

    /** Ids guardados na coluna de ligação do item `$a`, como uuid (lista ou um só). */
    private static function idsLigados(string $a, string $chave): string
    {
        return 'SELECT e::uuid FROM jsonb_array_elements_text(CASE jsonb_typeof(' . "{$a}.dados->{$chave}"
            . ") WHEN 'array' THEN {$a}.dados->{$chave} WHEN 'string' THEN jsonb_build_array({$a}.dados->{$chave}) ELSE '[]'::jsonb END) AS e"
            . " WHERE e ~* '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$'";
    }

    /** "contém": % e _ digitados valem como texto, não como curinga. */
    private static function padraoLike(string $texto): string
    {
        return '%' . addcslashes($texto, '\\%_') . '%';
    }
}
