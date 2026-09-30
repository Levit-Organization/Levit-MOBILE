package com.example.levit

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.levit.data.repository.AuthRepository
import com.example.levit.databinding.ActivitySplashBinding

class SplashActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySplashBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySplashBinding.inflate(layoutInflater)
        setContentView(binding.root)

        iniciarAnimacaoSplash()
    }

    private fun iniciarAnimacaoSplash() {
        binding.ivLogoLevit.alpha = 0f
        binding.ivLogoLevit.translationY = 50f

        binding.ivLogoLevit.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(1200)
            .withEndAction { irParaProximaTela() }
            .start()
    }

    private fun irParaProximaTela() {
        // Rota protegida: só entra direto no Dashboard se já existir um
        // token salvo e ele ainda não tiver expirado (JWT de 8h). Caso
        // contrário, cai na tela pública (Entrar/Registrar).
        val autenticado = AuthRepository(applicationContext).estaAutenticado()
        val destino = if (autenticado) DashboardActivity::class.java else MainActivity::class.java

        startActivity(Intent(this, destino))
        finish()
    }
}