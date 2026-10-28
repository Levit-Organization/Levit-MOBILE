package com.example.levit.data.remote

import com.example.levit.data.local.SessionManager
import okhttp3.Interceptor
import okhttp3.Response

/**
 * Equivalente aos interceptors do axios em client-frontend/src/services/api.js:
 * lê o token salvo e adiciona "Authorization: Bearer <token>" antes de
 * cada chamada.
 *
 * 401 numa chamada que levou token = sessão expirada, revogada (logout em
 * outro lugar) ou senha trocada depois do login (o AuthFilter do backend
 * recusa os três). A sessão local é apagada aqui, num lugar só, e as telas
 * protegidas mandam para o login ao perceber. Sem token (ex.: senha errada
 * no login) é só um erro comum e a tela trata.
 */
class AuthInterceptor(private val sessionManager: SessionManager) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val requestOriginal = chain.request()
        val token = sessionManager.obterToken()

        val request = if (token != null) {
            requestOriginal.newBuilder()
                .header("Authorization", "Bearer $token")
                .build()
        } else {
            requestOriginal
        }

        val response = chain.proceed(request)
        // Só apaga se ainda for o mesmo token: um login feito enquanto esta
        // chamada voltava não pode ser desfeito por ela.
        if (response.code == 401 && token != null && sessionManager.obterToken() == token) {
            sessionManager.limparSessao()
        }
        return response
    }
}
