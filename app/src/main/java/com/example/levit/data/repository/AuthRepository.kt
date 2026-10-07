package com.example.levit.data.repository

import android.content.Context
import com.example.levit.data.local.SessionManager
import com.example.levit.data.remote.ApiClient
import com.example.levit.data.remote.dto.ApiEnvelope
import com.example.levit.data.remote.dto.AuthData
import com.example.levit.data.remote.dto.LoginRequest
import com.example.levit.data.remote.dto.RegistrarRequest
import com.google.gson.Gson
import retrofit2.Response
import java.io.IOException

sealed class ResultadoAuth {
    data class Sucesso(val dados: AuthData) : ResultadoAuth()
    data class Erro(val mensagem: String, val camposInvalidos: Map<String, Any>? = null) : ResultadoAuth()}

class AuthRepository(context: Context) {

    private val api = ApiClient.authApi(context)
    private val session = SessionManager(context.applicationContext)
    private val gson = Gson()

    /** true se há um token salvo e ele ainda não expirou (checagem local, sem chamar a API). */
    fun estaAutenticado(): Boolean = session.sessaoValida()

    fun usuarioLogado() = session.obterUsuario()


    suspend fun signIn(email: String, senha: String): ResultadoAuth =
        chamar { api.login(LoginRequest(email = email, senha = senha)) }

    suspend fun signUp(
        nome: String,
        email: String,
        senha: String,
        cnpjCpf: String,
        nomeEmpresa: String
    ): ResultadoAuth = chamar {
        api.registrar(
            RegistrarRequest(
                nome = nome,
                email = email,
                senha = senha,
                cnpj_cpf = cnpjCpf,
                // Mesma regra do LEVIT Web: se não informar empresa, usa o próprio nome.
                nome_empresa = nomeEmpresa.ifBlank { nome }
            )
        )
    }

    /**
     * Chama /auth/logout para revogar o token no servidor e, em qualquer
     * cenário (sucesso, erro de rede ou token já expirado), limpa a sessão
     * local, igual ao bloco try/finally do AuthContext.jsx do LEVIT Web.
     */
    suspend fun signOut() {
        try {
            api.logout()
        } catch (e: Exception) {
            // Ignorado de propósito: mesmo que o servidor não responda,
            // a sessão precisa sair do dispositivo.
        } finally {
            session.limparSessao()
        }
    }

    /**
     * Envolve as chamadas de login/registrar: sucesso salva a sessão;
     * erro HTTP decodifica {message, errors} do corpo de erro;
     * falha de rede cai numa mensagem genérica.
     */
    private suspend fun chamar(requisicao: suspend () -> Response<ApiEnvelope<AuthData>>): ResultadoAuth {
        return try {
            val resposta = requisicao()

            if (resposta.isSuccessful) {
                val dados = resposta.body()?.data
                if (dados != null) {
                    session.salvarSessao(dados.token, dados.usuario, dados.empresa)
                    ResultadoAuth.Sucesso(dados)
                } else {
                    ResultadoAuth.Erro("Resposta inesperada do servidor.")
                }
            } else {
                val envelope = resposta.errorBody()?.string()?.let {
                    runCatching { gson.fromJson(it, ApiEnvelope::class.java) }.getOrNull()
                }
                ResultadoAuth.Erro(
                    mensagem = envelope?.message ?: "Erro ao comunicar com o servidor (${resposta.code()}).",
                    camposInvalidos = envelope?.errors
                )
            }
        } catch (e: IOException) {
            ResultadoAuth.Erro("Não foi possível conectar ao servidor. Verifique sua internet.")
        } catch (e: Exception) {
            ResultadoAuth.Erro("Ocorreu um erro inesperado. Tente novamente.")
        }
    }
}