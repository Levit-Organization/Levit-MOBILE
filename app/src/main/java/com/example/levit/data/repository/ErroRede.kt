package com.example.levit.data.repository

import androidx.annotation.StringRes
import com.example.levit.R
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

/** Categoria de falha com a mensagem e o ícone (ligatura Material Icons) que a representam. */
enum class TipoErro(@StringRes val mensagem: Int, @StringRes val icone: Int) {
    SEM_INTERNET(R.string.erro_sem_internet, R.string.icone_wifi_off),
    SERVIDOR_INDISPONIVEL(R.string.erro_servidor_indisponivel, R.string.icone_cloud_off),
    TEMPO_ESGOTADO(R.string.erro_tempo_esgotado, R.string.icone_schedule),
    CONEXAO_SEGURA(R.string.erro_conexao_segura, R.string.icone_https),
    SESSAO_EXPIRADA(R.string.erro_sessao_expirada, R.string.icone_lock),
    SEM_PERMISSAO(R.string.erro_sem_permissao, R.string.icone_block),
    NAO_ENCONTRADO(R.string.erro_nao_encontrado, R.string.icone_busca_vazia),
    MUITAS_REQUISICOES(R.string.erro_muitas_requisicoes, R.string.icone_espera),
    ERRO_SERVIDOR(R.string.erro_servidor, R.string.icone_problema),
    INESPERADO(R.string.erro_inesperado, R.string.icone_erro)
}

object ClassificadorDeErro {

    /** Falhas de rede: a ordem importa, pois as classes são subtipos de IOException. */
    fun deExcecao(e: Throwable): TipoErro = when (e) {
        is UnknownHostException -> TipoErro.SEM_INTERNET
        is SocketTimeoutException -> TipoErro.TEMPO_ESGOTADO
        is ConnectException -> TipoErro.SERVIDOR_INDISPONIVEL
        is SSLException -> TipoErro.CONEXAO_SEGURA
        is IOException -> TipoErro.SEM_INTERNET
        else -> TipoErro.INESPERADO
    }

    /** Status HTTP que merecem texto próprio; null deixa valer a mensagem enviada pela API (ex.: 422). */
    fun deStatus(codigo: Int): TipoErro? = when (codigo) {
        401 -> TipoErro.SESSAO_EXPIRADA
        403 -> TipoErro.SEM_PERMISSAO
        404 -> TipoErro.NAO_ENCONTRADO
        408, 504 -> TipoErro.TEMPO_ESGOTADO
        429 -> TipoErro.MUITAS_REQUISICOES
        in 500..599 -> TipoErro.ERRO_SERVIDOR
        else -> null
    }
}
