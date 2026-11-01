package com.example.levit

import android.content.Context
import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.RecyclerView
import com.example.levit.data.repository.AuthRepository
import com.example.levit.data.repository.RegistroRepository
import com.example.levit.data.repository.ResultadoApi
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.launch
import androidx.core.os.bundleOf
import com.example.levit.data.remote.dto.Registro
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.gson.Gson

class RegistrosFragment : HamburgerMenuBaseFragment() {

    override val pageLayoutResId: Int = R.layout.fragment_registros

    private val repositorio by lazy { RegistroRepository(requireContext().applicationContext) }

    private val adapter = RegistrosAdapter(
        aoEditar = { abrirForm(it) },
        aoExcluir = { confirmarExclusao(it) }
    )

    private val moduloId: String get() = requireArguments().getString("moduloId").orEmpty()
    private var busca = ""

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        if (!menuFoiMontado()) return
        busca = ""

        setupHamburgerMenuButton(R.id.ivMenu)

        FotoPerfil.aplicar(view.findViewById(R.id.tvAvatar))

        view.findViewById<RecyclerView>(R.id.rvRegistros).adapter = adapter

        view.findViewById<View>(R.id.tvMeusModulos).setOnClickListener {
            findNavController().navigate(R.id.action_global_to_modulos)
        }

        // "Editar módulo" só aparece quando essa funcionalidade existir.
        view.findViewById<View>(R.id.btnEditarModulo).visibility = View.GONE
        view.findViewById<View>(R.id.btnNovoRegistro).setOnClickListener { abrirForm(null) }

        val campoBusca = view.findViewById<TextInputEditText>(R.id.etBuscaRegistro)
        campoBusca.setOnEditorActionListener { _, acao, _ ->
            if (acao == EditorInfo.IME_ACTION_SEARCH) {
                busca = campoBusca.text.toString().trim()
                esconderTeclado(campoBusca)
                viewLifecycleOwner.lifecycleScope.launch { carregarRegistros() }
                true
            } else false
        }
        // Apagar tudo (inclusive pelo X do campo) volta à lista completa.
        campoBusca.doAfterTextChanged { texto ->
            if (texto.isNullOrBlank() && busca.isNotEmpty()) {
                busca = ""
                viewLifecycleOwner.lifecycleScope.launch { carregarRegistros() }
            }
        }

        carregar()
    }

    private fun carregar() {
        mostrarCarregando(true)
        viewLifecycleOwner.lifecycleScope.launch {
            val modulo = when (val res = repositorio.detalhesDoModulo(moduloId)) {
                is ResultadoApi.Sucesso -> res.dados
                is ResultadoApi.Erro -> {
                    tratarErro(res, esconderControles = true)
                    return@launch
                }
            }

            requireView().findViewById<TextView>(R.id.tvNomeBreadcrumb).text = modulo.nome
            requireView().findViewById<TextView>(R.id.tvTituloModulo).text = modulo.nome

            // O web só usa esta tela para módulos comuns.
            if ((modulo.tipo ?: "dados") != "dados") {
                mostrarMensagem(getString(R.string.registros_tipo_indisponivel))
                esconderControles()
                return@launch
            }

            adapter.campos = modulo.camposOrdenados
            carregarRegistros()
        }
    }

    private suspend fun carregarRegistros() {
        mostrarCarregando(true)
        when (val res = repositorio.listar(moduloId, busca)) {
            is ResultadoApi.Sucesso -> {
                adapter.submitList(res.dados)
                if (res.dados.isEmpty()) {
                    mostrarMensagem(
                        getString(if (busca.isEmpty()) R.string.registros_vazio else R.string.registros_busca_vazia)
                    )
                } else {
                    mostrarCarregando(false)
                }
            }
            is ResultadoApi.Erro -> {
                adapter.submitList(emptyList())
                tratarErro(res, esconderControles = false)
            }
        }
    }

    private fun tratarErro(erro: ResultadoApi.Erro, esconderControles: Boolean) {
        if (erro.sessaoExpirada) {
            findNavController().navigate(R.id.action_global_to_login)
            return
        }
        mostrarMensagem(erro.mensagem)
        if (esconderControles) esconderControles()
    }

    private fun esconderControles() {
        val v = requireView()
        v.findViewById<View>(R.id.layoutBuscaRegistro).visibility = View.GONE
        v.findViewById<View>(R.id.btnNovoRegistro).visibility = View.GONE
    }

    private fun mostrarCarregando(carregando: Boolean) {
        val v = requireView()
        v.findViewById<ProgressBar>(R.id.progressRegistros).visibility =
            if (carregando) View.VISIBLE else View.GONE
        if (carregando) v.findViewById<View>(R.id.tvRegistrosMensagem).visibility = View.GONE
    }

    private fun mostrarMensagem(texto: String) {
        val v = requireView()
        v.findViewById<ProgressBar>(R.id.progressRegistros).visibility = View.GONE
        val msg = v.findViewById<TextView>(R.id.tvRegistrosMensagem)
        msg.text = texto
        msg.visibility = View.VISIBLE
    }

    private fun esconderTeclado(alvo: View) {
        val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(alvo.windowToken, 0)
    }

    private fun abrirForm(registro: Registro?) {
        findNavController().navigate(
            R.id.registroFormFragment,
            bundleOf(
                "moduloId" to moduloId,
                "registroJson" to registro?.let { Gson().toJson(it) }
            )
        )
    }

    private fun confirmarExclusao(registro: Registro) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.registros_excluir_titulo)
            .setMessage(R.string.registros_excluir_mensagem)
            .setNegativeButton(R.string.equipe_cancelar, null)
            .setPositiveButton(R.string.registros_excluir_confirmar) { _, _ ->
                viewLifecycleOwner.lifecycleScope.launch {
                    when (val res = repositorio.excluir(moduloId, registro.id)) {
                        is ResultadoApi.Sucesso -> {
                            Toast.makeText(requireContext(), R.string.registros_excluido, Toast.LENGTH_SHORT).show()
                            carregarRegistros()
                        }
                        is ResultadoApi.Erro -> {
                            if (res.sessaoExpirada) {
                                findNavController().navigate(R.id.action_global_to_login)
                            } else {
                                Toast.makeText(requireContext(), res.mensagem, Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                }
            }
            .show()
    }
}