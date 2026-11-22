package com.example.levit.data.repository

import android.content.Context
import com.example.levit.R
import com.example.levit.data.local.SessionManager
import com.example.levit.data.remote.ApiClient
import com.example.levit.data.remote.dto.ApiEnvelope
import com.example.levit.data.remote.dto.CriarModuloRequest
import com.example.levit.data.remote.dto.Modulo
import com.google.gson.Gson
import retrofit2.Response

sealed class ResultadoModulo<out T> {
    data class Sucesso<out T>(val dados: T) : ResultadoModulo<T>()
    data class Erro(
        val mensagem: String,
        val sessaoExpirada: Boolean = false
    ) : ResultadoModulo<Nothing>()
}

class ModuloRepository(context: Context) {

    private val api = ApiClient.moduloApi(context)
    private val contexto = context.applicationContext
    private val session = SessionManager(contexto)
    private val gson = Gson()

    suspend fun listar(): ResultadoModulo<List<Modulo>> = chamar { api.listar() }

    suspend fun criar(request: CriarModuloRequest): ResultadoModulo<Modulo> =
        chamar { api.criar(request) }

    private suspend fun <T> chamar(
        requisicao: suspend () -> Response<ApiEnvelope<T>>
    ): ResultadoModulo<T> {
        return try {
            val resposta = requisicao()

            if (resposta.isSuccessful) {
                val dados = resposta.body()?.data
                if (dados != null) {
                    ResultadoModulo.Sucesso(dados)
                } else {
                    ResultadoModulo.Erro(contexto.getString(R.string.erro_resposta_inesperada))
                }
            } else if (resposta.code() == 401) {
                session.limparSessao()
                ResultadoModulo.Erro(contexto.getString(R.string.erro_sessao_expirada), sessaoExpirada = true)
            } else {
                val envelope = resposta.errorBody()?.string()?.let {
                    runCatching { gson.fromJson(it, ApiEnvelope::class.java) }.getOrNull()
                }
                ResultadoModulo.Erro(mensagemDeErro(envelope, resposta.code()))
            }
        } catch (e: Exception) {
            ResultadoModulo.Erro(contexto.getString(ClassificadorDeErro.deExcecao(e).mensagem))
        }
    }

    private fun mensagemDeErro(envelope: ApiEnvelope<*>?, codigo: Int): String {
        val detalhes = envelope?.errors?.values
            ?.joinToString(" | ") { it.toString().trim('[', ']') }
        val mensagem = envelope?.message

        return when {
            !detalhes.isNullOrBlank() -> detalhes
            !mensagem.isNullOrBlank() -> mensagem
            else -> "Erro ao comunicar com o servidor ($codigo)."
        }
    }
}