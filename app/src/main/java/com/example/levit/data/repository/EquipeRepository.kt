package com.example.levit.data.repository

import android.content.Context
import com.example.levit.data.remote.ApiClient
import com.example.levit.data.remote.dto.AtualizarMembroRequest
import com.example.levit.data.remote.dto.ConvidarRequest
import com.example.levit.data.remote.dto.ConviteCriado
import com.example.levit.data.remote.dto.Membro

class EquipeRepository(context: Context) {

    private val api = ApiClient.equipeApi(context)
    private val chamador = ChamadorApi(context)

    suspend fun listarMembros(): ResultadoApi<List<Membro>> =
        chamador.chamar { api.listarMembros() }

    suspend fun convidar(email: String, cargoId: String): ResultadoApi<ConviteCriado> =
        chamador.chamar { api.convidar(ConvidarRequest(email, cargoId)) }

    suspend fun atualizarMembro(
        id: String,
        nome: String? = null,
        cargoId: String? = null
    ): ResultadoApi<Membro> =
        chamador.chamar { api.atualizarMembro(id, AtualizarMembroRequest(nome, cargoId)) }

    suspend fun removerMembro(id: String): ResultadoApi<Unit> =
        chamador.chamarSemCorpo { api.removerMembro(id) }
}