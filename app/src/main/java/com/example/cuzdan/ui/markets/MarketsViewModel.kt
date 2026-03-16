package com.example.cuzdan.ui.markets

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.cuzdan.data.local.entity.Asset
import com.example.cuzdan.data.local.entity.AssetType
import com.example.cuzdan.data.repository.AssetRepository
import com.example.cuzdan.util.Resource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.math.BigDecimal
import javax.inject.Inject

data class MarketsUiState(
    val prices: List<Asset> = emptyList(),
    val filteredPrices: List<Asset> = emptyList(),
    val isLoading: Boolean = false,
    val selectedType: AssetType? = null,
    val searchQuery: String = "",
    val errorMessage: String? = null
)

@HiltViewModel
class MarketsViewModel @Inject constructor(
    private val repository: AssetRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(MarketsUiState())
    val uiState: StateFlow<MarketsUiState> = _uiState.asStateFlow()

    init {
        filterByType(AssetType.BIST) // Varsayılan olarak BIST göster
    }

    private fun loadMarketPrices(type: AssetType) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                val marketAssets = repository.getMarketAssets(type)
                _uiState.update { it.copy(prices = marketAssets, isLoading = false) }
                applyFilters()
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, errorMessage = "Hata: ${e.localizedMessage}") }
            }
        }
    }

    fun refreshPrices() {
        val currentType = _uiState.value.selectedType ?: AssetType.BIST
        loadMarketPrices(currentType)
    }

    fun filterByType(type: AssetType?) {
        _uiState.update { it.copy(selectedType = type) }
        type?.let { loadMarketPrices(it) } ?: applyFilters()
    }

    fun search(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
        applyFilters()
    }

    private fun applyFilters() {
        _uiState.update { state ->
            val filtered = state.prices.filter { asset ->
                val matchesSearch = asset.name.contains(state.searchQuery, ignoreCase = true) || 
                                   asset.symbol.contains(state.searchQuery, ignoreCase = true)
                matchesSearch
            }
            state.copy(filteredPrices = filtered)
        }
    }
}
