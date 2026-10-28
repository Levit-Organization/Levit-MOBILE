package com.example.levit.auth

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.levit.data.remote.ApiClient
import com.example.levit.databinding.ActivityRecuperarSenhaBinding
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import android.util.Patterns

/**
 * Solicita um link que abre RedefinirSenhaActivity. O backend identifica
 * plataforma=mobile e mantém os links web para os demais clientes.
 */
class RecuperarSenhaActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRecuperarSenhaBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRecuperarSenhaBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.textVoltar.setOnClickListener { finish() }

        binding.btnEnviar.setOnClickListener { solicitarRecuperacao() }
    }

    private fun solicitarRecuperacao() {
        val email = binding.emailCorporativo.text.toString().trim()

        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            Toast.makeText(this, "Informe um e-mail válido.", Toast.LENGTH_SHORT).show()
            return
        }

        binding.btnEnviar.isEnabled = false

        lifecycleScope.launch {
            try {
                val api = ApiClient.authApi(applicationContext)
                val resposta = api.forgotPassword(mapOf("email" to email, "plataforma" to "mobile"))

                val mensagem = if (resposta.isSuccessful) {
                    "Se o e-mail existir em nossa base, um link de recuperação foi enviado."
                } else {
                    "Não foi possível solicitar o link agora (${resposta.code()}). Tente novamente mais tarde."
                }
                Toast.makeText(this@RecuperarSenhaActivity, mensagem, Toast.LENGTH_LONG).show()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Toast.makeText(
                    this@RecuperarSenhaActivity,
                    "Não foi possível conectar ao servidor.",
                    Toast.LENGTH_LONG
                ).show()
            } finally {
                binding.btnEnviar.isEnabled = true
            }
        }
    }
}
