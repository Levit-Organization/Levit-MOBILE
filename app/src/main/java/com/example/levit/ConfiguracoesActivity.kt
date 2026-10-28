package com.example.levit

import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.text.InputType
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import com.example.levit.data.remote.ApiClient
import com.example.levit.data.remote.dto.Modulo
import com.example.levit.data.repository.ModuloRepository
import com.example.levit.data.repository.Resultado
import com.example.levit.data.repository.ResultadoModulo
import com.example.levit.data.repository.executarApi
import com.example.levit.data.repository.mensagemDeErro
import com.example.levit.databinding.ActivityConfiguracoesBinding
import com.example.levit.databinding.ItemCfgAvancadoBinding
import com.example.levit.databinding.ItemCfgEmpresaBinding
import com.example.levit.databinding.ItemCfgPerfilBinding
import com.example.levit.databinding.ItemCfgTabelaBinding
import com.example.levit.databinding.ItemCfgUploaderBinding
import com.example.levit.databinding.ItemCampoTextoBinding
import com.example.levit.ui.Pessoas
import com.example.levit.ui.Tabelas
import com.example.levit.ui.abrirArquivo
import com.example.levit.ui.avisar
import com.example.levit.ui.caixaDeIcone
import com.example.levit.ui.configurarSelect
import com.example.levit.ui.divisoria
import com.example.levit.ui.mostrarCarregando
import com.example.levit.ui.mostrarEstado
import com.example.levit.ui.inflarFilho
import com.google.android.material.button.MaterialButton
import com.google.android.material.tabs.TabLayout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.ResponseBody
import retrofit2.Response
import java.io.File
import java.io.IOException

/**
 * Configurações (Configuracoes.jsx): Perfil, Empresa e tabelas, Avançado e
 * dados. Como no web, a foto e a logo ficam guardadas só neste aparelho e o
 * perfil/senha ainda não têm endpoint no backend.
 */
class ConfiguracoesActivity : HamburgerMenuBaseActivity() {

    private lateinit var binding: ActivityConfiguracoesBinding
    private val moduloRepository by lazy { ModuloRepository(applicationContext) }
    private val moduloApi by lazy { ApiClient.moduloApi(applicationContext) }
    private val backupApi by lazy { ApiClient.backupApi(applicationContext) }

    private var aba = 0
    private var modulos: List<Modulo>? = null
    private var erroModulos: String? = null
    private var pickerAberto: String? = null
    private var salvandoIcone: String? = null

    private var aoEscolherImagem: ((Uri) -> Unit)? = null
    private val escolherImagem = registerForActivityResult(ActivityResultContracts.GetContent()) { uri -> uri?.let { aoEscolherImagem?.invoke(it) } }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityConfiguracoesBinding.inflate(layoutInflater)
        setContentViewWithHamburgerMenu(binding.root)
        if (!menuPronto()) return
        setupHamburgerMenuButton(R.id.ivMenu)
        setupSino(binding.flSino)
        listOf(R.string.cfg_aba_perfil, R.string.cfg_aba_empresa, R.string.cfg_aba_avancado).forEach {
            binding.abas.addTab(binding.abas.newTab().setText(it))
        }
        binding.abas.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) { aba = tab.position; renderizar() }
            override fun onTabUnselected(tab: TabLayout.Tab) = Unit
            override fun onTabReselected(tab: TabLayout.Tab) = Unit
        })
        renderizar()
        carregarModulos()
    }

    private fun carregarModulos() = lifecycleScope.launch {
        when (val r = moduloRepository.listar()) {
            is ResultadoModulo.Sucesso -> { modulos = r.dados; erroModulos = null }
            is ResultadoModulo.Erro -> { if (r.sessaoExpirada) return@launch redirecionarParaLogin(); erroModulos = r.mensagem }
        }
        if (aba != 0) renderizar()
    }

    private fun renderizar() {
        val c = binding.llConteudo
        c.removeAllViews()
        when (aba) {
            0 -> abaPerfil(c)
            1 -> abaEmpresa(c)
            else -> abaAvancado(c)
        }
    }

    // --- Peças ------------------------------------------------------------------------

    private fun campo(c: ItemCampoTextoBinding, rotulo: String, valor: String?, placeholder: String? = null, dica: String? = null,
                      tipo: Int = InputType.TYPE_CLASS_TEXT, aoMudar: (String) -> Unit = {}): ItemCampoTextoBinding {
        c.campo.hint = rotulo
        placeholder?.let { c.campo.placeholderText = it }
        dica?.let { c.campo.helperText = it }
        c.etValor.inputType = tipo
        c.etValor.setText(valor)
        c.etValor.doAfterTextChanged { aoMudar(it?.toString().orEmpty()) }
        return c
    }

    /** Arquivo local da foto/logo (avatarStorage.js do web guardava no navegador). */
    private fun arquivoDeImagem(chave: String) = File(File(filesDir, "imagens"), "$chave.img")

    /** ImageUploader: círculo (foto, com as iniciais) ou quadrado (logo, com o ícone), com o botão de câmera. */
    private fun uploader(u: ItemCfgUploaderBinding, chave: String, iniciais: String?, aoErro: (String) -> Unit, aoMudar: () -> Unit) {
        val quadrado = iniciais == null
        if (quadrado) u.caixa.setBackgroundResource(R.drawable.bg_uploader_quadrado)
        u.caixa.clipToOutline = true
        val arquivo = arquivoDeImagem(chave)
        if (arquivo.exists()) {
            u.ivImagem.setImageBitmap(BitmapFactory.decodeFile(arquivo.path, BitmapFactory.Options().apply { inSampleSize = 2 }))
            u.ivImagem.isVisible = true
        } else if (quadrado) u.tvIcone.isVisible = true
        else {
            u.tvIniciais.text = iniciais
            u.tvIniciais.isVisible = true
        }
        u.btnCamera.setOnClickListener {
            aoEscolherImagem = { uri ->
                val tipo = contentResolver.getType(uri).orEmpty()
                val tamanho = contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { c -> if (c.moveToFirst()) c.getLong(0) else 0L } ?: 0L
                when {
                    !tipo.startsWith("image/") -> aoErro(getString(R.string.cfg_escolha_imagem))
                    tamanho > TAMANHO_MAXIMO_IMAGEM -> aoErro(getString(R.string.cfg_imagem_grande))
                    else -> lifecycleScope.launch {
                        withContext(Dispatchers.IO) {
                            arquivo.parentFile?.mkdirs()
                            contentResolver.openInputStream(uri)?.use { entrada -> arquivo.outputStream().use { entrada.copyTo(it) } }
                        }
                        aoMudar()
                    }
                }
            }
            escolherImagem.launch("image/*")
        }
    }

    // --- Perfil -------------------------------------------------------------------------

    private fun abaPerfil(destino: LinearLayout) {
        val b = ItemCfgPerfilBinding.inflate(layoutInflater, destino, true)
        val usuario = authRepository.usuarioLogado()
        val chave = "usuario_${usuario?.id}"
        uploader(b.uploader, chave, Pessoas.iniciais(usuario?.nome),
            aoErro = { b.tvErroImagem.text = it; b.tvErroImagem.visibility = View.VISIBLE },
            aoMudar = { renderizar() })
        b.tvRemover.isVisible = arquivoDeImagem(chave).exists()
        b.tvRemover.setOnClickListener { arquivoDeImagem(chave).delete(); renderizar() }
        campo(b.campoNome, getString(R.string.cfg_nome), usuario?.nome, tipo = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PERSON_NAME)
        campo(b.campoEmail, getString(R.string.cfg_email), usuario?.email, tipo = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS)
        val telefone = campo(b.campoTelefone, getString(R.string.cfg_telefone), "", getString(R.string.cfg_telefone_ex), tipo = InputType.TYPE_CLASS_PHONE)
        telefone.etValor.doAfterTextChanged { t ->
            val atual = t?.toString().orEmpty()
            val mascarado = Pessoas.mascararTelefone(atual)
            if (mascarado != atual) { telefone.etValor.setText(mascarado); telefone.etValor.setSelection(mascarado.length) }
        }
        campo(b.campoLinkedin, getString(R.string.cfg_linkedin), "", getString(R.string.cfg_linkedin_ex))
        campo(b.campoBio, getString(R.string.cfg_bio), "", getString(R.string.cfg_bio_ex),
            tipo = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES).etValor.minLines = 3
        b.btnSalvar.setOnClickListener { b.tvAvisoPerfil.visibility = View.VISIBLE }

        val senhaTipo = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        val atual = campo(b.campoSenhaAtual, getString(R.string.cfg_senha_atual), "", tipo = senhaTipo)
        val nova = campo(b.campoNovaSenha, getString(R.string.cfg_nova_senha), "", dica = getString(R.string.cfg_minimo_8), tipo = senhaTipo)
        val confirmar = campo(b.campoConfirmar, getString(R.string.cfg_confirmar_senha), "", tipo = senhaTipo)
        b.btnAtualizarSenha.setOnClickListener {
            b.tvErroSenha.visibility = View.GONE
            b.tvAvisoSenha.visibility = View.GONE
            val n = nova.etValor.text.toString()
            val erro = when {
                n.length < 8 -> getString(R.string.cfg_senha_curta)
                n != confirmar.etValor.text.toString() -> getString(R.string.cfg_senha_diferente)
                else -> null
            }
            if (erro != null) { b.tvErroSenha.text = erro; b.tvErroSenha.visibility = View.VISIBLE; return@setOnClickListener }
            b.tvAvisoSenha.visibility = View.VISIBLE
            listOf(atual, nova, confirmar).forEach { it.etValor.setText("") }
        }
    }

    // --- Empresa e tabelas ------------------------------------------------------------------

    private fun abaEmpresa(destino: LinearLayout) {
        val b = ItemCfgEmpresaBinding.inflate(layoutInflater, destino, true)
        val empresa = authRepository.empresaLogada()
        val chave = "logo_${empresa?.id}"
        uploader(b.uploader, chave, iniciais = null,
            aoErro = { b.tvErroLogo.text = it; b.tvErroLogo.visibility = View.VISIBLE },
            aoMudar = { renderizar() })
        b.tvRemover.isVisible = arquivoDeImagem(chave).exists()
        b.tvRemover.setOnClickListener { arquivoDeImagem(chave).delete(); renderizar() }
        b.tvNomeEmpresa.text = empresa?.nome ?: getString(R.string.cfg_carregando)

        val lista = modulos
        val corpo = b.llTabelas
        when {
            lista == null && erroModulos != null -> corpo.mostrarEstado(getString(R.string.tab_erro_carregar), erroModulos,
                R.drawable.ic_warning, getString(R.string.tab_tentar), erro = true) { carregarModulos() }
            lista == null -> corpo.mostrarCarregando(3)
            lista.isEmpty() -> corpo.mostrarEstado(getString(R.string.cfg_nenhuma_tabela), getString(R.string.cfg_nenhuma_tabela_desc), R.drawable.ic_grid_view)
            else -> lista.forEachIndexed { i, m ->
                if (i > 0) corpo.addView(divisoria())
                corpo.addView(linhaDeTabela(m, corpo))
            }
        }
    }

    private fun linhaDeTabela(m: Modulo, pai: LinearLayout): View {
        val b = ItemCfgTabelaBinding.inflate(layoutInflater, pai, false)
        b.linha.addView(caixaDeIcone(m.icone, ladoDp = 36, iconeDp = 18), 0)
        b.tvNome.text = m.nome
        val aberto = pickerAberto == m.id
        b.tvAcao.setText(when {
            salvandoIcone == m.id -> R.string.cfg_salvando
            aberto -> R.string.tab_fechar
            else -> R.string.tab_alterar_icone
        })
        b.tvAcao.isEnabled = salvandoIcone != m.id
        b.tvAcao.setOnClickListener { pickerAberto = if (aberto) null else m.id; renderizar() }
        if (aberto) {
            b.grade.isVisible = true
            Tabelas.ICONES.forEach { icone ->
                b.grade.addView(b.grade.inflarFilho<TextView>(R.layout.item_icone_cfg).apply {
                    text = icone
                    isSelected = m.icone == icone
                    contentDescription = getString(R.string.cfg_usar_icone, icone)
                    setOnClickListener { escolherIcone(m, icone) }
                })
            }
        }
        return b.root
    }

    /** Otimista: troca na tela e volta se a API recusar. */
    private fun escolherIcone(m: Modulo, icone: String) {
        pickerAberto = null
        if (icone == m.icone) return renderizar()
        val anterior = m.icone
        salvandoIcone = m.id
        modulos = modulos?.map { if (it.id == m.id) it.copy(icone = icone) else it }
        renderizar()
        lifecycleScope.launch {
            val r = executarApi(getString(R.string.cfg_erro_icone)) { moduloApi.atualizarCampos(m.id, mapOf("icone" to icone)) }
            salvandoIcone = null
            if (r is Resultado.Erro) {
                if (r.sessaoExpirada) return@launch redirecionarParaLogin()
                modulos = modulos?.map { if (it.id == m.id) it.copy(icone = anterior) else it }
                avisar(r.mensagem, longo = true)
            }
            renderizar()
        }
    }

    // --- Avançado e dados -------------------------------------------------------------------

    private var tabelaCsv = ""

    private fun abaAvancado(destino: LinearLayout) {
        val b = ItemCfgAvancadoBinding.inflate(layoutInflater, destino, true)
        val erro = b.tvErro
        b.btnJson.setOnClickListener {
            exportar(b.btnJson, getString(R.string.cfg_exportar_json), erro, "backup_levit_${System.currentTimeMillis()}.json",
                "application/json", getString(R.string.cfg_json_ok)) { backupApi.exportarJson() }
        }
        val s = b.selectTabela
        s.campo.hint = getString(R.string.cfg_tabela_exportar)
        s.campo.placeholderText = getString(R.string.aut_selecione_tabela)
        s.acValor.configurarSelect(modulos.orEmpty().map { it.id to it.nome }, tabelaCsv) { tabelaCsv = it; b.btnCsv.isEnabled = true }
        b.btnCsv.setOnClickListener {
            val nome = modulos?.firstOrNull { it.id == tabelaCsv }?.nome ?: "modulo"
            exportar(b.btnCsv, getString(R.string.cfg_exportar_csv), erro, "${nome}_${System.currentTimeMillis()}.csv",
                "text/csv", getString(R.string.cfg_csv_ok)) { backupApi.exportarCsv(tabelaCsv) }
        }
        b.btnCsv.isEnabled = tabelaCsv.isNotEmpty()
    }

    /** Baixa e entrega o arquivo; o nome vem do Content-Disposition, como no web. */
    private fun exportar(btn: MaterialButton, rotulo: String, erro: TextView, nomePadrao: String, mime: String, sucesso: String,
                         requisicao: suspend () -> Response<ResponseBody>) {
        erro.visibility = View.GONE
        btn.isEnabled = false
        btn.text = getString(R.string.cfg_exportando)
        lifecycleScope.launch {
            try {
                val r = requisicao()
                if (r.isSuccessful) {
                    val bytes = withContext(Dispatchers.IO) { r.body()?.bytes() } ?: ByteArray(0)
                    val nome = Regex("filename=\"?([^\";]+)\"?").find(r.headers()["Content-Disposition"].orEmpty())?.groupValues?.get(1) ?: nomePadrao
                    abrirArquivo(bytes, nome, mime)
                    avisar(sucesso)
                } else {
                    if (r.code() == 401) return@launch redirecionarParaLogin()
                    erro.text = mensagemDeErro(r.errorBody()?.string(), getString(R.string.cfg_erro_exportar))
                    erro.visibility = View.VISIBLE
                }
            } catch (e: IOException) {
                erro.text = getString(R.string.cfg_erro_exportar)
                erro.visibility = View.VISIBLE
            }
            btn.isEnabled = true
            btn.text = rotulo
        }
    }

    companion object {
        private const val TAMANHO_MAXIMO_IMAGEM = 2L * 1024 * 1024
    }
}
