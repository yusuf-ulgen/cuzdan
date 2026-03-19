package com.example.cuzdan.ui.markets

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.cuzdan.data.local.entity.Asset
import com.example.cuzdan.data.local.entity.AssetType
import com.example.cuzdan.data.repository.AssetRepository
import com.example.cuzdan.data.repository.PortfolioRepository
import com.example.cuzdan.util.PreferenceManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.math.BigDecimal
import javax.inject.Inject

data class AssetDetailUiState(
    val symbol: String = "",
    val name: String = "",
    val currentPrice: BigDecimal = BigDecimal.ZERO,
    val dailyChangePercentage: BigDecimal = BigDecimal.ZERO,
    val currency: String = "TRY",
    val history: List<Pair<Long, Double>> = emptyList(),
    val isLoading: Boolean = false,
    val isSaved: Boolean = false,
    val errorMessage: String? = null
)


@HiltViewModel
class AssetDetailViewModel @Inject constructor(
    private val repository: AssetRepository,
    private val portfolioRepository: PortfolioRepository,
    private val prefManager: PreferenceManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(AssetDetailUiState())
    val uiState: StateFlow<AssetDetailUiState> = _uiState.asStateFlow()

    fun init(symbol: String, name: String, typeString: String, currency: String = "TRY") {
        if (_uiState.value.symbol.isNotEmpty()) return // Zaten initialize edilmişse çalışma
        
        _uiState.update { it.copy(symbol = symbol, name = name, currency = currency) }
        loadHistory(symbol)
        observeCurrentPrice(symbol)
    }

    fun updateRange(range: String) {
        val interval = when (range) {
            "1d" -> "1m"
            "1w" -> "30m"
            "1mo" -> "1h"
            "1y" -> "1d"
            else -> "1d"
        }
        loadHistory(_uiState.value.symbol, range, interval)
    }


    private fun loadHistory(symbol: String, range: String = "1d", interval: String = "1m") {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val history = repository.getAssetHistory(symbol, range, interval)
            _uiState.update { it.copy(history = history, isLoading = false) }
        }
    }

    private fun observeCurrentPrice(symbol: String) {
        viewModelScope.launch {
            // repository.getMarketAssetBySymbolFlow (marketAssetDao'dan) kullanabiliriz
            // Veya direkt getLatestPrice üzerinden sadece fiyatı alabiliriz.
            // Ancak yüzdelik değişim de lazım olduğu için tüm objeyi dinlemek daha iyi.
            repository.getMarketAssetBySymbolFlow(symbol).collect { marketAsset ->
                marketAsset?.let {
                    _uiState.update { state ->
                        state.copy(
                            currentPrice = it.currentPrice,
                            dailyChangePercentage = it.dailyChangePercentage
                        )
                    }
                }
            }
        }
    }

    fun saveAsset(amount: BigDecimal, avgCost: BigDecimal, typeString: String) {
        viewModelScope.launch {
            val state = _uiState.value
            var portfolioId = prefManager.getSelectedPortfolioId()
            if (portfolioId == -1L) portfolioId = 1L // Ana Portföy varsayılan

            val assetType = AssetType.valueOf(typeString)
            
            // Nakit ise fiyat her zaman 1 (Sabit)
            val isCash = assetType == AssetType.NAKIT
            val finalCurrentPrice = if (isCash) BigDecimal.ONE else state.currentPrice
            val finalAvgCost = if (isCash) BigDecimal.ONE else avgCost
            
            val asset = Asset(
                symbol = state.symbol,
                name = state.name,
                amount = amount,
                averageBuyPrice = finalAvgCost,
                currentPrice = finalCurrentPrice,
                dailyChangePercentage = if (isCash) BigDecimal.ZERO else state.dailyChangePercentage,
                assetType = assetType,
                portfolioId = portfolioId,
                currency = if (isCash) "TRY" else state.currency
            )

            repository.upsertAsset(asset)
            _uiState.update { it.copy(isSaved = true) }
        }
    }
}
