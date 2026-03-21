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
import androidx.lifecycle.lifecycleScope
import com.example.cuzdan.data.repository.PortfolioRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class NotificationsFragment : Fragment() {

    @Inject
    lateinit var prefManager: PreferenceManager

    @Inject
    lateinit var portfolioRepository: PortfolioRepository

    private var _binding: FragmentNotificationsBinding? = null
    private val binding get() = _binding!!

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
    }


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
            SettingItem(0, "Koyu Tema", hasSwitch = true, isSwitchChecked = prefManager.getThemeMode() == "dark", iconRes = R.drawable.ic_dashboard_black_24dp),
            SettingItem(1, getString(R.string.settings_notifications), hasSwitch = true, isSwitchChecked = prefManager.isNotificationsEnabled(), iconRes = R.drawable.ic_notifications_black_24dp),
            SettingItem(3, getString(R.string.settings_language), value = if (prefManager.getLanguage() == "tr") "Türkçe" else "English", iconRes = R.drawable.ic_reports),
            SettingItem(4, getString(R.string.settings_currency), value = prefManager.getHomeCurrency(), iconRes = R.drawable.ic_currency),
            SettingItem(5, getString(R.string.settings_biometrics), hasSwitch = true, isSwitchChecked = prefManager.isBiometricsEnabled(), iconRes = R.drawable.ic_wallet),

            SettingItem(6, getString(R.string.settings_device_management), iconRes = R.drawable.ic_settings),
            SettingItem(7, getString(R.string.settings_faq), iconRes = R.drawable.ic_reports),
            SettingItem(8, getString(R.string.settings_support), iconRes = R.drawable.ic_wallet),
            SettingItem(9, getString(R.string.settings_recommend), iconRes = R.drawable.ic_menu),
            SettingItem(10, getString(R.string.settings_agreement), iconRes = R.drawable.ic_assets),
            SettingItem(11, getString(R.string.settings_legal), iconRes = R.drawable.ic_assets)
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
            0 -> {
                prefManager.setThemeMode(if (isChecked) "dark" else "light")
                requireActivity().recreate()
            }
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
                requireActivity().recreate()
            }
            .show()
    }

    private fun showCurrencyDialog() {
        val currencies = arrayOf("TL", "EUR", "USD")
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.settings_currency)
            .setItems(currencies) { _, which ->
                prefManager.setHomeCurrency(currencies[which])
                setupRecyclerView()
                Toast.makeText(context, "Para birimi değiştirildi: ${currencies[which]}", Toast.LENGTH_SHORT).show()
            }
            .show()
    }

    private fun showDeviceManagementDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.reset_warning_title)
            .setMessage(R.string.reset_warning_message)
            .setPositiveButton(R.string.settings_account_reset) { _, _ ->
                viewLifecycleOwner.lifecycleScope.launch {
                    portfolioRepository.clearAllData()
                    prefManager.resetPreferences()
                    Toast.makeText(context, R.string.settings_account_reset, Toast.LENGTH_SHORT).show()
                    requireActivity().recreate()
                }
            }
            .setNegativeButton(R.string.dialog_cancel, null)
            .show()
    }

    private fun showFAQDialog() {
        AgreementBottomSheet.newInstance(
            title = getString(R.string.faq_title),
            content = "${getString(R.string.faq_q1)}\n${getString(R.string.faq_a1)}\n\n${getString(R.string.faq_q2)}\n${getString(R.string.faq_a2)}",
            isReadOnly = true
        ).show(parentFragmentManager, "FAQ")
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
        AgreementBottomSheet.newInstance(
            title = getString(R.string.settings_agreement),
            content = getString(R.string.user_agreement_text),
            isReadOnly = false,
            onAccepted = {
                Toast.makeText(context, R.string.dialog_confirm, Toast.LENGTH_SHORT).show()
            }
        ).show(parentFragmentManager, "Agreement")
    }

    private fun showLegalWarningDialog() {
        AgreementBottomSheet.newInstance(
            title = getString(R.string.settings_legal),
            content = getString(R.string.legal_warning_text),
            isReadOnly = true
        ).show(parentFragmentManager, "Legal")
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}