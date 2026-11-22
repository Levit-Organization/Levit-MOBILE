package com.example.levit.ui

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.isVisible
import com.example.levit.R
import com.example.levit.data.remote.dto.Comparacao
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import java.io.File
import kotlin.math.abs
import kotlin.math.max

/*
 * Peças visuais repetidas nas telas, equivalentes aos componentes de
 * client-frontend/src/components/ui (Avatar, Badge, EmptyState, Indicador,
 * BarrasHorizontais). As cores saem sempre dos tokens de colors.xml.
 */

fun Context.dp(valor: Int): Int = (valor * resources.displayMetrics.density).toInt()

fun Context.cor(@ColorRes id: Int): Int = ContextCompat.getColor(this, id)

fun Context.avisar(mensagem: CharSequence, longo: Boolean = false) =
    Toast.makeText(this, mensagem, if (longo) Toast.LENGTH_LONG else Toast.LENGTH_SHORT).show()

private val CORES_PESSOA = listOf(
    R.color.people_1 to R.color.people_1_bg,
    R.color.people_2 to R.color.people_2_bg,
    R.color.people_3 to R.color.people_3_bg,
    R.color.people_4 to R.color.people_4_bg,
    R.color.people_5 to R.color.people_5_bg,
    R.color.people_6 to R.color.people_6_bg,
)

private val CORES_ETAPA = listOf(
    R.color.stage_1 to R.color.stage_1_bg,
    R.color.stage_2 to R.color.stage_2_bg,
    R.color.stage_3 to R.color.stage_3_bg,
    R.color.stage_4 to R.color.stage_4_bg,
    R.color.stage_5 to R.color.stage_5_bg,
)

/** (texto, fundo) da etapa pela posição no quadro. */
fun coresDaEtapa(indice: Int, total: Int): Pair<Int, Int> = CORES_ETAPA[Funil.cor(indice, total)]

/** Avatar.jsx: círculo com as iniciais na cor estável da pessoa. */
fun TextView.mostrarAvatar(nome: String?) {
    val (texto, fundo) = CORES_PESSOA[Pessoas.indiceDaCor(nome)]
    text = Pessoas.iniciais(nome)
    setTextColor(context.cor(texto))
    setBackgroundResource(R.drawable.bg_bolinha)
    backgroundTintList = ColorStateList.valueOf(context.cor(fundo))
}

/** Infla um layout pequeno (peça de tela) sem pai. */
@Suppress("UNCHECKED_CAST")
fun <T : View> Context.inflar(layout: Int): T = LayoutInflater.from(this).inflate(layout, null, false) as T

/** Infla dentro de um pai (para os LayoutParams do XML valerem) sem adicionar. */
@Suppress("UNCHECKED_CAST")
fun <T : View> ViewGroup.inflarFilho(layout: Int): T = LayoutInflater.from(context).inflate(layout, this, false) as T

/** Badge.jsx: pílula pequena, com ponto opcional. */
fun Context.selo(texto: String, @ColorRes corTexto: Int, @ColorRes corFundo: Int, ponto: Boolean = false): TextView =
    inflar<TextView>(R.layout.item_selo).apply {
        text = if (ponto) "●  $texto" else texto
        setTextColor(cor(corTexto))
        backgroundTintList = ColorStateList.valueOf(cor(corFundo))
        layoutParams = ViewGroup.MarginLayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { marginEnd = dp(6) }
    }

/**
 * EmptyState / ErrorState: o vazio ensina o próximo passo (DESIGN.md).
 * Substitui o conteúdo do container.
 */
fun ViewGroup.mostrarEstado(
    titulo: String,
    descricao: String? = null,
    @DrawableRes icone: Int = R.drawable.ic_grid_view,
    acao: String? = null,
    erro: Boolean = false,
    aoAgir: (() -> Unit)? = null
) {
    removeAllViews()
    val v = LayoutInflater.from(context).inflate(R.layout.item_estado, this, false)
    v.findViewById<ImageView>(R.id.ivEstadoIcone).apply {
        setImageResource(icone)
        imageTintList = ColorStateList.valueOf(context.cor(if (erro) R.color.danger else R.color.primary))
        setBackgroundResource(R.drawable.bg_bolinha)
        backgroundTintList = ColorStateList.valueOf(context.cor(if (erro) R.color.danger_bg else R.color.primary_100))
    }
    v.findViewById<TextView>(R.id.tvEstadoTitulo).text = titulo
    v.findViewById<TextView>(R.id.tvEstadoDescricao).apply {
        text = descricao
        isVisible = !descricao.isNullOrBlank()
    }
    v.findViewById<MaterialButton>(R.id.btnEstadoAcao).apply {
        isVisible = acao != null && aoAgir != null
        text = acao
        setOnClickListener { aoAgir?.invoke() }
    }
    addView(v)
}

/** Skeleton simples: blocos cinza com a forma do conteúdo, no lugar de spinner (DESIGN.md). */
fun ViewGroup.mostrarCarregando(cartoes: Int = 3) {
    removeAllViews()
    repeat(cartoes) { addView(inflarFilho<View>(R.layout.item_esqueleto)) }
}

/** Select do web: campo com lista suspensa. opcoes = (valor, rótulo). */
fun MaterialAutoCompleteTextView.configurarSelect(
    opcoes: List<Pair<String, String>>,
    selecionado: String?,
    aoEscolher: (String) -> Unit
) {
    setAdapter(ArrayAdapter(context, R.layout.item_opcao, opcoes.map { it.second }))
    setText(opcoes.firstOrNull { it.first == selecionado }?.second.orEmpty(), false)
    setOnItemClickListener { _, _, posicao, _ -> aoEscolher(opcoes[posicao].first) }
}

/** useConfirm do web: diálogo de confirmação, com o botão em vermelho quando destrói algo. */
fun Context.confirmar(
    titulo: String,
    mensagem: String,
    rotuloConfirmar: String,
    rotuloCancelar: String = "Cancelar",
    perigo: Boolean = true,
    aoConfirmar: () -> Unit
) {
    val dialogo = MaterialAlertDialogBuilder(this)
        .setTitle(titulo)
        .setMessage(mensagem)
        .setNegativeButton(rotuloCancelar, null)
        .setPositiveButton(rotuloConfirmar) { _, _ -> aoConfirmar() }
        .show()
    if (perigo) dialogo.getButton(android.content.DialogInterface.BUTTON_POSITIVE).setTextColor(cor(R.color.danger))
}

/** Escolha de uma opção numa lista (o seletor de etapa do cartão, no web). */
fun Context.escolher(titulo: String, opcoes: List<Pair<String, String>>, atual: String?, aoEscolher: (String) -> Unit) {
    val marcado = opcoes.indexOfFirst { it.first == atual }
    MaterialAlertDialogBuilder(this)
        .setTitle(titulo)
        .setSingleChoiceItems(opcoes.map { it.second }.toTypedArray(), marcado) { dialogo, i ->
            dialogo.dismiss()
            if (opcoes[i].first != atual) aoEscolher(opcoes[i].first)
        }
        .setNegativeButton("Cancelar", null)
        .show()
}

/**
 * Abre um arquivo baixado da API (PDF, currículo) no app que a pessoa tiver.
 * Fica no cache do app, servido por FileProvider: nada vai para o
 * armazenamento público nem exige permissão de arquivos.
 */
fun Activity.abrirArquivo(bytes: ByteArray, nome: String, mime: String) {
    val pasta = File(cacheDir, "compartilhados").apply { mkdirs() }
    // Nome vindo da API: só o que for seguro como nome de arquivo.
    val seguro = nome.replace(Regex("[^\\p{L}\\p{N}._ -]"), "_").take(100).ifBlank { "arquivo" }
    val arquivo = File(pasta, seguro).apply { writeBytes(bytes) }
    val uri = FileProvider.getUriForFile(this, "$packageName.arquivos", arquivo)
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, mime)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    try {
        startActivity(Intent.createChooser(intent, nome))
    } catch (e: ActivityNotFoundException) {
        avisar("Nenhum app neste aparelho abre esse tipo de arquivo.", longo = true)
    }
}

/** Indicador de Graficos.jsx: rótulo, número grande, variação e "antes: X". */
fun ViewGroup.adicionarIndicador(
    rotulo: String,
    comparacao: Comparacao?,
    formatar: (Double?) -> String = Relatorios::numero,
    subirEBom: Boolean = true,
    dica: String? = null
) {
    val v = LayoutInflater.from(context).inflate(R.layout.item_indicador, this, false)
    v.findViewById<TextView>(R.id.tvIndicadorRotulo).text = rotulo
    v.findViewById<TextView>(R.id.tvIndicadorValor).text = formatar(comparacao?.atual ?: 0.0)
    val variacao = Relatorios.variacao(comparacao, subirEBom)
    v.findViewById<TextView>(R.id.tvIndicadorVariacao).apply {
        isVisible = variacao != null
        text = when (variacao?.subiu) {
            true -> "↗ ${variacao.texto}"
            false -> "↘ ${variacao.texto}"
            null -> variacao?.texto
        }
        setTextColor(context.cor(when (variacao?.bom) {
            true -> R.color.success
            false -> R.color.danger
            null -> R.color.light_text
        }))
    }
    v.findViewById<TextView>(R.id.tvIndicadorDica).text = dica ?: "antes: ${formatar(comparacao?.anterior ?: 0.0)}"
    addView(v)
}

data class Barra(val rotulo: String, val valor: Double, val detalhe: String? = null, val extra: String? = null)

/** BarrasHorizontais de Graficos.jsx: valor escrito na ponta, detalhe embaixo do rótulo. */
fun ViewGroup.adicionarBarras(itens: List<Barra>, formatar: (Double?) -> String = Relatorios::numero, vazio: String) {
    if (itens.isEmpty()) {
        addView(inflarFilho<TextView>(R.layout.item_barras_vazio).apply { text = vazio })
        return
    }
    val maximo = itens.maxOf { abs(it.valor) }.takeIf { it > 0 } ?: 1.0
    itens.forEach { item ->
        val linha = LayoutInflater.from(context).inflate(R.layout.item_barra, this, false)
        linha.findViewById<TextView>(R.id.tvBarraRotulo).text = item.rotulo
        linha.findViewById<TextView>(R.id.tvBarraDetalhe).apply {
            text = item.detalhe
            isVisible = !item.detalhe.isNullOrBlank()
        }
        linha.findViewById<TextView>(R.id.tvBarraValor).text =
            listOfNotNull(formatar(item.valor), item.extra).joinToString("  ")
        val proporcao = if (item.valor == 0.0) 0f else max(abs(item.valor) / maximo, 0.015).toFloat()
        (linha.findViewById<View>(R.id.vBarra).layoutParams as LinearLayout.LayoutParams).weight = proporcao
        (linha.findViewById<View>(R.id.vBarraResto).layoutParams as LinearLayout.LayoutParams).weight = 1f - proporcao
        addView(linha)
    }
}

/** Título de seção dentro de um painel (h2/h3 do web). */
fun ViewGroup.adicionarTitulo(texto: String, descricao: String? = null, tamanho: Float = 16f, topo: Int = 0) {
    addView(inflarFilho<TextView>(R.layout.item_titulo_secao).apply {
        text = texto
        textSize = tamanho
        setPadding(0, context.dp(topo), 0, 0)
    })
    if (!descricao.isNullOrBlank()) addView(inflarFilho<TextView>(R.layout.item_descricao_secao).apply { text = descricao })
}

fun ViewGroup.adicionarTexto(texto: String, @ColorRes corTexto: Int = R.color.light_text, tamanho: Float = 12f, topo: Int = 4) {
    addView(inflarFilho<TextView>(R.layout.item_texto).apply {
        text = texto
        textSize = tamanho
        setTextColor(context.cor(corTexto))
        setPadding(0, context.dp(topo), 0, 0)
    })
}

/** Caixa quadrada com o ícone da tabela (bg-primary-100 text-primary rounded-lg). */
fun Context.caixaDeIcone(nome: String?, ladoDp: Int = 44, iconeDp: Int = 20, ativo: Boolean = false): TextView =
    inflar<TextView>(R.layout.item_caixa_icone).apply {
        text = nome?.takeIf { it.isNotBlank() } ?: "extension"
        textSize = iconeDp.toFloat()
        setTextColor(cor(if (ativo) R.color.white else R.color.primary))
        backgroundTintList = ColorStateList.valueOf(cor(if (ativo) R.color.primary else R.color.primary_100))
        layoutParams = LinearLayout.LayoutParams(dp(ladoDp), dp(ladoDp))
    }

/** SeloPrazo.jsx: cor pela urgência (atrasado, vence hoje, nos próximos dias) ou só a data. */
fun Context.seloPrazo(dataIso: String?, concluido: Boolean): TextView? {
    val s = Prazos.situacao(dataIso, concluido = concluido) ?: return null
    val (texto, fundo, icone) = when (s.tipo) {
        Prazos.Tipo.ATRASADO -> Triple(R.color.danger, R.color.danger_bg, "error")
        Prazos.Tipo.HOJE -> Triple(R.color.warning, R.color.warning_bg, "schedule")
        Prazos.Tipo.BREVE -> Triple(R.color.info, R.color.info_bg, "event")
        Prazos.Tipo.FUTURO -> Triple(R.color.ink_soft, R.color.sidebar, "event")
        Prazos.Tipo.CONCLUIDO -> Triple(R.color.ink_soft, R.color.sidebar, "event_available")
    }
    val icones = ResourcesCompat.getFont(this, R.font.material_icons)
    return selo(" ${s.rotulo}", texto, fundo).apply {
        text = android.text.SpannableStringBuilder(icone).apply {
            if (icones != null) setSpan(TypefaceSpanCompat(icones), 0, length, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            append(" ").append(s.rotulo)
        }
    }
}

/** Span com uma fonte própria (TypefaceSpan(Typeface) só existe a partir da API 28). */
private class TypefaceSpanCompat(private val fonte: android.graphics.Typeface) : android.text.style.MetricAffectingSpan() {
    override fun updateDrawState(tp: android.text.TextPaint) { tp.typeface = fonte }
    override fun updateMeasureState(tp: android.text.TextPaint) { tp.typeface = fonte }
}

/** PessoaResponsavel.jsx: iniciais num círculo e, opcionalmente, o nome. */
fun Context.pessoaResponsavel(nome: String?, comNome: Boolean = true): View {
    if (nome.isNullOrBlank()) return inflar(R.layout.item_sem_responsavel)
    val b = com.example.levit.databinding.ItemPessoaResponsavelBinding.inflate(LayoutInflater.from(this))
    b.root.contentDescription = nome
    b.tvIniciais.text = Pessoas.iniciais(nome)
    b.tvNome.text = nome
    b.tvNome.isVisible = comNome
    return b.root
}

/** Texto simples com os tokens do tema. */
fun Context.texto(
    t: CharSequence?,
    tamanho: Float = 14f,
    @ColorRes corTexto: Int = R.color.light_text,
    fonte: Int? = null,
    topo: Int = 0
): TextView = inflar<TextView>(R.layout.item_texto).apply {
    text = t
    textSize = tamanho
    setTextColor(cor(corTexto))
    fonte?.let { typeface = ResourcesCompat.getFont(context, it) }
    setPadding(0, dp(topo), 0, 0)
}

/** Alert.jsx: error, warning, info ou success. */
fun Context.alerta(t: CharSequence, tipo: String = "error", topo: Int = 12): TextView = inflar<TextView>(R.layout.item_alerta).apply {
    text = t
    val (corTexto, fundo) = when (tipo) {
        "warning" -> R.color.warning to R.drawable.bg_alerta_aviso
        "info" -> R.color.info to R.drawable.bg_alerta_info
        "success" -> R.color.success to R.drawable.bg_alerta_sucesso
        else -> R.color.danger to R.drawable.bg_alerta_erro
    }
    setTextColor(cor(corTexto))
    setBackgroundResource(fundo)
    setPadding(dp(12), dp(10), dp(12), dp(10))
    layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(topo) }
}

/** divide-y divide-divider */
fun Context.divisoria(margemVertical: Int = 0): View = inflar<View>(R.layout.item_divisoria).apply {
    layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1)).apply {
        topMargin = dp(margemVertical); bottomMargin = dp(margemVertical)
    }
}

/** Button.jsx criado por código: primary, secondary ou ghost. */
fun Context.botao(t: String, variante: String = "secondary", @DrawableRes icone: Int? = null, aoClicar: (() -> Unit)? = null): MaterialButton =
    inflar<MaterialButton>(when (variante) {
        "primary" -> R.layout.item_botao_primario
        "ghost" -> R.layout.item_botao_fantasma
        else -> R.layout.item_botao_secundario
    }).apply {
        text = t
        icone?.let { setIconResource(it) }
        aoClicar?.let { a -> setOnClickListener { a() } }
    }

/** Corpo rolável para uma Folha montada por código. */
fun Context.corpoDeFolha(): Pair<androidx.core.widget.NestedScrollView, LinearLayout> {
    val b = com.example.levit.databinding.SheetCorpoBinding.inflate(LayoutInflater.from(this))
    return b.root to b.corpo
}

/** Título + subtítulo de Folha (Modal/Drawer do web). */
fun ViewGroup.tituloDeFolha(titulo: String, subtitulo: String? = null) {
    addView(inflarFilho<TextView>(R.layout.item_folha_titulo).apply { text = titulo })
    if (!subtitulo.isNullOrBlank()) addView(inflarFilho<TextView>(R.layout.item_folha_subtitulo).apply { text = subtitulo })
}
