package com.example.levit.auth

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.levit.data.remote.ApiClient
import com.example.levit.databinding.ActivityRecuperarSenhaBinding
import kotlinx.coroutines.launch

/**
 * ATENÇÃO: o backend ainda não tem as rotas POST /auth/forgot-password e
 * POST /auth/reset-password. Esta tela já está pronta para funcionar assim
 * que o backend implementar; até lá, a chamada abaixo devolve 404.
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

        if (email.isEmpty()) {
            Toast.makeText(this, "Informe seu e-mail.", Toast.LENGTH_SHORT).show()
            return
        }

        binding.btnEnviar.isEnabled = false

        lifecycleScope.launch {
            try {
                val api = ApiClient.authApi(applicationContext)
                val resposta = api.forgotPassword(mapOf("email" to email))

                val mensagem = if (resposta.isSuccessful) {
                    "Se o e-mail existir em nossa base, um link de recuperação foi enviado."
                } else {
                    "Recuperação de senha ainda não disponível no servidor."
                }
                Toast.makeText(this@RecuperarSenhaActivity, mensagem, Toast.LENGTH_LONG).show()
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