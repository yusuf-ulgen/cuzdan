package com.example.cuzdan.ui.dashboard

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.navigation.fragment.findNavController
import com.example.cuzdan.R
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.cuzdan.databinding.FragmentDashboardBinding
import com.example.cuzdan.ui.dashboard.DashboardAdapter
import com.example.cuzdan.ui.dashboard.DashboardUiState
import com.example.cuzdan.ui.dashboard.DashboardViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class DashboardFragment : Fragment() {

    private var _binding: FragmentDashboardBinding? = null
    private val binding get() = _binding!!

    private val viewModel: DashboardViewModel by viewModels()
    private val adapter = DashboardAdapter()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDashboardBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        setupRecyclerView()
        setupListeners()
        observeUiState()
    }

    private fun setupRecyclerView() {
        binding.rvAssets.layoutManager = LinearLayoutManager(requireContext())
        binding.rvAssets.adapter = adapter
    }

    private fun setupListeners() {
        binding.swipeRefresh.setOnRefreshListener {
            viewModel.refreshPrices()
        }
        binding.btnShowHeatmap.setOnClickListener {
            findNavController().navigate(R.id.action_navigation_home_to_heatmapFragment)
        }
    }

    private fun observeUiState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    when (state) {
                        is DashboardUiState.Loading -> {
                            handleLoading()
                        }
                        is DashboardUiState.Success -> {
                            handleSuccess(state)
                        }
                        is DashboardUiState.Error -> {
                            handleError(state)
                        }
                    }
                }
            }
        }
    }

    private fun handleLoading() {
        binding.shimmerView.startShimmer()
        binding.shimmerView.visibility = View.VISIBLE
        binding.rvAssets.visibility = View.GONE
        binding.tvError.visibility = View.GONE
    }

    private fun handleSuccess(state: DashboardUiState.Success) {
        binding.swipeRefresh.isRefreshing = false
        binding.shimmerView.stopShimmer()
        binding.shimmerView.visibility = View.GONE
        binding.rvAssets.visibility = View.VISIBLE
        binding.tvError.visibility = View.GONE
        adapter.submitList(state.assets)
    }

    private fun handleError(state: DashboardUiState.Error) {
        binding.swipeRefresh.isRefreshing = false
        binding.shimmerView.stopShimmer()
        binding.shimmerView.visibility = View.GONE
        binding.rvAssets.visibility = View.GONE
        binding.tvError.visibility = View.VISIBLE
        binding.tvError.text = state.message
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}