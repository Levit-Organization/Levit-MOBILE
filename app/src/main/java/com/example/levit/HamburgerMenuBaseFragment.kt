package com.example.levit

import android.os.Bundle
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.OnBackPressedCallback
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.example.levit.data.repository.AuthRepository
import com.google.android.material.navigation.NavigationView
import kotlinx.coroutines.launch
import androidx.core.os.bundleOf
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.example.levit.data.remote.ApiClient
import com.example.levit.data.repository.ChamadorApi
import com.example.levit.data.repository.ResultadoApi
import com.example.levit.databinding.ItemSinoBinding
import com.example.levit.ui.Notificacoes
import com.example.levit.ui.PainelNotificacoes
import com.example.levit.ui.avisar
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

abstract class HamburgerMenuBaseFragment : Fragment(), NavigationView.OnNavigationItemSelectedListener {

    protected abstract val pageLayoutResId: Int

    protected lateinit var hamburgerDrawerLayout: DrawerLayout
    protected lateinit var hamburgerNavigationView: NavigationView
    protected fun menuFoiMontado(): Boolean = ::hamburgerDrawerLayout.isInitialized

    // Tela do nav_graph -> item da gaveta que fica marcado nela.
    private val itensDoMenu = mapOf(
        R.id.dashboardFragment to R.id.nav_dashboard,
        R.id.modulosFragment to R.id.nav_modulos,
        R.id.equipeFragment to R.id.nav_equipe,
        R.id.recrutamentoFragment to R.id.nav_recrutamento,
        R.id.automacoesFragment to R.id.nav_automacoes,
        R.id.automacaoFormFragment to R.id.nav_automacoes,
        R.id.relatoriosFragment to R.id.nav_relatorios,
    )


    protected val authRepository by lazy { AuthRepository(requireContext().applicationContext) }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        if (!authRepository.estaAutenticado()) {
            redirecionarParaLogin()
            return View(requireContext())
        }

        val fullDrawerLayout = inflater.inflate(
            R.layout.activity_hamburger_menu_container, container, false
        ) as DrawerLayout
        val pageContainer = fullDrawerLayout.findViewById<FrameLayout>(R.id.pageContentContainer)
        inflater.inflate(pageLayoutResId, pageContainer, true)

        hamburgerDrawerLayout = fullDrawerLayout
        hamburgerNavigationView = fullDrawerLayout.findViewById(R.id.hamburgerNavigationView)
        hamburgerNavigationView.setNavigationItemSelectedListener(this)

        return fullDrawerLayout
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        if (!::hamburgerDrawerLayout.isInitialized) {
            return
        }

        view.findViewById<FrameLayout>(R.id.flSino)?.let { montarSino(it) }

        // Item da tela atual marcado, como o item ativo da sidebar do web.
        itensDoMenu[findNavController().currentDestination?.id]
            ?.let { hamburgerNavigationView.setCheckedItem(it) }

        requireActivity().onBackPressedDispatcher.addCallback(
            viewLifecycleOwner,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    if (hamburgerDrawerLayout.isDrawerOpen(GravityCompat.START)) {
                        hamburgerDrawerLayout.closeDrawer(GravityCompat.START)
                    } else {
                        isEnabled = false
                        requireActivity().onBackPressedDispatcher.onBackPressed()
                        isEnabled = true
                    }
                }
            }
        )
    }

    protected fun setupHamburgerMenuButton(hamburgerButtonId: Int) {
        view?.findViewById<View>(hamburgerButtonId)?.setOnClickListener {
            hamburgerDrawerLayout.openDrawer(GravityCompat.START)
        }
    }

    protected fun abrirRegistros(moduloId: String) {
        findNavController().navigate(R.id.registrosFragment, bundleOf("moduloId" to moduloId))
    }

    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.nav_dashboard -> {
                val nav = findNavController()
                if (nav.currentDestination?.id != R.id.dashboardFragment) {
                    // O Dashboard é sempre a raiz da pilha logada: volta até ele.
                    if (!nav.popBackStack(R.id.dashboardFragment, false)) {
                        nav.navigate(R.id.dashboardFragment)
                    }
                }
            }
            R.id.nav_modulos -> {
                if (findNavController().currentDestination?.id != R.id.modulosFragment) {
                    findNavController().navigate(R.id.action_global_to_modulos)
                }
            }
            R.id.nav_equipe -> {
                if (findNavController().currentDestination?.id != R.id.equipeFragment) {
                    findNavController().navigate(R.id.action_global_to_equipe)
                }
            }
            R.id.nav_perfil -> {
                findNavController().navigate(R.id.action_global_to_perfil)
            }

            R.id.nav_recrutamento -> {
                if (findNavController().currentDestination?.id != R.id.recrutamentoFragment) {
                    findNavController().navigate(R.id.recrutamentoFragment)
                }
            }

            R.id.nav_automacoes -> {
                if (findNavController().currentDestination?.id != R.id.automacoesFragment) {
                    findNavController().navigate(R.id.automacoesFragment)
                }
            }

            R.id.nav_relatorios -> {
                if (findNavController().currentDestination?.id != R.id.relatoriosFragment) {
                    findNavController().navigate(R.id.relatoriosFragment)
                }
            }

            R.id.nav_configuracoes -> {
                if (findNavController().currentDestination?.id != R.id.configuracoesFragment) {
                    findNavController().navigate(R.id.configuracoesFragment)
                }
            }

            R.id.nav_sair -> sair()
        }
        if (::hamburgerDrawerLayout.isInitialized) {
            hamburgerDrawerLayout.closeDrawer(GravityCompat.START)
        }

        // Perfil, Sair e itens "Em breve" não são a tela atual: não tomam a marcação.
        return when (item.itemId) {
            R.id.nav_perfil, R.id.nav_sair -> false
            else -> true
        }
    }

    // --- Notificações (sino com contador, consulta a cada 30 s) ---------------------

    private var sinoIcone: TextView? = null
    private var sinoContador: TextView? = null
    private var naoLidas = 0

    override fun onDestroyView() {
        sinoIcone = null
        sinoContador = null
        super.onDestroyView()
    }

    private fun montarSino(destino: FrameLayout) {
        val sino = ItemSinoBinding.inflate(layoutInflater, destino, false)
        sinoIcone = sino.tvIcone
        sinoContador = sino.tvContador
        sino.root.contentDescription = Notificacoes.rotuloDoSino(requireContext(), 0)
        sino.root.setOnClickListener {
            PainelNotificacoes(
                requireActivity(), naoLidas,
                { mostrarContagem(it, avisarNovas = false) },
                { abrirLinkDeNotificacao(it) },
                { redirecionarParaLogin() }
            ).abrir()
        }
        destino.addView(sino.root)

        val contexto = requireContext().applicationContext
        val chamador = ChamadorApi(contexto)
        val api = ApiClient.notificacaoApi(contexto)
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (isActive) {
                    when (val r = chamador.chamar { api.contagem() }) {
                        is ResultadoApi.Sucesso -> mostrarContagem(
                            r.dados.get("nao_lidas")?.takeIf { it.isJsonPrimitive }?.asString
                                ?.toDoubleOrNull()?.toInt() ?: 0,
                            avisarNovas = true
                        )
                        is ResultadoApi.Erro -> if (r.sessaoExpirada) {
                            redirecionarParaLogin()
                            return@repeatOnLifecycle
                        }
                    }
                    delay(INTERVALO_CONTAGEM_MS)
                }
            }
        }
    }

    private fun mostrarContagem(n: Int, avisarNovas: Boolean) {
        naoLidas = n
        sinoIcone?.text = getString(if (n > 0) R.string.ic_notifications_active else R.string.ic_notifications_none)
        sinoContador?.apply { isVisible = n > 0; text = Notificacoes.contador(n) }
        (sinoIcone?.parent as? View)?.contentDescription = Notificacoes.rotuloDoSino(requireContext(), n)
        // A contagem vista fica fora da tela: trocar de tela não repete o aviso.
        val usuario = authRepository.usuarioLogado()?.id
        if (usuarioDaContagem != usuario) { usuarioDaContagem = usuario; ultimaContagemVista = null }
        val anterior = ultimaContagemVista
        if (avisarNovas && anterior != null && n > anterior && isAdded) {
            val novas = n - anterior
            requireContext().avisar(
                if (novas == 1) getString(R.string.not_uma_nova) else getString(R.string.not_n_novas, novas)
            )
        }
        ultimaContagemVista = n
    }

    /** Links das notificações (rotas do web) para as telas do app. */
    private fun abrirLinkDeNotificacao(link: String) {
        val nav = findNavController()
        val caminho = link.substringBefore('?').trimEnd('/')
        val item = Regex("[?&]item=([^&]+)").find(link)?.groupValues?.get(1)
        val tabela = Regex("^/modulos/([^/]+)/(registros|financeiro)$").find(caminho)
        when {
            tabela != null && tabela.groupValues[2] == "financeiro" ->
                nav.navigate(R.id.financeiroFragment, bundleOf("moduloId" to tabela.groupValues[1]))
            tabela != null -> nav.navigate(
                R.id.registrosFragment,
                bundleOf("moduloId" to tabela.groupValues[1], "itemId" to item?.let { android.net.Uri.decode(it) })
            )
            caminho.startsWith("/recrutamento") -> nav.navigate(R.id.recrutamentoFragment)
            caminho.startsWith("/automacoes") -> nav.navigate(R.id.automacoesFragment)
            caminho.startsWith("/team") -> nav.navigate(R.id.action_global_to_equipe)
            caminho.startsWith("/relatorios") -> nav.navigate(R.id.relatoriosFragment)
            caminho.startsWith("/configuracoes") -> nav.navigate(R.id.configuracoesFragment)
            caminho.startsWith("/modulos") -> nav.navigate(R.id.action_global_to_modulos)
            else -> nav.popBackStack(R.id.dashboardFragment, false)
        }
    }

    companion object {
        private const val INTERVALO_CONTAGEM_MS = 30_000L
        private var ultimaContagemVista: Int? = null
        private var usuarioDaContagem: String? = null
    }
    private fun sair() {
        viewLifecycleOwner.lifecycleScope.launch {
            authRepository.signOut()
            redirecionarParaLogin()
        }
    }

    protected fun redirecionarParaLogin() {
        findNavController().navigate(R.id.action_global_to_login)
    }
}