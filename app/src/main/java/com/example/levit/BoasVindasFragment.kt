package com.example.levit

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.EditText
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.levit.data.local.ServerConfig
import com.example.levit.databinding.ActivityMainBinding

class BoasVindasFragment : Fragment() {

    private var _binding: ActivityMainBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = ActivityMainBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnEntrar.setOnClickListener {
            findNavController().navigate(R.id.action_boasVindas_to_login)
        }

        binding.tvRegistrar.setOnClickListener {
            findNavController().navigate(R.id.action_boasVindas_to_cadastro)
        }

        if (ServerConfig.podeTrocar) binding.ivLogo.setOnLongClickListener {
            abrirConfiguracaoDeServidor()
            true
        }

        animarTitulo()
    }

    private fun abrirConfiguracaoDeServidor() {
        val campo = EditText(requireContext()).apply {
            hint = "ex.: 10.0.2.2:8080 ou 192.168.0.23:8080"
            setText(ServerConfig.obterBaseUrl(requireContext()))
            setSingleLine(true)
        }

        val dialogo = AlertDialog.Builder(requireContext())
            .setTitle("Endereço do backend")
            .setMessage("Digite host:porta do computador rodando o php spark serve.")
            .setView(campo)
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Salvar", null)
            .create()

        // O botão é ligado aqui para o diálogo não fechar quando o endereço é inválido.
        dialogo.setOnShowListener {
            dialogo.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                try {
                    ServerConfig.salvarBaseUrl(requireContext(), campo.text.toString())
                    Toast.makeText(requireContext(), "Servidor atualizado.", Toast.LENGTH_SHORT).show()
                    dialogo.dismiss()
                } catch (e: Exception) {
                    campo.error = "Informe um endereço válido, como 192.168.0.10:8080."
                }
            }
        }
        dialogo.show()
    }

    private fun animarTitulo() {
        val distanciaInicial = 40f
        val duracaoMs = 500L

        binding.tvHeadlineLine1.translationY = distanciaInicial
        binding.tvHeadlineLine1.animate()
            .alpha(1f)
            .translationY(0f)
            .setStartDelay(100L)
            .setDuration(duracaoMs)
            .setInterpolator(DecelerateInterpolator())
            .start()

        binding.tvHeadlineLine2.translationY = distanciaInicial
        binding.tvHeadlineLine2.animate()
            .alpha(1f)
            .translationY(0f)
            .setStartDelay(220L)
            .setDuration(duracaoMs)
            .setInterpolator(DecelerateInterpolator())
            .start()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}