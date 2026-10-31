package com.example.levit.data.repository

import android.content.Context
import com.example.levit.data.remote.ApiClient
import com.example.levit.data.remote.dto.AtualizarCargoRequest
import com.example.levit.data.remote.dto.Cargo
import com.example.levit.data.remote.dto.CriarCargoRequest
import com.example.levit.data.remote.dto.DefinirNivelRequest
import com.example.levit.data.remote.dto.NivelModulo

class CargoRepository(context: Context) {

    private val api = ApiClient.cargoApi(context)
    private val chamador = ChamadorApi(context)

    suspend fun listarCargos(): ResultadoApi<List<Cargo>> =
        chamador.chamar { api.listarCargos() }

    suspend fun criarCargo(nome: String, permissoes: List<String>): ResultadoApi<Cargo> =
        chamador.chamar { api.criarCargo(CriarCargoRequest(nome, permissoes)) }

    // PUT é substituição completa: sempre passe as permissões atuais do cargo.
    suspend fun atualizarCargo(
        id: String,
        nome: String,
        permissoes: List<String>
    ): ResultadoApi<Cargo> =
        chamador.chamar { api.atualizarCargo(id, AtualizarCargoRequest(nome, permissoes)) }

    suspend fun listarNiveis(cargoId: String): ResultadoApi<List<NivelModulo>> =
        chamador.chamar { api.listarNiveis(cargoId) }

    suspend fun definirNivel(
        cargoId: String,
        moduloId: String,
        nivel: String
    ): ResultadoApi<List<NivelModulo>> =
        chamador.chamar { api.definirNivel(cargoId, moduloId, DefinirNivelRequest(nivel)) }

    suspend fun removerNivel(cargoId: String, moduloId: String): ResultadoApi<Unit> =
        chamador.chamarSemCorpo { api.removerNivel(cargoId, moduloId) }

    /**
     * Aplica as mudanças de acesso por módulo de um cargo.
     * Chave = id do módulo. Valor = novo nível ("visualizar", "editar",
     * "gerenciar") ou null para tirar o acesso.
     * Passe só o que mudou. Para no primeiro erro e devolve ele.
     */
    suspend fun aplicarNiveis(
        cargoId: String,
        alteracoes: Map<String, String?>
    ): ResultadoApi<Unit> {
        for ((moduloId, nivel) in alteracoes) {
            val erro: ResultadoApi.Erro? = if (nivel == null) {
                removerNivel(cargoId, moduloId) as? ResultadoApi.Erro
            } else {
                definirNivel(cargoId, moduloId, nivel) as? ResultadoApi.Erro
            }
            if (erro != null) return erro
        }
        return ResultadoApi.Sucesso(Unit)
    }
}