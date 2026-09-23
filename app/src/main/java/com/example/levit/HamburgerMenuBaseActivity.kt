package com.example.levit

import android.content.Intent
import android.view.MenuItem
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import com.google.android.material.navigation.NavigationView

abstract class HamburgerMenuBaseActivity : AppCompatActivity(), NavigationView.OnNavigationItemSelectedListener {

    protected lateinit var hamburgerDrawerLayout: DrawerLayout
    protected lateinit var hamburgerNavigationView: NavigationView

    // Injeta o layout da página atual dentro do esqueleto que contém o menu hambúrguer
    fun setContentViewWithHamburgerMenu(layoutResID: Int) {
        val fullDrawerLayout = layoutInflater.inflate(R.layout.activity_hamburger_menu_container, null) as DrawerLayout
        val pageContainer = fullDrawerLayout.findViewById<FrameLayout>(R.id.pageContentContainer)

        layoutInflater.inflate(layoutResID, pageContainer, true)
        super.setContentView(fullDrawerLayout)

        this.hamburgerDrawerLayout = fullDrawerLayout
        this.hamburgerNavigationView = fullDrawerLayout.findViewById(R.id.hamburgerNavigationView)
        hamburgerNavigationView.setNavigationItemSelectedListener(this)

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
        findViewById<android.view.View>(hamburgerButtonId)?.setOnClickListener {
            hamburgerDrawerLayout.openDrawer(GravityCompat.START)
        }
    }

    // Ações globais dos itens do menu hambúrguer
    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.nav_dashboard -> {
                if (this !is DashboardActivity) {
                    startActivity(Intent(this, DashboardActivity::class.java))
                    finish()
                }
            }
            R.id.nav_modulos -> {
                // Só abre a ModulosActivity se ainda não estivermos nela
                if (this !is ModulosActivity) {
                    startActivity(Intent(this, ModulosActivity::class.java))
                    finish() // Fecha a activity atual para não acumular páginas na memória (opcional, mas recomendado)
                }
            }
            R.id.nav_equipe -> {
                Toast.makeText(this, "Equipe selecionada", Toast.LENGTH_SHORT).show()
            }
            R.id.nav_sobre -> {
                Toast.makeText(this, "Sobre Nós selecionado", Toast.LENGTH_SHORT).show()
            }
            R.id.nav_perfil -> {
                Toast.makeText(this, "Perfil selecionado", Toast.LENGTH_SHORT).show()
            }
        }
        hamburgerDrawerLayout.closeDrawer(GravityCompat.START)
        return true
    }
}