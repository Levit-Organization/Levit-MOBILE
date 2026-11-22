package com.example.levit.data.repository

import android.content.Context
import com.example.levit.R
import com.example.levit.data.local.SessionManager
import kotlinx.coroutines.CancellationException
import okhttp3.ResponseBody
import retrofit2.Response

/** Download (PDF, currículo, arquivo de item): devolve os bytes. Mesmas regras de 401/403 do ChamadorApi. */
suspend fun baixarApi(
    context: Context,
    requisicao: suspend () -> Response<ResponseBody>
): ResultadoApi<ByteArray> = try {
    val resposta = requisicao()
    if (resposta.isSuccessful) {
        resposta.body()?.bytes()?.let { ResultadoApi.Sucesso(it) }
            ?: ResultadoApi.Erro(context.getString(R.string.erro_resposta_inesperada))
    } else {
        erroDeDownload(context, resposta.code())
    }
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    context.erroApi(ClassificadorDeErro.deExcecao(e))
}

private fun erroDeDownload(context: Context, codigo: Int): ResultadoApi.Erro {
    val tipo = ClassificadorDeErro.deStatus(codigo)
    if (tipo == TipoErro.SESSAO_EXPIRADA) SessionManager(context.applicationContext).limparSessao()
    return if (tipo != null) context.erroApi(tipo)
    else ResultadoApi.Erro(context.getString(R.string.erro_baixar_arquivo))
}
