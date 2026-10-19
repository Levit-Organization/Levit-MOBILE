package com.example.levit

import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import com.example.levit.data.remote.dto.Modulo

fun iniciais(nome: String?): String {
    val partes = nome?.trim()?.split(Regex("\\s+"))?.filter { it.isNotEmpty() }.orEmpty()
    return when {
        partes.isEmpty() -> "?"
        partes.size == 1 -> partes[0].take(1).uppercase()
        else -> (partes.first().take(1) + partes.last().take(1)).uppercase()
    }
}

object ModuloUi {

    // Nomes iguais aos usados no LEVIT Web (Material Icons).
    fun iconeDrawable(icone: String?): Int = when (icone) {
        "person" -> R.drawable.ic_person
        "work", "business" -> R.drawable.ic_briefcase
        "description", "article" -> R.drawable.ic_description
        "event" -> R.drawable.ic_schedule
        "folder" -> R.drawable.ic_image
        "inventory_2", "shopping_cart", "local_shipping" -> R.drawable.ic_inventory
        "group" -> R.drawable.ic_group
        "account_balance" -> R.drawable.ic_payments
        else -> R.drawable.ic_grid_view
    }

    fun textoRegistros(context: Context, total: Int): String =
        if (total == 1) context.getString(R.string.modulo_registros_um)
        else context.getString(R.string.modulo_registros_outros, total)

    fun preencherCards(
        inflater: LayoutInflater,
        container: ViewGroup,
        modulos: List<Modulo>
    ) {
        container.removeAllViews()
        modulos.forEach { modulo ->
            val card = inflater.inflate(R.layout.item_modulo, container, false)
            card.findViewById<ImageView>(R.id.ivIconeModulo)
                .setImageResource(iconeDrawable(modulo.icone))
            card.findViewById<TextView>(R.id.tvNomeModulo).text = modulo.nome

            card.findViewById<TextView>(R.id.tvSubtituloModulo).text =
                textoRegistros(container.context, modulo.totalRegistros)

            container.addView(card)
        }
    }
}