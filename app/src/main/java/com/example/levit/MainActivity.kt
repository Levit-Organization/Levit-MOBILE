package com.example.levit

import android.content.Intent
import android.os.Bundle
import android.view.animation.DecelerateInterpolator
import androidx.appcompat.app.AppCompatActivity
import com.example.levit.auth.CadastroActivity
import com.example.levit.auth.LoginActivity
import com.example.levit.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnEntrar.setOnClickListener {
            startActivity(Intent(this, LoginActivity::class.java))
        }

        binding.tvRegistrar.setOnClickListener {
            startActivity(Intent(this, CadastroActivity::class.java))
        }

        animarTitulo()
    }

    private fun animarTitulo() {
        val distanciaInicial = 40f
        val duracaoMs = 500L

        binding.tvHeadlineLine1.translationY = distanciaInicial
        binding.tvHeadlineLine1.animate()
            .alpha(1f)
            .translationY(0f)
            .setStartDelay(100L)
            .setDuration(duracaoMs)
            .setInterpolator(DecelerateInterpolator())
            .start()

        binding.tvHeadlineLine2.translationY = distanciaInicial
        binding.tvHeadlineLine2.animate()
            .alpha(1f)
            .translationY(0f)
            .setStartDelay(220L)
            .setDuration(duracaoMs)
            .setInterpolator(DecelerateInterpolator())
            .start()
    }
}