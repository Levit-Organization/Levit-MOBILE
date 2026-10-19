package com.example.levit

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.levit.auth.LoginActivity
import com.example.levit.data.remote.dto.CampoRequest
import com.example.levit.data.remote.dto.CriarModuloRequest
import com.example.levit.data.repository.AuthRepository
import com.example.levit.data.repository.ModuloRepository
import com.example.levit.data.repository.ResultadoModulo
import com.example.levit.databinding.ActivityNovoModuloBinding
import kotlinx.coroutines.launch

class NovoModuloActivity : AppCompatActivity() {

    // "api" é o valor que o backend aceita em campo_modulo.tipo
    private enum class TipoCampo(val label: String, val iconRes: Int, val api: String) {
        TEXTO("Texto", R.drawable.ic_text_fields, "texto"),
        NUMERO("Número", R.drawable.ic_numbers, "numero"),
        DATA("Data", R.drawable.ic_calendar, "data"),
        SELECAO_UNICA("Seleção única", R.drawable.ic_radio_button_checked, "selecao"),
    }

    private class LinhaCampo(val view: View, val tipo: TipoCampo) {
        val nome: String
            get() = view.findViewById<EditText>(R.id.etNomeCampo).text.toString().trim()

        val opcoes: List<String>
            get() = view.findViewById<EditText>(R.id.etOpcoes).text.toString()
                .split(",")
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .distinct()
    }

    private lateinit var binding: ActivityNovoModuloBinding
    private val moduloRepository by lazy { ModuloRepository(applicationContext) }
    private val linhasCampos = mutableListOf<LinhaCampo>()

    // Mesmos nomes de ícone do LEVIT Web (Material Icons)
    private var iconeSelecionado = "dashboard"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityNovoModuloBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.tvAvatar.text = iniciais(AuthRepository(applicationContext).usuarioLogado()?.nome)

        val iconOptions = listOf(
            Triple(binding.iconOptionGrid, binding.iconImageGrid, "dashboard"),
            Triple(binding.iconOptionPerson, binding.iconImagePerson, "person"),
            Triple(binding.iconOptionBriefcase, binding.iconImageBriefcase, "work"),
            Triple(binding.iconOptionDocument, binding.iconImageDocument, "description"),
            Triple(binding.iconOptionSchedule, binding.iconImageSchedule, "event"),
            Triple(binding.iconOptionImage, binding.iconImageImage, "folder"),
        )

        fun selectIcon(selected: FrameLayout) {
            iconOptions.forEach { (container, image, nomeApi) ->
                val ativo = container == selected
                setIconSelected(container, image, ativo)
                if (ativo) iconeSelecionado = nomeApi
            }
        }

        iconOptions.forEach { (container, _, _) ->
            container.setOnClickListener { selectIcon(container) }
        }

        binding.etNomeModulo.observar { atualizarBotaoCriar() }

        // Ações ainda não integradas a um back-end, mexer dps
        binding.ivMenu.setOnClickListener {
            Toast.makeText(this, "Menu em desenvolvimento", Toast.LENGTH_SHORT).show()
        }

        binding.btnAdicionarCampo.setOnClickListener {
            mostrarSeletorDeTipoDeCampo()
        }

        binding.btnCancelar.setOnClickListener {
            finish()
        }

        binding.btnCriarModulo.setOnClickListener {
            criarModulo()
        }
    }

    private fun EditText.observar(aoMudar: () -> Unit) {
        addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) = aoMudar()
        })
    }

    private fun setIconSelected(container: FrameLayout, image: ImageView, selected: Boolean) {
        if (selected) {
            container.background = ContextCompat.getDrawable(this, R.drawable.bg_icon_option_selected)
            image.setColorFilter(ContextCompat.getColor(this, R.color.levit_purple))
        } else {
            container.background = ContextCompat.getDrawable(this, R.drawable.bg_icon_option_unselected)
            image.setColorFilter(ContextCompat.getColor(this, R.color.levit_hint))
        }
    }

    // Mesmas regras do backend: nome, pelo menos 1 campo com nome,
    // e campo de seleção com pelo menos 1 opção.
    private fun formularioValido(): Boolean {
        if (binding.etNomeModulo.text.isNullOrBlank()) return false
        if (linhasCampos.isEmpty()) return false
        return linhasCampos.all { linha ->
            linha.nome.isNotEmpty() &&
                    (linha.tipo != TipoCampo.SELECAO_UNICA || linha.opcoes.isNotEmpty())
        }
    }

    private fun atualizarBotaoCriar() = setCriarModuloEnabled(formularioValido())

    private fun setCriarModuloEnabled(enabled: Boolean) {
        binding.btnCriarModulo.isEnabled = enabled
        binding.btnCriarModulo.setBackgroundResource(
            if (enabled) R.drawable.bg_button_primary else R.drawable.bg_button_disabled
        )
    }

    private fun mostrarSeletorDeTipoDeCampo() {
        val tipos = TipoCampo.entries.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Tipo do campo")
            .setItems(tipos.map { it.label }.toTypedArray()) { _, which ->
                adicionarCampo(tipos[which])
            }
            .show()
    }

    private fun adicionarCampo(tipo: TipoCampo) {
        val row = layoutInflater.inflate(R.layout.item_campo_modulo, binding.llCamposContainer, false)
        val linha = LinhaCampo(row, tipo)

        row.findViewById<ImageView>(R.id.ivTipoCampo).setImageResource(tipo.iconRes)
        row.findViewById<TextView>(R.id.tvTipoCampoBadge).text = tipo.label

        val etOpcoes = row.findViewById<EditText>(R.id.etOpcoes)
        if (tipo == TipoCampo.SELECAO_UNICA) etOpcoes.visibility = View.VISIBLE

        row.findViewById<EditText>(R.id.etNomeCampo).observar { atualizarBotaoCriar() }
        etOpcoes.observar { atualizarBotaoCriar() }

        row.findViewById<ImageView>(R.id.btnRemoverCampo).setOnClickListener {
            binding.llCamposContainer.removeView(row)
            linhasCampos.remove(linha)
            atualizarEstadoVazio()
            atualizarBotaoCriar()
        }

        linhasCampos.add(linha)
        binding.llCamposContainer.addView(row)
        atualizarEstadoVazio()
        atualizarBotaoCriar()
    }

    private fun atualizarEstadoVazio() {
        binding.tvCamposVazio.visibility =
            if (linhasCampos.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun criarModulo() {
        if (!formularioValido()) return

        val request = CriarModuloRequest(
            nome = binding.etNomeModulo.text.toString().trim(),
            icone = iconeSelecionado,
            campos = linhasCampos.map { linha ->
                CampoRequest(
                    nome = linha.nome,
                    tipo = linha.tipo.api,
                    opcoes = if (linha.tipo == TipoCampo.SELECAO_UNICA) linha.opcoes else null
                )
            }
        )

        setCriarModuloEnabled(false)
        binding.btnCancelar.isEnabled = false

        lifecycleScope.launch {
            when (val resultado = moduloRepository.criar(request)) {
                is ResultadoModulo.Sucesso -> {
                    Toast.makeText(
                        this@NovoModuloActivity,
                        R.string.modulo_criado_sucesso,
                        Toast.LENGTH_SHORT
                    ).show()
                    finish()
                }
                is ResultadoModulo.Erro -> {
                    if (resultado.sessaoExpirada) {
                        irParaLogin()
                    } else {
                        Toast.makeText(
                            this@NovoModuloActivity,
                            resultado.mensagem,
                            Toast.LENGTH_LONG
                        ).show()
                        binding.btnCancelar.isEnabled = true
                        atualizarBotaoCriar()
                    }
                }
            }
        }
    }

    private fun irParaLogin() {
        val intent = Intent(this, LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
    }
}