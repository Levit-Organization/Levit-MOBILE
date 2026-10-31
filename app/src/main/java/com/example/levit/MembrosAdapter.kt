package com.example.levit

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.levit.data.remote.dto.Membro
import com.example.levit.databinding.ItemMembroBinding

class MembrosAdapter(
    private val aoEditar: (Membro) -> Unit,
    private val aoRemover: (Membro) -> Unit
) : ListAdapter<Membro, MembrosAdapter.MembroViewHolder>(Diff) {

    private val coresAvatar = intArrayOf(
        R.color.avatar_teal, R.color.avatar_orange, R.color.avatar_blue,
        R.color.avatar_pink, R.color.avatar_green
    )

    inner class MembroViewHolder(val b: ItemMembroBinding) : RecyclerView.ViewHolder(b.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MembroViewHolder =
        MembroViewHolder(ItemMembroBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: MembroViewHolder, position: Int) {
        val membro = getItem(position)
        val ctx = holder.b.root.context

        holder.b.tvAvatarMembro.text = iniciais(membro.nome)
        val cor = coresAvatar[Math.floorMod(membro.id.hashCode(), coresAvatar.size)]
        holder.b.tvAvatarMembro.backgroundTintList =
            ColorStateList.valueOf(ContextCompat.getColor(ctx, cor))

        holder.b.tvNomeMembro.text = membro.nome
        holder.b.tvEmailMembro.text = membro.email
        holder.b.tvCargoMembro.text = membro.cargoNome ?: ctx.getString(R.string.equipe_sem_cargo)

        // Status: nulo ou "ativo" = Ativo. "pendente" = convite ainda não aceito.
        val pendente = membro.status.equals("pendente", ignoreCase = true)
        holder.b.tvStatusMembro.setText(
            if (pendente) R.string.equipe_status_pendente else R.string.equipe_status_ativo
        )
        holder.b.tvStatusMembro.setTextColor(
            ContextCompat.getColor(ctx, if (pendente) R.color.status_pendente_text else R.color.status_ativo_text)
        )
        holder.b.tvStatusMembro.backgroundTintList = ColorStateList.valueOf(
            ContextCompat.getColor(ctx, if (pendente) R.color.status_pendente_bg else R.color.status_ativo_bg)
        )

        holder.b.btnEditarMembro.setOnClickListener { aoEditar(membro) }
        holder.b.btnRemoverMembro.setOnClickListener { aoRemover(membro) }
    }

    private object Diff : DiffUtil.ItemCallback<Membro>() {
        override fun areItemsTheSame(a: Membro, b: Membro) = a.id == b.id
        override fun areContentsTheSame(a: Membro, b: Membro) = a == b
    }
}