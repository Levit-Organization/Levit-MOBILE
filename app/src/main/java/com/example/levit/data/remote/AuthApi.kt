package com.example.levit.data.remote

import com.example.levit.data.remote.dto.ApiEnvelope
import com.example.levit.data.remote.dto.AuthData
import com.example.levit.data.remote.dto.LoginRequest
import com.example.levit.data.remote.dto.RegistrarRequest
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

/**
 * Endpoints confirmados no backend (app/Config/Routes.php):
 *   POST /auth/registrar  -> cria empresa + usuário fundador, retorna token
 *   POST /auth/login      -> autentica, retorna token
 *   POST /auth/logout     -> revoga o token atual (exige header Authorization)
 */
interface AuthApi {

    @POST("auth/registrar")
    suspend fun registrar(@Body body: RegistrarRequest): Response<ApiEnvelope<AuthData>>

    @POST("auth/login")
    suspend fun login(@Body body: LoginRequest): Response<ApiEnvelope<AuthData>>

    @POST("auth/logout")
    suspend fun logout(): Response<ApiEnvelope<Unit>>

    @POST("auth/forgot-password")
    suspend fun forgotPassword(@Body body: Map<String, String>): Response<ApiEnvelope<Unit>>

}