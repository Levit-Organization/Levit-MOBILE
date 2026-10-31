package com.example.levit.data.remote

import com.example.levit.data.remote.dto.ApiEnvelope
import com.example.levit.data.remote.dto.AtualizarCargoRequest
import com.example.levit.data.remote.dto.Cargo
import com.example.levit.data.remote.dto.CriarCargoRequest
import com.example.levit.data.remote.dto.DefinirNivelRequest
import com.example.levit.data.remote.dto.NivelModulo
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path

interface CargoApi {

    @GET("cargos")
    suspend fun listarCargos(): Response<ApiEnvelope<List<Cargo>>>

    @POST("cargos")
    suspend fun criarCargo(@Body body: CriarCargoRequest): Response<ApiEnvelope<Cargo>>

    @PUT("cargos/{id}")
    suspend fun atualizarCargo(
        @Path("id") id: String,
        @Body body: AtualizarCargoRequest
    ): Response<ApiEnvelope<Cargo>>

    @GET("cargos/{id}/modulos")
    suspend fun listarNiveis(@Path("id") cargoId: String): Response<ApiEnvelope<List<NivelModulo>>>

    @PUT("cargos/{cargoId}/modulos/{moduloId}")
    suspend fun definirNivel(
        @Path("cargoId") cargoId: String,
        @Path("moduloId") moduloId: String,
        @Body body: DefinirNivelRequest
    ): Response<ApiEnvelope<List<NivelModulo>>>

    @DELETE("cargos/{cargoId}/modulos/{moduloId}")
    suspend fun removerNivel(
        @Path("cargoId") cargoId: String,
        @Path("moduloId") moduloId: String
    ): Response<ApiEnvelope<Any?>>
}