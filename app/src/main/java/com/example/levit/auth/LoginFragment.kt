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
import com.example.levit.data.repository.AuthRepository
import com.example.levit.data.repository.ResultadoAuth
import com.example.levit.databinding.ActivityLoginBinding
import kotlinx.coroutines.launch

class LoginFragment : Fragment() {

    private var _binding: ActivityLoginBinding? = null
    private val binding get() = _binding!!
    private val authRepository by lazy { AuthRepository(requireContext().applicationContext) }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = ActivityLoginBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.textVoltar.setOnClickListener { findNavController().popBackStack() }

        binding.tvRedefinir.setOnClickListener {
            findNavController().navigate(R.id.action_login_to_recuperarSenha)
        }

        binding.tvRegistrar.setOnClickListener {
            findNavController().navigate(R.id.action_login_to_cadastro)
        }

        binding.btnEntrar.setOnClickListener { tentarLogin() }
    }

    private fun tentarLogin() {
        val email = binding.emailCorporativo.text.toString().trim()
        val senha = binding.etSenha.text.toString()

        if (email.isEmpty() || senha.isEmpty()) {
            Toast.makeText(requireContext(), "Preencha e-mail e senha.", Toast.LENGTH_SHORT).show()
            return
        }

        binding.btnEntrar.isEnabled = false

        viewLifecycleOwner.lifecycleScope.launch {
            when (val resultado = authRepository.signIn(email, senha)) {
                is ResultadoAuth.Sucesso -> irParaDashboard()
                is ResultadoAuth.Erro -> {
                    binding.btnEntrar.isEnabled = true
                    Toast.makeText(requireContext(), resultado.mensagem, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun irParaDashboard() {
        findNavController().navigate(R.id.action_login_to_dashboard)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}