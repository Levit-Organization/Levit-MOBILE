package com.example.levit

import android.os.Bundle
import android.widget.CheckBox
import android.widget.LinearLayout
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import com.example.levit.data.remote.ApiClient
import com.example.levit.data.remote.dto.AtualizarMembroRequest
import com.example.levit.data.remote.dto.Cargo
import com.example.levit.data.remote.dto.CargoRequest
import com.example.levit.data.remote.dto.Convite
import com.example.levit.data.remote.dto.ConviteRequest
import com.example.levit.data.remote.dto.Membro
import com.example.levit.data.remote.dto.Modulo
import com.example.levit.data.remote.dto.NivelRequest
import com.example.levit.data.repository.ModuloRepository
import com.example.levit.data.repository.Resultado
import com.example.levit.data.repository.ResultadoModulo
import com.example.levit.data.repository.chamarApi
import com.example.levit.data.repository.executarApi
import com.example.levit.databinding.ActivityEquipeBinding
import com.example.levit.databinding.ItemCargoBinding
import com.example.levit.databinding.ItemConvitesCartaoBinding
import com.example.levit.databinding.ItemEquipeTopoBinding
import com.example.levit.databinding.ItemEstadoListaBinding
import com.example.levit.ui.AdaptadorDeLinhas
import com.example.levit.ui.CabecalhoFixo
import com.example.levit.ui.LinhaDeLista
import com.example.levit.ui.divisoria
import com.example.levit.ui.inflar
import androidx.recyclerview.widget.ConcatAdapter
import com.example.levit.databinding.ItemConviteBinding
import com.example.levit.databinding.ItemMembroBinding
import com.example.levit.databinding.ItemNivelTabelaBinding
import com.example.levit.databinding.SheetAcessoBinding
import com.example.levit.databinding.SheetCargoBinding
import com.example.levit.databinding.SheetMembroBinding
import com.example.levit.ui.Datas
import com.example.levit.ui.Folha
import com.example.levit.ui.Pessoas
import com.example.levit.ui.avisar
import com.example.levit.ui.configurarSelect
import com.example.levit.ui.confirmar
import com.example.levit.ui.mostrarAvatar
import com.example.levit.ui.mostrarCarregando
import com.example.levit.ui.mostrarEstado
import com.example.levit.ui.selo
import com.google.android.material.tabs.TabLayout
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/** Equipe: colaboradores, convites pendentes, cargos e permissões (TeamManagement.jsx). */
class EquipeActivity : HamburgerMenuBaseActivity() {

    private lateinit var binding: ActivityEquipeBinding
    private val api by lazy { ApiClient.equipeApi(applicationContext) }
    private val moduloRepository by lazy { ModuloRepository(applicationContext) }

    private var membros: List<Membro> = emptyList()
    private var convites: List<Convite> = emptyList()
    private var cargos: List<Cargo> = emptyList()
    private var modulos: List<Modulo> = emptyList()
    private var carregando = true
    private var erro: Resultado.Erro? = null
    private var abaCargos = false
    private var filtroCargo: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityEquipeBinding.inflate(layoutInflater)
        setContentViewWithHamburgerMenu(binding.root)
        if (!menuPronto()) return
        setupHamburgerMenuButton(R.id.ivMenu)
        setupSino(binding.flSino)
        topo = ItemEquipeTopoBinding.inflate(layoutInflater, binding.lista, false)
        binding.lista.adapter = ConcatAdapter(CabecalhoFixo(topo.root), adaptador)

        binding.abas.addTab(binding.abas.newTab())
        binding.abas.addTab(binding.abas.newTab())
        binding.abas.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                abaCargos = tab.position == 1
                renderizar()
            }
            override fun onTabUnselected(tab: TabLayout.Tab) = Unit
            override fun onTabReselected(tab: TabLayout.Tab) = Unit
        })
        topo.etBusca.doAfterTextChanged { if (!abaCargos) renderizar() }
        binding.btnConvidar.setOnClickListener { abrirMembro(null) }
        topo.btnCriarCargo.setOnClickListener { abrirCargo(null) }
        renderizar()
    }

    override fun onResume() {
        super.onResume()
        if (menuPronto()) carregar()
    }

    private fun carregar() = lifecycleScope.launch {
        coroutineScope {
            val m = async { chamarApi(getString(R.string.eq_erro_carregar)) { api.membros() } }
            val c = async { chamarApi(getString(R.string.eq_erro_carregar)) { api.cargos() } }
            val conv = async { chamarApi(getString(R.string.eq_erro_carregar)) { api.convites() } }
            val mod = async { moduloRepository.listar() }

            val resultados = listOf(m.await(), c.await())
            if (resultados.any { (it as? Resultado.Erro)?.sessaoExpirada == true }) return@coroutineScope redirecionarParaLogin()
            erro = resultados.filterIsInstance<Resultado.Erro>().firstOrNull()
            (m.await() as? Resultado.Sucesso)?.let { membros = it.dados }
            (c.await() as? Resultado.Sucesso)?.let { cargos = it.dados }
            // Convites e tabelas são complementares: se falharem, a tela segue sem eles.
            convites = (conv.await() as? Resultado.Sucesso)?.dados.orEmpty()
            modulos = (mod.await() as? ResultadoModulo.Sucesso)?.dados.orEmpty()
        }
        if (isFinishing) return@launch
        carregando = false
        renderizar()
    }

    // --- Tela -------------------------------------------------------------------

    private lateinit var topo: ItemEquipeTopoBinding

    /** Linhas da lista: cartão de convites, colaborador, cargo ou um estado (carregando, vazio, erro). */
    private sealed interface Linha : LinhaDeLista {
        data class Convites(val convites: List<Convite>) : Linha {
            override val chave get() = "convites"
            override val tipo get() = TIPO_CONVITES
        }
        data class DeMembro(val m: Membro) : Linha {
            override val chave get() = m.id
            override val tipo get() = TIPO_MEMBRO
        }
        data class DeCargo(val c: Cargo) : Linha {
            override val chave get() = c.id
            override val tipo get() = TIPO_CARGO
        }
        data class Estado(val chave0: String, val montar: (LinearLayout) -> Unit) : Linha {
            override val chave get() = chave0
            override val tipo get() = TIPO_ESTADO
        }
    }

    private val adaptador = AdaptadorDeLinhas<Linha>(
        criar = { pai, tipo ->
            when (tipo) {
                TIPO_CONVITES -> ItemConvitesCartaoBinding.inflate(layoutInflater, pai, false)
                TIPO_MEMBRO -> ItemMembroBinding.inflate(layoutInflater, pai, false)
                TIPO_CARGO -> ItemCargoBinding.inflate(layoutInflater, pai, false)
                else -> ItemEstadoListaBinding.inflate(layoutInflater, pai, false)
            }
        },
        ligar = { b, linha ->
            when (linha) {
                is Linha.Convites -> ligarConvites(b as ItemConvitesCartaoBinding, linha.convites)
                is Linha.DeMembro -> ligarMembro(b as ItemMembroBinding, linha.m)
                is Linha.DeCargo -> ligarCargo(b as ItemCargoBinding, linha.c)
                is Linha.Estado -> linha.montar((b as ItemEstadoListaBinding).conteudo)
            }
        }
    )

    private fun renderizar() {
        binding.abas.getTabAt(0)?.text = getString(R.string.eq_aba_colaboradores, membros.size)
        binding.abas.getTabAt(1)?.text = getString(R.string.eq_aba_cargos, cargos.size)
        topo.llFiltros.isVisible = !abaCargos && erro == null
        topo.btnCriarCargo.isVisible = abaCargos && erro == null && cargos.isNotEmpty()

        // Filtro de cargo: "Todos" + cargos existentes.
        val opcoesFiltro = listOf("" to getString(R.string.eq_todos_cargos)) + cargos.map { it.id to (it.nome ?: "—") }
        if (opcoesFiltro.none { it.first == filtroCargo }) filtroCargo = ""
        topo.acFiltroCargo.configurarSelect(opcoesFiltro, filtroCargo) { filtroCargo = it; renderizar() }

        val e = erro
        adaptador.submitList(when {
            carregando && membros.isEmpty() -> listOf(Linha.Estado("carregando") { it.mostrarCarregando() })
            e != null -> listOf(Linha.Estado("erro:${e.mensagem}:${e.semAcesso}") {
                it.mostrarEstado(
                    titulo = getString(if (e.semAcesso) R.string.eq_sem_acesso_titulo else R.string.eq_erro_carregar),
                    descricao = if (e.semAcesso) getString(R.string.eq_sem_acesso_desc) else e.mensagem,
                    icone = if (e.semAcesso) R.drawable.ic_lock else R.drawable.ic_warning,
                    acao = if (e.semAcesso) null else getString(R.string.eq_tentar),
                    erro = true
                ) { carregando = true; erro = null; renderizar(); carregar() }
            })
            abaCargos -> linhasDeCargos()
            else -> linhasDeMembros()
        })
    }

    private fun linhasDeMembros(): List<Linha> {
        val linhas = mutableListOf<Linha>()
        if (convites.isNotEmpty()) linhas += Linha.Convites(convites)

        val termo = topo.etBusca.text?.toString()?.trim()?.lowercase().orEmpty()
        val cargoFiltro = cargos.firstOrNull { it.id == filtroCargo }
        val filtrados = membros.filter { m ->
            val buscaOk = termo.isEmpty() || m.nome.orEmpty().lowercase().contains(termo) || m.email.orEmpty().lowercase().contains(termo)
            val cargoOk = cargoFiltro == null || m.cargoNome.equals(cargoFiltro.nome, ignoreCase = true)
            buscaOk && cargoOk
        }
        if (filtrados.isEmpty()) {
            val comFiltro = termo.isNotEmpty() || cargoFiltro != null
            linhas += Linha.Estado("vazio:$comFiltro") {
                it.mostrarEstado(
                    titulo = getString(if (comFiltro) R.string.eq_ninguem_filtro else R.string.eq_so_voce),
                    descricao = getString(if (comFiltro) R.string.eq_ninguem_filtro_desc else R.string.eq_so_voce_desc),
                    icone = if (comFiltro) R.drawable.ic_search else R.drawable.ic_person_add
                )
            }
            return linhas
        }
        return linhas + filtrados.map { Linha.DeMembro(it) }
    }

    private fun ligarMembro(v: ItemMembroBinding, m: Membro) {
        val nome = m.nome?.takeIf { it.isNotBlank() } ?: getString(R.string.eq_colaborador)
        v.tvAvatar.mostrarAvatar(m.nome)
        v.tvNome.text = m.nome ?: "—"
        v.tvEmail.text = m.email ?: "—"
        val desde = Datas.desde(m.criadoEm)
        v.tvDesde.isVisible = desde != null
        v.tvDesde.text = desde?.let { getString(R.string.eq_desde, it) }
        v.flCargo.removeAllViews()
        m.cargoNome?.let { v.flCargo.addView(selo(it, R.color.primary, R.color.primary_100)) }
        v.btnEditar.contentDescription = getString(R.string.eq_editar_colaborador, nome)
        v.btnEditar.setOnClickListener { abrirMembro(m) }
        v.btnExcluir.contentDescription = getString(R.string.eq_excluir_colaborador, nome)
        v.btnExcluir.setOnClickListener { removerMembro(m) }
    }

    /** ConvitesPendentes: reenviar ou cancelar quem ainda não aceitou. */
    private fun ligarConvites(c: ItemConvitesCartaoBinding, convites: List<Convite>) {
        c.tvTitulo.text = getString(R.string.eq_convites_titulo, convites.size)
        val cartao = c.llConvites
        cartao.removeAllViews()
        convites.forEach { convite ->
            cartao.addView(divisoria())
            val v = ItemConviteBinding.inflate(layoutInflater, cartao, false)
            v.tvEmail.text = convite.email
            val cargo = convite.cargoNome ?: getString(R.string.eq_sem_cargo)
            v.tvDetalhe.text = convite.convidadoPorNome?.let { getString(R.string.eq_convidado_por, cargo, it) } ?: cargo
            val dias = Datas.diasAte(convite.expiraEm)
            v.flValidade.addView(
                if (convite.expirado == true) selo(getString(R.string.eq_expirado), R.color.danger, R.color.danger_bg, ponto = true)
                else selo(
                    if (dias != null && dias <= 1) getString(R.string.eq_vence_hoje) else getString(R.string.eq_vence_em, dias ?: 0),
                    R.color.warning, R.color.warning_bg, ponto = true
                )
            )
            v.btnReenviar.setOnClickListener { reenviarConvite(convite, v) }
            v.btnCancelar.setOnClickListener { cancelarConvite(convite) }
            cartao.addView(v.root)
        }
    }

    private fun linhasDeCargos(): List<Linha> {
        if (cargos.isEmpty()) return listOf(Linha.Estado("sem-cargos") {
            it.mostrarEstado(
                titulo = getString(R.string.eq_nenhum_cargo),
                descricao = getString(R.string.eq_nenhum_cargo_desc),
                icone = R.drawable.ic_key,
                acao = getString(R.string.eq_criar_cargo)
            ) { abrirCargo(null) }
        })
        return cargos.map { Linha.DeCargo(it) }
    }

    private fun ligarCargo(v: ItemCargoBinding, cargo: Cargo) {
        v.tvNome.text = cargo.nome ?: "—"
        v.tvPessoas.isVisible = cargo.usuarios != null
        cargo.usuarios?.let { v.tvPessoas.text = resources.getQuantityString(R.plurals.eq_pessoas, it, it) }
        // Acesso total já diz tudo; senão, as 3 primeiras permissões e "+n".
        v.grupoPermissoes.removeAllViews()
        if (cargo.acessoTotal) {
            v.grupoPermissoes.addView(selo("🔑 " + getString(R.string.eq_acesso_total), R.color.warning, R.color.warning_bg))
        } else {
            val permissoes = cargo.permissoes.orEmpty()
            permissoes.take(3).forEach { v.grupoPermissoes.addView(selo(it, R.color.ink_soft, R.color.sidebar)) }
            if (permissoes.size > 3) v.grupoPermissoes.addView(selo("+${permissoes.size - 3}", R.color.light_text, R.color.surface))
        }
        v.grupoPermissoes.isVisible = v.grupoPermissoes.childCount > 0
        v.btnAcesso.contentDescription = getString(R.string.eq_acesso_por_tabela_de, cargo.nome)
        v.btnAcesso.setOnClickListener { abrirAcesso(cargo) }
        v.btnEditar.contentDescription = getString(R.string.eq_editar_cargo_de, cargo.nome)
        v.btnEditar.setOnClickListener { abrirCargo(cargo) }
    }

    // --- Convites ---------------------------------------------------------------

    private fun reenviarConvite(convite: Convite, v: ItemConviteBinding) {
        v.btnReenviar.isEnabled = false
        lifecycleScope.launch {
            val r = executarApi(getString(R.string.eq_erro_reenviar)) { api.reenviarConvite(convite.id) }
            v.btnReenviar.isEnabled = true
            when (r) {
                is Resultado.Sucesso -> { avisar(getString(R.string.eq_convite_reenviado, convite.email), longo = true); carregar() }
                is Resultado.Erro -> tratarErro(r)
            }
        }
    }

    private fun cancelarConvite(convite: Convite) {
        confirmar(
            titulo = getString(R.string.eq_cancelar_convite_titulo, convite.email),
            mensagem = getString(R.string.eq_cancelar_convite_msg),
            rotuloConfirmar = getString(R.string.eq_cancelar_convite),
            rotuloCancelar = getString(R.string.eq_manter)
        ) {
            lifecycleScope.launch {
                when (val r = executarApi(getString(R.string.eq_erro_cancelar)) { api.cancelarConvite(convite.id) }) {
                    is Resultado.Sucesso -> { avisar(getString(R.string.eq_convite_cancelado)); carregar() }
                    is Resultado.Erro -> tratarErro(r)
                }
            }
        }
    }

    private fun removerMembro(m: Membro) {
        confirmar(
            titulo = getString(R.string.eq_remover_titulo, m.nome ?: getString(R.string.eq_colaborador)),
            mensagem = getString(R.string.eq_remover_msg),
            rotuloConfirmar = getString(R.string.acao_remover)
        ) {
            lifecycleScope.launch {
                when (val r = executarApi(getString(R.string.eq_erro_remover)) { api.removerMembro(m.id) }) {
                    is Resultado.Sucesso -> { avisar(getString(R.string.eq_removido)); carregar() }
                    is Resultado.Erro -> tratarErro(r)
                }
            }
        }
    }

    private fun tratarErro(r: Resultado.Erro) {
        if (r.sessaoExpirada) redirecionarParaLogin() else avisar(r.mensagem, longo = true)
    }

    // --- Convidar / editar colaborador -------------------------------------------

    private fun abrirMembro(membro: Membro?) {
        val b = SheetMembroBinding.inflate(layoutInflater)
        val editando = membro != null
        // A API não deixa ninguém mudar o próprio cargo (evita alguém se dar mais acesso).
        val ehVoce = editando && membro?.id == authRepository.usuarioLogado()?.id
        // GET /equipe não traz o cargo_id: acha o cargo pelo nome para já vir marcado.
        var cargoId: String? = membro?.cargoNome?.let { nome -> cargos.firstOrNull { it.nome.equals(nome, ignoreCase = true) }?.id }
        val inicial = listOf(membro?.nome.orEmpty(), membro?.email.orEmpty(), cargoId.orEmpty(), "")

        b.tvTitulo.setText(if (editando) R.string.eq_editar_titulo else R.string.eq_convidar_titulo)
        b.tvInfo.isVisible = !editando
        b.campoNome.isVisible = editando
        b.campoMensagem.isVisible = !editando
        b.btnSalvar.setText(if (editando) R.string.eq_salvar_alteracoes else R.string.eq_enviar_convite)
        b.etNome.setText(membro?.nome)
        b.etEmail.setText(membro?.email)
        b.acCargo.configurarSelect(cargos.map { it.id to (it.nome ?: "—") }, cargoId) { cargoId = it; b.campoCargo.error = null }
        b.campoCargo.isEnabled = !ehVoce
        if (ehVoce) b.campoCargo.helperText = getString(R.string.eq_proprio_cargo)
        b.etEmail.doAfterTextChanged { b.campoEmail.error = null }

        fun atual() = listOf(b.etNome.text.toString(), b.etEmail.text.toString(), cargoId.orEmpty(), b.etMensagem.text.toString())
        val folha = Folha(this, b.root) { atual() != inicial }
        b.btnCancelar.setOnClickListener { folha.fechar() }
        b.btnSalvar.setOnClickListener {
            val email = b.etEmail.text.toString().trim()
            val mensagem = b.etMensagem.text.toString()
            b.campoEmail.error = when {
                email.isEmpty() -> getString(R.string.eq_informe_email)
                !Pessoas.emailValido(email) -> getString(R.string.eq_email_invalido)
                else -> null
            }
            b.campoCargo.error = if (cargoId == null && !ehVoce) getString(R.string.eq_selecione_cargo) else null
            b.campoMensagem.error = if (mensagem.length > 500) getString(R.string.eq_mensagem_longa) else null
            if (b.campoEmail.error != null || b.campoCargo.error != null || b.campoMensagem.error != null) return@setOnClickListener

            b.btnSalvar.isEnabled = false
            b.tvErro.isVisible = false
            lifecycleScope.launch {
                val r = if (membro != null) {
                    executarApi(getString(R.string.eq_erro_atualizar)) {
                        api.atualizarMembro(membro.id, AtualizarMembroRequest(
                            nome = b.etNome.text.toString().trim(),
                            email = email,
                            // null não vai no JSON: o próprio cargo nunca é enviado.
                            cargoId = if (ehVoce) null else cargoId
                        ))
                    }
                } else {
                    executarApi(getString(R.string.eq_erro_convidar)) {
                        api.convidar(ConviteRequest(email, cargoId!!, mensagem.trim().ifEmpty { null }))
                    }
                }
                when (r) {
                    is Resultado.Sucesso -> {
                        folha.dispensar()
                        avisar(if (editando) getString(R.string.eq_atualizado) else getString(R.string.eq_convite_enviado, email))
                        carregar()
                    }
                    is Resultado.Erro -> {
                        if (r.sessaoExpirada) return@launch redirecionarParaLogin()
                        // O painel continua aberto com o que foi digitado.
                        b.tvErro.text = r.mensagem
                        b.tvErro.isVisible = true
                        b.btnSalvar.isEnabled = true
                    }
                }
            }
        }
        folha.abrir()
    }

    // --- Cargo ------------------------------------------------------------------

    private fun abrirCargo(cargo: Cargo?) {
        val b = SheetCargoBinding.inflate(layoutInflater)
        val editando = cargo != null
        val inicial = (cargo?.nome.orEmpty()) to (cargo?.permissoes.orEmpty().toSet())
        val marcadas = inicial.second.toMutableSet()

        b.tvTitulo.setText(if (editando) R.string.eq_editar_cargo else R.string.eq_criar_cargo)
        b.btnSalvar.setText(if (editando) R.string.eq_salvar else R.string.eq_criar_cargo)
        b.etNome.setText(cargo?.nome)
        b.etNome.doAfterTextChanged { b.campoNome.error = null }
        PERMISSOES.forEach { permissao ->
            b.llPermissoes.addView(inflar<CheckBox>(R.layout.item_caixa_marcar).apply {
                text = permissao
                isChecked = permissao in marcadas
                setOnCheckedChangeListener { _, marcado -> if (marcado) marcadas += permissao else marcadas -= permissao }
            })
        }

        val folha = Folha(this, b.root) { b.etNome.text.toString() != inicial.first || marcadas != inicial.second }
        b.btnCancelar.setOnClickListener { folha.fechar() }
        b.btnSalvar.setOnClickListener {
            val nome = b.etNome.text.toString().trim()
            if (nome.isEmpty()) {
                b.campoNome.error = getString(R.string.eq_informe_nome_cargo)
                return@setOnClickListener
            }
            // Mesma ordem do catálogo, como o web manda.
            val corpo = CargoRequest(nome, PERMISSOES.filter { it in marcadas })
            b.btnSalvar.isEnabled = false
            b.tvErro.isVisible = false
            lifecycleScope.launch {
                val r = if (cargo != null) executarApi(getString(R.string.eq_erro_atualizar_cargo)) { api.atualizarCargo(cargo.id, corpo) }
                else executarApi(getString(R.string.eq_erro_criar_cargo)) { api.criarCargo(corpo) }
                when (r) {
                    is Resultado.Sucesso -> {
                        folha.dispensar()
                        avisar(getString(if (editando) R.string.eq_cargo_atualizado else R.string.eq_cargo_criado))
                        carregar()
                    }
                    is Resultado.Erro -> {
                        if (r.sessaoExpirada) return@launch redirecionarParaLogin()
                        b.tvErro.text = r.mensagem
                        b.tvErro.isVisible = true
                        b.btnSalvar.isEnabled = true
                    }
                }
            }
        }
        folha.abrir()
    }

    // --- Acesso por tabela ------------------------------------------------------

    private fun abrirAcesso(cargo: Cargo) {
        val b = SheetAcessoBinding.inflate(layoutInflater)
        b.tvCargo.text = cargo.nome
        val folha = Folha(this, b.root)
        b.btnConcluido.setOnClickListener { folha.dispensar() }

        when {
            cargo.acessoTotal -> b.llTabelas.mostrarEstado(getString(R.string.eq_acesso_total_tudo), icone = R.drawable.ic_key)
            modulos.isEmpty() -> b.llTabelas.mostrarEstado(getString(R.string.eq_nenhuma_tabela), icone = R.drawable.ic_inventory)
            else -> {
                b.llTabelas.mostrarCarregando(modulos.size.coerceAtMost(4))
                lifecycleScope.launch {
                    when (val r = chamarApi(getString(R.string.eq_erro_niveis)) { api.niveisDoCargo(cargo.id) }) {
                        is Resultado.Sucesso -> montarNiveis(b, cargo, r.dados.associate { it.moduloId to it.nivel.orEmpty() })
                        is Resultado.Erro -> {
                            tratarErro(r)
                            montarNiveis(b, cargo, emptyMap())
                        }
                    }
                }
            }
        }
        folha.abrir()
    }

    private fun montarNiveis(b: SheetAcessoBinding, cargo: Cargo, niveis: Map<String, String>) {
        val opcoes = listOf(
            "" to getString(R.string.eq_nivel_sem_acesso),
            "visualizar" to getString(R.string.eq_nivel_ver),
            "editar" to getString(R.string.eq_nivel_editar),
            "gerenciar" to getString(R.string.eq_nivel_tudo),
        )
        b.llTabelas.removeAllViews()
        modulos.forEach { modulo ->
            val linha = ItemNivelTabelaBinding.inflate(layoutInflater, b.llTabelas, false)
            linha.tvTabela.text = modulo.nome
            linha.acNivel.configurarSelect(opcoes, niveis[modulo.id].orEmpty()) { nivel ->
                definirNivel(cargo, modulo, nivel)
            }
            b.llTabelas.addView(linha.root)
        }
    }

    private fun definirNivel(cargo: Cargo, modulo: Modulo, nivel: String) = lifecycleScope.launch {
        val r = if (nivel.isEmpty()) {
            executarApi(getString(R.string.eq_erro_remover_nivel)) { api.removerNivel(cargo.id, modulo.id) }
        } else {
            executarApi(getString(R.string.eq_erro_definir_nivel)) { api.definirNivel(cargo.id, modulo.id, NivelRequest(nivel)) }
        }
        if (r is Resultado.Erro) tratarErro(r)
    }

    companion object {
        private const val TIPO_CONVITES = 1
        private const val TIPO_MEMBRO = 2
        private const val TIPO_CARGO = 3
        private const val TIPO_ESTADO = 4

        /**
         * Catálogo de permissões globais. Igual ao GLOBAL_PERMISSIONS do web, que
         * também é mantido à mão em sincronia com PermissaoSeeder.php (não há
         * endpoint GET /permissoes).
         */
        private val PERMISSOES = listOf(
            "criar_modulos",
            "visualizar_equipe",
            "gerenciar_equipe",
            "gerenciar_cargos",
            "exportar_dados",
            "resetar_dados",
            "ver_relatorios",
        )
    }
}
