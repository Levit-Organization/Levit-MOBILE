package com.example.levit.data.repository

import android.content.Context
import com.example.levit.R
import com.example.levit.data.local.SessionManager
import com.example.levit.data.remote.ApiClient
import com.example.levit.data.remote.dto.ApiEnvelope
import com.example.levit.data.remote.dto.AuthData
import com.example.levit.data.remote.dto.LoginRequest
import com.example.levit.data.remote.dto.RedefinirSenhaRequest
import com.example.levit.data.remote.dto.RegistrarRequest
import com.google.gson.Gson
import kotlinx.coroutines.CancellationException
import retrofit2.Response

sealed class ResultadoAuth {
    data class Sucesso(val dados: AuthData) : ResultadoAuth()
    data class Erro(
        val mensagem: String,
        val camposInvalidos: Map<String, Any>? = null,
        val tipo: TipoErro? = null
    ) : ResultadoAuth()
}

sealed class ResultadoRedefinicaoSenha {
    object Sucesso : ResultadoRedefinicaoSenha()
    data class Erro(
        val mensagem: String,
        val linkInvalido: Boolean = false,
        val tipo: TipoErro? = null
    ) : ResultadoRedefinicaoSenha()
}
class AuthRepository(context: Context) {

    private val api = ApiClient.authApi(context)
    private val contexto = context.applicationContext
    private val session = SessionManager(contexto)
    private val gson = Gson()

    /** true se há um token salvo e ele ainda não expirou (checagem local, sem chamar a API). */
    fun estaAutenticado(): Boolean = session.sessaoValida()

    fun usuarioLogado() = session.obterUsuario()

    fun empresaLogada() = session.obterEmpresa()

    suspend fun redefinirSenha(token: String, senha: String): ResultadoRedefinicaoSenha {
        return try {
            val resposta = api.redefinirSenha(RedefinirSenhaRequest(token, senha))
            if (resposta.isSuccessful && resposta.body()?.status == "success") {
                session.limparSessao()
                ResultadoRedefinicaoSenha.Sucesso
            } else {
                val envelope = resposta.errorBody()?.string()?.let {
                    runCatching { gson.fromJson(it, ApiEnvelope::class.java) }.getOrNull()
                }
                val detalhes = envelope?.errors?.values?.joinToString(" ") {
                    if (it is List<*>) it.joinToString(" ") else it.toString()
                }
                ResultadoRedefinicaoSenha.Erro(
                    mensagem = detalhes?.takeIf { it.isNotBlank() }
                        ?: envelope?.message
                        ?: contexto.getString(R.string.erro_redefinir_senha, resposta.code()),
                    linkInvalido = resposta.code() == 422 && envelope?.errors?.containsKey("senha") != true
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val tipo = ClassificadorDeErro.deExcecao(e)
            ResultadoRedefinicaoSenha.Erro(contexto.getString(tipo.mensagem), tipo = tipo)
        }
    }


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
                    ResultadoAuth.Erro(contexto.getString(R.string.erro_resposta_inesperada))
                }
            } else {
                val envelope = resposta.errorBody()?.string()?.let {
                    runCatching { gson.fromJson(it, ApiEnvelope::class.java) }.getOrNull()
                }
                ResultadoAuth.Erro(
                    mensagem = envelope?.message ?: contexto.getString(R.string.erro_http_generico, resposta.code()),
                    camposInvalidos = envelope?.errors
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val tipo = ClassificadorDeErro.deExcecao(e)
            ResultadoAuth.Erro(contexto.getString(tipo.mensagem), tipo = tipo)
        }
    }
}