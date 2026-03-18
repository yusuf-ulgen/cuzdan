package com.example.cuzdan.ui.reports

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.navigation.fragment.findNavController
import com.example.cuzdan.R
import com.example.cuzdan.util.PreferenceManager
import com.example.cuzdan.util.formatCurrency
import com.example.cuzdan.databinding.FragmentReportsBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject
import com.example.cuzdan.ui.currency.CurrencyBottomSheet

@AndroidEntryPoint
class ReportsFragment : Fragment() {

    private var _binding: FragmentReportsBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ReportsViewModel by activityViewModels()
    
    @Inject
    lateinit var prefManager: PreferenceManager
    
    private var isHidden = false
    private lateinit var adapter: ReportCategoryAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentReportsBinding.inflate(inflater, container, false)
        
        isHidden = prefManager.isPrivacyModeEnabled()
        
        setupRecyclerView()
        setupUI()
        observeState()
        
        // Ensure localized strings are updated
        viewModel.refreshLocalization(requireContext())
        
        return binding.root
    }

    private fun setupUI() {
        binding.btnPrivacyToggle.setOnClickListener {
            isHidden = !isHidden
            prefManager.setPrivacyModeEnabled(isHidden)
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

        binding.btnCurrencySwitcher.setOnClickListener {
            showCurrencySwitcher()
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
        val currentId = prefManager.getSelectedPortfolioId()
        val portfolios = state.portfolios
        
        binding.textPortfolioName.text = if (currentId == -1L) {
            getString(R.string.total_portfolios)
        } else {
            portfolios.find { it.id == currentId }?.name ?: ""
        }

        updateHideShowUI(state)
        adapter.setCurrency(state.currency)
        adapter.setItems(state.categories)
    }

    private fun updateHideShowUI(state: ReportsUiState) {
        if (isHidden) {
            binding.btnPrivacyToggle.setImageResource(R.drawable.ic_eye_off)
            binding.textTotalAmount.text = "***** ${state.currency}"
            binding.textDailyChangeAbs.text = "*****"
            binding.textDailyChangePerc.text = "*****"
        } else {
            val isPositive = state.totalProfitLoss >= java.math.BigDecimal.ZERO
            val color = if (isPositive) R.color.accent_green else R.color.accent_red
            val colorInt = requireContext().getColor(color)
            
            binding.textTotalAmount.setTextColor(requireContext().getColor(R.color.white))
            binding.textDailyChangeAbs.setTextColor(colorInt)
            binding.textDailyChangePerc.setTextColor(colorInt)
            
            binding.textTotalAmount.text = state.totalValue.formatCurrency(state.currency)
            binding.textDailyChangeAbs.text = state.totalProfitLoss.formatCurrency(state.currency)
            binding.textDailyChangePerc.text = String.format("%%%+.2f", state.totalProfitPerc)
        }
        
        // Currency icon update
        val currencyIcon = when(state.currency) {
            "USD" -> R.drawable.ic_usd
            "EUR" -> R.drawable.ic_eur
            else -> R.drawable.ic_tl
        }
        binding.btnCurrencySwitcher.setImageResource(currencyIcon)
        
        if (::adapter.isInitialized) {
            adapter.setPrivacyEnabled(isHidden)
        }

        // Sync Status and Offline Indicator
        binding.textLastUpdated.text = state.lastUpdated ?: ""
        binding.textLastUpdated.visibility = if (state.lastUpdated != null) View.VISIBLE else View.GONE
        binding.textOfflineIndicator.visibility = if (state.isOffline) View.VISIBLE else View.GONE
    }

    private fun setupRecyclerView() {
        adapter = ReportCategoryAdapter(emptyList(), isHidden) { _ -> }
        binding.recyclerReportCategories.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerReportCategories.adapter = adapter
    }

    private fun showCurrencySwitcher() {
        val bottomSheet = com.example.cuzdan.ui.currency.CurrencyBottomSheet.newInstance(com.example.cuzdan.ui.currency.CurrencyBottomSheet.SOURCE_REPORTS)
        bottomSheet.show(childFragmentManager, "CurrencyBottomSheet")
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
