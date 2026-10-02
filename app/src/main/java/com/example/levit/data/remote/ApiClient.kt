package com.example.levit.data.remote

import android.content.Context
import com.example.levit.data.local.SessionManager
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object ApiClient {

    /**
     * 10.0.2.2 é o alias do host da máquina dentro do emulador Android.
     * Em dispositivo físico, troque pelo IP da máquina na rede local ou
     * pela URL pública do backend, e mova isto para BuildConfig por
     * ambiente (debug/release) em vez de deixar fixo no código.
     */
    private const val BASE_URL = "http://10.0.2.2:8080/api/v1/"

    @Volatile
    private var retrofit: Retrofit? = null

    fun authApi(context: Context): AuthApi = retrofit(context).create(AuthApi::class.java)

    private fun retrofit(context: Context): Retrofit =
        retrofit ?: synchronized(this) {
            retrofit ?: build(context).also { retrofit = it }
        }

    private fun build(context: Context): Retrofit {
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
            .baseUrl(BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }
}