package com.example.levit

import android.content.Intent
import android.os.Bundle
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
    }

}
