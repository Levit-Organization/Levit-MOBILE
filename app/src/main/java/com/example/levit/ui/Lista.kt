package com.example.levit.ui

import android.annotation.SuppressLint
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import androidx.viewbinding.ViewBinding
import com.example.levit.R

/*
 * Listas que crescem (itens, candidatos, lançamentos, colaboradores...) usam
 * RecyclerView: só as linhas visíveis existem na tela. Cada linha vem de um
 * layout XML (ViewBinding); quem usa diz como criar e como preencher.
 */

/** Uma linha: chave estável (para o DiffUtil) e o tipo de layout. Use data class. */
interface LinhaDeLista {
    val chave: String
    val tipo: Int
}

class SuporteDeLinha(val binding: ViewBinding) : RecyclerView.ViewHolder(binding.root)

/** ListAdapter genérico: compara pela chave e pelo conteúdo (equals da data class). */
class AdaptadorDeLinhas<T : LinhaDeLista>(
    private val criar: (pai: ViewGroup, tipo: Int) -> ViewBinding,
    private val ligar: (binding: ViewBinding, linha: T) -> Unit
) : ListAdapter<T, SuporteDeLinha>(object : DiffUtil.ItemCallback<T>() {
    override fun areItemsTheSame(a: T, b: T) = a.chave == b.chave && a.tipo == b.tipo
    @SuppressLint("DiffUtilEquals")
    override fun areContentsTheSame(a: T, b: T) = a == b
}) {
    override fun getItemViewType(position: Int) = getItem(position).tipo
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = SuporteDeLinha(criar(parent, viewType))
    override fun onBindViewHolder(holder: SuporteDeLinha, position: Int) = ligar(holder.binding, getItem(position))
}

/** Um bloco fixo no topo da lista (cabeçalho, filtros): criado uma vez e nunca reciclado. */
class CabecalhoFixo(private val view: View) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    override fun getItemCount() = 1
    override fun getItemViewType(position: Int) = R.id.cabecalho_fixo
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        object : RecyclerView.ViewHolder(view.also { (it.parent as? ViewGroup)?.removeView(it) }) {}.apply { setIsRecyclable(false) }
    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) = Unit
}

/**
 * Linhas que juntas formam um cartão (cabeçalho do mês + lançamentos): o fundo
 * de cada uma é o pedaço certo do bg_card, e a última ganha a margem de baixo.
 */
fun View.pedacoDeCartao(primeiro: Boolean, ultimo: Boolean, margemDepois: Int = 8) {
    setBackgroundResource(when {
        primeiro && ultimo -> R.drawable.bg_card
        primeiro -> R.drawable.bg_card_topo
        ultimo -> R.drawable.bg_card_base
        else -> R.drawable.bg_card_meio
    })
    (layoutParams as? ViewGroup.MarginLayoutParams)?.let {
        it.bottomMargin = if (ultimo) context.dp(margemDepois) else 0
        layoutParams = it
    }
}

/** Linha de um layout sem ids (uma única view): a própria view vira o ViewBinding. */
fun ligacaoDe(view: View) = ViewBinding { view }
