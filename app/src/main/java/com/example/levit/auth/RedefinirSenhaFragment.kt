package com.example.levit.auth

import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.example.levit.R
import com.example.levit.data.local.ServerConfig
import com.example.levit.data.repository.AuthRepository
import com.example.levit.data.repository.ResultadoRedefinicaoSenha
import com.example.levit.databinding.FragmentRedefinirSenhaBinding
import kotlinx.coroutines.launch

/**
 * Redefinir senha: aberta pelo link levit://reset-password?token=... do e-mail
 * (o HostActivity recebe o link e navega para cá com o token).
 */
class RedefinirSenhaFragment : Fragment() {

    private var _binding: FragmentRedefinirSenhaBinding? = null
    private val binding get() = checkNotNull(_binding)
    private val repository by lazy { AuthRepository(requireContext().applicationContext) }
    private var token: String? = null
    private var enviando = false

    private val pedirRedeLocal = registerForActivityResult(ActivityResultContracts.RequestPermission()) { autorizado ->
        if (autorizado) redefinirSenha()
        else mostrarErro(getString(R.string.redefinir_permissao_rede), false)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentRedefinirSenhaBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.textVoltar.setOnClickListener {
            if (!enviando) irParaLogin()
        }
        binding.etSenha.doAfterTextChanged {
            binding.layoutSenha.error = null
            atualizarForca()
            if (binding.layoutConfirmarSenha.error != null) validarConfirmacao()
        }
        binding.etConfirmarSenha.doAfterTextChanged { binding.layoutConfirmarSenha.error = null }
        binding.btnEnviar.setOnClickListener { redefinirSenha() }
        binding.btnNovoLink.setOnClickListener { findNavController().navigate(R.id.recuperarSenhaFragment) }

        token = arguments?.getString("token")?.takeIf(ValidacaoRedefinicaoSenha::tokenValido)
        binding.tvMensagem.visibility = View.GONE
        binding.btnNovoLink.visibility = View.GONE
        if (token == null) mostrarErro(getString(R.string.redefinir_link_invalido), true)
        atualizarEstado()
        atualizarForca()
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
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

        // Android 17+: servidor na rede local (desenvolvimento) pede permissão própria.
        if (Build.VERSION.SDK_INT >= 37 && servidorNaRedeLocal() &&
            ContextCompat.checkSelfPermission(requireContext(), PERMISSAO_REDE_LOCAL) != PackageManager.PERMISSION_GRANTED
        ) {
            pedirRedeLocal.launch(PERMISSAO_REDE_LOCAL)
            return
        }

        enviando = true
        binding.tvMensagem.visibility = View.GONE
        atualizarEstado()
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                when (val resultado = repository.redefinirSenha(tokenAtual, senha)) {
                    ResultadoRedefinicaoSenha.Sucesso -> {
                        token = null
                        Toast.makeText(requireContext(), R.string.redefinir_sucesso, Toast.LENGTH_LONG).show()
                        enviando = false
                        irParaLogin()
                    }
                    is ResultadoRedefinicaoSenha.Erro -> {
                        if (resultado.linkInvalido) token = null
                        mostrarErro(resultado.mensagem, resultado.linkInvalido)
                    }
                }
            } finally {
                enviando = false
                _binding?.let { atualizarEstado() }
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
        val ctx = requireContext()
        val senha = binding.etSenha.text?.toString().orEmpty()
        val forca = ValidacaoRedefinicaoSenha.forca(senha)
        val cor = ContextCompat.getColor(ctx, when (forca) {
            1 -> R.color.vermelho_fraco
            2 -> R.color.laranja_razoavel
            3, 4 -> R.color.redefinir_verde
            else -> R.color.cinza_inativo
        })
        listOf(binding.barra1, binding.barra2, binding.barra3, binding.barra4).forEachIndexed { indice, barra ->
            barra.setBackgroundColor(if (indice < forca) cor else ContextCompat.getColor(ctx, R.color.cinza_inativo))
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
        findNavController().navigate(R.id.action_global_to_login)
    }

    private fun servidorNaRedeLocal(): Boolean {
        val host = runCatching { java.net.URI(ServerConfig.obterBaseUrl(requireContext())).host }.getOrNull().orEmpty()
        val partes = host.split('.')
        val segundo = partes.getOrNull(1)?.toIntOrNull() ?: -1
        return host == "localhost" || host.startsWith("10.") || host.startsWith("192.168.") ||
            host.startsWith("127.") || (partes.firstOrNull() == "172" && segundo in 16..31)
    }

    companion object {
        private const val PERMISSAO_REDE_LOCAL = "android.permission.ACCESS_LOCAL_NETWORK"
    }
}
