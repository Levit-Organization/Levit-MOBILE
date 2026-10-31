package com.example.levit

import android.content.Context
import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.levit.data.remote.dto.Cargo
import com.example.levit.databinding.ItemCargoBinding

data class CargoItem(val cargo: Cargo, val qtdMembros: Int)

class CargosAdapter(
    private val aoEditar: (Cargo) -> Unit
) : ListAdapter<CargoItem, CargosAdapter.CargoViewHolder>(Diff) {

    inner class CargoViewHolder(val b: ItemCargoBinding) : RecyclerView.ViewHolder(b.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CargoViewHolder =
        CargoViewHolder(ItemCargoBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: CargoViewHolder, position: Int) {
        val item = getItem(position)
        val cargo = item.cargo
        val ctx = holder.b.root.context

        holder.b.tvNomeCargo.text = cargo.nome
        holder.b.tvQtdMembros.text =
            ctx.resources.getQuantityString(R.plurals.equipe_membros_qtd, item.qtdMembros, item.qtdMembros)
        holder.b.btnEditarCargo.setOnClickListener { aoEditar(cargo) }

        val chips = holder.b.chipsPermissoes
        chips.removeAllViews()
        if (cargo.acessoTotal) {
            // Acesso total já cobre tudo: mostra só essa badge.
            chips.addView(
                criarBadge(ctx, ctx.getString(R.string.equipe_acesso_total), R.color.chip_purple_bg, R.color.levit_purple)
            )
        } else if (cargo.permissoesOuVazio.isEmpty()) {
            chips.addView(
                criarBadge(ctx, ctx.getString(R.string.equipe_sem_permissoes), R.color.levit_badge_bg, R.color.levit_text_secondary)
            )
        } else {
            cargo.permissoesOuVazio.forEach { codigo ->
                chips.addView(criarBadge(ctx, rotulo(ctx, codigo), R.color.levit_badge_bg, R.color.levit_text_secondary))
            }
        }
    }

    private fun rotulo(ctx: Context, codigo: String): String = when (codigo) {
        "criar_modulos" -> ctx.getString(R.string.equipe_perm_criar_modulos)
        "visualizar_equipe" -> ctx.getString(R.string.equipe_perm_visualizar_equipe)
        "gerenciar_equipe" -> ctx.getString(R.string.equipe_perm_gerenciar_equipe)
        "gerenciar_cargos" -> ctx.getString(R.string.equipe_perm_gerenciar_cargos)
        "exportar_dados" -> ctx.getString(R.string.equipe_perm_exportar_dados)
        "resetar_dados" -> ctx.getString(R.string.equipe_perm_resetar_dados)
        "ver_relatorios" -> ctx.getString(R.string.equipe_perm_ver_relatorios)
        else -> codigo
    }

    private fun criarBadge(ctx: Context, texto: String, fundo: Int, corTexto: Int): TextView {
        val d = ctx.resources.displayMetrics.density
        return TextView(ctx).apply {
            text = texto
            textSize = 12f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setTextColor(ContextCompat.getColor(ctx, corTexto))
            setBackgroundResource(R.drawable.bg_badge_pill)
            backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(ctx, fundo))
            setPadding((12 * d).toInt(), (4 * d).toInt(), (12 * d).toInt(), (4 * d).toInt())
        }
    }

    private object Diff : DiffUtil.ItemCallback<CargoItem>() {
        override fun areItemsTheSame(a: CargoItem, b: CargoItem) = a.cargo.id == b.cargo.id
        override fun areContentsTheSame(a: CargoItem, b: CargoItem) = a == b
    }
}