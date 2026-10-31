package com.example.levit

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.example.levit.data.remote.dto.Modulo
import com.example.levit.data.repository.CargoRepository
import com.example.levit.data.repository.ModuloRepository
import com.example.levit.data.repository.ResultadoApi
import com.example.levit.data.repository.ResultadoModulo
import com.example.levit.databinding.FragmentCargoFormBinding
import com.google.android.material.materialswitch.MaterialSwitch
import kotlinx.coroutines.launch

/** Criar (cargoId nulo) ou editar (cargoId preenchido) um cargo. */
class CargoFormFragment : Fragment(R.layout.fragment_cargo_form) {

    private var _binding: FragmentCargoFormBinding? = null
    private val binding get() = _binding!!

    private val cargoRepository by lazy { CargoRepository(requireContext().applicationContext) }
    private val moduloRepository by lazy { ModuloRepository(requireContext().applicationContext) }

    private val cargoId: String? get() = arguments?.getString("cargoId")

    private val permissoesDisponiveis = listOf(
        "criar_modulos" to R.string.equipe_perm_criar_modulos,
        "visualizar_equipe" to R.string.equipe_perm_visualizar_equipe,
        "gerenciar_equipe" to R.string.equipe_perm_gerenciar_equipe,
        "gerenciar_cargos" to R.string.equipe_perm_gerenciar_cargos,
        "exportar_dados" to R.string.equipe_perm_exportar_dados,
        "resetar_dados" to R.string.equipe_perm_resetar_dados,
        "ver_relatorios" to R.string.equipe_perm_ver_relatorios
    )

    private var acessoTotal = false
    private var modulos: List<Modulo> = emptyList()
    private val permissoesMarcadas = mutableSetOf<String>()
    private var niveisOriginais: Map<String, String> = emptyMap()
    private var niveisAtuais: MutableMap<String, String?> = HashMap()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentCargoFormBinding.bind(view)

        binding.tvTituloCargoForm.setText(
            if (cargoId == null) R.string.cargo_form_titulo_novo else R.string.cargo_form_titulo_editar
        )
        binding.ivVoltar.setOnClickListener { findNavController().popBackStack() }
        binding.btnSalvarCargo.setOnClickListener { salvar() }

        carregar()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun carregar() {
        binding.progressCargoForm.visibility = View.VISIBLE
        binding.btnSalvarCargo.isEnabled = false
        permissoesMarcadas.clear()
        niveisOriginais = emptyMap()

        viewLifecycleOwner.lifecycleScope.launch {
            when (val res = moduloRepository.listar()) {
                is ResultadoModulo.Sucesso -> modulos = res.dados
                is ResultadoModulo.Erro -> {
                    falhar(res.mensagem, res.sessaoExpirada)
                    return@launch
                }
            }

            val id = cargoId
            if (id != null) {
                val cargo = when (val res = cargoRepository.listarCargos()) {
                    is ResultadoApi.Sucesso -> res.dados.firstOrNull { it.id == id }
                    is ResultadoApi.Erro -> {
                        falhar(res.mensagem, res.sessaoExpirada)
                        return@launch
                    }
                }
                if (cargo == null) {
                    falhar(getString(R.string.cargo_nao_encontrado), false)
                    return@launch
                }
                binding.etNomeCargo.setText(cargo.nome)
                permissoesMarcadas.addAll(cargo.permissoesOuVazio)
                acessoTotal = cargo.acessoTotal

                // Cargo com acesso total ignora níveis por módulo, então nem busca.
                if (!acessoTotal) {
                    when (val res = cargoRepository.listarNiveis(id)) {
                        is ResultadoApi.Sucesso ->
                            niveisOriginais = res.dados.associate { it.moduloId to it.nivel }
                        is ResultadoApi.Erro -> {
                            falhar(res.mensagem, res.sessaoExpirada)
                            return@launch
                        }
                    }
                }
            }

            niveisAtuais = HashMap<String, String?>(niveisOriginais)
            montarTela()
        }
    }

    private fun montarTela() {
        binding.progressCargoForm.visibility = View.GONE
        binding.btnSalvarCargo.isEnabled = true

        val container = binding.containerPermissoes
        container.removeAllViews() // tira a linha de exemplo do Design
        permissoesDisponiveis.forEach { (codigo, rotuloRes) ->
            val linha = layoutInflater.inflate(R.layout.item_permissao, container, false) as MaterialSwitch
            linha.setText(rotuloRes)
            linha.isChecked = codigo in permissoesMarcadas
            linha.setOnCheckedChangeListener { _, marcado ->
                if (marcado) permissoesMarcadas.add(codigo) else permissoesMarcadas.remove(codigo)
            }
            container.addView(linha)
        }

        when {
            acessoTotal -> {
                binding.tvAvisoModulos.setText(R.string.cargo_aviso_acesso_total)
                binding.tvAvisoModulos.visibility = View.VISIBLE
                binding.rvNiveisModulo.visibility = View.GONE
            }
            modulos.isEmpty() -> {
                binding.tvAvisoModulos.setText(R.string.cargo_aviso_sem_modulos)
                binding.tvAvisoModulos.visibility = View.VISIBLE
                binding.rvNiveisModulo.visibility = View.GONE
            }
            else -> {
                binding.tvAvisoModulos.visibility = View.GONE
                binding.rvNiveisModulo.visibility = View.VISIBLE
                binding.rvNiveisModulo.adapter = NiveisModuloAdapter(modulos, niveisAtuais) { moduloId, nivel ->
                    niveisAtuais[moduloId] = nivel
                }
            }
        }
    }

    private fun salvar() {
        binding.layoutNomeCargo.error = null
        val nome = binding.etNomeCargo.text.toString().trim()
        if (nome.isEmpty()) {
            binding.layoutNomeCargo.error = getString(R.string.cargo_erro_nome)
            return
        }

        // Sempre reenvia as permissões atuais: o PUT do cargo substitui tudo.
        val permissoes = permissoesMarcadas.toList()

        // Só o que mudou nos módulos (null = remover o acesso).
        val alteracoes: Map<String, String?> = if (acessoTotal) emptyMap() else {
            modulos.mapNotNull { m ->
                val atual = niveisAtuais[m.id]
                if (atual != niveisOriginais[m.id]) m.id to atual else null
            }.toMap()
        }

        binding.btnSalvarCargo.isEnabled = false
        binding.progressCargoForm.visibility = View.VISIBLE

        viewLifecycleOwner.lifecycleScope.launch {
            val id = cargoId
            val resCargo = if (id == null) {
                cargoRepository.criarCargo(nome, permissoes)
            } else {
                cargoRepository.atualizarCargo(id, nome, permissoes)
            }
            val cargo = when (resCargo) {
                is ResultadoApi.Sucesso -> resCargo.dados
                is ResultadoApi.Erro -> {
                    erroAoSalvar(resCargo.mensagem, resCargo.sessaoExpirada)
                    return@launch
                }
            }

            if (alteracoes.isNotEmpty()) {
                val resNiveis = cargoRepository.aplicarNiveis(cargo.id, alteracoes)
                if (resNiveis is ResultadoApi.Erro) {
                    if (resNiveis.sessaoExpirada) {
                        erroAoSalvar(resNiveis.mensagem, true)
                    } else {
                        // O cargo já foi salvo; só os níveis falharam. Volta e avisa.
                        Toast.makeText(
                            requireContext(),
                            getString(R.string.cargo_salvo_parcial, resNiveis.mensagem),
                            Toast.LENGTH_LONG
                        ).show()
                        findNavController().popBackStack()
                    }
                    return@launch
                }
            }

            Toast.makeText(requireContext(), R.string.cargo_salvo, Toast.LENGTH_SHORT).show()
            findNavController().popBackStack()
        }
    }

    private fun erroAoSalvar(mensagem: String, sessaoExpirada: Boolean) {
        binding.progressCargoForm.visibility = View.GONE
        binding.btnSalvarCargo.isEnabled = true
        if (sessaoExpirada) {
            findNavController().navigate(R.id.action_global_to_login)
        } else {
            Toast.makeText(requireContext(), mensagem, Toast.LENGTH_LONG).show()
        }
    }

    // Falha ao carregar: não dá para editar nada, então sai da tela.
    private fun falhar(mensagem: String, sessaoExpirada: Boolean) {
        binding.progressCargoForm.visibility = View.GONE
        if (sessaoExpirada) {
            findNavController().navigate(R.id.action_global_to_login)
        } else {
            Toast.makeText(requireContext(), mensagem, Toast.LENGTH_LONG).show()
            findNavController().popBackStack()
        }
    }
}