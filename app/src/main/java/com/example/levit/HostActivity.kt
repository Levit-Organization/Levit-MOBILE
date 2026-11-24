package com.example.levit

import android.content.Intent
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.os.bundleOf
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import com.example.levit.auth.ValidacaoRedefinicaoSenha
import com.example.levit.data.repository.AuthRepository

class HostActivity : AppCompatActivity() {

    private lateinit var navController: NavController

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_host)
        aplicarMargensDoSistema()

        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.navHostFragment) as NavHostFragment
        navController = navHostFragment.navController

        val autenticado = AuthRepository(applicationContext).estaAutenticado()

        val graph = navController.navInflater.inflate(R.navigation.main_graph)
        graph.setStartDestination(
            if (autenticado) R.id.dashboardFragment else R.id.boasVindasFragment
        )
        navController.graph = graph

        // Só na primeira abertura: ao girar a tela o link já foi usado.
        if (savedInstanceState == null) abrirLinkDeSenha(intent)
    }

    /**
     * Com edge-to-edge (obrigatório no Android 15+) o conteúdo ia para baixo da barra de status e
     * do recorte da câmera. O padding abaixo vale para todas as telas e acompanha o teclado.
     */
    private fun aplicarMargensDoSistema() {
        val raiz = findViewById<android.view.View>(R.id.navHostFragment)
        ViewCompat.setOnApplyWindowInsetsListener(raiz) { v, insets ->
            val tipos = WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            val barras = insets.getInsets(tipos)
            val teclado = insets.getInsets(WindowInsetsCompat.Type.ime())
            v.setPadding(barras.left, barras.top, barras.right, maxOf(barras.bottom, teclado.bottom))
            WindowInsetsCompat.CONSUMED
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        abrirLinkDeSenha(intent)
    }

    /** levit://reset-password?token=... vira a tela de nova senha. */
    private fun abrirLinkDeSenha(intent: Intent?) {
        val link = intent?.data ?: return
        val token = ValidacaoRedefinicaoSenha.tokenDoLink(link.toString())
        navController.navigate(R.id.redefinirSenhaFragment, bundleOf("token" to token))
    }
}