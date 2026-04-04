package com.example.cuzdan

import android.content.Context
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
import com.example.cuzdan.ui.notifications.AgreementBottomSheet
import com.example.cuzdan.util.PreferenceManager
import com.example.cuzdan.util.PriceSyncManager
import dagger.hilt.android.AndroidEntryPoint
import java.util.concurrent.Executor
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    @Inject
    lateinit var prefManager: PreferenceManager

    @Inject
    lateinit var priceSyncManager: PriceSyncManager

    private lateinit var binding: ActivityMainBinding

    override fun onResume() {
        super.onResume()
        priceSyncManager.startPolling()

        // Biyometrik kontrol (Cooldown 30 saniye)
        if (prefManager.isAgreementAccepted() && prefManager.isBiometricsEnabled()) {
            val now = System.currentTimeMillis()
            val lastAuth = prefManager.getLastAuthTimestamp()
            if (now - lastAuth > 30_000) { // 30 saniye cooldown
                checkBiometrics()
            }
        }
    }

    override fun onPause() {
        super.onPause()
        priceSyncManager.stopPolling()
    }
    
    override fun attachBaseContext(newBase: Context) {
        val prefManager = PreferenceManager(newBase)
        val lang = prefManager.getLanguage()
        super.attachBaseContext(com.example.cuzdan.util.LocaleHelper.onAttach(newBase, lang))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val themeMode = PreferenceManager(this).getThemeMode()
        if (themeMode == "light") {
            setTheme(R.style.Theme_Cuzdan_Light)
        } else {
            setTheme(R.style.Theme_Cuzdan_Dark)
        }
        super.onCreate(savedInstanceState)



        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val navView: BottomNavigationView = binding.navView

        val navController = findNavController(R.id.nav_host_fragment_activity_main)
        // Passing each menu ID as a set of Ids because each
        // menu should be considered as top level destinations.
        // setupActionBarWithNavController(navController, appBarConfiguration)
        navView.setupWithNavController(navController)
        
        // Fix icon size for PNG-based navigation icons
        navView.itemIconSize = resources.getDimensionPixelSize(R.dimen.nav_icon_size)

        // Custom Scale Animation for Bottom Nav Icons
        navController.addOnDestinationChangedListener { _, destination, _ ->
            updateBottomNavIcons(navView, destination.id)
        }

        if (!prefManager.isAgreementAccepted()) {
            checkUserAgreement()
        }
    }

    private fun updateBottomNavIcons(navView: BottomNavigationView, selectedId: Int) {
        val menuView = navView.getChildAt(0) as? android.view.ViewGroup ?: return
        for (i in 0 until menuView.childCount) {
            val itemView = menuView.getChildAt(i) as? android.view.ViewGroup ?: continue
            val itemId = itemView.id
            
            // Find the icon ImageView inside the item view
            val iconView = itemView.findViewById<android.view.View>(com.google.android.material.R.id.navigation_bar_item_icon_view)
            
            if (iconView != null) {
                val scale = if (itemId == selectedId) 1.2f else 1.0f
                iconView.animate()
                    .scaleX(scale)
                    .scaleY(scale)
                    .setDuration(200)
                    .start()
            }
        }
    }

    private fun checkUserAgreement() {
        if (!prefManager.isAgreementAccepted()) {
            AgreementBottomSheet.newInstance(
                title = getString(R.string.settings_agreement),
                content = getString(R.string.user_agreement_text),
                isReadOnly = false,
                isMandatory = true,
                onAccepted = {
                    prefManager.setAgreementAccepted(true)
                    checkBiometrics()
                }
            ).show(supportFragmentManager, "InitialAgreement")
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
                    if (errorCode != BiometricPrompt.ERROR_USER_CANCELED && errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON) {
                        finish()
                    }
                }

                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    // Başarılı giriş, timestamp güncelle
                    prefManager.setLastAuthTimestamp(System.currentTimeMillis())
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