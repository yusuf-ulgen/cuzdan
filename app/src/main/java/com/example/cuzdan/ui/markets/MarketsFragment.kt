package com.example.cuzdan.ui.markets

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
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
import com.example.cuzdan.data.local.entity.AssetType
import com.example.cuzdan.databinding.FragmentMarketsBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MarketsFragment : Fragment() {

    private var _binding: FragmentMarketsBinding? = null
    private val binding get() = _binding!!

    private val viewModel: MarketsViewModel by viewModels()
    private lateinit var adapter: MarketAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMarketsBinding.inflate(inflater, container, false)
        
        setupRecyclerView()
        setupListeners()
        observeState()
        
        return binding.root
    }

    private fun setupRecyclerView() {
        adapter = MarketAdapter()
        binding.recyclerMarkets.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerMarkets.adapter = adapter
    }

    private fun setupListeners() {
        binding.editSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                viewModel.search(s?.toString() ?: "")
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        binding.chipGroupMarkets.setOnCheckedChangeListener { _, checkedId ->
            val type = when (checkedId) {
                R.id.chip_bist -> AssetType.BIST
                R.id.chip_crypto -> AssetType.KRIPTO
                R.id.chip_currency -> AssetType.DOVIZ
                else -> null
            }
            viewModel.filterByType(type)
        }
    }

    private fun observeState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    adapter.setItems(state.filteredPrices)
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
