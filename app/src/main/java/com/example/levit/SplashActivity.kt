package com.example.levit

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
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
        // Estado inicial da logo: transparente e deslocada 50 pixels para baixo
        binding.ivLogoLevit.alpha = 0f
        binding.ivLogoLevit.translationY = 50f

        // Executa a animação
        binding.ivLogoLevit.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(1200) // Duração de 1.2 segundos
            .withEndAction {
                // Vai para a tela inicial automaticamente ao terminar a animação
                startActivity(Intent(this, MainActivity::class.java))
                finish()
            }
            .start()
    }
}