package com.example.levit

import android.os.Bundle
import android.view.View
import android.widget.FrameLayout
import androidx.core.os.bundleOf
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.example.levit.data.remote.ApiClient
import com.example.levit.data.remote.dto.Modulo
import com.example.levit.data.repository.ChamadorApi
import com.example.levit.data.repository.ModuloRepository
import com.example.levit.data.repository.ResultadoApi
import com.example.levit.data.repository.ResultadoModulo
import com.example.levit.databinding.FragmentTabelasBinding
import com.example.levit.databinding.ItemTabelaBinding
import com.example.levit.ui.avisar
import com.example.levit.ui.confirmar
import com.example.levit.ui.mostrarCarregando
import com.example.levit.ui.mostrarEstado
import kotlinx.coroutines.launch
import java.text.Normalizer

/** Tabelas (Modules.jsx): a lista de tabelas da empresa, com buscar, editar e excluir. */
class ModulosFragment : HamburgerMenuBaseFragment() {

    override val pageLayoutResId: Int = R.layout.fragment_tabelas

    private var _b: FragmentTabelasBinding? = null
    private val b get() = checkNotNull(_b)

    private val moduloRepository by lazy { ModuloRepository(requireContext().applicationContext) }
    private val api by lazy { ApiClient.moduloApi(requireContext().applicationContext) }
    private val chamador by lazy { ChamadorApi(requireContext().applicationContext) }
    private var tabelas: List<Modulo>? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        if (!menuFoiMontado()) return
        val pagina = view.findViewById<FrameLayout>(R.id.pageContentContainer).getChildAt(0)
        _b = FragmentTabelasBinding.bind(pagina)

        setupHamburgerMenuButton(R.id.ivMenu)
        b.btnNovaTabela.setOnClickListener { abrirForm(null) }
        b.etBuscaModulo.doAfterTextChanged { tabelas?.let { mostrar(it) } }
    }

    override fun onResume() {
        super.onResume()
        if (menuFoiMontado()) carregar()
    }

    override fun onDestroyView() {
        _b = null
        super.onDestroyView()
    }

    private fun abrirForm(tabelaId: String?) {
        findNavController().navigate(
            R.id.novoModuloFragment,
            bundleOf(TabelaFormFragment.ARG_ID to tabelaId)
        )
    }

    private fun carregar() {
        if (tabelas == null) b.llConteudo.mostrarCarregando()
        viewLifecycleOwner.lifecycleScope.launch {
            val r = moduloRepository.listar()
            if (_b == null) return@launch
            when (r) {
                is ResultadoModulo.Sucesso -> { tabelas = r.dados; mostrar(r.dados) }
                is ResultadoModulo.Erro -> {
                    if (r.sessaoExpirada) return@launch redirecionarParaLogin()
                    b.llConteudo.mostrarEstado(
                        titulo = getString(R.string.tab_erro_carregar),
                        descricao = r.mensagem,
                        icone = R.drawable.ic_warning,
                        acao = getString(R.string.tab_tentar),
                        erro = true
                    ) { carregar() }
                }
            }
        }
    }

    private fun mostrar(todas: List<Modulo>) {
        val conteudo = b.llConteudo
        conteudo.removeAllViews()
        if (todas.isEmpty()) {
            b.layoutBuscaModulo.visibility = View.GONE
            conteudo.mostrarEstado(
                titulo = getString(R.string.tab_nenhuma_titulo),
                descricao = getString(R.string.tab_nenhuma_desc),
                icone = R.drawable.ic_grid_view,
                acao = getString(R.string.tab_criar_primeira)
            ) { abrirForm(null) }
            return
        }
        b.layoutBuscaModulo.visibility = View.VISIBLE
        val termo = semAcento(b.etBuscaModulo.text.toString().trim())
        val lista = if (termo.isEmpty()) todas else todas.filter { semAcento(it.nome).contains(termo, ignoreCase = true) }
        if (lista.isEmpty()) {
            conteudo.mostrarEstado(
                titulo = getString(R.string.modulos_busca_vazia),
                icone = R.drawable.ic_search
            )
            return
        }
        lista.forEach { tabela ->
            val v = ItemTabelaBinding.inflate(layoutInflater, conteudo, false)
            v.tvIcone.text = tabela.icone?.takeIf { it.isNotBlank() } ?: "extension"
            v.tvNome.text = tabela.nome
            v.tvItens.text = resources.getQuantityString(R.plurals.tab_n_itens, tabela.totalRegistros, tabela.totalRegistros)
            v.areaAbrir.setOnClickListener { abrirRegistros(tabela.id) }
            v.btnEditar.contentDescription = getString(R.string.tab_editar_de, tabela.nome)
            v.btnEditar.setOnClickListener { abrirForm(tabela.id) }
            v.btnExcluir.contentDescription = getString(R.string.tab_excluir_de, tabela.nome)
            v.btnExcluir.setOnClickListener { excluir(tabela) }
            conteudo.addView(v.root)
        }
    }

    private fun excluir(tabela: Modulo) {
        requireContext().confirmar(
            titulo = getString(R.string.tab_excluir_titulo, tabela.nome),
            mensagem = getString(R.string.tab_excluir_msg),
            rotuloConfirmar = getString(R.string.tab_excluir_botao)
        ) {
            viewLifecycleOwner.lifecycleScope.launch {
                when (val r = chamador.chamarSemCorpo { api.excluir(tabela.id) }) {
                    is ResultadoApi.Sucesso -> {
                        // Some da lista na hora; a próxima carga confirma.
                        tabelas = tabelas?.filterNot { it.id == tabela.id }?.also { mostrar(it) }
                        requireContext().avisar(getString(R.string.tab_excluida))
                    }
                    is ResultadoApi.Erro ->
                        if (r.sessaoExpirada) redirecionarParaLogin()
                        else requireContext().avisar(r.mensagem, longo = true)
                }
            }
        }
    }

    private fun semAcento(texto: String): String =
        Normalizer.normalize(texto, Normalizer.Form.NFD)
            .replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
}
