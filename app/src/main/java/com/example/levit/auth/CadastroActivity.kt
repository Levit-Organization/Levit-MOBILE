package com.example.levit.auth

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.levit.R
import com.example.levit.databinding.ActivityCadastroBinding

class CadastroActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCadastroBinding

    // Variável para guardar o nível da senha em tempo real
    private var forcaSenhaAtual = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCadastroBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.textVoltar.setOnClickListener { finish() }

        binding.tvFacaLogin.setOnClickListener {
            startActivity(Intent(this, LoginActivity::class.java))
        }

        // Monitora a digitação da senha
        binding.etSenha.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                verificarForcaSenha(s.toString())
                if (binding.etConfirmarSenha.text.toString().isNotEmpty()) {
                    validarSenhasIguais()
                }
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        // Monitora a confirmação da senha
        binding.etConfirmarSenha.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                validarSenhasIguais()
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        // Regras de bloqueio ao clicar em Criar Conta
        binding.btnCriarConta.setOnClickListener {
            // 1. Extrair os textos digitados
            val nome = binding.nomeCompleto.text.toString().trim()
            val email = binding.etEmailCorporativo.text.toString().trim()
            val cpfCnpj = binding.CPFouCNPJ.text.toString().trim()

            // Limpa os erros visuais anteriores
            binding.layoutNomeCompleto.error = null
            binding.layoutEmailCorporativo.error = null
            binding.layoutCPFouCPNJ.error = null

            var possuiErro = false

            // 2. Validações de campos vazios
            if (nome.isEmpty()) {
                binding.layoutNomeCompleto.error = "Preencha o nome completo"
                possuiErro = true
            }

            if (email.isEmpty()) {
                binding.layoutEmailCorporativo.error = "Preencha o e-mail"
                possuiErro = true
            }

            if (cpfCnpj.isEmpty()) {
                binding.layoutCPFouCPNJ.error = "Preencha o CPF ou CNPJ"
                possuiErro = true
            }

            // 3. Validações da Senha
            if (forcaSenhaAtual < 3) {
                Toast.makeText(this, "A senha precisa ser 'Boa' ou 'Forte'", Toast.LENGTH_SHORT).show()
                possuiErro = true
            }

            if (!validarSenhasIguais()) {
                possuiErro = true // O aviso visual vermelho já é acionado dentro da função
            }

            // 4. Se encontrou qualquer erro, interrompe o envio
            if (possuiErro) {
                return@setOnClickListener
            }

            // Se o código chegou até aqui, todos os campos estão preenchidos e válidos!
            Toast.makeText(this, "Dados validados! Pronto para enviar.", Toast.LENGTH_SHORT).show()
            // criarContaNoFirebase(nome, email, cpfCnpj, binding.etSenha.text.toString())

            if (cpfCnpj.isEmpty()) {
                binding.layoutCPFouCPNJ.error = "Preencha o CPF"
                possuiErro = true
            } else if (!isCpfValido(cpfCnpj)) {
                // Se não estiver vazio, checa se a matemática bate
                binding.layoutCPFouCPNJ.error = "CPF inválido ou inexistente"
                possuiErro = true
            }

        }



    }

    private fun verificarForcaSenha(senha: String) {
        forcaSenhaAtual = 0
        if (senha.length >= 8) forcaSenhaAtual++
        if (senha.matches(".*[A-Z].*".toRegex()) && senha.matches(".*[a-z].*".toRegex())) forcaSenhaAtual++
        if (senha.matches(".*[0-9].*".toRegex())) forcaSenhaAtual++
        if (senha.matches(".*[@#\$%^&+=!].*".toRegex())) forcaSenhaAtual++

        if (senha.isEmpty()) forcaSenhaAtual = 0

        atualizarCoresDasBarras(forcaSenhaAtual)
    }

    private fun atualizarCoresDasBarras(forca: Int) {
        val cinza = ContextCompat.getColor(this, R.color.cinza_inativo)
        val corAtual = when (forca) {
            1 -> ContextCompat.getColor(this, R.color.vermelho_fraco)
            2 -> ContextCompat.getColor(this, R.color.laranja_razoavel)
            3 -> ContextCompat.getColor(this, R.color.amarelo_bom)
            4 -> ContextCompat.getColor(this, R.color.verde_forte)
            else -> cinza
        }

        binding.barra1.setBackgroundColor(if (forca >= 1) corAtual else cinza)
        binding.barra2.setBackgroundColor(if (forca >= 2) corAtual else cinza)
        binding.barra3.setBackgroundColor(if (forca >= 3) corAtual else cinza)
        binding.barra4.setBackgroundColor(if (forca >= 4) corAtual else cinza)

        binding.textoForcaSenha.setTextColor(corAtual)
        binding.textoForcaSenha.text = when (forca) {
            1 -> "Senha fraca"
            2 -> "Senha razoável"
            3 -> "Senha boa"
            4 -> "Senha forte"
            else -> ""
        }
    }

    private fun validarSenhasIguais(): Boolean {
        val senha = binding.etSenha.text.toString()
        val confirmar = binding.etConfirmarSenha.text.toString()

        if (confirmar.isEmpty()) {
            binding.layoutConfirmarSenha.error = null
            return false
        }

        return if (senha != confirmar) {
            binding.layoutConfirmarSenha.error = "As senhas não coincidem"
            false
        } else {
            binding.layoutConfirmarSenha.error = null
            true
        }
    }

    private fun isCpfValido(documento: String): Boolean {
        // Remove tudo que não for número (resolve o requisito de não ter letras)
        val cpf = documento.replace("[^0-9]".toRegex(), "")

        // Um CPF deve ter exatamente 11 números
        if (cpf.length != 11) return false

        // Bloqueia CPFs com todos os números iguais (matematicamente passariam no cálculo, mas são falsos)
        if (cpf.all { it == cpf[0] }) return false

        // Cálculo do 1º dígito verificador
        var soma = 0
        for (i in 0..8) {
            soma += Character.getNumericValue(cpf[i]) * (10 - i)
        }
        var resto = 11 - (soma % 11)
        val digito1 = if (resto == 10 || resto == 11) 0 else resto

        if (digito1 != Character.getNumericValue(cpf[9])) return false

        // Cálculo do 2º dígito verificador
        soma = 0
        for (i in 0..9) {
            soma += Character.getNumericValue(cpf[i]) * (11 - i)
        }
        resto = 11 - (soma % 11)
        val digito2 = if (resto == 10 || resto == 11) 0 else resto

        return digito2 == Character.getNumericValue(cpf[10])
    }

}