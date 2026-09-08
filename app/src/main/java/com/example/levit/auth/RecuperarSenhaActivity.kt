package com.example.levit.auth

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.levit.databinding.ActivityRecuperarSenhaBinding

class RecuperarSenhaActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRecuperarSenhaBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRecuperarSenhaBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Ação de voltar na seta de texto
        binding.textVoltar.setOnClickListener {
            finish()
        }

        // Ver dps
        binding.btnEnviar.setOnClickListener {
            Toast.makeText(this, "E-mail de redefinição enviado", Toast.LENGTH_SHORT).show()
        }
    }
}
