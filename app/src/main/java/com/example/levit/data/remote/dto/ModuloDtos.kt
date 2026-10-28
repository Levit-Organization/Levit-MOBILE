package com.example.levit.data.remote.dto

import com.google.gson.annotations.SerializedName

data class Modulo(
    val id: String,
    val nome: String,
    val icone: String? = null,
    val tipo: String? = null,
    @SerializedName("total_registros") val totalRegistros: Int = 0
)