package com.example.levit.data.remote

import com.example.levit.data.remote.dto.ApiEnvelope
import com.example.levit.data.remote.dto.AutomacaoResumo
import com.example.levit.data.remote.dto.PrazosAbertos
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query

/** O que o Início do web busca além de tabelas, equipe e quadro (prazoService, automacaoService). */
interface InicioApi {

    @GET("itens/prazos")
    suspend fun prazos(@Query("escopo") escopo: String = "todos"): Response<ApiEnvelope<PrazosAbertos>>

    @GET("automacoes")
    suspend fun automacoes(): Response<ApiEnvelope<List<AutomacaoResumo>>>
}
