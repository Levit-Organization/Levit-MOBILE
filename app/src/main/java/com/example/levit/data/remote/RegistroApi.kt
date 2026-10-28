package com.example.levit.data.remote

import com.example.levit.data.remote.dto.ApiEnvelope
import com.example.levit.data.remote.dto.GrupoLigado
import com.example.levit.data.remote.dto.ImportarRequest
import com.example.levit.data.remote.dto.ImportarResposta
import com.example.levit.data.remote.dto.Registro
import com.example.levit.data.remote.dto.RegistroRequest
import com.example.levit.data.remote.dto.TituloItem
import okhttp3.MultipartBody
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query

/** Mesmos endpoints de registroService.js e arquivoService.js do web. */
interface RegistroApi {

    /** busca: texto livre; filtros: JSON da pesquisa avançada (utils/filtros.js). */
    @GET("modulos/{id}/registros")
    suspend fun listar(
        @Path("id") moduloId: String,
        @Query("busca") busca: String?,
        @Query("filtros") filtros: String?
    ): Response<ApiEnvelope<List<Registro>>>

    @GET("modulos/{id}/titulos")
    suspend fun titulos(@Path("id") moduloId: String): Response<ApiEnvelope<List<TituloItem>>>

    @GET("modulos/{id}/registros/{registro}/ligados")
    suspend fun ligados(@Path("id") moduloId: String, @Path("registro") registroId: String): Response<ApiEnvelope<List<GrupoLigado>>>

    @POST("modulos/{id}/registros")
    suspend fun criar(@Path("id") moduloId: String, @Body body: RegistroRequest): Response<ApiEnvelope<Any>>

    @PUT("modulos/{id}/registros/{registro}")
    suspend fun atualizar(@Path("id") moduloId: String, @Path("registro") registroId: String, @Body body: RegistroRequest): Response<ApiEnvelope<Any>>

    @DELETE("modulos/{id}/registros/{registro}")
    suspend fun excluir(@Path("id") moduloId: String, @Path("registro") registroId: String): Response<ApiEnvelope<Any>>

    @POST("modulos/{id}/registros/importar")
    suspend fun importar(@Path("id") moduloId: String, @Body body: ImportarRequest): Response<ApiEnvelope<ImportarResposta>>

    @Multipart
    @POST("modulos/{id}/arquivos")
    suspend fun enviarArquivo(@Path("id") moduloId: String, @Part arquivo: MultipartBody.Part): Response<ApiEnvelope<Any>>

    @GET("modulos/{id}/arquivos/{registro}")
    suspend fun baixarArquivo(@Path("id") moduloId: String, @Path("registro") registroId: String): Response<ResponseBody>

    @DELETE("modulos/{id}/arquivos/{registro}")
    suspend fun excluirArquivo(@Path("id") moduloId: String, @Path("registro") registroId: String): Response<ApiEnvelope<Any>>
}
