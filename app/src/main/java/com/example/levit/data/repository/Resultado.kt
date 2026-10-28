package com.example.levit.data.repository

import com.example.levit.data.remote.dto.ApiEnvelope
import com.google.gson.Gson
import kotlinx.coroutines.CancellationException
import okhttp3.ResponseBody
import retrofit2.Response
import java.io.IOException

/**
 * Resultado de uma chamada à API, para as telas novas (Recrutamento, Equipe,
 * Relatórios). O código HTTP vai junto porque a tela decide por ele: 401 manda
 * para o login, 403 mostra "sem acesso", como no web.
 */
sealed class Resultado<out T> {
    data class Sucesso<out T>(val dados: T) : Resultado<T>()
    data class Erro(val mensagem: String, val codigo: Int = 0) : Resultado<Nothing>() {
        val sessaoExpirada get() = codigo == 401
        val semAcesso get() = codigo == 403
    }
}

private val gson = Gson()

/**
 * Mesma regra do mensagemDeErro de client-frontend/src/lib/queryClient.js:
 * "message" da API seguido dos erros de validação; sem nada, o texto padrão.
 */
fun mensagemDeErro(corpoDeErro: String?, padrao: String): String {
    val envelope = corpoDeErro?.let { runCatching { gson.fromJson(it, ApiEnvelope::class.java) }.getOrNull() }
    val erros = envelope?.errors?.values?.joinToString(" ") {
        if (it is List<*>) it.joinToString(" ") else it.toString()
    }.orEmpty()
    val mensagem = envelope?.message
    return when {
        !mensagem.isNullOrBlank() && erros.isNotBlank() -> "$mensagem $erros"
        !mensagem.isNullOrBlank() -> mensagem
        else -> padrao
    }
}

private suspend fun <R, T> tratar(
    padrao: String,
    requisicao: suspend () -> Response<R>,
    sucesso: (Response<R>) -> Resultado<T>
): Resultado<T> = try {
    val resposta = requisicao()
    if (resposta.isSuccessful) sucesso(resposta)
    else Resultado.Erro(mensagemDeErro(resposta.errorBody()?.string(), padrao), resposta.code())
} catch (e: CancellationException) {
    throw e
} catch (e: IOException) {
    Resultado.Erro("Não foi possível conectar ao servidor. Verifique sua internet.")
} catch (e: Exception) {
    Resultado.Erro(padrao)
}

/** Chamada que devolve dados em { status, data }. */
suspend fun <T> chamarApi(padrao: String, requisicao: suspend () -> Response<ApiEnvelope<T>>): Resultado<T> =
    tratar(padrao, requisicao) { resposta ->
        resposta.body()?.data?.let { Resultado.Sucesso(it) } ?: Resultado.Erro(padrao, resposta.code())
    }

/** Chamada em que só importa ter dado certo (excluir, reenviar...). */
suspend fun <T> executarApi(padrao: String, requisicao: suspend () -> Response<ApiEnvelope<T>>): Resultado<Unit> =
    tratar(padrao, requisicao) { Resultado.Sucesso(Unit) }

/** Download (PDF, currículo): devolve os bytes. */
suspend fun baixarApi(padrao: String, requisicao: suspend () -> Response<ResponseBody>): Resultado<ByteArray> =
    tratar(padrao, requisicao) { resposta ->
        resposta.body()?.bytes()?.let { Resultado.Sucesso(it) } ?: Resultado.Erro(padrao, resposta.code())
    }
