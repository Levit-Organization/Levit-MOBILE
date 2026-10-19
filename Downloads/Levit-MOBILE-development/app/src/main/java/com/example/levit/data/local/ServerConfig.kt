package com.example.levit.data.local

import android.content.Context
import java.net.URI

/** Endereço de desenvolvimento configurável ao pressionar o logo da tela inicial. */
object ServerConfig {
    private const val PADRAO = "http://10.0.2.2:8080/api/v1/"
    private const val ARQUIVO = "levit_server_config"
    private const val CHAVE = "base_url"

    fun obterBaseUrl(context: Context): String =
        context.getSharedPreferences(ARQUIVO, Context.MODE_PRIVATE).getString(CHAVE, PADRAO) ?: PADRAO

    fun salvarBaseUrl(context: Context, endereco: String) {
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
