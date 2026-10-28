package com.example.levit.data.remote.dto

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.annotations.SerializedName

/** Resumo de uma tabela ligada; acesso = false quando o cargo não vê essa tabela. */
data class TabelaResumo(
    val id: String?,
    val nome: String?,
    val icone: String?,
    val acesso: Boolean?,
    val campos: List<Coluna>? = null
)

/** Coluna de outra tabela que aponta para esta (o outro lado da ligação), para filtrar. */
data class LigacaoRecebida(
    @SerializedName("campo_id") val campoId: String?,
    @SerializedName("campo_nome") val campoNome: String?,
    val modulo: TabelaResumo?
)

/**
 * Coluna de uma tabela (campo_modulo). opcoes chega como lista (coluna de lista)
 * ou { multiplo } (coluna de ligação); às vezes ainda como texto JSON.
 */
data class Coluna(
    val id: String?,
    val nome: String?,
    val tipo: String?,
    val opcoes: JsonElement?,
    @SerializedName("modulo_vinculado_id") val moduloVinculadoId: String?,
    @SerializedName("modulo_vinculado") val moduloVinculado: TabelaResumo?
) {
    private val opcoesJson: JsonElement?
        get() = opcoes?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }
            ?.let { runCatching { JsonParser.parseString(it.asString) }.getOrNull() } ?: opcoes

    val listaDeOpcoes: List<String>
        get() = opcoesJson?.takeIf { it.isJsonArray }?.asJsonArray?.mapNotNull { e -> e.takeIf { !it.isJsonNull }?.asString }.orEmpty()

    val multiplo: Boolean
        get() = opcoesJson?.takeIf { it.isJsonObject }?.asJsonObject?.get("multiplo")?.let { it.isJsonPrimitive && it.asBoolean } == true
}

/** GET /modulos/{id}: a tabela com as colunas, na ordem. */
data class TabelaDetalhe(
    val id: String,
    val nome: String?,
    val icone: String?,
    val tipo: String?,
    val campos: List<Coluna>?,
    @SerializedName("ligacoes_recebidas") val ligacoesRecebidas: List<LigacaoRecebida>? = null
)

/** Um item (registro). dados = { idDaColuna: valor }, valor texto, número ou lista de ids. */
data class Registro(
    val id: String,
    val dados: JsonObject?,
    @SerializedName("criado_em") val criadoEm: String?,
    @SerializedName("arquivo_nome") val arquivoNome: String?,
    @SerializedName("arquivo_tamanho") val arquivoTamanho: Double?
)

/** GET /modulos/{id}/titulos: o nome de cada item (a primeira coluna de texto). */
data class TituloItem(val id: String, val titulo: String?)

/** GET .../registros/{id}/ligados: itens de outras tabelas que apontam para este. */
data class GrupoLigado(
    @SerializedName("campo_id") val campoId: String?,
    @SerializedName("campo_nome") val campoNome: String?,
    val modulo: TabelaResumo?,
    val total: Int?,
    val itens: List<TituloItem>?
)

data class AtualizarTabelaRequest(val nome: String, val icone: String, val tipo: String)

data class ReordenarRequest(val ordem: List<String>)

data class RegistroRequest(val dados: Map<String, Any?>)

/** Cada item com a linha da planilha, para o backend dizer qual linha falhou. */
data class ItemImportado(val linha: Int, val dados: Map<String, Any>)

data class ImportarRequest(val itens: List<ItemImportado>)

data class ErroDeLinha(val linha: Int?, val mensagem: String?)

data class ImportarResposta(val criados: Int?, val erros: List<ErroDeLinha>?)
