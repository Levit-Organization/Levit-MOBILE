package com.example.levit.data.remote.dto

import com.google.gson.annotations.SerializedName

data class Categoria(val id: String, val nome: String?)

data class NovaCategoria(val nome: String)

/** Um lançamento; valor e saldo vêm como texto (numeric do Postgres), conciliado como "t"/"f" ou booleano. */
data class Lancamento(
    val id: String,
    val descricao: String?,
    @SerializedName("data_lancamento") val dataLancamento: String?,
    val tipo: String?,
    val valor: String?,
    @SerializedName("categoria_id") val categoriaId: String?,
    @SerializedName("categoria_nome") val categoriaNome: String?,
    @SerializedName("conciliado") val conciliadoBruto: Any?,
    @SerializedName("saldo_acumulado") val saldoAcumulado: String?,
    @SerializedName("criado_em") val criadoEm: String?
) {
    val conciliado get() = conciliadoBruto == true || conciliadoBruto == "t"
    val valorNumero get() = valor?.toDoubleOrNull() ?: 0.0
}

data class Lancamentos(val lancamentos: List<Lancamento>?)

data class NovoLancamento(
    val descricao: String,
    val data: String,
    val tipo: String,
    val valor: Double,
    val conciliado: Boolean,
    @SerializedName("categoria_id") val categoriaId: String?
)
