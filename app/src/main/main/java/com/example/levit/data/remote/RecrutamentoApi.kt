package com.example.levit.data.remote

import com.example.levit.data.remote.dto.ApiEnvelope
import com.example.levit.data.remote.dto.Candidato
import com.example.levit.data.remote.dto.KanbanBruto
import com.example.levit.data.remote.dto.MoverFaseRequest
import com.example.levit.data.remote.dto.NovaEtapaRequest
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Part
import retrofit2.http.Path

/** Mesmos endpoints de client-frontend/src/services/recrutamentoService.js. */
interface RecrutamentoApi {

    @GET("recrutamento/kanban")
    suspend fun kanbanGlobal(): Response<ApiEnvelope<KanbanBruto>>

    @GET("modulos/{vaga}/kanban")
    suspend fun kanbanDaVaga(@Path("vaga") vagaId: String): Response<ApiEnvelope<KanbanBruto>>

    @PUT("modulos/{vaga}/candidatos/{candidato}/fase")
    suspend fun moverFase(
        @Path("vaga") vagaId: String,
        @Path("candidato") candidatoId: String,
        @Body body: MoverFaseRequest
    ): Response<ApiEnvelope<Candidato>>

    @POST("modulos/{vaga}/fases")
    suspend fun criarEtapa(@Path("vaga") vagaId: String, @Body body: NovaEtapaRequest): Response<ApiEnvelope<Any>>

    @Multipart
    @POST("modulos/{vaga}/candidatos")
    suspend fun adicionarCandidato(
        @Path("vaga") vagaId: String,
        @Part("nome") nome: RequestBody,
        @Part("email") email: RequestBody,
        @Part("telefone") telefone: RequestBody,
        @Part("mensagem") mensagem: RequestBody,
        @Part curriculo: MultipartBody.Part?
    ): Response<ApiEnvelope<Candidato>>

    @GET("modulos/{vaga}/candidatos/{candidato}/curriculo")
    suspend fun curriculo(@Path("vaga") vagaId: String, @Path("candidato") candidatoId: String): Response<ResponseBody>
}
