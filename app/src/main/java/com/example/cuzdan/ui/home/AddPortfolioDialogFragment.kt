package com.example.cuzdan.ui.home

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.example.cuzdan.R
import com.example.cuzdan.data.local.entity.Portfolio
import com.example.cuzdan.data.repository.PortfolioRepository
import com.example.cuzdan.databinding.DialogAddPortfolioBinding
import com.example.cuzdan.util.PreferenceManager
import com.example.cuzdan.util.showToast
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class AddPortfolioDialogFragment : BottomSheetDialogFragment() {

    @Inject
    lateinit var portfolioRepository: PortfolioRepository
    
    @Inject
    lateinit var prefManager: PreferenceManager

    private var _binding: DialogAddPortfolioBinding? = null
    private val binding get() = _binding!!

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return BottomSheetDialog(requireContext(), R.style.CustomBottomSheetDialog)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return try {
            _binding = DialogAddPortfolioBinding.inflate(inflater, container, false)
            binding.root
        } catch (e: Exception) {
            android.util.Log.e("CuzdanDebug", "Portföy ekleme diyaloğu şişirilirken hata oluştu!", e)
            // Hata durumunda boş bir view dönüp çökmesini engelleyelim veya hatayı görelim
            View(requireContext())
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupListeners()
    }

    private fun setupListeners() {
        binding.btnSave.setOnClickListener {
            val name = binding.editPortfolioName.text.toString().trim()
            if (name.isEmpty()) {
                binding.editPortfolioName.error = getString(R.string.portfolio_name_hint)
            } else {
                val includeTotal = binding.switchIncludeTotal.isChecked
                
                CoroutineScope(Dispatchers.Main).launch {
                    val newId = portfolioRepository.insertPortfolio(
                        Portfolio(name = name, isIncludedInTotal = includeTotal)
                    )
                    prefManager.setSelectedPortfolioId(newId)
                    showToast(R.string.toast_portfolio_added)
                    dismiss()
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
