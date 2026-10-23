package com.example.levit

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import com.example.levit.data.repository.ModuloRepository
import com.example.levit.data.repository.ResultadoModulo
import kotlinx.coroutines.launch

class ModulosActivity : HamburgerMenuBaseActivity() {

    private val moduloRepository by lazy { ModuloRepository(applicationContext) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentViewWithHamburgerMenu(R.layout.activity_modulos)
        if (!menuPronto()) return

        setupHamburgerMenuButton(R.id.ivMenu)

        findViewById<Button>(R.id.btnNovoModuloTop).setOnClickListener {
            startActivity(Intent(this, NovoModuloActivity::class.java))
        }
    }

    override fun onResume() {
        super.onResume()
        if (menuPronto()) carregarModulos()
    }

    private fun carregarModulos() {
        val progresso = findViewById<ProgressBar>(R.id.progressModulos)
        val mensagem = findViewById<TextView>(R.id.tvModulosMensagem)
        val lista = findViewById<LinearLayout>(R.id.llListaModulos)

        progresso.visibility = View.VISIBLE
        mensagem.visibility = View.GONE

        lifecycleScope.launch {
            when (val resultado = moduloRepository.listar()) {
                is ResultadoModulo.Sucesso -> {
                    ModuloUi.preencherCards(layoutInflater, lista, resultado.dados)
                    if (resultado.dados.isEmpty()) {
                        mensagem.text = getString(R.string.modulos_vazio)
                        mensagem.visibility = View.VISIBLE
                    }
                }
                is ResultadoModulo.Erro -> {
                    if (resultado.sessaoExpirada) {
                        redirecionarParaLogin()
                        return@launch
                    }
                    lista.removeAllViews()
                    mensagem.text = resultado.mensagem
                    mensagem.visibility = View.VISIBLE
                }
            }
            progresso.visibility = View.GONE
        }
    }
}