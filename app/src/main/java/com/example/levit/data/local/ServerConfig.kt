package com.example.levit.data.local

import android.content.Context
import com.example.levit.BuildConfig
import java.net.URI

/**
 * Endereço da API. Vem do build (BuildConfig.API_BASE_URL); em debug pode ser
 * trocado pressionando o logo da tela inicial, para testar em celular físico.
 * Em release a troca é ignorada: ninguém com o aparelho na mão consegue
 * apontar o app para outro servidor.
 */
object ServerConfig {
    private const val PADRAO = BuildConfig.API_BASE_URL
    private const val ARQUIVO = "levit_server_config"
    private const val CHAVE = "base_url"

    val podeTrocar: Boolean = BuildConfig.DEBUG

    fun obterBaseUrl(context: Context): String {
        check(PADRAO.isNotBlank()) {
            "API_BASE_URL vazio. Gere o release com -Plevit.apiUrl=https://<host>/api/v1/"
        }
        if (!podeTrocar) return PADRAO
        return context.getSharedPreferences(ARQUIVO, Context.MODE_PRIVATE).getString(CHAVE, PADRAO) ?: PADRAO
    }

    fun salvarBaseUrl(context: Context, endereco: String) {
        check(podeTrocar) { "Troca de servidor só existe em debug." }
        val baseUrl = normalizarBaseUrl(endereco)
        context.getSharedPreferences(ARQUIVO, Context.MODE_PRIVATE).edit().putString(CHAVE, baseUrl).apply()
    }

    fun normalizarBaseUrl(endereco: String): String {
        val texto = endereco.trim()
        require(texto.isNotEmpty()) { "Informe o endereço do servidor." }
        val uri = URI(if (texto.contains("://")) texto else "http://$texto")
        require(uri.scheme in listOf("http", "https") && uri.host != null &&
            uri.rawUserInfo == null && uri.query == null && uri.fragment == null &&
            (uri.port == -1 || uri.port in 1..65535) &&
            uri.path.orEmpty().trimEnd('/') in listOf("", "/api/v1")) {
            "Informe um host:porta ou uma URL HTTP/HTTPS com /api/v1/."
        }
        return "${uri.scheme}://${uri.rawAuthority}/api/v1/"
    }
}
