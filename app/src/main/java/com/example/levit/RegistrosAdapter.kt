package com.example.levit

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.levit.data.remote.dto.CampoModulo
import com.example.levit.data.remote.dto.Registro
import com.example.levit.data.remote.dto.formatarValorRegistro
import com.example.levit.databinding.ItemRegistroBinding
import com.example.levit.databinding.ItemRegistroCampoBinding

class RegistrosAdapter(
    private val aoEditar: (Registro) -> Unit,
    private val aoExcluir: (Registro) -> Unit
) : ListAdapter<Registro, RegistrosAdapter.RegistroViewHolder>(Diff) {

    /** Campos do módulo, em ordem. Defina antes de mandar a lista. */
    var campos: List<CampoModulo> = emptyList()

    inner class RegistroViewHolder(val b: ItemRegistroBinding) : RecyclerView.ViewHolder(b.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        RegistroViewHolder(ItemRegistroBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: RegistroViewHolder, position: Int) {
        val registro = getItem(position)
        val ctx = holder.b.root.context
        val valores = registro.valores

        // Só os campos preenchidos. O primeiro vira título, os 2 seguintes viram linhas.
        val preenchidos = campos.mapNotNull { campo ->
            val texto = formatarCampo(campo, valores[campo.id])
            if (texto.isEmpty()) null else campo to texto
        }

        holder.b.tvTituloRegistro.text =
            preenchidos.firstOrNull()?.second ?: ctx.getString(R.string.registros_sem_dados)

        val container = holder.b.containerCampos
        container.removeAllViews()
        preenchidos.drop(1).take(2).forEach { (campo, texto) ->
            val linha = ItemRegistroCampoBinding.inflate(LayoutInflater.from(ctx), container, false)
            linha.tvRotuloCampo.text = ctx.getString(R.string.registros_rotulo, campo.nome)
            linha.tvValorCampo.text = texto
            container.addView(linha.root)
        }
        container.visibility = if (container.childCount == 0) View.GONE else View.VISIBLE

        val data = registro.criadoEm?.let { dataBr(it) }
        holder.b.tvDataRegistro.visibility = if (data == null) View.GONE else View.VISIBLE
        holder.b.tvDataRegistro.text = data?.let { ctx.getString(R.string.registros_criado_em, it) }

        holder.b.btnEditarRegistro.setOnClickListener { aoEditar(registro) }
        holder.b.btnExcluirRegistro.setOnClickListener { aoExcluir(registro) }
    }

    private fun formatarCampo(campo: CampoModulo, valor: Any?): String {
        val texto = formatarValorRegistro(valor)
        return if (campo.tipo == "data") dataBr(texto) else texto
    }

    // "2026-05-20" ou "2026-05-20 10:30:00" vira "20/05/2026". Outro formato volta igual.
    private fun dataBr(texto: String): String =
        Regex("^(\\d{4})-(\\d{2})-(\\d{2})").find(texto)
            ?.destructured?.let { (ano, mes, dia) -> "$dia/$mes/$ano" }
            ?: texto

    private object Diff : DiffUtil.ItemCallback<Registro>() {
        override fun areItemsTheSame(a: Registro, b: Registro) = a.id == b.id
        override fun areContentsTheSame(a: Registro, b: Registro) = a == b
    }
}