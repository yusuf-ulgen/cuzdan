package com.example.cuzdan.ui.home

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
import com.example.cuzdan.R
import com.example.cuzdan.databinding.FragmentHomeBinding
import com.example.cuzdan.util.PreferenceManager
import com.example.cuzdan.util.formatCurrency
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.util.Locale
import javax.inject.Inject

@AndroidEntryPoint
class HomeFragment : Fragment() {

    @Inject
    lateinit var prefManager: PreferenceManager

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private val viewModel: HomeViewModel by viewModels()
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
        
        return binding.root
    }

    private fun setupRecyclerView() {
        adapter = WalletCategoryAdapter()
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
            viewModel.uiState.value.let { updateUI(it) }
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

        if (isPrivacyEnabled) {
            binding.textTotalBalance.text = "**** ${state.currency}"
            binding.textDailyChangeAbs.text = "****"
            binding.textDailyChangePerc.text = "***%"
            binding.imageDailyChangeTrending.imageTintList = android.content.res.ColorStateList.valueOf(requireContext().getColor(R.color.text_label))
        } else {
            binding.textTotalBalance.text = state.totalBalance.formatCurrency(state.currency)
            binding.textDailyChangeAbs.text = state.dailyChangeAbs.formatCurrency(state.currency)
            binding.textDailyChangePerc.text = String.format("%%%+.2f", state.dailyChangePerc)
            
            val isPositive = state.dailyChangeAbs >= java.math.BigDecimal.ZERO
            val color = if (isPositive) R.color.accent_green else R.color.accent_red
            binding.textDailyChangePerc.setTextColor(requireContext().getColor(color))
            binding.imageDailyChangeTrending.imageTintList = android.content.res.ColorStateList.valueOf(requireContext().getColor(color))
            binding.imageDailyChangeTrending.rotation = if (isPositive) 0f else 180f
        }
        
        // Donut Chart simulation
        binding.textDonutCenterPercent.text = if (isPrivacyEnabled) "***%" else "%100"
        
        adapter.setItemsWithPrivacy(state.categorySummaries, isPrivacyEnabled)
    }
    private fun showPortfolioManagement() {
        val bottomSheet = PortfolioManagementBottomSheet()
        bottomSheet.show(childFragmentManager, "PortfolioManagement")
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}