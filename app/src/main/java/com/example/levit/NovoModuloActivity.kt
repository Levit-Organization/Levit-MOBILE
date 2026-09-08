package com.example.levit

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.levit.databinding.ActivityNovoModuloBinding

class NovoModuloActivity : AppCompatActivity() {

    private enum class TipoCampo(val label: String, val iconRes: Int) {
        TEXTO("Texto", R.drawable.ic_text_fields),
        NUMERO("Número", R.drawable.ic_numbers),
        DATA("Data", R.drawable.ic_calendar),
        SELECAO_UNICA("Seleção única", R.drawable.ic_radio_button_checked),
    }

    private lateinit var binding: ActivityNovoModuloBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityNovoModuloBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val iconOptions = listOf(
            binding.iconOptionGrid to binding.iconImageGrid,
            binding.iconOptionPerson to binding.iconImagePerson,
            binding.iconOptionBriefcase to binding.iconImageBriefcase,
            binding.iconOptionDocument to binding.iconImageDocument,
            binding.iconOptionSchedule to binding.iconImageSchedule,
            binding.iconOptionImage to binding.iconImageImage,
        )

        fun selectIcon(selected: FrameLayout) {
            iconOptions.forEach { (container, image) ->
                setIconSelected(container, image, container == selected)
            }
        }

        iconOptions.forEach { (container, _) ->
            container.setOnClickListener { selectIcon(container) }
        }

        binding.etNomeModulo.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                setCriarModuloEnabled(!s.isNullOrBlank())
            }
        })

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
            Toast.makeText(this, "Criação de módulo em desenvolvimento", Toast.LENGTH_SHORT).show()
        }
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

        row.findViewById<ImageView>(R.id.ivTipoCampo).setImageResource(tipo.iconRes)
        row.findViewById<TextView>(R.id.tvTipoCampoBadge).text = tipo.label
        row.findViewById<ImageView>(R.id.btnRemoverCampo).setOnClickListener {
            binding.llCamposContainer.removeView(row)
            atualizarEstadoVazio()
        }

        binding.llCamposContainer.addView(row)
        atualizarEstadoVazio()
    }

    private fun atualizarEstadoVazio() {
        binding.tvCamposVazio.visibility =
            if (binding.llCamposContainer.childCount > 0) View.GONE else View.VISIBLE
    }
}
