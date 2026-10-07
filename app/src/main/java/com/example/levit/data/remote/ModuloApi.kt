package com.example.levit.data.remote

import com.example.levit.data.remote.dto.ApiEnvelope
import com.example.levit.data.remote.dto.CriarModuloRequest
import com.example.levit.data.remote.dto.Modulo
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

interface ModuloApi {

    @GET("modulos")
    suspend fun listar(): Response<ApiEnvelope<List<Modulo>>>

    @POST("modulos")
    suspend fun criar(@Body body: CriarModuloRequest): Response<ApiEnvelope<Modulo>>
}