package com.example.levit

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.levit.data.repository.AuthRepository
import com.example.levit.databinding.ActivitySplashBinding
// Importando para que o usuario consiga aceitar a permissao e o app entrar em contato com o backend
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat

class SplashActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySplashBinding

    // Pede a permissão de rede local (Android 17+). Segue mesmo que a pessoa negue.
    private val pedirRedeLocal = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { iniciarAnimacaoSplash() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySplashBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (faltaPermissaoRedeLocal()) {
            pedirRedeLocal.launch(PERMISSAO_REDE_LOCAL)
        } else {
            iniciarAnimacaoSplash()
        }
    }

    private fun faltaPermissaoRedeLocal() =
        Build.VERSION.SDK_INT >= 37 &&
                ContextCompat.checkSelfPermission(this, PERMISSAO_REDE_LOCAL) !=
                PackageManager.PERMISSION_GRANTED

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

    companion object {
        private const val PERMISSAO_REDE_LOCAL = "android.permission.ACCESS_LOCAL_NETWORK"
    }
}