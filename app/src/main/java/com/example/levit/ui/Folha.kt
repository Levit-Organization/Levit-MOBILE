package com.example.levit.ui

import android.content.Context
import android.view.View
import android.view.WindowManager
import androidx.activity.addCallback
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog

/**
 * Drawer/Modal do web no celular: uma folha que sobe de baixo. Com algo
 * digitado ([sujo]), fechar pergunta antes de descartar, como o "dirty" do web.
 */
class Folha(private val context: Context, conteudo: View, private val sujo: () -> Boolean = { false }) {

    private val dialogo = BottomSheetDialog(context).apply {
        setContentView(conteudo)
        setCancelable(false)
        setCanceledOnTouchOutside(false)
        behavior.state = BottomSheetBehavior.STATE_EXPANDED
        behavior.skipCollapsed = true
        // Arrastar para baixo também fecha: só se não houver nada a perder.
        behavior.isDraggable = false
        window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        onBackPressedDispatcher.addCallback(this) { fechar() }
    }

    fun abrir(): Folha = apply { dialogo.show() }

    /** Fecha perguntando se há algo digitado. */
    fun fechar() {
        if (!sujo()) {
            dialogo.dismiss()
            return
        }
        context.confirmar(
            titulo = "Descartar o que foi preenchido?",
            mensagem = "O que você digitou aqui vai ser perdido.",
            rotuloConfirmar = "Descartar",
            rotuloCancelar = "Continuar editando"
        ) { dialogo.dismiss() }
    }

    /** Fecha sem perguntar (depois de salvar). */
    fun dispensar() = dialogo.dismiss()

    fun aoFechar(acao: () -> Unit) = apply { dialogo.setOnDismissListener { acao() } }
}
