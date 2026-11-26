<?php

namespace App\Services;

use App\Services\Indicadores\Agrupamento;
use App\Services\Indicadores\Comparacao;
use App\Services\Indicadores\FunilRecrutamento;
use App\Services\Indicadores\Painel;
use App\Services\Indicadores\RelatorioMontado;

/**
 * Indicadores sobre os DADOS da empresa (não só sobre a atividade): funil de
 * recrutamento, entradas e saídas do financeiro, cumprimento de prazos e um
 * relatório montado pela pessoa (agrupar uma tabela por uma coluna).
 *
 * Todo indicador com período vem junto do valor do período anterior de mesmo
 * tamanho, para a tela e o PDF mostrarem a variação.
 *
 * Períodos: `$inicio` inclusivo e `$fim` exclusivo, no fuso local (Relogio).
 *
 * Ponto de entrada dos controllers e do relatório agendado. As consultas
 * ficam em Indicadores\Painel e Indicadores\RelatorioMontado; os cálculos
 * puros, em Indicadores\Comparacao, FunilRecrutamento e Agrupamento.
 */
class IndicadoresService
{
    /** Fases de quem saiu do processo: ficam fora do funil e contam como saída. */
    public const FASES_DE_SAIDA = FunilRecrutamento::FASES_DE_SAIDA;

    /** Candidato parado na mesma fase há esse tanto de dias pede atenção. */
    public const DIAS_PARADO = FunilRecrutamento::DIAS_PARADO;

    /** Colunas pelas quais dá para agrupar no relatório montado. */
    public const TIPOS_AGRUPAVEIS = Agrupamento::TIPOS_AGRUPAVEIS;

    public const OPERACOES = Agrupamento::OPERACOES;

    // ------------------------------------------------------------------
    // Consultas
    // ------------------------------------------------------------------

    public function painel(string $empresaId, \DateTimeImmutable $inicio, \DateTimeImmutable $fim): array
    {
        return (new Painel())->painel($empresaId, $inicio, $fim);
    }

    /** Tabelas de dados com as colunas que servem para agrupar e para somar. */
    public function tabelas(string $empresaId): array
    {
        return (new RelatorioMontado())->tabelas($empresaId);
    }

    /** Agrupa os itens de uma tabela por uma coluna (ver RelatorioMontado::agrupar). */
    public function agrupar(string $empresaId, array $params): array
    {
        return (new RelatorioMontado())->agrupar($empresaId, $params);
    }

    /**
     * Os filtros do relatório montado em português, para o PDF.
     *
     * @return list<string>
     */
    public function descreverFiltros(string $empresaId, array $filtros): array
    {
        return (new RelatorioMontado())->descreverFiltros($empresaId, $filtros);
    }

    // ------------------------------------------------------------------
    // Cálculos puros (atalhos para as classes em App\Services\Indicadores)
    // ------------------------------------------------------------------

    public static function periodoAnterior(\DateTimeImmutable $inicio, \DateTimeImmutable $fim): array
    {
        return Comparacao::periodoAnterior($inicio, $fim);
    }

    public static function variacao(float|int $atual, float|int $anterior): ?float
    {
        return Comparacao::variacao($atual, $anterior);
    }

    public static function comparar(float|int $atual, float|int $anterior): array
    {
        return Comparacao::comparar($atual, $anterior);
    }

    public static function descreverPeriodo(\DateTimeImmutable $inicio, \DateTimeImmutable $fim): array
    {
        return Comparacao::descreverPeriodo($inicio, $fim);
    }

    public static function faseDeSaida(string $nome): bool
    {
        return FunilRecrutamento::faseDeSaida($nome);
    }

    /**
     * @param array<int, array{id: string, nome: string, ordem: int|string}>                                 $fases     ordenadas
     * @param array<int, array{id: string, fase_atual_id: string, criado_em: string}>                         $candidatos
     * @param array<int, array{candidato_id: string, fase_anterior_id: ?string, fase_nova_id: ?string, criado_em: string}> $historico ordenado por data
     * @param array{atual: array{0: string, 1: string}, anterior: array{0: string, 1: string}}               $periodos
     */
    public static function funil(array $fases, array $candidatos, array $historico, array $periodos, string $agora): array
    {
        return FunilRecrutamento::funil($fases, $candidatos, $historico, $periodos, $agora);
    }

    /** @return array{0: array, 1: ?array} a coluna (com o nome já composto) e a ligação usada */
    public static function colunaDeAgrupamento(string $chave, string $moduloId, array $campos): array
    {
        return Agrupamento::colunaDeAgrupamento($chave, $moduloId, $campos);
    }

    /**
     * @param array<string, ?string> $nomes      id => nome, para responsável (pessoas) e ligação (itens)
     * @param int|null               $totalItens itens distintos, quando um item pode cair em vários grupos
     */
    public static function montarGrupos(array $linhas, string $operacao, string $tipoGrupo, array $nomes = [], ?int $totalItens = null): array
    {
        return Agrupamento::montarGrupos($linhas, $operacao, $tipoGrupo, $nomes, $totalItens);
    }
}
