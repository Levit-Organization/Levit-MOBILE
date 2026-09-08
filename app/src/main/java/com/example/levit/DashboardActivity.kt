package com.example.levit

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.levit.databinding.ActivityDashboardBinding

class DashboardActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDashboardBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDashboardBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Ações ainda não integradas a um back-end, mexer dps
        binding.ivMenu.setOnClickListener {
            Toast.makeText(this, "Menu em desenvolvimento", Toast.LENGTH_SHORT).show()
        }

        binding.btnAddModulo.setOnClickListener {
            startActivity(Intent(this, NovoModuloActivity::class.java))
        }

        binding.tvVerFunil.setOnClickListener {
            Toast.makeText(this, "Funil de recrutamento em desenvolvimento", Toast.LENGTH_SHORT).show()
        }

        binding.btnVerMais.setOnClickListener {
            Toast.makeText(this, "Equipe em desenvolvimento", Toast.LENGTH_SHORT).show()
        }
    }
}
