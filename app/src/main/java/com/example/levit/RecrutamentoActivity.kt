package com.example.levit

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.view.View
import android.widget.LinearLayout
import androidx.recyclerview.widget.ConcatAdapter
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.levit.data.remote.ApiClient
import com.example.levit.data.remote.dto.Candidato
import com.example.levit.data.remote.dto.Etapa
import com.example.levit.data.remote.dto.Modulo
import com.example.levit.data.remote.dto.MoverFaseRequest
import com.example.levit.data.remote.dto.NovaEtapaRequest
import com.example.levit.data.repository.ModuloRepository
import com.example.levit.data.repository.Resultado
import com.example.levit.data.repository.ResultadoModulo
import com.example.levit.data.repository.baixarApi
import com.example.levit.data.repository.chamarApi
import com.example.levit.data.repository.executarApi
import com.example.levit.databinding.ActivityRecrutamentoBinding
import com.example.levit.databinding.ItemCandidatoBinding
import com.example.levit.databinding.ItemEstadoListaBinding
import com.example.levit.databinding.ItemRecResultadoBuscaBinding
import com.example.levit.ui.AdaptadorDeLinhas
import com.example.levit.ui.CabecalhoFixo
import com.example.levit.ui.LinhaDeLista
import com.example.levit.ui.inflarFilho
import com.example.levit.ui.ligacaoDe
import com.example.levit.databinding.SheetCandidatoBinding
import com.example.levit.databinding.SheetNovaEtapaBinding
import com.example.levit.databinding.SheetNovoCandidatoBinding
import com.example.levit.ui.Datas
import com.example.levit.ui.Folha
import com.example.levit.ui.Funil
import com.example.levit.ui.Pessoas
import com.example.levit.ui.abrirArquivo
import com.example.levit.ui.avisar
import com.example.levit.ui.configurarSelect
import com.example.levit.ui.cor
import com.example.levit.ui.coresDaEtapa
import com.example.levit.ui.escolher
import com.example.levit.ui.mostrarAvatar
import com.example.levit.ui.mostrarCarregando
import com.example.levit.ui.mostrarEstado
import com.example.levit.ui.selo
import com.google.android.material.tabs.TabLayout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * Recrutamento: o quadro de etapas de cada vaga (RecrutamentoKanban.jsx).
 * No celular não se arrasta entre colunas: cada etapa é uma aba, e o cartão
 * tem o seletor "mover para", o mesmo que o web mostra no toque.
 */
class RecrutamentoActivity : HamburgerMenuBaseActivity() {

    private lateinit var binding: ActivityRecrutamentoBinding
    private val api by lazy { ApiClient.recrutamentoApi(applicationContext) }
    private val moduloRepository by lazy { ModuloRepository(applicationContext) }

    private var vagas: List<Modulo> = emptyList()
    private var vagaEscolhida: String? = null
    private var etapas: List<Etapa> = emptyList()
    private var etapaAtiva: String? = null
    private var carregando = true
    private var erro: Resultado.Erro? = null

    // Com um movimento ainda sendo gravado, a atualização periódica espera:
    // trazer o quadro do servidor nesse meio devolveria o cartão à etapa antiga.
    private var movimentosPendentes = 0
    private var montandoAbas = false

    // Perfil aberto: relido do quadro atual, então reflete a etapa nova na hora.
    private var perfilId: String? = null
    private var perfil: SheetCandidatoBinding? = null

    private var aoEscolherCurriculo: ((Uri?) -> Unit)? = null
    private val escolherCurriculo = registerForActivityResult(ActivityResultContracts.OpenDocument()) {
        aoEscolherCurriculo?.invoke(it)
    }

    // Sem escolha (ou com a vaga escolhida removida), usa a primeira vaga. Sem
    // vagas, mostra o quadro de todas em modo somente leitura (como o web).
    private val vagaSelecionada: String?
        get() = vagas.firstOrNull { it.id == vagaEscolhida }?.id ?: vagas.firstOrNull()?.id
    private val somenteLeitura get() = vagaSelecionada == null
    private val termo get() = Funil.normalizar(binding.etBusca.text?.toString()?.trim())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRecrutamentoBinding.inflate(layoutInflater)
        setContentViewWithHamburgerMenu(binding.root)
        if (!menuPronto()) return
        setupHamburgerMenuButton(R.id.ivMenu)
        setupSino(binding.flSino)
        topo = ItemRecResultadoBuscaBinding.inflate(layoutInflater, binding.lista, false)
        binding.lista.adapter = ConcatAdapter(CabecalhoFixo(topo.root), adaptador)

        binding.etBusca.doAfterTextChanged { renderizar() }
        binding.btnNovaEtapa.setOnClickListener { abrirNovaEtapa() }
        binding.btnNovoCandidato.setOnClickListener { abrirNovoCandidato() }
        binding.btnCopiarLink.setOnClickListener { copiarLink() }
        binding.abasEtapas.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                if (montandoAbas) return
                etapaAtiva = tab.tag as? String
                renderizarCartoes()
                binding.lista.scrollToPosition(0)
            }
            override fun onTabUnselected(tab: TabLayout.Tab) = Unit
            override fun onTabReselected(tab: TabLayout.Tab) = Unit
        })

        // Carrega ao abrir e a cada volta à tela; enquanto visível, atualiza a
        // cada 15 s (KANBAN_POLL_MS do web). Em segundo plano não consome API.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                carregarTudo()
                while (isActive) {
                    delay(INTERVALO_ATUALIZACAO_MS)
                    if (movimentosPendentes == 0) carregarQuadro(silencioso = true)
                }
            }
        }
    }

    private suspend fun carregarTudo() {
        when (val r = moduloRepository.listar()) {
            is ResultadoModulo.Sucesso -> vagas = r.dados.filter { it.tipo == "recrutamento" }
            is ResultadoModulo.Erro -> {
                if (r.sessaoExpirada) return redirecionarParaLogin()
                erro = Resultado.Erro(r.mensagem)
                carregando = false
                return renderizar()
            }
        }
        carregarQuadro(silencioso = false)
    }

    private suspend fun carregarQuadro(silencioso: Boolean) {
        val vaga = vagaSelecionada
        val r = chamarApi(getString(R.string.rec_erro_padrao)) {
            if (vaga != null) api.kanbanDaVaga(vaga) else api.kanbanGlobal()
        }
        // Trocou de vaga enquanto carregava, ou moveu alguém: esta resposta já é velha.
        if (vaga != vagaSelecionada || (silencioso && movimentosPendentes > 0)) return
        when (r) {
            is Resultado.Sucesso -> {
                etapas = Funil.etapas(r.dados)
                erro = null
            }
            is Resultado.Erro -> {
                if (r.sessaoExpirada) return redirecionarParaLogin()
                // Uma falha na atualização periódica não apaga o quadro que já está na tela.
                if (!silencioso || etapas.isEmpty()) erro = r
            }
        }
        carregando = false
        renderizar()
        perfilId?.let { atualizarPerfil(it) }
    }

    private fun recarregar() = lifecycleScope.launch { carregarQuadro(silencioso = false) }

    // --- Tela -------------------------------------------------------------------

    private lateinit var topo: ItemRecResultadoBuscaBinding

    /** Linhas: candidato da etapa ativa, etapa vazia ou um estado (carregando, erro, sem vagas/etapas). */
    private sealed interface Linha : LinhaDeLista {
        data class DeCandidato(val c: Candidato, val etapa: Etapa, val mover: Boolean) : Linha {
            override val chave get() = c.id
            override val tipo get() = TIPO_CANDIDATO
        }
        data object EtapaVazia : Linha {
            override val chave get() = "vazia"
            override val tipo get() = TIPO_VAZIA
        }
        data class Estado(val chave0: String, val montar: (LinearLayout) -> Unit) : Linha {
            override val chave get() = chave0
            override val tipo get() = TIPO_ESTADO
        }
    }

    private val adaptador = AdaptadorDeLinhas<Linha>(
        criar = { pai, tipo ->
            when (tipo) {
                TIPO_CANDIDATO -> ItemCandidatoBinding.inflate(layoutInflater, pai, false)
                TIPO_VAZIA -> ligacaoDe(pai.inflarFilho<View>(R.layout.item_rec_etapa_vazia))
                else -> ItemEstadoListaBinding.inflate(layoutInflater, pai, false)
            }
        },
        ligar = { b, linha ->
            when (linha) {
                is Linha.DeCandidato -> ligarCartao(b as ItemCandidatoBinding, linha.c, linha.etapa)
                Linha.EtapaVazia -> Unit
                is Linha.Estado -> linha.montar((b as ItemEstadoListaBinding).conteudo)
            }
        }
    )

    private fun estado(chave: String, montar: (LinearLayout) -> Unit) = adaptador.submitList(listOf(Linha.Estado(chave, montar)))

    private fun renderizar() {
        val total = etapas.sumOf { it.candidatos.size }
        binding.tvSubtitulo.text = if (carregando) getString(R.string.rec_carregando)
        else resources.getQuantityString(R.plurals.rec_total_no_quadro, total, total)
        binding.btnCopiarLink.isVisible = !carregando && !somenteLeitura

        val opcoesVaga = if (vagas.isEmpty()) listOf("all" to getString(R.string.rec_nenhuma_vaga_opcao))
        else vagas.map { it.id to it.nome }
        binding.acVaga.configurarSelect(opcoesVaga, vagaSelecionada ?: "all") { trocarVaga(it) }
        binding.campoVaga.isEnabled = vagas.isNotEmpty()
        binding.campoBusca.isEnabled = total > 0 || binding.etBusca.text?.isNotEmpty() == true
        binding.btnNovaEtapa.isEnabled = vagas.isNotEmpty()
        binding.btnNovoCandidato.isEnabled = vagas.isNotEmpty()

        val semQuadro = etapas.isEmpty()
        binding.abasEtapas.isVisible = !semQuadro
        topo.tvResultadoBusca.isVisible = false
        when {
            carregando && semQuadro -> estado("carregando") { it.mostrarCarregando() }
            erro != null && semQuadro -> {
                val e = erro!!
                estado("erro") { it.mostrarEstado(
                    titulo = getString(if (e.semAcesso) R.string.rec_sem_acesso_titulo else R.string.rec_erro_titulo),
                    descricao = if (e.semAcesso) getString(R.string.rec_sem_acesso_desc) else e.mensagem,
                    icone = if (e.semAcesso) R.drawable.ic_lock else R.drawable.ic_warning,
                    acao = if (e.semAcesso) null else getString(R.string.rec_tentar),
                    erro = true
                ) { carregando = true; renderizar(); recarregar() } }
            }
            vagas.isEmpty() && semQuadro -> estado("sem-vagas") { it.mostrarEstado(
                titulo = getString(R.string.rec_sem_vagas_titulo),
                descricao = getString(R.string.rec_sem_vagas_desc),
                icone = R.drawable.ic_briefcase,
                acao = getString(R.string.rec_criar_vaga)
            ) { TabelaFormActivity.abrir(this, modelo = "vaga") } }
            semQuadro -> estado("sem-etapas") { it.mostrarEstado(
                titulo = getString(R.string.rec_sem_etapas_titulo),
                descricao = getString(R.string.rec_sem_etapas_desc),
                icone = R.drawable.ic_person_search,
                acao = getString(R.string.rec_criar_primeira_etapa)
            ) { abrirNovaEtapa() } }
            else -> {
                montarAbas()
                renderizarCartoes()
            }
        }
    }

    private fun visiveis(etapa: Etapa): List<Candidato> {
        val t = termo
        return if (t.isEmpty()) etapa.candidatos else etapa.candidatos.filter { Funil.normalizar(it.nome).contains(t) }
    }

    /** Uma aba por etapa: ponto na cor da posição no quadro, nome e quantos há. */
    private fun montarAbas() {
        if (etapas.none { it.id == etapaAtiva }) etapaAtiva = etapas.first().id
        montandoAbas = true
        val abas = binding.abasEtapas
        abas.removeAllTabs()
        etapas.forEachIndexed { indice, etapa ->
            val (corEtapa, _) = coresDaEtapa(indice, etapas.size)
            val texto = SpannableStringBuilder("●  ${etapa.nome}  ${visiveis(etapa).size}").apply {
                setSpan(ForegroundColorSpan(cor(corEtapa)), 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            abas.addTab(abas.newTab().setText(texto).setTag(etapa.id), etapa.id == etapaAtiva)
        }
        montandoAbas = false
    }

    private fun renderizarCartoes() {
        val indice = etapas.indexOfFirst { it.id == etapaAtiva }.takeIf { it >= 0 } ?: return adaptador.submitList(emptyList())
        val etapa = etapas[indice]

        val busca = binding.etBusca.text?.toString()?.trim().orEmpty()
        if (busca.isNotEmpty()) {
            val encontrados = etapas.sumOf { visiveis(it).size }
            topo.tvResultadoBusca.isVisible = true
            topo.tvResultadoBusca.text = if (encontrados == 0) getString(R.string.rec_ninguem_com, busca)
            else resources.getQuantityString(R.plurals.rec_encontrados, encontrados, encontrados)
        }

        val candidatos = visiveis(etapa)
        val mover = !somenteLeitura && etapas.size > 1
        adaptador.submitList(
            if (candidatos.isEmpty() && busca.isEmpty()) listOf(Linha.EtapaVazia)
            else candidatos.map { Linha.DeCandidato(it, etapa, mover) }
        )
    }

    private fun ligarCartao(v: ItemCandidatoBinding, c: Candidato, etapa: Etapa) {
        val nome = c.nome?.takeIf { it.isNotBlank() } ?: getString(R.string.rec_sem_nome)
        v.tvAvatar.mostrarAvatar(c.nome)
        v.tvNome.text = nome
        v.ivCurriculo.isVisible = c.temCurriculo == true
        v.tvDetalhe.text = c.vaga ?: c.email

        val referencia = c.atualizadoEm ?: c.criadoEm
        val desde = Datas.tempoRelativo(referencia)
        val parado = Datas.estaParado(referencia)
        v.linhaTempo.isVisible = desde != null || parado
        v.tvDesde.text = desde?.let { getString(R.string.rec_nesta_etapa, it) }.orEmpty()
        v.flParado.removeAllViews()
        if (parado) v.flParado.addView(selo(getString(R.string.rec_parado), R.color.warning, R.color.warning_bg, ponto = true))

        v.areaPerfil.setOnClickListener { abrirPerfil(c.id) }
        v.btnMover.isVisible = !somenteLeitura && etapas.size > 1
        v.btnMover.text = etapa.nome
        v.btnMover.contentDescription = getString(R.string.rec_etapa_de, nome)
        v.btnMover.setOnClickListener {
            escolher(getString(R.string.rec_mover_para), etapas.map { it.id to it.nome }, etapa.id) { para ->
                mover(c, etapa.id, para)
            }
        }
    }

    private fun trocarVaga(id: String) {
        if (id == vagaSelecionada || id == "all") return
        vagaEscolhida = id
        binding.etBusca.setText("")
        etapas = emptyList()
        etapaAtiva = null
        erro = null
        carregando = true
        renderizar()
        recarregar()
    }

    private fun copiarLink() {
        val vaga = vagaSelecionada ?: return
        val link = "${BuildConfig.WEB_URL.trimEnd('/')}/vagas/$vaga"
        getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText(getString(R.string.rec_copiar_link), link))
        avisar(getString(R.string.rec_link_copiado))
    }

    // --- Mover ------------------------------------------------------------------

    /**
     * Move na tela na hora (otimista) e grava. Se a API falhar, desfaz só esse
     * cartão e avisa; o resto do quadro fica como está.
     */
    private fun mover(candidato: Candidato, de: String, para: String) {
        if (de == para) return
        val vaga = candidato.moduloId ?: vagaSelecionada ?: return
        movimentosPendentes++
        etapas = Funil.mover(etapas, candidato, de, para, Datas.agora())
        renderizar()
        perfilId?.let { atualizarPerfil(it) }

        lifecycleScope.launch {
            val r = chamarApi(getString(R.string.rec_erro_mover)) {
                api.moverFase(vaga, candidato.id, MoverFaseRequest(para))
            }
            movimentosPendentes--
            if (r is Resultado.Erro) {
                if (r.sessaoExpirada) return@launch redirecionarParaLogin()
                etapas = Funil.mover(etapas, candidato, para, de, candidato.atualizadoEm.orEmpty())
                avisar(r.mensagem, longo = true)
                renderizar()
                perfilId?.let { atualizarPerfil(it) }
                carregarQuadro(silencioso = false)
            }
        }
    }

    // --- Perfil -----------------------------------------------------------------

    private fun localizar(id: String): Pair<Candidato, Int>? {
        etapas.forEachIndexed { i, etapa -> etapa.candidatos.firstOrNull { it.id == id }?.let { return it to i } }
        return null
    }

    private fun abrirPerfil(id: String) {
        val b = SheetCandidatoBinding.inflate(layoutInflater)
        perfilId = id
        perfil = b
        val folha = Folha(this, b.root).aoFechar { perfilId = null; perfil = null }
        b.btnFechar.setOnClickListener { folha.dispensar() }
        atualizarPerfil(id)
        folha.abrir()
    }

    private fun atualizarPerfil(id: String) {
        val b = perfil ?: return
        val (c, indice) = localizar(id) ?: return
        val etapa = etapas[indice]
        val (corEtapa, fundoEtapa) = coresDaEtapa(indice, etapas.size)

        b.tvAvatar.mostrarAvatar(c.nome)
        b.tvNome.text = c.nome?.takeIf { it.isNotBlank() } ?: getString(R.string.rec_sem_nome)
        b.grupoSelos.removeAllViews()
        b.grupoSelos.addView(selo(etapa.nome, corEtapa, fundoEtapa, ponto = true))
        if (Datas.estaParado(c.atualizadoEm ?: c.criadoEm)) {
            b.grupoSelos.addView(selo(getString(R.string.rec_parado_14), R.color.warning, R.color.warning_bg, ponto = true))
        }
        val vagaNome = c.vaga ?: vagas.firstOrNull { it.id == c.moduloId }?.nome
        val quando = Datas.tempoRelativo(c.criadoEm) ?: "—"
        b.tvCandidatouSe.text = if (vagaNome != null) getString(R.string.rec_candidatou_se_vaga, quando, vagaNome)
        else getString(R.string.rec_candidatou_se, quando)

        // Mover pela tela: alternativa ao arrastar do web.
        val podeMover = !somenteLeitura && etapas.size > 1
        b.campoEtapa.isVisible = podeMover
        b.divEtapa.isVisible = podeMover
        b.acEtapa.configurarSelect(etapas.map { it.id to it.nome }, etapa.id) { para -> mover(c, etapa.id, para) }

        mostrarDado(b.tvEmail, c.email)
        mostrarDado(b.tvTelefone, c.telefone)
        b.linhaEmail.setOnClickListener { c.email?.let { abrirEmail(it) } }
        b.linhaEmail.isClickable = !c.email.isNullOrBlank()
        b.linhaTelefone.setOnClickListener {
            c.telefone?.let { startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + it.replace(Regex("[^\\d+]"), "")))) }
        }
        b.linhaTelefone.isClickable = !c.telefone.isNullOrBlank()

        val temCurriculo = c.temCurriculo == true
        b.tvCurriculo.text = if (temCurriculo) c.curriculoNome ?: getString(R.string.rec_curriculo) else getString(R.string.rec_nao_enviado)
        b.tvCurriculo.setTextColor(cor(if (temCurriculo) R.color.ink else R.color.light_text))
        b.btnBaixarCurriculo.isVisible = temCurriculo
        b.btnBaixarCurriculo.setOnClickListener { baixarCurriculo(c, b) }

        b.blocoMensagem.isVisible = !c.mensagem.isNullOrBlank()
        b.tvMensagem.text = c.mensagem
        b.btnEnviarEmail.isVisible = !c.email.isNullOrBlank()
        b.btnEnviarEmail.setOnClickListener { c.email?.let { abrirEmail(it) } }
    }

    private fun mostrarDado(campo: android.widget.TextView, valor: String?) {
        campo.text = valor?.takeIf { it.isNotBlank() } ?: getString(R.string.rec_nao_informado)
        campo.setTextColor(cor(if (valor.isNullOrBlank()) R.color.light_text else R.color.ink))
    }

    private fun abrirEmail(email: String) {
        runCatching { startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:$email"))) }
            .onFailure { avisar(email) }
    }

    private fun baixarCurriculo(c: Candidato, b: SheetCandidatoBinding) {
        val vaga = c.moduloId ?: vagaSelecionada ?: return
        b.btnBaixarCurriculo.isEnabled = false
        lifecycleScope.launch {
            val r = baixarApi(getString(R.string.rec_erro_curriculo)) { api.curriculo(vaga, c.id) }
            b.btnBaixarCurriculo.isEnabled = true
            when (r) {
                is Resultado.Sucesso -> {
                    val nome = c.curriculoNome ?: "Currículo - ${c.nome}"
                    abrirArquivo(r.dados, nome, mimeDoArquivo(nome))
                }
                is Resultado.Erro -> if (r.sessaoExpirada) redirecionarParaLogin() else avisar(r.mensagem, longo = true)
            }
        }
    }

    // --- Nova etapa -------------------------------------------------------------

    private fun abrirNovaEtapa() {
        if (vagas.isEmpty()) return
        val b = SheetNovaEtapaBinding.inflate(layoutInflater)
        var vagaId: String? = vagaSelecionada
        val folha = Folha(this, b.root) { !b.etNome.text.isNullOrBlank() }

        fun atualizarBotao() { b.btnSalvar.isEnabled = !b.etNome.text.isNullOrBlank() && vagaId != null }
        b.acVaga.configurarSelect(vagas.map { it.id to it.nome }, vagaId) { vagaId = it; atualizarBotao() }
        b.etNome.doAfterTextChanged { atualizarBotao() }
        atualizarBotao()
        b.btnCancelar.setOnClickListener { folha.fechar() }
        b.btnSalvar.setOnClickListener {
            val vaga = vagaId ?: return@setOnClickListener
            val nome = b.etNome.text.toString().trim()
            b.btnSalvar.isEnabled = false
            b.tvErro.isVisible = false
            lifecycleScope.launch {
                when (val r = executarApi(getString(R.string.rec_erro_etapa)) { api.criarEtapa(vaga, NovaEtapaRequest(nome)) }) {
                    is Resultado.Sucesso -> {
                        folha.dispensar()
                        avisar(getString(R.string.rec_etapa_criada))
                        if (vaga != vagaSelecionada) trocarVaga(vaga) else carregarQuadro(silencioso = false)
                    }
                    is Resultado.Erro -> {
                        if (r.sessaoExpirada) return@launch redirecionarParaLogin()
                        b.tvErro.text = r.mensagem
                        b.tvErro.isVisible = true
                        atualizarBotao()
                    }
                }
            }
        }
        folha.abrir()
        b.etNome.requestFocus()
    }

    // --- Novo candidato ---------------------------------------------------------

    private data class Arquivo(val uri: Uri, val nome: String, val tamanho: Long)

    private fun abrirNovoCandidato() {
        if (vagas.isEmpty()) return
        val b = SheetNovoCandidatoBinding.inflate(layoutInflater)
        var vagaId: String? = vagaSelecionada
        var arquivo: Arquivo? = null
        val campos = listOf(b.etNome, b.etEmail, b.etTelefone, b.etMensagem)
        val folha = Folha(this, b.root) { arquivo != null || campos.any { !it.text.isNullOrBlank() } }

        b.acVaga.configurarSelect(vagas.map { it.id to it.nome }, vagaId) { vagaId = it; b.campoVaga.error = null }
        b.etNome.doAfterTextChanged { b.campoNome.error = null }
        b.etEmail.doAfterTextChanged { b.campoEmail.error = null }
        b.etTelefone.doAfterTextChanged { texto ->
            val atual = texto?.toString().orEmpty()
            val mascarado = Pessoas.mascararTelefone(atual)
            if (mascarado != atual) {
                b.etTelefone.setText(mascarado)
                b.etTelefone.setSelection(mascarado.length)
            }
            b.campoTelefone.error = null
        }

        fun mostrarArquivo(erro: String? = null) {
            b.tvArquivo.text = arquivo?.nome.orEmpty()
            b.btnTirarArquivo.isVisible = arquivo != null
            b.tvArquivoDica.text = erro ?: getString(R.string.rec_curriculo_dica)
            b.tvArquivoDica.setTextColor(cor(if (erro != null) R.color.danger else R.color.light_text))
        }

        aoEscolherCurriculo = { uri ->
            if (uri != null) {
                arquivo = lerArquivo(uri)
                mostrarArquivo(arquivo?.let { erroDoCurriculo(it) })
            }
        }
        b.btnArquivo.setOnClickListener { escolherCurriculo.launch(TIPOS_CURRICULO) }
        b.btnTirarArquivo.setOnClickListener { arquivo = null; mostrarArquivo() }
        b.btnCancelar.setOnClickListener { folha.fechar() }

        b.btnSalvar.setOnClickListener {
            val nome = b.etNome.text.toString()
            val email = b.etEmail.text.toString()
            val telefone = b.etTelefone.text.toString()
            val erros = Pessoas.errosDoCandidato(nome, email, telefone)
            b.campoNome.error = erros["nome"]
            b.campoEmail.error = erros["email"]
            b.campoTelefone.error = erros["telefone"]
            b.campoVaga.error = if (vagaId == null) getString(R.string.rec_selecione_vaga) else null
            val erroArquivo = arquivo?.let { erroDoCurriculo(it) }
            mostrarArquivo(erroArquivo)
            val vaga = vagaId
            if (erros.isNotEmpty() || vaga == null || erroArquivo != null) return@setOnClickListener

            b.btnSalvar.isEnabled = false
            b.btnSalvar.setText(R.string.rec_salvando)
            b.tvErro.isVisible = false
            lifecycleScope.launch {
                val parteArquivo = arquivo?.let { a ->
                    val bytes = withContext(Dispatchers.IO) {
                        contentResolver.openInputStream(a.uri)?.use { it.readBytes() }
                    }
                    bytes?.let {
                        MultipartBody.Part.createFormData("curriculo", a.nome, it.toRequestBody(mimeDoArquivo(a.nome).toMediaType()))
                    }
                }
                val texto = "text/plain".toMediaType()
                val r = chamarApi(getString(R.string.rec_erro_candidato)) {
                    api.adicionarCandidato(
                        vaga,
                        nome.trim().toRequestBody(texto),
                        email.trim().toRequestBody(texto),
                        telefone.trim().toRequestBody(texto),
                        b.etMensagem.text.toString().trim().toRequestBody(texto),
                        parteArquivo
                    )
                }
                when (r) {
                    is Resultado.Sucesso -> {
                        folha.dispensar()
                        avisar(getString(R.string.rec_candidato_adicionado))
                        if (vaga != vagaSelecionada) trocarVaga(vaga) else carregarQuadro(silencioso = false)
                    }
                    is Resultado.Erro -> {
                        if (r.sessaoExpirada) return@launch redirecionarParaLogin()
                        b.tvErro.text = r.mensagem
                        b.tvErro.isVisible = true
                        b.btnSalvar.isEnabled = true
                        b.btnSalvar.setText(R.string.rec_adicionar_candidato)
                    }
                }
            }
        }
        folha.aoFechar { aoEscolherCurriculo = null }.abrir()
    }

    private fun lerArquivo(uri: Uri): Arquivo? =
        contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { c ->
            if (!c.moveToFirst()) return null
            Arquivo(uri, c.getString(0) ?: "curriculo", if (c.isNull(1)) 0 else c.getLong(1))
        }

    /** Mesmas regras de utils/candidato.js e do backend: PDF ou Word, até 5 MB. */
    private fun erroDoCurriculo(a: Arquivo): String? = when {
        !Regex("\\.(pdf|docx?)$", RegexOption.IGNORE_CASE).containsMatchIn(a.nome) -> getString(R.string.rec_curriculo_formato)
        a.tamanho > CURRICULO_MAXIMO_BYTES -> getString(R.string.rec_curriculo_grande)
        else -> null
    }

    private fun mimeDoArquivo(nome: String) = when (nome.substringAfterLast('.', "").lowercase()) {
        "pdf" -> "application/pdf"
        "doc" -> "application/msword"
        "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
        else -> "application/octet-stream"
    }

    companion object {
        private const val TIPO_CANDIDATO = 1
        private const val TIPO_VAZIA = 2
        private const val TIPO_ESTADO = 3
        private const val INTERVALO_ATUALIZACAO_MS = 15_000L
        private const val CURRICULO_MAXIMO_BYTES = 5L * 1024 * 1024
        private val TIPOS_CURRICULO = arrayOf(
            "application/pdf",
            "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
        )
    }
}
