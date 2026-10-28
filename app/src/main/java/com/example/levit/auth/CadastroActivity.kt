package com.example.levit.auth

import android.content.Intent
import android.os.Bundle
import android.util.Patterns
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import com.example.levit.DashboardActivity
import com.example.levit.R
import com.example.levit.data.repository.AuthRepository
import com.example.levit.data.repository.ResultadoAuth
import com.example.levit.databinding.ActivityCadastroBinding
import kotlinx.coroutines.launch

/** Mesmo formulário e regras de client-frontend/src/pages/Register.jsx. */
class CadastroActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCadastroBinding
    private val authRepository by lazy { AuthRepository(applicationContext) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCadastroBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.textVoltar.setOnClickListener { finish() }

        binding.tvFacaLogin.setOnClickListener {
            startActivity(Intent(this, LoginActivity::class.java))
        }

        // Máscara de CPF/CNPJ enquanto digita, como o mascararCpfCnpj do web.
        binding.CPFouCNPJ.doAfterTextChanged { texto ->
            val atual = texto?.toString().orEmpty()
            val mascarado = ValidacaoCadastro.mascararCpfCnpj(atual)
            if (mascarado != atual) {
                binding.CPFouCNPJ.setText(mascarado)
                binding.CPFouCNPJ.setSelection(mascarado.length)
            }
            binding.layoutCPFouCPNJ.error = null
        }

        binding.etSenha.doAfterTextChanged {
            binding.layoutSenha.error = null
            atualizarForcaSenha(it?.toString().orEmpty())
            if (binding.etConfirmarSenha.text?.isNotEmpty() == true) validarSenhasIguais()
        }

        binding.etConfirmarSenha.doAfterTextChanged { validarSenhasIguais() }

        binding.btnCriarConta.setOnClickListener { enviar() }
    }

    private fun enviar() {
        val nome = binding.nomeCompleto.text.toString().trim()
        val email = binding.etEmailCorporativo.text.toString().trim()
        val cpfCnpj = binding.CPFouCNPJ.text.toString().trim()
        val nomeEmpresa = binding.etNomeEmpresa.text.toString().trim()
        val senha = binding.etSenha.text.toString()

        binding.layoutNomeCompleto.error =
            if (nome.isEmpty()) getString(R.string.cadastro_erro_nome) else null
        binding.layoutEmailCorporativo.error =
            if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) getString(R.string.cadastro_erro_email) else null
        binding.layoutCPFouCPNJ.error = when {
            cpfCnpj.count { it in '0'..'9' } !in listOf(11, 14) -> getString(R.string.cadastro_erro_documento_tamanho)
            !ValidacaoCadastro.documentoValido(cpfCnpj) -> getString(R.string.cadastro_erro_documento_invalido)
            else -> null
        }
        binding.layoutSenha.error = when {
            senha.length < 8 -> getString(R.string.cadastro_erro_senha_curta)
            !ValidacaoCadastro.senhaValida(senha) -> getString(R.string.cadastro_erro_senha_regra)
            else -> null
        }
        val confirmacaoOk = validarSenhasIguais(obrigatoria = true)

        val possuiErro = listOf(
            binding.layoutNomeCompleto, binding.layoutEmailCorporativo,
            binding.layoutCPFouCPNJ, binding.layoutSenha
        ).any { it.error != null } || !confirmacaoOk
        if (possuiErro) return

        // Sem nome de empresa, a API recebe o nome da pessoa (mesma regra do web).
        registrar(nome = nome, email = email, senha = senha, cnpjCpf = cpfCnpj, nomeEmpresa = nomeEmpresa)
    }

    private fun registrar(nome: String, email: String, senha: String, cnpjCpf: String, nomeEmpresa: String) {
        binding.btnCriarConta.isEnabled = false
        binding.btnCriarConta.setText(R.string.cadastro_button_carregando)

        lifecycleScope.launch {
            when (val resultado = authRepository.signUp(
                nome = nome,
                email = email,
                senha = senha,
                cnpjCpf = cnpjCpf,
                nomeEmpresa = nomeEmpresa
            )) {
                is ResultadoAuth.Sucesso -> {
                    val intent = Intent(this@CadastroActivity, DashboardActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    startActivity(intent)
                }
                is ResultadoAuth.Erro -> {
                    binding.btnCriarConta.isEnabled = true
                    binding.btnCriarConta.setText(R.string.cadastro_button_title)
                    // Igual ao web: com erros de validação da API (ex.: e-mail
                    // já em uso), mostra todos; senão, a mensagem principal.
                    val detalhes = resultado.camposInvalidos?.values?.flatMap {
                        if (it is List<*>) it.map(Any?::toString) else listOf(it.toString())
                    }?.joinToString(" ")
                    Toast.makeText(
                        this@CadastroActivity,
                        detalhes?.takeIf { it.isNotBlank() } ?: resultado.mensagem,
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    private fun atualizarForcaSenha(senha: String) {
        val forca = ValidacaoCadastro.forca(senha)
        val vazio = ContextCompat.getColor(this, R.color.divider)
        // Mesmas cores do FORCA_COLORS do Register.jsx.
        val cor = ContextCompat.getColor(this, when (forca) {
            1 -> R.color.danger
            2 -> R.color.warning
            3 -> R.color.info
            4 -> R.color.success
            else -> R.color.light_text
        })

        listOf(binding.barra1, binding.barra2, binding.barra3, binding.barra4).forEachIndexed { indice, barra ->
            barra.setBackgroundColor(if (senha.isNotEmpty() && forca > indice) cor else vazio)
        }

        binding.textoForcaSenha.setTextColor(cor)
        binding.textoForcaSenha.text = if (senha.isEmpty()) "" else getString(when (forca) {
            1 -> R.string.forca_fraca
            2 -> R.string.forca_razoavel
            3 -> R.string.forca_boa
            4 -> R.string.forca_forte
            else -> R.string.forca_muito_fraca
        })
    }

    private fun validarSenhasIguais(obrigatoria: Boolean = false): Boolean {
        val senha = binding.etSenha.text.toString()
        val confirmar = binding.etConfirmarSenha.text.toString()

        val erro = when {
            confirmar.isEmpty() && obrigatoria -> getString(R.string.redefinir_confirmacao_obrigatoria)
            confirmar.isEmpty() -> null
            senha != confirmar -> getString(R.string.redefinir_senhas_diferentes)
            else -> null
        }
        binding.layoutConfirmarSenha.error = erro
        return erro == null && confirmar.isNotEmpty()
    }
}
