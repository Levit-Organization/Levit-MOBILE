package com.example.levit

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.os.bundleOf
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import com.example.levit.auth.ValidacaoRedefinicaoSenha
import com.example.levit.data.repository.AuthRepository

class HostActivity : AppCompatActivity() {

    private lateinit var navController: NavController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_host)

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