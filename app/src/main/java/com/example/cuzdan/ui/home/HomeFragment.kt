package com.example.cuzdan.ui.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.cuzdan.R
import com.example.cuzdan.databinding.FragmentHomeBinding
import com.example.cuzdan.util.PreferenceManager
import com.example.cuzdan.util.formatCurrency
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject
import com.example.cuzdan.ui.currency.CurrencyBottomSheet

@AndroidEntryPoint
class HomeFragment : Fragment() {

    @Inject
    lateinit var prefManager: PreferenceManager

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private val viewModel: HomeViewModel by activityViewModels()
    private lateinit var adapter: WalletCategoryAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        
        setupRecyclerView()
        setupUI()
        observeState()
        
        // Ensure localized strings are updated with current Activity context
        viewModel.refreshLocalization(requireContext())
        
        return binding.root
    }

    private fun setupRecyclerView() {
        adapter = WalletCategoryAdapter(emptyList()) { category ->
            viewModel.toggleCategoryExpansion(category.type)
        }
        binding.recyclerWalletCategories.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerWalletCategories.adapter = adapter
    }

    private fun setupUI() {
        binding.btnPrevPortfolio.setOnClickListener {
            viewModel.selectPrevPortfolio()
        }
        binding.btnNextPortfolio.setOnClickListener {
            viewModel.selectNextPortfolio()
        }
        binding.layoutPortfolioSelector.setOnClickListener {
            showPortfolioManagement()
        }
        binding.btnPrivacyToggle.setOnClickListener {
            val isEnabled = prefManager.isPrivacyModeEnabled()
            prefManager.setPrivacyModeEnabled(!isEnabled)
            updateUI(viewModel.uiState.value)
        }
        binding.btnCurrencySwitcher.setOnClickListener {
            showCurrencySwitcher()
        }
        binding.btnAddPortfolio.setOnClickListener {
            AddPortfolioDialogFragment().show(childFragmentManager, "AddPortfolio")
        }
        binding.textPortfolioName.setOnClickListener {
            PortfolioManagementBottomSheet().show(parentFragmentManager, "PortfolioManagement")
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

    private fun updateUI(state: WalletUiState) {
        val isPrivacyEnabled = prefManager.isPrivacyModeEnabled()
        
        binding.textPortfolioName.text = state.selectedPortfolioName

        binding.btnPrivacyToggle.setImageResource(

            if (isPrivacyEnabled) R.drawable.ic_eye_off else R.drawable.ic_eye_on
        )



        binding.donutChart.setSegments(state.donutSegments)
        binding.donutChart.setLabelColor(
            requireContext().getColor(if (prefManager.getThemeMode() == "light") R.color.text_primary_light else R.color.white)
        )


        if (isPrivacyEnabled) {

            binding.textTotalBalance.text = "**** ${state.currency}"
            binding.textDailyChangePerc.text = "****"
        } else {

            binding.textTotalBalance.text = state.totalBalance.formatCurrency(state.currency)
            val changeStr = state.dailyChangeAbs.formatCurrency(state.currency)
            val percStr = String.format("%%%+.2f", state.dailyChangePerc)
            binding.textDailyChangePerc.text = "$changeStr ($percStr)"
            
            val isPositive = state.dailyChangeAbs >= java.math.BigDecimal.ZERO
            val textColor = if (isPositive) R.color.pill_green_text else R.color.pill_red_text
            val bgColor = if (isPositive) R.color.pill_green_bg else R.color.pill_red_bg
            
            binding.textTotalBalance.setTextColor(requireContext().getColor(if (prefManager.getThemeMode() == "light") R.color.text_primary_light else R.color.white))
            binding.textDailyChangePerc.setTextColor(requireContext().getColor(textColor))
            binding.layoutDailyChangePill.backgroundTintList = android.content.res.ColorStateList.valueOf(requireContext().getColor(bgColor))
        }


        
        // Currency icon update
        val currencyIcon = when(state.currency) {
            "USD" -> R.drawable.ic_usd
            "EUR" -> R.drawable.ic_eur
            else -> R.drawable.ic_tl
        }
        binding.btnCurrencySwitcher.setImageResource(currencyIcon)
        
        adapter.setItemsWithPrivacy(state.categorySummaries, isPrivacyEnabled, state.currency)
    }


    private fun showPortfolioManagement() {
        val bottomSheet = PortfolioManagementBottomSheet()
        bottomSheet.show(childFragmentManager, "PortfolioManagement")
    }

    private fun showCurrencySwitcher() {
        val bottomSheet = CurrencyBottomSheet.newInstance(CurrencyBottomSheet.SOURCE_HOME)
        bottomSheet.show(childFragmentManager, "CurrencyBottomSheet")
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}