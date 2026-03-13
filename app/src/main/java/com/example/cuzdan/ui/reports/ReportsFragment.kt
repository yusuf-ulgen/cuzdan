package com.example.cuzdan.ui.reports

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.navigation.fragment.findNavController
import com.example.cuzdan.R
import com.example.cuzdan.databinding.FragmentReportsBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.Locale

@AndroidEntryPoint
class ReportsFragment : Fragment() {

    private var _binding: FragmentReportsBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ReportsViewModel by viewModels()
    private var isHidden = false
    private lateinit var adapter: ReportCategoryAdapter
    private val currencyFormat = NumberFormat.getCurrencyInstance(Locale("tr", "TR"))

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentReportsBinding.inflate(inflater, container, false)
        
        setupRecyclerView()
        setupUI()
        observeState()
        
        return binding.root
    }

    private fun setupUI() {
        binding.imageHideShow.setOnClickListener {
            isHidden = !isHidden
            updateHideShowUI(viewModel.uiState.value)
        }

        binding.layoutDailyChange.setOnClickListener {
            findNavController().navigate(R.id.navigation_profit_loss_chart)
        }

        binding.btnPrevPortfolio.setOnClickListener { 
            viewModel.selectPrevPortfolio()
        }
        
        binding.btnNextPortfolio.setOnClickListener { 
            viewModel.selectNextPortfolio()
        }
    }

    private fun observeState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    updateUI(state)
                }
            }
        }
    }

    private fun updateUI(state: ReportsUiState) {
        
        if (state.portfolios.isNotEmpty()) {
            binding.textPortfolioName.text = state.portfolios[state.selectedPortfolioIndex].name
        }

        updateHideShowUI(state)
        
        adapter.setItems(state.categories)
    }

    private fun updateHideShowUI(state: ReportsUiState) {
        if (isHidden) {
            binding.imageHideShow.setImageResource(R.drawable.ic_eye_off)
            binding.textTotalAmount.text = "***** TL"
            binding.textDailyChangeAbs.text = "*****"
            binding.textDailyChangePerc.text = "*****"
        } else {
            binding.imageHideShow.setImageResource(R.drawable.ic_eye_on)
            binding.textTotalAmount.text = currencyFormat.format(state.totalValue)
            binding.textDailyChangeAbs.text = currencyFormat.format(state.totalProfitLoss)
            binding.textDailyChangePerc.text = String.format("%%%+.2f", 0.0) // Günlük değişim için ek alan gerekebilir, şimdilik 0.0
        }
        
        if (::adapter.isInitialized) {
            adapter.setHidden(isHidden)
        }
    }

    private fun setupRecyclerView() {
        adapter = ReportCategoryAdapter(emptyList(), isHidden)
        binding.recyclerReportCategories.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = this@ReportsFragment.adapter
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
