package com.example.levit.ui

import android.content.Context
import android.graphics.Typeface
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextPaint
import android.text.style.MetricAffectingSpan
import androidx.core.content.res.ResourcesCompat
import androidx.fragment.app.Fragment
import com.example.levit.R
import com.example.levit.data.repository.TipoErro
import com.google.android.material.snackbar.Snackbar

/** Aplica a fonte Material Icons só ao trecho marcado, para a ligatura virar ícone. */
private class FonteIconeSpan(private val fonte: Typeface) : MetricAffectingSpan() {
    override fun updateDrawState(paint: TextPaint) { paint.typeface = fonte }
    override fun updateMeasureState(paint: TextPaint) { paint.typeface = fonte }
}

/** "ícone + texto" em um único CharSequence; sem a fonte, devolve só o texto. */
fun Context.comIcone(ligatura: String, texto: CharSequence): CharSequence {
    val fonte = ResourcesCompat.getFont(this, R.font.material_icons) ?: return texto
    return SpannableStringBuilder()
        .append(ligatura, FonteIconeSpan(fonte), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        .append("  ")
        .append(texto)
}

/** Mostra a falha numa Snackbar, com o ícone do tipo de erro quando houver. */
fun Fragment.mostrarErro(mensagem: String, tipo: TipoErro?) {
    val texto = tipo?.let { requireContext().comIcone(getString(it.icone), mensagem) } ?: mensagem
    Snackbar.make(requireView(), texto, Snackbar.LENGTH_LONG).setTextMaxLines(4).show()
}
