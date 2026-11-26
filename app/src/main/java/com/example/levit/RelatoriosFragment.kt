package com.example.levit

import android.app.DatePickerDialog
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.navigation.fragment.findNavController
import androidx.lifecycle.lifecycleScope
import com.example.levit.data.remote.ApiClient
import com.example.levit.data.remote.dto.Agrupamento
import com.example.levit.data.remote.dto.Comparacao
import com.example.levit.data.remote.dto.Envio
import com.example.levit.data.remote.dto.FinanceiroIndicador
import com.example.levit.data.remote.dto.Grupo
import com.example.levit.data.remote.dto.Indicadores
import com.example.levit.data.remote.dto.Pessoa
import com.example.levit.data.remote.dto.PrazosIndicador
import com.example.levit.data.remote.dto.Relatorio
import com.example.levit.data.remote.dto.RelatorioRequest
import com.example.levit.data.remote.dto.TabelaRelatorio
import com.example.levit.data.remote.dto.VagaIndicador
import com.example.levit.data.repository.ChamadorApi
import com.example.levit.data.repository.ResultadoApi
import com.example.levit.data.repository.baixarApi
import com.example.levit.databinding.FragmentRelatoriosBinding
import com.example.levit.databinding.ItemRelAcoesBinding
import com.example.levit.databinding.ItemRelEnvioBinding
import com.example.levit.databinding.ItemRelParBinding
import com.example.levit.databinding.ItemRelatorioBinding
import com.example.levit.databinding.ItemTituloPdfBinding
import com.example.levit.databinding.SheetRelatorioBinding
import com.example.levit.ui.Barra
import com.example.levit.ui.Datas
import com.example.levit.ui.Folha
import com.example.levit.ui.Relatorios
import com.example.levit.ui.abrirArquivo
import com.example.levit.ui.adicionarBarras
import com.example.levit.ui.adicionarIndicador
import com.example.levit.ui.adicionarTexto
import com.example.levit.ui.adicionarTitulo
import com.example.levit.ui.alerta
import com.example.levit.ui.avisar
import com.example.levit.ui.inflar
import com.example.levit.ui.inflarFilho
import com.example.levit.ui.configurarSelect
import com.example.levit.ui.confirmar
import com.example.levit.ui.cor
import com.example.levit.ui.dp
import com.example.levit.ui.mostrarCarregando
import com.example.levit.ui.mostrarEstado
import com.example.levit.ui.selo
import com.google.android.material.button.MaterialButton
import com.google.android.material.tabs.TabLayout
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.Date
import kotlin.math.roundToInt

/**
 * Relatórios: painel com os números do período comparados ao
 * anterior, relatório montado (agrupar uma tabela por uma coluna) e PDFs,
 * baixados na hora ou agendados por e-mail.
 */
class RelatoriosFragment : HamburgerMenuBaseFragment() {

    override val pageLayoutResId: Int = R.layout.fragment_relatorios

    private var _binding: FragmentRelatoriosBinding? = null
    private val binding get() = checkNotNull(_binding)
    private val chamador by lazy { ChamadorApi(requireContext().applicationContext) }
    private val api by lazy { ApiClient.relatorioApi(requireContext().applicationContext) }
    private val equipeApi by lazy { ApiClient.equipeApi(requireContext().applicationContext) }

    private var aba = ABA_PAINEL
    private var atalho = "30d"
    private var inicio = Datas.somarDias(Datas.iso(), -29)
    private var fim = Datas.iso()
    private val periodoValido get() = inicio.isNotBlank() && fim.isNotBlank() && inicio <= fim

    // Agendados (também decidem o acesso: 403 = só acesso total vê relatórios).
    private var relatorios: List<Relatorio>? = null
    private var erroRelatorios: ResultadoApi.Erro? = null
    private var pessoas: List<Pessoa> = emptyList()
    private val historicosAbertos = mutableSetOf<String>()
    private var secoesBaixar = Relatorios.SECOES_PADRAO.toSet()

    // Painel
    private var indicadores: Indicadores? = null
    private var chaveIndicadores: String? = null
    private var erroIndicadores: ResultadoApi.Erro? = null
    private var tarefaIndicadores: Job? = null

    // Montar relatório
    private var tabelas: List<TabelaRelatorio>? = null
    private var erroTabelas: ResultadoApi.Erro? = null
    private var moduloId = ""
    private var agruparPor = ""
    private var operacao = "contar"
    private var campoValor = ""
    private var noPeriodo = false
    private var tituloPdf = ""
    private var agrupamento: Agrupamento? = null
    private var chaveAgrupamento: String? = null
    private var erroAgrupamento: ResultadoApi.Erro? = null
    private var tarefaAgrupamento: Job? = null

    override fun onDestroyView() {
        tarefaIndicadores?.cancel()
        tarefaAgrupamento?.cancel()
        _binding = null
        super.onDestroyView()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        if (!menuFoiMontado()) return
        val paginaRaiz = view.findViewById<FrameLayout>(R.id.pageContentContainer).getChildAt(0)
        _binding = FragmentRelatoriosBinding.bind(paginaRaiz)
        setupHamburgerMenuButton(R.id.ivMenu)

        listOf(R.string.rel_aba_painel, R.string.rel_aba_montar, R.string.rel_aba_pdf).forEach {
            binding.abas.addTab(binding.abas.newTab().setText(it))
        }
        binding.abas.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) { aba = tab.position; mostrarAba() }
            override fun onTabUnselected(tab: TabLayout.Tab) = Unit
            override fun onTabReselected(tab: TabLayout.Tab) = Unit
        })
        binding.btnNovoRelatorio.setOnClickListener { abrirFormulario(null) }
        configurarPeriodo()
        configurarMontar()
        mostrarAba()
    }

    override fun onResume() {
        super.onResume()
        if (menuFoiMontado() && _binding != null) carregarRelatorios()
    }

    private val semAcesso get() = erroRelatorios?.semPermissao == true

    private fun mostrarAba() {
        binding.abas.isVisible = !semAcesso
        binding.llPeriodo.isVisible = !semAcesso
        binding.llMontar.isVisible = !semAcesso && aba == ABA_MONTAR
        binding.btnNovoRelatorio.isVisible = !semAcesso && aba == ABA_PDF
        binding.tvPeriodoErro.isVisible = !periodoValido
        if (semAcesso) {
            binding.llConteudo.mostrarEstado(
                titulo = getString(R.string.rel_sem_acesso_titulo),
                descricao = getString(R.string.rel_sem_acesso_desc),
                icone = R.drawable.ic_lock
            )
            return
        }
        when (aba) {
            ABA_PAINEL -> mostrarPainel()
            ABA_MONTAR -> mostrarMontar()
            else -> mostrarPdf()
        }
    }

    private fun tratarSessao(r: ResultadoApi.Erro): Boolean {
        if (r.sessaoExpirada) redirecionarParaLogin()
        return r.sessaoExpirada
    }

    // Período

    private fun configurarPeriodo() {
        atualizarCamposPeriodo()
        binding.etInicio.setOnClickListener { escolherData(inicio, maximo = fim) { inicio = it; periodoMudou("outro") } }
        binding.etFim.setOnClickListener { escolherData(fim, minimo = inicio, maximo = Datas.iso()) { fim = it; periodoMudou("outro") } }
    }

    private fun atualizarCamposPeriodo() {
        binding.acAtalho.configurarSelect(Relatorios.ATALHOS.map { (valor, rotulo) -> valor to getString(rotulo) }, atalho) { valor ->
            Relatorios.periodoDoAtalho(valor)?.let { (i, f) -> inicio = i; fim = f }
            periodoMudou(valor)
        }
        binding.etInicio.setText(Datas.dataCurta(inicio))
        binding.etFim.setText(Datas.dataCurta(fim))
        binding.switchPeriodo.text = getString(R.string.rel_so_periodo, Relatorios.textoDoPeriodo(inicio, fim).em(requireContext()))
    }

    private fun periodoMudou(novoAtalho: String) {
        atalho = novoAtalho
        atualizarCamposPeriodo()
        mostrarAba()
    }

    private fun escolherData(atual: String, minimo: String? = null, maximo: String? = null, aoEscolher: (String) -> Unit) {
        val cal = Calendar.getInstance().apply { time = Datas.ler(atual) ?: Date() }
        DatePickerDialog(requireContext(), { _, a, m, d ->
            aoEscolher(Datas.iso(Calendar.getInstance().apply { set(a, m, d) }.time))
        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).apply {
            minimo?.let { Datas.ler(it) }?.let { datePicker.minDate = it.time }
            maximo?.let { Datas.ler(it) }?.let { datePicker.maxDate = it.time + 86_399_000 }
        }.show()
    }

    // Peças de layout

    private fun cartao(destino: ViewGroup = binding.llConteudo, topo: Int = 12): LinearLayout =
        destino.inflarFilho<LinearLayout>(R.layout.item_rel_cartao).apply {
            (layoutParams as ViewGroup.MarginLayoutParams).topMargin = requireContext().dp(topo)
            destino.addView(this)
        }

    /** Indicadores dois a dois por linha (grid-cols-2 do web no celular). */
    private fun ViewGroup.indicadoresEmPares(vararg itens: (ViewGroup) -> Unit) {
        itens.toList().chunked(2).forEach { par ->
            val linha = ItemRelParBinding.inflate(layoutInflater, this, false)
            par[0](linha.colunaA)
            if (par.size == 2) par[1](linha.colunaB)
            else {
                linha.colunaB.isVisible = false
                linha.espaco.isVisible = true
            }
            addView(linha.root)
        }
    }

    // Painel

    private fun mostrarPainel() {
        if (!periodoValido) return binding.llConteudo.removeAllViews()
        val chave = "$inicio|$fim"
        if (chave != chaveIndicadores) carregarIndicadores(chave)
        desenharPainel()
    }

    private fun carregarIndicadores(chave: String) {
        chaveIndicadores = chave
        tarefaIndicadores?.cancel()
        val (i, f) = inicio to fim
        tarefaIndicadores = viewLifecycleOwner.lifecycleScope.launch {
            val r = chamador.chamar { api.indicadores(i, f) }
            if (chave != chaveIndicadores) return@launch
            when (r) {
                is ResultadoApi.Sucesso -> { indicadores = r.dados; erroIndicadores = null }
                is ResultadoApi.Erro -> { if (tratarSessao(r)) return@launch; erroIndicadores = r }
            }
            tarefaIndicadores = null
            if (aba == ABA_PAINEL) desenharPainel()
        }
    }

    private fun desenharPainel() {
        val conteudo = binding.llConteudo
        val d = indicadores
        val buscando = tarefaIndicadores != null
        // Ainda buscando um período novo: mostra o anterior apagado, como o web.
        conteudo.alpha = if (buscando && d != null) 0.7f else 1f
        when {
            buscando && d == null -> return conteudo.mostrarCarregando(2)
            erroIndicadores != null -> return conteudo.mostrarEstado(
                titulo = getString(R.string.rel_erro_indicadores),
                descricao = erroIndicadores?.mensagem,
                icone = R.drawable.ic_warning,
                acao = getString(R.string.rel_tentar),
                erro = true
            ) { chaveIndicadores = null; mostrarPainel() }
            d == null -> return
        }
        conteudo.removeAllViews()
        val dados = d ?: return
        val r = dados.resumo

        conteudo.adicionarTitulo(
            getString(R.string.rel_resumo),
            getString(R.string.rel_comparado_com, Relatorios.textoDoPeriodo(dados.periodoAnterior?.inicio, dados.periodoAnterior?.fim).em(requireContext()))
        )
        cartao(topo = 8).indicadoresEmPares(
            { it.adicionarIndicador(getString(R.string.rel_itens_criados), r?.itensCriados) },
            { it.adicionarIndicador(getString(R.string.rel_candidatos_inscritos), r?.candidatos) },
            { it.adicionarIndicador(getString(R.string.rel_atividades), r?.atividades) },
            { it.adicionarIndicador(getString(R.string.rel_pessoas_ativas), r?.pessoasAtivas) },
        )

        conteudo.adicionarTitulo(getString(R.string.rel_prazos), getString(R.string.rel_prazos_desc), topo = 28)
        dados.prazos?.let { desenharPrazos(it) }

        val vagas = dados.recrutamento.orEmpty()
        if (vagas.isNotEmpty()) {
            conteudo.adicionarTitulo(getString(R.string.rel_recrutamento), topo = 28)
            vagas.forEach { desenharVaga(it) }
        }
        val financeiros = dados.financeiro.orEmpty()
        if (financeiros.isNotEmpty()) {
            conteudo.adicionarTitulo(getString(R.string.rel_financeiro), topo = 28)
            financeiros.forEach { desenharFinanceiro(it) }
        }
        if (vagas.isEmpty() && financeiros.isEmpty()) {
            val vazio = conteudo.inflarFilho<LinearLayout>(R.layout.item_rel_vazio)
            vazio.mostrarEstado(getString(R.string.rel_sem_quadros_titulo), getString(R.string.rel_sem_quadros_desc), R.drawable.ic_bar_chart)
            conteudo.addView(vazio)
        }
    }

    private fun desenharPrazos(p: PrazosIndicador) {
        val c = cartao(topo = 8)
        val taxa = p.taxaConclusao
        val dica = when {
            taxa?.atual == null -> getString(R.string.rel_nenhum_prazo)
            taxa.anterior != null -> getString(R.string.rel_taxa_antes, Relatorios.numero(taxa.atual), Relatorios.numero(taxa.anterior))
            else -> getString(R.string.rel_taxa, Relatorios.numero(taxa.atual))
        }
        c.indicadoresEmPares(
            { it.adicionarIndicador(getString(R.string.rel_prazos_venceram), p.vencendo) },
            { it.adicionarIndicador(getString(R.string.rel_prazos_concluidos), p.concluidos, dica = dica) },
            { coluna ->
                // Sem comparação: é a foto de agora, vermelha se houver atraso.
                coluna.adicionarIndicador(getString(R.string.rel_atrasados_agora), Comparacao(p.atrasadosAgora, null, 0.0), dica = getString(R.string.rel_em_todas_tabelas))
                coluna.getChildAt(coluna.childCount - 1).findViewById<TextView>(R.id.tvIndicadorVariacao).isVisible = false
                if ((p.atrasadosAgora ?: 0.0) > 0) {
                    coluna.getChildAt(coluna.childCount - 1).findViewById<TextView>(R.id.tvIndicadorValor).setTextColor(requireContext().cor(R.color.danger))
                }
            },
        )
        c.adicionarTitulo(getString(R.string.rel_atrasados_por_responsavel), tamanho = 14f, topo = 20)
        c.adicionarBarras(
            p.atrasadosPorResponsavel.orEmpty().map { Barra(it.responsavel ?: "—", it.total ?: 0.0) },
            vazio = getString(R.string.rel_nenhum_atrasado)
        )
    }

    private fun desenharVaga(vaga: VagaIndicador) {
        val c = cartao()
        c.adicionarTitulo(vaga.nome.orEmpty(), getString(R.string.rel_candidatos_total, Relatorios.numero(vaga.total)), tamanho = 16f)
        c.indicadoresEmPares(
            { it.adicionarIndicador(getString(R.string.rel_inscritos_periodo), vaga.inscritos) },
            { it.adicionarIndicador(getString(R.string.rel_ultima_etapa), vaga.concluidos) },
            { it.adicionarIndicador(getString(R.string.rel_sairam), vaga.sairam, subirEBom = false) },
        )
        val parados = (vaga.parados ?: 0.0).toInt()
        if (parados > 0) c.addView(requireContext().alerta(resources.getQuantityString(R.plurals.rel_parados, parados, parados), "warning"))

        val fases = vaga.fases.orEmpty()
        val noFunil = fases.filter { it.saida != true }
        val saidas = fases.filter { it.saida == true }
        val primeira = noFunil.firstOrNull()?.chegaram ?: 0.0
        c.adicionarTitulo(getString(R.string.rel_ate_onde), getString(R.string.rel_ate_onde_desc), tamanho = 14f, topo = 20)
        c.adicionarBarras(
            if (primeira == 0.0) emptyList() else noFunil.map { f ->
                Barra(
                    rotulo = f.nome.orEmpty(),
                    valor = f.chegaram ?: 0.0,
                    extra = "${((f.chegaram ?: 0.0) / primeira * 100).roundToInt()}%",
                    detalhe = listOfNotNull(
                        getString(R.string.rel_agora, Relatorios.numero(f.atual)),
                        f.diasMedios?.let { getString(R.string.rel_dias_media, Relatorios.numero(it)) }
                    ).joinToString(" · ")
                )
            },
            vazio = getString(R.string.rel_ninguem_inscreveu)
        )
        if (saidas.isNotEmpty()) c.adicionarTexto(getString(
            R.string.rel_fora_do_funil,
            saidas.joinToString(", ") { "${it.nome} (${getString(R.string.rel_agora, Relatorios.numero(it.atual))})" }
        ), topo = 10)
    }

    private fun desenharFinanceiro(fin: FinanceiroIndicador) {
        val c = cartao()
        c.adicionarTitulo(fin.nome.orEmpty(), tamanho = 16f)
        c.indicadoresEmPares(
            { it.adicionarIndicador(getString(R.string.rel_entradas), fin.entradas, Relatorios::moeda) },
            { it.adicionarIndicador(getString(R.string.rel_saidas), fin.saidas, Relatorios::moeda, subirEBom = false) },
            { it.adicionarIndicador(getString(R.string.rel_saldo), fin.saldo, Relatorios::moeda) },
        )
        c.adicionarTitulo(getString(R.string.rel_saidas_categoria), tamanho = 14f, topo = 20)
        c.adicionarBarras(
            fin.saidasPorCategoria.orEmpty().take(8).map { Barra(it.categoria ?: "—", it.total ?: 0.0) },
            formatar = Relatorios::moeda,
            vazio = getString(R.string.rel_nenhuma_saida)
        )
    }

    // Montar relatório

    private val operacoes get() = listOf(
        "contar" to getString(R.string.rel_contar),
        "somar" to getString(R.string.rel_somar),
        "media" to getString(R.string.rel_media),
    )

    private fun configurarMontar() {
        binding.acCalcular.configurarSelect(operacoes, operacao) { operacao = it; mostrarAba() }
        binding.switchPeriodo.setOnCheckedChangeListener { _, ligado -> noPeriodo = ligado; mostrarAba() }
    }

    private fun tabelaEscolhida() = tabelas?.firstOrNull { it.id == moduloId }

    private fun mostrarMontar() {
        val conteudo = binding.llConteudo
        conteudo.alpha = 1f
        val lista = tabelas
        if (lista == null) {
            binding.llMontar.isVisible = false
            if (erroTabelas != null) {
                return conteudo.mostrarEstado(getString(R.string.rel_erro_tabelas), erroTabelas?.mensagem, R.drawable.ic_warning,
                    getString(R.string.rel_tentar), erro = true) { erroTabelas = null; mostrarMontar() }
            }
            conteudo.mostrarCarregando(2)
            carregarTabelas()
            return
        }
        if (lista.isEmpty()) {
            binding.llMontar.isVisible = false
            return conteudo.mostrarEstado(getString(R.string.rel_nenhuma_tabela), getString(R.string.rel_nenhuma_tabela_desc), R.drawable.ic_inventory)
        }

        val precisaValor = configurarSeletores(lista)

        if (moduloId.isEmpty()) {
            return conteudo.mostrarEstado(getString(R.string.rel_monte_titulo), getString(R.string.rel_monte_desc), R.drawable.ic_bar_chart)
        }
        if (agruparPor.isEmpty() || (precisaValor && campoValor.isEmpty())) return conteudo.removeAllViews()
        if (noPeriodo && !periodoValido) return conteudo.removeAllViews()

        val params = parametrosDoAgrupamento()
        val chave = params.toSortedMap().toString()
        if (chave != chaveAgrupamento) carregarAgrupamento(chave, params)
        desenharAgrupamento()
    }

    private fun carregarTabelas() {
        viewLifecycleOwner.lifecycleScope.launch {
            when (val r = chamador.chamar { api.tabelas() }) {
                is ResultadoApi.Sucesso -> tabelas = r.dados
                is ResultadoApi.Erro -> { if (tratarSessao(r)) return@launch; erroTabelas = r }
            }
            if (aba == ABA_MONTAR) mostrarAba()
        }
    }

    /** Preenche os seletores de tabela, agrupamento e valor; devolve se a operação pede coluna de valor. */
    private fun configurarSeletores(lista: List<TabelaRelatorio>): Boolean {
        val tabela = tabelaEscolhida()
        binding.acTabela.configurarSelect(lista.map { it.id to (it.nome ?: "—") }, moduloId) { id ->
            val t = lista.firstOrNull { it.id == id }
            moduloId = id
            agruparPor = t?.agrupar?.firstOrNull()?.id.orEmpty()
            campoValor = t?.numeros?.firstOrNull()?.id.orEmpty()
            mostrarAba()
        }
        // As colunas das tabelas ligadas vêm com o nome do grupo na frente.
        val opcoesAgrupar = tabela?.agrupar.orEmpty().map { c ->
            val nome = if (c.tipo in POR_MES) getString(R.string.rel_por_mes, c.nome) else c.nome.orEmpty()
            c.id to (c.grupo?.takeIf { it.isNotBlank() && it != "Esta tabela" }?.let { "$it › $nome" } ?: nome)
        }
        binding.acAgrupar.configurarSelect(opcoesAgrupar, agruparPor) { agruparPor = it; mostrarAba() }
        binding.campoAgrupar.isEnabled = opcoesAgrupar.isNotEmpty()
        val precisaValor = operacao != "contar"
        val numeros = tabela?.numeros.orEmpty()
        binding.campoValor.isVisible = precisaValor
        binding.campoValor.isEnabled = tabela != null && numeros.isNotEmpty()
        binding.campoValor.helperText = if (tabela != null && numeros.isEmpty()) getString(R.string.rel_sem_numeros) else null
        binding.acValor.configurarSelect(numeros.map { it.id to (it.nome ?: "—") }, campoValor) { campoValor = it; mostrarAba() }
        binding.switchPeriodo.isChecked = noPeriodo
        return precisaValor
    }

    private fun parametrosDoAgrupamento(): Map<String, String> = buildMap {
        put("modulo_id", moduloId)
        put("agrupar_por", agruparPor)
        put("operacao", operacao)
        put("campo_valor", campoValor)
        if (noPeriodo) { put("inicio", inicio); put("fim", fim) }
    }

    private fun carregarAgrupamento(chave: String, params: Map<String, String>) {
        chaveAgrupamento = chave
        tarefaAgrupamento?.cancel()
        tarefaAgrupamento = viewLifecycleOwner.lifecycleScope.launch {
            val r = chamador.chamar { api.agrupar(params) }
            if (chave != chaveAgrupamento) return@launch
            when (r) {
                is ResultadoApi.Sucesso -> { agrupamento = r.dados; erroAgrupamento = null }
                is ResultadoApi.Erro -> { if (tratarSessao(r)) return@launch; erroAgrupamento = r }
            }
            tarefaAgrupamento = null
            if (aba == ABA_MONTAR) desenharAgrupamento()
        }
    }

    private fun desenharAgrupamento() {
        val conteudo = binding.llConteudo
        val dados = agrupamento
        val buscando = tarefaAgrupamento != null
        conteudo.alpha = if (buscando && dados != null) 0.7f else 1f
        when {
            buscando && dados == null -> return conteudo.mostrarCarregando(2)
            erroAgrupamento != null -> return conteudo.mostrarEstado(getString(R.string.rel_erro_montar), erroAgrupamento?.mensagem,
                R.drawable.ic_warning, getString(R.string.rel_tentar), erro = true) { chaveAgrupamento = null; mostrarMontar() }
            dados == null -> return
        }
        val d = dados ?: return
        conteudo.removeAllViews()
        val c = cartao(topo = 0)
        val colunaGrupo = tabelaEscolhida()?.agrupar?.firstOrNull { it.id == agruparPor }
        val rotulo: (String?) -> String = { g ->
            if (colunaGrupo?.tipo in POR_MES && g != null && Regex("^\\d{4}-\\d{2}$").matches(g)) rotuloMes(g) else g.orEmpty()
        }
        val nomeValor = when (operacao) {
            "contar" -> getString(R.string.rel_itens)
            "somar" -> getString(R.string.rel_soma_de, d.colunaValor.orEmpty())
            else -> getString(R.string.rel_media_de, d.colunaValor.orEmpty())
        }
        val tituloPadrao = getString(R.string.rel_x_por_y, d.tabela.orEmpty(), d.agrupadoPor.orEmpty())
        c.adicionarTitulo(tituloPadrao, subtituloDoAgrupamento(d, nomeValor))

        val grupos = d.grupos.orEmpty()
        c.addView(acoesDoAgrupamento(c, d, rotulo, nomeValor, tituloPadrao).root)
        c.addView(campoDoTituloDoPdf(c, tituloPadrao))
        c.addView(barrasDoAgrupamento(c, grupos, rotulo))
    }

    private fun subtituloDoAgrupamento(d: Agrupamento, nomeValor: String): String {
        val totalItens = d.totalItens ?: 0.0
        return buildList {
            add(nomeValor)
            add(resources.getQuantityString(R.plurals.rel_n_itens, if (totalItens == 1.0) 1 else 2, Relatorios.numero(totalItens)))
            if ((d.cortados ?: 0) > 0) add(getString(R.string.rel_cortados, d.cortados))
        }.joinToString(" · ")
    }

    /** Botões de exportar o agrupamento em CSV e PDF. */
    private fun acoesDoAgrupamento(
        c: ViewGroup, d: Agrupamento, rotulo: (String?) -> String, nomeValor: String, tituloPadrao: String
    ): ItemRelAcoesBinding {
        val acoes = ItemRelAcoesBinding.inflate(layoutInflater, c, false)
        val grupos = d.grupos.orEmpty()
        acoes.btnCsv.isEnabled = grupos.isNotEmpty()
        acoes.btnCsv.setOnClickListener {
            val linhas = grupos.map { g -> if (operacao == "contar") listOf(rotulo(g.rotulo), g.valor) else listOf(rotulo(g.rotulo), g.valor, g.itens) }
            val cabecalho = if (operacao == "contar") listOf(d.agrupadoPor, getString(R.string.rel_itens)) else listOf(d.agrupadoPor, nomeValor, getString(R.string.rel_itens))
            requireActivity().abrirArquivo(Relatorios.gerarCsv(cabecalho, linhas).toByteArray(), "${nomeDeArquivo("${d.tabela}-por-${d.agrupadoPor}")}.csv", "text/csv")
        }
        val pdf = acoes.btnPdf
        pdf.setOnClickListener {
            val titulo = tituloPdf.trim()
            val params = parametrosDoAgrupamento() + (if (titulo.isNotEmpty()) mapOf("titulo" to titulo) else emptyMap())
            baixarPdf(pdf, "${nomeDeArquivo(titulo.ifEmpty { tituloPadrao })}.pdf") { api.pdfMontado(params) }
        }
        return acoes
    }

    private fun campoDoTituloDoPdf(c: ViewGroup, tituloPadrao: String): View {
        val campoTitulo = ItemTituloPdfBinding.inflate(layoutInflater, c, false)
        campoTitulo.etTitulo.setText(tituloPdf)
        campoTitulo.campoTitulo.placeholderText = tituloPadrao
        campoTitulo.etTitulo.addTextChangedListener(object : android.text.TextWatcher {
            override fun afterTextChanged(s: android.text.Editable?) { tituloPdf = s?.toString().orEmpty() }
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) = Unit
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) = Unit
        })
        return campoTitulo.root
    }

    private fun barrasDoAgrupamento(c: ViewGroup, grupos: List<Grupo>, rotulo: (String?) -> String): View {
        val barras = c.inflarFilho<LinearLayout>(R.layout.item_bloco_lista)
        barras.adicionarBarras(
            grupos.map { g ->
                Barra(
                    rotulo = rotulo(g.rotulo),
                    valor = g.valor ?: 0.0,
                    detalhe = if (operacao == "contar") null
                    else resources.getQuantityString(R.plurals.rel_n_itens, if (g.itens == 1.0) 1 else 2, Relatorios.numero(g.itens))
                )
            },
            vazio = getString(R.string.rel_nenhum_item)
        )
        return barras
    }

    /** rotuloMes: "2026-10" vira "out/26". */
    private fun rotuloMes(anoMes: String): String {
        val (a, m) = anoMes.split("-").map { it.toInt() }
        val nome = java.text.SimpleDateFormat("MMM", java.util.Locale.forLanguageTag("pt-BR"))
            .format(Calendar.getInstance().apply { set(a, m - 1, 1) }.time).replace(".", "")
        return "$nome/${a.toString().takeLast(2)}"
    }

    private fun nomeDeArquivo(texto: String): String =
        java.text.Normalizer.normalize(texto, java.text.Normalizer.Form.NFD)
            .replace(Regex("[\\u0300-\\u036f]"), "")
            .replace(Regex("[^\\w-]+"), "-")
            .trim('-')
            .lowercase()
            .ifEmpty { "relatorio" }

    // PDFs: baixar agora e agendados

    private fun baixarPdf(botao: MaterialButton, nome: String, requisicao: suspend () -> retrofit2.Response<okhttp3.ResponseBody>) {
        val textoOriginal = botao.text
        botao.isEnabled = false
        botao.setText(R.string.rel_gerando)
        viewLifecycleOwner.lifecycleScope.launch {
            val r = baixarApi(requireContext(), requisicao)
            botao.isEnabled = true
            botao.text = textoOriginal
            when (r) {
                is ResultadoApi.Sucesso -> requireActivity().abrirArquivo(r.dados, nome, "application/pdf")
                is ResultadoApi.Erro -> if (!tratarSessao(r)) requireContext().avisar(r.mensagem, longo = true)
            }
        }
    }

    private fun parametrosPdf(i: String, f: String, titulo: String?, secoes: Collection<String>?) = buildMap {
        put("inicio", i)
        put("fim", f)
        put("titulo", titulo ?: getString(R.string.rel_titulo_pdf_padrao))
        // Mesma ordem do catálogo; vazio = padrão do backend.
        secoes?.let { s -> Relatorios.SECOES.map { it.first }.filter { it in s } }?.takeIf { it.isNotEmpty() }
            ?.let { put("secoes", it.joinToString(",")) }
    }

    private fun carregarRelatorios() = viewLifecycleOwner.lifecycleScope.launch {
        val r = chamador.chamar { api.listar() }
        when (r) {
            is ResultadoApi.Sucesso -> { relatorios = r.dados; erroRelatorios = null }
            is ResultadoApi.Erro -> { if (tratarSessao(r)) return@launch; erroRelatorios = r }
        }
        if (!semAcesso && pessoas.isEmpty()) {
            (chamador.chamar { equipeApi.pessoas() } as? ResultadoApi.Sucesso)?.let { pessoas = it.dados }
        }
        mostrarAba()
    }

    private fun mostrarPdf() {
        val conteudo = binding.llConteudo
        conteudo.alpha = 1f
        conteudo.removeAllViews()

        // Baixar agora
        val c = cartao(topo = 0)
        c.adicionarTitulo(getString(R.string.rel_baixar_agora))
        c.adicionarTexto(getString(R.string.rel_o_que_entra), R.color.ink, 14f, topo = 10)
        val baixar = c.inflarFilho<MaterialButton>(R.layout.item_rel_baixar)
        fun atualizarBaixar() { baixar.isEnabled = periodoValido && secoesBaixar.isNotEmpty() }
        Relatorios.SECOES.forEach { (valor, rotulo) ->
            c.addView(requireContext().inflar<CheckBox>(R.layout.item_caixa_marcar).apply {
                text = getString(rotulo)
                isChecked = valor in secoesBaixar
                setOnCheckedChangeListener { _, marcado ->
                    secoesBaixar = if (marcado) secoesBaixar + valor else secoesBaixar - valor
                    atualizarBaixar()
                }
            })
        }
        atualizarBaixar()
        baixar.setOnClickListener {
            val params = parametrosPdf(inicio, fim, null, secoesBaixar)
            baixarPdf(baixar, "relatorio-$inicio-a-$fim.pdf") { api.pdf(params) }
        }
        c.addView(baixar)

        // Agendados
        conteudo.adicionarTitulo(getString(R.string.rel_agendados), topo = 28)
        val lista = conteudo.inflarFilho<LinearLayout>(R.layout.item_bloco_lista)
        conteudo.addView(lista)
        val rels = relatorios
        val e = erroRelatorios
        when {
            e != null -> lista.mostrarEstado(getString(R.string.rel_erro_relatorios), e.mensagem, R.drawable.ic_warning,
                getString(R.string.rel_tentar), erro = true) { carregarRelatorios() }
            rels == null -> lista.mostrarCarregando(2)
            rels.isEmpty() -> lista.mostrarEstado(getString(R.string.rel_nenhum_agendado), getString(R.string.rel_nenhum_agendado_desc),
                R.drawable.ic_schedule, getString(R.string.rel_agendar_primeiro)) { abrirFormulario(null) }
            else -> rels.forEach { lista.addView(cartaoRelatorio(it, lista)) }
        }
    }

    private fun corpoDo(r: Relatorio, ativo: Boolean = r.ativo == true) = RelatorioRequest(
        nome = r.nome.orEmpty(),
        frequencia = r.frequencia ?: "semanal",
        dia = r.dia ?: 1,
        destinatarios = r.destinatarios.orEmpty(),
        secoes = r.secoes ?: Relatorios.SECOES_PADRAO,
        ativo = ativo
    )

    private fun cartaoRelatorio(r: Relatorio, pai: ViewGroup): View {
        val v = ItemRelatorioBinding.inflate(layoutInflater, pai, false)
        val ativo = r.ativo == true
        v.root.alpha = if (ativo) 1f else 0.8f
        v.tvNome.text = r.nome
        val agenda = r.descricaoAgenda.orEmpty()
        v.tvAgenda.text = when {
            !ativo -> getString(R.string.rel_pausado_sufixo, agenda)
            r.proximoEnvio != null -> getString(R.string.rel_proximo_envio, agenda, Datas.dataCurta(r.proximoEnvio))
            else -> agenda
        }
        ligarChaveDeAtivo(v, r, ativo)

        val nomes = pessoas.associate { it.id to it.nome.orEmpty() }
        v.tvRecebem.text = getString(R.string.rel_recebem,
            r.destinatarios.orEmpty().joinToString(", ") { nomes[it] ?: getString(R.string.rel_pessoa_removida) })
        mostrarUltimoEnvio(v, r)
        ligarAcoesDoCartao(v, r)
        ligarHistorico(v, r)
        return v.root
    }

    private fun ligarChaveDeAtivo(v: ItemRelatorioBinding, r: Relatorio, ativo: Boolean) {
        v.switchAtivo.isChecked = ativo
        v.switchAtivo.contentDescription = getString(if (ativo) R.string.rel_pausar else R.string.rel_ligar)
        v.switchAtivo.setOnCheckedChangeListener { _, ligado ->
            viewLifecycleOwner.lifecycleScope.launch {
                when (val res = chamador.chamarSemCorpo { api.atualizar(r.id, corpoDo(r, ligado)) }) {
                    is ResultadoApi.Sucesso -> requireContext().avisar(getString(if (ligado) R.string.rel_ligado_msg else R.string.rel_pausado_msg))
                    is ResultadoApi.Erro -> if (!tratarSessao(res)) requireContext().avisar(res.mensagem, longo = true)
                }
                carregarRelatorios()
            }
        }
    }

    private fun mostrarUltimoEnvio(v: ItemRelatorioBinding, r: Relatorio) {
        val ultimo = r.ultimoEnvio
        v.tvUltimo.isVisible = ultimo != null
        if (ultimo == null) return
        val ok = ultimo.status == "enviado"
        v.tvUltimo.text = if (ok) getString(R.string.rel_ultimo_envio_ok, Datas.dataHora(ultimo.enviadoEm), ultimo.totalAtividades ?: 0)
        else getString(R.string.rel_ultimo_envio_falhou, Datas.dataHora(ultimo.enviadoEm), ultimo.erro ?: getString(R.string.rel_erro_desconhecido))
        v.tvUltimo.setTextColor(requireContext().cor(if (ok) R.color.light_text else R.color.danger))
    }

    /** Enviar agora, editar e excluir. */
    private fun ligarAcoesDoCartao(v: ItemRelatorioBinding, r: Relatorio) {
        v.btnEnviar.setOnClickListener {
            v.btnEnviar.isEnabled = false
            viewLifecycleOwner.lifecycleScope.launch {
                when (val res = chamador.chamarSemCorpo { api.enviarAgora(r.id) }) {
                    is ResultadoApi.Sucesso -> requireContext().avisar(getString(R.string.rel_enviado_email))
                    is ResultadoApi.Erro -> if (!tratarSessao(res)) requireContext().avisar(res.mensagem, longo = true)
                }
                v.btnEnviar.isEnabled = true
                carregarRelatorios()
            }
        }
        v.btnEditar.setOnClickListener { abrirFormulario(r) }
        v.btnExcluir.setOnClickListener {
            requireContext().confirmar(getString(R.string.rel_excluir_titulo), getString(R.string.rel_excluir_msg, r.nome), getString(R.string.rel_excluir_botao)) {
                viewLifecycleOwner.lifecycleScope.launch {
                    when (val res = chamador.chamarSemCorpo { api.excluir(r.id) }) {
                        is ResultadoApi.Sucesso -> requireContext().avisar(getString(R.string.rel_excluido))
                        is ResultadoApi.Erro -> if (!tratarSessao(res)) requireContext().avisar(res.mensagem, longo = true)
                    }
                    carregarRelatorios()
                }
            }
        }
    }

    private fun ligarHistorico(v: ItemRelatorioBinding, r: Relatorio) {
        fun mostrarHistorico(aberto: Boolean) {
            v.llHistorico.isVisible = aberto
            if (aberto) carregarHistorico(r, v.llHistorico)
        }
        v.btnHistorico.setOnClickListener {
            val aberto = r.id !in historicosAbertos
            if (aberto) historicosAbertos += r.id else historicosAbertos -= r.id
            mostrarHistorico(aberto)
        }
        mostrarHistorico(r.id in historicosAbertos)
    }

    private fun carregarHistorico(r: Relatorio, destino: LinearLayout) {
        destino.mostrarCarregando(1)
        viewLifecycleOwner.lifecycleScope.launch {
            when (val res = chamador.chamar { api.envios(r.id) }) {
                is ResultadoApi.Erro -> {
                    if (tratarSessao(res)) return@launch
                    destino.removeAllViews()
                    destino.adicionarTexto(res.mensagem, R.color.danger, 14f, topo = 8)
                }
                is ResultadoApi.Sucesso -> {
                    destino.removeAllViews()
                    if (res.dados.isEmpty()) return@launch destino.adicionarTexto(getString(R.string.rel_sem_envios), tamanho = 14f, topo = 8)
                    res.dados.forEach { destino.addView(linhaDeEnvio(r, it)) }
                }
            }
        }
    }

    private fun linhaDeEnvio(r: Relatorio, envio: Envio): View {
        val linha = ItemRelEnvioBinding.inflate(layoutInflater)
        linha.tvData.text = Datas.dataHora(envio.enviadoEm)
        linha.root.addView(
            if (envio.status == "enviado") requireContext().selo(getString(R.string.rel_n_atividades, envio.totalAtividades ?: 0), R.color.success, R.color.success_bg)
            else requireContext().selo(getString(R.string.rel_nao_enviado), R.color.danger, R.color.danger_bg),
            1
        )
        val baixar = linha.btnBaixar
        baixar.setOnClickListener {
            // O período do envio termina à meia-noite (exclusivo): o último dia é o anterior.
            val ini = Datas.ler(envio.periodoInicio, utc = true) ?: return@setOnClickListener
            val fimExclusivo = Datas.ler(envio.periodoFim, utc = true) ?: return@setOnClickListener
            val i = Datas.iso(ini)
            val f = Datas.iso(Date(fimExclusivo.time - 60_000))
            val params = parametrosPdf(i, f, r.nome, r.secoes)
            baixarPdf(baixar, "relatorio-$i-a-$f.pdf") { api.pdf(params) }
        }
        return linha.root
    }

    // Formulário de agendamento

    private fun abrirFormulario(relatorio: Relatorio?) {
        val b = SheetRelatorioBinding.inflate(layoutInflater)
        val editando = relatorio != null
        val inicial = relatorio?.let { corpoDo(it) } ?: RelatorioRequest("", "semanal", 1, emptyList(), Relatorios.SECOES_PADRAO, true)
        var frequencia = inicial.frequencia
        var dia = inicial.dia
        val secoes = inicial.secoes.toMutableSet()
        val destinatarios = inicial.destinatarios.toMutableSet()

        b.tvTitulo.setText(if (editando) R.string.rel_editar_titulo else R.string.rel_novo_titulo)
        b.btnSalvar.setText(if (editando) R.string.rel_salvar else R.string.rel_agendar)
        b.etNome.setText(inicial.nome)
        b.switchAtivo.isChecked = inicial.ativo

        fun atualizarDia() {
            b.campoDia.isVisible = frequencia != "diario"
            b.campoDia.hint = getString(if (frequencia == "semanal") R.string.rel_dia_semana else R.string.rel_dia_mes)
            val opcoes = if (frequencia == "semanal") resources.getStringArray(R.array.rel_dias_semana).mapIndexed { i, nome -> "${i + 1}" to nome }
            else (1..28).map { "$it" to getString(R.string.rel_dia_n, it) }
            b.acDia.configurarSelect(opcoes, "$dia") { dia = it.toInt() }
            b.tvCobre.setText(when (frequencia) {
                "diario" -> R.string.rel_cobre_diario
                "semanal" -> R.string.rel_cobre_semanal
                else -> R.string.rel_cobre_mensal
            })
        }
        val botoesFrequencia = mapOf("diario" to b.btnDiario.id, "semanal" to b.btnSemanal.id, "mensal" to b.btnMensal.id)
        b.grupoFrequencia.check(botoesFrequencia.getValue(frequencia))
        b.grupoFrequencia.addOnButtonCheckedListener { _, id, marcado ->
            if (!marcado) return@addOnButtonCheckedListener
            frequencia = botoesFrequencia.entries.first { it.value == id }.key
            dia = 1
            atualizarDia()
        }
        atualizarDia()

        preencherEscolhas(b, secoes, destinatarios)

        fun atual() = RelatorioRequest(b.etNome.text.toString(), frequencia, dia, destinatarios.toList(),
            Relatorios.SECOES.map { it.first }.filter { it in secoes }, b.switchAtivo.isChecked)
        val folha = Folha(requireContext(), b.root) {
            val a = atual()
            a.copy(destinatarios = a.destinatarios.sorted(), secoes = a.secoes.sorted()) !=
                inicial.copy(destinatarios = inicial.destinatarios.sorted(), secoes = inicial.secoes.sorted())
        }
        b.btnCancelar.setOnClickListener { folha.fechar() }
        b.btnSalvar.setOnClickListener {
            val corpo = atual().let { it.copy(nome = it.nome.trim()) }
            val erro = erroDoAgendamento(corpo)
            b.tvErro.text = erro
            b.tvErro.isVisible = erro != null
            if (erro == null) salvarAgendamento(b, folha, relatorio, corpo)
        }
        folha.abrir()
    }

    /** Marcadores de seções e de destinatários; os conjuntos recebem as mudanças de cada um. */
    private fun preencherEscolhas(b: SheetRelatorioBinding, secoes: MutableSet<String>, destinatarios: MutableSet<String>) {
        Relatorios.SECOES.forEach { (valor, rotulo) ->
            b.llSecoes.addView(requireContext().inflar<CheckBox>(R.layout.item_caixa_marcar).apply {
                text = getString(rotulo)
                isChecked = valor in secoes
                setOnCheckedChangeListener { _, marcado -> if (marcado) secoes += valor else secoes -= valor }
            })
        }
        pessoas.forEach { p ->
            b.llPessoas.addView(requireContext().inflar<CheckBox>(R.layout.item_caixa_marcar).apply {
                text = p.nome
                isChecked = p.id in destinatarios
                setOnCheckedChangeListener { _, marcado -> if (marcado) destinatarios += p.id else destinatarios -= p.id }
            })
        }
    }

    private fun erroDoAgendamento(corpo: RelatorioRequest): String? = when {
        corpo.nome.isEmpty() -> getString(R.string.rel_de_nome)
        corpo.destinatarios.isEmpty() -> getString(R.string.rel_escolha_quem)
        corpo.secoes.isEmpty() -> getString(R.string.rel_marque_parte)
        else -> null
    }

    /** Cria o agendamento ou atualiza o existente e mostra o resultado na própria folha. */
    private fun salvarAgendamento(b: SheetRelatorioBinding, folha: Folha, relatorio: Relatorio?, corpo: RelatorioRequest) {
        b.btnSalvar.isEnabled = false
        viewLifecycleOwner.lifecycleScope.launch {
            val res = if (relatorio != null) chamador.chamarSemCorpo { api.atualizar(relatorio.id, corpo) }
            else chamador.chamarSemCorpo { api.criar(corpo) }
            when (res) {
                is ResultadoApi.Sucesso -> {
                    folha.dispensar()
                    requireContext().avisar(getString(if (relatorio != null) R.string.rel_atualizado else R.string.rel_agendado))
                    carregarRelatorios()
                }
                is ResultadoApi.Erro -> {
                    if (tratarSessao(res)) return@launch
                    b.tvErro.text = res.mensagem
                    b.tvErro.isVisible = true
                    b.btnSalvar.isEnabled = true
                }
            }
        }
    }

    companion object {
        private const val ABA_PAINEL = 0
        private const val ABA_MONTAR = 1
        private const val ABA_PDF = 2
        private val POR_MES = listOf("data", "prazo")
    }
}
