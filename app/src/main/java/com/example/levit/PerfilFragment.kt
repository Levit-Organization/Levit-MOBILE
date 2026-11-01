package com.example.levit

import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import com.example.levit.data.repository.AuthRepository
import com.google.android.material.textfield.TextInputEditText

class PerfilFragment : HamburgerMenuBaseFragment() {

    override val pageLayoutResId: Int = R.layout.fragment_perfil

    private val usuarioId: String?
        get() = AuthRepository(requireContext().applicationContext).usuarioLogado()?.id

    // Seletor de fotos do sistema: não precisa de permissão.
    private val seletorFoto = registerForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        val id = usuarioId
        if (uri == null || id == null) return@registerForActivityResult
        if (FotoPerfil.salvar(requireContext().applicationContext, id, uri)) {
            atualizarFoto()
        } else {
            Toast.makeText(requireContext(), R.string.perfil_foto_erro, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        if (!menuFoiMontado()) return

        setupHamburgerMenuButton(R.id.ivMenu)

        val usuario = AuthRepository(requireContext().applicationContext).usuarioLogado()

        view.findViewById<TextView>(R.id.tvNomePerfil).text = usuario?.nome.orEmpty()
        view.findViewById<TextInputEditText>(R.id.etNomePerfil).setText(usuario?.nome.orEmpty())
        view.findViewById<TextInputEditText>(R.id.etEmailPerfil).setText(usuario?.email.orEmpty())

        view.findViewById<View>(R.id.frameFoto).setOnClickListener {
            seletorFoto.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
            )
        }
        view.findViewById<View>(R.id.tvRemoverFoto).setOnClickListener {
            usuarioId?.let { FotoPerfil.remover(requireContext().applicationContext, it) }
            atualizarFoto()
        }
        atualizarFoto()

        // O servidor ainda não entrega nem salva estes dados, nem tem endpoint de
        // edição ou de troca de senha. Ficam escondidos até o backend existir
        // (issue "Editar perfil e trocar senha"): é só remover esta lista.
        listOf(
            R.id.layoutEmpresaPerfil,
            R.id.layoutCpfCnpjPerfil,
            R.id.layoutTelefonePerfil,
            R.id.layoutLinkedinPerfil,
            R.id.btnEditarPerfil,
            R.id.cardSeguranca
        ).forEach { view.findViewById<View>(it).visibility = View.GONE }

        view.findViewById<View>(R.id.tvAvisoEdicao).visibility = View.VISIBLE
    }

    private fun atualizarFoto() {
        val raiz = view ?: return
        FotoPerfil.aplicar(raiz.findViewById(R.id.tvAvatar))
        FotoPerfil.aplicar(raiz.findViewById(R.id.tvAvatarGrande))
        val temFoto = usuarioId?.let { FotoPerfil.existe(requireContext().applicationContext, it) } == true
        raiz.findViewById<View>(R.id.tvRemoverFoto).visibility = if (temFoto) View.VISIBLE else View.GONE
    }
}