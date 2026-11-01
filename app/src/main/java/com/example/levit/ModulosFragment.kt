package com.example.levit

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.example.levit.data.repository.ModuloRepository
import com.example.levit.data.repository.ResultadoModulo
import kotlinx.coroutines.launch
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.widget.doAfterTextChanged
import com.example.levit.data.remote.dto.Modulo
import com.google.android.material.textfield.TextInputEditText
import java.text.Normalizer

class ModulosFragment : HamburgerMenuBaseFragment() {

    override val pageLayoutResId: Int = R.layout.activity_modulos

    private val moduloRepository by lazy { ModuloRepository(requireContext().applicationContext) }

    private var todosModulos: List<Modulo> = emptyList()
    private var modulosCarregados = false

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        if (!menuFoiMontado()) return

        view.findViewById<TextInputEditText>(R.id.etBuscaModulo).doAfterTextChanged {
            if (modulosCarregados) exibirModulos()
        }

        setupHamburgerMenuButton(R.id.ivMenu)

        view.findViewById<Button>(R.id.btnNovoModuloTop).setOnClickListener {
            findNavController().navigate(R.id.action_modulos_to_novoModulo)
        }
    }

    // Recarrega ao voltar da criação, para o módulo novo aparecer na hora.
    override fun onResume() {
        super.onResume()
        if (menuFoiMontado()) carregarModulos()
    }

    private fun carregarModulos() {
        val raiz = requireView()
        val progresso = raiz.findViewById<ProgressBar>(R.id.progressModulos)
        val mensagem = raiz.findViewById<TextView>(R.id.tvModulosMensagem)
        val lista = raiz.findViewById<LinearLayout>(R.id.llListaModulos)

        progresso.visibility = View.VISIBLE
        mensagem.visibility = View.GONE

        viewLifecycleOwner.lifecycleScope.launch {
            when (val resultado = moduloRepository.listar()) {
                is ResultadoModulo.Sucesso -> {
                    todosModulos = resultado.dados
                    modulosCarregados = true
                    exibirModulos()
                }
                is ResultadoModulo.Erro -> {
                    if (resultado.sessaoExpirada) {
                        redirecionarParaLogin()
                        return@launch
                    }
                    lista.removeAllViews()
                    mensagem.text = resultado.mensagem
                    mensagem.visibility = View.VISIBLE
                }
            }
            progresso.visibility = View.GONE
        }
    }

    private fun exibirModulos() {
        val raiz = view ?: return
        val lista = raiz.findViewById<LinearLayout>(R.id.llListaModulos)
        val mensagem = raiz.findViewById<TextView>(R.id.tvModulosMensagem)
        val termo = semAcento(
            raiz.findViewById<TextInputEditText>(R.id.etBuscaModulo).text.toString().trim()
        )

        val filtrados = if (termo.isEmpty()) todosModulos
        else todosModulos.filter { semAcento(it.nome).contains(termo, ignoreCase = true) }

        ModuloUi.preencherCards(layoutInflater, lista, filtrados) { abrirRegistros(it.id) }

        when {
            todosModulos.isEmpty() -> {
                mensagem.text = getString(R.string.modulos_vazio)
                mensagem.visibility = View.VISIBLE
            }
            filtrados.isEmpty() -> {
                mensagem.text = getString(R.string.modulos_busca_vazia)
                mensagem.visibility = View.VISIBLE
            }
            else -> mensagem.visibility = View.GONE
        }
    }

    private fun semAcento(texto: String): String =
        Normalizer.normalize(texto, Normalizer.Form.NFD)
            .replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
}