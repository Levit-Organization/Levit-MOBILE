package com.example.levit.data.remote

import com.example.levit.data.local.SessionManager
import okhttp3.Interceptor
import okhttp3.Response

/**
 * Equivalente ao interceptor do axios em client-frontend/src/services/api.js:
 * lê o token salvo e adiciona "Authorization: Bearer <token>" antes de
 * cada chamada.
 */
class AuthInterceptor(private val sessionManager: SessionManager) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val requestOriginal = chain.request()
        val token = sessionManager.obterToken()

        val request = if (token != null) {
            requestOriginal.newBuilder()
                .addHeader("Authorization", "Bearer $token")
                .build()
        } else {
            requestOriginal
        }

        return chain.proceed(request)
    }
}