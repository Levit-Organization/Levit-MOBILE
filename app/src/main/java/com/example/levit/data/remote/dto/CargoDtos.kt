package com.example.levit.data.remote.dto

import com.google.gson.annotations.SerializedName

data class Cargo(
    val id: String,
    val nome: String,
    val permissoes: List<String>? = null,
    // O Postgres devolve booleano como texto ("t"/"f"), por isso Any?.
    // Use sempre a propriedade acessoTotal abaixo.
    @SerializedName("acesso_total") val acessoTotalBruto: Any? = null
) {
    val permissoesOuVazio: List<String>
        get() = permissoes ?: emptyList()

    val acessoTotal: Boolean
        get() = acessoTotalBruto == true || acessoTotalBruto == "t" || acessoTotalBruto == "true"
}

data class CriarCargoRequest(
    val nome: String,
    val permissoes: List<String>
)

// PUT é substituição completa: sempre envie nome E a lista de permissões atual.
data class AtualizarCargoRequest(
    val nome: String,
    val permissoes: List<String>
)

data class NivelModulo(
    @SerializedName("modulo_id") val moduloId: String,
    val nivel: String,
    @SerializedName("modulo_nome") val moduloNome: String? = null
)

data class DefinirNivelRequest(
    val nivel: String
)