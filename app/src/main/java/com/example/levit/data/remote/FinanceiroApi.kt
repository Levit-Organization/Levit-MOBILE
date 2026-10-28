package com.example.levit.data.remote

import com.example.levit.data.remote.dto.ApiEnvelope
import com.example.levit.data.remote.dto.Categoria
import com.example.levit.data.remote.dto.Lancamentos
import com.example.levit.data.remote.dto.NovoLancamento
import com.example.levit.data.remote.dto.NovaCategoria
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

/** Mesmos endpoints de financeiroService.js do web. */
interface FinanceiroApi {

    @GET("financeiro/categorias")
    suspend fun categorias(): Response<ApiEnvelope<List<Categoria>>>

    @POST("financeiro/categorias")
    suspend fun criarCategoria(@Body body: NovaCategoria): Response<ApiEnvelope<Any>>

    @GET("modulos/{id}/lancamentos")
    suspend fun lancamentos(@Path("id") moduloId: String): Response<ApiEnvelope<Lancamentos>>

    @POST("modulos/{id}/lancamentos")
    suspend fun criarLancamento(@Path("id") moduloId: String, @Body body: NovoLancamento): Response<ApiEnvelope<Any>>
}
