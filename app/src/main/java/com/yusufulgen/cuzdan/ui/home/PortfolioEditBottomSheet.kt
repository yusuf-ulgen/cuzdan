package com.yusufulgen.cuzdan.ui.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.appcompat.app.AlertDialog
import com.yusufulgen.cuzdan.R
import com.yusufulgen.cuzdan.databinding.BottomSheetPortfolioEditBinding
import com.yusufulgen.cuzdan.util.showToast
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineScope

@AndroidEntryPoint
class PortfolioEditBottomSheet : BottomSheetDialogFragment() {

    private var _binding: BottomSheetPortfolioEditBinding? = null
    private val binding get() = _binding!!

    private val viewModel: HomeViewModel by viewModels()
    private var portfolioId: Long = 0

    companion object {
        private const val ARG_PORTFOLIO_ID = "portfolio_id"

        fun newInstance(portfolioId: Long): PortfolioEditBottomSheet {
            val fragment = PortfolioEditBottomSheet()
            val args = Bundle()
            args.putLong(ARG_PORTFOLIO_ID, portfolioId)
            fragment.arguments = args
            return fragment
        }
    }

    override fun getTheme(): Int = com.yusufulgen.cuzdan.R.style.CustomBottomSheetDialog

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        portfolioId = arguments?.getLong(ARG_PORTFOLIO_ID) ?: 0
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = BottomSheetPortfolioEditBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        loadPortfolio()
        setupListeners()
    }

    private fun loadPortfolio() {
        viewLifecycleOwner.lifecycleScope.launch {
            val portfolio = viewModel.getPortfolioById(portfolioId)
            portfolio?.let {
                binding.editPortfolioName.setText(it.name)
                binding.switchInclusion.isChecked = it.isIncludedInTotal
            }
        }
    }

    private fun setupListeners() {
        binding.btnClose.setOnClickListener { dismiss() }
        
        binding.btnSavePortfolio.setOnClickListener {
            val newName = binding.editPortfolioName.text.toString()
            if (newName.isBlank()) {
                showToast(com.yusufulgen.cuzdan.R.string.toast_error_name_empty)
                return@setOnClickListener
            }
            
            viewLifecycleOwner.lifecycleScope.launch {
                viewModel.updatePortfolio(portfolioId, newName, binding.switchInclusion.isChecked)
                showToast(com.yusufulgen.cuzdan.R.string.toast_portfolio_updated)
                dismiss()
            }
        }

        binding.btnDeletePortfolio.setOnClickListener {
            AlertDialog.Builder(requireContext())
                .setTitle(com.yusufulgen.cuzdan.R.string.reset_warning_title)
                .setMessage(com.yusufulgen.cuzdan.R.string.toast_portfolio_deleted)
                .setPositiveButton(com.yusufulgen.cuzdan.R.string.dialog_confirm) { dialog, _ ->
                    viewLifecycleOwner.lifecycleScope.launch {
                        viewModel.deletePortfolio(portfolioId)
                        showToast(com.yusufulgen.cuzdan.R.string.toast_portfolio_deleted)
                        dismiss()
                    }
                }
                .setNegativeButton(com.yusufulgen.cuzdan.R.string.dialog_cancel, null)
                .show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
