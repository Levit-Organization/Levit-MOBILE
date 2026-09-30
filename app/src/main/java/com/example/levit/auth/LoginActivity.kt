package com.example.levit.auth

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.levit.DashboardActivity
import com.example.levit.data.repository.AuthRepository
import com.example.levit.data.repository.ResultadoAuth
import com.example.levit.databinding.ActivityLoginBinding
import kotlinx.coroutines.launch

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private val authRepository by lazy { AuthRepository(applicationContext) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.textVoltar.setOnClickListener { finish() }

        binding.tvRedefinir.setOnClickListener {
            startActivity(Intent(this, RecuperarSenhaActivity::class.java))
        }

        binding.tvRegistrar.setOnClickListener {
            startActivity(Intent(this, CadastroActivity::class.java))
        }

        binding.btnEntrar.setOnClickListener { tentarLogin() }
    }

    private fun tentarLogin() {
        val email = binding.emailCorporativo.text.toString().trim()
        val senha = binding.etSenha.text.toString()

        if (email.isEmpty() || senha.isEmpty()) {
            Toast.makeText(this, "Preencha e-mail e senha.", Toast.LENGTH_SHORT).show()
            return
        }

        binding.btnEntrar.isEnabled = false

        lifecycleScope.launch {
            when (val resultado = authRepository.signIn(email, senha)) {
                is ResultadoAuth.Sucesso -> irParaDashboard()
                is ResultadoAuth.Erro -> {
                    binding.btnEntrar.isEnabled = true
                    Toast.makeText(this@LoginActivity, resultado.mensagem, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun irParaDashboard() {
        val intent = Intent(this, DashboardActivity::class.java)
        // Limpa a pilha de telas de auth: depois do login, "voltar" não pode
        // devolver o usuário para o formulário de login.
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
    }
}