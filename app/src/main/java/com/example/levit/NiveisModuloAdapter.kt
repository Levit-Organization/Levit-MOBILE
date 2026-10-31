package com.example.levit

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ArrayAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.levit.data.remote.dto.Modulo
import com.example.levit.databinding.ItemNivelModuloBinding

/**
 * Um card por módulo, com o menu de nível. [niveis] é o mapa vivo do fragment
 * (moduloId -> nível, ou null = sem acesso), por isso rebind mostra o valor atual.
 */
class NiveisModuloAdapter(
    private val modulos: List<Modulo>,
    private val niveis: Map<String, String?>,
    private val aoMudar: (moduloId: String, nivel: String?) -> Unit
) : RecyclerView.Adapter<NiveisModuloAdapter.NivelViewHolder>() {

    // Mesma ordem dos rótulos abaixo. null = sem acesso (no backend vira DELETE).
    private val codigos = listOf<String?>(null, "visualizar", "editar", "gerenciar")
    private val rotulosRes = listOf(
        R.string.cargo_nivel_nenhum, R.string.cargo_nivel_visualizar,
        R.string.cargo_nivel_editar, R.string.cargo_nivel_gerenciar
    )

    inner class NivelViewHolder(val b: ItemNivelModuloBinding) : RecyclerView.ViewHolder(b.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        NivelViewHolder(ItemNivelModuloBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun getItemCount() = modulos.size

    override fun onBindViewHolder(holder: NivelViewHolder, position: Int) {
        val modulo = modulos[position]
        val ctx = holder.b.root.context
        val rotulos = rotulosRes.map { ctx.getString(it) }

        holder.b.tvNomeModuloNivel.text = modulo.nome
        holder.b.actNivel.setAdapter(ArrayAdapter(ctx, android.R.layout.simple_list_item_1, rotulos))
        val indice = codigos.indexOf(niveis[modulo.id]).coerceAtLeast(0)
        holder.b.actNivel.setText(rotulos[indice], false)
        holder.b.actNivel.setOnItemClickListener { _, _, pos, _ -> aoMudar(modulo.id, codigos[pos]) }
    }
}