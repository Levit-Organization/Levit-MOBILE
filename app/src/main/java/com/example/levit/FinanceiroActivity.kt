package com.example.levit

import android.app.DatePickerDialog
import android.os.Bundle
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import com.example.levit.data.remote.ApiClient
import com.example.levit.data.remote.dto.Categoria
import com.example.levit.data.remote.dto.Lancamento
import com.example.levit.data.remote.dto.NovaCategoria
import com.example.levit.data.remote.dto.NovoLancamento
import com.example.levit.data.repository.Resultado
import com.example.levit.data.repository.chamarApi
import com.example.levit.data.repository.executarApi
import com.example.levit.databinding.ActivityFinanceiroBinding
import com.example.levit.databinding.ItemEstadoListaBinding
import com.example.levit.databinding.ItemFinanceiroTopoBinding
import com.example.levit.databinding.ItemLancamentoBinding
import com.example.levit.databinding.ItemMaisLancamentosBinding
import com.example.levit.databinding.ItemMesFinanceiroBinding
import com.example.levit.ui.AdaptadorDeLinhas
import com.example.levit.ui.CabecalhoFixo
import com.example.levit.ui.LinhaDeLista
import com.example.levit.ui.pedacoDeCartao
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import androidx.recyclerview.widget.ConcatAdapter
import com.example.levit.databinding.SheetLancamentoBinding
import com.example.levit.ui.Datas
import com.example.levit.ui.Folha
import com.example.levit.ui.Relatorios
import com.example.levit.ui.avisar
import com.example.levit.ui.caixaDeIcone
import com.example.levit.ui.configurarSelect
import com.example.levit.ui.cor
import com.example.levit.ui.mostrarCarregando
import com.example.levit.ui.mostrarEstado
import com.example.levit.ui.selo
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import java.util.Calendar

/**
 * Tabela de entradas e saídas (FinancialModule.jsx): totais do ano filtrado,
 * filtros por categoria, ano e busca, e os lançamentos agrupados por mês.
 */
class FinanceiroActivity : HamburgerMenuBaseActivity() {

    private lateinit var binding: ActivityFinanceiroBinding
    private val api by lazy { ApiClient.financeiroApi(applicationContext) }
    private val moduloApi by lazy { ApiClient.moduloApi(applicationContext) }
    private val tabelaId by lazy { intent.getStringExtra(EXTRA_ID).orEmpty() }

    private var lancamentos: List<Lancamento>? = null
    private var categorias: List<Categoria> = emptyList()
    private var erro: Resultado.Erro? = null
    private val anoAtual = Calendar.getInstance().get(Calendar.YEAR).toString()
    private var ano = anoAtual
    private var categoria = "all"
    private var busca = ""
    private val abertos = mutableMapOf<String, Boolean>()
    private val limites = mutableMapOf<String, Int>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityFinanceiroBinding.inflate(layoutInflater)
        setContentViewWithHamburgerMenu(binding.root)
        if (!menuPronto()) return
        binding.btnVoltar.setOnClickListener { finish() }
        binding.btnNovo.setOnClickListener { abrirFormulario() }
        configurarLista()
        binding.flIcone.addView(caixaDeIcone("account_balance_wallet", ladoDp = 40))
        lifecycleScope.launch {
            (chamarApi(getString(R.string.fin_padrao)) { moduloApi.detalhes(tabelaId) } as? Resultado.Sucesso)?.let { r ->
                binding.tvTitulo.text = r.dados.nome
                binding.flIcone.removeAllViews()
                binding.flIcone.addView(caixaDeIcone(r.dados.icone ?: "account_balance_wallet", ladoDp = 40))
            } ?: run { binding.tvTitulo.setText(R.string.fin_padrao) }
        }
    }

    override fun onResume() {
        super.onResume()
        if (menuPronto()) carregar()
    }

    /** Categorias + lançamentos; as categorias padrão são criadas na primeira visita (como no web). */
    private fun carregar() {
        if (lancamentos == null) renderizar()
        lifecycleScope.launch {
            val (cats, lanc) = coroutineScope {
                val c = async { chamarApi(getString(R.string.fin_erro_carregar)) { api.categorias() } }
                val l = async { chamarApi(getString(R.string.fin_erro_carregar)) { api.lancamentos(tabelaId) } }
                c.await() to l.await()
            }
            when (lanc) {
                is Resultado.Sucesso -> { lancamentos = lanc.dados.lancamentos.orEmpty(); erro = null }
                is Resultado.Erro -> { if (lanc.sessaoExpirada) return@launch redirecionarParaLogin(); erro = lanc }
            }
            var lista = (cats as? Resultado.Sucesso)?.dados.orEmpty()
            val faltando = CATEGORIAS_PADRAO.filter { nome -> lista.none { it.nome.equals(nome, ignoreCase = true) } }
            if (cats is Resultado.Sucesso && faltando.isNotEmpty()) {
                val criadas = coroutineScope {
                    faltando.map { nome -> async { executarApi("") { api.criarCategoria(NovaCategoria(nome)) } } }.awaitAll()
                }
                if (criadas.any { it is Resultado.Sucesso }) {
                    (chamarApi("") { api.categorias() } as? Resultado.Sucesso)?.let { lista = it.dados }
                }
            }
            categorias = lista
            renderizar()
        }
    }

    /** As 4 categorias padrão, com o id da que já existe na empresa (como o select do web). */
    private fun opcoesDeCategoria() = CATEGORIAS_PADRAO.map { nome ->
        (categorias.firstOrNull { it.nome.equals(nome, ignoreCase = true) }?.id ?: nome) to nome
    }

    data class Totais(val entradas: Double, val saidas: Double) { val saldo get() = entradas - saidas }

    private fun somar(lista: List<Lancamento>) = Totais(
        lista.filter { it.tipo == "entrada" }.sumOf { it.valorNumero },
        lista.filter { it.tipo == "saida" }.sumOf { it.valorNumero },
    )

    // --- Lista: cabeçalho fixo (resumo + filtros) e linhas recicladas ----------------

    /** Linhas da lista: cabeçalho de mês, lançamento, "mostrar mais" ou um estado. */
    private sealed interface Linha : LinhaDeLista {
        data class Mes(override val chave: String, val nome: String, val quantidade: Int, val totais: Totais, val aberto: Boolean) : Linha {
            override val tipo get() = TIPO_MES
        }
        data class Lanc(val l: Lancamento, val ultimo: Boolean) : Linha {
            override val chave get() = l.id
            override val tipo get() = TIPO_LANC
        }
        data class Mais(override val chave: String, val restantes: Int) : Linha {
            override val tipo get() = TIPO_MAIS
        }
        data class Estado(val titulo: String, val descricao: String?, val acao: String?, val erro: Boolean, val carregando: Boolean = false) : Linha {
            override val chave get() = "estado"
            override val tipo get() = TIPO_ESTADO
        }
    }

    private val adaptador = AdaptadorDeLinhas<Linha>(
        criar = { pai, tipo ->
            when (tipo) {
                TIPO_MES -> ItemMesFinanceiroBinding.inflate(layoutInflater, pai, false)
                TIPO_LANC -> ItemLancamentoBinding.inflate(layoutInflater, pai, false)
                TIPO_MAIS -> ItemMaisLancamentosBinding.inflate(layoutInflater, pai, false)
                else -> ItemEstadoListaBinding.inflate(layoutInflater, pai, false)
            }
        },
        ligar = { b, linha ->
            when (linha) {
                is Linha.Mes -> ligarMes(b as ItemMesFinanceiroBinding, linha)
                is Linha.Lanc -> ligarLancamento(b as ItemLancamentoBinding, linha)
                is Linha.Mais -> (b as ItemMaisLancamentosBinding).btnMais.apply {
                    text = getString(R.string.fin_mostrar_mais, minOf(linha.restantes, LINHAS_POR_LOTE), linha.restantes)
                    setOnClickListener { limites[linha.chave] = (limites[linha.chave] ?: LINHAS_POR_LOTE) + LINHAS_POR_LOTE; renderizar() }
                }
                is Linha.Estado -> (b as ItemEstadoListaBinding).conteudo.let { c ->
                    if (linha.carregando) c.mostrarCarregando(3)
                    else c.mostrarEstado(linha.titulo, linha.descricao, if (linha.erro) R.drawable.ic_warning else R.drawable.ic_payments,
                        linha.acao, erro = linha.erro) { if (linha.erro) carregar() else abrirFormulario() }
                }
            }
        }
    )

    private lateinit var topo: ItemFinanceiroTopoBinding

    private fun configurarLista() {
        topo = ItemFinanceiroTopoBinding.inflate(layoutInflater, binding.lista, false)
        topo.etBusca.doAfterTextChanged { busca = it?.toString().orEmpty(); renderizar() }
        binding.lista.adapter = ConcatAdapter(CabecalhoFixo(topo.root), adaptador)
    }

    private fun renderizar() {
        val todos = lancamentos
        val anos = (todos.orEmpty().mapNotNull { it.dataLancamento?.take(4) } + anoAtual).distinct().sortedDescending()
        topo.acAno.configurarSelect(anos.map { it to it }, ano) { ano = it; renderizar() }
        topo.acCategoria.configurarSelect(listOf("all" to getString(R.string.fin_todas_categorias)) + opcoesDeCategoria(), categoria) {
            categoria = it
            renderizar()
        }

        val termo = busca.trim().lowercase()
        val visiveis = todos.orEmpty().filter { l ->
            l.dataLancamento?.take(4) == ano &&
                (categoria == "all" || l.categoriaId == categoria) &&
                (termo.isEmpty() || listOf(l.descricao, l.categoriaNome, l.tipo).any { it?.lowercase()?.contains(termo) == true })
        }.sortedWith(compareByDescending<Lancamento> { it.dataLancamento }.thenByDescending { it.criadoEm })
        val totais = somar(visiveis)

        // SummaryCard: entradas, saídas e saldo.
        listOf(
            Triple(topo.resEntradas, R.string.fin_entradas to totais.entradas, R.color.success),
            Triple(topo.resSaidas, R.string.fin_saidas to totais.saidas, R.color.danger),
            Triple(topo.resSaldo, R.string.fin_saldo to totais.saldo, R.color.ink),
        ).forEach { (b, rotuloValor, corValor) ->
            b.tvRotulo.setText(rotuloValor.first)
            b.tvValor.text = Relatorios.moeda(rotuloValor.second)
            b.tvValor.setTextColor(cor(corValor))
        }

        val e = erro
        val linhas: List<Linha> = when {
            todos == null && e == null -> listOf(Linha.Estado("", null, null, erro = false, carregando = true))
            e != null && todos == null -> listOf(Linha.Estado(getString(R.string.fin_erro_carregar), e.mensagem, getString(R.string.tab_tentar), erro = true))
            else -> {
                val meses = resources.getStringArray(R.array.fin_meses)
                val porMes = visiveis.groupBy { it.dataLancamento?.substring(5, 7) }
                val comLancamento = (1..12).map { "%02d".format(it) }.filter { porMes[it].orEmpty().isNotEmpty() }
                if (comLancamento.isEmpty()) {
                    val filtrando = categoria != "all" || termo.isNotEmpty()
                    listOf(Linha.Estado(
                        titulo = if (filtrando) getString(R.string.fin_nenhum_encontrado) else getString(R.string.fin_nenhum_em, ano),
                        descricao = getString(if (filtrando) R.string.fin_tente_outra else R.string.fin_registre),
                        acao = if (filtrando) null else getString(R.string.fin_novo),
                        erro = false
                    ))
                } else comLancamento.flatMap { mes -> linhasDoMes(meses[mes.toInt() - 1], porMes.getValue(mes)) }
            }
        }
        adaptador.submitList(linhas)
    }

    /** MonthCard: cabeçalho que abre e fecha e, aberto, até 50 lançamentos por vez. */
    private fun linhasDoMes(nomeMes: String, lista: List<Lancamento>): List<Linha> {
        val chave = "$ano-$nomeMes"
        val aberto = abertos[chave] ?: true
        val limite = limites[chave] ?: LINHAS_POR_LOTE
        val linhas = mutableListOf<Linha>(Linha.Mes(chave, nomeMes, lista.size, somar(lista), aberto))
        if (!aberto) return linhas
        val mostrados = lista.take(limite)
        val restantes = lista.size - mostrados.size
        mostrados.forEachIndexed { i, l -> linhas += Linha.Lanc(l, ultimo = restantes == 0 && i == mostrados.lastIndex) }
        if (restantes > 0) linhas += Linha.Mais(chave, restantes)
        return linhas
    }

    private fun ligarMes(b: ItemMesFinanceiroBinding, m: Linha.Mes) {
        b.root.pedacoDeCartao(primeiro = true, ultimo = !m.aberto)
        b.root.setOnClickListener { abertos[m.chave] = !m.aberto; renderizar() }
        b.tvSeta.text = if (m.aberto) "expand_more" else "chevron_right"
        b.tvMes.text = getString(R.string.fin_mes_ano, m.nome, ano)
        b.tvQuantidade.text = resources.getQuantityString(R.plurals.fin_n_lancamentos, m.quantidade, m.quantidade)
        val t = m.totais
        val saldoPositivo = t.saldo >= 0
        b.tvTotais.text = SpannableStringBuilder().apply {
            fun parte(texto: String, corParte: Int) {
                val i = length
                append(texto)
                setSpan(ForegroundColorSpan(cor(corParte)), i, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            parte("+${Relatorios.moeda(t.entradas)}", R.color.success)
            parte("  |  ", R.color.faint)
            parte("-${Relatorios.moeda(t.saidas)}", R.color.danger)
            parte("  |  ", R.color.faint)
            parte("${if (saldoPositivo) "+" else ""}${Relatorios.moeda(t.saldo)}", if (saldoPositivo) R.color.success else R.color.danger)
        }
    }

    /** Uma linha da tabela do web (Data, Descrição, Tipo, Categoria, Valor, Saldo acum., Conciliado). */
    private fun ligarLancamento(b: ItemLancamentoBinding, linha: Linha.Lanc) {
        val l = linha.l
        val entrada = l.tipo == "entrada"
        b.root.pedacoDeCartao(primeiro = false, ultimo = linha.ultimo)
        b.tvDescricao.text = l.descricao
        b.tvData.text = l.dataLancamento?.split("-")?.let { if (it.size == 3) "${it[2]}/${it[1]}/${it[0]}" else null }.orEmpty()
        b.tvValor.text = (if (entrada) "" else "-") + Relatorios.moeda(l.valorNumero)
        b.tvValor.setTextColor(cor(if (entrada) R.color.success else R.color.danger))
        val saldo = l.saldoAcumulado?.toDoubleOrNull() ?: 0.0
        b.tvSaldo.text = getString(R.string.fin_saldo_acum, Relatorios.moeda(saldo))
        b.tvSaldo.setTextColor(cor(if (saldo >= 0) R.color.light_text else R.color.danger))
        b.grupoSelos.removeAllViews()
        b.grupoSelos.addView(selo(getString(if (entrada) R.string.fin_entrada else R.string.fin_saida),
            if (entrada) R.color.success else R.color.danger, if (entrada) R.color.success_bg else R.color.danger_bg, ponto = true))
        b.grupoSelos.addView(selo(l.categoriaNome ?: getString(R.string.fin_sem_categoria), R.color.ink_soft, R.color.sidebar))
        b.grupoSelos.addView(selo("${getString(R.string.fin_conciliado)}: ${getString(if (l.conciliado) R.string.fin_conciliado_sim else R.string.fin_pendente)}",
            if (l.conciliado) R.color.info else R.color.warning, if (l.conciliado) R.color.info_bg else R.color.warning_bg))
    }

    // --- Novo lançamento --------------------------------------------------------------

    private fun abrirFormulario() {
        val b = SheetLancamentoBinding.inflate(layoutInflater)
        var data = Datas.iso()
        var tipo = "saida"
        var categoriaId = ""
        val inicial = listOf("", data, tipo, "", categoriaId, false)
        fun atual() = listOf(b.etDescricao.text.toString(), data, tipo, b.etValor.text.toString(), categoriaId, b.switchConciliado.isChecked)
        val folha = Folha(this, b.root) { atual() != inicial }

        b.etData.setText(Datas.dataCurta(data))
        b.etData.setOnClickListener {
            val cal = Calendar.getInstance().apply { Datas.ler(data)?.let { time = it } }
            DatePickerDialog(this, { _, a, m, d ->
                data = Datas.iso(Calendar.getInstance().apply { set(a, m, d) }.time)
                b.etData.setText(Datas.dataCurta(data))
                b.campoData.error = null
            }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
        }
        b.acTipo.configurarSelect(listOf("saida" to getString(R.string.fin_tipo_saida), "entrada" to getString(R.string.fin_tipo_entrada)), tipo) { tipo = it }
        b.acCategoria.configurarSelect(listOf("" to getString(R.string.fin_selecione)) + opcoesDeCategoria(), categoriaId) { categoriaId = it }
        b.etDescricao.doAfterTextChanged { b.campoDescricao.error = null }
        b.etValor.doAfterTextChanged { b.campoValor.error = null }
        b.btnCancelar.setOnClickListener { folha.fechar() }
        b.btnSalvar.setOnClickListener {
            val descricao = b.etDescricao.text.toString().trim()
            val valor = b.etValor.text.toString().replace(',', '.').toDoubleOrNull()
            b.campoDescricao.error = if (descricao.isEmpty()) getString(R.string.fin_informe_descricao) else null
            b.campoData.error = if (data.isEmpty()) getString(R.string.fin_informe_data) else null
            b.campoValor.error = if (valor == null || valor <= 0) getString(R.string.fin_valor_invalido) else null
            if (b.campoDescricao.error != null || b.campoData.error != null || b.campoValor.error != null) return@setOnClickListener

            b.btnSalvar.isEnabled = false
            b.tvErro.isVisible = false
            lifecycleScope.launch {
                val corpo = NovoLancamento(descricao, data, tipo, valor!!, b.switchConciliado.isChecked, categoriaId.ifEmpty { null })
                when (val r = executarApi(getString(R.string.fin_erro_salvar)) { api.criarLancamento(tabelaId, corpo) }) {
                    is Resultado.Sucesso -> { folha.dispensar(); avisar(getString(R.string.fin_salvo)); carregar() }
                    is Resultado.Erro -> {
                        if (r.sessaoExpirada) return@launch redirecionarParaLogin()
                        b.tvErro.text = r.mensagem
                        b.tvErro.isVisible = true
                        b.btnSalvar.isEnabled = true
                    }
                }
            }
        }
        folha.abrir()
    }

    companion object {
        const val EXTRA_ID = "com.example.levit.extra.TABELA_ID"
        private val CATEGORIAS_PADRAO = listOf("Fixo", "Variável", "Investimento", "Receita")
        private const val LINHAS_POR_LOTE = 50
        private const val TIPO_MES = 1
        private const val TIPO_LANC = 2
        private const val TIPO_MAIS = 3
        private const val TIPO_ESTADO = 4
    }
}
