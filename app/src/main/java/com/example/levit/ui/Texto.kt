package com.example.levit.ui

import android.content.Context
import androidx.annotation.StringRes

/**
 * Texto de tela ainda não resolvido: o id da string em strings.xml mais os argumentos do formato.
 * Deixa as regras em Kotlin puro (testáveis na JVM) sem texto fixo: quem tem Context chama [em].
 */
class Texto(@StringRes val id: Int, vararg val args: Any) {

    fun em(context: Context): String = context.getString(id, *args)

    override fun equals(other: Any?) = other is Texto && id == other.id && args.contentEquals(other.args)

    override fun hashCode() = 31 * id + args.contentHashCode()

    override fun toString() = "Texto($id, ${args.joinToString()})"
}
