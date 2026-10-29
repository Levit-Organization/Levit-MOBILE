package com.example.levit

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.example.levit.data.repository.ModuloRepository
import com.example.levit.data.repository.ResultadoModulo
import kotlinx.coroutines.launch

class ModulosFragment : HamburgerMenuBaseFragment() {

    override val pageLayoutResId: Int = R.layout.activity_modulos

    private val moduloRepository by lazy { ModuloRepository(requireContext().applicationContext) }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        if (!menuFoiMontado()) return

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
                    ModuloUi.preencherCards(layoutInflater, lista, resultado.dados)
                    if (resultado.dados.isEmpty()) {
                        mensagem.text = getString(R.string.modulos_vazio)
                        mensagem.visibility = View.VISIBLE
                    }
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
}