package com.example.levit.data.remote.dto

import com.google.gson.annotations.SerializedName

/** GET /equipe. A API não manda o cargo_id do colaborador, só o nome do cargo. */
data class Membro(
    val id: String,
    val nome: String?,
    val email: String?,
    @SerializedName("criado_em") val criadoEm: String?,
    @SerializedName("cargo_nome") val cargoNome: String?
)

/** GET /equipe/pessoas: só id e nome, sem exigir permissão de ver a equipe. */
data class Pessoa(val id: String, val nome: String?)

data class Convite(
    val id: String,
    val email: String?,
    @SerializedName("cargo_id") val cargoId: String?,
    @SerializedName("cargo_nome") val cargoNome: String?,
    @SerializedName("convidado_por_nome") val convidadoPorNome: String?,
    @SerializedName("expira_em") val expiraEm: String?,
    val expirado: Boolean?
)

/**
 * GET /cargos. acesso_total chega como booleano ou como "t"/"f" (Postgres
 * pelo CodeIgniter), por isso fica como Any e é lido por [acessoTotal].
 */
data class Cargo(
    val id: String,
    val nome: String?,
    @SerializedName("acesso_total") val acessoTotalBruto: Any?,
    val permissoes: List<String>?,
    val usuarios: Int?
) {
    val acessoTotal: Boolean
        get() = acessoTotalBruto == true || acessoTotalBruto == "t" || acessoTotalBruto == "true" || acessoTotalBruto == "1"
}

/** GET /cargos/{id}/modulos: nível de acesso do cargo em cada tabela. */
data class NivelModulo(
    @SerializedName("modulo_id") val moduloId: String,
    val nivel: String?
)

data class ConviteRequest(
    val email: String,
    @SerializedName("cargo_id") val cargoId: String,
    val mensagem: String? = null
)

data class AtualizarMembroRequest(
    val nome: String?,
    val email: String?,
    @SerializedName("cargo_id") val cargoId: String?
)

data class CargoRequest(val nome: String, val permissoes: List<String>)

data class NivelRequest(val nivel: String)
