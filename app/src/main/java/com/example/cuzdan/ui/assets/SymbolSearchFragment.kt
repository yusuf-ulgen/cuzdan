package com.example.cuzdan.ui.assets

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.example.cuzdan.data.local.entity.AssetType
import com.example.cuzdan.databinding.FragmentSymbolSearchBinding
import com.example.cuzdan.ui.markets.MarketAdapter
import dagger.hilt.android.AndroidEntryPoint
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import com.example.cuzdan.R

@AndroidEntryPoint
class SymbolSearchFragment : Fragment() {

    private var _binding: FragmentSymbolSearchBinding? = null
    private val binding get() = _binding!!
    
    private val viewModel: SymbolSearchViewModel by viewModels()
    private var assetType: String? = null
    private lateinit var adapter: MarketAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSymbolSearchBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        assetType = arguments?.getString("assetType")
        val type = try { AssetType.valueOf(assetType ?: "BIST") } catch (e: Exception) { AssetType.BIST }

        if (type == AssetType.KRIPTO) {
            binding.btnCurrencySwitcher.visibility = View.VISIBLE
            binding.btnCurrencySwitcher.setOnClickListener {
                viewModel.toggleCurrency(type)
            }
        }

        binding.btnFavorites.setOnClickListener {
            viewModel.toggleFavoritesOnly(type)
        }

        setupRecyclerView()
        setupSearch()
        observeViewModel()
        
        binding.btnBack.setOnClickListener {
            findNavController().navigateUp()
        }
        
        viewModel.loadInitialSymbols(type)
        val localizedTypeName = getLocalizedAssetTypeName(type)
        binding.textTitle.text = getString(R.string.asset_title_template, localizedTypeName)
    }

    private fun getLocalizedAssetTypeName(type: AssetType): String {
        return getString(when(type) {
            AssetType.KRIPTO -> R.string.asset_type_crypto
            AssetType.BIST -> R.string.asset_type_stocks
            AssetType.DOVIZ -> R.string.asset_type_currency
            AssetType.EMTIA -> R.string.asset_type_commodity
            AssetType.NAKIT -> R.string.asset_type_cash
            AssetType.FON -> R.string.asset_type_fund
        })
    }

    private fun setupRecyclerView() {
        val type = try { AssetType.valueOf(assetType ?: "BIST") } catch (e: Exception) { AssetType.BIST }
        adapter = MarketAdapter(
            showChange = false,
            onItemClick = { selectedAsset ->
                val action = SymbolSearchFragmentDirections.actionNavigationSymbolSearchToNavigationAssetDetail(
                    symbol = selectedAsset.symbol,
                    name = selectedAsset.name,
                    assetType = selectedAsset.assetType.name,
                    currency = selectedAsset.currency
                )
                findNavController().navigate(action)
            },
            onFavoriteClick = { asset ->
                viewModel.toggleFavorite(asset, type)
            }
        )
        binding.recyclerSymbols.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerSymbols.adapter = adapter
    }

    private fun setupSearch() {
        binding.editSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val type = try { AssetType.valueOf(assetType ?: "BIST") } catch (e: Exception) { AssetType.BIST }
                viewModel.search(s?.toString() ?: "", type)
            }
            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private fun observeViewModel() {
        viewModel.uiState
            .flowWithLifecycle(viewLifecycleOwner.lifecycle)
            .onEach { state ->
                binding.progressBar.visibility = if (state.isLoading) View.VISIBLE else View.GONE

                binding.btnCurrencySwitcher.setImageResource(if (state.currency == "TL") R.drawable.ic_tl else R.drawable.ic_usd)
                binding.btnFavorites.setImageResource(if (state.isFavoritesOnly) R.drawable.ic_star else R.drawable.ic_star_outline)
                adapter.setItems(state.results)
                
                if (state.error != null) {
                    Toast.makeText(context, state.error, Toast.LENGTH_SHORT).show()
                }
            }
            .launchIn(viewLifecycleOwner.lifecycleScope)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
