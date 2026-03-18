package com.example.cuzdan.ui.markets

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.cuzdan.R
import com.example.cuzdan.data.local.entity.AssetType
import com.example.cuzdan.data.repository.AssetRepository
import com.example.cuzdan.util.Resource
import com.example.cuzdan.data.local.entity.MarketAsset
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.math.BigDecimal
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)


data class MarketsUiState(
    val prices: List<MarketAsset> = emptyList(),
    val filteredPrices: List<MarketAsset> = emptyList(),
    val isLoading: Boolean = false,
    val selectedType: AssetType = AssetType.BIST,
    val searchQuery: String = "",
    val errorMessage: String? = null
)

@HiltViewModel
class MarketsViewModel @Inject constructor(
    private val repository: AssetRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    private val _selectedType = MutableStateFlow(AssetType.BIST)
    private val _isLoading = MutableStateFlow(false)
    private val _errorMessage = MutableStateFlow<String?>(null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<MarketsUiState> = combine(

        _selectedType.flatMapLatest { repository.getMarketAssetsFlow(it) },
        _searchQuery,
        _selectedType,
        _isLoading,
        _errorMessage
    ) { prices, query, type, loading, error ->
        val filtered = prices.filter { asset ->
            asset.name.contains(query, ignoreCase = true) || 
            asset.symbol.contains(query, ignoreCase = true)
        }
        MarketsUiState(
            prices = prices,
            filteredPrices = filtered,
            isLoading = loading,
            selectedType = type,
            searchQuery = query,
            errorMessage = error
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), MarketsUiState())

    init {
        refreshPrices()
    }

    fun refreshPrices() {
        val currentType = _selectedType.value
        viewModelScope.launch {
            repository.refreshMarketAssets(currentType).collect { resource ->
                when (resource) {
                    is Resource.Loading -> _isLoading.value = true
                    is Resource.Success -> {
                        _isLoading.value = false
                        _errorMessage.value = null
                    }
                    is Resource.Error -> {
                        _isLoading.value = false
                        _errorMessage.value = resource.message
                    }
                }
            }
        }
    }

    fun filterByType(type: AssetType?) {
        type?.let { 
            _selectedType.value = it
            refreshPrices() 
        }
    }

    fun search(query: String) {
        _searchQuery.value = query
    }
}

