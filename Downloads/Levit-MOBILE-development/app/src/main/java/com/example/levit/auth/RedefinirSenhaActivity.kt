package com.example.levit.auth

import android.content.Intent
import android.os.Bundle
import android.os.Build
import android.content.pm.PackageManager
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.widget.doAfterTextChanged
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.example.levit.R
import com.example.levit.data.repository.AuthRepository
import com.example.levit.data.local.ServerConfig
import com.example.levit.data.repository.ResultadoRedefinicaoSenha
import com.example.levit.databinding.ActivityRecuperarSenha2Binding
import kotlinx.coroutines.launch

class RedefinirSenhaActivity : AppCompatActivity() {
    private lateinit var binding: ActivityRecuperarSenha2Binding
    private val repository by lazy { AuthRepository(applicationContext) }
    private var token: String? = null
    private var enviando = false
    private val pedirRedeLocal = registerForActivityResult(ActivityResultContracts.RequestPermission()) { autorizado ->
        if (autorizado) redefinirSenha()
        else mostrarErro(getString(R.string.redefinir_permissao_rede), false)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRecuperarSenha2Binding.inflate(layoutInflater)
        setContentView(binding.root)
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val barras = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(barras.left, barras.top, barras.right, barras.bottom)
            insets
        }

        binding.textVoltar.setOnClickListener {
            if (!enviando) {
                if (isTaskRoot) irParaLogin() else finish()
            }
        }
        binding.etSenha.doAfterTextChanged {
            binding.layoutSenha.error = null
            atualizarForca()
            if (binding.layoutConfirmarSenha.error != null) validarConfirmacao()
        }
        binding.etConfirmarSenha.doAfterTextChanged {
            binding.layoutConfirmarSenha.error = null
        }
        binding.btnEnviar.setOnClickListener { redefinirSenha() }
        binding.btnNovoLink.setOnClickListener {
            startActivity(Intent(this, RecuperarSenhaActivity::class.java))
            finish()
        }
        receberLink(intent)
        atualizarForca()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Não troca o token enquanto a requisição atual ainda está em andamento.
        if (enviando) return
        setIntent(intent)
        binding.etSenha.text?.clear()
        binding.etConfirmarSenha.text?.clear()
        binding.layoutSenha.error = null
        binding.layoutConfirmarSenha.error = null
        receberLink(intent)
    }

    private fun receberLink(intent: Intent) {
        token = if (intent.data != null) {
            ValidacaoRedefinicaoSenha.tokenDoLink(intent.data.toString())
        } else {
            intent.getStringExtra(EXTRA_TOKEN)?.takeIf(ValidacaoRedefinicaoSenha::tokenValido)
        }
        binding.tvMensagem.visibility = View.GONE
        binding.btnNovoLink.visibility = View.GONE
        if (token == null) mostrarErro(getString(R.string.redefinir_link_invalido), true)
        atualizarEstado()
    }

    private fun validarConfirmacao(): Boolean {
        val senha = binding.etSenha.text?.toString().orEmpty()
        val confirmacao = binding.etConfirmarSenha.text?.toString().orEmpty()
        val erro = when {
            confirmacao.isEmpty() -> getString(R.string.redefinir_confirmacao_obrigatoria)
            senha != confirmacao -> getString(R.string.redefinir_senhas_diferentes)
            else -> null
        }
        binding.layoutConfirmarSenha.error = erro
        return erro == null
    }

    private fun redefinirSenha() {
        if (enviando) return
        val tokenAtual = token ?: return
        val senha = binding.etSenha.text?.toString().orEmpty()
        val senhaValida = ValidacaoRedefinicaoSenha.senhaValida(senha)
        binding.layoutSenha.error = if (senhaValida) null else getString(R.string.redefinir_requisitos)
        val confirmacaoValida = validarConfirmacao()
        if (!senhaValida || !confirmacaoValida) {
            if (!senhaValida) binding.etSenha.requestFocus() else binding.etConfirmarSenha.requestFocus()
            return
        }

        if (Build.VERSION.SDK_INT >= 37 && servidorNaRedeLocal() &&
            ContextCompat.checkSelfPermission(this, PERMISSAO_REDE_LOCAL) != PackageManager.PERMISSION_GRANTED
        ) {
            pedirRedeLocal.launch(PERMISSAO_REDE_LOCAL)
            return
        }

        enviando = true
        binding.tvMensagem.visibility = View.GONE
        atualizarEstado()
        lifecycleScope.launch {
            try {
                when (val resultado = repository.redefinirSenha(tokenAtual, senha)) {
                    ResultadoRedefinicaoSenha.Sucesso -> {
                        token = null
                        binding.etSenha.text?.clear()
                        binding.etConfirmarSenha.text?.clear()
                        Toast.makeText(this@RedefinirSenhaActivity, R.string.redefinir_sucesso, Toast.LENGTH_LONG).show()
                        irParaLogin()
                    }
                    is ResultadoRedefinicaoSenha.Erro -> {
                        if (resultado.linkInvalido) token = null
                        mostrarErro(resultado.mensagem, resultado.linkInvalido)
                    }
                }
            } finally {
                enviando = false
                atualizarEstado()
            }
        }
    }

    private fun mostrarErro(mensagem: String, pedirNovoLink: Boolean) {
        binding.tvMensagem.text = mensagem
        binding.tvMensagem.visibility = View.VISIBLE
        binding.btnNovoLink.visibility = if (pedirNovoLink) View.VISIBLE else View.GONE
    }

    private fun atualizarEstado() {
        val habilitado = !enviando && token != null
        binding.btnEnviar.isEnabled = habilitado
        binding.etSenha.isEnabled = habilitado
        binding.etConfirmarSenha.isEnabled = habilitado
        binding.btnEnviar.setText(if (enviando) R.string.redefinir_enviando else R.string.redefinir_botao)
        binding.progressRedefinicao.visibility = if (enviando) View.VISIBLE else View.GONE
        binding.textVoltar.isEnabled = !enviando
    }

    private fun atualizarForca() {
        val senha = binding.etSenha.text?.toString().orEmpty()
        val forca = ValidacaoRedefinicaoSenha.forca(senha)
        val cor = ContextCompat.getColor(this, when (forca) {
            1 -> R.color.vermelho_fraco
            2 -> R.color.laranja_razoavel
            3, 4 -> R.color.redefinir_verde
            else -> R.color.cinza_inativo
        })
        listOf(binding.barra1, binding.barra2, binding.barra3, binding.barra4).forEachIndexed { indice, barra ->
            barra.setBackgroundColor(if (indice < forca) cor else ContextCompat.getColor(this, R.color.cinza_inativo))
        }
        binding.textoForcaSenha.setTextColor(cor)
        binding.textoForcaSenha.text = when (forca) {
            1 -> getString(R.string.redefinir_forca_fraca)
            2 -> getString(R.string.redefinir_requisitos)
            3 -> getString(R.string.redefinir_forca_boa)
            4 -> getString(R.string.redefinir_forca_forte)
            else -> ""
        }
    }

    private fun irParaLogin() {
        startActivity(Intent(this, LoginActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        })
        finish()
    }

    private fun servidorNaRedeLocal(): Boolean {
        val host = runCatching { java.net.URI(ServerConfig.obterBaseUrl(this)).host }.getOrNull().orEmpty()
        val partes = host.split('.')
        val segundo = partes.getOrNull(1)?.toIntOrNull() ?: -1
        return host == "localhost" || host.startsWith("10.") || host.startsWith("192.168.") ||
            host.startsWith("127.") || (partes.firstOrNull() == "172" && segundo in 16..31)
    }

    companion object {
        const val EXTRA_TOKEN = "com.example.levit.extra.RESET_PASSWORD_TOKEN"
        private const val PERMISSAO_REDE_LOCAL = "android.permission.ACCESS_LOCAL_NETWORK"
    }
}
