package com.example.levit.auth

import android.os.Bundle
import android.util.Patterns
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.example.levit.R
import com.example.levit.data.repository.AuthRepository
import com.example.levit.data.repository.ResultadoAuth
import com.example.levit.databinding.ActivityCadastroBinding
import kotlinx.coroutines.launch

class CadastroFragment : Fragment() {

    private var _binding: ActivityCadastroBinding? = null
    private val binding get() = _binding!!
    private val authRepository by lazy { AuthRepository(requireContext().applicationContext) }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = ActivityCadastroBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.textVoltar.setOnClickListener { findNavController().popBackStack() }

        binding.tvFacaLogin.setOnClickListener {
            // Se o Login já está na pilha (veio dele), volta pra ele.
            // Se não (veio das Boas-vindas), troca o Cadastro pelo Login.
            val voltouAoLogin = findNavController().popBackStack(R.id.loginFragment, false)
            if (!voltouAoLogin) {
                findNavController().navigate(R.id.action_cadastro_to_login)
            }
        }

        // Máscara de CPF/CNPJ enquanto digita (mesma do web).
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

        // Sem nome de empresa, o AuthRepository envia o nome da pessoa (mesma regra do web).
        registrar(nome = nome, email = email, senha = senha, cnpjCpf = cpfCnpj, nomeEmpresa = nomeEmpresa)
    }

    private fun registrar(nome: String, email: String, senha: String, cnpjCpf: String, nomeEmpresa: String) {
        binding.btnCriarConta.isEnabled = false
        binding.btnCriarConta.setText(R.string.cadastro_button_carregando)

        viewLifecycleOwner.lifecycleScope.launch {
            when (val resultado = authRepository.signUp(
                nome = nome,
                email = email,
                senha = senha,
                cnpjCpf = cnpjCpf,
                nomeEmpresa = nomeEmpresa
            )) {
                is ResultadoAuth.Sucesso -> {
                    // Cadastro já deixa a sessão autenticada: vai pro Dashboard
                    // limpando a pilha (voltar não retorna pro formulário).
                    findNavController().navigate(R.id.action_cadastro_to_dashboard)
                }
                is ResultadoAuth.Erro -> {
                    binding.btnCriarConta.isEnabled = true
                    binding.btnCriarConta.setText(R.string.cadastro_button_title)
                    // Com erros de validação da API (ex.: e-mail já em uso), mostra
                    // todos; senão, a mensagem principal.
                    val detalhes = resultado.camposInvalidos?.values?.flatMap {
                        if (it is List<*>) it.map(Any?::toString) else listOf(it.toString())
                    }?.joinToString(" ")
                    Toast.makeText(
                        requireContext(),
                        detalhes?.takeIf { it.isNotBlank() } ?: resultado.mensagem,
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    private fun atualizarForcaSenha(senha: String) {
        val contexto = requireContext()
        val forca = ValidacaoCadastro.forca(senha)
        val cinza = ContextCompat.getColor(contexto, R.color.cinza_inativo)
        val cor = ContextCompat.getColor(contexto, when (forca) {
            1 -> R.color.vermelho_fraco
            2 -> R.color.laranja_razoavel
            3 -> R.color.amarelo_bom
            4 -> R.color.verde_forte
            else -> R.color.cinza_inativo
        })

        listOf(binding.barra1, binding.barra2, binding.barra3, binding.barra4)
            .forEachIndexed { indice, barra ->
                barra.setBackgroundColor(if (senha.isNotEmpty() && forca > indice) cor else cinza)
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
            confirmar.isEmpty() && obrigatoria -> getString(R.string.cadastro_erro_confirmacao_obrigatoria)
            confirmar.isEmpty() -> null
            senha != confirmar -> getString(R.string.cadastro_erro_senhas_diferentes)
            else -> null
        }
        binding.layoutConfirmarSenha.error = erro
        return erro == null && confirmar.isNotEmpty()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}