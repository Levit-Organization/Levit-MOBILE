package com.example.levit.data.repository

import android.content.Context
import com.example.levit.data.local.SessionManager
import com.example.levit.data.remote.dto.ApiEnvelope
import com.google.gson.Gson
import retrofit2.Response
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException

sealed class ResultadoApi<out T> {
    data class Sucesso<out T>(val dados: T) : ResultadoApi<T>()
    data class Erro(
        val mensagem: String,
        val sessaoExpirada: Boolean = false,
        val semPermissao: Boolean = false
    ) : ResultadoApi<Nothing>()
}

class ChamadorApi(context: Context) {

    private val session = SessionManager(context.applicationContext)
    private val gson = Gson()

    /** Para chamadas que devolvem dados no corpo (listas, objetos). */
    suspend fun <T> chamar(
        requisicao: suspend () -> Response<ApiEnvelope<T>>
    ): ResultadoApi<T> = executar(requisicao) { dados ->
        if (dados != null) ResultadoApi.Sucesso(dados)
        else ResultadoApi.Erro("Resposta inesperada do servidor.")
    }

    /** Para chamadas que respondem "data: null" em caso de sucesso (DELETE). */
    suspend fun chamarSemCorpo(
        requisicao: suspend () -> Response<ApiEnvelope<Any?>>
    ): ResultadoApi<Unit> = executar(requisicao) { ResultadoApi.Sucesso(Unit) }

    private suspend fun <T, R> executar(
        requisicao: suspend () -> Response<ApiEnvelope<T>>,
        aoSucesso: (T?) -> ResultadoApi<R>
    ): ResultadoApi<R> {
        return try {
            val resposta = requisicao()
            when {
                resposta.isSuccessful -> aoSucesso(resposta.body()?.data)
                resposta.code() == 401 -> {
                    session.limparSessao()
                    ResultadoApi.Erro("Sessão expirada. Entre novamente.", sessaoExpirada = true)
                }
                resposta.code() == 403 -> ResultadoApi.Erro(
                    "Você não tem permissão para fazer isso.",
                    semPermissao = true
                )
                else -> ResultadoApi.Erro(mensagemDeErro(resposta))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            ResultadoApi.Erro("Não foi possível conectar ao servidor. Verifique sua internet.")
        } catch (e: Exception) {
            ResultadoApi.Erro("Ocorreu um erro inesperado. Tente novamente.")
        }
    }

    private fun mensagemDeErro(resposta: Response<*>): String {
        val envelope = resposta.errorBody()?.string()?.let {
            runCatching { gson.fromJson(it, ApiEnvelope::class.java) }.getOrNull()
        }
        val detalhes = envelope?.errors?.values
            ?.joinToString(" | ") { it.toString().trim('[', ']') }
        val mensagem = envelope?.message

        return when {
            !detalhes.isNullOrBlank() -> detalhes
            !mensagem.isNullOrBlank() -> mensagem
            else -> "Erro ao comunicar com o servidor (${resposta.code()})."
        }
    }
}