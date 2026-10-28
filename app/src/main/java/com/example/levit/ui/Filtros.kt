package com.example.levit.ui

import android.app.Activity
import android.app.DatePickerDialog
import android.text.InputType
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.example.levit.R
import com.example.levit.data.remote.ApiClient
import com.example.levit.data.remote.dto.Coluna
import com.example.levit.data.remote.dto.TabelaDetalhe
import com.example.levit.data.repository.Resultado
import com.example.levit.data.repository.chamarApi
import com.example.levit.databinding.ItemCampoSelectBinding
import com.example.levit.databinding.ItemCampoTextoBinding
import com.example.levit.databinding.ItemFiltroAcoesBinding
import com.example.levit.databinding.ItemFiltroLogicaBinding
import com.example.levit.databinding.ItemFiltroRegraBinding
import com.google.gson.Gson
import kotlinx.coroutines.launch
import java.util.Calendar

/**
 * Pesquisa avançada (utils/filtros.js), espelho de
 * api-backend/app/Services/Pesquisa/Filtros.php.
 */
object Filtros {
    const val MAX_FILTROS = 20

    private val IGUALDADE = listOf("igual", "diferente", "vazio", "preenchido")
    private val ORDEM = listOf("igual", "diferente", "maior", "menor", "entre", "vazio", "preenchido")
    private val OPERADORES_POR_TIPO = mapOf(
        "texto" to listOf("contem", "nao_contem", "igual", "diferente", "vazio", "preenchido"),
        "numero" to ORDEM, "data" to ORDEM, "prazo" to ORDEM, "momento" to ORDEM,
        "selecao" to IGUALDADE, "responsavel" to IGUALDADE, "recorrencia" to IGUALDADE, "vinculo" to IGUALDADE,
        "ligacao" to listOf("preenchido", "vazio"),
    )
    private val ROTULOS = mapOf(
        "igual" to "é igual a", "diferente" to "é diferente de", "contem" to "contém", "nao_contem" to "não contém",
        "maior" to "maior que", "menor" to "menor que", "entre" to "entre", "vazio" to "está vazio", "preenchido" to "está preenchido",
    )
    private val ROTULOS_DATA = mapOf("igual" to "é em", "diferente" to "não é em", "maior" to "depois de", "menor" to "antes de")
    private val ROTULOS_POR_TIPO = mapOf(
        "data" to ROTULOS_DATA, "prazo" to ROTULOS_DATA, "momento" to ROTULOS_DATA,
        "vinculo" to mapOf("igual" to "é", "diferente" to "não é"),
        "ligacao" to mapOf("preenchido" to "tem algum item", "vazio" to "não tem nenhum item"),
    )
    val OPERADORES_SEM_VALOR = listOf("vazio", "preenchido")

    fun operadoresDoTipo(tipo: String?): List<Pair<String, String>> =
        (OPERADORES_POR_TIPO[tipo] ?: IGUALDADE).map { it to (ROTULOS_POR_TIPO[tipo]?.get(it) ?: ROTULOS.getValue(it)) }

    fun operadorPadrao(tipo: String?) = operadoresDoTipo(tipo).first().first

    /** Caminho pela ligação: ida (esta → ligada) ou volta (ligada → esta). */
    data class Via(val campo: String, val sentido: String)

    /** O que dá para filtrar. campo.id vazio = "tem algum/nenhum item ligado". */
    data class Alvo(val chave: String, val grupo: String, val rotulo: String, val campo: Coluna, val via: List<Via>)

    data class Regra(var alvo: String = "", var operador: String = "", var valor: String = "", var valorAte: String = "")

    private fun coluna(id: String, nome: String, tipo: String) = Coluna(id, nome, tipo, null, null, null)

    /** alvosDoFiltro: colunas desta tabela, das tabelas que ela aponta e das que apontam para ela. */
    fun alvos(t: TabelaDetalhe?): List<Alvo> {
        if (t == null) return emptyList()
        val campos = t.campos.orEmpty()
        val alvos = campos.map { Alvo(it.id.orEmpty(), "Esta tabela", it.nome.orEmpty(), it, emptyList()) }.toMutableList()
        alvos += Alvo("criado_em", "Esta tabela", "Criado em", coluna("criado_em", "Criado em", "momento"), emptyList())
        campos.filter { it.tipo == "vinculo" && it.moduloVinculado?.acesso == true }.forEach { ligacao ->
            val via = listOf(Via(ligacao.id.orEmpty(), "ida"))
            val grupo = "${ligacao.nome} (tabela ${ligacao.moduloVinculado?.nome})"
            ligacao.moduloVinculado?.campos.orEmpty().forEach { c ->
                alvos += Alvo("${ligacao.id}>${c.id}", grupo, "${ligacao.nome} › ${c.nome}", c, via)
            }
        }
        t.ligacoesRecebidas.orEmpty().forEach { recebida ->
            val via = listOf(Via(recebida.campoId.orEmpty(), "volta"))
            val nome = recebida.modulo?.nome.orEmpty()
            val grupo = "$nome ligados pela coluna “${recebida.campoNome}”"
            alvos += Alvo("${recebida.campoId}<", grupo, "$nome ligados", coluna("", nome, "ligacao"), via)
            recebida.modulo?.campos.orEmpty().forEach { c ->
                alvos += Alvo("${recebida.campoId}<${c.id}", grupo, "Algum de $nome › ${c.nome}", c, via)
            }
        }
        return alvos
    }

    /** A regra tem tudo o que precisa para ir à API (as incompletas são ignoradas). */
    fun completa(r: Regra, alvo: Alvo?): Boolean {
        if (alvo == null || operadoresDoTipo(alvo.campo.tipo).none { it.first == r.operador }) return false
        if (r.operador in OPERADORES_SEM_VALOR) return true
        return r.valor.isNotBlank() && (r.operador != "entre" || r.valorAte.isNotBlank())
    }

    /** filtrosParaApi: JSON { logica, regras } ou null quando não há nada para filtrar. */
    fun paraApi(logica: String, regras: List<Regra>, alvos: List<Alvo>): String? {
        val porChave = alvos.associateBy { it.chave }
        val convertidas = regras.mapNotNull { r ->
            val alvo = porChave[r.alvo]
            if (!completa(r, alvo)) return@mapNotNull null
            buildMap<String, Any> {
                put("campo", alvo!!.campo.id.orEmpty())
                put("operador", r.operador)
                if (r.operador !in OPERADORES_SEM_VALOR) put("valor", r.valor.trim())
                if (r.operador == "entre") put("valor_ate", r.valorAte.trim())
                if (alvo.via.isNotEmpty()) put("via", alvo.via.map { mapOf("campo" to it.campo, "sentido" to it.sentido) })
            }
        }
        if (convertidas.isEmpty()) return null
        return Gson().toJson(mapOf("logica" to if (logica == "ou") "ou" else "e", "regras" to convertidas))
    }
}

/**
 * PainelFiltros.jsx: filtros por qualquer coluna da tabela e das ligadas,
 * combinados por "todos" (E) ou "qualquer um" (OU).
 */
class PainelFiltros(
    private val activity: Activity,
    private val destino: LinearLayout,
    private val aoMudar: () -> Unit
) {
    private var alvos: List<Filtros.Alvo> = emptyList()
    private var logica = "e"
    private val regras = mutableListOf<Filtros.Regra>()
    private var pessoas: List<Pair<String, String>>? = null
    private val titulos = mutableMapOf<String, List<Pair<String, String>>>()

    fun definirTabela(t: TabelaDetalhe) {
        alvos = Filtros.alvos(t)
        desenhar()
    }

    fun totalRegras() = regras.size

    fun paraApi(): String? = Filtros.paraApi(logica, regras, alvos)

    fun limpar() {
        regras.clear()
        logica = "e"
        desenhar()
        aoMudar()
    }

    private fun mudou() {
        aoMudar()
    }

    private fun desenhar() {
        destino.removeAllViews()
        val ctx = activity
        val inflater = ctx.layoutInflater
        if (regras.size > 1) {
            val l = ItemFiltroLogicaBinding.inflate(inflater, destino, false)
            l.btnTodos.tag = "e"
            l.btnQualquer.tag = "ou"
            l.grupo.check(if (logica == "ou") l.btnQualquer.id else l.btnTodos.id)
            l.grupo.addOnButtonCheckedListener { g, id, marcado ->
                if (marcado) { logica = g.findViewById<View>(id).tag as String; mudou() }
            }
            destino.addView(l.root)
        }
        if (regras.isEmpty()) {
            val temLigacoes = alvos.map { it.grupo }.distinct().size > 1
            destino.addView(destino.inflarFilho<TextView>(R.layout.item_filtro_vazio).apply {
                text = ctx.getString(R.string.filtro_nenhum) + " " +
                    ctx.getString(if (temLigacoes) R.string.filtro_nenhum_ligacoes else R.string.filtro_nenhum_combine)
            })
        }
        regras.forEachIndexed { indice, regra -> destino.addView(linhaDaRegra(regra, indice)) }

        val acoes = ItemFiltroAcoesBinding.inflate(inflater, destino, false)
        acoes.btnAdicionar.isVisible = regras.size < Filtros.MAX_FILTROS
        acoes.btnAdicionar.setOnClickListener {
            regras += Filtros.Regra()
            desenhar()
        }
        acoes.btnLimpar.isVisible = regras.isNotEmpty()
        acoes.btnLimpar.setOnClickListener { limpar() }
        destino.addView(acoes.root)
    }

    private fun linhaDaRegra(regra: Filtros.Regra, indice: Int): View {
        val ctx = activity
        val b = ItemFiltroRegraBinding.inflate(ctx.layoutInflater, destino, false)
        val bloco = b.bloco
        val alvo = alvos.firstOrNull { it.chave == regra.alvo }

        // Filtrar por: os grupos viram prefixo do rótulo (o optgroup do web).
        val opcoesAlvo = alvos.map { a -> a.chave to if (a.grupo == "Esta tabela") a.rotulo else "${a.grupo} — ${a.rotulo}" }
        b.cabecalho.addView(select(R.string.filtro_filtrar_por, opcoesAlvo, regra.alvo, R.string.filtro_escolha_coluna,
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)) { chave ->
            val novo = alvos.firstOrNull { it.chave == chave }
            regra.alvo = chave
            regra.operador = novo?.let { Filtros.operadorPadrao(it.campo.tipo) }.orEmpty()
            regra.valor = ""
            regra.valorAte = ""
            desenhar()
            mudou()
        }, 0)
        b.btnRemover.contentDescription = ctx.getString(R.string.filtro_remover, indice + 1)
        b.btnRemover.setOnClickListener { regras.removeAt(indice); desenhar(); mudou() }

        if (alvo != null) {
            bloco.addView(select(R.string.filtro_condicao, Filtros.operadoresDoTipo(alvo.campo.tipo), regra.operador, null) {
                regra.operador = it
                desenhar()
                mudou()
            })
            if (regra.operador !in Filtros.OPERADORES_SEM_VALOR) {
                val entre = regra.operador == "entre"
                bloco.addView(valor(alvo.campo, if (entre) R.string.rel_de else R.string.filtro_valor, regra.valor) { regra.valor = it; mudou() })
                if (entre) bloco.addView(valor(alvo.campo, R.string.rel_ate, regra.valorAte) { regra.valorAte = it; mudou() })
            }
        }
        return bloco
    }

    private fun select(
        rotulo: Int,
        opcoes: List<Pair<String, String>>,
        atual: String,
        vazio: Int?,
        params: LinearLayout.LayoutParams? = null,
        aoEscolher: (String) -> Unit
    ): View {
        val s = ItemCampoSelectBinding.inflate(activity.layoutInflater)
        s.campo.hint = activity.getString(rotulo)
        vazio?.let { s.campo.placeholderText = activity.getString(it) }
        s.acValor.configurarSelect(opcoes, atual, aoEscolher)
        params?.let { s.root.layoutParams = it }
        return s.root
    }

    /** ValorDoFiltro: lista, pessoa, item ligado, data, número ou texto. */
    private fun valor(campo: Coluna, rotulo: Int, atual: String, aoMudar: (String) -> Unit): View {
        val ctx = activity
        return when (campo.tipo) {
            "selecao" -> select(rotulo, campo.listaDeOpcoes.map { it to it }, atual, R.string.filtro_selecione, aoEscolher = aoMudar)
            "recorrencia" -> select(rotulo, Prazos.REPETICOES, atual, R.string.filtro_selecione, aoEscolher = aoMudar)
            "responsavel" -> {
                val v = select(rotulo, pessoas.orEmpty(), atual, R.string.filtro_selecione, aoEscolher = aoMudar)
                if (pessoas == null) carregarPessoas()
                v
            }
            "vinculo" -> {
                val alvo = campo.moduloVinculadoId.orEmpty()
                val v = select(rotulo, titulos[alvo].orEmpty(), atual, R.string.filtro_selecione_item, aoEscolher = aoMudar)
                if (!titulos.containsKey(alvo) && alvo.isNotEmpty()) carregarTitulos(alvo)
                v
            }
            "data", "prazo", "momento" -> {
                val c = ItemCampoTextoBinding.inflate(ctx.layoutInflater)
                c.campo.hint = ctx.getString(rotulo)
                c.etValor.isFocusable = false
                c.etValor.inputType = InputType.TYPE_NULL
                c.etValor.setText(Datas.dataCurta(atual.takeIf { it.isNotEmpty() }))
                c.etValor.setOnClickListener {
                    val cal = Calendar.getInstance().apply { Datas.ler(atual)?.let { time = it } }
                    DatePickerDialog(ctx, { _, a, m, d ->
                        val iso = Datas.iso(Calendar.getInstance().apply { set(a, m, d) }.time)
                        c.etValor.setText(Datas.dataCurta(iso))
                        aoMudar(iso)
                    }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
                }
                c.root
            }
            else -> {
                val c = ItemCampoTextoBinding.inflate(ctx.layoutInflater)
                c.campo.hint = ctx.getString(rotulo)
                c.etValor.setText(atual)
                if (campo.tipo == "numero") {
                    c.etValor.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL or InputType.TYPE_NUMBER_FLAG_SIGNED
                    c.campo.placeholderText = "0"
                } else {
                    c.campo.placeholderText = ctx.getString(R.string.filtro_digite)
                }
                c.etValor.doAfterTextChanged { aoMudar(it?.toString().orEmpty()) }
                c.root
            }
        }
    }

    private fun carregarPessoas() {
        pessoas = emptyList()
        (activity as LifecycleOwner).lifecycleScope.launch {
            val r = chamarApi("") { ApiClient.equipeApi(activity).pessoas() }
            if (r is Resultado.Sucesso) { pessoas = r.dados.map { it.id to it.nome.orEmpty() }; desenhar() }
        }
    }

    private fun carregarTitulos(tabelaId: String) {
        titulos[tabelaId] = emptyList()
        (activity as LifecycleOwner).lifecycleScope.launch {
            val r = chamarApi("") { ApiClient.registroApi(activity).titulos(tabelaId) }
            if (r is Resultado.Sucesso) { titulos[tabelaId] = r.dados.map { it.id to Vinculos.tituloDoItem(it.titulo) }; desenhar() }
        }
    }

    init { destino.isVisible = false }
}
