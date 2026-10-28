package com.example.levit.data.remote

import com.example.levit.data.remote.dto.Agrupamento
import com.example.levit.data.remote.dto.ApiEnvelope
import com.example.levit.data.remote.dto.Envio
import com.example.levit.data.remote.dto.Indicadores
import com.example.levit.data.remote.dto.Relatorio
import com.example.levit.data.remote.dto.RelatorioRequest
import com.example.levit.data.remote.dto.TabelaRelatorio
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.QueryMap

/** Mesmos endpoints de relatorioService.js do web. */
interface RelatorioApi {

    @GET("relatorios")
    suspend fun listar(): Response<ApiEnvelope<List<Relatorio>>>

    @POST("relatorios")
    suspend fun criar(@Body body: RelatorioRequest): Response<ApiEnvelope<Any>>

    @PUT("relatorios/{id}")
    suspend fun atualizar(@Path("id") id: String, @Body body: RelatorioRequest): Response<ApiEnvelope<Any>>

    @DELETE("relatorios/{id}")
    suspend fun excluir(@Path("id") id: String): Response<ApiEnvelope<Any>>

    @GET("relatorios/{id}/envios")
    suspend fun envios(@Path("id") id: String): Response<ApiEnvelope<List<Envio>>>

    @POST("relatorios/{id}/enviar-agora")
    suspend fun enviarAgora(@Path("id") id: String): Response<ApiEnvelope<Any>>

    @GET("relatorios/indicadores")
    suspend fun indicadores(@Query("inicio") inicio: String, @Query("fim") fim: String): Response<ApiEnvelope<Indicadores>>

    @GET("relatorios/tabelas")
    suspend fun tabelas(): Response<ApiEnvelope<List<TabelaRelatorio>>>

    @GET("relatorios/agrupar")
    suspend fun agrupar(@QueryMap params: Map<String, String>): Response<ApiEnvelope<Agrupamento>>

    /** PDF do período. secoes: "resumo,recrutamento,..." (vazio = padrão do backend). */
    @GET("relatorios/pdf")
    suspend fun pdf(@QueryMap params: Map<String, String>): Response<ResponseBody>

    @GET("relatorios/agrupar/pdf")
    suspend fun pdfMontado(@QueryMap params: Map<String, String>): Response<ResponseBody>
}
