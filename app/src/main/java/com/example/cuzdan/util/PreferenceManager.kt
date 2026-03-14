package com.example.cuzdan.util

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PreferenceManager @Inject constructor(
    @ApplicationContext context: Context
) {
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs: SharedPreferences = EncryptedSharedPreferences.create(
        context,
        "cuzdan_secure_prefs",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun setCurrency(currency: String) {
        prefs.edit().putString("currency", currency).apply()
    }

    fun getCurrency(): String {
        return prefs.getString("currency", "TL") ?: "TL"
    }

    fun setLanguage(language: String) {
        prefs.edit().putString("language", language).apply()
    }

    fun getLanguage(): String {
        return prefs.getString("language", "tr") ?: "tr"
    }

    fun setBiometricsEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("biometrics_enabled", enabled).apply()
    }

    fun isBiometricsEnabled(): Boolean {
        return prefs.getBoolean("biometrics_enabled", false)
    }

    fun setNotificationsEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("notifications_enabled", enabled).apply()
    }

    fun isNotificationsEnabled(): Boolean {
        return prefs.getBoolean("notifications_enabled", true)
    }

    fun setAgreementAccepted(accepted: Boolean) {
        prefs.edit().putBoolean("agreement_accepted", accepted).apply()
    }

    fun isAgreementAccepted(): Boolean {
        return prefs.getBoolean("agreement_accepted", false)
    }

    fun setPrivacyModeEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("privacy_mode_enabled", enabled).apply()
    }

    fun isPrivacyModeEnabled(): Boolean {
        return prefs.getBoolean("privacy_mode_enabled", false)
    }

    fun resetPreferences() {
        prefs.edit().clear().apply()
    }
}
