package com.example.levit.data.remote

import com.example.levit.data.remote.dto.ApiEnvelope
import com.example.levit.data.remote.dto.AtualizarMembroRequest
import com.example.levit.data.remote.dto.Cargo
import com.example.levit.data.remote.dto.CargoRequest
import com.example.levit.data.remote.dto.Convite
import com.example.levit.data.remote.dto.ConviteRequest
import com.example.levit.data.remote.dto.Membro
import com.example.levit.data.remote.dto.NivelModulo
import com.example.levit.data.remote.dto.NivelRequest
import com.example.levit.data.remote.dto.Pessoa
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path

/** Mesmos endpoints de equipeService.js e cargoService.js do web. */
interface EquipeApi {

    @GET("equipe")
    suspend fun membros(): Response<ApiEnvelope<List<Membro>>>

    @GET("equipe/pessoas")
    suspend fun pessoas(): Response<ApiEnvelope<List<Pessoa>>>

    @POST("equipe/convidar")
    suspend fun convidar(@Body body: ConviteRequest): Response<ApiEnvelope<Any>>

    @PUT("equipe/{id}")
    suspend fun atualizarMembro(@Path("id") id: String, @Body body: AtualizarMembroRequest): Response<ApiEnvelope<Any>>

    @DELETE("equipe/{id}")
    suspend fun removerMembro(@Path("id") id: String): Response<ApiEnvelope<Any>>

    @GET("equipe/convites")
    suspend fun convites(): Response<ApiEnvelope<List<Convite>>>

    @POST("equipe/convites/{id}/reenviar")
    suspend fun reenviarConvite(@Path("id") id: String): Response<ApiEnvelope<Any>>

    @DELETE("equipe/convites/{id}")
    suspend fun cancelarConvite(@Path("id") id: String): Response<ApiEnvelope<Any>>

    @GET("cargos")
    suspend fun cargos(): Response<ApiEnvelope<List<Cargo>>>

    @POST("cargos")
    suspend fun criarCargo(@Body body: CargoRequest): Response<ApiEnvelope<Any>>

    @PUT("cargos/{id}")
    suspend fun atualizarCargo(@Path("id") id: String, @Body body: CargoRequest): Response<ApiEnvelope<Any>>

    @GET("cargos/{id}/modulos")
    suspend fun niveisDoCargo(@Path("id") cargoId: String): Response<ApiEnvelope<List<NivelModulo>>>

    @PUT("cargos/{id}/modulos/{modulo}")
    suspend fun definirNivel(
        @Path("id") cargoId: String,
        @Path("modulo") moduloId: String,
        @Body body: NivelRequest
    ): Response<ApiEnvelope<List<NivelModulo>>>

    @DELETE("cargos/{id}/modulos/{modulo}")
    suspend fun removerNivel(@Path("id") cargoId: String, @Path("modulo") moduloId: String): Response<ApiEnvelope<Any>>
}
