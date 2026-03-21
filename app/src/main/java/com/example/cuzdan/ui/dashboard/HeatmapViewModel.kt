package com.example.cuzdan.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.cuzdan.data.local.entity.Asset
import com.example.cuzdan.data.repository.AssetRepository
import com.example.cuzdan.util.PreferenceManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HeatmapViewModel @Inject constructor(
    private val repository: AssetRepository,
    private val prefManager: PreferenceManager
) : ViewModel() {

    private val _assets = MutableStateFlow<List<Asset>>(emptyList())
    val assets: StateFlow<List<Asset>> = _assets.asStateFlow()

    init {
        observeAssets()
    }

    private fun observeAssets() {
        viewModelScope.launch {
            val portfolioId = prefManager.getSelectedPortfolioId().let { if (it == -1L) 1L else it }
            repository.getAssetsByPortfolioId(portfolioId).collectLatest { assetList ->
                // Filter out cash (NAKIT) if preferred, but heatmap usually includes all assets with price
                _assets.value = assetList.filter { it.assetType != com.example.cuzdan.data.local.entity.AssetType.NAKIT || it.symbol != "TRY" }
            }
        }
    }
}
