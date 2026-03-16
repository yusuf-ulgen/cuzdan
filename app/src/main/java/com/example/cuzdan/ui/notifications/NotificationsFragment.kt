package com.example.cuzdan.ui.notifications

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.cuzdan.R
import com.example.cuzdan.databinding.DialogSupportBinding
import com.example.cuzdan.databinding.FragmentNotificationsBinding
import com.example.cuzdan.util.PreferenceManager
import com.google.android.material.bottomsheet.BottomSheetDialog
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class NotificationsFragment : Fragment() {

    @Inject
    lateinit var prefManager: PreferenceManager

    private var _binding: FragmentNotificationsBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentNotificationsBinding.inflate(inflater, container, false)
        
        setupRecyclerView()
        
        return binding.root
    }

    private fun setupRecyclerView() {
        val settings = listOf(
            SettingItem(1, getString(R.string.settings_notifications), hasSwitch = true, isSwitchChecked = prefManager.isNotificationsEnabled()),
            // Abonelik kaldırıldı
            SettingItem(3, getString(R.string.settings_language), value = if (prefManager.getLanguage() == "tr") "Türkçe" else "English"),
            SettingItem(4, getString(R.string.settings_currency), value = prefManager.getCurrency()),
            SettingItem(5, getString(R.string.settings_biometrics), hasSwitch = true, isSwitchChecked = prefManager.isBiometricsEnabled()),
            SettingItem(6, getString(R.string.settings_device_management)),
            SettingItem(7, getString(R.string.settings_faq)),
            SettingItem(8, getString(R.string.settings_support)),
            SettingItem(9, getString(R.string.settings_recommend)),
            SettingItem(10, getString(R.string.settings_agreement)),
            SettingItem(11, getString(R.string.settings_legal))
        )

        val adapter = SettingsAdapter(
            items = settings,
            onSwitchChanged = { id, isChecked ->
                handleSwitchChange(id, isChecked)
            },
            onItemClicked = { id ->
                handleItemClick(id)
            }
        )
        binding.recyclerSettings.apply {
            layoutManager = LinearLayoutManager(context)
            this.adapter = adapter
        }
    }

    private fun handleSwitchChange(id: Int, isChecked: Boolean) {
        when (id) {
            1 -> prefManager.setNotificationsEnabled(isChecked)
            5 -> {
                prefManager.setBiometricsEnabled(isChecked)
                if (isChecked) {
                    Toast.makeText(context, "Biyometrik veriler için bildirim izni istenecek.", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun handleItemClick(id: Int) {
        when (id) {
            3 -> showLanguageDialog()
            4 -> showCurrencyDialog()
            6 -> showDeviceManagementDialog()
            7 -> showFAQDialog()
            8 -> showSupportDialog()
            9 -> shareApp()
            10 -> showAgreementDialog()
            11 -> showLegalWarningDialog()
        }
    }

    private fun showLanguageDialog() {
        val languages = arrayOf("Türkçe", "English")
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.settings_language)
            .setItems(languages) { _, which ->
                val lang = if (which == 0) "tr" else "en"
                prefManager.setLanguage(lang)
                setupRecyclerView()
                Toast.makeText(context, "Dil değiştirildi: ${languages[which]}", Toast.LENGTH_SHORT).show()
            }
            .show()
    }

    private fun showCurrencyDialog() {
        val currencies = arrayOf("TL", "EUR", "USD")
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.settings_currency)
            .setItems(currencies) { _, which ->
                prefManager.setCurrency(currencies[which])
                setupRecyclerView()
                Toast.makeText(context, "Para birimi değiştirildi: ${currencies[which]}", Toast.LENGTH_SHORT).show()
            }
            .show()
    }

    private fun showDeviceManagementDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.settings_device_management)
            .setMessage("Hesabınızı sıfırlamak istediğinize emin misiniz? Bu işlem tüm yerel ayarlarınızı temizleyecektir.")
            .setPositiveButton(R.string.settings_account_reset) { _, _ ->
                prefManager.resetPreferences()
                Toast.makeText(context, "Hesap sıfırlandı.", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(R.string.dialog_cancel, null)
            .show()
    }

    private fun showFAQDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.settings_faq)
            .setMessage("1. Uygulama nedir?\nCüzdan, varlıklarınızı takip etmenize yardımcı olan bir uygulamadır.\n\n2. Verilerim güvende mi?\nEvet, tüm verileriniz cihazınızda saklanır.")
            .setPositiveButton("Tamam", null)
            .show()
    }

    private fun showSupportDialog() {
        val dialog = BottomSheetDialog(requireContext())
        val binding = DialogSupportBinding.inflate(layoutInflater)
        dialog.setContentView(binding.root)

        binding.buttonSend.setOnClickListener {
            val email = binding.editEmail.text.toString()
            val message = binding.editMessage.text.toString()
            
            if (email.isNotEmpty() && message.isNotEmpty()) {
                val intent = Intent(Intent.ACTION_SENDTO).apply {
                    data = Uri.parse("mailto:admin@cuzdan.com")
                    putExtra(Intent.EXTRA_SUBJECT, "Destek Talebi")
                    putExtra(Intent.EXTRA_TEXT, "Göndüren: $email\n\nMesaj: $message")
                }
                startActivity(Intent.createChooser(intent, "E-posta gönder..."))
                dialog.dismiss()
            } else {
                Toast.makeText(context, "Lütfen tüm alanları doldurun.", Toast.LENGTH_SHORT).show()
            }
        }
        dialog.show()
    }

    private fun shareApp() {
        val sendIntent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_TEXT, "Cüzdan uygulamasını denemelisin! ${getString(R.string.play_store_link)}")
            type = "text/plain"
        }
        startActivity(Intent.createChooser(sendIntent, null))
    }

    private fun showAgreementDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.settings_agreement)
            .setMessage(R.string.user_agreement_text)
            .setPositiveButton("Tamam", null)
            .show()
    }

    private fun showLegalWarningDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.settings_legal)
            .setMessage(R.string.legal_warning_text)
            .setPositiveButton("Tamam", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}