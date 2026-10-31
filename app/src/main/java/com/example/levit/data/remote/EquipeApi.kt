package com.example.levit.data.remote

import com.example.levit.data.remote.dto.ApiEnvelope
import com.example.levit.data.remote.dto.AtualizarMembroRequest
import com.example.levit.data.remote.dto.ConvidarRequest
import com.example.levit.data.remote.dto.ConviteCriado
import com.example.levit.data.remote.dto.Membro
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path

interface EquipeApi {

    @GET("equipe")
    suspend fun listarMembros(): Response<ApiEnvelope<List<Membro>>>

    @POST("equipe/convidar")
    suspend fun convidar(@Body body: ConvidarRequest): Response<ApiEnvelope<ConviteCriado>>

    @PUT("equipe/{id}")
    suspend fun atualizarMembro(
        @Path("id") id: String,
        @Body body: AtualizarMembroRequest
    ): Response<ApiEnvelope<Membro>>

    @DELETE("equipe/{id}")
    suspend fun removerMembro(@Path("id") id: String): Response<ApiEnvelope<Any?>>
}