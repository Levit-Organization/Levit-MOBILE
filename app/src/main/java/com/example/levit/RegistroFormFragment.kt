package com.example.levit

import android.os.Bundle
import android.text.InputType
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.example.levit.data.remote.dto.CampoModulo
import com.example.levit.data.remote.dto.Registro
import com.example.levit.data.remote.dto.formatarValorRegistro
import com.example.levit.data.repository.RegistroRepository
import com.example.levit.data.repository.ResultadoApi
import com.example.levit.databinding.FragmentRegistroFormBinding
import com.example.levit.databinding.ItemCampoInputBinding
import com.example.levit.databinding.ItemCampoSelecaoBinding
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.textfield.TextInputLayout
import com.google.gson.Gson
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/** Criar (registroJson nulo) ou editar um registro, com campos montados pelo tipo. */
class RegistroFormFragment : Fragment(R.layout.fragment_registro_form) {

    private class LeitorCampo(
        val campo: CampoModulo,
        val layout: TextInputLayout,
        val ler: () -> String?
    )

    private var _binding: FragmentRegistroFormBinding? = null
    private val binding get() = _binding!!

    private val repositorio by lazy { RegistroRepository(requireContext().applicationContext) }
    private val moduloId: String get() = requireArguments().getString("moduloId").orEmpty()
    private val registroEdicao: Registro? by lazy {
        arguments?.getString("registroJson")?.let { Gson().fromJson(it, Registro::class.java) }
    }

    private var campos: List<CampoModulo> = emptyList()
    private val leitores = mutableListOf<LeitorCampo>()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentRegistroFormBinding.bind(view)

        binding.tvTituloRegistroForm.setText(
            if (registroEdicao == null) R.string.registro_form_titulo_novo else R.string.registro_form_titulo_editar
        )
        binding.ivVoltar.setOnClickListener { findNavController().popBackStack() }
        binding.btnSalvarRegistro.setOnClickListener { salvar() }

        carregar()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun carregar() {
        binding.progressRegistroForm.visibility = View.VISIBLE
        binding.btnSalvarRegistro.isEnabled = false

        viewLifecycleOwner.lifecycleScope.launch {
            val modulo = when (val res = repositorio.detalhesDoModulo(moduloId)) {
                is ResultadoApi.Sucesso -> res.dados
                is ResultadoApi.Erro -> {
                    falhar(res.mensagem, res.sessaoExpirada)
                    return@launch
                }
            }
            binding.progressRegistroForm.visibility = View.GONE
            campos = modulo.camposOrdenados

            if (campos.isEmpty()) {
                binding.tvAvisoRegistroForm.visibility = View.VISIBLE
                return@launch
            }
            montarCampos()
            binding.btnSalvarRegistro.isEnabled = true
        }
    }

    private fun montarCampos() {
        val container = binding.containerCamposForm
        container.removeAllViews()
        leitores.clear()

        val atuais = registroEdicao?.valores ?: emptyMap()
        campos.forEach { campo ->
            val atual = atuais[campo.id]
            when (campo.tipo) {
                "selecao" -> montarSelecao(container, campo, atual)
                "data" -> montarData(container, campo, atual)
                "numero" -> montarInput(
                    container, campo, atual,
                    InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL or InputType.TYPE_NUMBER_FLAG_SIGNED
                )
                else -> montarInput(
                    container, campo, atual,
                    InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
                )
            }
        }
    }

    private fun montarInput(container: ViewGroup, campo: CampoModulo, atual: Any?, tipoTeclado: Int) {
        val b = ItemCampoInputBinding.inflate(layoutInflater, container, false)
        b.layoutCampo.hint = campo.nome
        b.etCampo.inputType = tipoTeclado
        b.etCampo.setText(formatarValorRegistro(atual))
        container.addView(b.root)
        leitores += LeitorCampo(campo, b.layoutCampo) { b.etCampo.text.toString().trim() }
    }

    private fun montarData(container: ViewGroup, campo: CampoModulo, atual: Any?) {
        val b = ItemCampoInputBinding.inflate(layoutInflater, container, false)
        b.layoutCampo.hint = campo.nome

        var iso: String? = (atual as? String)?.let { Regex("^\\d{4}-\\d{2}-\\d{2}").find(it)?.value }
        fun mostrar() = b.etCampo.setText(iso?.let { paraExibicao(it) }.orEmpty())
        mostrar()

        b.etCampo.isFocusable = false
        b.etCampo.isClickable = true
        b.layoutCampo.endIconMode = TextInputLayout.END_ICON_CUSTOM
        b.layoutCampo.setEndIconDrawable(R.drawable.ic_close)
        b.layoutCampo.endIconContentDescription = getString(R.string.registro_limpar_data)
        b.layoutCampo.setEndIconOnClickListener {
            iso = null
            mostrar()
        }
        b.etCampo.setOnClickListener {
            abrirSeletorData(iso) { novo ->
                iso = novo
                mostrar()
            }
        }

        container.addView(b.root)
        leitores += LeitorCampo(campo, b.layoutCampo) { iso }
    }

    private fun montarSelecao(container: ViewGroup, campo: CampoModulo, atual: Any?) {
        val b = ItemCampoSelecaoBinding.inflate(layoutInflater, container, false)
        b.layoutCampo.hint = campo.nome

        val opcoes = campo.opcoes ?: emptyList()
        val rotulos = listOf(getString(R.string.registro_nenhum)) + opcoes
        b.actCampo.setAdapter(ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, rotulos))

        var escolhida: String? = (atual as? String)?.takeIf { it in opcoes }
        escolhida?.let { b.actCampo.setText(it, false) }
        b.actCampo.setOnItemClickListener { _, _, posicao, _ ->
            escolhida = if (posicao == 0) null else opcoes[posicao - 1]
            if (posicao == 0) b.actCampo.setText("", false)
        }

        container.addView(b.root)
        leitores += LeitorCampo(campo, b.layoutCampo) { escolhida }
    }

    private fun abrirSeletorData(isoAtual: String?, aoEscolher: (String) -> Unit) {
        val seletor = MaterialDatePicker.Builder.datePicker()
            .setTitleText(R.string.registro_escolher_data)
            .apply { isoAtual?.let { runCatching { formatoIso().parse(it)?.time }.getOrNull() }?.let(::setSelection) }
            .build()
        seletor.addOnPositiveButtonClickListener { milissegundos ->
            aoEscolher(formatoIso().format(milissegundos))
        }
        seletor.show(childFragmentManager, "seletor_data")
    }

    private fun salvar() {
        val dados = mutableMapOf<String, Any?>()
        var erro = false

        leitores.forEach { leitor ->
            leitor.layout.error = null
            val bruto = leitor.ler()?.takeIf { it.isNotEmpty() } ?: return@forEach
            if (leitor.campo.tipo == "numero") {
                val numero = bruto.toDoubleOrNull()
                if (numero == null) {
                    leitor.layout.error = getString(R.string.registro_erro_numero)
                    erro = true
                    return@forEach
                }
                dados[leitor.campo.id] = if (numero % 1.0 == 0.0) numero.toLong() else numero
            } else {
                dados[leitor.campo.id] = bruto
            }
        }
        if (erro) return

        val edicao = registroEdicao
        if (edicao == null && dados.isEmpty()) {
            Toast.makeText(requireContext(), R.string.registro_erro_vazio, Toast.LENGTH_SHORT).show()
            return
        }

        binding.btnSalvarRegistro.isEnabled = false
        binding.progressRegistroForm.visibility = View.VISIBLE

        viewLifecycleOwner.lifecycleScope.launch {
            // O PUT substitui o registro inteiro: "dados" leva todos os valores preenchidos.
            val resultado = if (edicao == null) {
                repositorio.criar(moduloId, dados)
            } else {
                repositorio.atualizar(moduloId, edicao.id, dados)
            }
            when (resultado) {
                is ResultadoApi.Sucesso -> {
                    Toast.makeText(requireContext(), R.string.registro_salvo, Toast.LENGTH_SHORT).show()
                    findNavController().popBackStack()
                }
                is ResultadoApi.Erro -> {
                    binding.progressRegistroForm.visibility = View.GONE
                    binding.btnSalvarRegistro.isEnabled = true
                    if (resultado.sessaoExpirada) {
                        findNavController().navigate(R.id.action_global_to_login)
                    } else {
                        // Cobre 403 e 422: a mensagem já vem pronta do backend.
                        Toast.makeText(requireContext(), resultado.mensagem, Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    // Falha ao carregar o módulo: não dá para montar o formulário, então sai.
    private fun falhar(mensagem: String, sessaoExpirada: Boolean) {
        binding.progressRegistroForm.visibility = View.GONE
        if (sessaoExpirada) {
            findNavController().navigate(R.id.action_global_to_login)
        } else {
            Toast.makeText(requireContext(), mensagem, Toast.LENGTH_LONG).show()
            findNavController().popBackStack()
        }
    }

    // O seletor de datas do Material trabalha em UTC, então os formatos também.
    private fun formatoIso() = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    private fun paraExibicao(iso: String): String = runCatching {
        SimpleDateFormat("dd/MM/yyyy", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
            .format(formatoIso().parse(iso)!!)
    }.getOrDefault(iso)
}