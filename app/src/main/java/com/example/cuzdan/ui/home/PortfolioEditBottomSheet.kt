package com.example.cuzdan.ui.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.example.cuzdan.databinding.BottomSheetPortfolioEditBinding
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

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

    override fun getTheme(): Int = com.example.cuzdan.R.style.CustomBottomSheetDialog

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
                Toast.makeText(requireContext(), "İsim boş olamaz", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            
            viewLifecycleOwner.lifecycleScope.launch {
                viewModel.updatePortfolio(portfolioId, newName, binding.switchInclusion.isChecked)
                Toast.makeText(requireContext(), "Kaydedildi", Toast.LENGTH_SHORT).show()
                dismiss()
            }
        }

        binding.btnDeletePortfolio.setOnClickListener {
            androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle("Portföyü Sil")
                .setMessage("Bu portföyü ve içindeki tüm varlıkları silmek istediğinize emin misiniz?")
                .setPositiveButton("Sil") { _, _ ->
                    viewLifecycleOwner.lifecycleScope.launch {
                        viewModel.deletePortfolio(portfolioId)
                        Toast.makeText(requireContext(), "Silindi", Toast.LENGTH_SHORT).show()
                        dismiss()
                    }
                }
                .setNegativeButton("İptal", null)
                .show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
