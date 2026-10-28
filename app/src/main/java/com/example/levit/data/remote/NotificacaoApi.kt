package com.example.levit.data.remote

import com.example.levit.data.remote.dto.ApiEnvelope
import com.google.gson.JsonObject
import retrofit2.Response
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

/** Mesmos endpoints de notificacaoService.js do web. */
interface NotificacaoApi {

    /** { notificacoes: [...], proximo } — paginado por cursor. */
    @GET("notificacoes")
    suspend fun listar(
        @Query("limite") limite: Int = 20,
        @Query("antes_de") antesDe: String? = null,
        @Query("nao_lidas") naoLidas: Int? = null
    ): Response<ApiEnvelope<JsonObject>>

    /** { nao_lidas } */
    @GET("notificacoes/contagem")
    suspend fun contagem(): Response<ApiEnvelope<JsonObject>>

    @PUT("notificacoes/{id}/lida")
    suspend fun marcarLida(@Path("id") id: String): Response<ApiEnvelope<Any>>

    @PUT("notificacoes/lidas")
    suspend fun marcarTodasLidas(): Response<ApiEnvelope<Any>>

    @DELETE("notificacoes/{id}")
    suspend fun excluir(@Path("id") id: String): Response<ApiEnvelope<Any>>
}
