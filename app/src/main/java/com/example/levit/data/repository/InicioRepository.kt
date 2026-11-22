package com.example.levit.data.repository

import android.content.Context
import com.example.levit.data.remote.ApiClient
import com.example.levit.data.remote.dto.AutomacaoResumo
import com.example.levit.data.remote.dto.PrazosAbertos

class InicioRepository(context: Context) {

    private val api = ApiClient.inicioApi(context)
    private val chamador = ChamadorApi(context)

    suspend fun prazos(): ResultadoApi<PrazosAbertos> = chamador.chamar { api.prazos() }

    suspend fun automacoes(): ResultadoApi<List<AutomacaoResumo>> = chamador.chamar { api.automacoes() }
}
