package com.example.levit.data.remote

import com.example.levit.data.remote.dto.ApiEnvelope
import com.example.levit.data.remote.dto.Registro
import com.example.levit.data.remote.dto.RegistroRequest
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

interface RegistroApi {

    @GET("modulos/{moduloId}/registros")
    suspend fun listar(
        @Path("moduloId") moduloId: String,
        @Query("busca") busca: String? = null
    ): Response<ApiEnvelope<List<Registro>>>

    @POST("modulos/{moduloId}/registros")
    suspend fun criar(
        @Path("moduloId") moduloId: String,
        @Body body: RegistroRequest
    ): Response<ApiEnvelope<Registro>>

    @PUT("modulos/{moduloId}/registros/{registroId}")
    suspend fun atualizar(
        @Path("moduloId") moduloId: String,
        @Path("registroId") registroId: String,
        @Body body: RegistroRequest
    ): Response<ApiEnvelope<Registro>>

    @DELETE("modulos/{moduloId}/registros/{registroId}")
    suspend fun excluir(
        @Path("moduloId") moduloId: String,
        @Path("registroId") registroId: String
    ): Response<ApiEnvelope<Any?>>
}