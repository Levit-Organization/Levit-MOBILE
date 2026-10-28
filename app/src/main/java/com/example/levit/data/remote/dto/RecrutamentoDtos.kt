package com.example.levit.data.remote.dto

import com.google.gson.JsonElement
import com.google.gson.annotations.SerializedName

/** Candidato como o CandidatoService::apresentar devolve. */
data class Candidato(
    val id: String,
    @SerializedName("modulo_id") val moduloId: String?,
    val nome: String?,
    val email: String?,
    val telefone: String?,
    val mensagem: String?,
    val vaga: String?,
    @SerializedName("fase_atual_id") val faseAtualId: String?,
    @SerializedName("criado_em") val criadoEm: String?,
    @SerializedName("atualizado_em") val atualizadoEm: String?,
    @SerializedName("tem_curriculo") val temCurriculo: Boolean?,
    @SerializedName("curriculo_nome") val curriculoNome: String?
)

/** Uma coluna do quadro: { fase, total, candidatos }. */
data class ColunaKanban(
    val fase: String?,
    val total: Int?,
    val candidatos: List<Candidato>?
)

/** Etapa já resolvida, na ordem do quadro (a API manda um objeto { faseId: coluna }). */
data class Etapa(val id: String, val nome: String, val candidatos: List<Candidato>)

data class MoverFaseRequest(@SerializedName("fase_id") val faseId: String)

data class NovaEtapaRequest(val nome: String)

/**
 * O PHP devolve [] (lista) quando o quadro não tem etapas e um objeto quando
 * tem; por isso a resposta chega crua e vira lista aqui, mantendo a ordem.
 */
typealias KanbanBruto = JsonElement
