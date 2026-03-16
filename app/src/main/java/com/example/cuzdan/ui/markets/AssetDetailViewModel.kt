package com.example.cuzdan.ui.markets

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.cuzdan.data.local.entity.Asset
import com.example.cuzdan.data.local.entity.AssetType
import com.example.cuzdan.data.repository.AssetRepository
import com.example.cuzdan.data.repository.PortfolioRepository
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
    val history: List<Pair<Long, Double>> = emptyList(),
    val isLoading: Boolean = false,
    val isSaved: Boolean = false,
    val errorMessage: String? = null
)

@HiltViewModel
class AssetDetailViewModel @Inject constructor(
    private val repository: AssetRepository,
    private val portfolioRepository: PortfolioRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AssetDetailUiState())
    val uiState: StateFlow<AssetDetailUiState> = _uiState.asStateFlow()

    fun init(symbol: String, name: String, typeString: String) {
        _uiState.update { it.copy(symbol = symbol, name = name) }
        loadHistory(symbol)
        fetchCurrentPrice(symbol, typeString)
    }

    private fun loadHistory(symbol: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val history = repository.getAssetHistory(symbol)
            _uiState.update { it.copy(history = history, isLoading = false) }
        }
    }

    private fun fetchCurrentPrice(symbol: String, typeString: String) {
        viewModelScope.launch {
            try {
                val type = AssetType.valueOf(typeString)
                val marketAssets = repository.getMarketAssets(type)
                val asset = marketAssets.find { it.symbol == symbol }
                asset?.let {
                    _uiState.update { state ->
                        state.copy(
                            currentPrice = it.currentPrice,
                            dailyChangePercentage = it.dailyChangePercentage
                        )
                    }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = "Fiyat bilgisi alınamadı") }
            }
        }
    }

    fun saveAsset(amount: BigDecimal, avgCost: BigDecimal, typeString: String) {
        viewModelScope.launch {
            val state = _uiState.value
            val portfolioId = portfolioRepository.getOrCreateDefaultPortfolioId()
            
            val asset = Asset(
                symbol = state.symbol,
                name = state.name,
                amount = amount,
                averageBuyPrice = avgCost,
                currentPrice = state.currentPrice,
                dailyChangePercentage = state.dailyChangePercentage,
                assetType = AssetType.valueOf(typeString),
                portfolioId = portfolioId
            )
            repository.upsertAsset(asset)
            _uiState.update { it.copy(isSaved = true) }
        }
    }
}
