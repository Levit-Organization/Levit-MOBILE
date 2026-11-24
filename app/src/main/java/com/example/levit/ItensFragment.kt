package com.example.levit

import android.app.DatePickerDialog
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.text.InputType
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextPaint
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.view.View
import android.view.ViewGroup
import android.webkit.MimeTypeMap
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.edit
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.core.os.bundleOf
import android.widget.FrameLayout
import com.example.levit.data.remote.ApiClient
import com.example.levit.data.remote.dto.Coluna
import com.example.levit.data.remote.dto.Pessoa
import com.example.levit.data.remote.dto.Registro
import com.example.levit.data.remote.dto.RegistroRequest
import com.example.levit.data.remote.dto.TabelaDetalhe
import com.example.levit.data.repository.ChamadorApi
import com.example.levit.data.repository.ResultadoApi
import com.example.levit.data.repository.baixarApi
import com.example.levit.databinding.FragmentItensBinding
import com.example.levit.databinding.ItemDetalheRegistroBinding
import com.example.levit.databinding.ItemCampoFormularioBinding
import com.example.levit.databinding.ItemCampoVinculoBinding
import com.example.levit.databinding.ItemLigadosGrupoBinding
import com.example.levit.databinding.ItemEstadoListaBinding
import com.example.levit.databinding.ItemEtapaVaziaBinding
import com.example.levit.databinding.ItemPaginacaoBinding
import com.example.levit.databinding.ItemItensTopoBinding
import com.example.levit.ui.AdaptadorDeLinhas
import com.example.levit.ui.CabecalhoFixo
import com.example.levit.ui.LinhaDeLista
import com.example.levit.ui.divisoria
import com.example.levit.ui.inflar
import com.example.levit.ui.inflarFilho
import androidx.recyclerview.widget.ConcatAdapter
import com.example.levit.databinding.ItemCampoSelectBinding
import com.example.levit.databinding.ItemCampoTextoBinding
import com.example.levit.databinding.ItemRegistroBinding
import com.example.levit.databinding.SheetItemBinding
import com.example.levit.ui.Datas
import com.example.levit.ui.Folha
import com.example.levit.ui.ImportarPlanilha
import com.example.levit.ui.PainelFiltros
import com.example.levit.ui.Prazos
import com.example.levit.ui.Tabelas
import com.example.levit.ui.Vinculos
import com.example.levit.ui.abrirArquivo
import com.example.levit.ui.avisar
import com.example.levit.ui.caixaDeIcone
import com.example.levit.ui.configurarSelect
import com.example.levit.ui.confirmar
import com.example.levit.ui.cor
import com.example.levit.ui.coresDaEtapa
import com.example.levit.ui.escolher
import com.example.levit.ui.mostrarCarregando
import com.example.levit.ui.mostrarEstado
import com.example.levit.ui.pessoaResponsavel
import com.example.levit.ui.seloPrazo
import com.google.android.material.chip.Chip
import com.google.android.material.tabs.TabLayout
import com.google.gson.JsonElement
import com.google.gson.JsonNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.text.Collator
import java.text.NumberFormat
import java.util.Calendar
import java.util.Locale
import kotlin.math.ceil

/**
 * Itens de uma tabela (ModuleRecords.jsx): lista ou quadro, busca, "só os
 * meus", ordenação, pesquisa avançada, formulário com os 8 tipos de coluna e,
 * nas tabelas de arquivos, enviar, baixar e excluir.
 */
class ItensFragment : HamburgerMenuBaseFragment() {

    override val pageLayoutResId: Int = R.layout.fragment_itens

    /** Uma coluna mostrada no cartão (as colunas da tabela do web). */
    private data class ColunaVisivel(val chave: String, val titulo: String, val tipo: String, val campo: Coluna?)

    private var _binding: FragmentItensBinding? = null
    private val binding get() = checkNotNull(_binding)
    private val chamador by lazy { ChamadorApi(requireContext().applicationContext) }
    private val api by lazy { ApiClient.registroApi(requireContext().applicationContext) }
    private val moduloApi by lazy { ApiClient.moduloApi(requireContext().applicationContext) }
    private val equipeApi by lazy { ApiClient.equipeApi(requireContext().applicationContext) }
    private val tabelaId by lazy { arguments?.getString("moduloId").orEmpty() }
    private val preferencias by lazy { requireContext().getSharedPreferences("levit_visao", Context.MODE_PRIVATE) }

    private var tabela: TabelaDetalhe? = null
    private var erroTabela: ResultadoApi.Erro? = null
    private var registros: List<Registro>? = null
    private var erroRegistros: ResultadoApi.Erro? = null
    private var pessoas: List<Pessoa>? = null
    private val titulosLigados = mutableMapOf<String, Map<String, String?>>()
    private var busca = ""
    private var tarefaBusca: Job? = null
    private var soMeus = false
    private var ordem: Pair<String, Boolean>? = null // chave, crescente
    private var pagina = 1
    private var etapaAtiva: String? = null
    private var itemDoLink: String? = null
    private lateinit var filtros: PainelFiltros

    private var aoEscolherArquivo: ((Uri?) -> Unit)? = null
    private val escolherArquivo = registerForActivityResult(ActivityResultContracts.OpenDocument()) { aoEscolherArquivo?.invoke(it) }

    private val campos get() = tabela?.campos.orEmpty()
    private val ehArquivo get() = tabela?.tipo == "arquivo"
    private val camposResponsavel get() = campos.filter { it.tipo == "responsavel" }
    private val camposLista get() = campos.filter { it.tipo == "selecao" && it.listaDeOpcoes.isNotEmpty() }
    private val usuarioId by lazy { authRepository.usuarioLogado()?.id }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        if (!menuFoiMontado()) return
        val paginaRaiz = view.findViewById<FrameLayout>(R.id.pageContentContainer).getChildAt(0)
        _binding = FragmentItensBinding.bind(paginaRaiz)
        // Só na primeira vez: ao voltar de outra tela o link já foi usado.
        if (savedInstanceState == null && itemDoLink == null) itemDoLink = arguments?.getString("itemId")
        topo = ItemItensTopoBinding.inflate(layoutInflater, binding.lista, false)
        rodape = ItemPaginacaoBinding.inflate(layoutInflater, binding.lista, false)
        binding.lista.adapter = ConcatAdapter(CabecalhoFixo(topo.root), adaptador, CabecalhoFixo(rodape.root))

        ligarBotoesDaTela()
        ligarControlesDaLista()
        filtros = PainelFiltros(requireActivity(), viewLifecycleOwner, topo.painelFiltros) {
            pagina = 1
            atualizarBotaoFiltros()
            carregarRegistros(adiado = true)
        }
    }

    private fun ligarBotoesDaTela() {
        binding.btnVoltar.setOnClickListener { findNavController().popBackStack() }
        binding.btnConfigurar.setOnClickListener { findNavController().navigate(R.id.novoModuloFragment, bundleOf("tabelaId" to tabelaId)) }
        binding.btnNovo.setOnClickListener { abrirFormulario(null) }
        binding.btnImportar.setOnClickListener { tabela?.let { t -> ImportarPlanilha(requireActivity(), t, pessoas.orEmpty(), titulosLigados) { carregarRegistros() }.abrir() } }
    }

    /** Busca, filtros, visão lista/quadro, ordem, paginação e abas do quadro. */
    private fun ligarControlesDaLista() {
        topo.etBusca.doAfterTextChanged { texto ->
            // Espera a digitação parar, como o useDebouncedValue do web.
            tarefaBusca?.cancel()
            tarefaBusca = viewLifecycleOwner.lifecycleScope.launch {
                delay(300)
                busca = texto?.toString()?.trim().orEmpty()
                pagina = 1
                carregarRegistros()
            }
        }
        topo.cbSoMeus.setOnCheckedChangeListener { _, marcado -> soMeus = marcado; pagina = 1; renderizar() }
        topo.btnFiltrar.setOnClickListener {
            topo.painelFiltros.isVisible = !topo.painelFiltros.isVisible
            atualizarBotaoFiltros()
        }
        topo.grupoVisao.addOnButtonCheckedListener { _, id, marcado ->
            if (marcado) mudarPreferencia(visao = if (id == topo.btnQuadro.id) "quadro" else "lista")
        }
        topo.btnDirecao.setOnClickListener {
            ordem = ordem?.let { it.first to !it.second }
            pagina = 1
            renderizar()
        }
        rodape.btnAnterior.setOnClickListener { pagina--; renderizar(); binding.lista.scrollToPosition(0) }
        rodape.btnProxima.setOnClickListener { pagina++; renderizar(); binding.lista.scrollToPosition(0) }
        topo.abasQuadro.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                if (montandoAbas) return
                etapaAtiva = tab.tag as? String
                renderizarConteudo()
            }
            override fun onTabUnselected(tab: TabLayout.Tab) = Unit
            override fun onTabReselected(tab: TabLayout.Tab) = Unit
        })
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    override fun onResume() {
        super.onResume()
        if (menuFoiMontado()) carregarTabela()
    }

    // --- Carregamento -------------------------------------------------------------

    private fun carregarTabela() {
        if (tabela == null) mostrar(listOf(estadoCarregando()))
        viewLifecycleOwner.lifecycleScope.launch {
            when (val r = chamador.chamar { moduloApi.detalhesTabela(tabelaId) }) {
                is ResultadoApi.Sucesso -> {
                    val t = r.dados
                    // Módulos especiais têm telas próprias.
                    if (t.tipo == "recrutamento") {
                        findNavController().navigate(
                            R.id.recrutamentoFragment, null,
                            androidx.navigation.navOptions { popUpTo(R.id.registrosFragment) { inclusive = true } }
                        )
                        return@launch
                    }
                    if (t.tipo == "financeiro") {
                        findNavController().navigate(
                            R.id.financeiroFragment, bundleOf("moduloId" to t.id),
                            androidx.navigation.navOptions { popUpTo(R.id.registrosFragment) { inclusive = true } }
                        )
                        return@launch
                    }
                    tabela = t
                    erroTabela = null
                    filtros.definirTabela(t)
                    cabecalho()
                    carregarApoio()
                    carregarRegistros()
                }
                is ResultadoApi.Erro -> {
                    if (r.sessaoExpirada) return@launch redirecionarParaLogin()
                    erroTabela = r
                    renderizar()
                }
            }
        }
    }

    /** Nomes da equipe (coluna Responsável) e dos itens das tabelas ligadas. */
    private fun carregarApoio() {
        if (camposResponsavel.isNotEmpty() && pessoas == null) viewLifecycleOwner.lifecycleScope.launch {
            (chamador.chamar { equipeApi.pessoas() } as? ResultadoApi.Sucesso)?.let { pessoas = it.dados; renderizar() }
        }
        val ligadas = campos.filter { it.tipo == "vinculo" && !it.moduloVinculadoId.isNullOrBlank() && it.moduloVinculado?.acesso != false }
            .mapNotNull { it.moduloVinculadoId }.distinct()
        ligadas.forEach { alvo ->
            viewLifecycleOwner.lifecycleScope.launch {
                (chamador.chamar { api.titulos(alvo) } as? ResultadoApi.Sucesso)?.let { r ->
                    titulosLigados[alvo] = r.dados.associate { it.id to it.titulo }
                    renderizar()
                }
            }
        }
    }

    private var tarefaRegistros: Job? = null

    private fun carregarRegistros(adiado: Boolean = false) {
        tarefaRegistros?.cancel()
        tarefaRegistros = viewLifecycleOwner.lifecycleScope.launch {
            if (adiado) delay(300)
            val filtrosJson = filtros.paraApi()
            val r = chamador.chamar { api.listar(tabelaId, busca.ifEmpty { null }, filtrosJson) }
            when (r) {
                is ResultadoApi.Sucesso -> { registros = r.dados; erroRegistros = null }
                is ResultadoApi.Erro -> { if (r.sessaoExpirada) return@launch redirecionarParaLogin(); erroRegistros = r }
            }
            renderizar()
            // Link direto para um item (vindo de outra tabela ligada): abre o item.
            itemDoLink?.let { alvo ->
                itemDoLink = null
                registros?.firstOrNull { it.id == alvo }?.takeIf { !ehArquivo }?.let { abrirFormulario(it) }
            }
        }
    }

    private fun cabecalho() {
        val t = tabela ?: return
        binding.flIcone.removeAllViews()
        binding.flIcone.addView(requireContext().caixaDeIcone(t.icone, ladoDp = 40))
        binding.tvTitulo.text = t.nome
        binding.btnNovo.text = getString(if (ehArquivo) R.string.itens_enviar_arquivo else R.string.itens_novo)
        binding.btnNovo.setIconResource(if (ehArquivo) R.drawable.ic_download else R.drawable.ic_add)
        binding.btnImportar.isVisible = !ehArquivo && campos.isNotEmpty()
        topo.btnFiltrar.isVisible = !ehArquivo && campos.isNotEmpty()
        topo.cbSoMeus.isVisible = camposResponsavel.isNotEmpty()
        atualizarBotaoFiltros()
    }

    private fun atualizarBotaoFiltros() {
        val n = filtros.totalRegras()
        topo.btnFiltrar.text = if (n > 0) getString(R.string.itens_filtros_n, n) else getString(R.string.itens_filtrar)
        val ativo = n > 0 || topo.painelFiltros.isVisible
        topo.btnFiltrar.backgroundTintList = androidx.core.content.ContextCompat.getColorStateList(
            requireContext(), if (ativo) R.color.primary_100 else R.color.surface
        )
    }

    // --- Valores ------------------------------------------------------------------

    private fun bruto(r: Registro, campoId: String?): JsonElement? =
        campoId?.let { r.dados?.get(it) }?.takeIf { !it.isJsonNull }

    private fun texto(e: JsonElement?): String? = when {
        e == null || e.isJsonNull -> null
        e.isJsonPrimitive -> e.asString
        e.isJsonArray -> e.asJsonArray.joinToString(", ") { if (it.isJsonPrimitive) it.asString else it.toString() }
        else -> e.toString()
    }?.takeIf { it.isNotEmpty() }

    private fun idsLigados(e: JsonElement?): List<String> = when {
        e == null || e.isJsonNull -> emptyList()
        e.isJsonArray -> e.asJsonArray.mapNotNull { it.takeIf { x -> x.isJsonPrimitive }?.asString?.takeIf { s -> s.isNotEmpty() } }
        e.isJsonPrimitive -> listOfNotNull(e.asString.takeIf { it.isNotEmpty() })
        else -> emptyList()
    }

    private val nomes get() = pessoas.orEmpty().associate { it.id to it.nome.orEmpty() }

    private fun concluido(r: Registro) = Prazos.itemConcluido(campos.filter { it.tipo == "selecao" }.map { texto(bruto(r, it.id)) })

    /** Nomes dos itens ligados; sem os nomes (carregando ou sem acesso), só quantos são. */
    private fun rotuloDosLigados(ids: List<String>, titulos: Map<String, String?>?): String? {
        val contexto = requireContext()
        if (titulos == null) return Vinculos.contagem(ids)?.em(contexto)
        return ids.takeIf { it.isNotEmpty() }?.let { Vinculos.nomes(it, titulos).joinToString(", ") { nome -> nome.em(contexto) } }
    }

    /** O valor como texto, para ordenar e para mostrar (ler de cada coluna do web). */
    private fun ler(r: Registro, c: ColunaVisivel): String? = when (c.chave) {
        "criado_em" -> r.criadoEm
        "arquivo_nome" -> r.arquivoNome
        "arquivo_tamanho" -> r.arquivoTamanho?.toString()
        else -> when (c.tipo) {
            "responsavel" -> texto(bruto(r, c.chave))?.let { nomes[it] }
            "vinculo" -> rotuloDosLigados(idsLigados(bruto(r, c.chave)), titulosLigados[c.campo?.moduloVinculadoId])
            else -> texto(bruto(r, c.chave))
        }
    }

    /** Até 5 colunas, mas Responsável e Prazo sempre aparecem; e "Criado em". */
    private fun colunasVisiveis(): List<ColunaVisivel> {
        if (ehArquivo) return listOf(
            ColunaVisivel("arquivo_nome", getString(R.string.itens_arquivo), "texto", null),
            ColunaVisivel("arquivo_tamanho", getString(R.string.itens_tamanho), "numero", null),
            ColunaVisivel("criado_em", getString(R.string.itens_criado_em), "data", null),
        )
        return campos.filterIndexed { i, c -> i < 5 || c.tipo == "responsavel" || c.tipo == "prazo" }
            .map { ColunaVisivel(it.id.orEmpty(), it.nome.orEmpty(), it.tipo ?: "texto", it) } +
            ColunaVisivel("criado_em", getString(R.string.itens_criado_em), "data", null)
    }

    private val colator = Collator.getInstance(Locale.forLanguageTag("pt-BR")).apply { strength = Collator.PRIMARY }

    /** compararRegistros: vazios sempre no fim, números como número, texto em pt-BR. */
    private fun ordenar(lista: List<Registro>, colunas: List<ColunaVisivel>): List<Registro> {
        val (chave, crescente) = ordem ?: return lista
        val coluna = colunas.firstOrNull { it.chave == chave } ?: return lista
        fun valor(r: Registro): Any? = ler(r, coluna)?.let { v -> if (coluna.tipo == "numero") v.toDoubleOrNull() else v }
        return lista.sortedWith { a, b ->
            val va = valor(a); val vb = valor(b)
            when {
                va == null && vb == null -> 0
                va == null -> 1
                vb == null -> -1
                else -> {
                    val r = if (va is Double && vb is Double) va.compareTo(vb) else colator.compare(va.toString(), vb.toString())
                    if (crescente) r else -r
                }
            }
        }
    }

    // --- Visão (lista/quadro), lembrada por tabela -------------------------------------

    private fun preferencia(): JSONObject = runCatching { JSONObject(preferencias.getString(tabelaId, "{}") ?: "{}") }.getOrDefault(JSONObject())

    private fun mudarPreferencia(visao: String? = null, campo: String? = null) {
        val p = preferencia()
        visao?.let { p.put("visao", it) }
        campo?.let { p.put("campo", it) }
        preferencias.edit { putString(tabelaId, p.toString()) }
        etapaAtiva = null
        renderizar()
    }

    private val podeQuadro get() = !ehArquivo && camposLista.isNotEmpty()
    private val visao get() = if (podeQuadro && preferencia().optString("visao") == "quadro") "quadro" else "lista"

    /** Sem escolha salva, agrupa pela lista que tem uma opção de conclusão (o andamento do trabalho). */
    private val campoGrupo: Coluna?
        get() = camposLista.firstOrNull { it.id == preferencia().optString("campo") }
            ?: camposLista.firstOrNull { c -> c.listaDeOpcoes.any { Prazos.opcaoDeConclusao(it) } }
            ?: camposLista.firstOrNull()

    // --- Tela -----------------------------------------------------------------------

    private fun filtrados(): List<Registro> {
        val todos = registros.orEmpty()
        if (!soMeus || camposResponsavel.isEmpty()) return todos
        return todos.filter { r -> camposResponsavel.any { texto(bruto(r, it.id)) == usuarioId } }
    }

    // --- Lista reciclada -------------------------------------------------------------

    private lateinit var topo: ItemItensTopoBinding
    private lateinit var rodape: ItemPaginacaoBinding

    /** Linhas: cartão da lista, cartão do quadro, coluna vazia do quadro ou um estado. */
    private sealed interface Linha : LinhaDeLista {
        data class Cartao(val r: Registro, val versao: Int) : Linha {
            override val chave get() = r.id
            override val tipo get() = TIPO_CARTAO
        }
        data class Quadro(val r: Registro, val versao: Int) : Linha {
            override val chave get() = r.id
            override val tipo get() = TIPO_QUADRO
        }
        data class EtapaVazia(override val chave: String) : Linha {
            override val tipo get() = TIPO_VAZIA
        }
        data class Estado(
            val titulo: String, val descricao: String?, val icone: Int, val acao: String?,
            val erro: Boolean, val carregando: Boolean, val versao: Int
        ) : Linha {
            override val chave get() = "estado"
            override val tipo get() = TIPO_ESTADO
        }
    }

    private var versao = 0
    private var acaoDoEstado: (() -> Unit)? = null
    private var colunasAtuais: List<ColunaVisivel> = emptyList()

    // Contexto do quadro, calculado a cada renderização e usado ao preencher os cartões.
    private var grupoDoQuadro: Coluna? = null
    private var etapasDoQuadro: List<Pair<String, String>> = emptyList()
    private var campoTituloQuadro: Coluna? = null
    private var detalhesDoQuadro: List<Coluna> = emptyList()
    private var campoPrazoQuadro: Coluna? = null
    private var campoRespQuadro: Coluna? = null

    private val adaptador = AdaptadorDeLinhas<Linha>(
        criar = { pai, tipo ->
            when (tipo) {
                TIPO_CARTAO, TIPO_QUADRO -> ItemRegistroBinding.inflate(layoutInflater, pai, false)
                TIPO_VAZIA -> ItemEtapaVaziaBinding.inflate(layoutInflater, pai, false)
                else -> ItemEstadoListaBinding.inflate(layoutInflater, pai, false)
            }
        },
        ligar = { b, linha ->
            when (linha) {
                is Linha.Cartao -> ligarCartao(b as ItemRegistroBinding, linha.r)
                is Linha.Quadro -> ligarCartaoDoQuadro(b as ItemRegistroBinding, linha.r)
                is Linha.EtapaVazia -> Unit
                is Linha.Estado -> (b as ItemEstadoListaBinding).conteudo.let { c ->
                    if (linha.carregando) c.mostrarCarregando(5)
                    else c.mostrarEstado(linha.titulo, linha.descricao, linha.icone, linha.acao, linha.erro) { acaoDoEstado?.invoke() }
                }
            }
        }
    )

    private fun mostrar(linhas: List<Linha>) = adaptador.submitList(linhas)

    private fun estadoCarregando() = Linha.Estado("", null, 0, null, erro = false, carregando = true, versao = versao)

    private fun estado(titulo: String, descricao: String?, icone: Int, acao: String?, erro: Boolean = false, aoAgir: (() -> Unit)? = null): Linha.Estado {
        acaoDoEstado = aoAgir
        return Linha.Estado(titulo, descricao, icone, acao, erro, carregando = false, versao = versao)
    }

    private fun renderizar() {
        versao++
        val t = tabela
        binding.barraAcoes.isVisible = t != null
        val total = registros?.size ?: 0
        val lista = filtrados()
        binding.tvSubtitulo.text = if (soMeus) getString(R.string.itens_com_voce, lista.size,
            resources.getQuantityString(R.plurals.tab_n_itens, total, total))
        else resources.getQuantityString(R.plurals.tab_n_itens, total, total)

        renderizarControles(t)

        val e = erroTabela
        when {
            e != null -> mostrar(listOf(estado(getString(R.string.itens_nao_abriu_titulo), e.mensagem, R.drawable.ic_warning,
                getString(R.string.tab_tentar), erro = true) { carregarTabela() }))
            t == null || (registros == null && erroRegistros == null) -> mostrar(listOf(estadoCarregando()))
            registros == null -> mostrar(listOf(estado(getString(R.string.itens_erro_carregar), erroRegistros?.mensagem,
                R.drawable.ic_warning, getString(R.string.tab_tentar), erro = true) { carregarRegistros() }))
            lista.isEmpty() && total > 0 -> mostrar(listOf(estado(getString(R.string.itens_nenhum_com_voce),
                getString(R.string.itens_nenhum_com_voce_desc), R.drawable.ic_person_search, getString(R.string.itens_ver_todos)) {
                topo.cbSoMeus.isChecked = false
            }))
            total == 0 -> mostrar(listOf(estadoSemItens()))
            else -> renderizarConteudo()
        }
    }

    /** Alternância lista/quadro, agrupamento e ordem: o que a barra de cima mostra para a tabela atual. */
    private fun renderizarControles(t: TabelaDetalhe?) {
        topo.grupoVisao.isVisible = podeQuadro
        topo.grupoVisao.check(if (visao == "quadro") topo.btnQuadro.id else topo.btnLista.id)
        topo.campoAgrupar.isVisible = visao == "quadro" && camposLista.size > 1
        campoGrupo?.let { g -> topo.acAgrupar.configurarSelect(camposLista.map { it.id.orEmpty() to it.nome.orEmpty() }, g.id) { mudarPreferencia(campo = it) } }
        val colunas = colunasVisiveis()
        topo.linhaOrdem.isVisible = visao == "lista" && t != null
        topo.acOrdem.configurarSelect(listOf("" to getString(R.string.itens_ordem_padrao)) + colunas.map { it.chave to it.titulo }, ordem?.first ?: "") {
            ordem = if (it.isEmpty()) null else it to (ordem?.second ?: true)
            pagina = 1
            renderizar()
        }
        topo.btnDirecao.isVisible = ordem != null
        topo.btnDirecao.text = getString(if (ordem?.second != false) R.string.ic_arrow_upward else R.string.ic_arrow_downward)
        topo.btnDirecao.contentDescription = getString(if (ordem?.second != false) R.string.itens_crescente else R.string.itens_decrescente)
        topo.abasQuadro.isVisible = false
        rodape.linhaPaginas.isVisible = false
    }

    /** Tabela sem nenhum item: explica se é por busca, filtro ou se ainda não há nada. */
    private fun estadoSemItens(): Linha.Estado {
        val filtrando = filtros.paraApi() != null
        val buscando = busca.isNotEmpty()
        return estado(
            titulo = getString(if (buscando || filtrando) R.string.itens_nenhum_resultado else R.string.itens_nenhum_ainda),
            descricao = when {
                filtrando -> getString(R.string.itens_filtrando_desc)
                buscando -> getString(R.string.itens_busca_desc, busca)
                ehArquivo -> getString(R.string.itens_arquivo_desc)
                else -> getString(R.string.itens_vazio_desc)
            },
            icone = R.drawable.ic_inventory,
            acao = when {
                filtrando -> getString(R.string.itens_limpar_filtros)
                buscando -> null
                ehArquivo -> getString(R.string.itens_enviar_arquivo)
                else -> getString(R.string.itens_criar_primeiro)
            }
        ) { if (filtrando) filtros.limpar() else abrirFormulario(null) }
    }

    private fun renderizarConteudo() {
        if (visao == "quadro") renderizarQuadro() else renderizarLista()
    }

    private fun renderizarLista() {
        val colunas = colunasVisiveis()
        colunasAtuais = colunas
        val ordenados = ordenar(filtrados(), colunas)
        val paginas = maxOf(1, ceil(ordenados.size / POR_PAGINA.toDouble()).toInt())
        pagina = pagina.coerceIn(1, paginas)
        mostrar(ordenados.drop((pagina - 1) * POR_PAGINA).take(POR_PAGINA).map { Linha.Cartao(it, versao) })
        rodape.linhaPaginas.isVisible = paginas > 1
        rodape.tvPagina.text = getString(R.string.itens_pagina, pagina, paginas)
        rodape.btnAnterior.isEnabled = pagina > 1
        rodape.btnProxima.isEnabled = pagina < paginas
    }

    private fun ligarCartao(v: ItemRegistroBinding, r: Registro) {
        val semCriado = colunasAtuais.filter { it.chave != "criado_em" }
        val primeira = semCriado.firstOrNull()
        v.tvTitulo.text = primeira?.let { valorParaMostrar(r, it) } ?: "--"
        v.llDetalhes.removeAllViews()
        semCriado.drop(1).forEach { c -> v.llDetalhes.addView(linhaDeDetalhe(r, c, v.llDetalhes)) }
        v.grupoSelos.removeAllViews()
        v.grupoSelos.isVisible = false
        v.btnMover.isVisible = false
        v.tvCriadoEm.isVisible = true
        v.btnAcao.isVisible = true
        v.btnExcluir.isVisible = true
        v.tvCriadoEm.text = Datas.ler(r.criadoEm)?.let { java.text.SimpleDateFormat("dd/MM/yyyy", Locale.US).format(it) } ?: "--"
        if (ehArquivo) {
            v.btnAcao.setIconResource(R.drawable.ic_download)
            v.btnAcao.contentDescription = getString(R.string.itens_baixar)
            v.btnAcao.setOnClickListener { baixar(r) }
            v.areaAbrir.setOnClickListener(null)
            v.areaAbrir.isClickable = false
        } else {
            v.btnAcao.setIconResource(R.drawable.ic_edit)
            v.btnAcao.contentDescription = getString(R.string.itens_editar)
            v.btnAcao.setOnClickListener { abrirFormulario(r) }
            v.areaAbrir.setOnClickListener { abrirFormulario(r) }
        }
        v.btnExcluir.setOnClickListener { excluir(r) }
    }

    /** Texto mostrado na lista: data crua, criado_em em dd/mm/aaaa, tamanho em KB (como a tabela do web). */
    private fun valorParaMostrar(r: Registro, c: ColunaVisivel): String? {
        val valor = ler(r, c) ?: return null
        return when (c.chave) {
            "arquivo_tamanho" -> String.format(Locale.US, "%.2f KB", (r.arquivoTamanho ?: 0.0) / 1024)
            else -> if (c.tipo == "recorrencia") Prazos.rotuloDaRepeticao(valor)?.em(requireContext()) ?: valor else valor
        }
    }

    private fun linhaDeDetalhe(r: Registro, c: ColunaVisivel, pai: ViewGroup): View {
        val b = ItemDetalheRegistroBinding.inflate(layoutInflater, pai, false)
        b.tvRotulo.text = getString(R.string.itens_rotulo_detalhe, c.titulo)
        val valor = ler(r, c)
        val conteudo: View = when {
            valor == null -> requireContext().inflar<View>(R.layout.item_valor_vazio)
            c.tipo == "prazo" -> requireContext().seloPrazo(valor, concluido(r)) ?: requireContext().inflar<TextView>(R.layout.item_valor_detalhe).apply { text = valor }
            c.tipo == "responsavel" -> requireContext().pessoaResponsavel(valor)
            c.tipo == "vinculo" -> textoDosLigados(r, c)
            else -> requireContext().inflar<TextView>(R.layout.item_valor_detalhe).apply { text = valorParaMostrar(r, c) }
        }
        b.flValor.addView(conteudo)
        return b.root
    }

    /** Ligação: cada item ligado é um link para ele na tabela dele. */
    private fun textoDosLigados(r: Registro, c: ColunaVisivel): TextView {
        val alvo = c.campo?.moduloVinculadoId
        val mapa = titulosLigados[alvo]
        val ids = idsLigados(bruto(r, c.chave))
        val texto = SpannableStringBuilder()
        if (mapa == null || alvo == null) texto.append(Vinculos.contagem(ids)?.em(requireContext()))
        else ids.forEachIndexed { i, id ->
            if (i > 0) texto.append(", ")
            if (!mapa.containsKey(id)) { texto.append(getString(R.string.itens_item_excluido)); return@forEachIndexed }
            val inicio = texto.length
            texto.append(Vinculos.tituloDoItem(mapa[id]).em(requireContext()))
            texto.setSpan(object : ClickableSpan() {
                override fun onClick(widget: View) = abrirItem(alvo, id)
                override fun updateDrawState(ds: TextPaint) { ds.color = requireContext().cor(R.color.primary_700); ds.isUnderlineText = false }
            }, inicio, texto.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        return requireContext().inflar<TextView>(R.layout.item_valor_detalhe).apply {
            maxLines = Int.MAX_VALUE
            text = texto
            movementMethod = LinkMovementMethod.getInstance()
        }
    }

    // --- Quadro (QuadroItens.jsx): cada opção da lista vira uma coluna --------------------

    private var montandoAbas = false

    private fun etapaDe(r: Registro, grupo: Coluna) = texto(bruto(r, grupo.id))?.takeIf { it in grupo.listaDeOpcoes } ?: ""

    private fun renderizarQuadro() {
        val grupo = campoGrupo ?: return renderizarLista()
        val itens = ordenar(filtrados(), colunasVisiveis())
        val opcoes = grupo.listaDeOpcoes
        val temSemValor = itens.any { texto(bruto(it, grupo.id)) !in opcoes }
        val etapas = opcoes.map { it to it } + if (temSemValor) listOf("" to getString(R.string.itens_sem_grupo, grupo.nome.orEmpty().lowercase())) else emptyList()
        if (etapas.none { it.first == etapaAtiva }) etapaAtiva = etapas.first().first

        montandoAbas = true
        val abas = topo.abasQuadro
        abas.isVisible = true
        abas.removeAllTabs()
        etapas.forEachIndexed { indice, (valor, rotulo) ->
            val corPonto = if (valor.isEmpty()) R.color.faint else coresDaEtapa(indice, opcoes.size).first
            val n = itens.count { etapaDe(it, grupo) == valor }
            val titulo = SpannableStringBuilder("●  $rotulo  $n").apply {
                setSpan(android.text.style.ForegroundColorSpan(requireContext().cor(corPonto)), 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            abas.addTab(abas.newTab().setText(titulo).setTag(valor), valor == etapaAtiva)
        }
        montandoAbas = false

        // Título do cartão: a primeira coluna de texto; o resto vira detalhe (até 2).
        val campoPrazo = campos.firstOrNull { it.tipo == "prazo" }
        val campoResp = campos.firstOrNull { it.tipo == "responsavel" }
        val especiais = setOf(grupo.id, campoPrazo?.id, campoResp?.id)
        grupoDoQuadro = grupo
        etapasDoQuadro = etapas
        campoPrazoQuadro = campoPrazo
        campoRespQuadro = campoResp
        campoTituloQuadro = campos.firstOrNull { it.tipo == "texto" && it.id !in especiais } ?: campos.firstOrNull { it.id !in especiais }
        detalhesDoQuadro = campos.filter { it.id !in especiais && it.id != campoTituloQuadro?.id }.take(2)

        val daEtapa = itens.filter { etapaDe(it, grupo) == etapaAtiva }
        mostrar(if (daEtapa.isEmpty()) listOf(Linha.EtapaVazia(etapaAtiva.orEmpty())) else daEtapa.map { Linha.Quadro(it, versao) })
    }

    private fun ligarCartaoDoQuadro(v: ItemRegistroBinding, r: Registro) {
        val grupo = grupoDoQuadro ?: return
        val tituloCampo = campoTituloQuadro?.let { ColunaVisivel(it.id.orEmpty(), it.nome.orEmpty(), it.tipo ?: "texto", it) }
        v.tvTitulo.text = tituloCampo?.let { textoNoQuadro(r, it) } ?: getString(R.string.itens_sem_titulo)
        v.llDetalhes.removeAllViews()
        detalhesDoQuadro.forEach { c ->
            val cv = ColunaVisivel(c.id.orEmpty(), c.nome.orEmpty(), c.tipo ?: "texto", c)
            textoNoQuadro(r, cv)?.let { valor ->
                v.llDetalhes.addView(v.llDetalhes.inflarFilho<TextView>(R.layout.item_detalhe_quadro).apply {
                    text = getString(R.string.itens_detalhe_quadro, c.nome, valor)
                })
            }
        }
        v.grupoSelos.removeAllViews()
        val prazo = campoPrazoQuadro?.let { texto(bruto(r, it.id)) }
        val responsavel = campoRespQuadro?.let { texto(bruto(r, it.id)) }?.let { nomes[it] }
        prazo?.let { requireContext().seloPrazo(it, concluido(r)) }?.let { v.grupoSelos.addView(it) }
        responsavel?.let { v.grupoSelos.addView(requireContext().pessoaResponsavel(it, comNome = false)) }
        v.grupoSelos.isVisible = v.grupoSelos.childCount > 0
        v.tvCriadoEm.isVisible = false
        v.btnAcao.isVisible = false
        v.btnExcluir.isVisible = false
        v.btnMover.isVisible = true
        v.areaAbrir.isClickable = true
        v.areaAbrir.setOnClickListener { abrirFormulario(r) }
        val atual = etapaDe(r, grupo)
        v.btnMover.text = etapasDoQuadro.first { it.first == atual }.second
        v.btnMover.contentDescription = getString(R.string.itens_grupo_de, grupo.nome, v.tvTitulo.text)
        v.btnMover.setOnClickListener {
            requireContext().escolher(grupo.nome.orEmpty(), etapasDoQuadro, atual) { novo -> moverItem(r, grupo, novo) }
        }
    }

    /** formatar de QuadroItens.jsx: data em dd/mm/aaaa, número em pt-BR, repetição pelo rótulo. */
    private fun textoNoQuadro(r: Registro, c: ColunaVisivel): String? {
        if (c.tipo == "vinculo" || c.tipo == "responsavel") return ler(r, c)
        val valor = texto(bruto(r, c.chave)) ?: return null
        return when (c.tipo) {
            "data" -> Datas.ler(valor)?.let { java.text.SimpleDateFormat("dd/MM/yyyy", Locale.US).format(it) } ?: valor
            "numero" -> valor.toDoubleOrNull()?.let { NumberFormat.getNumberInstance(Locale.forLanguageTag("pt-BR")).format(it) } ?: valor
            "recorrencia" -> Prazos.rotuloDaRepeticao(valor)?.em(requireContext()) ?: valor
            else -> valor
        }
    }

    /** Mover no quadro = trocar o valor da coluna de lista. Otimista: volta se a API recusar. */
    private fun moverItem(r: Registro, grupo: Coluna, valor: String) {
        val antigos = r.dados?.deepCopy() ?: com.google.gson.JsonObject()
        val novos = antigos.deepCopy().apply { add(grupo.id, if (valor.isEmpty()) JsonNull.INSTANCE else com.google.gson.JsonPrimitive(valor)) }
        fun aplicar(dados: com.google.gson.JsonObject) {
            registros = registros?.map { if (it.id == r.id) it.copy(dados = dados) else it }
            renderizar()
        }
        aplicar(novos)
        viewLifecycleOwner.lifecycleScope.launch {
            val corpo = RegistroRequest(novos.entrySet().associate { it.key to it.value })
            val res = chamador.chamarSemCorpo { api.atualizar(tabelaId, r.id, corpo) }
            if (res is ResultadoApi.Erro) {
                if (res.sessaoExpirada) return@launch redirecionarParaLogin()
                aplicar(antigos)
                requireContext().avisar(res.mensagem, longo = true)
            }
        }
    }

    // --- Excluir e baixar -----------------------------------------------------------------

    private fun excluir(r: Registro) {
        requireContext().confirmar(
            titulo = getString(if (ehArquivo) R.string.itens_excluir_arquivo_titulo else R.string.itens_excluir_item_titulo),
            mensagem = if (ehArquivo) getString(R.string.itens_excluir_arquivo_msg, r.arquivoNome ?: getString(R.string.itens_este_arquivo))
            else getString(R.string.itens_excluir_item_msg),
            rotuloConfirmar = getString(R.string.itens_excluir)
        ) {
            viewLifecycleOwner.lifecycleScope.launch {
                val res = chamador.chamarSemCorpo {
                    if (ehArquivo) api.excluirArquivo(tabelaId, r.id) else api.excluir(tabelaId, r.id)
                }
                when (res) {
                    is ResultadoApi.Sucesso -> {
                        registros = registros?.filterNot { it.id == r.id }
                        renderizar()
                        requireContext().avisar(getString(if (ehArquivo) R.string.itens_arquivo_excluido else R.string.itens_excluido))
                    }
                    is ResultadoApi.Erro -> if (res.sessaoExpirada) redirecionarParaLogin() else requireContext().avisar(res.mensagem, longo = true)
                }
            }
        }
    }

    private fun baixar(r: Registro) {
        viewLifecycleOwner.lifecycleScope.launch {
            when (val res = baixarApi(requireContext()) { api.baixarArquivo(tabelaId, r.id) }) {
                is ResultadoApi.Sucesso -> {
                    val nome = r.arquivoNome ?: "arquivo"
                    val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(nome.substringAfterLast('.', "").lowercase())
                    requireActivity().abrirArquivo(res.dados, nome, mime ?: "application/octet-stream")
                }
                is ResultadoApi.Erro -> if (res.sessaoExpirada) redirecionarParaLogin() else requireContext().avisar(res.mensagem, longo = true)
            }
        }
    }

    // --- Formulário do item --------------------------------------------------------------

    private fun abrirFormulario(registro: Registro?) {
        val t = tabela ?: return
        val b = SheetItemBinding.inflate(layoutInflater)
        val iniciais: Map<String, Any?> = registro?.dados?.entrySet()?.associate { it.key to it.value }.orEmpty()
        val dados = iniciais.toMutableMap()
        var arquivo: Pair<Uri, String>? = null

        b.tvTitulo.setText(when {
            ehArquivo -> R.string.itens_enviar_arquivo
            registro != null -> R.string.itens_editar_titulo
            else -> R.string.itens_novo_titulo
        })
        b.btnSalvar.setText(rotuloDoBotaoSalvar(registro))
        b.blocoArquivo.isVisible = ehArquivo

        fun representacao(m: Map<String, Any?>) = m.filterValues { it != null && it != JsonNull.INSTANCE && it != "" }
            .mapValues { (_, v) -> if (v is JsonElement) texto(v) ?: "" else v.toString() }
        val folha = Folha(requireContext(), b.root) { if (ehArquivo) arquivo != null else representacao(dados) != representacao(iniciais) }

        if (ehArquivo) {
            aoEscolherArquivo = { uri ->
                uri?.let {
                    val nome = nomeDoArquivo(it)
                    arquivo = it to nome
                    b.tvArquivo.text = nome
                }
            }
            b.btnArquivo.setOnClickListener { escolherArquivo.launch(arrayOf("*/*")) }
            folha.aoFechar { aoEscolherArquivo = null }
        } else {
            if (t.campos.isNullOrEmpty()) {
                b.llCampos.addView(b.llCampos.inflarFilho<TextView>(R.layout.item_sem_colunas))
            }
            campos.forEach { campo -> b.llCampos.addView(campoDoFormulario(campo, dados)) }
        }

        b.btnCancelar.setOnClickListener { folha.fechar() }
        b.btnSalvar.setOnClickListener { salvarItem(b, folha, registro, dados, arquivo) }

        if (registro != null && !ehArquivo) carregarLigadosAEste(registro, b.blocoLigados, folha)
        folha.abrir()
    }

    private fun salvarItem(b: SheetItemBinding, folha: Folha, registro: Registro?, dados: Map<String, Any?>, arquivo: Pair<Uri, String>?) {
        b.tvErro.isVisible = false
        if (ehArquivo && arquivo == null) return mostrarErroDoFormulario(b, getString(R.string.itens_selecione_arquivo))
        b.btnSalvar.isEnabled = false
        b.btnSalvar.setText(R.string.itens_salvando)
        viewLifecycleOwner.lifecycleScope.launch {
            when (val res = enviarItem(registro, dados, arquivo) ?: return@launch) {
                is ResultadoApi.Sucesso -> {
                    folha.dispensar()
                    requireContext().avisar(getString(mensagemDeSucesso(registro)))
                    carregarRegistros()
                }
                is ResultadoApi.Erro -> {
                    if (res.sessaoExpirada) return@launch redirecionarParaLogin()
                    mostrarErroDoFormulario(b, res.mensagem)
                    b.btnSalvar.isEnabled = true
                    b.btnSalvar.setText(rotuloDoBotaoSalvar(registro))
                }
            }
        }
    }

    private fun mostrarErroDoFormulario(b: SheetItemBinding, mensagem: String) {
        b.tvErro.text = mensagem
        b.tvErro.isVisible = true
    }

    private fun mensagemDeSucesso(registro: Registro?) = when {
        ehArquivo -> R.string.itens_arquivo_enviado
        registro != null -> R.string.itens_atualizado
        else -> R.string.itens_criado
    }

    private fun rotuloDoBotaoSalvar(registro: Registro?) = when {
        registro != null -> R.string.itens_atualizar
        ehArquivo -> R.string.itens_enviar
        else -> R.string.itens_criar
    }

    private fun nomeDoArquivo(uri: Uri): String =
        requireContext().contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        } ?: "arquivo"

    /** Envia o arquivo, atualiza o item ou cria um novo, conforme o tipo da tabela. Null: arquivo ausente. */
    private suspend fun enviarItem(registro: Registro?, dados: Map<String, Any?>, arquivo: Pair<Uri, String>?): ResultadoApi<Unit>? = when {
        ehArquivo -> {
            val (uri, nome) = arquivo ?: return null
            val bytes = withContext(Dispatchers.IO) { requireContext().contentResolver.openInputStream(uri)?.use { it.readBytes() } } ?: ByteArray(0)
            val mime = requireContext().contentResolver.getType(uri) ?: "application/octet-stream"
            val parte = MultipartBody.Part.createFormData("arquivo", nome, bytes.toRequestBody(mime.toMediaType()))
            chamador.chamarSemCorpo { api.enviarArquivo(tabelaId, parte) }
        }
        registro != null -> chamador.chamarSemCorpo { api.atualizar(tabelaId, registro.id, RegistroRequest(dados)) }
        else -> chamador.chamarSemCorpo { api.criar(tabelaId, RegistroRequest(dados)) }
    }

    /** Rótulo (nome + tipo) e o campo certo para cada tipo de coluna (renderFieldInput do web). */
    private fun campoDoFormulario(campo: Coluna, dados: MutableMap<String, Any?>): View {
        val rotulo = ItemCampoFormularioBinding.inflate(layoutInflater)
        val bloco = rotulo.root
        rotulo.tvRotulo.text = SpannableStringBuilder(campo.nome.orEmpty()).apply {
            val inicio = length
            append("   ").append(Tabelas.TIPO_NO_FORMULARIO[campo.tipo]?.let { getString(it) }.orEmpty())
            setSpan(android.text.style.ForegroundColorSpan(requireContext().cor(R.color.light_text)), inicio, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            setSpan(android.text.style.AbsoluteSizeSpan(12, true), inicio, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        val id = campo.id.orEmpty()
        val atual = (dados[id] as? JsonElement)?.let { texto(it) } ?: dados[id] as? String

        when (campo.tipo) {
            "texto", "numero" -> campoDeTexto(campo, dados, bloco, id, atual)
            "data", "prazo" -> campoDeData(dados, bloco, id, atual)
            "recorrencia" -> bloco.addView(select(listOf("" to getString(R.string.itens_nao_repete)) + Prazos.repeticoes(requireContext()), atual) { dados[id] = it })
            "responsavel" -> {
                val opcoes = pessoas?.map { p -> p.id to if (p.id == usuarioId) getString(R.string.itens_voce, p.nome) else p.nome.orEmpty() }
                val vazio = if (pessoas == null) getString(R.string.itens_carregando_equipe) else getString(R.string.itens_sem_responsavel)
                bloco.addView(select(listOf("" to vazio) + opcoes.orEmpty(), atual) { dados[id] = it })
            }
            "selecao" -> bloco.addView(select(listOf("" to getString(R.string.itens_selecione)) + campo.listaDeOpcoes.map { it to it }, atual) { dados[id] = it })
            "vinculo" -> bloco.addView(campoVinculo(campo, dados))
        }
        return bloco
    }

    private fun campoDeTexto(campo: Coluna, dados: MutableMap<String, Any?>, bloco: ViewGroup, id: String, atual: String?) {
        val c = ItemCampoTextoBinding.inflate(layoutInflater, bloco, false)
        c.etValor.setText(atual)
        if (campo.tipo == "numero") {
            c.etValor.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL or InputType.TYPE_NUMBER_FLAG_SIGNED
            c.campo.placeholderText = "0"
        } else {
            c.etValor.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            c.campo.placeholderText = getString(R.string.itens_digite, campo.nome.orEmpty().lowercase())
        }
        c.etValor.doAfterTextChanged { dados[id] = it?.toString().orEmpty() }
        bloco.addView(c.root)
    }

    /** Campo que abre o seletor de datas; "Limpar" apaga o valor. */
    private fun campoDeData(dados: MutableMap<String, Any?>, bloco: ViewGroup, id: String, atual: String?) {
        val c = ItemCampoTextoBinding.inflate(layoutInflater, bloco, false)
        c.etValor.isFocusable = false
        c.etValor.isCursorVisible = false
        c.etValor.inputType = InputType.TYPE_NULL
        c.campo.placeholderText = getString(R.string.itens_escolher_data)
        fun mostrar(v: String?) = c.etValor.setText(v?.let { Datas.ler(it) }?.let { java.text.SimpleDateFormat("dd/MM/yyyy", Locale.US).format(it) } ?: v)
        mostrar(atual)
        c.etValor.setOnClickListener {
            val cal = Calendar.getInstance().apply { (dados[id]?.let { (it as? JsonElement)?.let(::texto) ?: it as? String })?.let { Datas.ler(it) }?.let { time = it } }
            DatePickerDialog(requireContext(), { _, a, m, d ->
                val iso = Datas.iso(Calendar.getInstance().apply { set(a, m, d) }.time)
                dados[id] = iso
                mostrar(iso)
            }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).apply {
                setButton(android.content.DialogInterface.BUTTON_NEUTRAL, getString(R.string.itens_limpar)) { _, _ ->
                    dados[id] = ""
                    mostrar(null)
                }
            }.show()
        }
        bloco.addView(c.root)
    }

    private fun select(opcoes: List<Pair<String, String>>, atual: String?, aoEscolher: (String) -> Unit): View {
        val c = ItemCampoSelectBinding.inflate(layoutInflater)
        c.acValor.configurarSelect(opcoes, atual.orEmpty(), aoEscolher)
        return c.root
    }

    /** CampoVinculo.jsx: um item (lista com "Nenhum") ou vários (etiquetas removíveis + lista para adicionar). */
    private fun campoVinculo(campo: Coluna, dados: MutableMap<String, Any?>): View {
        val id = campo.id.orEmpty()
        val alvo = campo.moduloVinculado
        if (alvo?.acesso == false) return requireContext().inflar<TextView>(R.layout.item_sem_acesso_tabela).apply {
            text = getString(R.string.itens_sem_acesso_tabela, alvo.nome)
        }
        val vinculo = ItemCampoVinculoBinding.inflate(layoutInflater)
        val bloco = vinculo.root
        val mapa = titulosLigados[campo.moduloVinculadoId]
        val opcoes = mapa?.map { (itemId, titulo) -> itemId to Vinculos.tituloDoItem(titulo).em(requireContext()) }.orEmpty()
        val vazio = when {
            mapa == null -> getString(R.string.itens_carregando)
            mapa.isEmpty() -> getString(R.string.itens_tabela_sem_itens, alvo?.nome ?: "ligada")
            else -> null
        }
        val atual = idsLigados(dados[id] as? JsonElement ?: (dados[id] as? List<*>)?.let { l -> com.google.gson.Gson().toJsonTree(l) }
            ?: (dados[id] as? String)?.let { com.google.gson.JsonPrimitive(it) })

        if (!campo.multiplo) {
            bloco.removeAllViews()
            bloco.addView(select(listOf("" to (vazio ?: getString(R.string.itens_nenhum))) + opcoes, atual.firstOrNull()) { dados[id] = it })
            return bloco
        }
        val escolhidos = atual.toMutableList()
        val grupo = vinculo.grupo
        fun desenhar() {
            grupo.removeAllViews()
            escolhidos.forEach { itemId ->
                val nome = mapa?.get(itemId)?.let { Vinculos.tituloDoItem(it).em(requireContext()) } ?: if (mapa?.containsKey(itemId) == true) Vinculos.tituloDoItem(null).em(requireContext()) else getString(R.string.itens_item_excluido)
                grupo.addView(grupo.inflarFilho<Chip>(R.layout.item_chip_vinculo).apply {
                    text = nome
                    closeIconContentDescription = getString(R.string.itens_tirar, nome)
                    setOnCloseIconClickListener { escolhidos.remove(itemId); dados[id] = escolhidos.toList(); desenhar() }
                })
            }
            grupo.isVisible = escolhidos.isNotEmpty()
            if (bloco.childCount > 1) bloco.removeViewAt(1)
            val restantes = opcoes.filter { it.first !in escolhidos }
            val textoVazio = vazio ?: if (restantes.isEmpty()) getString(R.string.itens_todos_escolhidos) else getString(R.string.itens_adicionar_item)
            val s = ItemCampoSelectBinding.inflate(layoutInflater)
            s.campo.placeholderText = textoVazio
            s.acValor.configurarSelect(restantes, null) { escolhido ->
                if (escolhido.isNotEmpty()) { escolhidos += escolhido; dados[id] = escolhidos.toList(); desenhar() }
            }
            bloco.addView(s.root)
        }
        desenhar()
        return bloco
    }

    /** ItensQueApontam.jsx: os itens de outras tabelas que apontam para este. */
    private fun carregarLigadosAEste(registro: Registro, destino: LinearLayout, folha: Folha) {
        viewLifecycleOwner.lifecycleScope.launch {
            val r = chamador.chamar { api.ligados(tabelaId, registro.id) } as? ResultadoApi.Sucesso ?: return@launch
            if (r.dados.isEmpty()) return@launch
            destino.isVisible = true
            destino.addView(destino.inflarFilho<View>(R.layout.item_ligados_cabecalho))
            r.dados.forEach { grupo ->
                val modulo = grupo.modulo
                val g = ItemLigadosGrupoBinding.inflate(layoutInflater, destino, false)
                g.tvGrupo.text = getString(R.string.itens_pela_coluna, modulo?.nome.orEmpty(), grupo.campoNome.orEmpty(), grupo.total ?: 0)
                grupo.itens.orEmpty().forEachIndexed { i, item ->
                    if (i > 0) g.llItens.addView(requireContext().divisoria())
                    g.llItens.addView(g.llItens.inflarFilho<TextView>(R.layout.item_ligado_linha).apply {
                        text = Vinculos.tituloDoItem(item.titulo).em(requireContext())
                        setOnClickListener {
                            folha.dispensar()
                            modulo?.id?.let { abrirItem(it, item.id) }
                        }
                    })
                }
                if ((grupo.total ?: 0) > grupo.itens.orEmpty().size) {
                    g.tvMais.text = getString(R.string.itens_mostrando_recentes, grupo.itens.orEmpty().size)
                    g.tvMais.isVisible = true
                }
                destino.addView(g.root)
            }
        }
    }

    /** linkDoItem do web: abre a tabela do item e o próprio item. */
    private fun abrirItem(tabelaDestino: String, itemId: String) {
        findNavController().navigate(R.id.registrosFragment, bundleOf("moduloId" to tabelaDestino, "itemId" to itemId))
    }

    companion object {
        private const val POR_PAGINA = 25
        private const val TIPO_CARTAO = 1
        private const val TIPO_QUADRO = 2
        private const val TIPO_VAZIA = 3
        private const val TIPO_ESTADO = 4
    }
}
