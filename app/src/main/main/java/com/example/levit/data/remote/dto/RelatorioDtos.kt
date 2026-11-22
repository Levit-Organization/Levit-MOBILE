package com.example.levit.data.remote.dto

import com.google.gson.annotations.SerializedName

/** Relatório agendado, como RelatorioService::apresentar devolve. */
data class Relatorio(
    val id: String,
    val nome: String?,
    val frequencia: String?,
    val dia: Int?,
    val destinatarios: List<String>?,
    val secoes: List<String>?,
    val ativo: Boolean?,
    @SerializedName("descricao_agenda") val descricaoAgenda: String?,
    @SerializedName("proximo_envio") val proximoEnvio: String?,
    @SerializedName("ultimo_envio") val ultimoEnvio: Envio?
)

data class Envio(
    val id: String?,
    val status: String?,
    @SerializedName("enviado_em") val enviadoEm: String?,
    @SerializedName("total_atividades") val totalAtividades: Int?,
    val erro: String?,
    @SerializedName("periodo_inicio") val periodoInicio: String?,
    @SerializedName("periodo_fim") val periodoFim: String?
)

data class RelatorioRequest(
    val nome: String,
    val frequencia: String,
    val dia: Int,
    val destinatarios: List<String>,
    val secoes: List<String>,
    val ativo: Boolean
)

/** { atual, anterior, variacao } de um indicador comparado ao período anterior. */
data class Comparacao(val atual: Double?, val anterior: Double?, val variacao: Double?)

data class Indicadores(
    @SerializedName("periodo_anterior") val periodoAnterior: Periodo?,
    val resumo: Resumo?,
    val prazos: PrazosIndicador?,
    val recrutamento: List<VagaIndicador>?,
    val financeiro: List<FinanceiroIndicador>?
)

data class Periodo(val inicio: String?, val fim: String?)

data class Resumo(
    @SerializedName("itens_criados") val itensCriados: Comparacao?,
    val candidatos: Comparacao?,
    val atividades: Comparacao?,
    @SerializedName("pessoas_ativas") val pessoasAtivas: Comparacao?
)

data class TaxaConclusao(val atual: Double?, val anterior: Double?)

data class PrazosIndicador(
    val vencendo: Comparacao?,
    val concluidos: Comparacao?,
    @SerializedName("taxa_conclusao") val taxaConclusao: TaxaConclusao?,
    @SerializedName("atrasados_agora") val atrasadosAgora: Double?,
    @SerializedName("atrasados_por_responsavel") val atrasadosPorResponsavel: List<TotalPorRotulo>?
)

data class TotalPorRotulo(val responsavel: String?, val categoria: String?, val total: Double?)

data class FaseIndicador(
    val nome: String?,
    val saida: Boolean?,
    val chegaram: Double?,
    val atual: Double?,
    @SerializedName("dias_medios") val diasMedios: Double?
)

data class VagaIndicador(
    @SerializedName("modulo_id") val moduloId: String?,
    val nome: String?,
    val total: Double?,
    val inscritos: Comparacao?,
    val concluidos: Comparacao?,
    val sairam: Comparacao?,
    val parados: Double?,
    val fases: List<FaseIndicador>?
)

data class FinanceiroIndicador(
    @SerializedName("modulo_id") val moduloId: String?,
    val nome: String?,
    val entradas: Comparacao?,
    val saidas: Comparacao?,
    val saldo: Comparacao?,
    @SerializedName("saidas_por_categoria") val saidasPorCategoria: List<TotalPorRotulo>?
)

/** GET /relatorios/tabelas: o que dá para agrupar e somar em cada tabela. */
data class TabelaRelatorio(
    val id: String,
    val nome: String?,
    val agrupar: List<ColunaRelatorio>?,
    val numeros: List<ColunaRelatorio>?
)

data class ColunaRelatorio(val id: String, val nome: String?, val tipo: String?, val grupo: String?)

data class Agrupamento(
    val tabela: String?,
    @SerializedName("agrupado_por") val agrupadoPor: String?,
    @SerializedName("coluna_valor") val colunaValor: String?,
    @SerializedName("total_itens") val totalItens: Double?,
    val cortados: Int?,
    val grupos: List<Grupo>?
)

data class Grupo(val rotulo: String?, val valor: Double?, val itens: Double?)
