package com.example.levit

import android.app.DatePickerDialog
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.CheckBox
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import com.example.levit.data.remote.ApiClient
import com.example.levit.data.remote.dto.Cargo
import com.example.levit.data.remote.dto.Coluna
import com.example.levit.data.remote.dto.Membro
import com.example.levit.data.remote.dto.Modulo
import com.example.levit.data.remote.dto.Pessoa
import com.example.levit.data.remote.dto.TabelaDetalhe
import com.example.levit.data.repository.ModuloRepository
import com.example.levit.data.repository.Resultado
import com.example.levit.data.repository.ResultadoModulo
import com.example.levit.data.repository.chamarApi
import com.example.levit.data.repository.executarApi
import com.example.levit.databinding.ActivityAutomacaoFormBinding
import com.example.levit.databinding.ItemAutAdicionarAcaoBinding
import com.example.levit.databinding.ItemAutCabecalhoNoBinding
import com.example.levit.databinding.ItemAutLigarBinding
import com.example.levit.databinding.ItemAutLogicaBinding
import com.example.levit.databinding.ItemAutOpcaoBinding
import com.example.levit.databinding.ItemAutRegraBinding
import com.example.levit.databinding.ItemAutRodapeBinding
import com.example.levit.databinding.ItemCampoSelectBinding
import com.example.levit.databinding.ItemCampoTextoBinding
import com.example.levit.ui.AcaoAutomacao
import com.example.levit.ui.Automacoes
import com.example.levit.ui.Datas
import com.example.levit.ui.Funil
import com.example.levit.ui.Prazos
import com.example.levit.ui.RegraAutomacao
import com.example.levit.ui.SimulacaoAutomacao
import com.example.levit.ui.Vinculos
import com.example.levit.ui.avisar
import com.example.levit.ui.botao
import com.example.levit.ui.configurarSelect
import com.example.levit.ui.divisoria
import com.example.levit.ui.dp
import com.example.levit.ui.inflar
import com.example.levit.ui.inflarFilho
import com.example.levit.ui.mostrarCarregando
import com.example.levit.ui.texto
import com.google.android.material.button.MaterialButton
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.gson.JsonObject
import kotlinx.coroutines.launch
import java.util.Calendar

/**
 * Criar ou editar uma automação (AutomacaoForm.jsx): propriedades, 1 Quando
 * (gatilhos), 2 Se (condições, opcional) e 3 Então (uma ou mais ações).
 */
class AutomacaoFormActivity : HamburgerMenuBaseActivity() {

    private lateinit var binding: ActivityAutomacaoFormBinding
    private val api by lazy { ApiClient.automacaoApi(applicationContext) }
    private val moduloApi by lazy { ApiClient.moduloApi(applicationContext) }
    private val equipeApi by lazy { ApiClient.equipeApi(applicationContext) }
    private val moduloRepository by lazy { ModuloRepository(applicationContext) }

    private val moduloIdParam by lazy { intent.getStringExtra(EXTRA_MODULO) }
    private val automacaoId by lazy { intent.getStringExtra(EXTRA_ID) }
    private val editando get() = automacaoId != null

    // Estado do formulário
    private var nome = ""
    private var moduloId = ""
    private var ativarAoSalvar = true
    private val gatilhos = mutableListOf("criacao")
    private var temCondicao = false
    private var logica = "e"
    private val regras = mutableListOf(RegraAutomacao())
    private val acoes = mutableListOf(Automacoes.novaAcao())

    // Dados de apoio
    private var modulos: List<Modulo> = emptyList()
    private val detalhes = mutableMapOf<String, TabelaDetalhe>()
    private var equipe: List<Membro>? = null
    private var cargos: List<Cargo>? = null
    private var pessoas: List<Pessoa>? = null
    private val titulos = mutableMapOf<String, List<Pair<String, String>>>()
    private var opcoesEtapa: List<String> = emptyList()
    private var carregandoInicial = false
    private var salvando = false
    private lateinit var btnTestar: MaterialButton
    private lateinit var btnSalvar: MaterialButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAutomacaoFormBinding.inflate(layoutInflater)
        setContentViewWithHamburgerMenu(binding.root)
        if (!menuPronto()) return
        binding.btnVoltar.setOnClickListener { finish() }
        binding.tvTitulo.setText(if (editando) R.string.aut_editar_titulo else R.string.aut_nova)
        moduloId = moduloIdParam.orEmpty()
        carregandoInicial = editando
        renderizar()

        lifecycleScope.launch {
            (moduloRepository.listar() as? ResultadoModulo.Sucesso)?.let { modulos = it.dados }
            if (editando) carregarAutomacao()
            if (moduloId.isNotEmpty()) carregarModulo(moduloId)
            carregandoInicial = false
            renderizar()
        }
    }

    private suspend fun carregarAutomacao() {
        val r = chamarApi(getString(R.string.aut_erro_carregar_uma)) { api.listar(moduloIdParam.orEmpty()) }
        if (r is Resultado.Erro) { if (r.sessaoExpirada) redirecionarParaLogin(); mostrarErro(r.mensagem); return }
        val a = (r as Resultado.Sucesso).dados.firstOrNull { it.get("id")?.asString == automacaoId }
            ?: return mostrarErro(getString(R.string.aut_nao_encontrada))
        nome = a.get("nome")?.takeIf { it.isJsonPrimitive }?.asString.orEmpty()
        gatilhos.clear()
        gatilhos += Automacoes.gatilhosDaAutomacao(a).ifEmpty { listOf("criacao") }
        ativarAoSalvar = a.get("ativo")?.takeIf { it.isJsonPrimitive }?.let {
            if (it.asJsonPrimitive.isBoolean) it.asBoolean else it.asString in listOf("t", "true", "1")
        } == true
        Automacoes.condicoesDaAutomacao(a)?.let { c ->
            temCondicao = true
            logica = c.logica
            regras.clear(); regras += c.regras
        }
        val salvas = a.get("acoes")?.takeIf { it.isJsonArray }?.asJsonArray?.mapNotNull { it.takeIf { e -> e.isJsonObject }?.asJsonObject }.orEmpty()
        if (salvas.isNotEmpty()) {
            acoes.clear()
            salvas.forEach { ac ->
                val conf = ac.get("configuracao")?.takeIf { it.isJsonObject }?.asJsonObject
                acoes += AcaoAutomacao(ac.get("tipo")?.asString.orEmpty(), paraMapa(conf))
            }
        }
    }

    /** JSON da configuração para o mapa editável (mapeamento é um objeto aninhado). */
    private fun paraMapa(o: JsonObject?): MutableMap<String, Any?> {
        val m = mutableMapOf<String, Any?>()
        o?.entrySet()?.forEach { (k, v) ->
            m[k] = when {
                v.isJsonNull -> null
                v.isJsonObject -> paraMapa(v.asJsonObject)
                v.isJsonPrimitive -> v.asString
                else -> v.toString()
            }
        }
        return m
    }

    private suspend fun carregarModulo(id: String) {
        if (id.isEmpty() || detalhes.containsKey(id)) return
        (chamarApi("") { moduloApi.detalhes(id) } as? Resultado.Sucesso)?.let { detalhes[id] = it.dados }
        // Etapas da vaga (para condições sobre "Etapa"): vêm do próprio quadro.
        if (Automacoes.ehVaga(tipoDe(id))) {
            (chamarApi("") { ApiClient.recrutamentoApi(this).kanbanDaVaga(id) } as? Resultado.Sucesso)?.let { r ->
                opcoesEtapa = Funil.etapas(r.dados).map { it.nome }
            }
        }
    }

    private fun tipoDe(id: String) = modulos.firstOrNull { it.id == id }?.tipo ?: detalhes[id]?.tipo

    private val vaga get() = Automacoes.ehVaga(tipoDe(moduloId))
    private val gatilhosDisponiveis get() = Automacoes.gatilhosDoModulo(tipoDe(moduloId))
    private val acoesDisponiveis get() = Automacoes.acoesDoModulo(tipoDe(moduloId))
    private val camposModulo get() = detalhes[moduloId]?.campos.orEmpty()
    private val camposResponsavel get() = camposModulo.filter { it.tipo == "responsavel" }

    /** Em vagas, condições e variáveis usam os campos do candidato. */
    private val camposCondicao: List<Coluna>
        get() = if (vaga) Automacoes.CAMPOS_CANDIDATO.map { (id, nome, tipo) -> Coluna(id, nome, tipo, null, null, null) } else camposModulo

    private val dicaVariaveis get() = camposCondicao.takeIf { it.isNotEmpty() }?.let { c ->
        getString(R.string.aut_variaveis, c.joinToString(", ") { "{{${it.nome}}}" })
    }

    private val definicaoValida get() = moduloId.isNotEmpty() &&
        acoes.all { Automacoes.acaoValida(it) } &&
        acoes.all { it.tipo in acoesDisponiveis } &&
        gatilhos.all { it in gatilhosDisponiveis } &&
        (!temCondicao || regras.all { Automacoes.regraValida(it) })

    private val podeSalvar get() = !salvando && nome.trim().length >= 2 && definicaoValida

    private fun definicao() = Automacoes.montarDefinicao(nome, gatilhos, temCondicao, logica, regras, acoes)

    private fun atualizarBotoes() {
        if (!::btnSalvar.isInitialized) return
        btnTestar.isEnabled = definicaoValida
        if (android.os.Build.VERSION.SDK_INT >= 26) btnTestar.tooltipText = getString(if (definicaoValida) R.string.aut_testar_pronto else R.string.aut_testar_dica)
        btnSalvar.isEnabled = podeSalvar
        btnSalvar.setText(if (salvando) R.string.aut_salvando else R.string.aut_salvar)
    }

    private fun mostrarErro(mensagem: String?) {
        binding.tvErro.text = mensagem
        binding.tvErro.isVisible = !mensagem.isNullOrBlank()
    }

    // --- Peças --------------------------------------------------------------------

    private fun cartao(): LinearLayout = inflar(R.layout.item_aut_cartao)

    private fun conector(): View = inflar(R.layout.item_aut_conector)

    /** Número do nó (1, 2, 3, 3.2...) + título. */
    private fun cabecalhoDoNo(numero: String, titulo: String, direita: View? = null): View {
        val b = ItemAutCabecalhoNoBinding.inflate(layoutInflater)
        b.tvNumero.text = numero
        b.tvTitulo.text = titulo
        direita?.let { b.linha.addView(it) }
        return b.root
    }

    private fun campoTexto(rotulo: String, valor: String?, placeholder: String? = null, dica: String? = null, multilinha: Boolean = false,
                           tipoEntrada: Int = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES, aoMudar: (String) -> Unit): View {
        val c = ItemCampoTextoBinding.inflate(layoutInflater)
        c.campo.hint = rotulo
        placeholder?.let { c.campo.placeholderText = it }
        dica?.let { c.campo.helperText = it }
        c.etValor.inputType = if (multilinha) tipoEntrada or InputType.TYPE_TEXT_FLAG_MULTI_LINE else tipoEntrada
        if (multilinha) { c.etValor.minLines = 3; c.etValor.gravity = Gravity.TOP }
        c.etValor.setText(valor)
        c.etValor.doAfterTextChanged { aoMudar(it?.toString().orEmpty()); atualizarBotoes() }
        return c.root
    }

    private fun campoSelect(rotulo: String, opcoes: List<Pair<String, String>>, valor: String?, placeholder: String? = null,
                            dica: String? = null, habilitado: Boolean = true, aoEscolher: (String) -> Unit): View {
        val s = ItemCampoSelectBinding.inflate(layoutInflater)
        s.campo.hint = rotulo
        placeholder?.let { s.campo.placeholderText = it }
        dica?.let { s.campo.helperText = it }
        s.campo.isEnabled = habilitado
        s.acValor.configurarSelect(opcoes, valor.orEmpty()) { aoEscolher(it); atualizarBotoes() }
        return s.root
    }

    private fun campoData(rotulo: String, valor: String?, aoMudar: (String) -> Unit): View {
        val c = ItemCampoTextoBinding.inflate(layoutInflater)
        c.campo.hint = rotulo
        c.etValor.isFocusable = false
        c.etValor.inputType = InputType.TYPE_NULL
        c.etValor.setText(Datas.dataCurta(valor?.takeIf { it.isNotEmpty() }))
        c.etValor.setOnClickListener {
            val cal = Calendar.getInstance().apply { Datas.ler(valor)?.let { time = it } }
            DatePickerDialog(this, { _, a, m, d ->
                val iso = Datas.iso(Calendar.getInstance().apply { set(a, m, d) }.time)
                c.etValor.setText(Datas.dataCurta(iso))
                aoMudar(iso)
                atualizarBotoes()
            }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
        }
        return c.root
    }

    /** Grade de escolha com ícone (os botões de gatilho e de ação do web). */
    private fun grade(opcoes: List<Pair<String, com.example.levit.ui.InfoComIcone>>, marcado: (String) -> Boolean, aoTocar: (String) -> Unit): View {
        val g = inflar<GridLayout>(R.layout.item_aut_grade)
        opcoes.forEach { (valor, info) ->
            val b = ItemAutOpcaoBinding.inflate(layoutInflater, g, false)
            b.tvIcone.text = info.icone
            b.tvRotulo.text = info.rotulo
            b.root.contentDescription = info.rotulo
            b.root.isSelected = marcado(valor)
            b.root.setOnClickListener { aoTocar(valor) }
            g.addView(b.root)
        }
        return g
    }

    // --- Tela ---------------------------------------------------------------------

    private fun renderizar() {
        val conteudo = binding.llConteudo
        conteudo.removeAllViews()
        if (carregandoInicial) return conteudo.mostrarCarregando(4)

        // Propriedades
        val prop = cartao()
        prop.addView(texto(getString(R.string.aut_propriedades), 16f, R.color.ink, R.font.archivo_semibold))
        prop.addView(campoTexto(getString(R.string.aut_nome), nome, getString(R.string.aut_nome_ex)) { nome = it })
        prop.addView(campoSelect(getString(R.string.aut_tabela_origem), modulos.map { it.id to it.nome }, moduloId,
            placeholder = getString(R.string.aut_selecione_tabela),
            dica = if (vaga) getString(R.string.aut_vaga_dica) else null,
            habilitado = !editando) { trocarModulo(it) })
        prop.addView(divisoria(12))
        prop.addView(inflar<MaterialSwitch>(R.layout.item_aut_switch).apply {
            text = getString(R.string.aut_ativar_ao_salvar)
            isChecked = ativarAoSalvar
            setOnCheckedChangeListener { _, v -> ativarAoSalvar = v }
        })
        conteudo.addView(prop)
        conteudo.addView(conector())

        // 1 Quando
        val quando = cartao()
        quando.addView(cabecalhoDoNo("1", getString(R.string.aut_quando)))
        quando.addView(grade(gatilhosDisponiveis.map { it to Automacoes.GATILHOS.getValue(it) }, { it in gatilhos }) { valor ->
            // Alterna um gatilho; sempre sobra pelo menos um selecionado.
            if (valor !in gatilhos) gatilhos += valor else if (gatilhos.size > 1) gatilhos -= valor
            renderizar()
        })
        conteudo.addView(quando)
        conteudo.addView(conector())

        // 2 Se
        val se = cartao()
        val chave = inflar<MaterialSwitch>(R.layout.item_aut_switch).apply {
            isChecked = temCondicao
            contentDescription = getString(R.string.aut_usar_condicoes)
            setOnCheckedChangeListener { _, ligado ->
                temCondicao = ligado
                if (ligado && regras.isEmpty()) regras += RegraAutomacao()
                renderizar()
            }
        }
        se.addView(cabecalhoDoNo("2", getString(R.string.aut_se), chave))
        if (temCondicao) desenharCondicoes(se) else se.addView(texto(getString(R.string.aut_sem_condicao), 12f))
        if (temCondicao && regras.any { it.operador in Automacoes.OPERADORES_DE_MUDANCA }) {
            se.addView(texto(getString(if (vaga) R.string.aut_mudanca_vaga else R.string.aut_mudanca_item), 12f, topo = 12))
        }
        conteudo.addView(se)
        conteudo.addView(conector())

        // 3 Então (uma ou mais ações)
        acoes.forEachIndexed { indice, acao ->
            val c = cartao()
            val remover = if (acoes.size > 1) inflar<TextView>(R.layout.item_icone_excluir).apply {
                contentDescription = getString(R.string.aut_remover_acao)
                layoutParams = LinearLayout.LayoutParams(dp(36), dp(36))
                setOnClickListener { acoes.removeAt(indice); renderizar() }
            } else null
            c.addView(cabecalhoDoNo(if (indice == 0) "3" else "3.${indice + 1}", getString(R.string.aut_entao), remover))
            c.addView(grade(acoesDisponiveis.map { it to Automacoes.ACOES.getValue(it) }, { it == acao.tipo }) { tipo ->
                acao.tipo = tipo
                acao.configuracao = Automacoes.configuracaoInicial(tipo)
                renderizar()
            })
            desenharAcao(c, acao)
            conteudo.addView(c)
            conteudo.addView(conector())
        }
        val adicionar = ItemAutAdicionarAcaoBinding.inflate(layoutInflater)
        adicionar.btnAdicionar.setOnClickListener { acoes += Automacoes.novaAcao(); renderizar() }
        conteudo.addView(adicionar.root)

        // Rodapé: Testar, Cancelar, Salvar
        val rodape = ItemAutRodapeBinding.inflate(layoutInflater)
        btnTestar = rodape.btnTestar
        btnTestar.setOnClickListener { SimulacaoAutomacao(this, moduloId, definicao(), vaga) { redirecionarParaLogin() }.abrir() }
        btnSalvar = rodape.btnSalvar
        btnSalvar.setOnClickListener { salvar() }
        rodape.btnCancelar.setOnClickListener { finish() }
        conteudo.addView(rodape.root)
        atualizarBotoes()
    }

    /** Trocar de tabela reinicia o que depende dela: gatilhos, condições e algumas ações. */
    private fun trocarModulo(novo: String) {
        moduloId = novo
        gatilhos.clear(); gatilhos += gatilhosDisponiveis.first()
        temCondicao = false
        regras.clear(); regras += RegraAutomacao()
        acoes.replaceAll { a -> if (a.tipo in acoesDisponiveis && a.tipo != "atualizar_campo") a else Automacoes.novaAcao() }
        opcoesEtapa = emptyList()
        renderizar()
        lifecycleScope.launch { carregarModulo(novo); renderizar() }
    }

    // --- Condições (CondicoesEditor.jsx) -------------------------------------------------

    private fun desenharCondicoes(destino: LinearLayout) {
        if (regras.size > 1) {
            val l = ItemAutLogicaBinding.inflate(layoutInflater)
            l.btnTodas.tag = "e"
            l.btnQualquer.tag = "ou"
            l.grupo.check(if (logica == "ou") l.btnQualquer.id else l.btnTodas.id)
            l.grupo.addOnButtonCheckedListener { g, id, marcado -> if (marcado) logica = g.findViewById<View>(id).tag as String }
            destino.addView(l.root)
        }
        val campos = camposCondicao
        regras.forEachIndexed { indice, regra ->
            val caixaB = ItemAutRegraBinding.inflate(layoutInflater, destino, false)
            val caixa = caixaB.root
            val topo = caixaB.topo
            topo.addView(campoSelect(getString(R.string.aut_coluna), campos.map { it.id.orEmpty() to it.nome.orEmpty() }, regra.campo,
                placeholder = getString(R.string.aut_selecione)) { regra.campo = it; regra.valor = ""; regra.valorDe = ""; renderizar() },
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            if (regras.size > 1) topo.addView(topo.inflarFilho<TextView>(R.layout.item_icone_remover).apply {
                contentDescription = getString(R.string.aut_remover_condicao, indice + 1)
                setOnClickListener { regras.removeAt(indice); renderizar() }
            })
            val corpo = caixaB.corpo
            corpo.addView(campoSelect(getString(R.string.aut_operador), Automacoes.OPERADORES.toList(), regra.operador) { op ->
                regra.operador = op
                // Condição de mudança precisa do valor anterior: garante o gatilho que o fornece.
                if (op in Automacoes.OPERADORES_DE_MUDANCA) {
                    val necessario = Automacoes.gatilhoDeMudanca(tipoDe(moduloId))
                    if (necessario !in gatilhos) gatilhos += necessario
                }
                renderizar()
            })
            val campo = campos.firstOrNull { it.id == regra.campo }
            val mudanca = regra.operador in Automacoes.OPERADORES_DE_MUDANCA
            if (regra.operador == "mudou_de_para") corpo.addView(campoValor(getString(R.string.aut_de), campo, regra.valorDe) { regra.valorDe = it })
            if (regra.operador !in Automacoes.OPERADORES_SEM_VALOR) {
                val ph = if (regra.operador == "maior" || regra.operador == "menor") getString(R.string.aut_numero_ou_data) else getString(R.string.aut_valor)
                corpo.addView(campoValor(getString(if (mudanca) R.string.aut_para else R.string.aut_valor), campo, regra.valor, ph) { regra.valor = it })
            }
            destino.addView(caixa)
        }
        if (regras.size < Automacoes.MAX_REGRAS) destino.addView(botao(getString(R.string.aut_adicionar_condicao), "ghost", R.drawable.ic_add) {
            regras += RegraAutomacao(); renderizar()
        })
    }

    /**
     * CampoValor: lista para colunas de lista/etapa, a equipe para "Responsável",
     * itens para "Ligação", calendário para datas e prazos, texto livre para o resto.
     */
    private fun campoValor(rotulo: String, campo: Coluna?, valor: String, placeholder: String? = null, aoMudar: (String) -> Unit): View {
        when (campo?.tipo) {
            "responsavel" -> {
                if (pessoas == null) carregarPessoas()
                return campoSelect(rotulo, pessoas.orEmpty().map { it.id to it.nome.orEmpty() }, valor, getString(R.string.aut_selecione)) { aoMudar(it) }
            }
            "vinculo" -> {
                val alvo = campo.moduloVinculadoId.orEmpty()
                if (!titulos.containsKey(alvo) && alvo.isNotEmpty()) carregarTitulos(alvo)
                return campoSelect(rotulo, titulos[alvo].orEmpty(), valor, getString(R.string.aut_selecione_item)) { aoMudar(it) }
            }
            "recorrencia" -> return campoSelect(rotulo, Prazos.REPETICOES, valor, getString(R.string.aut_selecione)) { aoMudar(it) }
        }
        val opcoes = when (campo?.tipo) {
            "selecao" -> campo.listaDeOpcoes
            "etapa" -> opcoesEtapa
            else -> null
        }
        if (!opcoes.isNullOrEmpty()) return campoSelect(rotulo, opcoes.map { it to it }, valor, getString(R.string.aut_selecione)) { aoMudar(it) }
        if (campo?.tipo == "data" || campo?.tipo == "prazo") return campoData(rotulo, valor, aoMudar)
        return campoTexto(rotulo, valor, placeholder ?: getString(R.string.aut_valor), tipoEntrada = InputType.TYPE_CLASS_TEXT) { aoMudar(it) }
    }

    private fun carregarPessoas() {
        pessoas = emptyList()
        lifecycleScope.launch { (chamarApi("") { equipeApi.pessoas() } as? Resultado.Sucesso)?.let { pessoas = it.dados; renderizar() } }
    }

    private fun carregarTitulos(tabela: String) {
        titulos[tabela] = emptyList()
        lifecycleScope.launch {
            (chamarApi("") { ApiClient.registroApi(this@AutomacaoFormActivity).titulos(tabela) } as? Resultado.Sucesso)?.let { r ->
                titulos[tabela] = r.dados.map { it.id to Vinculos.tituloDoItem(it.titulo) }
                renderizar()
            }
        }
    }

    // --- Ações ------------------------------------------------------------------------

    private fun desenharAcao(destino: LinearLayout, acao: AcaoAutomacao) {
        val c = acao.configuracao
        fun s(chave: String) = c[chave]?.toString().orEmpty()
        val bloco = inflar<LinearLayout>(R.layout.item_aut_bloco_acao)
        when (acao.tipo) {
            "enviar_email" -> {
                if (camposResponsavel.isNotEmpty()) {
                    val linha = inflar<com.google.android.material.chip.ChipGroup>(R.layout.item_aut_chips)
                    linha.addView(texto(getString(R.string.aut_enviar_para), 12f))
                    camposResponsavel.forEach { campo ->
                        val ativo = s("destinatario_campo_id") == campo.id
                        linha.addView(linha.inflarFilho<com.google.android.material.chip.Chip>(R.layout.item_aut_chip).apply {
                            text = getString(R.string.aut_do_item, campo.nome)
                            isChecked = ativo
                            setOnClickListener {
                                c["destinatario"] = ""
                                c["destinatario_campo_id"] = if (ativo) "" else campo.id
                                renderizar()
                            }
                        })
                    }
                    bloco.addView(linha)
                }
                if (camposResponsavel.any { it.id == s("destinatario_campo_id") }) {
                    bloco.addView(texto(getString(R.string.aut_vai_para_conta), 12f, topo = 4))
                } else {
                    val dica = when {
                        vaga -> getString(R.string.aut_destinatario_vaga)
                        camposModulo.isNotEmpty() -> getString(R.string.aut_destinatario_item, camposModulo.joinToString(", ") { "{{${it.nome}}}" })
                        else -> null
                    }
                    bloco.addView(campoTexto(getString(R.string.aut_destinatario), s("destinatario").ifEmpty { s("destinatario_campo_id") },
                        getString(R.string.aut_destinatario_ex), dica, tipoEntrada = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS) {
                        c["destinatario"] = it
                        if (s("destinatario_campo_id").isNotEmpty()) c["destinatario_campo_id"] = ""
                    })
                }
                bloco.addView(campoTexto(getString(R.string.aut_assunto), s("assunto"), getString(R.string.aut_assunto_ex), dicaVariaveis) { c["assunto"] = it })
                bloco.addView(campoTexto(getString(R.string.aut_corpo), s("corpo"), getString(R.string.aut_corpo_ex), multilinha = true) { c["corpo"] = it })
            }
            "webhook" -> bloco.addView(campoTexto(getString(R.string.aut_webhook), s("url"), getString(R.string.aut_webhook_ex),
                getString(R.string.aut_webhook_dica), tipoEntrada = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI) { c["url"] = it })
            "atualizar_campo" -> desenharAtualizarCampo(bloco, c)
            "criar_registro" -> desenharCriarRegistro(bloco, c)
            "notificacao" -> {
                if (equipe == null || cargos == null) carregarEquipe()
                val destinos = Automacoes.DESTINO_NOTIFICACAO.entries
                    .filter { it.key != "responsavel" || camposResponsavel.isNotEmpty() || s("destinatarios") == "responsavel" }
                    .map { it.key to it.value }
                bloco.addView(campoSelect(getString(R.string.aut_quem_recebe), destinos, s("destinatarios").ifEmpty { "todos" }) {
                    c["destinatarios"] = it; c["destino_id"] = ""; renderizar()
                })
                if (s("destinatarios") == "cargo") bloco.addView(campoSelect(getString(R.string.aut_cargo),
                    cargos.orEmpty().map { it.id to it.nome.orEmpty() }, s("destino_id"), getString(R.string.aut_selecione_cargo)) { c["destino_id"] = it })
                if (s("destinatarios") == "usuario") bloco.addView(campoSelect(getString(R.string.aut_pessoa),
                    equipe.orEmpty().map { it.id to (it.nome ?: it.email.orEmpty()) }, s("destino_id"), getString(R.string.aut_selecione_pessoa)) { c["destino_id"] = it })
                bloco.addView(texto(getString(R.string.aut_so_quem_tem_acesso), 12f, topo = 6))
                bloco.addView(campoTexto(getString(R.string.aut_titulo_notif), s("titulo"), getString(R.string.aut_titulo_notif_ex), dicaVariaveis) { c["titulo"] = it.take(200) })
                bloco.addView(campoTexto(getString(R.string.aut_mensagem_opcional), s("mensagem"), getString(R.string.aut_mensagem_ex), multilinha = true) { c["mensagem"] = it })
            }
        }
        destino.addView(bloco)
    }

    private fun carregarEquipe() {
        equipe = equipe ?: emptyList()
        cargos = cargos ?: emptyList()
        lifecycleScope.launch {
            (chamarApi("") { equipeApi.membros() } as? Resultado.Sucesso)?.let { equipe = it.dados }
            (chamarApi("") { equipeApi.cargos() } as? Resultado.Sucesso)?.let { cargos = it.dados }
            renderizar()
        }
    }

    private fun variaveisDaOrigem() = camposModulo.takeIf { it.isNotEmpty() }?.let { getString(R.string.aut_aceita_variaveis, it.joinToString(", ") { c -> "{{${c.nome}}}" }) }

    /** AcaoAtualizarCampo: muda uma coluna do item que disparou a automação. */
    private fun desenharAtualizarCampo(bloco: LinearLayout, c: MutableMap<String, Any?>) {
        val campo = camposModulo.firstOrNull { it.id == c["campo_id"]?.toString() }
        bloco.addView(campoSelect(getString(R.string.aut_coluna), camposModulo.map { it.id.orEmpty() to it.nome.orEmpty() },
            c["campo_id"]?.toString(), getString(R.string.aut_selecione_coluna)) { c["campo_id"] = it; c["valor"] = ""; renderizar() })
        val valor = c["valor"]?.toString().orEmpty()
        if (campo?.tipo in listOf("selecao", "responsavel", "prazo", "recorrencia", "vinculo")) {
            bloco.addView(campoValor(getString(R.string.aut_novo_valor), campo, valor) { c["valor"] = it })
        } else {
            bloco.addView(campoTexto(getString(R.string.aut_novo_valor), valor,
                if (campo?.tipo == "data") "AAAA-MM-DD" else getString(R.string.aut_deixe_vazio)) { c["valor"] = it })
        }
        bloco.addView(texto(getString(R.string.aut_nao_funciona_exclusao, variaveisDaOrigem() ?: getString(R.string.aut_aceita_variaveis_padrao)), 12f, topo = 6))
    }

    /** AcaoCriarRegistro: cria um item em outra tabela, preenchendo as colunas a partir deste. */
    @Suppress("UNCHECKED_CAST")
    private fun desenharCriarRegistro(bloco: LinearLayout, c: MutableMap<String, Any?>) {
        val destinoId = c["modulo_destino_id"]?.toString().orEmpty()
        val modulosDestino = modulos.filter { it.tipo != "recrutamento" && it.tipo != "financeiro" }
        bloco.addView(campoSelect(getString(R.string.aut_tabela_onde_criar), modulosDestino.map { it.id to it.nome }, destinoId,
            getString(R.string.aut_selecione_tabela)) {
            c["modulo_destino_id"] = it; c["mapeamento"] = mutableMapOf<String, Any?>(); c["ligar_campo_id"] = null
            renderizar()
            lifecycleScope.launch { carregarModulo(it); renderizar() }
        })
        if (destinoId.isEmpty()) return
        if (!detalhes.containsKey(destinoId)) lifecycleScope.launch { carregarModulo(destinoId); renderizar() }
        val mapeamento = (c["mapeamento"] as? MutableMap<String, Any?>) ?: mutableMapOf<String, Any?>().also { c["mapeamento"] = it }
        bloco.addView(texto(getString(R.string.aut_colunas_novo_item), 14f, R.color.ink, R.font.archivo_medium, topo = 12))
        val camposDestino = detalhes[destinoId]?.campos.orEmpty()
        if (camposDestino.isEmpty()) bloco.addView(texto(getString(R.string.aut_carregando_colunas), 12f, topo = 4))
        camposDestino.forEach { campo ->
            val id = campo.id.orEmpty()
            when {
                // Ligação que aponta para esta tabela: pode receber o próprio item que disparou.
                campo.tipo == "vinculo" && campo.moduloVinculadoId == moduloId -> {
                    val caixa = ItemAutLigarBinding.inflate(layoutInflater, bloco, false)
                    (caixa.cbLigar as CheckBox).apply {
                        text = getString(R.string.aut_ligar_ao_item, campo.nome)
                        isChecked = c["ligar_campo_id"] == id
                        setOnCheckedChangeListener { _, marcado ->
                            mapeamento.remove(id)
                            c["ligar_campo_id"] = if (marcado) id else null
                            atualizarBotoes()
                        }
                    }
                    bloco.addView(caixa.root)
                }
                campo.tipo == "vinculo" -> bloco.addView(campoValor(campo.nome.orEmpty(), campo, mapeamento[id]?.toString().orEmpty()) { mapeamento[id] = it })
                else -> bloco.addView(campoTexto(campo.nome.orEmpty(), mapeamento[id]?.toString(),
                    if (campo.tipo == "selecao") getString(R.string.aut_uma_das_opcoes, campo.listaDeOpcoes.joinToString(", ")) else getString(R.string.aut_texto_ou_variavel)
                ) { mapeamento[id] = it })
            }
        }
        bloco.addView(texto(getString(R.string.aut_campos_vazios, variaveisDaOrigem().orEmpty()).trim(), 12f, topo = 8))
    }

    // --- Salvar -------------------------------------------------------------------------

    private fun salvar() {
        if (!podeSalvar) return
        salvando = true
        atualizarBotoes()
        mostrarErro(null)
        val corpo = definicao() + ("ativo" to ativarAoSalvar)
        lifecycleScope.launch {
            // Editar atualiza no lugar: mantém o id e o histórico de execuções.
            val r = if (editando) executarApi(getString(R.string.aut_erro_salvar)) { api.atualizar(moduloIdParam.orEmpty(), automacaoId!!, corpo) }
            else executarApi(getString(R.string.aut_erro_salvar)) { api.criar(moduloId, corpo) }
            salvando = false
            when (r) {
                is Resultado.Sucesso -> {
                    avisar(getString(if (editando) R.string.aut_atualizada else R.string.aut_criada))
                    finish()
                }
                is Resultado.Erro -> {
                    if (r.sessaoExpirada) return@launch redirecionarParaLogin()
                    mostrarErro(r.mensagem)
                    binding.rolagem.smoothScrollTo(0, 0)
                    atualizarBotoes()
                }
            }
        }
    }

    companion object {
        const val EXTRA_MODULO = "com.example.levit.extra.AUTOMACAO_MODULO"
        const val EXTRA_ID = "com.example.levit.extra.AUTOMACAO_ID"

        fun abrir(context: Context, moduloId: String? = null, automacaoId: String? = null) =
            context.startActivity(Intent(context, AutomacaoFormActivity::class.java)
                .putExtra(EXTRA_MODULO, moduloId).putExtra(EXTRA_ID, automacaoId))
    }
}
