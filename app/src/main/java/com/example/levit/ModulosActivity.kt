package com.example.levit

import android.content.Intent
import android.os.Bundle
import android.widget.Button

class ModulosActivity : HamburgerMenuBaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 1. Injeta o layout da página de Módulos dentro do esqueleto do menu
        setContentViewWithHamburgerMenu(R.layout.activity_modulos)

        // 2. Liga o ícone de hambúrguer desta página ao menu lateral
        setupHamburgerMenuButton(R.id.ivMenu)

        // 3. Ação para o botão de criar novo módulo no topo
        findViewById<Button>(R.id.btnNovoModuloTop)?.setOnClickListener {
            startActivity(Intent(this, NovoModuloActivity::class.java))
        }
    }
}