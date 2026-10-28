package com.example.levit

import android.content.Intent
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.example.levit.data.remote.ApiClient
import com.example.levit.databinding.ItemSinoBinding
import com.example.levit.data.repository.Resultado
import com.example.levit.data.repository.chamarApi
import com.example.levit.ui.Notificacoes
import com.example.levit.ui.PainelNotificacoes
import com.example.levit.ui.avisar
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.lifecycleScope
import com.example.levit.auth.LoginActivity
import com.example.levit.data.repository.AuthRepository
import com.google.android.material.navigation.NavigationView
import kotlinx.coroutines.launch

abstract class HamburgerMenuBaseActivity : AppCompatActivity(), NavigationView.OnNavigationItemSelectedListener {

    protected lateinit var hamburgerDrawerLayout: DrawerLayout
    protected lateinit var hamburgerNavigationView: NavigationView

    protected val authRepository by lazy { AuthRepository(applicationContext) }

    // As telas filhas usam para saber se o menu foi montado (sessão válida)
    // e se a tela ainda está de pé (não foi mandada para o login).
    protected fun menuPronto(): Boolean = ::hamburgerDrawerLayout.isInitialized && !isFinishing

    // Injeta o layout da página atual dentro do esqueleto que contém o menu hambúrguer
    fun setContentViewWithHamburgerMenu(layoutResID: Int) {
        montarComMenu { pageContainer -> layoutInflater.inflate(layoutResID, pageContainer, true) }
    }

    // Variante para telas com ViewBinding: recebe a raiz já inflada.
    fun setContentViewWithHamburgerMenu(conteudo: View) {
        montarComMenu { pageContainer ->
            pageContainer.addView(conteudo, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        }
    }

    // A sessão pode cair com a tela aberta (8 h do JWT, ou 401 de token
    // revogado limpando a sessão no AuthInterceptor). Ao voltar para a
    // tela, confere de novo, como o ProtectedRoute do web faz a cada rota.
    override fun onResume() {
        super.onResume()
        if (menuPronto() && !authRepository.estaAutenticado()) redirecionarParaLogin()
    }

    private fun montarComMenu(preencher: (FrameLayout) -> Unit) {
        // Toda tela que passa por aqui é uma rota privada. Se o token já
        // expirou (ou nunca existiu), não faz sentido montar a tela: manda
        // direto pro login, do mesmo jeito que o interceptor 401 do
        // client-frontend/src/services/api.js redireciona pra /login.
        if (!authRepository.estaAutenticado()) {
            redirecionarParaLogin()
            return
        }

        val fullDrawerLayout = layoutInflater.inflate(R.layout.activity_hamburger_menu_container, null) as DrawerLayout
        val pageContainer = fullDrawerLayout.findViewById<FrameLayout>(R.id.pageContentContainer)

        preencher(pageContainer)
        super.setContentView(fullDrawerLayout)

        // Android 15+ desenha de ponta a ponta: o conteúdo desce da barra de
        // status e sobe da barra de navegação, em todas as telas com menu.
        ViewCompat.setOnApplyWindowInsetsListener(pageContainer) { view, insets ->
            val barras = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            view.setPadding(barras.left, barras.top, barras.right, barras.bottom)
            insets
        }

        this.hamburgerDrawerLayout = fullDrawerLayout
        this.hamburgerNavigationView = fullDrawerLayout.findViewById(R.id.hamburgerNavigationView)
        hamburgerNavigationView.setNavigationItemSelectedListener(this)
        // Item da tela atual marcado, como o item ativo da sidebar do web.
        telasDoMenu.entries.firstOrNull { it.value == javaClass }?.let { hamburgerNavigationView.setCheckedItem(it.key) }

        // Gerir o botão voltar para fechar o menu caso esteja aberto
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (hamburgerDrawerLayout.isDrawerOpen(GravityCompat.START)) {
                    hamburgerDrawerLayout.closeDrawer(GravityCompat.START)
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                    isEnabled = true
                }
            }
        })
    }

    // Método reutilizável para ligar qualquer ícone de menu hambúrguer da página à abertura do menu
    protected fun setupHamburgerMenuButton(hamburgerButtonId: Int) {
        findViewById<View>(hamburgerButtonId)?.setOnClickListener {
            hamburgerDrawerLayout.openDrawer(GravityCompat.START)
        }
    }

    // Telas do menu, na ordem da navegação do web (Layout.jsx).
    private val telasDoMenu = mapOf(
        R.id.nav_dashboard to DashboardActivity::class.java,
        R.id.nav_modulos to ModulosActivity::class.java,
        R.id.nav_recrutamento to RecrutamentoActivity::class.java,
        R.id.nav_automacoes to AutomacoesActivity::class.java,
        R.id.nav_equipe to EquipeActivity::class.java,
        R.id.nav_relatorios to RelatoriosActivity::class.java,
        R.id.nav_configuracoes to ConfiguracoesActivity::class.java,
    )

    // Ações globais dos itens do menu hambúrguer
    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        val destino = telasDoMenu[item.itemId]
        when {
            item.itemId == R.id.nav_sair -> sair()
            destino != null && destino != javaClass -> {
                startActivity(Intent(this, destino))
                finish()
                // Troca de seção sem a animação de "abrir por cima" (é a mesma camada).
                @Suppress("DEPRECATION")
                overridePendingTransition(0, 0)
            }
        }
        if (::hamburgerDrawerLayout.isInitialized) {
            hamburgerDrawerLayout.closeDrawer(GravityCompat.START)
        }
        return true
    }

    // --- Notificações (BotaoNotificacoes + useNaoLidas do web) ------------------------

    private var sinoIcone: TextView? = null
    private var sinoContador: TextView? = null
    private var naoLidas = 0

    /**
     * Sino com contador de não lidas no cabeçalho. A contagem é uma consulta
     * leve a cada 30 s enquanto a tela está visível (e ao voltar a ela); quando
     * chegam novas, avisa uma vez.
     */
    protected fun setupSino(destino: FrameLayout) {
        val sino = ItemSinoBinding.inflate(layoutInflater, destino, false)
        val icone = sino.tvIcone
        val contador = sino.tvContador
        val botao = sino.root
        botao.contentDescription = Notificacoes.rotuloDoSino(0)
        botao.setOnClickListener {
            PainelNotificacoes(this@HamburgerMenuBaseActivity, naoLidas, { mostrarContagem(it, avisarNovas = false) },
                { abrirLink(it) }, { redirecionarParaLogin() }).abrir()
        }
        destino.addView(botao)
        sinoIcone = icone
        sinoContador = contador
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (isActive) {
                    (chamarApi("") { ApiClient.notificacaoApi(this@HamburgerMenuBaseActivity).contagem() } as? Resultado.Sucesso)?.let { r ->
                        mostrarContagem(r.dados.get("nao_lidas")?.takeIf { it.isJsonPrimitive }?.asString?.toDoubleOrNull()?.toInt() ?: 0, avisarNovas = true)
                    }
                    delay(INTERVALO_CONTAGEM_MS)
                }
            }
        }
    }

    private fun mostrarContagem(n: Int, avisarNovas: Boolean) {
        naoLidas = n
        sinoIcone?.text = if (n > 0) "notifications_active" else "notifications_none"
        sinoContador?.apply { isVisible = n > 0; text = Notificacoes.contador(n) }
        (sinoIcone?.parent as? View)?.contentDescription = Notificacoes.rotuloDoSino(n)
        // A contagem vista fica fora da tela: trocar de tela não pode repetir o aviso.
        val usuario = authRepository.usuarioLogado()?.id
        if (usuarioDaContagem != usuario) { usuarioDaContagem = usuario; ultimaContagemVista = null }
        val anterior = ultimaContagemVista
        if (avisarNovas && anterior != null && n > anterior) {
            val novas = n - anterior
            avisar(if (novas == 1) getString(R.string.not_uma_nova) else getString(R.string.not_n_novas, novas))
        }
        ultimaContagemVista = n
    }

    /** Links das notificações (rotas do web) para as telas do app. */
    private fun abrirLink(link: String) {
        val caminho = link.substringBefore('?').trimEnd('/')
        val item = Regex("[?&]item=([^&]+)").find(link)?.groupValues?.get(1)
        val tabela = Regex("^/modulos/([^/]+)/(registros|financeiro)$").find(caminho)
        val intent = when {
            tabela != null && tabela.groupValues[2] == "financeiro" ->
                Intent(this, FinanceiroActivity::class.java).putExtra(FinanceiroActivity.EXTRA_ID, tabela.groupValues[1])
            tabela != null -> Intent(this, ItensActivity::class.java).putExtra(ItensActivity.EXTRA_ID, tabela.groupValues[1])
                .apply { item?.let { putExtra(ItensActivity.EXTRA_ITEM, android.net.Uri.decode(it)) } }
            caminho.startsWith("/recrutamento") -> Intent(this, RecrutamentoActivity::class.java)
            caminho.startsWith("/automacoes") -> Intent(this, AutomacoesActivity::class.java)
            caminho.startsWith("/team") -> Intent(this, EquipeActivity::class.java)
            caminho.startsWith("/relatorios") -> Intent(this, RelatoriosActivity::class.java)
            caminho.startsWith("/configuracoes") -> Intent(this, ConfiguracoesActivity::class.java)
            caminho.startsWith("/modulos") -> Intent(this, ModulosActivity::class.java)
            else -> Intent(this, DashboardActivity::class.java)
        }
        startActivity(intent)
    }

    private fun sair() {
        lifecycleScope.launch {
            authRepository.signOut()
            redirecionarParaLogin()
        }
    }

    companion object {
        private const val INTERVALO_CONTAGEM_MS = 30_000L
        private var ultimaContagemVista: Int? = null
        private var usuarioDaContagem: String? = null
    }

    protected fun redirecionarParaLogin() {
        val intent = Intent(this, LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
}