package com.example.levit.data.remote

import android.content.Context
import com.example.levit.data.local.ServerConfig
import com.example.levit.data.local.SessionManager
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object ApiClient {

    @Volatile
    private var retrofit: Retrofit? = null

    @Volatile
    private var baseUrlAtual: String? = null

    fun authApi(context: Context): AuthApi = retrofit(context).create(AuthApi::class.java)

    /**
     * Reconstrói o Retrofit sempre que o endereço salvo em ServerConfig for
     * diferente do que foi usado da última vez, para uma troca de servidor
     * feita em tempo real (tela de configuração) valer imediatamente, sem
     * precisar fechar e abrir o app de novo.
     */
    private fun retrofit(context: Context): Retrofit {
        val baseUrlDesejada = ServerConfig.obterBaseUrl(context)

        if (retrofit == null || baseUrlAtual != baseUrlDesejada) {
            synchronized(this) {
                if (retrofit == null || baseUrlAtual != baseUrlDesejada) {
                    retrofit = build(context, baseUrlDesejada)
                    baseUrlAtual = baseUrlDesejada
                }
            }
        }
        return retrofit!!
    }

    private fun build(context: Context, baseUrl: String): Retrofit {
        val sessionManager = SessionManager(context.applicationContext)

        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }

        val client = OkHttpClient.Builder()
            .addInterceptor(AuthInterceptor(sessionManager))
            .addInterceptor(logging)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()

        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }
}
