package com.example.levit.auth

import java.net.URI
import java.net.URLDecoder

/** Regras iguais às de POST /auth/reset-password, sem dependências de UI. */
object ValidacaoRedefinicaoSenha {
    private val formatoToken = Regex("^[0-9a-f]{64}$")

    fun tokenValido(token: String?): Boolean = token != null && formatoToken.matches(token)

    fun senhaValida(senha: String): Boolean =
        senha.length >= 8 && Regex("[A-Za-z]").containsMatchIn(senha) &&
            Regex("[0-9]").containsMatchIn(senha) && '\n' !in senha && '\r' !in senha

    fun forca(senha: String): Int {
        if (senha.isEmpty()) return 0
        if (!senhaValida(senha)) return if (senha.length < 4) 1 else 2
        val extra = Regex("[A-Z]").containsMatchIn(senha) && Regex("[a-z]").containsMatchIn(senha) ||
            Regex("[^A-Za-z0-9]").containsMatchIn(senha)
        return if (extra) 4 else 3
    }

    /** Aceita apenas o destino registrado no Manifest, com um único token. */
    fun tokenDoLink(link: String?): String? = runCatching {
        val uri = URI(link ?: return null)
        if (!uri.scheme.equals("levit", ignoreCase = true) ||
            !uri.host.equals("reset-password", ignoreCase = true) ||
            uri.rawUserInfo != null || uri.port != -1 ||
            uri.path !in listOf("", "/") || uri.fragment != null
        ) return null

        val tokens = uri.rawQuery.orEmpty().split('&').map { parte ->
            val par = parte.split('=', limit = 2)
            URLDecoder.decode(par[0], "UTF-8") to
                URLDecoder.decode(par.getOrElse(1) { "" }, "UTF-8")
        }.filter { it.first == "token" }.map { it.second }
        tokens.singleOrNull()?.takeIf(::tokenValido)
    }.getOrNull()
}
