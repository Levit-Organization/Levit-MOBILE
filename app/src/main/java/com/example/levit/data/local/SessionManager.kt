package com.example.levit.data.local

import android.content.Context
import android.util.Base64
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.example.levit.data.remote.dto.Empresa
import com.example.levit.data.remote.dto.Usuario
import com.google.gson.Gson
import org.json.JSONObject

/**
 * Guarda token JWT + dados do usuário/empresa em um SharedPreferences
 * criptografado (AES-256), em vez de AsyncStorage/SharedPreferences puro.
 *
 * O backend não expõe refresh-token nem endpoint /me. O JWT expira em
 * 8 horas (JwtService::ttlSeconds) e não é renovado automaticamente.
 * Por isso a validade da sessão aqui é checada localmente, decodificando
 * o "exp" do próprio token, sem round-trip com o servidor.
 */
class SessionManager(context: Context) {

    private val gson = Gson()

    private val prefs by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        EncryptedSharedPreferences.create(
            context,
            "levit_secure_prefs",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    fun salvarSessao(token: String, usuario: Usuario, empresa: Empresa) {
        prefs.edit()
            .putString(CHAVE_TOKEN, token)
            .putString(CHAVE_USUARIO, gson.toJson(usuario))
            .putString(CHAVE_EMPRESA, gson.toJson(empresa))
            .apply()
    }

    fun obterToken(): String? = prefs.getString(CHAVE_TOKEN, null)

    fun obterUsuario(): Usuario? =
        prefs.getString(CHAVE_USUARIO, null)?.let { gson.fromJson(it, Usuario::class.java) }

    fun limparSessao() {
        prefs.edit().clear().apply()
    }

    /**
     * true se existe um token salvo E o "exp" dele ainda não passou.
     * Não valida a assinatura (isso é papel do backend), serve só para
     * decidir, no cliente, se vale a pena mandar o usuário direto pro
     * Dashboard ou de volta pro Login.
     */
    fun sessaoValida(): Boolean {
        val token = obterToken() ?: return false
        val exp = decodificarExpiracao(token) ?: return false
        val agoraEmSegundos = System.currentTimeMillis() / 1000
        return exp > agoraEmSegundos
    }

    private fun decodificarExpiracao(token: String): Long? {
        return try {
            val partes = token.split(".")
            if (partes.size != 3) return null
            val payloadJson = String(Base64.decode(partes[1], Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP))
            JSONObject(payloadJson).getLong("exp")
        } catch (e: Exception) {
            null
        }
    }

    companion object {
        private const val CHAVE_TOKEN = "levit_token"
        private const val CHAVE_USUARIO = "levit_usuario"
        private const val CHAVE_EMPRESA = "levit_empresa"
    }
}