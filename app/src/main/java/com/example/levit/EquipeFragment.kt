package com.example.levit

import android.os.Bundle
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.RecyclerView
import com.example.levit.data.remote.dto.Cargo
import com.example.levit.data.remote.dto.Membro
import com.example.levit.data.repository.AuthRepository
import com.example.levit.data.repository.CargoRepository
import com.example.levit.data.repository.EquipeRepository
import com.example.levit.data.repository.ResultadoApi
import com.google.android.material.tabs.TabLayout
import kotlinx.coroutines.launch
import android.content.DialogInterface
import android.util.Patterns
import android.widget.ArrayAdapter
import com.example.levit.databinding.DialogMembroBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import androidx.core.os.bundleOf

class EquipeFragment : HamburgerMenuBaseFragment() {

    override val pageLayoutResId: Int = R.layout.fragment_equipe

    private val equipeRepository by lazy { EquipeRepository(requireContext().applicationContext) }
    private val cargoRepository by lazy { CargoRepository(requireContext().applicationContext) }

    private val adapter = MembrosAdapter(
        aoEditar = { abrirDialogoMembro(it) },
        aoRemover = { confirmarRemocao(it) }
    )

    private var cargos: List<Cargo> = emptyList()

    private var membros: List<Membro> = emptyList()

    private val cargosAdapter = CargosAdapter(
        aoEditar = { abrirFormCargo(it.id) }
    )

    private var abaAtual = 0

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        if (!menuFoiMontado()) return

        setupHamburgerMenuButton(R.id.ivMenu)

        val nome = AuthRepository(requireContext().applicationContext).usuarioLogado()?.nome
        view.findViewById<TextView>(R.id.tvAvatar).text = iniciais(nome)

        view.findViewById<RecyclerView>(R.id.rvMembros).adapter = adapter

        view.findViewById<RecyclerView>(R.id.rvCargos).adapter = cargosAdapter

        view.findViewById<TabLayout>(R.id.tabsEquipe)
            .addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
                override fun onTabSelected(tab: TabLayout.Tab) {
                    abaAtual = tab.position
                    mostrarAba(view, tab.position)
                }
                override fun onTabUnselected(tab: TabLayout.Tab) {}
                override fun onTabReselected(tab: TabLayout.Tab) {}
            })

        if (abaAtual != 0) {
            view.findViewById<TabLayout>(R.id.tabsEquipe).getTabAt(abaAtual)?.select()
        }

        view.findViewById<View>(R.id.btnAdicionarMembro).setOnClickListener { abrirDialogoMembro(null) }
        view.findViewById<View>(R.id.btnCriarCargo).setOnClickListener { abrirFormCargo(null) }

        carregar(view)
    }

    private fun mostrarAba(view: View, posicao: Int) {
        view.findViewById<View>(R.id.secaoMembros).visibility =
            if (posicao == 0) View.VISIBLE else View.GONE
        view.findViewById<View>(R.id.secaoCargos).visibility =
            if (posicao == 1) View.VISIBLE else View.GONE
    }

    private fun carregar(view: View) {
        val progress = view.findViewById<ProgressBar>(R.id.progressEquipe)
        progress.visibility = View.VISIBLE

        viewLifecycleOwner.lifecycleScope.launch {
            val resMembros = equipeRepository.listarMembros()
            // Cargos exigem outra permissão: se vier 403, a aba de membros segue normal.
            val resCargos = cargoRepository.listarCargos()
            progress.visibility = View.GONE

            if ((resMembros is ResultadoApi.Erro && resMembros.sessaoExpirada) ||
                (resCargos is ResultadoApi.Erro && resCargos.sessaoExpirada)
            ) {
                findNavController().navigate(R.id.action_global_to_login)
                return@launch
            }

            if (resCargos is ResultadoApi.Sucesso) cargos = resCargos.dados

            val msgMembros = view.findViewById<TextView>(R.id.tvMembrosMensagem)
            when (resMembros) {
                is ResultadoApi.Sucesso -> {
                    membros = resMembros.dados
                    mostrarMembros(view, msgMembros, resMembros.dados)
                }
                is ResultadoApi.Erro -> {
                    adapter.submitList(emptyList())
                    msgMembros.text = resMembros.mensagem
                    msgMembros.visibility = View.VISIBLE
                    if (resMembros.semPermissao) {
                        view.findViewById<View>(R.id.btnAdicionarMembro).visibility = View.GONE
                    }
                }
            }

            mostrarCargos(view, resCargos)
        }
    }

    private fun mostrarCargos(view: View, resultado: ResultadoApi<List<Cargo>>) {
        val mensagem = view.findViewById<TextView>(R.id.tvCargosMensagem)
        when (resultado) {
            is ResultadoApi.Sucesso -> {
                cargosAdapter.submitList(
                    cargos.map { c -> CargoItem(c, membros.count { it.cargoId == c.id }) }
                )
                if (cargos.isEmpty()) {
                    mensagem.setText(R.string.equipe_cargos_vazio)
                    mensagem.visibility = View.VISIBLE
                } else {
                    mensagem.visibility = View.GONE
                }
            }
            is ResultadoApi.Erro -> {
                cargosAdapter.submitList(emptyList())
                mensagem.text = resultado.mensagem
                mensagem.visibility = View.VISIBLE
                if (resultado.semPermissao) {
                    view.findViewById<View>(R.id.btnCriarCargo).visibility = View.GONE
                }
            }
        }
    }
    private fun mostrarMembros(view: View, mensagem: TextView, lista: List<Membro>) {
        adapter.submitList(lista)
        if (lista.isEmpty()) {
            mensagem.setText(R.string.equipe_vazio_membros)
            mensagem.visibility = View.VISIBLE
        } else {
            mensagem.visibility = View.GONE
        }
    }

    // membro == null: convidar. membro != null: editar.
    private fun abrirDialogoMembro(membro: Membro?) {
        if (cargos.isEmpty()) {
            Toast.makeText(requireContext(), R.string.equipe_sem_cargos, Toast.LENGTH_LONG).show()
            return
        }
        val editando = membro != null
        val b = DialogMembroBinding.inflate(layoutInflater)

        b.layoutNomeMembro.visibility = if (editando) View.VISIBLE else View.GONE
        b.layoutEmailMembro.visibility = if (editando) View.GONE else View.VISIBLE
        b.tvAvisoConvite.visibility = if (editando) View.GONE else View.VISIBLE
        if (membro != null) b.etNomeMembro.setText(membro.nome)

        b.actCargoMembro.setAdapter(
            ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, cargos.map { it.nome })
        )
        var cargoEscolhido: Cargo? = membro?.cargoId?.let { id -> cargos.firstOrNull { it.id == id } }
        cargoEscolhido?.let { b.actCargoMembro.setText(it.nome, false) }
        b.actCargoMembro.setOnItemClickListener { _, _, posicao, _ ->
            cargoEscolhido = cargos[posicao]
            b.layoutCargoMembro.error = null
        }

        val dialogo = MaterialAlertDialogBuilder(requireContext())
            .setTitle(if (editando) R.string.equipe_dialogo_editar_titulo else R.string.equipe_dialogo_convidar_titulo)
            .setView(b.root)
            .setNegativeButton(R.string.equipe_cancelar, null)
            .setPositiveButton(if (editando) R.string.equipe_salvar else R.string.equipe_enviar_convite, null)
            .create()

        // Listener definido no onShow para o diálogo não fechar quando a validação falha.
        dialogo.setOnShowListener {
            val botao = dialogo.getButton(DialogInterface.BUTTON_POSITIVE)
            botao.setOnClickListener {
                val cargo = cargoEscolhido
                var erro = false
                b.layoutCargoMembro.error = null
                b.layoutNomeMembro.error = null
                b.layoutEmailMembro.error = null

                if (cargo == null) {
                    b.layoutCargoMembro.error = getString(R.string.equipe_erro_cargo)
                    erro = true
                }
                val nome = b.etNomeMembro.text.toString().trim()
                val email = b.etEmailMembro.text.toString().trim()
                if (editando && nome.isEmpty()) {
                    b.layoutNomeMembro.error = getString(R.string.equipe_erro_nome)
                    erro = true
                }
                if (!editando && !Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                    b.layoutEmailMembro.error = getString(R.string.equipe_erro_email)
                    erro = true
                }
                if (erro || cargo == null) return@setOnClickListener

                botao.isEnabled = false
                viewLifecycleOwner.lifecycleScope.launch {
                    val resultado = if (membro == null) {
                        equipeRepository.convidar(email, cargo.id)
                    } else {
                        // Só envia o que mudou.
                        val novoNome = nome.takeIf { it != membro.nome }
                        val novoCargo = cargo.id.takeIf { it != membro.cargoId }
                        if (novoNome == null && novoCargo == null) {
                            dialogo.dismiss()
                            return@launch
                        }
                        equipeRepository.atualizarMembro(membro.id, novoNome, novoCargo)
                    }
                    when (resultado) {
                        is ResultadoApi.Sucesso -> {
                            dialogo.dismiss()
                            val msg = if (editando) R.string.equipe_membro_atualizado else R.string.equipe_convite_enviado
                            Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
                            view?.let { carregar(it) }
                        }
                        is ResultadoApi.Erro -> {
                            botao.isEnabled = true
                            if (resultado.sessaoExpirada) {
                                dialogo.dismiss()
                                findNavController().navigate(R.id.action_global_to_login)
                            } else {
                                // Cobre 404 e 409 também: a mensagem já vem do backend.
                                Toast.makeText(requireContext(), resultado.mensagem, Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                }
            }
        }
        dialogo.show()
    }

    private fun confirmarRemocao(membro: Membro) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.equipe_remover_titulo)
            .setMessage(getString(R.string.equipe_remover_mensagem, membro.nome))
            .setNegativeButton(R.string.equipe_cancelar, null)
            .setPositiveButton(R.string.equipe_remover_confirmar) { _, _ ->
                viewLifecycleOwner.lifecycleScope.launch {
                    when (val resultado = equipeRepository.removerMembro(membro.id)) {
                        is ResultadoApi.Sucesso -> {
                            Toast.makeText(requireContext(), R.string.equipe_membro_removido, Toast.LENGTH_SHORT).show()
                            view?.let { carregar(it) }
                        }
                        is ResultadoApi.Erro -> {
                            if (resultado.sessaoExpirada) {
                                findNavController().navigate(R.id.action_global_to_login)
                            } else {
                                Toast.makeText(requireContext(), resultado.mensagem, Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                }
            }
            .show()
    }

    private fun abrirFormCargo(cargoId: String?) {
        findNavController().navigate(R.id.cargoFormFragment, bundleOf("cargoId" to cargoId))
    }
}