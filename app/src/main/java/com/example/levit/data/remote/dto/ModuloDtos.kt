package com.example.levit.data.remote.dto

import com.google.gson.annotations.SerializedName

data class CampoRequest(
    val nome: String,
    val tipo: String,
    val opcoes: List<String>? = null
)

data class CriarModuloRequest(
    val nome: String,
    val icone: String,
    val tipo: String = "dados",
    val campos: List<CampoRequest>
)

data class Modulo(
    val id: String,
    val nome: String,
    val icone: String? = null,
    val tipo: String? = null,
    @SerializedName("total_registros") val totalRegistros: Int = 0
)