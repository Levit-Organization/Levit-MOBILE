package com.example.levit.data.remote.dto

import com.google.gson.annotations.SerializedName

data class CampoModulo(
    val id: String,
    val nome: String,
    val tipo: String,                       // texto, numero, data, selecao
    val opcoes: List<String>? = null,       // só para selecao
    val ordem: Int? = null
)

data class ModuloDetalhe(
    val id: String,
    val nome: String,
    val icone: String? = null,
    val tipo: String? = null,               // dados, arquivo, financeiro, recrutamento
    val campos: List<CampoModulo>? = null
) {
    val camposOrdenados: List<CampoModulo>
        get() = (campos ?: emptyList()).sortedBy { it.ordem ?: Int.MAX_VALUE }
}

data class Registro(
    val id: String,
    // Objeto {idDoCampo: valor}. Vem como [] quando vazio, por isso Any?.
    val dados: Any? = null,
    @SerializedName("criado_em") val criadoEm: String? = null
) {
    val valores: Map<String, Any?>
        get() = (dados as? Map<*, *>)
            ?.entries?.associate { it.key.toString() to it.value }
            ?: emptyMap()
}

// Corpo de POST e PUT. Só os campos preenchidos entram.
data class RegistroRequest(val dados: Map<String, Any?>)

/** Mostra o valor como texto: 120.0 vira "120", nulo vira vazio. */
fun formatarValorRegistro(valor: Any?): String = when (valor) {
    null -> ""
    is Double -> if (valor % 1.0 == 0.0) valor.toLong().toString() else valor.toString()
    else -> valor.toString()
}