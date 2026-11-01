package com.example.levit.data.repository

import android.content.Context
import com.example.levit.data.remote.ApiClient
import com.example.levit.data.remote.dto.ModuloDetalhe
import com.example.levit.data.remote.dto.Registro
import com.example.levit.data.remote.dto.RegistroRequest

class RegistroRepository(context: Context) {

    private val moduloApi = ApiClient.moduloApi(context)
    private val registroApi = ApiClient.registroApi(context)
    private val chamador = ChamadorApi(context.applicationContext)

    suspend fun detalhesDoModulo(moduloId: String): ResultadoApi<ModuloDetalhe> =
        chamador.chamar { moduloApi.detalhes(moduloId) }

    /** busca vazia ou só com espaços lista tudo. */
    suspend fun listar(moduloId: String, busca: String? = null): ResultadoApi<List<Registro>> =
        chamador.chamar { registroApi.listar(moduloId, busca?.trim()?.ifEmpty { null }) }

    suspend fun criar(moduloId: String, dados: Map<String, Any?>): ResultadoApi<Registro> =
        chamador.chamar { registroApi.criar(moduloId, RegistroRequest(dados)) }

    /** O PUT substitui o registro inteiro: envie todos os valores atuais, não só o que mudou. */
    suspend fun atualizar(
        moduloId: String,
        registroId: String,
        dados: Map<String, Any?>
    ): ResultadoApi<Registro> =
        chamador.chamar { registroApi.atualizar(moduloId, registroId, RegistroRequest(dados)) }

    suspend fun excluir(moduloId: String, registroId: String): ResultadoApi<Unit> =
        chamador.chamarSemCorpo { registroApi.excluir(moduloId, registroId) }
}