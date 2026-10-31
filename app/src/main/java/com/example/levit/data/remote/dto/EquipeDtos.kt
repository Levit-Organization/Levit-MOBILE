package com.example.levit.data.remote.dto

import com.google.gson.annotations.SerializedName

data class Membro(
    val id: String,
    val nome: String,
    val email: String,
    @SerializedName("cargo_nome") val cargoNome: String? = null,
    @SerializedName("cargo_id") val cargoId: String? = null,

    val status: String? = null,

)

data class ConvidarRequest(
    val email: String,
    @SerializedName("cargo_id") val cargoId: String
)

data class ConviteCriado(
    @SerializedName("convite_id") val conviteId: String? = null,
    val token: String? = null
)

// Campos nulos não são enviados no JSON: só vai o que foi preenchido.
data class AtualizarMembroRequest(
    val nome: String? = null,
    @SerializedName("cargo_id") val cargoId: String? = null
)