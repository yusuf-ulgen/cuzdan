package com.example.cuzdan.ui.markets

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.cuzdan.data.local.entity.AssetType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import java.math.BigDecimal
import javax.inject.Inject

data class MarketPrice(
    val id: String,
    val name: String,
    val symbol: String,
    val currentPrice: BigDecimal,
    val dailyChangePerc: BigDecimal,
    val type: AssetType
)

data class MarketsUiState(
    val allPrices: List<MarketPrice> = emptyList(),
    val filteredPrices: List<MarketPrice> = emptyList(),
    val isLoading: Boolean = false,
    val selectedType: AssetType? = null,
    val searchQuery: String = ""
)

@HiltViewModel
class MarketsViewModel @Inject constructor() : ViewModel() {

    private val _uiState = MutableStateFlow(MarketsUiState())
    val uiState: StateFlow<MarketsUiState> = _uiState.asStateFlow()

    init {
        loadMockData()
    }

    private fun loadMockData() {
        val mockData = listOf(
            MarketPrice("1", "Bitcoin", "BTC/USDT", BigDecimal("65430.20"), BigDecimal("2.45"), AssetType.KRIPTO),
            MarketPrice("2", "Ethereum", "ETH/USDT", BigDecimal("3520.15"), BigDecimal("-1.20"), AssetType.KRIPTO),
            MarketPrice("3", "Gram Altın", "GA", BigDecimal("2450.75"), BigDecimal("0.85"), AssetType.ALTIN),
            MarketPrice("4", "Dolar", "USD/TRY", BigDecimal("32.45"), BigDecimal("0.15"), AssetType.DOVIZ),
            MarketPrice("5", "Ereğli Demir Çelik", "EREGL", BigDecimal("45.60"), BigDecimal("3.20"), AssetType.BIST),
            MarketPrice("6", "Türk Hava Yolları", "THYAO", BigDecimal("285.40"), BigDecimal("-0.75"), AssetType.BIST),
            MarketPrice("7", "Solana", "SOL/USDT", BigDecimal("145.20"), BigDecimal("5.60"), AssetType.KRIPTO),
            MarketPrice("8", "Euro", "EUR/TRY", BigDecimal("35.12"), BigDecimal("-0.05"), AssetType.DOVIZ)
        )
        _uiState.update { it.copy(allPrices = mockData, filteredPrices = mockData) }
    }

    fun filterByType(type: AssetType?) {
        _uiState.update { it.copy(selectedType = type) }
        applyFilters()
    }

    fun search(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
        applyFilters()
    }

    private fun applyFilters() {
        _uiState.update { state ->
            val filtered = state.allPrices.filter { price ->
                val matchesType = state.selectedType == null || price.type == state.selectedType
                val matchesSearch = price.name.contains(state.searchQuery, ignoreCase = true) || 
                                   price.symbol.contains(state.searchQuery, ignoreCase = true)
                matchesType && matchesSearch
            }
            state.copy(filteredPrices = filtered)
        }
    }
}
