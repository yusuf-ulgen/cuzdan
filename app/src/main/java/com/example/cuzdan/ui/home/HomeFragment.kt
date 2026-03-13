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
import com.example.cuzdan.databinding.FragmentHomeBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.Locale

@AndroidEntryPoint
class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private val viewModel: HomeViewModel by viewModels()
    private val currencyFormat = NumberFormat.getCurrencyInstance(Locale("tr", "TR"))
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
        if (state.portfolios.isNotEmpty()) {
            binding.textPortfolioName.text = state.portfolios[state.selectedPortfolioIndex].name
        }

        binding.textTotalBalance.text = currencyFormat.format(state.totalBalance)
        binding.textDailyChangeAbs.text = currencyFormat.format(state.dailyChangeAbs)
        binding.textDailyChangePerc.text = String.format("%%%+.2f", state.dailyChangePerc)
        
        // Donut Chart simulation
        binding.textDonutCenterPercent.text = "%100"
        
        adapter.setItems(state.categorySummaries)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}