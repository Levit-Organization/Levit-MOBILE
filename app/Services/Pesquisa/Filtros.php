<?php

namespace App\Services\Pesquisa;

use App\Exceptions\AcessoNegadoException;

/**
 * Pesquisa avançada nos itens de uma tabela, inclusive pelos dados das tabelas
 * ligadas a ela (colunas do tipo "vinculo"). Vira um trecho de WHERE com
 * parâmetros nomeados (:f1:, :f2:...): nada do que vem do usuário entra cru no
 * SQL, e as colunas só são aceitas se existirem na tabela certa.
 *
 * Formato (o das condições de automação, mais grupos e ligações):
 *   { "logica": "e" | "ou", "regras": [ regra | grupo ] }
 *   regra: { "campo", "operador", "valor"?, "valor_ate"?,
 *            "via"?: [ { "campo", "sentido": "ida" | "volta" } ],
 *            "quantificador"?: "algum" | "nenhum" }
 *
 * - "via" percorre ligações antes de olhar a coluna. "ida" vai do item para o
 *   que ele aponta (Pedido › Cliente); "volta" vai para os itens de outra tabela
 *   que apontam para ele (Cliente › Pedidos do cliente).
 * - Com "via", a regra vale se ALGUM item ligado atende (ou NENHUM, com o
 *   quantificador "nenhum"). Sem "campo" no fim, só pergunta se há item ligado
 *   (operadores "preenchido" e "vazio").
 *
 * A tradução para SQL fica em MontagemDoSql, a conferência dos valores em
 * ValorDoFiltro e o texto para relatórios em DescricaoDosFiltros.
 */
final class Filtros
{
    public const MAX_REGRAS = 20;

    /** Ligações seguidas numa regra (Pedido › Cliente › Cidade = 1). */
    public const MAX_SALTOS = 3;

    private const IGUALDADE = ['igual', 'diferente', 'vazio', 'preenchido'];
    private const ORDEM     = ['igual', 'diferente', 'maior', 'menor', 'entre', 'vazio', 'preenchido'];

    public const OPERADORES_POR_TIPO = [
        'texto'       => ['igual', 'diferente', 'contem', 'nao_contem', 'vazio', 'preenchido'],
        'numero'      => self::ORDEM,
        'data'        => self::ORDEM,
        'prazo'       => self::ORDEM,
        'momento'     => self::ORDEM,
        'selecao'     => self::IGUALDADE,
        'responsavel' => self::IGUALDADE,
        'recorrencia' => self::IGUALDADE,
        'vinculo'     => self::IGUALDADE,
    ];

    /** Colunas que todo item tem (tipo "momento": comparadas pelo dia). */
    public const COLUNAS_DO_SISTEMA = ['criado_em' => 'Criado em', 'atualizado_em' => 'Atualizado em'];

    /**
     * @param array<string, array> $campos  colunas de todas as tabelas da empresa, por id
     *                                      (modulo_id, nome, tipo, modulo_vinculado_id)
     * @param \Closure(string): bool $podeVer se quem pesquisa enxerga a tabela
     * @param array<string, string> $tabelas nome das tabelas por id (só para descrever)
     */
    public function __construct(private readonly array $campos, private readonly \Closure $podeVer, private readonly array $tabelas = [])
    {
    }

    /**
     * Os filtros em português, um por linha, para mostrar num relatório
     * ("Cliente › Cidade contém “Recife”"). Espera filtros já aceitos por paraSql.
     *
     * @param \Closure(array $campo, string $valor): string $nomeDoValor nome de uma pessoa ou de um item ligado
     *
     * @return list<string>
     */
    public function descrever(array $filtros, \Closure $nomeDoValor): array
    {
        return (new DescricaoDosFiltros($this->campos, $this->tabelas))->descrever($filtros, $nomeDoValor);
    }

    /**
     * @return array{0: string, 1: array<string, string>} condição e parâmetros nomeados
     *
     * @throws \DomainException      filtro malformado ou com valor inválido
     * @throws AcessoNegadoException ligação para uma tabela que a pessoa não vê
     */
    public function paraSql(array $filtros, string $moduloId, string $apelido = 'r'): array
    {
        return (new MontagemDoSql($this->campos, $this->podeVer))->montar($filtros, $moduloId, $apelido);
    }

    public static function uuidValido(string $valor): bool
    {
        return (bool) preg_match('/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i', $valor);
    }
}
