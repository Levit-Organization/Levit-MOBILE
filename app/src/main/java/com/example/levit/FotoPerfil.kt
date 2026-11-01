package com.example.levit

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.RoundedBitmapDrawableFactory
import com.example.levit.data.repository.AuthRepository
import java.io.File
import java.io.FileOutputStream

/** Foto de perfil guardada só no aparelho, uma por usuário. */
object FotoPerfil {

    private const val LADO_MAXIMO = 512

    private fun arquivo(context: Context, usuarioId: String) =
        File(context.filesDir, "avatar_$usuarioId.jpg")

    fun existe(context: Context, usuarioId: String) = arquivo(context, usuarioId).exists()

    fun carregar(context: Context, usuarioId: String): Bitmap? =
        arquivo(context, usuarioId).takeIf { it.exists() }?.let { BitmapFactory.decodeFile(it.path) }

    fun remover(context: Context, usuarioId: String) {
        arquivo(context, usuarioId).delete()
    }

    /** Recorta no centro, reduz e salva. Devolve false se a imagem não pôde ser lida. */
    fun salvar(context: Context, usuarioId: String, uri: Uri): Boolean = runCatching {
        val resolver = context.contentResolver

        // 1) Só as dimensões, para decodificar já reduzido e não estourar a memória.
        val limites = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, limites) }
        var amostra = 1
        while (limites.outWidth / amostra > LADO_MAXIMO * 2 || limites.outHeight / amostra > LADO_MAXIMO * 2) {
            amostra *= 2
        }

        val original = resolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = amostra })
        } ?: return false

        // 2) Corrige a rotação das fotos da câmera.
        val graus = resolver.openInputStream(uri)?.use {
            when (ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
        } ?: 0f
        val girada = if (graus == 0f) original else
            Bitmap.createBitmap(original, 0, 0, original.width, original.height,
                Matrix().apply { postRotate(graus) }, true)

        // 3) Quadrado central, no tamanho final.
        val lado = minOf(girada.width, girada.height)
        val quadrado = Bitmap.createBitmap(girada, (girada.width - lado) / 2, (girada.height - lado) / 2, lado, lado)
        val finalLado = minOf(lado, LADO_MAXIMO)
        val pronta = Bitmap.createScaledBitmap(quadrado, finalLado, finalLado, true)

        FileOutputStream(arquivo(context, usuarioId)).use { pronta.compress(Bitmap.CompressFormat.JPEG, 85, it) }
        true
    }.getOrDefault(false)

    /** Põe a foto no círculo; sem foto, volta às iniciais no fundo roxo. */
    fun aplicar(avatar: TextView) {
        val contexto = avatar.context.applicationContext
        val usuario = AuthRepository(contexto).usuarioLogado()
        val foto = usuario?.id?.let { carregar(contexto, it) }

        if (foto != null) {
            avatar.text = ""
            avatar.backgroundTintList = null
            avatar.background = RoundedBitmapDrawableFactory.create(avatar.resources, foto)
                .apply { isCircular = true }
        } else {
            avatar.text = iniciais(usuario?.nome)
            avatar.setBackgroundResource(R.drawable.bg_circle)
            avatar.backgroundTintList =
                ColorStateList.valueOf(ContextCompat.getColor(contexto, R.color.levit_purple))
        }
    }
}