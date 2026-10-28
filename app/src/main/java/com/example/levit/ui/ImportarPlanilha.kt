package com.example.levit.ui

import android.net.Uri
import android.provider.OpenableColumns
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.example.levit.R
import com.example.levit.data.remote.ApiClient
import com.example.levit.data.remote.dto.ImportarRequest
import com.example.levit.data.remote.dto.ItemImportado
import com.example.levit.data.remote.dto.Pessoa
import com.example.levit.data.remote.dto.TabelaDetalhe
import com.example.levit.data.repository.Resultado
import com.example.levit.data.repository.chamarApi
import com.example.levit.databinding.ItemCampoSelectBinding
import com.example.levit.databinding.ItemImportarPreviaBinding
import com.example.levit.databinding.SheetImportarBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * ImportarPlanilha.jsx: escolher o arquivo, conferir de qual coluna da
 * planilha vem cada coluna da tabela (com prévia) e importar. Não dispara
 * automações.
 */
class ImportarPlanilha(
    private val activity: ComponentActivity,
    private val tabela: TabelaDetalhe,
    pessoas: List<Pessoa>,
    private val ligados: Map<String, Map<String, String?>>,
    private val aoImportar: () -> Unit
) {
    private val campos = tabela.campos.orEmpty()
    private val pessoas = pessoas.map { it.id to it.nome.orEmpty() }
    private val b = SheetImportarBinding.inflate(activity.layoutInflater)
    private val folha = Folha(activity, b.root)

    private var nomeArquivo = ""
    private var cabecalho: List<String> = emptyList()
    private var linhas: List<List<Any?>> = emptyList()
    private var mapa: MutableMap<String, Int> = mutableMapOf()
    private var erro: String? = null
    private var lendo = false
    private var importando = false
    private var resultado: Pair<Int, List<Pair<Int, String>>>? = null
    private var etapa = "arquivo"

    private val escolher = activity.activityResultRegistry.register(
        "importar-planilha-${System.nanoTime()}", ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let { ler(it) } }

    fun abrir() {
        folha.aoFechar { escolher.unregister() }
        desenhar()
        folha.abrir()
    }

    private val preparo get() = Importacao.prepararItens(linhas, campos, mapa, pessoas, ligados)

    private fun ler(uri: Uri) {
        erro = null
        lendo = true
        desenhar()
        activity.lifecycleScope.launch {
            try {
                val nome = activity.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
                    if (it.moveToFirst()) it.getString(0) else null
                } ?: "planilha"
                val bytes = withContext(Dispatchers.IO) { activity.contentResolver.openInputStream(uri)?.use { it.readBytes() } } ?: ByteArray(0)
                val matriz = withContext(Dispatchers.Default) {
                    when {
                        Regex("\\.xlsx$", RegexOption.IGNORE_CASE).containsMatchIn(nome) -> Importacao.lerXlsx(bytes)
                        Regex("\\.(csv|txt)$", RegexOption.IGNORE_CASE).containsMatchIn(nome) -> Importacao.lerCsv(Importacao.decodificarTexto(bytes))
                        else -> throw IllegalArgumentException(activity.getString(R.string.imp_formato_invalido))
                    }
                }
                if (matriz.size < 2) throw IllegalArgumentException(activity.getString(R.string.imp_sem_linhas))
                cabecalho = matriz.first().map { it?.toString()?.trim().orEmpty() }
                linhas = matriz.drop(1)
                nomeArquivo = nome
                mapa = Importacao.mapearColunas(cabecalho, campos)
                etapa = "conferir"
            } catch (e: Exception) {
                erro = e.message?.takeIf { e is IllegalArgumentException } ?: activity.getString(R.string.imp_erro_ler)
            }
            lendo = false
            desenhar()
        }
    }

    private fun importar() {
        importando = true
        erro = null
        desenhar()
        val p = preparo
        activity.lifecycleScope.launch {
            var criados = 0
            val erros = p.problemas.map { it.linha to it.mensagem }.toMutableList()
            for (lote in p.itens.chunked(MAX_POR_ENVIO)) {
                val r = chamarApi(activity.getString(R.string.imp_erro)) {
                    ApiClient.registroApi(activity).importar(tabela.id, ImportarRequest(lote.map { (linha, dados) -> ItemImportado(linha, dados) }))
                }
                when (r) {
                    is Resultado.Sucesso -> {
                        criados += r.dados.criados ?: 0
                        erros += r.dados.erros.orEmpty().map { (it.linha ?: 0) to it.mensagem.orEmpty() }
                    }
                    is Resultado.Erro -> {
                        erro = r.mensagem
                        importando = false
                        return@launch desenhar()
                    }
                }
            }
            resultado = criados to erros.sortedBy { it.first }
            etapa = "resultado"
            importando = false
            aoImportar()
            desenhar()
        }
    }

    // --- Tela ---------------------------------------------------------------------

    init {
        b.tvTitulo.text = activity.getString(R.string.imp_titulo, tabela.nome)
        b.caixaArquivo.setOnClickListener { escolher.launch(TIPOS) }
        b.btnCancelar.setOnClickListener { folha.dispensar() }
        b.btnTrocar.setOnClickListener { etapa = "arquivo"; desenhar() }
        b.btnImportar.setOnClickListener { importar() }
        b.btnConcluir.setOnClickListener { folha.dispensar() }
    }

    private fun desenhar() {
        b.tvErro.text = erro
        b.tvErro.isVisible = erro != null
        b.blocoArquivo.isVisible = etapa == "arquivo"
        b.blocoConferir.isVisible = etapa == "conferir"
        b.blocoResultado.isVisible = etapa != "arquivo" && etapa != "conferir" && resultado != null
        when (etapa) {
            "arquivo" -> desenharArquivo()
            "conferir" -> desenharConferir()
            else -> desenharResultado()
        }
    }

    private fun desenharArquivo() {
        b.tvEscolher.text = activity.getString(if (lendo) R.string.imp_lendo else R.string.imp_escolher)
    }

    private fun desenharConferir() {
        val p = preparo
        b.tvResumo.text = activity.getString(R.string.imp_resumo, nomeArquivo,
            activity.resources.getQuantityString(R.plurals.imp_linhas, linhas.size, linhas.size))

        val opcoes = listOf("-1" to activity.getString(R.string.imp_nao_importar)) +
            cabecalho.mapIndexed { i, t -> "$i" to t.ifEmpty { activity.getString(R.string.imp_coluna_n, i + 1) } }
        b.llMapa.removeAllViews()
        campos.forEach { campo ->
            val s = ItemCampoSelectBinding.inflate(activity.layoutInflater)
            s.campo.hint = campo.nome
            s.acValor.configurarSelect(opcoes, "${mapa[campo.id.orEmpty()] ?: -1}") {
                mapa[campo.id.orEmpty()] = it.toInt()
                desenhar()
            }
            b.llMapa.addView(s.root)
        }

        val selos = b.grupoSelos
        selos.removeAllViews()
        selos.addView(activity.selo(activity.getString(R.string.imp_prontas, p.itens.size), R.color.success, R.color.success_bg))
        if (p.problemas.isNotEmpty()) selos.addView(activity.selo(activity.getString(R.string.imp_com_problema, p.problemas.size), R.color.danger, R.color.danger_bg))
        if (mapa.values.none { it >= 0 }) selos.addView(activity.selo(activity.getString(R.string.imp_nenhuma_coluna), R.color.warning, R.color.warning_bg))

        // Prévia das 5 primeiras linhas, uma por cartão.
        val usadas = campos.filter { (mapa[it.id.orEmpty()] ?: -1) >= 0 }
        b.tvPrevia.isVisible = usadas.isNotEmpty()
        b.llPrevia.removeAllViews()
        if (usadas.isNotEmpty()) {
            linhas.take(PREVIA).forEachIndexed { i, celulas ->
                val problema = p.problemas.firstOrNull { it.linha == i + 2 }
                val cartao = ItemImportarPreviaBinding.inflate(activity.layoutInflater, b.llPrevia, false)
                if (problema != null) cartao.root.setBackgroundResource(R.drawable.bg_alerta_erro)
                cartao.tvLinha.text = activity.getString(R.string.imp_linha_n, i + 2)
                usadas.forEach { c ->
                    val bruto = celulas.getOrNull(mapa[c.id.orEmpty()] ?: -1)
                    val mostrado = when (bruto) {
                        is Importacao.DataExcel -> Datas.dataCurta(bruto.iso)
                        is Double -> if (bruto % 1.0 == 0.0) bruto.toLong().toString() else bruto.toString()
                        null -> ""
                        else -> bruto.toString()
                    }
                    cartao.llCelulas.addView(cartao.llCelulas.inflarFilho<TextView>(R.layout.item_importar_celula).apply {
                        text = activity.getString(R.string.imp_celula, c.nome, mostrado)
                    })
                }
                b.llPrevia.addView(cartao.root)
            }
        }
        b.tvNaoVaoEntrar.isVisible = p.problemas.isNotEmpty()
        listarProblemas(b.llProblemas, p.problemas.take(50).map { it.linha to it.mensagem })

        b.btnImportar.text = if (importando) activity.getString(R.string.imp_importando)
            else activity.resources.getQuantityString(R.plurals.imp_importar_n, p.itens.size, p.itens.size)
        b.btnImportar.isEnabled = !importando && p.itens.isNotEmpty()
    }

    private fun listarProblemas(destino: LinearLayout, problemas: List<Pair<Int, String>>) {
        destino.removeAllViews()
        problemas.forEach { (linha, msg) ->
            destino.addView(destino.inflarFilho<TextView>(R.layout.item_importar_problema).apply {
                text = activity.getString(R.string.imp_problema, linha, msg)
            })
        }
    }

    private fun desenharResultado() {
        val (criados, erros) = resultado ?: return
        b.tvResultado.text = if (criados > 0) activity.resources.getQuantityString(R.plurals.imp_importados, criados, criados, tabela.nome)
            else activity.getString(R.string.imp_nenhum_importado)
        b.tvResultado.setTextColor(activity.cor(if (criados > 0) R.color.success else R.color.warning))
        b.tvResultado.setBackgroundResource(if (criados > 0) R.drawable.bg_alerta_sucesso else R.drawable.bg_alerta_aviso)
        b.tvFicaramDeFora.isVisible = erros.isNotEmpty()
        b.tvFicaramDeFora.text = activity.resources.getQuantityString(R.plurals.imp_ficaram_de_fora, erros.size, erros.size)
        listarProblemas(b.llErros, erros.take(100))
    }

    companion object {
        private const val MAX_POR_ENVIO = 1000
        private const val PREVIA = 5
        private val TIPOS = arrayOf(
            "text/csv", "text/comma-separated-values", "text/plain", "application/csv",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "application/octet-stream"
        )
    }
}
