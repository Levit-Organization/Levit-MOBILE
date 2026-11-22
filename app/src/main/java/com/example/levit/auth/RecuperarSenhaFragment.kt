package com.example.levit.auth

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.example.levit.R
import com.example.levit.data.remote.ApiClient
import com.example.levit.data.repository.ClassificadorDeErro
import com.example.levit.ui.mostrarErro
import com.example.levit.databinding.ActivityRecuperarSenhaBinding
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

/**
 * ATENÇÃO: o backend ainda não tem as rotas POST /auth/forgot-password e
 * POST /auth/reset-password. Esta tela já está pronta para funcionar assim
 * que o backend implementar; até lá, a chamada abaixo devolve 404.
 */
class RecuperarSenhaFragment : Fragment() {

    private var _binding: ActivityRecuperarSenhaBinding? = null
    private val binding get() = checkNotNull(_binding)

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = ActivityRecuperarSenhaBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.textVoltar.setOnClickListener { findNavController().popBackStack() }

        binding.btnEnviar.setOnClickListener { solicitarRecuperacao() }
    }

    private fun solicitarRecuperacao() {
        val email = binding.emailCorporativo.text.toString().trim()
        val contexto = requireContext().applicationContext

        if (email.isEmpty()) {
            Toast.makeText(contexto, R.string.recuperar_informe_email, Toast.LENGTH_SHORT).show()
            return
        }

        binding.btnEnviar.isEnabled = false

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val api = ApiClient.authApi(contexto)
                val resposta = api.forgotPassword(mapOf("email" to email))

                val mensagem = if (resposta.isSuccessful) {
                    getString(R.string.recuperar_enviado)
                } else {
                    getString(R.string.recuperar_indisponivel)
                }
                Toast.makeText(contexto, mensagem, Toast.LENGTH_LONG).show()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val tipo = ClassificadorDeErro.deExcecao(e)
                mostrarErro(getString(tipo.mensagem), tipo)
            } finally {
                // O ?. evita crash se a pessoa saiu da tela antes da resposta chegar.
                _binding?.btnEnviar?.isEnabled = true
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}