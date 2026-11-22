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

/**
 * Início (Dashboard.jsx do web): primeiro o que ninguém pode esquecer (prazos e
 * o que pede atenção), depois as tabelas e a equipe. Automações e recrutamento
 * entram nas etapas deles.
 */
class DashboardFragment : HamburgerMenuBaseFragment() {

    override val pageLayoutResId: Int = R.layout.fragment_inicio

    private var _b: FragmentInicioBinding? = null
    private val b get() = _b!!

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

    private val tabelas get() = modulos.orEmpty().filter { it.tipo != "recrutamento" }

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

        // Ainda sem tela: ficam escondidos até a etapa deles.
        b.cartaoRecrutamento.isVisible = false
        b.indAutomacoes.root.isVisible = false

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
                val p = async { inicioRepository.prazos() }

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
                when (val r = p.await()) {
                    is ResultadoApi.Sucesso -> { prazos = r.dados; erroPrazos = null }
                    is ResultadoApi.Erro -> erroPrazos = r.mensagem
                }
            }
            if (_b == null) return@launch
            if (sessaoExpirou) return@launch redirecionarParaLogin()
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
        mostrarEquipe()
    }

    private fun indicadores() {
        val totalItens = tabelas.sumOf { it.totalRegistros }
        val pessoas = equipe?.size ?: 0
        preencherIndicador(
            b.indTabelas, "table_chart", R.color.primary, R.color.primary_100, tabelas.size,
            resources.getQuantityString(R.plurals.ini_tabelas, tabelas.size),
            resources.getQuantityString(R.plurals.ini_itens_guardados, totalItens, totalItens)
        )
        preencherIndicador(
            b.indEquipe, "groups", R.color.success, R.color.success_bg, pessoas,
            resources.getQuantityString(R.plurals.ini_pessoas_na_equipe, pessoas), null
        )
    }

    private fun preencherIndicador(
        i: ItemIndicadorInicioBinding, icone: String, corIcone: Int, fundo: Int,
        valor: Int, rotulo: String, dica: String?
    ) {
        val ctx = requireContext()
        i.tvIcone.text = icone
        i.tvIcone.setTextColor(ctx.cor(corIcone))
        i.tvIcone.backgroundTintList = ColorStateList.valueOf(ctx.cor(fundo))
        i.tvValor.text = "$valor"
        i.flSelo.removeAllViews()
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
    private fun mostrarAtencao() {
        val ctx = requireContext()
        val destino = b.llAtencao
        destino.removeAllViews()
        val atrasados = prazos?.atrasados.orEmpty()
        if (atrasados.isEmpty()) {
            destino.mostrarEstado(
                getString(R.string.ini_tudo_em_dia), getString(R.string.ini_tudo_em_dia_desc), R.drawable.ic_calendar
            )
            destino.getChildAt(0)?.background = null
            return
        }
        val meus = atrasados.count { it.meu == true }
        val a = ItemAtencaoBinding.inflate(layoutInflater, destino, false)
        a.tvIcone.text = "event_busy"
        a.tvIcone.setTextColor(ctx.cor(R.color.danger))
        a.tvIcone.backgroundTintList = ColorStateList.valueOf(ctx.cor(R.color.danger_bg))
        a.tvTitulo.text = resources.getQuantityString(R.plurals.ini_itens_atrasados, atrasados.size, atrasados.size)
        a.tvTexto.text = if (meus > 0) resources.getQuantityString(R.plurals.ini_com_voce, meus, meus)
        else getString(R.string.ini_nenhum_com_voce)
        a.tvTexto.isVisible = true
        a.root.setOnClickListener { atrasados.first().moduloId?.let { abrirRegistros(it) } }
        destino.addView(a.root)
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
