package com.example.levit.data.remote

import com.example.levit.data.remote.dto.ApiEnvelope
import com.example.levit.data.remote.dto.AtualizarTabelaRequest
import com.example.levit.data.remote.dto.Modulo
import com.example.levit.data.remote.dto.ReordenarRequest
import com.example.levit.data.remote.dto.TabelaDetalhe
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path

/** Mesmos endpoints de client-frontend/src/services/moduloService.js. */
interface ModuloApi {

    @GET("modulos")
    suspend fun listar(): Response<ApiEnvelope<List<Modulo>>>

    /** Corpo livre (Map): a coluna de ligação manda campos que as outras não mandam. */
    @POST("modulos")
    suspend fun criar(@Body body: Map<String, @JvmSuppressWildcards Any?>): Response<ApiEnvelope<Modulo>>

    @GET("modulos/{id}")
    suspend fun detalhes(@Path("id") id: String): Response<ApiEnvelope<TabelaDetalhe>>

    @PUT("modulos/{id}")
    suspend fun atualizar(@Path("id") id: String, @Body body: AtualizarTabelaRequest): Response<ApiEnvelope<Any>>

    /** Atualização parcial (o web manda só { icone } ao trocar o ícone em Configurações). */
    @PUT("modulos/{id}")
    suspend fun atualizarCampos(@Path("id") id: String, @Body body: Map<String, @JvmSuppressWildcards Any?>): Response<ApiEnvelope<Any>>

    @DELETE("modulos/{id}")
    suspend fun excluir(@Path("id") id: String): Response<ApiEnvelope<Any>>

    @POST("modulos/{id}/campos")
    suspend fun adicionarColuna(@Path("id") id: String, @Body body: Map<String, @JvmSuppressWildcards Any?>): Response<ApiEnvelope<Any>>

    @PUT("modulos/{id}/campos/{campo}")
    suspend fun atualizarColuna(
        @Path("id") id: String,
        @Path("campo") campoId: String,
        @Body body: Map<String, @JvmSuppressWildcards Any?>
    ): Response<ApiEnvelope<Any>>

    @DELETE("modulos/{id}/campos/{campo}")
    suspend fun excluirColuna(@Path("id") id: String, @Path("campo") campoId: String): Response<ApiEnvelope<Any>>

    @PUT("modulos/{id}/campos/reordenar")
    suspend fun reordenarColunas(@Path("id") id: String, @Body body: ReordenarRequest): Response<ApiEnvelope<Any>>
}
