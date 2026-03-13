package com.example.cuzdan.ui.notifications

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.cuzdan.databinding.FragmentNotificationsBinding
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class NotificationsFragment : Fragment() {

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
            SettingItem(1, "Bildirimler", "Açık"),
            SettingItem(2, "Abonelik", "Ücretli"),
            SettingItem(3, "Dil", "Türkçe"),
            SettingItem(4, "Para Birimi", "TL"),
            SettingItem(5, "Yüz / Parmak Tanıma", hasSwitch = true, isSwitchChecked = true),
            SettingItem(6, "Cihaz Yönetimi"),
            SettingItem(7, "Sıkça Sorulan Sorular (SSS)"),
            SettingItem(8, "Destek"),
            SettingItem(9, "Arkadaşlarına Öner"),
            SettingItem(10, "Kullanıcı Sözleşmesi ve Gizlilik Politikası"),
            SettingItem(11, "Yasal Uyarı")
        )

        val adapter = SettingsAdapter(settings)
        binding.recyclerSettings.apply {
            layoutManager = LinearLayoutManager(context)
            this.adapter = adapter
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}