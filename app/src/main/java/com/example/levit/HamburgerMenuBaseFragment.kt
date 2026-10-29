package com.example.levit

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavOptions
import androidx.navigation.fragment.findNavController
import com.example.levit.data.repository.AuthRepository
import com.google.android.material.navigation.NavigationView
import kotlinx.coroutines.launch

abstract class HamburgerMenuBaseFragment : Fragment(), NavigationView.OnNavigationItemSelectedListener {

    protected abstract val pageLayoutResId: Int

    protected lateinit var hamburgerDrawerLayout: DrawerLayout
    protected lateinit var hamburgerNavigationView: NavigationView
    protected fun menuFoiMontado(): Boolean = ::hamburgerDrawerLayout.isInitialized


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
                Toast.makeText(requireContext(), "Equipe selecionada", Toast.LENGTH_SHORT).show()
            }
            R.id.nav_perfil -> {
                Toast.makeText(requireContext(), "Perfil selecionado", Toast.LENGTH_SHORT).show()
            }
            R.id.nav_sair -> sair()
        }
        if (::hamburgerDrawerLayout.isInitialized) {
            hamburgerDrawerLayout.closeDrawer(GravityCompat.START)
        }
        return true
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