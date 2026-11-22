package com.example.levit

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.levit.data.remote.dto.Convite
import com.example.levit.data.remote.dto.Membro
import com.example.levit.databinding.ItemMembroBinding

/** Uma linha da aba Membros: colaborador ativo ou convite ainda não aceito. */
sealed class LinhaEquipe {
    abstract val chave: String
    data class Colaborador(val membro: Membro) : LinhaEquipe() {
        override val chave = "m-${membro.id}"
    }
    data class ConvitePendente(val convite: Convite) : LinhaEquipe() {
        override val chave = "c-${convite.id}"
    }
}

class MembrosAdapter(
    private val aoEditar: (Membro) -> Unit,
    private val aoRemover: (Membro) -> Unit,
    private val aoReenviar: (Convite) -> Unit,
    private val aoCancelar: (Convite) -> Unit
) : ListAdapter<LinhaEquipe, MembrosAdapter.MembroViewHolder>(Diff) {

    private val coresAvatar = intArrayOf(
        R.color.avatar_teal, R.color.avatar_orange, R.color.avatar_blue,
        R.color.avatar_pink, R.color.avatar_green
    )

    inner class MembroViewHolder(val b: ItemMembroBinding) : RecyclerView.ViewHolder(b.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MembroViewHolder =
        MembroViewHolder(ItemMembroBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: MembroViewHolder, position: Int) {
        val ctx = holder.b.root.context
        when (val linha = getItem(position)) {
            is LinhaEquipe.Colaborador -> {
                val membro = linha.membro
                holder.b.tvAvatarMembro.text = iniciais(membro.nome)
                pintarAvatar(holder, membro.id)
                holder.b.tvNomeMembro.text = membro.nome
                holder.b.tvEmailMembro.text = membro.email
                holder.b.tvCargoMembro.text = membro.cargoNome ?: ctx.getString(R.string.equipe_sem_cargo)
                pintarStatus(holder, R.string.equipe_status_ativo, R.color.status_ativo_text, R.color.status_ativo_bg)

                holder.b.btnEditarMembro.setImageResource(R.drawable.ic_edit)
                holder.b.btnEditarMembro.contentDescription = ctx.getString(R.string.equipe_dialogo_editar_titulo)
                holder.b.btnRemoverMembro.contentDescription = ctx.getString(R.string.equipe_remover_titulo)
                holder.b.btnEditarMembro.setOnClickListener { aoEditar(membro) }
                holder.b.btnRemoverMembro.setOnClickListener { aoRemover(membro) }
            }
            is LinhaEquipe.ConvitePendente -> {
                val convite = linha.convite
                holder.b.tvAvatarMembro.text = iniciais(convite.email)
                pintarAvatar(holder, convite.id)
                holder.b.tvNomeMembro.text = convite.email
                holder.b.tvEmailMembro.text = convite.convidadoPorNome
                    ?.let { ctx.getString(R.string.equipe_convite_por, it) }
                    ?: ctx.getString(R.string.equipe_convite_sem_autor)
                holder.b.tvCargoMembro.text = convite.cargoNome ?: ctx.getString(R.string.equipe_sem_cargo)
                if (convite.expirado == true) {
                    pintarStatus(holder, R.string.equipe_status_expirado, R.color.danger, R.color.danger_bg)
                } else {
                    pintarStatus(holder, R.string.equipe_status_pendente, R.color.status_pendente_text, R.color.status_pendente_bg)
                }

                holder.b.btnEditarMembro.setImageResource(R.drawable.ic_refresh)
                holder.b.btnEditarMembro.contentDescription = ctx.getString(R.string.equipe_reenviar)
                holder.b.btnRemoverMembro.contentDescription = ctx.getString(R.string.equipe_cancelar_convite)
                holder.b.btnEditarMembro.setOnClickListener { aoReenviar(convite) }
                holder.b.btnRemoverMembro.setOnClickListener { aoCancelar(convite) }
            }
        }
    }

    private fun pintarAvatar(holder: MembroViewHolder, semente: String) {
        val cor = coresAvatar[Math.floorMod(semente.hashCode(), coresAvatar.size)]
        holder.b.tvAvatarMembro.backgroundTintList =
            ColorStateList.valueOf(ContextCompat.getColor(holder.b.root.context, cor))
    }

    private fun pintarStatus(holder: MembroViewHolder, texto: Int, corTexto: Int, corFundo: Int) {
        val ctx = holder.b.root.context
        holder.b.tvStatusMembro.setText(texto)
        holder.b.tvStatusMembro.setTextColor(ContextCompat.getColor(ctx, corTexto))
        holder.b.tvStatusMembro.backgroundTintList =
            ColorStateList.valueOf(ContextCompat.getColor(ctx, corFundo))
    }

    private object Diff : DiffUtil.ItemCallback<LinhaEquipe>() {
        override fun areItemsTheSame(a: LinhaEquipe, b: LinhaEquipe) = a.chave == b.chave
        override fun areContentsTheSame(a: LinhaEquipe, b: LinhaEquipe) = a == b
    }
}