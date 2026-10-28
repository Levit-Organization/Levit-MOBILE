package com.example.levit.ui

import android.view.Gravity
import android.widget.LinearLayout
import androidx.activity.ComponentActivity
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import com.example.levit.R
import com.example.levit.data.remote.ApiClient
import com.example.levit.data.repository.Resultado
import com.example.levit.data.repository.chamarApi
import com.example.levit.data.repository.executarApi
import com.example.levit.databinding.ItemAutExecucaoBinding
import com.example.levit.databinding.ItemAutPassoBinding
import com.example.levit.databinding.ItemAutPassoTesteBinding
import com.example.levit.databinding.ItemCampoSelectBinding
import com.google.android.material.chip.ChipGroup
import com.google.gson.GsonBuilder
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

private fun JsonObject.str(chave: String): String? = get(chave)?.takeIf { it.isJsonPrimitive }?.asString
private fun JsonObject.arr(chave: String) = get(chave)?.takeIf { it.isJsonArray }?.asJsonArray?.mapNotNull { it.takeIf { e -> e.isJsonObject }?.asJsonObject }.orEmpty()

/**
 * HistoricoExecucoes.jsx: as execuções de uma automação, paginadas por cursor.
 * Com a folha aberta, atualiza sozinha a cada 15 s (retentativas e execuções novas).
 */
class HistoricoExecucoes(
    private val activity: ComponentActivity,
    private val moduloId: String,
    private val automacaoId: String,
    private val nome: String,
    private val aoSessaoExpirar: () -> Unit
) {
    private val api = ApiClient.automacaoApi(activity)
    private val corpoEFolha = activity.corpoDeFolha()
    private val corpo = corpoEFolha.second
    private val folha = Folha(activity, corpoEFolha.first)
    private var execucoes: List<JsonObject>? = null
    private var proximo: String? = null
    private var erro: String? = null
    private val abertas = mutableSetOf<String>()
    private var reexecutando: String? = null
    private var atualizacao: Job? = null

    fun abrir() {
        desenhar()
        folha.aoFechar { atualizacao?.cancel() }.abrir()
        atualizacao = activity.lifecycleScope.launch {
            // A folha nasce de um clique, então não dá para usar repeatOnLifecycle aqui:
            // o laço só pula as atualizações enquanto a tela está em segundo plano.
            while (isActive) {
                if (activity.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) carregar(maisAntigas = false)
                delay(15_000)
            }
        }
    }

    private suspend fun carregar(maisAntigas: Boolean) {
        val r = chamarApi(activity.getString(R.string.aut_erro_historico)) {
            api.execucoes(moduloId, automacaoId, antesDe = if (maisAntigas) proximo else null)
        }
        when (r) {
            is Resultado.Sucesso -> {
                val pagina = r.dados.arr("execucoes")
                execucoes = if (maisAntigas) execucoes.orEmpty() + pagina else pagina
                // Primeira página atualizada não pode perder as antigas já carregadas.
                if (maisAntigas || proximo == null) proximo = r.dados.str("proximo")
                erro = null
            }
            is Resultado.Erro -> { if (r.sessaoExpirada) return aoSessaoExpirar(); erro = r.mensagem }
        }
        desenhar()
    }

    private fun desenhar() {
        corpo.removeAllViews()
        corpo.tituloDeFolha(activity.getString(R.string.aut_historico_titulo), nome)
        val lista = execucoes
        val conteudo = corpo.inflarFilho<LinearLayout>(R.layout.item_bloco_folha)
        corpo.addView(conteudo)
        when {
            lista == null && erro == null -> conteudo.mostrarCarregando(5)
            lista == null -> conteudo.mostrarEstado(activity.getString(R.string.aut_erro_historico), erro, R.drawable.ic_warning,
                activity.getString(R.string.tab_tentar), erro = true) { activity.lifecycleScope.launch { carregar(false) } }
            lista.isEmpty() -> conteudo.mostrarEstado(activity.getString(R.string.aut_nenhuma_execucao),
                activity.getString(R.string.aut_nenhuma_execucao_desc), R.drawable.ic_history)
            else -> {
                lista.forEach { conteudo.addView(execucao(it)) }
                if (proximo != null) conteudo.addView(activity.botao(activity.getString(R.string.aut_carregar_mais), "ghost") {
                    activity.lifecycleScope.launch { carregar(maisAntigas = true) }
                })
            }
        }
        corpo.addView(activity.botao(activity.getString(R.string.aut_fechar), "secondary") { folha.dispensar() }.apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = activity.dp(16) }
        })
    }

    private fun corDoStatus(status: String?) = when (status) {
        "sucesso" -> R.color.success to R.color.success_bg
        "erro" -> R.color.danger to R.color.danger_bg
        "retentando" -> R.color.warning to R.color.warning_bg
        else -> R.color.ink_soft to R.color.sidebar
    }

    private fun execucao(e: JsonObject): LinearLayout {
        val id = e.str("id").orEmpty()
        val status = e.str("status")
        // Falhas já vêm abertas, como no web.
        val aberta = id in abertas || (status == "erro" && "fechada-$id" !in abertas)
        val b = ItemAutExecucaoBinding.inflate(activity.layoutInflater)
        b.topo.setOnClickListener {
            if (aberta) { abertas -= id; abertas += "fechada-$id" } else { abertas += id; abertas -= "fechada-$id" }
            desenhar()
        }
        val info = Automacoes.STATUS_EXECUCAO[status] ?: InfoComIcone(status.orEmpty(), "help_outline")
        val (corTexto, corFundo) = corDoStatus(status)
        b.topo.addView(activity.selo(info.rotulo, corTexto, corFundo, ponto = true), 0)
        val gatilho = e.str("gatilho")?.let { Automacoes.gatilhoLabel(it) } ?: activity.getString(R.string.aut_execucao)
        val tentativa = e.str("tentativa")?.toIntOrNull() ?: 1
        b.tvGatilho.text = if (tentativa > 1) activity.getString(R.string.aut_tentativa, gatilho, tentativa) else gatilho
        val ms = e.str("duracao_ms")?.toDoubleOrNull()?.toLong()
        b.tvQuando.text = listOf(Automacoes.quando(e.str("executado_em")), Automacoes.duracao(ms)).filter { it.isNotEmpty() }.joinToString(" · ")
        b.tvSeta.text = if (aberta) "expand_less" else "expand_more"
        if (!aberta) return b.root

        val detalhe = b.detalhe
        b.divisoria.isVisible = true
        detalhe.isVisible = true
        val passos = e.arr("passos")
        if (passos.isNotEmpty()) passos.forEach { p ->
            val ok = p.str("status") == "sucesso"
            val linha = ItemAutPassoBinding.inflate(activity.layoutInflater, detalhe, false)
            linha.tvIcone.text = if (ok) "check" else "close"
            linha.tvIcone.setTextColor(activity.cor(if (ok) R.color.success else R.color.danger))
            val indice = (p.str("indice")?.toIntOrNull() ?: 0) + 1
            linha.tvTitulo.text = activity.getString(R.string.aut_acao_n, indice, Automacoes.acaoLabel(p.str("tipo")))
            p.str("mensagem")?.let { linha.tvMensagem.text = it; linha.tvMensagem.isVisible = true }
            detalhe.addView(linha.root)
        } else e.str("detalhes")?.let { detalhe.addView(activity.texto(it, 14f, R.color.danger, topo = 8)) }
        if (status == "retentando") detalhe.addView(activity.texto(activity.getString(R.string.aut_retentando_desc), 12f, topo = 8))
        if (status == "erro") detalhe.addView(activity.botao(activity.getString(R.string.aut_reexecutar), "secondary", R.drawable.ic_history) {
            reexecutar(id)
        }.apply {
            isEnabled = reexecutando != id
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { gravity = Gravity.END; topMargin = activity.dp(8) }
        })
        return b.root
    }

    private fun reexecutar(execucaoId: String) {
        reexecutando = execucaoId
        desenhar()
        activity.lifecycleScope.launch {
            when (val r = executarApi(activity.getString(R.string.aut_erro_reexecutar)) { api.reexecutar(moduloId, automacaoId, execucaoId) }) {
                is Resultado.Sucesso -> activity.avisar(activity.getString(R.string.aut_reexecucao_fila), longo = true)
                is Resultado.Erro -> if (r.sessaoExpirada) return@launch aoSessaoExpirar() else activity.avisar(r.mensagem, longo = true)
            }
            reexecutando = null
            carregar(maisAntigas = false)
        }
    }
}

/**
 * SimulacaoModal.jsx: o que a automação faria com um item real, sem enviar
 * nada. Funciona com a definição ainda não salva.
 */
class SimulacaoAutomacao(
    private val activity: ComponentActivity,
    private val moduloId: String,
    private val definicao: Map<String, Any?>,
    private val vaga: Boolean,
    private val aoSessaoExpirar: () -> Unit
) {
    private val api = ApiClient.automacaoApi(activity)
    private val corpoEFolha = activity.corpoDeFolha()
    private val corpo = corpoEFolha.second
    private val folha = Folha(activity, corpoEFolha.first)
    private var registros: List<Pair<String, String>> = emptyList()
    private var registroId = ""
    private var resultado: JsonObject? = null
    private var erro: String? = null
    private var carregando = true
    private var tarefa: Job? = null

    fun abrir() {
        desenhar()
        folha.abrir()
        if (!vaga) activity.lifecycleScope.launch {
            (chamarApi("") { ApiClient.registroApi(activity).listar(moduloId, null, null) } as? Resultado.Sucesso)?.let { r ->
                registros = r.dados.take(50).map { it.id to Automacoes.rotuloRegistro(it.id, it.dados) }
                desenhar()
            }
        }
        simular()
    }

    private fun simular() {
        tarefa?.cancel()
        carregando = true
        desenhar()
        tarefa = activity.lifecycleScope.launch {
            val entrada = buildMap<String, Any?> {
                put("automacao", definicao)
                if (registroId.isNotEmpty()) put("registro_id", registroId)
            }
            when (val r = chamarApi(activity.getString(R.string.aut_erro_testar)) { api.simular(moduloId, entrada) }) {
                is Resultado.Sucesso -> { resultado = r.dados; erro = null }
                is Resultado.Erro -> { if (r.sessaoExpirada) return@launch aoSessaoExpirar(); erro = r.mensagem; resultado = null }
            }
            carregando = false
            desenhar()
        }
    }

    private fun desenhar() {
        corpo.removeAllViews()
        corpo.tituloDeFolha(activity.getString(R.string.aut_testar_titulo), activity.getString(R.string.aut_testar_sub))
        if (vaga) corpo.addView(activity.texto(activity.getString(R.string.aut_candidato_recente), 14f, R.color.ink_soft, topo = 16))
        else {
            val s = ItemCampoSelectBinding.inflate(activity.layoutInflater)
            s.campo.hint = activity.getString(R.string.aut_item_exemplo)
            s.acValor.configurarSelect(listOf("" to activity.getString(R.string.aut_mais_recente)) + registros, registroId) {
                registroId = it
                simular()
            }
            corpo.addView(s.root, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = activity.dp(16) })
        }
        val r = resultado
        when {
            carregando -> corpo.addView(corpo.inflarFilho<LinearLayout>(R.layout.item_bloco_folha).apply { mostrarCarregando(2) })
            erro != null -> corpo.addView(activity.alerta(erro!!, "error", 16))
            r != null -> desenharResultado(r)
        }
        corpo.addView(activity.botao(activity.getString(R.string.aut_fechar), "secondary") { folha.dispensar() }.apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = activity.dp(20) }
        })
    }

    private fun desenharResultado(r: JsonObject) {
        val dados = r.get("dados")?.takeIf { it.isJsonObject }?.asJsonObject
        if (!vaga && r.str("registro_id").isNullOrEmpty() && (dados == null || dados.size() == 0)) corpo.addView(activity.alerta(activity.getString(R.string.aut_sem_itens), "info", 16))
        if (vaga && dados?.str("nome").isNullOrEmpty()) corpo.addView(activity.alerta(activity.getString(R.string.aut_sem_candidatos), "info", 16))
        @Suppress("UNCHECKED_CAST")
        val regras = ((definicao["condicoes"] as? Map<String, Any?>)?.get("regras") as? List<Map<String, Any?>>).orEmpty()
        if (regras.any { it["operador"] in Automacoes.OPERADORES_DE_MUDANCA }) corpo.addView(activity.alerta(
            activity.getString(if (vaga) R.string.aut_mudanca_teste_vaga else R.string.aut_mudanca_teste), "info", 12))

        val linha = corpo.inflarFilho<LinearLayout>(R.layout.item_aut_condicao)
        val atendida = r.get("condicao_atendida")?.let { it.isJsonPrimitive && (it.asJsonPrimitive.isBoolean && it.asBoolean || it.asString == "t" || it.asString == "1") } == true
        linha.addView(if (atendida) activity.selo("✓ " + activity.getString(R.string.aut_atendida), R.color.success, R.color.success_bg)
        else activity.selo(activity.getString(R.string.aut_nao_atendida), R.color.warning, R.color.warning_bg))
        corpo.addView(linha)

        r.arr("passos").forEach { p ->
            val cartao = ItemAutPassoTesteBinding.inflate(activity.layoutInflater, corpo, false)
            val indice = (p.str("indice")?.toIntOrNull() ?: 0) + 1
            cartao.tvTitulo.text = activity.getString(R.string.aut_acao_n, indice, Automacoes.acaoLabel(p.str("tipo")))
            previa(p, cartao.root)
            corpo.addView(cartao.root)
        }
    }

    private fun par(destino: LinearLayout, rotulo: String, valor: CharSequence?, corValor: Int = R.color.ink) {
        destino.addView(activity.texto(rotulo, 12f, topo = 8))
        destino.addView(activity.texto(valor, 14f, corValor).apply { setTextIsSelectable(true) })
    }

    /** Previa do web, por tipo de ação. Texto puro: nunca renderiza HTML vindo dos dados. */
    private fun previa(p: JsonObject, destino: LinearLayout) {
        if (p.str("status") == "erro") return destino.addView(activity.alerta(p.str("mensagem").orEmpty(), "error", 10))
        val previa = p.get("previa")?.takeIf { it.isJsonObject }?.asJsonObject ?: return
        when (previa.str("tipo")) {
            "atualizar_campo" -> {
                val valor = previa.str("valor").orEmpty()
                destino.addView(activity.texto(
                    if (valor.isEmpty()) activity.getString(R.string.aut_previa_esvaziada, previa.str("campo"))
                    else activity.getString(R.string.aut_previa_passaria, previa.str("campo"), valor), 14f, R.color.ink, topo = 8))
            }
            "criar_registro" -> {
                destino.addView(activity.texto(activity.getString(R.string.aut_previa_novo_item, previa.str("modulo")), 14f, R.color.ink, topo = 8))
                previa.get("dados")?.takeIf { it.isJsonObject }?.asJsonObject?.entrySet()?.forEach { (campo, valor) ->
                    par(destino, campo, if (valor.isJsonPrimitive) valor.asString else valor.toString())
                }
            }
            "notificacao" -> {
                val n = previa.str("destinatarios")?.toDoubleOrNull()?.toInt() ?: 0
                par(destino, activity.getString(R.string.aut_previa_para), activity.resources.getQuantityString(R.plurals.aut_previa_pessoas, n, n))
                par(destino, activity.getString(R.string.aut_previa_titulo), previa.str("titulo"))
                previa.str("mensagem")?.takeIf { it.isNotEmpty() }?.let { par(destino, activity.getString(R.string.aut_previa_mensagem), it) }
            }
            "enviar_email" -> {
                par(destino, activity.getString(R.string.aut_previa_para), previa.str("destinatario"))
                val assunto = previa.str("assunto").orEmpty()
                par(destino, activity.getString(R.string.aut_previa_assunto), assunto.ifEmpty { activity.getString(R.string.aut_previa_vazio) },
                    if (assunto.isEmpty()) R.color.faint else R.color.ink)
                par(destino, activity.getString(R.string.aut_previa_mensagem), previa.str("corpo"))
            }
            else -> {
                par(destino, activity.getString(R.string.aut_previa_post), previa.str("url"))
                val payload: JsonElement? = previa.get("payload")
                par(destino, activity.getString(R.string.aut_previa_corpo), payload?.let { GsonBuilder().setPrettyPrinting().create().toJson(it) })
            }
        }
    }
}

/** Escolha de cor do selo do fluxo (gatilho = default, ação = primary). */
internal fun ChipGroup.adicionarSelo(texto: String, primario: Boolean) {
    addView(context.selo(texto, if (primario) R.color.primary else R.color.ink_soft, if (primario) R.color.primary_100 else R.color.sidebar))
}
