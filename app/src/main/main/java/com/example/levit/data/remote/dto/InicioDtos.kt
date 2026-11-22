package com.example.levit.data.remote.dto

import com.google.gson.annotations.SerializedName

/** Um item com prazo em aberto (PrazoService::listar). */
data class ItemPrazo(
    @SerializedName("registro_id") val registroId: String?,
    @SerializedName("modulo_id") val moduloId: String?,
    @SerializedName("modulo_nome") val moduloNome: String?,
    @SerializedName("modulo_icone") val moduloIcone: String?,
    val titulo: String?,
    @SerializedName("coluna_prazo") val colunaPrazo: String?,
    val prazo: String?,
    @SerializedName("responsavel_nome") val responsavelNome: String?,
    val meu: Boolean?
)

/** GET /itens/prazos: atrasados, vencem hoje e próximos 7 dias. */
data class PrazosAbertos(
    val atrasados: List<ItemPrazo>?,
    val hoje: List<ItemPrazo>?,
    val proximos: List<ItemPrazo>?
)
