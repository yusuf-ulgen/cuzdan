package com.yusufulgen.cuzdan.ui.home

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.yusufulgen.cuzdan.R
import com.yusufulgen.cuzdan.data.local.entity.Portfolio
import com.yusufulgen.cuzdan.data.repository.PortfolioRepository
import com.yusufulgen.cuzdan.databinding.DialogAddPortfolioBinding
import com.yusufulgen.cuzdan.util.PreferenceManager
import com.yusufulgen.cuzdan.util.showToast
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

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val themeMode = PreferenceManager(requireContext()).getThemeMode()
        val themeRes = if (themeMode == "light") R.style.Theme_Cuzdan_Light else R.style.Theme_Cuzdan_Dark
        val context = androidx.appcompat.view.ContextThemeWrapper(requireContext(), themeRes)
        val themedInflater = inflater.cloneInContext(context)
        _binding = DialogAddPortfolioBinding.inflate(themedInflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        if (_binding != null) {
            setupListeners()
        } else {
            dismiss()
        }
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
