package com.example.levit

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import com.example.levit.data.remote.dto.Modulo
import com.example.levit.data.repository.ModuloRepository
import com.example.levit.data.repository.ResultadoModulo
import kotlinx.coroutines.launch

class DashboardActivity : HamburgerMenuBaseActivity() {

    private val moduloRepository by lazy { ModuloRepository(applicationContext) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentViewWithHamburgerMenu(R.layout.activity_dashboard)
        if (!menuPronto()) return

        setupHamburgerMenuButton(R.id.ivMenu)

        findViewById<TextView>(R.id.tvAvatar).text =
            iniciais(authRepository.usuarioLogado()?.nome)

        findViewById<View>(R.id.btnAddModulo).setOnClickListener {
            startActivity(Intent(this, NovoModuloActivity::class.java))
        }

        findViewById<View>(R.id.tvVerTodos).setOnClickListener {
            startActivity(Intent(this, ModulosActivity::class.java))
        }
    }

    // Recarrega ao voltar da criação, para o módulo novo aparecer na hora.
    override fun onResume() {
        super.onResume()
        if (menuPronto()) carregarModulos()
    }

    private fun carregarModulos() {
        val progresso = findViewById<ProgressBar>(R.id.progressModulos)
        val mensagem = findViewById<TextView>(R.id.tvModulosMensagem)

        progresso.visibility = View.VISIBLE
        mensagem.visibility = View.GONE

        lifecycleScope.launch {
            when (val resultado = moduloRepository.listar()) {
                is ResultadoModulo.Sucesso -> mostrarModulos(resultado.dados)
                is ResultadoModulo.Erro -> {
                    if (resultado.sessaoExpirada) {
                        redirecionarParaLogin()
                        return@launch
                    }
                    findViewById<LinearLayout>(R.id.llModulos).removeAllViews()
                    findViewById<View>(R.id.tvVerTodos).visibility = View.GONE
                    mensagem.text = resultado.mensagem
                    mensagem.visibility = View.VISIBLE
                }
            }
            progresso.visibility = View.GONE
        }
    }

    private fun mostrarModulos(modulos: List<Modulo>) {
        findViewById<TextView>(R.id.tvStatModulos).text = modulos.size.toString()
        findViewById<TextView>(R.id.tvStatRegistros).text =
            modulos.sumOf { it.totalRegistros }.toString()

        val mensagem = findViewById<TextView>(R.id.tvModulosMensagem)
        if (modulos.isEmpty()) {
            mensagem.text = getString(R.string.modulos_vazio)
            mensagem.visibility = View.VISIBLE
        }

        ModuloUi.preencherCards(
            layoutInflater,
            findViewById(R.id.llModulos),
            modulos.take(LIMITE_NO_DASHBOARD)
        )

        findViewById<View>(R.id.tvVerTodos).visibility =
            if (modulos.size > LIMITE_NO_DASHBOARD) View.VISIBLE else View.GONE
    }

    companion object {
        private const val LIMITE_NO_DASHBOARD = 6
    }
}