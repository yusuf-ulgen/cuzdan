package com.yusufulgen.cuzdan.ui.markets

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yusufulgen.cuzdan.R
import com.yusufulgen.cuzdan.data.local.entity.AssetType
import com.yusufulgen.cuzdan.data.repository.AssetRepository
import com.yusufulgen.cuzdan.util.Resource
import com.yusufulgen.cuzdan.data.local.entity.MarketAsset
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.math.BigDecimal
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
enum class MarketsSortType {
    NAME_ASC, NAME_DESC, PRICE_ASC, PRICE_DESC, CHANGE_ASC, CHANGE_DESC
}

data class MarketsUiState(
    val prices: List<MarketAsset> = emptyList(),
    val filteredPrices: List<MarketAsset> = emptyList(),
    val isLoading: Boolean = false,
    val selectedType: AssetType? = null,
    val searchQuery: String = "",
    val isFavoritesOnly: Boolean = false,
    val sortType: MarketsSortType = MarketsSortType.NAME_ASC,
    val errorMessage: String? = null
)

@HiltViewModel
class MarketsViewModel @Inject constructor(
    private val repository: AssetRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    private val _selectedType = MutableStateFlow<AssetType?>(null)
    private val _isFavoritesOnly = MutableStateFlow(false)
    private val _sortType = MutableStateFlow(MarketsSortType.NAME_ASC)
    private val _isLoading = MutableStateFlow(false)
    private val _errorMessage = MutableStateFlow<String?>(null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<MarketsUiState> = combine(
        _selectedType.flatMapLatest { repository.getMarketAssetsFlow(it) },
        _searchQuery,
        _selectedType,
        _isFavoritesOnly,
        _sortType,
        _isLoading,
        _errorMessage
    ) { array ->
        val prices = (array[0] as? List<MarketAsset>) ?: emptyList()
        val query = (array[1] as? String) ?: ""
        val type = array[2] as? AssetType
        val favoritesOnly = (array[3] as? Boolean) ?: false
        val sortType = (array[4] as? MarketsSortType) ?: MarketsSortType.NAME_ASC
        val loading = (array[5] as? Boolean) ?: false
        val error = array[6] as? String

        val prioritySymbols = listOf("BTC", "ETH", "USDT", "SOL", "BNB", "XRP", "USDC", "ADA", "DOGE", "AVAX", "SHIB", "DOT", "TRX", "LINK", "MATIC")

        val filtered = prices.filter { asset ->
            (asset.name.contains(query, ignoreCase = true) || 
             asset.symbol.contains(query, ignoreCase = true) || 
             (asset.fullName?.contains(query, ignoreCase = true) == true)) &&
            (!favoritesOnly || asset.isFavorite)
        }.let { list ->
            // Apply priority sorting for KRIPTO
            val sortedList = if (type == AssetType.KRIPTO) {
                list.sortedWith(compareByDescending<MarketAsset> { asset ->
                    val cleanSym = asset.symbol.replace("USDT", "").replace("TRY", "")
                    val priorityIndex = prioritySymbols.indexOf(cleanSym)
                    if (priorityIndex != -1) 1000 - priorityIndex else 0
                }.thenBy { it.name })
            } else {
                list
            }

            when (sortType) {
                MarketsSortType.NAME_ASC -> if (type == AssetType.KRIPTO) sortedList else sortedList.sortedBy { it.name }
                MarketsSortType.NAME_DESC -> sortedList.sortedByDescending { it.name }
                MarketsSortType.PRICE_ASC -> sortedList.sortedBy { it.currentPrice }
                MarketsSortType.PRICE_DESC -> sortedList.sortedByDescending { it.currentPrice }
                MarketsSortType.CHANGE_ASC -> sortedList.sortedBy { it.dailyChangePercentage }
                MarketsSortType.CHANGE_DESC -> sortedList.sortedByDescending { it.dailyChangePercentage }
            }
        }
        MarketsUiState(
            prices = prices,
            filteredPrices = filtered,
            isLoading = loading,
            selectedType = type,
            searchQuery = query,
            isFavoritesOnly = favoritesOnly,
            sortType = sortType,
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
        _selectedType.value = type
        refreshPrices()
    }

    fun search(query: String) {
        _searchQuery.value = query
    }

    fun toggleFavoritesOnly() {
        _isFavoritesOnly.value = !_isFavoritesOnly.value
    }

    fun toggleFavorite(asset: MarketAsset) {
        viewModelScope.launch {
            repository.toggleFavorite(asset.symbol, asset.assetType, !asset.isFavorite)
        }
    }

    fun setSortType(sortType: MarketsSortType) {
        _sortType.value = sortType
    }
}

