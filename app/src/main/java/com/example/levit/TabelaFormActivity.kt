package com.example.levit

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import com.example.levit.data.remote.ApiClient
import com.example.levit.data.remote.dto.AtualizarTabelaRequest
import com.example.levit.data.remote.dto.Modulo
import com.example.levit.data.remote.dto.ReordenarRequest
import com.example.levit.data.repository.ModuloRepository
import com.example.levit.data.repository.Resultado
import com.example.levit.data.repository.ResultadoModulo
import com.example.levit.data.repository.chamarApi
import com.example.levit.data.repository.executarApi
import com.example.levit.databinding.ActivityTabelaFormBinding
import com.example.levit.databinding.ItemColunaBinding
import com.example.levit.databinding.ItemFaseBinding
import com.example.levit.databinding.ItemModeloTabelaBinding
import com.example.levit.ui.divisoria
import com.example.levit.ui.inflarFilho
import com.example.levit.databinding.ItemOpcaoColunaBinding
import com.example.levit.ui.ModeloTabela
import com.example.levit.ui.Tabelas
import com.example.levit.ui.avisar
import com.example.levit.ui.caixaDeIcone
import com.example.levit.ui.configurarSelect
import com.example.levit.ui.confirmar
import com.example.levit.ui.mostrarEstado
import com.example.levit.ui.selo
import kotlinx.coroutines.launch

/**
 * Criar ou editar uma tabela (ModuleForm.jsx): modelos prontos, tipo, ícone,
 * etapas (vaga nova) e colunas com os 8 tipos do web.
 */
class TabelaFormActivity : HamburgerMenuBaseActivity() {

    /** Uma coluna no formulário. nova = ainda não existe no servidor. */
    private class ColunaEdit(
        val id: String?,
        var nome: String,
        var tipo: String,
        var opcoes: MutableList<String>,
        var multiplo: Boolean,
        var vinculadaId: String,
        val vinculadaNome: String?,
        val nova: Boolean,
        val chave: Int
    )

    private lateinit var binding: ActivityTabelaFormBinding
    private val api by lazy { ApiClient.moduloApi(applicationContext) }
    private val moduloRepository by lazy { ModuloRepository(applicationContext) }

    private val tabelaId: String? by lazy { intent.getStringExtra(EXTRA_ID) }
    private val editando get() = tabelaId != null

    private var nome = ""
    private var icone = "extension"
    private var tipo = "dados"
    private val colunas = mutableListOf<ColunaEdit>()
    private val originais = mutableMapOf<String, Pair<String, List<String>>>()
    private val fases = mutableListOf("")
    private var tabelasDeDados: List<Modulo> = emptyList()
    private var gradeAberta = false
    private var salvando = false
    private var proximaChave = 0

    // Modelo: trocar de modelo sem ter editado nada não pede confirmação.
    private var modeloAplicado: String? = null
    private var fotoDoModelo: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTabelaFormBinding.inflate(layoutInflater)
        setContentViewWithHamburgerMenu(binding.root)
        if (!menuPronto()) return

        binding.btnVoltar.setOnClickListener { finish() }
        binding.btnCancelar.setOnClickListener { finish() }
        binding.tvTitulo.setText(if (editando) R.string.tab_editar_titulo else R.string.tab_nova_titulo)
        binding.etNome.doAfterTextChanged { nome = it?.toString().orEmpty(); binding.campoNome.error = null }
        binding.btnAlterarIcone.setOnClickListener { gradeAberta = !gradeAberta; mostrarIcones() }
        binding.btnAddColuna.setOnClickListener { adicionarColuna() }
        binding.btnAddFase.setOnClickListener { fases += ""; mostrarFases() }
        binding.btnSalvar.setOnClickListener { salvar() }

        binding.llModelos.isVisible = !editando
        if (!editando) montarModelos()

        // Tabelas que uma coluna de ligação pode apontar (só as de lista).
        lifecycleScope.launch {
            (moduloRepository.listar() as? ResultadoModulo.Sucesso)?.let { r ->
                tabelasDeDados = r.dados.filter { (it.tipo ?: "dados") == "dados" }
                mostrarColunas()
            }
        }
        if (editando) carregarTabela() else {
            // Vindo de Recrutamento: já começa pelo modelo de vaga (etapas prontas).
            Tabelas.MODELOS.firstOrNull { it.id == intent.getStringExtra(EXTRA_MODELO) }?.let { aplicarModeloAgora(it) } ?: atualizarTudo()
        }
    }

    private fun carregarTabela() {
        binding.btnSalvar.isEnabled = false
        lifecycleScope.launch {
            when (val r = chamarApi(getString(R.string.tab_erro_abrir)) { api.detalhes(tabelaId!!) }) {
                is Resultado.Sucesso -> {
                    val t = r.dados
                    nome = t.nome.orEmpty()
                    icone = t.icone ?: "extension"
                    tipo = t.tipo ?: "dados"
                    binding.etNome.setText(nome)
                    colunas.clear()
                    t.campos.orEmpty().forEach { c ->
                        colunas += ColunaEdit(
                            id = c.id, nome = c.nome.orEmpty(), tipo = c.tipo ?: "texto",
                            opcoes = c.listaDeOpcoes.toMutableList(), multiplo = c.multiplo,
                            vinculadaId = c.moduloVinculadoId.orEmpty(), vinculadaNome = c.moduloVinculado?.nome,
                            nova = false, chave = proximaChave++
                        )
                        c.id?.let { originais[it] = c.nome.orEmpty() to c.listaDeOpcoes }
                    }
                }
                is Resultado.Erro -> {
                    if (r.sessaoExpirada) return@launch redirecionarParaLogin()
                    mostrarErro(r.mensagem)
                }
            }
            atualizarTudo()
        }
    }

    private fun atualizarTudo() {
        binding.flIcone.removeAllViews()
        binding.flIcone.addView(caixaDeIcone(icone, ladoDp = 40))
        binding.acTipo.configurarSelect(Tabelas.TIPOS.map { it.valor to it.rotulo }, tipo) { novo ->
            tipo = novo
            atualizarTudo()
        }
        binding.campoTipo.isEnabled = !editando
        binding.campoTipo.helperText = (Tabelas.TIPOS.firstOrNull { it.valor == tipo } ?: Tabelas.TIPOS.first()).descricao
        mostrarIcones()
        mostrarFases()
        binding.tvInfoRecrutamento.isVisible = editando && tipo == "recrutamento"

        val semColunas = tipo == "financeiro" || tipo == "arquivo"
        binding.cartaoColunas.isVisible = !semColunas
        binding.cartaoSemColunas.isVisible = semColunas
        if (semColunas) {
            val financeiro = tipo == "financeiro"
            binding.tvSemColunasIcone.text = if (financeiro) "account_balance_wallet" else "folder_open"
            binding.tvSemColunasTitulo.setText(if (financeiro) R.string.tab_financeira else R.string.tab_arquivos)
            binding.tvSemColunasDesc.setText(if (financeiro) R.string.tab_financeira_desc else R.string.tab_arquivos_desc)
        }
        mostrarColunas()
        atualizarBotaoSalvar()
    }

    private val precisaDeFases get() = !editando && tipo == "recrutamento"
    private val fasesValidas get() = fases.map { it.trim() }.filter { it.isNotEmpty() }

    private fun atualizarBotaoSalvar() {
        binding.btnSalvar.isEnabled = !salvando && (!precisaDeFases || fasesValidas.isNotEmpty())
        binding.btnSalvar.setText(if (salvando) R.string.tab_salvando else R.string.tab_salvar)
    }

    private fun mostrarErro(mensagem: String?) {
        binding.tvErro.text = mensagem
        binding.tvErro.isVisible = !mensagem.isNullOrBlank()
    }

    // --- Modelos ------------------------------------------------------------------

    private fun fotoAtual() = listOf(nome, colunas.map { listOf(it.nome, it.tipo, it.opcoes) }).toString()

    private fun montarModelos() {
        val destino = binding.llGruposModelos
        destino.removeAllViews()
        Tabelas.GRUPOS_DE_MODELOS.forEach { (grupo, modelos) ->
            destino.addView(destino.inflarFilho<TextView>(R.layout.item_grupo_modelo).apply { text = grupo })
            modelos.forEach { modelo -> destino.addView(cartaoDoModelo(modelo)) }
        }
    }

    private fun cartaoDoModelo(modelo: ModeloTabela): View {
        val ativo = modeloAplicado == modelo.id
        val b = ItemModeloTabelaBinding.inflate(layoutInflater, binding.llGruposModelos, false)
        val cartao = b.root
        if (ativo) cartao.setBackgroundResource(R.drawable.bg_card_primario)
        cartao.contentDescription = modelo.titulo
        cartao.setOnClickListener { aplicarModelo(modelo) }
        b.linha.addView(caixaDeIcone(modelo.icone, ladoDp = 36, ativo = ativo), 0)
        b.tvTitulo.text = modelo.titulo
        b.tvDescricao.text = modelo.descricao
        return cartao
    }

    /** Preenche nome, ícone, tipo e colunas a partir do modelo (tudo editável depois). */
    private fun aplicarModelo(modelo: ModeloTabela) {
        val temConteudo = nome.isNotBlank() || colunas.any { it.nome.isNotBlank() }
        val editou = fotoDoModelo == null || fotoDoModelo != fotoAtual()
        if (temConteudo && editou && modeloAplicado != modelo.id) {
            confirmar(
                titulo = getString(R.string.tab_usar_modelo_titulo, modelo.titulo),
                mensagem = getString(R.string.tab_usar_modelo_msg),
                rotuloConfirmar = getString(R.string.tab_usar_modelo),
                rotuloCancelar = getString(R.string.tab_manter),
                perigo = false
            ) { aplicarModeloAgora(modelo) }
            return
        }
        aplicarModeloAgora(modelo)
    }

    private fun aplicarModeloAgora(modelo: ModeloTabela) {
        nome = modelo.nome
        icone = modelo.icone
        tipo = modelo.tipo
        binding.etNome.setText(nome)
        colunas.clear()
        modelo.colunas.forEach { c ->
            colunas += ColunaEdit(null, c.nome, c.tipo, c.opcoes.orEmpty().toMutableList(), false, "", null, nova = true, chave = proximaChave++)
        }
        fases.clear()
        fases += modelo.fases ?: listOf("")
        modeloAplicado = modelo.id
        fotoDoModelo = fotoAtual()
        montarModelos()
        atualizarTudo()
    }

    // --- Ícone --------------------------------------------------------------------

    private fun mostrarIcones() {
        binding.flIconeEscolhido.removeAllViews()
        binding.flIconeEscolhido.addView(caixaDeIcone(icone, ladoDp = 40))
        binding.tvAlterarIcone.setText(if (gradeAberta) R.string.tab_fechar else R.string.tab_alterar_icone)
        val grade = binding.gradeIcones
        grade.isVisible = gradeAberta
        if (!gradeAberta) return
        grade.removeAllViews()
        Tabelas.ICONES.forEach { nomeIcone ->
            val escolhido = nomeIcone == icone
            grade.addView(grade.inflarFilho<TextView>(R.layout.item_icone_opcao).apply {
                text = nomeIcone
                isSelected = escolhido
                contentDescription = nomeIcone
                setOnClickListener {
                    icone = nomeIcone
                    gradeAberta = false
                    binding.flIcone.removeAllViews()
                    binding.flIcone.addView(caixaDeIcone(icone, ladoDp = 40))
                    mostrarIcones()
                }
            })
        }
    }

    // --- Etapas (vaga nova) -------------------------------------------------------

    private fun mostrarFases() {
        binding.cartaoFases.isVisible = precisaDeFases
        if (!precisaDeFases) return
        val lista = binding.llFases
        lista.removeAllViews()
        fases.forEachIndexed { indice, fase ->
            val linha = ItemFaseBinding.inflate(layoutInflater, lista, false)
            linha.tvNumero.text = "${indice + 1}"
            val campo = linha.campo
            // A etapa já tem o número ao lado: sem a bolinha de "opção de lista".
            campo.bolinha.visibility = View.GONE
            campo.btnRemoverOpcao.contentDescription = getString(R.string.tab_remover_etapa)
            campo.etOpcao.apply {
                setText(fase)
                hint = getString(if (indice == 0) R.string.tab_etapa_ex_1 else R.string.tab_etapa_ex_n)
                doAfterTextChanged { fases[indice] = it?.toString().orEmpty(); atualizarAvisoFases() }
            }
            campo.btnRemoverOpcao.setOnClickListener {
                fases.removeAt(indice)
                if (fases.isEmpty()) fases += ""
                mostrarFases()
            }
            lista.addView(linha.root)
        }
        atualizarAvisoFases()
    }

    private fun atualizarAvisoFases() {
        binding.tvSemFase.isVisible = fasesValidas.isEmpty()
        atualizarBotaoSalvar()
    }

    // --- Colunas ------------------------------------------------------------------

    private fun adicionarColuna() {
        colunas += ColunaEdit(null, "", "texto", mutableListOf(), false, "", null, nova = true, chave = proximaChave++)
        mostrarColunas()
    }

    private fun mostrarColunas() {
        if (tipo == "financeiro" || tipo == "arquivo") return
        val lista = binding.llColunas
        lista.removeAllViews()
        if (colunas.isEmpty()) {
            lista.mostrarEstado(
                titulo = getString(R.string.tab_nenhuma_coluna),
                descricao = getString(R.string.tab_nenhuma_coluna_desc),
                icone = R.drawable.ic_grid_view,
                acao = getString(R.string.tab_primeira_coluna)
            ) { adicionarColuna() }
            // Já está dentro de um card: sem caixa dentro de card (DESIGN.md).
            lista.getChildAt(0)?.background = null
            return
        }
        colunas.forEachIndexed { indice, coluna ->
            if (indice > 0) lista.addView(divisoria())
            lista.addView(linhaDaColuna(coluna, indice))
        }
    }

    private fun linhaDaColuna(coluna: ColunaEdit, indice: Int): View {
        val b = ItemColunaBinding.inflate(layoutInflater, binding.llColunas, false)
        val info = Tabelas.tipoDaColuna(coluna.tipo)

        b.btnSubir.isEnabled = indice > 0
        b.btnSubir.alpha = if (indice > 0) 1f else 0.25f
        b.btnDescer.isEnabled = indice < colunas.size - 1
        b.btnDescer.alpha = if (indice < colunas.size - 1) 1f else 0.25f
        b.btnSubir.setOnClickListener { mover(indice, -1) }
        b.btnDescer.setOnClickListener { mover(indice, 1) }
        b.flIconeTipo.addView(caixaDeIcone(info.icone, ladoDp = 36, iconeDp = 18))

        b.etNome.setText(coluna.nome)
        b.etNome.doAfterTextChanged { coluna.nome = it?.toString().orEmpty(); b.campoNome.error = null }

        // Tipo só muda em coluna nova (como no web).
        b.acTipo.configurarSelect(Tabelas.TIPOS_DE_COLUNA.map { it.valor to it.rotulo }, coluna.tipo) { novo ->
            // Ligação guarda { multiplo }; lista guarda opções: trocar não leva um para o outro.
            if (novo == "vinculo" || coluna.tipo == "vinculo") {
                coluna.opcoes = mutableListOf()
                coluna.multiplo = false
                coluna.vinculadaId = ""
            }
            coluna.tipo = novo
            mostrarColunas()
        }
        b.campoTipo.isEnabled = !(editando && !coluna.nova)
        if (coluna.nova) b.flNovo.addView(selo(getString(R.string.tab_novo), R.color.primary, R.color.primary_100))

        b.tvDica.text = info.dica
        b.tvDica.isVisible = info.dica != null

        // Ligação
        b.blocoVinculo.isVisible = coluna.tipo == "vinculo"
        if (coluna.tipo == "vinculo") {
            b.campoVinculo.isVisible = coluna.nova
            b.cbMultiplo.isVisible = coluna.nova
            b.tvVinculoExistente.isVisible = !coluna.nova
            if (coluna.nova) {
                b.campoVinculo.placeholderText = getString(if (tabelasDeDados.isEmpty()) R.string.tab_crie_outra else R.string.tab_escolha_tabela)
                b.acVinculo.configurarSelect(
                    tabelasDeDados.map { it.id to if (it.id == tabelaId) getString(R.string.tab_esta_tabela, it.nome) else it.nome },
                    coluna.vinculadaId
                ) { coluna.vinculadaId = it; b.campoVinculo.error = null }
                b.cbMultiplo.isChecked = coluna.multiplo
                b.cbMultiplo.setOnCheckedChangeListener { _, marcado -> coluna.multiplo = marcado }
            } else {
                val alvo = coluna.vinculadaNome ?: getString(R.string.tab_removida)
                b.tvVinculoExistente.text = getString(if (coluna.multiplo) R.string.tab_liga_varios else R.string.tab_liga_um, alvo)
            }
        }

        // Lista de opções
        b.blocoOpcoes.isVisible = coluna.tipo == "selecao"
        if (coluna.tipo == "selecao") {
            coluna.opcoes.forEachIndexed { i, opcao ->
                val o = ItemOpcaoColunaBinding.inflate(layoutInflater, b.llOpcoes, false)
                o.etOpcao.setText(opcao)
                o.etOpcao.hint = getString(R.string.tab_opcao_n, i + 1)
                o.etOpcao.doAfterTextChanged { coluna.opcoes[i] = it?.toString().orEmpty() }
                o.btnRemoverOpcao.setOnClickListener { coluna.opcoes.removeAt(i); mostrarColunas() }
                b.llOpcoes.addView(o.root)
            }
            b.btnAddOpcao.setOnClickListener { coluna.opcoes += ""; mostrarColunas() }
        }

        b.btnRemover.setOnClickListener { removerColuna(coluna) }
        b.root.tag = coluna.chave
        return b.root
    }

    private fun mover(indice: Int, direcao: Int) {
        val alvo = indice + direcao
        if (alvo !in colunas.indices) return
        colunas[indice] = colunas[alvo].also { colunas[alvo] = colunas[indice] }
        mostrarColunas()
        // Editando e sem colunas novas, a ordem vai na hora (como no web).
        if (editando && colunas.none { it.nova }) {
            lifecycleScope.launch {
                val r = executarApi(getString(R.string.tab_erro_reordenar)) {
                    api.reordenarColunas(tabelaId!!, ReordenarRequest(colunas.mapNotNull { it.id }))
                }
                if (r is Resultado.Erro) avisar(r.mensagem, longo = true)
            }
        }
    }

    private fun removerColuna(coluna: ColunaEdit) {
        if (editando && coluna.id != null && !coluna.nova) {
            confirmar(
                titulo = getString(R.string.tab_excluir_coluna_titulo, coluna.nome),
                mensagem = getString(R.string.tab_excluir_coluna_msg),
                rotuloConfirmar = getString(R.string.tab_excluir_coluna)
            ) {
                lifecycleScope.launch {
                    when (val r = executarApi(getString(R.string.tab_erro_excluir_coluna)) { api.excluirColuna(tabelaId!!, coluna.id) }) {
                        is Resultado.Sucesso -> {
                            avisar(getString(R.string.tab_coluna_excluida))
                            colunas.remove(coluna)
                            originais.remove(coluna.id)
                            mostrarColunas()
                        }
                        is Resultado.Erro -> if (r.sessaoExpirada) redirecionarParaLogin() else avisar(r.mensagem, longo = true)
                    }
                }
            }
            return
        }
        colunas.remove(coluna)
        mostrarColunas()
    }

    // --- Salvar -------------------------------------------------------------------

    /** O que vai para a API de cada coluna (payloadDaColuna do web). */
    private fun payload(c: ColunaEdit): Map<String, Any?> =
        if (c.tipo == "vinculo") mapOf("nome" to c.nome, "tipo" to c.tipo, "modulo_vinculado_id" to c.vinculadaId, "opcoes" to mapOf("multiplo" to c.multiplo))
        else buildMap {
            put("nome", c.nome)
            put("tipo", c.tipo)
            if (c.tipo == "selecao" && c.opcoes.isNotEmpty()) put("opcoes", c.opcoes.toList())
        }

    /** Os "required" do formulário do web: nome da tabela, das colunas e a tabela ligada. */
    private fun validar(): Boolean {
        var ok = true
        if (nome.isBlank()) { binding.campoNome.error = getString(R.string.tab_obrigatorio); ok = false }
        if (tipo != "financeiro" && tipo != "arquivo") {
            colunas.forEach { c ->
                val linha = binding.llColunas.findViewWithTag<View>(c.chave) ?: return@forEach
                val b = ItemColunaBinding.bind(linha)
                if (c.nome.isBlank()) { b.campoNome.error = getString(R.string.tab_obrigatorio); ok = false }
                if (c.tipo == "vinculo" && c.nova && c.vinculadaId.isBlank()) { b.campoVinculo.error = getString(R.string.tab_obrigatorio); ok = false }
            }
        }
        return ok
    }

    private fun salvar() {
        if (!validar()) return
        salvando = true
        atualizarBotaoSalvar()
        mostrarErro(null)
        lifecycleScope.launch {
            val erro = if (editando) salvarEdicao() else salvarNova()
            salvando = false
            atualizarBotaoSalvar()
            if (erro == null) {
                avisar(getString(if (editando) R.string.tab_atualizada else R.string.tab_criada))
                setResult(RESULT_OK)
                finish()
            } else {
                if (erro.sessaoExpirada) return@launch redirecionarParaLogin()
                mostrarErro(erro.mensagem)
                binding.rolagem.smoothScrollTo(0, 0)
            }
        }
    }

    private suspend fun salvarNova(): Resultado.Erro? {
        val corpo = buildMap<String, Any?> {
            put("nome", nome)
            put("icone", icone)
            put("tipo", tipo)
            put("campos", if (tipo == "financeiro" || tipo == "arquivo") emptyList() else colunas.map { payload(it) })
            if (tipo == "recrutamento") put("fases", fasesValidas)
        }
        return chamarApi(getString(R.string.tab_erro_salvar)) { api.criar(corpo) } as? Resultado.Erro
    }

    private suspend fun salvarEdicao(): Resultado.Erro? {
        val id = tabelaId!!
        val padrao = getString(R.string.tab_erro_salvar)
        (executarApi(padrao) { api.atualizar(id, AtualizarTabelaRequest(nome, icone, tipo)) } as? Resultado.Erro)?.let { return it }
        for (c in colunas) {
            if (c.nova) {
                (executarApi(padrao) { api.adicionarColuna(id, payload(c)) } as? Resultado.Erro)?.let { return it }
            } else if (c.id != null) {
                val (nomeOriginal, opcoesOriginais) = originais[c.id] ?: continue
                val mudancas = buildMap<String, Any?> {
                    if (c.nome != nomeOriginal) put("nome", c.nome)
                    if (c.tipo == "selecao" && c.opcoes != opcoesOriginais) put("opcoes", c.opcoes.toList())
                }
                if (mudancas.isNotEmpty()) {
                    (executarApi(padrao) { api.atualizarColuna(id, c.id, mudancas) } as? Resultado.Erro)?.let { return it }
                }
            }
        }
        return null
    }

    companion object {
        const val EXTRA_ID = "com.example.levit.extra.TABELA_ID"

        const val EXTRA_MODELO = "com.example.levit.extra.MODELO"

        fun abrir(context: Context, tabelaId: String? = null, modelo: String? = null) =
            context.startActivity(Intent(context, TabelaFormActivity::class.java).putExtra(EXTRA_ID, tabelaId).putExtra(EXTRA_MODELO, modelo))
    }
}
