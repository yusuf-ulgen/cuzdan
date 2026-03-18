package com.example.cuzdan.ui.currency

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.activityViewModels
import com.example.cuzdan.databinding.BottomSheetCurrencyBinding
import com.example.cuzdan.ui.home.HomeViewModel
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

class CurrencyBottomSheet : BottomSheetDialogFragment() {

    private var _binding: BottomSheetCurrencyBinding? = null
    private val binding get() = _binding!!
    companion object {
        private const val ARG_SOURCE = "arg_source"
        const val SOURCE_HOME = "home"
        const val SOURCE_REPORTS = "reports"

        fun newInstance(source: String): CurrencyBottomSheet {
            return CurrencyBottomSheet().apply {
                arguments = Bundle().apply {
                    putString(ARG_SOURCE, source)
                }
            }
        }
    }

    private val homeViewModel: HomeViewModel by activityViewModels()
    private val reportsViewModel: com.example.cuzdan.ui.reports.ReportsViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = BottomSheetCurrencyBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        binding.layoutCurrencyTl.setOnClickListener {
            updateCurrency("TL")
        }
        
        binding.layoutCurrencyUsd.setOnClickListener {
            updateCurrency("USD")
        }
        
        binding.layoutCurrencyEur.setOnClickListener {
            updateCurrency("EUR")
        }
    }

    private fun updateCurrency(currency: String) {
        val source = arguments?.getString(ARG_SOURCE)
        if (source == SOURCE_HOME) {
            homeViewModel.setCurrency(currency)
        } else if (source == SOURCE_REPORTS) {
            reportsViewModel.setCurrency(currency)
        }
        dismiss()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
