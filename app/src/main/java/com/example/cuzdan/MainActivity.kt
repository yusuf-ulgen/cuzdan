package com.example.cuzdan

import android.os.Bundle
import com.google.android.material.bottomnavigation.BottomNavigationView
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import androidx.appcompat.app.AlertDialog
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.biometric.BiometricManager
import android.widget.Toast
import com.example.cuzdan.databinding.ActivityMainBinding
import com.example.cuzdan.util.PreferenceManager
import dagger.hilt.android.AndroidEntryPoint
import java.util.concurrent.Executor
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    @Inject
    lateinit var prefManager: PreferenceManager

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val navView: BottomNavigationView = binding.navView

        val navController = findNavController(R.id.nav_host_fragment_activity_main)
        // Passing each menu ID as a set of Ids because each
        // menu should be considered as top level destinations.
        // setupActionBarWithNavController(navController, appBarConfiguration)
        navView.setupWithNavController(navController)

        if (prefManager.isAgreementAccepted()) {
            checkBiometrics()
        } else {
            checkUserAgreement()
        }
    }

    private fun checkUserAgreement() {
        if (!prefManager.isAgreementAccepted()) {
            AlertDialog.Builder(this)
                .setTitle(R.string.settings_agreement)
                .setMessage(R.string.user_agreement_text)
                .setCancelable(false)
                .setPositiveButton(R.string.dialog_confirm) { _, _ ->
                    prefManager.setAgreementAccepted(true)
                    checkBiometrics()
                }
                .setNegativeButton(R.string.dialog_cancel) { _, _ ->
                    finish()
                }
                .show()
        }
    }

    private fun checkBiometrics() {
        if (!prefManager.isBiometricsEnabled()) return

        val biometricManager = BiometricManager.from(this)
        val canAuthenticate = biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)

        if (canAuthenticate != BiometricManager.BIOMETRIC_SUCCESS) {
            // Cihazda biyometrik veri yok veya desteklenmiyor
            prefManager.setBiometricsEnabled(false)
            Toast.makeText(this, "Telefonda kayıtlı biyometrik veri bulunamadı. Biyometrik giriş kapatıldı.", Toast.LENGTH_LONG).show()
            return
        }

        showBiometricPrompt()
    }

    private fun showBiometricPrompt() {
        val executor: Executor = ContextCompat.getMainExecutor(this)
        val biometricPrompt = BiometricPrompt(this, executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    // Hata durumunda uygulamayı kapat veya tekrar dene
                    finish()
                }

                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    // Başarılı giriş, uygulama devam edebilir
                }

                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()
                    // Başarısız deneme, kullanıcıya bilgi verilebilir
                }
            })

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(getString(R.string.settings_biometrics))
            .setSubtitle(getString(R.string.login_description))
            .setNegativeButtonText(getString(R.string.dialog_cancel))
            .build()

        biometricPrompt.authenticate(promptInfo)
    }
}