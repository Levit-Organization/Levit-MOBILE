package com.example.levit

import android.content.res.ColorStateList
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.example.levit.data.remote.dto.ItemPrazo
import com.example.levit.data.remote.dto.Membro
import com.example.levit.data.remote.dto.Modulo
import com.example.levit.data.remote.dto.PrazosAbertos
import com.example.levit.data.repository.EquipeRepository
import com.example.levit.data.repository.InicioRepository
import com.example.levit.data.repository.ModuloRepository
import com.example.levit.data.repository.ResultadoApi
import com.example.levit.data.repository.ResultadoModulo
import com.example.levit.databinding.FragmentInicioBinding
import com.example.levit.databinding.ItemAtencaoBinding
import com.example.levit.databinding.ItemIndicadorInicioBinding
import com.example.levit.databinding.ItemPrazoInicioBinding
import com.example.levit.databinding.ItemTabelaInicioBinding
import com.example.levit.ui.Prazos
import com.example.levit.ui.adicionarTexto
import com.example.levit.ui.cor
import com.example.levit.ui.divisoria
import com.example.levit.ui.dp
import com.example.levit.ui.inflarFilho
import com.example.levit.ui.mostrarAvatar
import com.example.levit.ui.mostrarCarregando
import com.example.levit.ui.mostrarEstado
import com.example.levit.ui.pessoaResponsavel
import com.example.levit.ui.selo
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import com.example.levit.data.remote.ApiClient
import com.example.levit.data.remote.dto.Convite
import com.example.levit.data.remote.dto.Etapa
import com.example.levit.data.repository.ChamadorApi
import com.example.levit.ui.Datas
import com.example.levit.ui.Funil
import com.example.levit.ui.coresDaEtapa
import com.example.levit.ui.texto
import com.example.levit.data.remote.dto.AutomacaoResumo

/**
 * Início (Dashboard.jsx do web): primeiro o que ninguém pode esquecer (prazos e
 * o que pede atenção), depois as tabelas e a equipe. Automações e recrutamento
 * entram nas etapas deles.
 */
class DashboardFragment : HamburgerMenuBaseFragment() {

    override val pageLayoutResId: Int = R.layout.fragment_inicio

    private var _b: FragmentInicioBinding? = null
    private val b get() = checkNotNull(_b)

    private val moduloRepository by lazy { ModuloRepository(requireContext().applicationContext) }
    private val equipeRepository by lazy { EquipeRepository(requireContext().applicationContext) }
    private val inicioRepository by lazy { InicioRepository(requireContext().applicationContext) }

    private var modulos: List<Modulo>? = null
    private var erroModulos: String? = null
    private var equipe: List<Membro>? = null
    private var equipeSemAcesso = false
    private var prazos: PrazosAbertos? = null
    private var erroPrazos: String? = null
    private var escopoPrazos = "meus"

    private val chamador by lazy { ChamadorApi(requireContext().applicationContext) }
    private val recrutamentoApi by lazy { ApiClient.recrutamentoApi(requireContext().applicationContext) }
    private var convites: List<Convite> = emptyList()
    private var etapasDaVaga: List<Etapa>? = null
    private var vagaSemAcesso = false

    private val tabelas get() = modulos.orEmpty().filter { it.tipo != "recrutamento" }
    private val vagas get() = modulos.orEmpty().filter { it.tipo == "recrutamento" }
    private fun vagaDestaque() = vagas.lastOrNull()

    private var automacoes: List<AutomacaoResumo> = emptyList()


    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        if (!menuFoiMontado()) return

        val pagina = view.findViewById<FrameLayout>(R.id.pageContentContainer).getChildAt(0)
        _b = FragmentInicioBinding.bind(pagina)
        setupHamburgerMenuButton(R.id.ivMenu)

        val primeiroNome = authRepository.usuarioLogado()?.nome?.trim()
            ?.split(Regex("\\s+"))?.firstOrNull().orEmpty()
        val hora = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        b.tvSaudacao.text = getString(
            when {
                hora < 12 -> R.string.ini_bom_dia
                hora < 18 -> R.string.ini_boa_tarde
                else -> R.string.ini_boa_noite
            }, primeiroNome
        )
        b.tvData.text = SimpleDateFormat("EEEE, d 'de' MMMM", Locale.forLanguageTag("pt-BR"))
            .format(Date()).replaceFirstChar { it.uppercase() }

        val nav = findNavController()
        b.grupoEscopo.check(if (escopoPrazos == "meus") b.btnMeus.id else b.btnDaEquipe.id)
        b.grupoEscopo.addOnButtonCheckedListener { _, id, marcado ->
            if (marcado) {
                escopoPrazos = if (id == b.btnMeus.id) "meus" else "todos"
                mostrarPrazos()
            }
        }
        b.btnNovaTabela.setOnClickListener { nav.navigate(R.id.action_dashboard_to_novoModulo) }
        b.tvVerTodas.setOnClickListener { nav.navigate(R.id.action_global_to_modulos) }
        b.tvVerEquipe.setOnClickListener { nav.navigate(R.id.action_global_to_equipe) }
        b.btnConvidar.setOnClickListener { nav.navigate(R.id.action_global_to_equipe) }
        b.tvAbrirRecrutamento.setOnClickListener { nav.navigate(R.id.recrutamentoFragment) }
    }

    override fun onResume() {
        super.onResume()
        if (menuFoiMontado()) carregar()
    }

    override fun onDestroyView() {
        _b = null
        super.onDestroyView()
    }

    private fun carregar() {
        if (modulos == null) renderizar()
        viewLifecycleOwner.lifecycleScope.launch {
            var sessaoExpirou = false
            coroutineScope {
                val m = async { moduloRepository.listar() }
                val e = async { equipeRepository.listarMembros() }
                val c = async { equipeRepository.listarConvites() }
                val p = async { inicioRepository.prazos() }
                val a = async { inicioRepository.automacoes() }

                when (val r = m.await()) {
                    is ResultadoModulo.Sucesso -> { modulos = r.dados; erroModulos = null }
                    is ResultadoModulo.Erro -> {
                        if (r.sessaoExpirada) sessaoExpirou = true
                        erroModulos = r.mensagem
                    }
                }
                when (val r = e.await()) {
                    is ResultadoApi.Sucesso -> { equipe = r.dados; equipeSemAcesso = false }
                    is ResultadoApi.Erro -> equipeSemAcesso = r.semPermissao
                }
                // Convites dependem de permissão: sem acesso, só não aparecem.
                convites = (c.await() as? ResultadoApi.Sucesso)?.dados.orEmpty()
                // Automações dependem de permissão: sem acesso, só não aparecem.
                automacoes = (a.await() as? ResultadoApi.Sucesso)?.dados.orEmpty()
                when (val r = p.await()) {
                    is ResultadoApi.Sucesso -> { prazos = r.dados; erroPrazos = null }
                    is ResultadoApi.Erro -> erroPrazos = r.mensagem
                }
            }
            if (_b == null) return@launch
            if (sessaoExpirou) return@launch redirecionarParaLogin()
            // O resumo de recrutamento mostra a vaga mais recente.
            vagaDestaque()?.let { vaga ->
                when (val r = chamador.chamar { recrutamentoApi.kanbanDaVaga(vaga.id) }) {
                    is ResultadoApi.Sucesso -> { etapasDaVaga = Funil.etapas(r.dados); vagaSemAcesso = false }
                    is ResultadoApi.Erro -> vagaSemAcesso = r.semPermissao
                }
            }
            if (_b == null) return@launch
            renderizar()
        }
    }

    // --- Tela ---------------------------------------------------------------------

    private fun renderizar() {
        val carregando = modulos == null && erroModulos == null
        b.llConteudo.isVisible = !carregando
        if (carregando) return b.llCarregando.mostrarCarregando(4)
        b.llCarregando.removeAllViews()
        indicadores()
        mostrarPrazos()
        mostrarAtencao()
        mostrarTabelas()
        mostrarRecrutamento()
        mostrarEquipe()
    }

    private fun indicadores() {
        val totalItens = tabelas.sumOf { it.totalRegistros }
        val ativas = automacoes.count { it.ativo }
        val execucoes = automacoes.sumOf { it.estatisticas?.execucoes7d ?: 0 }
        val falhas = automacoes.sumOf { it.estatisticas?.falhas7d ?: 0 }
        val pessoas = equipe?.size ?: 0
        preencherIndicador(
            b.indTabelas, "table_chart", R.color.primary, R.color.primary_100, tabelas.size,
            resources.getQuantityString(R.plurals.ini_tabelas, tabelas.size),
            resources.getQuantityString(R.plurals.ini_itens_guardados, totalItens, totalItens), null
        )
        preencherIndicador(
            b.indAutomacoes, "bolt", R.color.info, R.color.info_bg, ativas,
            resources.getQuantityString(R.plurals.ini_automacoes_ligadas, ativas),
            resources.getQuantityString(R.plurals.ini_execucoes_semana, execucoes, execucoes),
            if (falhas > 0) resources.getQuantityString(R.plurals.ini_falhas, falhas, falhas) else null
        )
        preencherIndicador(
            b.indEquipe, "groups", R.color.success, R.color.success_bg, pessoas,
            resources.getQuantityString(R.plurals.ini_pessoas_na_equipe, pessoas), null, null
        )
    }

    private fun preencherIndicador(
        i: ItemIndicadorInicioBinding, icone: String, corIcone: Int, fundo: Int,
        valor: Int, rotulo: String, dica: String?, seloTexto: String?
    ) {
        val ctx = requireContext()
        i.tvIcone.text = icone
        i.tvIcone.setTextColor(ctx.cor(corIcone))
        i.tvIcone.backgroundTintList = ColorStateList.valueOf(ctx.cor(fundo))
        i.tvValor.text = "$valor"
        i.flSelo.removeAllViews()
        seloTexto?.let { i.flSelo.addView(ctx.selo(it, R.color.danger, R.color.danger_bg)) }
        i.tvRotulo.text = rotulo
        i.tvDica.text = dica
        i.tvDica.isVisible = dica != null
    }

    /** Prazos: atrasados, hoje e próximos 7 dias; "Meus" ou "Da equipe". */
    private fun mostrarPrazos() {
        val ctx = requireContext()
        val destino = b.llPrazos
        destino.removeAllViews()
        val dados = prazos
        if (dados == null) {
            val mensagemErro = erroPrazos
            if (mensagemErro != null) destino.adicionarTexto(mensagemErro, R.color.danger, 14f, topo = 0)
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
                setTextColor(ctx.cor(corTitulo))
            })
            lista.take(POR_GRUPO).forEach { destino.addView(linhaDePrazo(it, destino)) }
            if (lista.size > POR_GRUPO) {
                destino.adicionarTexto(getString(R.string.ini_e_mais, lista.size - POR_GRUPO), topo = 2)
            }
        }
    }

    private fun linhaDePrazo(item: ItemPrazo, pai: ViewGroup): View {
        // A urgência já está no título do grupo: aqui só a data (sem repetir "Vence hoje").
        val situacao = Prazos.situacao(item.prazo)
        val prazo = if (situacao?.tipo == Prazos.Tipo.HOJE) null else situacao?.rotulo
        val l = ItemPrazoInicioBinding.inflate(layoutInflater, pai, false)
        l.tvIcone.text = item.moduloIcone ?: "table_chart"
        l.tvTitulo.text = item.titulo ?: getString(R.string.itens_sem_titulo)
        l.tvDetalhe.text = listOfNotNull(
            getString(R.string.ini_modulo_coluna, item.moduloNome.orEmpty(), item.colunaPrazo.orEmpty()),
            prazo
        ).joinToString(" · ")
        item.responsavelNome?.let { l.flResponsavel.addView(requireContext().pessoaResponsavel(it, comNome = false)) }
        l.root.setOnClickListener { item.moduloId?.let { abrirRegistros(it) } }
        return l.root
    }

    /** "Precisa da sua atenção". Por ora só os atrasados; convites e automações chegam nas etapas deles. */
    private data class Atencao(
        val icone: String, val cor: Int, val fundo: Int,
        val titulo: String, val texto: String?, val abrir: () -> Unit
    )

    /** "Precisa da sua atenção": o que pede alguém agir agora, em ordem de urgência. */
    private fun mostrarAtencao() {
        val ctx = requireContext()
        val nav = findNavController()
        val atrasados = prazos?.atrasados.orEmpty()
        val meusAtrasados = atrasados.count { it.meu == true }
        val pausadas = automacoes.filter { it.pausadaPorFalhasEm != null && !it.ativo }
        val parados = etapasDaVaga.orEmpty().flatMap { it.candidatos }
            .count { Datas.estaParado(it.atualizadoEm ?: it.criadoEm) }
        val vencidos = convites.count { it.expirado == true }
        val aguardando = convites.size - vencidos

        val itens = buildList {
            if (atrasados.isNotEmpty()) add(Atencao(
                "event_busy", R.color.danger, R.color.danger_bg,
                resources.getQuantityString(R.plurals.ini_itens_atrasados, atrasados.size, atrasados.size),
                if (meusAtrasados > 0) resources.getQuantityString(R.plurals.ini_com_voce, meusAtrasados, meusAtrasados)
                else getString(R.string.ini_nenhum_com_voce)
            ) { atrasados.first().moduloId?.let { abrirRegistros(it) } })
            pausadas.forEach { a ->
                add(Atencao(
                    "pause_circle", R.color.warning, R.color.warning_bg,
                    getString(R.string.ini_automacao_pausada, a.nome),
                    getString(R.string.ini_automacao_pausada_desc)
                ) { nav.navigate(R.id.automacoesFragment) })
            }
            if (parados > 0) add(Atencao(
                "schedule", R.color.warning, R.color.warning_bg,
                resources.getQuantityString(R.plurals.ini_candidatos_parados, parados, parados),
                vagaDestaque()?.let { getString(R.string.ini_em_vaga, it.nome) }
            ) { nav.navigate(R.id.recrutamentoFragment) })
            if (vencidos > 0) add(Atencao(
                "mail", R.color.danger, R.color.danger_bg,
                resources.getQuantityString(R.plurals.ini_convites_venceram, vencidos, vencidos),
                getString(R.string.ini_reenvie)
            ) { nav.navigate(R.id.action_global_to_equipe) })
            if (aguardando > 0) add(Atencao(
                "forward_to_inbox", R.color.info, R.color.info_bg,
                resources.getQuantityString(R.plurals.ini_convites_aguardando, aguardando, aguardando),
                getString(R.string.ini_quem_aceitar)
            ) { nav.navigate(R.id.action_global_to_equipe) })
        }

        val destino = b.llAtencao
        destino.removeAllViews()
        if (itens.isEmpty()) {
            destino.mostrarEstado(
                getString(R.string.ini_tudo_em_dia), getString(R.string.ini_tudo_em_dia_desc), R.drawable.ic_calendar
            )
            destino.getChildAt(0)?.background = null
            return
        }
        itens.forEachIndexed { i, a ->
            if (i > 0) destino.addView(ctx.divisoria())
            val l = ItemAtencaoBinding.inflate(layoutInflater, destino, false)
            l.tvIcone.text = a.icone
            l.tvIcone.setTextColor(ctx.cor(a.cor))
            l.tvIcone.backgroundTintList = ColorStateList.valueOf(ctx.cor(a.fundo))
            l.tvTitulo.text = a.titulo
            l.tvTexto.text = a.texto
            l.tvTexto.isVisible = a.texto != null
            l.root.setOnClickListener { a.abrir() }
            destino.addView(l.root)
        }
    }

    /** "Suas tabelas": até 6, ou o convite para criar a primeira. */
    private fun mostrarTabelas() {
        val destino = b.llTabelas
        destino.removeAllViews()
        b.tvVerTodas.isVisible = false
        when {
            erroModulos != null -> destino.mostrarEstado(
                getString(R.string.tab_erro_carregar), erroModulos, R.drawable.ic_warning,
                getString(R.string.tab_tentar), erro = true
            ) { carregar() }
            tabelas.isEmpty() -> destino.mostrarEstado(
                getString(R.string.tab_nenhuma_titulo), getString(R.string.tab_nenhuma_desc),
                R.drawable.ic_inventory, getString(R.string.tab_criar_primeira)
            ) { findNavController().navigate(R.id.action_dashboard_to_novoModulo) }
            else -> {
                tabelas.take(6).forEach { t ->
                    val l = ItemTabelaInicioBinding.inflate(layoutInflater, destino, false)
                    l.tvIcone.text = t.icone?.takeIf { it.isNotBlank() } ?: "extension"
                    l.tvNome.text = t.nome
                    l.tvItens.text = if (t.tipo == "financeiro") getString(R.string.ini_entradas_saidas)
                    else resources.getQuantityString(R.plurals.tab_n_itens, t.totalRegistros, t.totalRegistros)
                    l.root.setOnClickListener { abrirRegistros(t.id) }
                    destino.addView(l.root)
                }
                if (tabelas.size > 6) {
                    b.tvVerTodas.isVisible = true
                    b.tvVerTodas.text = getString(R.string.ini_ver_todas, tabelas.size)
                }
            }
        }
    }

    private fun mostrarRecrutamento() {
        val ctx = requireContext()
        val vaga = vagaDestaque()
        b.tvAbrirRecrutamento.setText(if (vaga != null) R.string.ini_abrir_quadro else R.string.ini_abrir_vaga)
        b.llCarregandoVaga.removeAllViews()
        b.llBarraFunil.removeAllViews()
        b.grupoLegenda.removeAllViews()
        b.llBarraFunil.isVisible = false
        b.grupoLegenda.isVisible = false
        b.tvRecrutamento.isVisible = true
        when {
            vaga == null -> b.tvRecrutamento.setText(R.string.ini_nenhuma_vaga)
            vagaSemAcesso -> b.tvRecrutamento.setText(R.string.ini_sem_acesso_vaga)
            etapasDaVaga == null -> {
                b.tvRecrutamento.isVisible = false
                b.llCarregandoVaga.mostrarCarregando(1)
            }
            else -> {
                val etapas = etapasDaVaga ?: return
                val total = etapas.sumOf { it.candidatos.size }
                b.tvRecrutamento.setTextColor(ctx.cor(R.color.ink_soft))
                b.tvRecrutamento.text = SpannableStringBuilder().apply {
                    append(vaga.nome)
                    setSpan(ForegroundColorSpan(ctx.cor(R.color.ink)), 0, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    append(" · ").append(resources.getQuantityString(R.plurals.ini_candidatos, total, total))
                    if (vagas.size > 1) append(" · ").append(resources.getQuantityString(R.plurals.ini_vagas_abertas, vagas.size, vagas.size))
                }
                if (total > 0) {
                    // A barra do funil: cada etapa ocupa o tamanho de quantos estão nela.
                    b.llBarraFunil.isVisible = true
                    b.grupoLegenda.isVisible = true
                    etapas.forEachIndexed { i, e ->
                        val qtd = e.candidatos.size
                        b.llBarraFunil.addView(b.llBarraFunil.inflarFilho<View>(R.layout.item_segmento_funil).apply {
                            backgroundTintList = ColorStateList.valueOf(
                                ctx.cor(if (qtd > 0) coresDaEtapa(i, etapas.size).first else R.color.divider)
                            )
                            (layoutParams as LinearLayout.LayoutParams).weight = maxOf(qtd.toFloat(), 0.35f)
                        })
                        b.grupoLegenda.addView(ctx.texto(SpannableStringBuilder("●  ${e.nome}: $qtd").apply {
                            setSpan(ForegroundColorSpan(ctx.cor(coresDaEtapa(i, etapas.size).first)), 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                        }, 12f))
                    }
                }
            }
        }
    }

    private fun mostrarEquipe() {
        val lista = equipe
        b.tvEquipe.isVisible = false
        b.linhaSoVoce.isVisible = false
        b.linhaAvatares.isVisible = false
        when {
            equipeSemAcesso -> { b.tvEquipe.isVisible = true; b.tvEquipe.setText(R.string.ini_sem_acesso_equipe) }
            lista == null -> { b.tvEquipe.isVisible = true; b.tvEquipe.text = "" }
            lista.size <= 1 -> b.linhaSoVoce.isVisible = true
            else -> {
                b.linhaAvatares.isVisible = true
                b.llAvatares.removeAllViews()
                lista.take(6).forEachIndexed { i, m ->
                    b.llAvatares.addView(b.llAvatares.inflarFilho<TextView>(R.layout.item_avatar_pilha).apply {
                        mostrarAvatar(m.nome)
                        if (i > 0) (layoutParams as LinearLayout.LayoutParams).marginStart = -requireContext().dp(8)
                    })
                }
                b.tvPessoas.text = resources.getQuantityString(R.plurals.ini_pessoas, lista.size, lista.size)
            }
        }
    }

    companion object {
        private const val POR_GRUPO = 5
    }
}
