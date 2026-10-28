package com.example.levit

import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.example.levit.data.remote.ApiClient
import com.example.levit.data.remote.dto.AutomacaoResumo
import com.example.levit.data.remote.dto.Convite
import com.example.levit.data.remote.dto.Etapa
import com.example.levit.data.remote.dto.ItemPrazo
import com.example.levit.data.remote.dto.Membro
import com.example.levit.data.remote.dto.Modulo
import com.example.levit.data.remote.dto.PrazosAbertos
import com.example.levit.data.repository.ModuloRepository
import com.example.levit.data.repository.Resultado
import com.example.levit.data.repository.ResultadoModulo
import com.example.levit.data.repository.chamarApi
import com.example.levit.databinding.ActivityDashboardBinding
import com.example.levit.databinding.ItemAtencaoBinding
import com.example.levit.databinding.ItemComeceTabelaBinding
import com.example.levit.databinding.ItemIndicadorInicioBinding
import com.example.levit.databinding.ItemPrazoInicioBinding
import com.example.levit.databinding.ItemTabelaInicioBinding
import com.example.levit.ui.Datas
import com.example.levit.ui.Funil
import com.example.levit.ui.Prazos
import com.example.levit.ui.Tabelas
import com.example.levit.ui.adicionarTexto
import com.example.levit.ui.cor
import com.example.levit.ui.coresDaEtapa
import com.example.levit.ui.divisoria
import com.example.levit.ui.dp
import com.example.levit.ui.inflar
import com.example.levit.ui.inflarFilho
import com.example.levit.ui.mostrarAvatar
import com.example.levit.ui.mostrarCarregando
import com.example.levit.ui.mostrarEstado
import com.example.levit.ui.pessoaResponsavel
import com.example.levit.ui.selo
import com.example.levit.ui.texto
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Início (Dashboard.jsx): primeiro o que ninguém pode esquecer (prazos e o que
 * pede atenção), depois as tabelas; recrutamento e equipe como resumo.
 */
class DashboardActivity : HamburgerMenuBaseActivity() {

    private lateinit var binding: ActivityDashboardBinding
    private val moduloRepository by lazy { ModuloRepository(applicationContext) }
    private val inicioApi by lazy { ApiClient.inicioApi(applicationContext) }
    private val equipeApi by lazy { ApiClient.equipeApi(applicationContext) }
    private val recrutamentoApi by lazy { ApiClient.recrutamentoApi(applicationContext) }

    private var modulos: List<Modulo>? = null
    private var erroModulos: String? = null
    private var equipe: List<Membro>? = null
    private var equipeSemAcesso = false
    private var convites: List<Convite> = emptyList()
    private var automacoes: List<AutomacaoResumo> = emptyList()
    private var prazos: PrazosAbertos? = null
    private var erroPrazos: String? = null
    private var etapasDaVaga: List<Etapa>? = null
    private var vagaSemAcesso = false
    private var escopoPrazos = "meus"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDashboardBinding.inflate(layoutInflater)
        setContentViewWithHamburgerMenu(binding.root)
        if (!menuPronto()) return
        setupHamburgerMenuButton(R.id.ivMenu)
        setupSino(binding.flSino)

        val primeiroNome = authRepository.usuarioLogado()?.nome?.trim()?.split(Regex("\\s+"))?.firstOrNull().orEmpty()
        val hora = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        binding.tvSaudacao.text = getString(when {
            hora < 12 -> R.string.ini_bom_dia
            hora < 18 -> R.string.ini_boa_tarde
            else -> R.string.ini_boa_noite
        }, primeiroNome)
        val data = SimpleDateFormat("EEEE, d 'de' MMMM", Locale.forLanguageTag("pt-BR")).format(Date())
            .replaceFirstChar { it.uppercase() }
        binding.tvData.text = authRepository.empresaLogada()?.nome?.let { "$data · $it" } ?: data
        configurarTela()
    }

    override fun onResume() {
        super.onResume()
        if (menuPronto()) carregar()
    }

    private fun carregar() {
        if (modulos == null) renderizar()
        lifecycleScope.launch {
            coroutineScope {
                val m = async { moduloRepository.listar() }
                val e = async { chamarApi("") { equipeApi.membros() } }
                val c = async { chamarApi("") { equipeApi.convites() } }
                val a = async { chamarApi("") { inicioApi.automacoes() } }
                val p = async { chamarApi(getString(R.string.ini_erro_prazos)) { inicioApi.prazos() } }

                when (val r = m.await()) {
                    is ResultadoModulo.Sucesso -> { modulos = r.dados; erroModulos = null }
                    is ResultadoModulo.Erro -> { if (r.sessaoExpirada) return@coroutineScope redirecionarParaLogin(); erroModulos = r.mensagem }
                }
                when (val r = e.await()) {
                    is Resultado.Sucesso -> { equipe = r.dados; equipeSemAcesso = false }
                    is Resultado.Erro -> equipeSemAcesso = r.semAcesso
                }
                // Convites e automações dependem de permissão: sem acesso, só não aparecem.
                convites = (c.await() as? Resultado.Sucesso)?.dados.orEmpty()
                automacoes = (a.await() as? Resultado.Sucesso)?.dados.orEmpty()
                when (val r = p.await()) {
                    is Resultado.Sucesso -> { prazos = r.dados; erroPrazos = null }
                    is Resultado.Erro -> erroPrazos = r.mensagem
                }
            }
            if (isFinishing) return@launch
            // O resumo de recrutamento mostra a vaga mais recente.
            vagaDestaque()?.let { vaga ->
                when (val r = chamarApi("") { recrutamentoApi.kanbanDaVaga(vaga.id) }) {
                    is Resultado.Sucesso -> { etapasDaVaga = Funil.etapas(r.dados); vagaSemAcesso = false }
                    is Resultado.Erro -> vagaSemAcesso = r.semAcesso
                }
            }
            renderizar()
        }
    }

    private val tabelas get() = modulos.orEmpty().filter { it.tipo != "recrutamento" }
    private val vagas get() = modulos.orEmpty().filter { it.tipo == "recrutamento" }
    private fun vagaDestaque() = vagas.lastOrNull()

    // --- Tela ---------------------------------------------------------------------

    private fun configurarTela() {
        binding.grupoEscopo.check(if (escopoPrazos == "meus") binding.btnMeus.id else binding.btnDaEquipe.id)
        binding.grupoEscopo.addOnButtonCheckedListener { _, id, marcado ->
            if (marcado) { escopoPrazos = if (id == binding.btnMeus.id) "meus" else "todos"; mostrarPrazos() }
        }
        binding.btnNovaTabela.setOnClickListener { TabelaFormActivity.abrir(this) }
        binding.tvAbrirRecrutamento.setOnClickListener { startActivity(Intent(this, RecrutamentoActivity::class.java)) }
        binding.tvVerEquipe.setOnClickListener { startActivity(Intent(this, EquipeActivity::class.java)) }
        binding.btnConvidar.setOnClickListener { startActivity(Intent(this, EquipeActivity::class.java)) }
        binding.tvVerTodas.setOnClickListener { startActivity(Intent(this, ModulosActivity::class.java)) }
    }

    private fun renderizar() {
        val carregando = modulos == null && erroModulos == null
        binding.llConteudo.isVisible = !carregando
        if (carregando) return binding.llCarregando.mostrarCarregando(4)
        binding.llCarregando.removeAllViews()
        indicadores()
        mostrarPrazos()
        mostrarAtencao()
        mostrarTabelas()
        mostrarRecrutamento()
        mostrarEquipe()
    }

    /** StatCard: tabelas, automações ligadas e pessoas na equipe. */
    private fun indicadores() {
        val totalItens = tabelas.sumOf { it.totalRegistros }
        val ativas = automacoes.count { it.ativo }
        val execucoes = automacoes.sumOf { it.estatisticas?.execucoes7d ?: 0 }
        val falhas = automacoes.sumOf { it.estatisticas?.falhas7d ?: 0 }
        val pessoas = equipe?.size ?: 0
        preencherIndicador(binding.indTabelas, "table_chart", R.color.primary, R.color.primary_100, tabelas.size,
            resources.getQuantityString(R.plurals.ini_tabelas, tabelas.size),
            resources.getQuantityString(R.plurals.ini_itens_guardados, totalItens, totalItens), null)
        preencherIndicador(binding.indAutomacoes, "bolt", R.color.info, R.color.info_bg, ativas,
            resources.getQuantityString(R.plurals.ini_automacoes_ligadas, ativas),
            resources.getQuantityString(R.plurals.ini_execucoes_semana, execucoes, execucoes),
            if (falhas > 0) resources.getQuantityString(R.plurals.ini_falhas, falhas, falhas) else null)
        preencherIndicador(binding.indEquipe, "groups", R.color.success, R.color.success_bg, pessoas,
            resources.getQuantityString(R.plurals.ini_pessoas_na_equipe, pessoas), null, null)
    }

    private fun preencherIndicador(b: ItemIndicadorInicioBinding, icone: String, corIcone: Int, fundo: Int, valor: Int, rotulo: String, dica: String?, selo: String?) {
        b.tvIcone.text = icone
        b.tvIcone.setTextColor(cor(corIcone))
        b.tvIcone.backgroundTintList = ColorStateList.valueOf(cor(fundo))
        b.tvValor.text = "$valor"
        b.flSelo.removeAllViews()
        selo?.let { b.flSelo.addView(selo(it, R.color.danger, R.color.danger_bg)) }
        b.tvRotulo.text = rotulo
        b.tvDica.text = dica
        b.tvDica.isVisible = dica != null
    }

    /** PainelPrazos.jsx: atrasados, hoje e próximos 7 dias; "Meus" ou "Da equipe". */
    private fun mostrarPrazos() {
        val destino = binding.llPrazos
        destino.removeAllViews()
        val dados = prazos
        if (dados == null) {
            if (erroPrazos != null) destino.adicionarTexto(erroPrazos!!, R.color.danger, 14f, topo = 0)
            else destino.mostrarCarregando(2)
            return
        }
        fun filtrar(lista: List<ItemPrazo>?) = lista.orEmpty().filter { escopoPrazos != "meus" || it.meu == true }
        val grupos = listOf(
            Triple(R.string.ini_atrasados, filtrar(dados.atrasados), R.color.danger),
            Triple(R.string.ini_vencem_hoje, filtrar(dados.hoje), R.color.warning),
            Triple(R.string.ini_proximos, filtrar(dados.proximos), R.color.light_text),
        )
        if (grupos.sumOf { it.second.size } == 0) {
            destino.mostrarEstado(
                getString(if (escopoPrazos == "meus") R.string.ini_nada_com_voce else R.string.ini_nada_na_equipe),
                getString(R.string.ini_nada_desc), R.drawable.ic_calendar
            )
            destino.getChildAt(0)?.background = null
            return
        }
        grupos.forEach { (titulo, lista, corTitulo) ->
            if (lista.isEmpty()) return@forEach
            destino.addView(destino.inflarFilho<TextView>(R.layout.item_grupo_prazo).apply {
                text = getString(R.string.ini_grupo, getString(titulo), lista.size)
                setTextColor(cor(corTitulo))
            })
            lista.take(POR_GRUPO).forEach { destino.addView(linhaDePrazo(it, destino)) }
            if (lista.size > POR_GRUPO) destino.adicionarTexto(getString(R.string.ini_e_mais, lista.size - POR_GRUPO), topo = 2)
        }
    }

    private fun linhaDePrazo(item: ItemPrazo, pai: ViewGroup): View {
        // A urgência já está no título do grupo: aqui só a data (sem repetir "Vence hoje").
        val situacao = Prazos.situacao(item.prazo)
        val prazo = if (situacao?.tipo == Prazos.Tipo.HOJE) null else situacao?.rotulo
        val b = ItemPrazoInicioBinding.inflate(layoutInflater, pai, false)
        b.tvIcone.text = item.moduloIcone ?: "table_chart"
        b.tvTitulo.text = item.titulo ?: getString(R.string.itens_sem_titulo)
        b.tvDetalhe.text = listOfNotNull(getString(R.string.ini_modulo_coluna, item.moduloNome.orEmpty(), item.colunaPrazo.orEmpty()), prazo).joinToString(" · ")
        item.responsavelNome?.let { b.flResponsavel.addView(pessoaResponsavel(it, comNome = false)) }
        b.root.setOnClickListener { item.moduloId?.let { abrirTabela(it) } }
        return b.root
    }

    private data class Atencao(val icone: String, val cor: Int, val fundo: Int, val titulo: String, val texto: String?, val abrir: () -> Unit)

    /** "Precisa da sua atenção": o que pede alguém agir agora, em ordem de urgência. */
    private fun mostrarAtencao() {
        val atrasados = prazos?.atrasados.orEmpty()
        val meusAtrasados = atrasados.count { it.meu == true }
        val pausadas = automacoes.filter { it.pausadaPorFalhasEm != null && !it.ativo }
        val parados = etapasDaVaga.orEmpty().flatMap { it.candidatos }.count { Datas.estaParado(it.atualizadoEm ?: it.criadoEm) }
        val vencidos = convites.count { it.expirado == true }
        val aguardando = convites.size - vencidos
        val itens = buildList {
            if (atrasados.isNotEmpty()) add(Atencao("event_busy", R.color.danger, R.color.danger_bg,
                resources.getQuantityString(R.plurals.ini_itens_atrasados, atrasados.size, atrasados.size),
                if (meusAtrasados > 0) resources.getQuantityString(R.plurals.ini_com_voce, meusAtrasados, meusAtrasados) else getString(R.string.ini_nenhum_com_voce)
            ) { atrasados.first().moduloId?.let { abrirTabela(it) } })
            pausadas.forEach { a -> add(Atencao("pause_circle", R.color.warning, R.color.warning_bg,
                getString(R.string.ini_automacao_pausada, a.nome), getString(R.string.ini_automacao_pausada_desc)
            ) { startActivity(Intent(this@DashboardActivity, AutomacoesActivity::class.java)) }) }
            if (parados > 0) add(Atencao("schedule", R.color.warning, R.color.warning_bg,
                resources.getQuantityString(R.plurals.ini_candidatos_parados, parados, parados),
                vagaDestaque()?.let { getString(R.string.ini_em_vaga, it.nome) }
            ) { startActivity(Intent(this@DashboardActivity, RecrutamentoActivity::class.java)) })
            if (vencidos > 0) add(Atencao("mail", R.color.danger, R.color.danger_bg,
                resources.getQuantityString(R.plurals.ini_convites_venceram, vencidos, vencidos), getString(R.string.ini_reenvie)
            ) { startActivity(Intent(this@DashboardActivity, EquipeActivity::class.java)) })
            if (aguardando > 0) add(Atencao("forward_to_inbox", R.color.info, R.color.info_bg,
                resources.getQuantityString(R.plurals.ini_convites_aguardando, aguardando, aguardando), getString(R.string.ini_quem_aceitar)
            ) { startActivity(Intent(this@DashboardActivity, EquipeActivity::class.java)) })
        }
        val destino = binding.llAtencao
        destino.removeAllViews()
        if (itens.isEmpty()) {
            destino.mostrarEstado(getString(R.string.ini_tudo_em_dia), getString(R.string.ini_tudo_em_dia_desc), R.drawable.ic_calendar)
            destino.getChildAt(0)?.background = null
            return
        }
        itens.forEachIndexed { i, a ->
            if (i > 0) destino.addView(divisoria())
            val b = ItemAtencaoBinding.inflate(layoutInflater, destino, false)
            b.tvIcone.text = a.icone
            b.tvIcone.setTextColor(cor(a.cor))
            b.tvIcone.backgroundTintList = ColorStateList.valueOf(cor(a.fundo))
            b.tvTitulo.text = a.titulo
            b.tvTexto.text = a.texto
            b.tvTexto.isVisible = a.texto != null
            b.root.setOnClickListener { a.abrir() }
            destino.addView(b.root)
        }
    }

    /** "Suas tabelas": até 6, ou os modelos para começar. */
    private fun mostrarTabelas() {
        val destino = binding.llTabelas
        destino.removeAllViews()
        binding.tvVerTodas.isVisible = false
        when {
            erroModulos != null -> destino.mostrarEstado(getString(R.string.tab_erro_carregar), erroModulos, R.drawable.ic_warning,
                getString(R.string.tab_tentar), erro = true) { carregar() }
            tabelas.isEmpty() -> {
                val b = ItemComeceTabelaBinding.inflate(layoutInflater, destino, false)
                Tabelas.MODELOS.filter { it.segmento != null || it.id == "tarefas" }.forEach { m ->
                    b.grupoModelos.addView(inflar<MaterialButton>(R.layout.item_botao_pequeno).apply {
                        text = m.titulo
                        setIconResource(R.drawable.ic_add)
                        setOnClickListener { TabelaFormActivity.abrir(this@DashboardActivity) }
                    })
                }
                destino.addView(b.root)
            }
            else -> {
                tabelas.take(6).forEach { t ->
                    val b = ItemTabelaInicioBinding.inflate(layoutInflater, destino, false)
                    b.tvIcone.text = t.icone?.takeIf { it.isNotBlank() } ?: "extension"
                    b.tvNome.text = t.nome
                    b.tvItens.text = if (t.tipo == "financeiro") getString(R.string.ini_entradas_saidas)
                        else resources.getQuantityString(R.plurals.tab_n_itens, t.totalRegistros, t.totalRegistros)
                    b.root.setOnClickListener { ModulosActivity.abrirTabela(this@DashboardActivity, t) }
                    destino.addView(b.root)
                }
                if (tabelas.size > 6) {
                    binding.tvVerTodas.isVisible = true
                    binding.tvVerTodas.text = getString(R.string.ini_ver_todas, tabelas.size)
                }
            }
        }
    }

    private fun mostrarRecrutamento() {
        val vaga = vagaDestaque()
        binding.tvAbrirRecrutamento.setText(if (vaga != null) R.string.ini_abrir_quadro else R.string.ini_abrir_vaga)
        binding.llCarregandoVaga.removeAllViews()
        binding.llBarraFunil.removeAllViews()
        binding.grupoLegenda.removeAllViews()
        binding.llBarraFunil.isVisible = false
        binding.grupoLegenda.isVisible = false
        binding.tvRecrutamento.isVisible = true
        when {
            vaga == null -> binding.tvRecrutamento.setText(R.string.ini_nenhuma_vaga)
            vagaSemAcesso -> binding.tvRecrutamento.setText(R.string.ini_sem_acesso_vaga)
            etapasDaVaga == null -> { binding.tvRecrutamento.isVisible = false; binding.llCarregandoVaga.mostrarCarregando(1) }
            else -> {
                val etapas = etapasDaVaga!!
                val total = etapas.sumOf { it.candidatos.size }
                binding.tvRecrutamento.setTextColor(cor(R.color.ink_soft))
                binding.tvRecrutamento.text = SpannableStringBuilder().apply {
                    append(vaga.nome)
                    setSpan(ForegroundColorSpan(cor(R.color.ink)), 0, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    append(" · ").append(resources.getQuantityString(R.plurals.ini_candidatos, total, total))
                    if (vagas.size > 1) append(" · ").append(resources.getQuantityString(R.plurals.ini_vagas_abertas, vagas.size, vagas.size))
                }
                if (total > 0) {
                    // A barra do funil: cada etapa ocupa o tamanho de quantos estão nela.
                    binding.llBarraFunil.isVisible = true
                    binding.grupoLegenda.isVisible = true
                    etapas.forEachIndexed { i, e ->
                        val qtd = e.candidatos.size
                        binding.llBarraFunil.addView(binding.llBarraFunil.inflarFilho<View>(R.layout.item_segmento_funil).apply {
                            backgroundTintList = ColorStateList.valueOf(cor(if (qtd > 0) coresDaEtapa(i, etapas.size).first else R.color.divider))
                            (layoutParams as LinearLayout.LayoutParams).weight = maxOf(qtd.toFloat(), 0.35f)
                        })
                        binding.grupoLegenda.addView(texto(SpannableStringBuilder("●  ${e.nome}: $qtd").apply {
                            setSpan(ForegroundColorSpan(cor(coresDaEtapa(i, etapas.size).first)), 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                        }, 12f))
                    }
                }
            }
        }
    }

    private fun mostrarEquipe() {
        val lista = equipe
        binding.tvEquipe.isVisible = false
        binding.linhaSoVoce.isVisible = false
        binding.linhaAvatares.isVisible = false
        when {
            equipeSemAcesso -> { binding.tvEquipe.isVisible = true; binding.tvEquipe.setText(R.string.ini_sem_acesso_equipe) }
            lista == null -> { binding.tvEquipe.isVisible = true; binding.tvEquipe.text = "" }
            lista.size <= 1 -> binding.linhaSoVoce.isVisible = true
            else -> {
                binding.linhaAvatares.isVisible = true
                binding.llAvatares.removeAllViews()
                lista.take(6).forEachIndexed { i, m ->
                    binding.llAvatares.addView(binding.llAvatares.inflarFilho<TextView>(R.layout.item_avatar_pilha).apply {
                        mostrarAvatar(m.nome)
                        if (i > 0) (layoutParams as LinearLayout.LayoutParams).marginStart = -dp(8)
                    })
                }
                binding.tvPessoas.text = resources.getQuantityString(R.plurals.ini_pessoas, lista.size, lista.size)
            }
        }
    }

    private fun abrirTabela(moduloId: String) {
        modulos?.firstOrNull { it.id == moduloId }?.let { ModulosActivity.abrirTabela(this, it) }
            ?: startActivity(Intent(this, ItensActivity::class.java).putExtra(ItensActivity.EXTRA_ID, moduloId))
    }

    companion object {
        private const val POR_GRUPO = 5
    }
}
