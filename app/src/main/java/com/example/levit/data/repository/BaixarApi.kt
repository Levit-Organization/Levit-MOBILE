package com.example.levit.data.repository

import android.content.Context
import com.example.levit.data.local.SessionManager
import kotlinx.coroutines.CancellationException
import okhttp3.ResponseBody
import retrofit2.Response
import java.io.IOException

/** Download (PDF, currículo, arquivo de item): devolve os bytes. Mesmas regras de 401/403 do ChamadorApi. */
suspend fun baixarApi(
    context: Context,
    requisicao: suspend () -> Response<ResponseBody>
): ResultadoApi<ByteArray> = try {
    val resposta = requisicao()
    when {
        resposta.isSuccessful ->
            resposta.body()?.bytes()?.let { ResultadoApi.Sucesso(it) }
                ?: ResultadoApi.Erro("Resposta inesperada do servidor.")
        resposta.code() == 401 -> {
            SessionManager(context.applicationContext).limparSessao()
            ResultadoApi.Erro("Sessão expirada. Entre novamente.", sessaoExpirada = true)
        }
        resposta.code() == 403 -> ResultadoApi.Erro("Você não tem permissão para fazer isso.", semPermissao = true)
        else -> ResultadoApi.Erro("Não foi possível baixar o arquivo.")
    }
} catch (e: CancellationException) {
    throw e
} catch (e: IOException) {
    ResultadoApi.Erro("Não foi possível conectar ao servidor. Verifique sua internet.")
} catch (e: Exception) {
    ResultadoApi.Erro("Não foi possível baixar o arquivo.")
}
