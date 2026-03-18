package com.example.cuzdan.ui.assets

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.cuzdan.R
import com.example.cuzdan.data.local.entity.MarketAsset
import com.example.cuzdan.data.local.entity.AssetType
import com.example.cuzdan.data.repository.AssetRepository
import com.example.cuzdan.data.local.entity.Asset
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.math.BigDecimal
import javax.inject.Inject
import com.example.cuzdan.util.PreferenceManager

data class SymbolSearchUiState(
    val results: List<MarketAsset> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val currency: String = "TL"
)

@HiltViewModel
class SymbolSearchViewModel @Inject constructor(
    private val repository: AssetRepository,
    private val prefManager: PreferenceManager,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(SymbolSearchUiState(currency = prefManager.getCryptoCurrency()))
    val uiState: StateFlow<SymbolSearchUiState> = _uiState.asStateFlow()

    private var searchJob: Job? = null
    private var usdRate: BigDecimal = BigDecimal("32.5")

    init {
        viewModelScope.launch {
            repository.getYahooPriceOnce("USDTRY=X")?.let { usdRate = it }
        }
    }

    fun loadInitialSymbols(type: AssetType) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val marketAssets = repository.getMarketAssetsOnce(type)
                if (marketAssets.isEmpty()) {
                    repository.refreshMarketAssets(type).collect { resource ->
                        if (resource is com.example.cuzdan.util.Resource.Success) {
                            val refreshedAssets = repository.getMarketAssetsOnce(type)
                            _uiState.update { it.copy(results = transformAssets(refreshedAssets, type), isLoading = false) }
                        } else if (resource is com.example.cuzdan.util.Resource.Error) {
                            _uiState.update { it.copy(isLoading = false, error = resource.message) }
                        }
                    }
                } else {
                    _uiState.update { it.copy(results = transformAssets(marketAssets, type), isLoading = false) }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = "${context.getString(R.string.error_loading)}: ${e.localizedMessage}") }
            }
        }
    }

    fun search(query: String, type: AssetType) {
        searchJob?.cancel()
        if (query.isBlank()) {
            loadInitialSymbols(type)
            return
        }
        searchJob = viewModelScope.launch {
            delay(500)
            _uiState.update { it.copy(isLoading = true) }
            val searchResults = repository.searchAssets(query, type)
            _uiState.update { it.copy(results = transformAssets(searchResults, type), isLoading = false) }
        }
    }

    private fun transformAssets(assets: List<MarketAsset>, type: AssetType): List<MarketAsset> {
        val currency = _uiState.value.currency
        if (type != AssetType.KRIPTO || currency == "USD") return assets
        
        return assets.map { asset ->
            asset.copy(
                currentPrice = asset.currentPrice.multiply(usdRate).setScale(2, java.math.RoundingMode.HALF_UP),
                currency = "TRY"
            )
        }
    }

    fun toggleCurrency(type: AssetType) {
        val newCurrency = if (_uiState.value.currency == "TL") "USD" else "TL"
        prefManager.setCryptoCurrency(newCurrency)
        _uiState.update { it.copy(currency = newCurrency) }
        loadInitialSymbols(type)
    }

    fun saveAssetFromMarket(marketAsset: MarketAsset, portfolioId: Long) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val asset = Asset(
                symbol = marketAsset.symbol,
                name = marketAsset.name,
                amount = BigDecimal.ZERO,
                averageBuyPrice = BigDecimal.ZERO,
                currentPrice = marketAsset.currentPrice,
                dailyChangePercentage = marketAsset.dailyChangePercentage,
                assetType = marketAsset.assetType,
                portfolioId = portfolioId
            )
            repository.upsertAsset(asset)
            _uiState.update { it.copy(results = emptyList(), isLoading = false) }
        }
    }
}

