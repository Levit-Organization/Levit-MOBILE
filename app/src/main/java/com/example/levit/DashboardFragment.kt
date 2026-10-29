package com.example.levit

import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import com.example.levit.data.remote.dto.Modulo
import com.example.levit.data.repository.ModuloRepository
import com.example.levit.data.repository.ResultadoModulo
import kotlinx.coroutines.launch
import androidx.navigation.fragment.findNavController

class DashboardFragment : HamburgerMenuBaseFragment() {

    override val pageLayoutResId: Int = R.layout.activity_dashboard

    private val moduloRepository by lazy { ModuloRepository(requireContext().applicationContext) }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        if (!menuFoiMontado()) return

        setupHamburgerMenuButton(R.id.ivMenu)

        view.findViewById<TextView>(R.id.tvAvatar).text =
            iniciais(authRepository.usuarioLogado()?.nome)

        view.findViewById<View>(R.id.btnAddModulo).setOnClickListener {
            findNavController().navigate(R.id.action_dashboard_to_novoModulo)
        }

        view.findViewById<View>(R.id.tvVerTodos).setOnClickListener {
            findNavController().navigate(R.id.action_global_to_modulos)
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
        val lista = raiz.findViewById<LinearLayout>(R.id.llModulos)
        val verTodos = raiz.findViewById<View>(R.id.tvVerTodos)

        progresso.visibility = View.VISIBLE
        mensagem.visibility = View.GONE

        viewLifecycleOwner.lifecycleScope.launch {
            when (val resultado = moduloRepository.listar()) {
                is ResultadoModulo.Sucesso -> mostrarModulos(raiz, resultado.dados)
                is ResultadoModulo.Erro -> {
                    if (resultado.sessaoExpirada) {
                        redirecionarParaLogin()
                        return@launch
                    }
                    lista.removeAllViews()
                    verTodos.visibility = View.GONE
                    mensagem.text = resultado.mensagem
                    mensagem.visibility = View.VISIBLE
                }
            }
            progresso.visibility = View.GONE
        }
    }

    private fun mostrarModulos(raiz: View, modulos: List<Modulo>) {
        raiz.findViewById<TextView>(R.id.tvStatModulos).text = modulos.size.toString()
        raiz.findViewById<TextView>(R.id.tvStatRegistros).text =
            modulos.sumOf { it.totalRegistros }.toString()

        val mensagem = raiz.findViewById<TextView>(R.id.tvModulosMensagem)
        if (modulos.isEmpty()) {
            mensagem.text = getString(R.string.modulos_vazio)
            mensagem.visibility = View.VISIBLE
        }

        ModuloUi.preencherCards(
            layoutInflater,
            raiz.findViewById(R.id.llModulos),
            modulos.take(LIMITE_NO_DASHBOARD)
        )

        raiz.findViewById<View>(R.id.tvVerTodos).visibility =
            if (modulos.size > LIMITE_NO_DASHBOARD) View.VISIBLE else View.GONE
    }

    companion object {
        private const val LIMITE_NO_DASHBOARD = 6
    }
}