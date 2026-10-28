package com.example.levit

import android.os.Bundle
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.ConcatAdapter
import androidx.appcompat.widget.PopupMenu
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import com.example.levit.data.remote.ApiClient
import com.example.levit.data.remote.dto.Modulo
import com.example.levit.data.repository.ModuloRepository
import com.example.levit.data.repository.Resultado
import com.example.levit.data.repository.ResultadoModulo
import com.example.levit.data.repository.chamarApi
import com.example.levit.data.repository.executarApi
import com.example.levit.databinding.ActivityAutomacoesBinding
import com.example.levit.databinding.ItemAutomacaoBinding
import com.example.levit.databinding.ItemEstadoListaBinding
import com.example.levit.databinding.ItemPaginacaoBinding
import com.example.levit.ui.AdaptadorDeLinhas
import com.example.levit.ui.CabecalhoFixo
import com.example.levit.ui.LinhaDeLista
import com.example.levit.ui.inflarFilho
import com.example.levit.ui.Automacoes
import com.example.levit.ui.HistoricoExecucoes
import com.example.levit.ui.SimulacaoAutomacao
import com.example.levit.ui.adicionarSelo
import com.example.levit.ui.avisar
import com.example.levit.ui.caixaDeIcone
import com.example.levit.ui.confirmar
import com.example.levit.ui.mostrarCarregando
import com.example.levit.ui.mostrarEstado
import com.example.levit.ui.selo
import com.example.levit.ui.texto
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlin.math.ceil

/**
 * Automações (Automacoes.jsx): todas as automações das tabelas que o cargo vê,
 * com o fluxo (Quando → Então), execuções da semana, ligar/desligar, testar,
 * histórico e excluir.
 */
class AutomacoesActivity : HamburgerMenuBaseActivity() {

    private lateinit var binding: ActivityAutomacoesBinding
    private val api by lazy { ApiClient.automacaoApi(applicationContext) }
    private val moduloRepository by lazy { ModuloRepository(applicationContext) }

    private var automacoes: List<JsonObject>? = null
    private var modulos: List<Modulo> = emptyList()
    private var erro: String? = null
    private var busca = ""
    private var pagina = 1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAutomacoesBinding.inflate(layoutInflater)
        setContentViewWithHamburgerMenu(binding.root)
        if (!menuPronto()) return
        setupHamburgerMenuButton(R.id.ivMenu)
        setupSino(binding.flSino)
        binding.btnCriar.setOnClickListener { AutomacaoFormActivity.abrir(this) }
        binding.etBusca.doAfterTextChanged { busca = it?.toString().orEmpty(); pagina = 1; renderizar() }
        rodape = ItemPaginacaoBinding.inflate(layoutInflater, binding.lista, false)
        binding.lista.adapter = ConcatAdapter(adaptador, CabecalhoFixo(rodape.root))
        rodape.btnAnterior.setOnClickListener { pagina--; renderizar(); binding.lista.scrollToPosition(0) }
        rodape.btnProxima.setOnClickListener { pagina++; renderizar(); binding.lista.scrollToPosition(0) }
    }

    override fun onResume() {
        super.onResume()
        if (menuPronto()) carregar()
    }

    private fun carregar() {
        if (automacoes == null) adaptador.submitList(listOf(Linha.Estado("carregando") { it.mostrarCarregando(4) }))
        lifecycleScope.launch {
            coroutineScope {
                val m = async { moduloRepository.listar() }
                val a = async { chamarApi(getString(R.string.aut_erro_carregar)) { api.listarTodas() } }
                (m.await() as? ResultadoModulo.Sucesso)?.let { modulos = it.dados }
                when (val r = a.await()) {
                    is Resultado.Sucesso -> { automacoes = r.dados; erro = null }
                    is Resultado.Erro -> { if (r.sessaoExpirada) return@coroutineScope redirecionarParaLogin(); erro = r.mensagem }
                }
            }
            if (!isFinishing) renderizar()
        }
    }

    // --- Leitura do JSON da automação ------------------------------------------------

    private fun JsonObject.str(chave: String) = get(chave)?.takeIf { it.isJsonPrimitive }?.asString
    private fun JsonObject.ativo() = get("ativo")?.takeIf { it.isJsonPrimitive }?.let {
        if (it.asJsonPrimitive.isBoolean) it.asBoolean else it.asString in listOf("t", "true", "1")
    } == true
    private fun JsonObject.estatistica(chave: String) =
        get("estatisticas")?.takeIf { it.isJsonObject }?.asJsonObject?.get(chave)?.takeIf { it.isJsonPrimitive }?.asString
    private fun JsonObject.acoes() = get("acoes")?.takeIf { it.isJsonArray }?.asJsonArray?.mapNotNull { it.takeIf { e -> e.isJsonObject }?.asJsonObject }.orEmpty()

    // --- Tela -----------------------------------------------------------------------

    private lateinit var rodape: ItemPaginacaoBinding

    /** Linhas: uma automação (com o JSON do momento, para o DiffUtil ver mudanças) ou um estado. */
    private sealed interface Linha : LinhaDeLista {
        data class Cartao(val a: JsonObject, val foto: String = a.toString()) : Linha {
            override val chave get() = a.get("id")?.toString().orEmpty()
            override val tipo get() = TIPO_CARTAO
        }
        data class Estado(val chave0: String, val montar: (LinearLayout) -> Unit) : Linha {
            override val chave get() = chave0
            override val tipo get() = TIPO_ESTADO
        }
    }

    private val adaptador = AdaptadorDeLinhas<Linha>(
        criar = { pai, tipo ->
            if (tipo == TIPO_CARTAO) ItemAutomacaoBinding.inflate(layoutInflater, pai, false)
            else ItemEstadoListaBinding.inflate(layoutInflater, pai, false)
        },
        ligar = { b, linha ->
            when (linha) {
                is Linha.Cartao -> ligarCartao(b as ItemAutomacaoBinding, linha.a)
                is Linha.Estado -> linha.montar((b as ItemEstadoListaBinding).conteudo)
            }
        }
    )

    private fun estado(chave: String, montar: (LinearLayout) -> Unit) = adaptador.submitList(listOf(Linha.Estado(chave, montar)))

    private fun renderizar() {
        val lista = automacoes
        val total = lista?.size ?: 0
        val ativas = lista.orEmpty().count { it.ativo() }
        val execucoes = lista.orEmpty().sumOf { it.estatistica("execucoes_7d")?.toIntOrNull() ?: 0 }
        val falhas = lista.orEmpty().sumOf { it.estatistica("falhas_7d")?.toIntOrNull() ?: 0 }
        binding.tvSubtitulo.text = if (total == 0) getString(R.string.aut_subtitulo_vazio) else listOfNotNull(
            resources.getQuantityString(R.plurals.aut_ativas_de, total, ativas, total),
            resources.getQuantityString(R.plurals.aut_execucoes_7d, execucoes, execucoes),
            if (falhas > 0) resources.getQuantityString(R.plurals.aut_falhas, falhas, falhas) else null,
        ).joinToString(" · ")

        val termo = busca.trim().lowercase()
        val filtradas = lista.orEmpty().filter {
            termo.isEmpty() || it.str("nome").orEmpty().lowercase().contains(termo) || it.str("modulo_nome").orEmpty().lowercase().contains(termo)
        }
        rodape.linhaPaginas.isVisible = false
        when {
            lista == null && erro == null -> estado("carregando") { it.mostrarCarregando(4) }
            erro != null && lista == null -> estado("erro") { it.mostrarEstado(getString(R.string.aut_erro_carregar), erro, R.drawable.ic_warning,
                getString(R.string.tab_tentar), erro = true) { carregar() } }
            filtradas.isEmpty() -> estado("vazio:$termo") { it.mostrarEstado(
                titulo = getString(if (termo.isNotEmpty()) R.string.aut_nenhuma_encontrada else R.string.aut_nenhuma_ainda),
                descricao = if (termo.isNotEmpty()) getString(R.string.aut_busca_desc, busca.trim()) else getString(R.string.aut_vazio_desc),
                icone = R.drawable.ic_bolt,
                acao = if (termo.isEmpty()) getString(R.string.aut_criar) else null
            ) { AutomacaoFormActivity.abrir(this) } }
            else -> {
                val paginas = maxOf(1, ceil(filtradas.size / POR_PAGINA.toDouble()).toInt())
                pagina = pagina.coerceIn(1, paginas)
                adaptador.submitList(filtradas.drop((pagina - 1) * POR_PAGINA).take(POR_PAGINA).map { Linha.Cartao(it) })
                rodape.linhaPaginas.isVisible = paginas > 1
                rodape.tvPagina.text = getString(R.string.itens_pagina, pagina, paginas)
                rodape.btnAnterior.isEnabled = pagina > 1
                rodape.btnProxima.isEnabled = pagina < paginas
            }
        }
    }

    private fun ligarCartao(v: ItemAutomacaoBinding, a: JsonObject) {
        val gatilhos = Automacoes.gatilhosDaAutomacao(a)
        val iconeGatilho = if (gatilhos.size == 1) Automacoes.GATILHOS[gatilhos[0]]?.icone ?: "bolt" else "bolt"
        v.flIcone.removeAllViews()
        v.flIcone.addView(caixaDeIcone(iconeGatilho, ladoDp = 36, iconeDp = 18))
        v.tvNome.text = a.str("nome")
        v.tvTabela.text = a.str("modulo_nome")

        // Fluxo: primeiro gatilho (+n), funil se tiver condições, → primeira ação (+n).
        val fluxo = v.grupoFluxo
        fluxo.removeAllViews()
        if (gatilhos.isNotEmpty()) fluxo.adicionarSelo(Automacoes.gatilhoLabel(gatilhos[0]), false)
        if (gatilhos.size > 1) fluxo.addView(texto("+${gatilhos.size - 1}", 11f))
        val totalCondicoes = Automacoes.condicoesDaAutomacao(a)?.regras?.size ?: 0
        if (totalCondicoes > 0) fluxo.addView(fluxo.inflarFilho<TextView>(R.layout.item_fluxo_icone).apply {
            text = "filter_alt"
            contentDescription = getString(R.string.aut_com_condicoes)
        })
        fluxo.addView(fluxo.inflarFilho<TextView>(R.layout.item_fluxo_icone).apply { text = "arrow_forward" })
        val acoes = a.acoes()
        fluxo.adicionarSelo(acoes.firstOrNull()?.str("tipo")?.let { Automacoes.acaoLabel(it) } ?: "—", true)
        if (acoes.size > 1) fluxo.addView(texto("+${acoes.size - 1}", 11f))

        // Execuções da semana e a última.
        val pausada = !a.ativo() && !a.str("pausada_por_falhas_em").isNullOrEmpty()
        v.tvExecucoes.text = a.estatistica("execucoes_7d") ?: "0"
        val falhas = a.estatistica("falhas_7d")?.toIntOrNull() ?: 0
        // Um selo de estado por linha: "Pausada por falhas" já conta a notícia das falhas.
        v.flFalhas.removeAllViews()
        v.flPausada.removeAllViews()
        if (falhas > 0 && !pausada) v.flFalhas.addView(selo(resources.getQuantityString(R.plurals.aut_falhas, falhas, falhas), R.color.danger, R.color.danger_bg))
        val ultima = a.estatistica("ultima_execucao_em")
        v.tvUltima.text = getString(R.string.aut_ultima_execucao, if (ultima.isNullOrEmpty()) getString(R.string.aut_nunca) else Automacoes.quando(ultima))
        v.linhaExecucoes.setOnClickListener { abrirHistorico(a) }
        if (pausada) v.flPausada.addView(selo("⏸ " + getString(R.string.aut_pausada_falhas), R.color.warning, R.color.warning_bg))

        v.switchAtivo.setOnCheckedChangeListener(null)
        v.switchAtivo.isChecked = a.ativo()
        v.switchAtivo.contentDescription = getString(R.string.aut_ativa_de, a.str("nome"))
        v.switchAtivo.setOnCheckedChangeListener { _, ligado -> alternar(a, ligado) }

        v.btnMais.contentDescription = getString(R.string.aut_mais_acoes, a.str("nome"))
        v.btnMais.setOnClickListener { botao ->
            PopupMenu(this, botao).apply {
                menu.add(0, 1, 0, R.string.aut_editar)
                menu.add(0, 2, 1, R.string.aut_testar)
                menu.add(0, 3, 2, R.string.aut_historico)
                menu.add(0, 4, 3, R.string.aut_excluir)
                setOnMenuItemClickListener { item ->
                    when (item.itemId) {
                        1 -> AutomacaoFormActivity.abrir(this@AutomacoesActivity, a.str("modulo_id"), a.str("id"))
                        2 -> testar(a)
                        3 -> abrirHistorico(a)
                        4 -> excluir(a)
                    }
                    true
                }
            }.show()
        }
    }

    /** Otimista: muda na tela e volta se a API recusar. Reativar limpa a pausa automática. */
    private fun alternar(a: JsonObject, ativo: Boolean) {
        val antes = a.deepCopy()
        a.add("ativo", JsonPrimitive(ativo))
        if (ativo) a.add("pausada_por_falhas_em", JsonNull.INSTANCE)
        renderizar()
        lifecycleScope.launch {
            val r = executarApi(getString(R.string.aut_erro_status)) {
                api.alternarAtivo(a.str("modulo_id").orEmpty(), a.str("id").orEmpty(), mapOf("ativo" to ativo))
            }
            if (r is Resultado.Erro) {
                if (r.sessaoExpirada) return@launch redirecionarParaLogin()
                a.add("ativo", antes.get("ativo") ?: JsonNull.INSTANCE)
                a.add("pausada_por_falhas_em", antes.get("pausada_por_falhas_em") ?: JsonNull.INSTANCE)
                avisar(r.mensagem, longo = true)
                renderizar()
            }
        }
    }

    private fun excluir(a: JsonObject) {
        confirmar(getString(R.string.aut_excluir_titulo, a.str("nome")), getString(R.string.aut_excluir_msg), getString(R.string.aut_excluir_botao)) {
            lifecycleScope.launch {
                when (val r = executarApi(getString(R.string.aut_erro_excluir)) { api.excluir(a.str("modulo_id").orEmpty(), a.str("id").orEmpty()) }) {
                    is Resultado.Sucesso -> { avisar(getString(R.string.aut_excluida)); carregar() }
                    is Resultado.Erro -> if (r.sessaoExpirada) redirecionarParaLogin() else avisar(r.mensagem, longo = true)
                }
            }
        }
    }

    private fun abrirHistorico(a: JsonObject) =
        HistoricoExecucoes(this, a.str("modulo_id").orEmpty(), a.str("id").orEmpty(), a.str("nome").orEmpty()) { redirecionarParaLogin() }.abrir()

    /** Definição salva no formato que o endpoint de simulação espera. */
    private fun testar(a: JsonObject) {
        val condicoes = Automacoes.condicoesDaAutomacao(a)
        val acoes = a.acoes().map { ac ->
            val conf = ac.get("configuracao")?.takeIf { it.isJsonObject }?.asJsonObject
            mapOf("tipo" to ac.str("tipo"), "configuracao" to (conf ?: com.google.gson.JsonObject()))
        }
        val definicao = mapOf(
            "gatilhos" to Automacoes.gatilhosDaAutomacao(a),
            "condicoes" to condicoes?.let { c ->
                mapOf("logica" to c.logica, "regras" to c.regras.map { mapOf("campo" to it.campo, "operador" to it.operador, "valor" to it.valor, "valor_de" to it.valorDe) })
            },
            "acoes" to acoes,
        )
        val tipo = modulos.firstOrNull { it.id == a.str("modulo_id") }?.tipo
        SimulacaoAutomacao(this, a.str("modulo_id").orEmpty(), definicao, Automacoes.ehVaga(tipo)) { redirecionarParaLogin() }.abrir()
    }

    companion object {
        private const val POR_PAGINA = 25
        private const val TIPO_CARTAO = 1
        private const val TIPO_ESTADO = 2
    }
}
