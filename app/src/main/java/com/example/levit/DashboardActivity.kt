package com.example.levit

import android.content.Intent
import android.os.Bundle
import android.widget.Toast

class DashboardActivity : HamburgerMenuBaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Puxa e injeta o menu hambúrguer à volta do design original do Dashboard
        setContentViewWithHamburgerMenu(R.layout.activity_dashboard)

        // Liga o botão de hambúrguer da barra superior (ivMenu) para abrir o menu lateral
        setupHamburgerMenuButton(R.id.ivMenu)

        // Restantes ações dos botões do Dashboard
        findViewById<android.view.View>(R.id.btnAddModulo)?.setOnClickListener {
            startActivity(Intent(this, NovoModuloActivity::class.java))
        }

        findViewById<android.view.View>(R.id.tvVerFunil)?.setOnClickListener {
            Toast.makeText(this, "Funil de recrutamento em desenvolvimento", Toast.LENGTH_SHORT).show()
        }

        findViewById<android.view.View>(R.id.btnVerMais)?.setOnClickListener {
            Toast.makeText(this, "Equipe em desenvolvimento", Toast.LENGTH_SHORT).show()
        }
    }
}