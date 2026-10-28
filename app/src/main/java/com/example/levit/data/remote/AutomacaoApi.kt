package com.example.levit.data.remote

import com.example.levit.data.remote.dto.ApiEnvelope
import com.google.gson.JsonObject
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Mesmos endpoints de automacaoService.js do web. As automações chegam como
 * JsonObject: gatilhos/condições/ações têm formato novo e antigo, e a
 * configuração vazia vem como [] (PHP) em vez de {}.
 */
interface AutomacaoApi {

    @GET("automacoes")
    suspend fun listarTodas(): Response<ApiEnvelope<List<JsonObject>>>

    @GET("modulos/{modulo}/automacoes")
    suspend fun listar(@Path("modulo") moduloId: String): Response<ApiEnvelope<List<JsonObject>>>

    @POST("modulos/{modulo}/automacoes")
    suspend fun criar(@Path("modulo") moduloId: String, @Body corpo: Map<String, @JvmSuppressWildcards Any?>): Response<ApiEnvelope<Any>>

    @PUT("modulos/{modulo}/automacoes/{id}")
    suspend fun atualizar(
        @Path("modulo") moduloId: String,
        @Path("id") id: String,
        @Body corpo: Map<String, @JvmSuppressWildcards Any?>
    ): Response<ApiEnvelope<Any>>

    /** Página de execuções, mais recentes primeiro. antesDe = cursor "proximo" da página anterior. */
    @GET("modulos/{modulo}/automacoes/{id}/execucoes")
    suspend fun execucoes(
        @Path("modulo") moduloId: String,
        @Path("id") id: String,
        @Query("limite") limite: Int = 20,
        @Query("antes_de") antesDe: String? = null
    ): Response<ApiEnvelope<JsonObject>>

    @POST("modulos/{modulo}/automacoes/simular")
    suspend fun simular(@Path("modulo") moduloId: String, @Body entrada: Map<String, @JvmSuppressWildcards Any?>): Response<ApiEnvelope<JsonObject>>

    @POST("modulos/{modulo}/automacoes/{id}/execucoes/{execucao}/reexecutar")
    suspend fun reexecutar(
        @Path("modulo") moduloId: String,
        @Path("id") id: String,
        @Path("execucao") execucaoId: String
    ): Response<ApiEnvelope<Any>>

    @PUT("modulos/{modulo}/automacoes/{id}/ativo")
    suspend fun alternarAtivo(
        @Path("modulo") moduloId: String,
        @Path("id") id: String,
        @Body corpo: Map<String, @JvmSuppressWildcards Any?>
    ): Response<ApiEnvelope<Any>>

    @DELETE("modulos/{modulo}/automacoes/{id}")
    suspend fun excluir(@Path("modulo") moduloId: String, @Path("id") id: String): Response<ApiEnvelope<Any>>
}
