package com.example.levit

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.lifecycle.lifecycleScope
import com.example.levit.data.remote.ApiClient
import com.example.levit.data.remote.dto.Modulo
import com.example.levit.data.repository.ModuloRepository
import com.example.levit.data.repository.Resultado
import com.example.levit.data.repository.ResultadoModulo
import com.example.levit.data.repository.executarApi
import com.example.levit.databinding.ActivityModulosBinding
import com.example.levit.databinding.ItemTabelaBinding
import com.example.levit.ui.avisar
import com.example.levit.ui.confirmar
import com.example.levit.ui.mostrarCarregando
import com.example.levit.ui.mostrarEstado
import kotlinx.coroutines.launch

/** Tabelas (Modules.jsx): a lista de tabelas da empresa, com editar e excluir. */
class ModulosActivity : HamburgerMenuBaseActivity() {

    private lateinit var binding: ActivityModulosBinding
    private val moduloRepository by lazy { ModuloRepository(applicationContext) }
    private val api by lazy { ApiClient.moduloApi(applicationContext) }
    private var tabelas: List<Modulo>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityModulosBinding.inflate(layoutInflater)
        setContentViewWithHamburgerMenu(binding.root)
        if (!menuPronto()) return
        setupHamburgerMenuButton(R.id.ivMenu)
        setupSino(binding.flSino)
        binding.btnNovaTabela.setOnClickListener { TabelaFormActivity.abrir(this) }
    }

    override fun onResume() {
        super.onResume()
        if (menuPronto()) carregar()
    }

    private fun carregar() {
        if (tabelas == null) binding.llConteudo.mostrarCarregando()
        lifecycleScope.launch {
            when (val r = moduloRepository.listar()) {
                is ResultadoModulo.Sucesso -> { tabelas = r.dados; mostrar(r.dados) }
                is ResultadoModulo.Erro -> {
                    if (r.sessaoExpirada) return@launch redirecionarParaLogin()
                    binding.llConteudo.mostrarEstado(
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

    private fun mostrar(lista: List<Modulo>) {
        val conteudo = binding.llConteudo
        conteudo.removeAllViews()
        if (lista.isEmpty()) {
            conteudo.mostrarEstado(
                titulo = getString(R.string.tab_nenhuma_titulo),
                descricao = getString(R.string.tab_nenhuma_desc),
                icone = R.drawable.ic_grid_view,
                acao = getString(R.string.tab_criar_primeira)
            ) { TabelaFormActivity.abrir(this) }
            return
        }
        lista.forEach { tabela ->
            val v = ItemTabelaBinding.inflate(layoutInflater, conteudo, false)
            v.tvIcone.text = tabela.icone?.takeIf { it.isNotBlank() } ?: "extension"
            v.tvNome.text = tabela.nome
            v.tvItens.text = resources.getQuantityString(R.plurals.tab_n_itens, tabela.totalRegistros, tabela.totalRegistros)
            v.areaAbrir.setOnClickListener { abrirTabela(this, tabela) }
            v.btnEditar.contentDescription = getString(R.string.tab_editar_de, tabela.nome)
            v.btnEditar.setOnClickListener { TabelaFormActivity.abrir(this, tabela.id) }
            v.btnExcluir.contentDescription = getString(R.string.tab_excluir_de, tabela.nome)
            v.btnExcluir.setOnClickListener { excluir(tabela) }
            conteudo.addView(v.root)
        }
    }

    private fun excluir(tabela: Modulo) {
        confirmar(
            titulo = getString(R.string.tab_excluir_titulo, tabela.nome),
            mensagem = getString(R.string.tab_excluir_msg),
            rotuloConfirmar = getString(R.string.tab_excluir_botao)
        ) {
            lifecycleScope.launch {
                when (val r = executarApi(getString(R.string.tab_erro_excluir)) { api.excluir(tabela.id) }) {
                    is Resultado.Sucesso -> {
                        // Some da lista na hora; a próxima carga confirma.
                        tabelas = tabelas?.filterNot { it.id == tabela.id }?.also { mostrar(it) }
                        avisar(getString(R.string.tab_excluida))
                    }
                    is Resultado.Erro -> if (r.sessaoExpirada) redirecionarParaLogin() else avisar(r.mensagem, longo = true)
                }
            }
        }
    }

    companion object {
        /** destinoDoModulo do web: cada tipo de tabela tem a sua tela. */
        fun abrirTabela(context: Context, tabela: Modulo) {
            val intent = when (tabela.tipo) {
                "recrutamento" -> Intent(context, RecrutamentoActivity::class.java)
                "financeiro" -> Intent(context, FinanceiroActivity::class.java).putExtra(FinanceiroActivity.EXTRA_ID, tabela.id)
                else -> Intent(context, ItensActivity::class.java).putExtra(ItensActivity.EXTRA_ID, tabela.id)
            }
            context.startActivity(intent)
        }
    }
}
