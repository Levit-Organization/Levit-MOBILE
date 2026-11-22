package com.example.levit.data.repository

import android.content.Context
import com.example.levit.R
import com.example.levit.data.local.SessionManager
import com.example.levit.data.remote.dto.ApiEnvelope
import com.google.gson.Gson
import retrofit2.Response
import kotlin.coroutines.cancellation.CancellationException

sealed class ResultadoApi<out T> {
    data class Sucesso<out T>(val dados: T) : ResultadoApi<T>()
    data class Erro(
        val mensagem: String,
        val sessaoExpirada: Boolean = false,
        val semPermissao: Boolean = false,
        val tipo: TipoErro? = null
    ) : ResultadoApi<Nothing>()
}

/** Erro pronto para a tela: texto vindo de strings.xml e flags de sessão/permissão. */
fun Context.erroApi(tipo: TipoErro) = ResultadoApi.Erro(
    mensagem = getString(tipo.mensagem),
    sessaoExpirada = tipo == TipoErro.SESSAO_EXPIRADA,
    semPermissao = tipo == TipoErro.SEM_PERMISSAO,
    tipo = tipo
)

class ChamadorApi(context: Context) {

    private val contexto = context.applicationContext
    private val session = SessionManager(contexto)
    private val gson = Gson()

    /** Para chamadas que devolvem dados no corpo (listas, objetos). */
    suspend fun <T> chamar(
        requisicao: suspend () -> Response<ApiEnvelope<T>>
    ): ResultadoApi<T> = executar(requisicao) { dados ->
        if (dados != null) ResultadoApi.Sucesso(dados)
        else ResultadoApi.Erro(contexto.getString(R.string.erro_resposta_inesperada))
    }

    /** Para chamadas que respondem "data: null" em caso de sucesso (DELETE). */
    suspend fun chamarSemCorpo(
        requisicao: suspend () -> Response<ApiEnvelope<Any?>>
    ): ResultadoApi<Unit> = executar(requisicao) { ResultadoApi.Sucesso(Unit) }

    private suspend fun <T, R> executar(
        requisicao: suspend () -> Response<ApiEnvelope<T>>,
        aoSucesso: (T?) -> ResultadoApi<R>
    ): ResultadoApi<R> = try {
        val resposta = requisicao()
        if (resposta.isSuccessful) aoSucesso(resposta.body()?.data) else erroHttp(resposta)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        contexto.erroApi(ClassificadorDeErro.deExcecao(e))
    }

    private fun erroHttp(resposta: Response<*>): ResultadoApi.Erro {
        val tipo = ClassificadorDeErro.deStatus(resposta.code())
        if (tipo == TipoErro.SESSAO_EXPIRADA) session.limparSessao()
        return if (tipo != null) contexto.erroApi(tipo) else ResultadoApi.Erro(mensagemDeErro(resposta))
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
            else -> contexto.getString(R.string.erro_http_generico, resposta.code())
        }
    }
}
