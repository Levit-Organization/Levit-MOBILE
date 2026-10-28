package com.example.levit.ui

import android.content.res.ColorStateList
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.ConcatAdapter
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.example.levit.R
import com.example.levit.data.remote.ApiClient
import com.example.levit.data.repository.Resultado
import com.example.levit.data.repository.chamarApi
import com.example.levit.data.repository.executarApi
import com.example.levit.databinding.ItemEstadoListaBinding
import com.example.levit.databinding.ItemNotificacaoBinding
import com.example.levit.databinding.ItemNotificacoesRodapeBinding
import com.example.levit.databinding.ItemNotificacoesTopoBinding
import com.example.levit.databinding.SheetNotificacoesBinding
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import kotlinx.coroutines.launch

/**
 * PainelNotificacoes.jsx: central de notificações com filtro (Todas / Não
 * lidas), agrupamento por dia, abrir (marca como lida e vai ao link), excluir
 * e "marcar todas como lidas". As ações são otimistas, como no web.
 */
class PainelNotificacoes(
    private val activity: ComponentActivity,
    private var naoLidas: Int,
    private val aoMudarContagem: (Int) -> Unit,
    private val abrirLink: (String) -> Unit,
    private val aoSessaoExpirar: () -> Unit
) {
    private val api = ApiClient.notificacaoApi(activity)
    private val folhaB = SheetNotificacoesBinding.inflate(activity.layoutInflater)
    private val topo = ItemNotificacoesTopoBinding.inflate(activity.layoutInflater, folhaB.lista, false)
    private val rodape = ItemNotificacoesRodapeBinding.inflate(activity.layoutInflater, folhaB.lista, false)
    private val folha = Folha(activity, folhaB.root)
    private var filtro = "todas"
    private var lista: MutableList<JsonObject>? = null
    private var proximo: String? = null
    private var erro: String? = null

    private fun JsonObject.str(chave: String) = get(chave)?.takeIf { it.isJsonPrimitive }?.asString

    fun abrir() {
        desenhar()
        folha.abrir()
        carregar(maisAntigas = false)
    }

    private fun carregar(maisAntigas: Boolean) {
        if (!maisAntigas) { lista = null; desenhar() }
        activity.lifecycleScope.launch {
            val r = chamarApi(activity.getString(R.string.not_erro_carregar)) {
                api.listar(antesDe = if (maisAntigas) proximo else null, naoLidas = if (filtro == "nao_lidas") 1 else null)
            }
            when (r) {
                is Resultado.Sucesso -> {
                    val pagina = r.dados.get("notificacoes")?.takeIf { it.isJsonArray }?.asJsonArray
                        ?.mapNotNull { it.takeIf { e -> e.isJsonObject }?.asJsonObject }.orEmpty()
                    lista = ((if (maisAntigas) lista.orEmpty() else emptyList()) + pagina).toMutableList()
                    proximo = r.dados.str("proximo")
                    erro = null
                }
                is Resultado.Erro -> { if (r.sessaoExpirada) return@launch aoSessaoExpirar(); erro = r.mensagem }
            }
            desenhar()
        }
    }

    private fun mudarContagem(nova: Int) {
        naoLidas = maxOf(0, nova)
        aoMudarContagem(naoLidas)
    }

    private fun agoraIso() = Datas.agora()

    private fun marcarLida(n: JsonObject) {
        if (!n.str("lida_em").isNullOrEmpty()) return
        n.add("lida_em", JsonPrimitive(agoraIso()))
        mudarContagem(naoLidas - 1)
        desenhar()
        activity.lifecycleScope.launch {
            val r = executarApi(activity.getString(R.string.not_erro_marcar)) { api.marcarLida(n.str("id").orEmpty()) }
            if (r is Resultado.Erro) desfazer(r)
        }
    }

    private fun marcarTodas() {
        lista?.forEach { if (it.str("lida_em").isNullOrEmpty()) it.add("lida_em", JsonPrimitive(agoraIso())) }
        mudarContagem(0)
        desenhar()
        activity.lifecycleScope.launch {
            val r = executarApi(activity.getString(R.string.not_erro_marcar_todas)) { api.marcarTodasLidas() }
            if (r is Resultado.Erro) desfazer(r)
        }
    }

    private fun excluir(n: JsonObject) {
        lista?.remove(n)
        if (n.str("lida_em").isNullOrEmpty()) mudarContagem(naoLidas - 1)
        desenhar()
        activity.lifecycleScope.launch {
            val r = executarApi(activity.getString(R.string.not_erro_excluir)) { api.excluir(n.str("id").orEmpty()) }
            if (r is Resultado.Erro) desfazer(r)
        }
    }

    /** Em caso de erro, refaz as consultas para voltar ao estado real. */
    private suspend fun desfazer(r: Resultado.Erro) {
        if (r.sessaoExpirada) return aoSessaoExpirar()
        activity.avisar(r.mensagem, longo = true)
        (chamarApi("") { api.contagem() } as? Resultado.Sucesso)?.let { c ->
            mudarContagem(c.dados.str("nao_lidas")?.toDoubleOrNull()?.toInt() ?: 0)
        }
        carregar(maisAntigas = false)
    }

    /** Linhas: rótulo do dia, notificação, "carregar mais" ou um estado (carregando, erro, vazio). */
    private sealed interface Linha : LinhaDeLista {
        data class Dia(val rotulo: String) : Linha {
            override val chave get() = "dia:$rotulo"
            override val tipo get() = TIPO_DIA
        }
        data class Item(val n: JsonObject, val naoLida: Boolean) : Linha {
            override val chave get() = n.get("id")?.toString().orEmpty()
            override val tipo get() = TIPO_ITEM
        }
        data object Mais : Linha {
            override val chave get() = "mais"
            override val tipo get() = TIPO_MAIS
        }
        data class Estado(val chave0: String, val montar: (LinearLayout) -> Unit) : Linha {
            override val chave get() = chave0
            override val tipo get() = TIPO_ESTADO
        }
    }

    private val adaptador = AdaptadorDeLinhas<Linha>(
        criar = { pai, tipo ->
            val inflater = activity.layoutInflater
            when (tipo) {
                TIPO_DIA -> ligacaoDe(pai.inflarFilho<TextView>(R.layout.item_notificacao_dia))
                TIPO_ITEM -> ItemNotificacaoBinding.inflate(inflater, pai, false)
                TIPO_MAIS -> ligacaoDe(pai.inflarFilho<View>(R.layout.item_notificacao_mais).apply { setOnClickListener { carregar(maisAntigas = true) } })
                else -> ItemEstadoListaBinding.inflate(inflater, pai, false)
            }
        },
        ligar = { b, linha ->
            when (linha) {
                is Linha.Dia -> (b.root as TextView).text = linha.rotulo
                is Linha.Item -> ligarItem(b as ItemNotificacaoBinding, linha.n, linha.naoLida)
                Linha.Mais -> Unit
                is Linha.Estado -> linha.montar((b as ItemEstadoListaBinding).conteudo)
            }
        }
    )

    init {
        folhaB.lista.adapter = ConcatAdapter(CabecalhoFixo(topo.root), adaptador, CabecalhoFixo(rodape.root))
        topo.btnTodas.tag = "todas"
        topo.btnNaoLidas.tag = "nao_lidas"
        topo.grupo.check(topo.btnTodas.id)
        topo.grupo.addOnButtonCheckedListener { g, id, marcado ->
            if (marcado) { filtro = g.findViewById<View>(id).tag as String; carregar(maisAntigas = false) }
        }
        rodape.btnMarcarTodas.setOnClickListener { marcarTodas() }
        rodape.btnFechar.setOnClickListener { folha.dispensar() }
    }

    private fun desenhar() {
        topo.tvSubtitulo.text = if (naoLidas > 0) activity.resources.getQuantityString(R.plurals.not_nao_lidas, naoLidas, naoLidas)
            else activity.getString(R.string.not_tudo_em_dia)
        rodape.btnMarcarTodas.isVisible = naoLidas > 0

        val itens = lista
        adaptador.submitList(when {
            itens == null && erro == null -> listOf(Linha.Estado("carregando") { it.mostrarCarregando(5) })
            itens == null -> listOf(Linha.Estado("erro") {
                it.mostrarEstado(activity.getString(R.string.not_erro_carregar), erro, R.drawable.ic_warning,
                    activity.getString(R.string.tab_tentar), erro = true) { carregar(false) }
            })
            itens.isEmpty() -> listOf(Linha.Estado("vazio:$filtro") {
                it.mostrarEstado(
                    activity.getString(if (filtro == "nao_lidas") R.string.not_nenhuma_nao_lida else R.string.not_nenhuma),
                    activity.getString(R.string.not_nenhuma_desc), R.drawable.ic_mail)
            })
            else -> buildList {
                Notificacoes.agruparPorDia(itens, { it.str("criado_em") }).forEach { (rotulo, doDia) ->
                    add(Linha.Dia(rotulo.uppercase()))
                    doDia.forEach { add(Linha.Item(it, it.str("lida_em").isNullOrEmpty())) }
                }
                if (proximo != null) add(Linha.Mais)
            }
        })
    }

    private fun ligarItem(b: ItemNotificacaoBinding, n: JsonObject, naoLida: Boolean) {
        val (icone, corIcone, fundoIcone) = Notificacoes.icone(n.str("tipo"))
        fun corPeloNome(nome: String) = CORES[nome] ?: R.color.light_text
        b.linha.isActivated = naoLida
        b.linha.setOnClickListener {
            marcarLida(n)
            n.str("link")?.takeIf { it.isNotEmpty() }?.let { link -> folha.dispensar(); abrirLink(link) }
        }
        b.tvIcone.text = icone.ifBlank { "extension" }
        b.tvIcone.setTextColor(activity.cor(corPeloNome(corIcone)))
        b.tvIcone.backgroundTintList = ColorStateList.valueOf(activity.cor(corPeloNome(fundoIcone)))
        b.tvTitulo.text = n.str("titulo")
        b.tvTitulo.setTextColor(activity.cor(if (naoLida) R.color.ink else R.color.ink_soft))
        b.tvTitulo.typeface = ResourcesCompat.getFont(activity, if (naoLida) R.font.archivo_semibold else R.font.archivo)
        val mensagem = n.str("mensagem")?.takeIf { it.isNotEmpty() }
        b.tvMensagem.isVisible = mensagem != null
        b.tvMensagem.text = mensagem
        b.tvQuando.text = Automacoes.quando(n.str("criado_em"))
        b.pontoNaoLida.isVisible = naoLida
        b.btnExcluir.contentDescription = activity.getString(R.string.not_excluir, n.str("titulo"))
        b.btnExcluir.setOnClickListener { excluir(n) }
    }

    companion object {
        private const val TIPO_DIA = 1
        private const val TIPO_ITEM = 2
        private const val TIPO_MAIS = 3
        private const val TIPO_ESTADO = 4

        /** Os tokens que ICONE_NOTIFICACAO do web usa. */
        private val CORES = mapOf(
            "primary_700" to R.color.primary_700, "primary_100" to R.color.primary_100,
            "info" to R.color.info, "info_bg" to R.color.info_bg,
            "warning" to R.color.warning, "warning_bg" to R.color.warning_bg,
            "success" to R.color.success, "success_bg" to R.color.success_bg,
            "light_text" to R.color.light_text, "background" to R.color.background,
        )
    }
}
