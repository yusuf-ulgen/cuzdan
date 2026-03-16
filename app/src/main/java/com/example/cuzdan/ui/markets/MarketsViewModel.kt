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
        observeAssets()
        refreshPrices()
    }

    private fun observeAssets() {
        // Tüm varlıkları (KRIPTO, BIST, DOVIZ, ALTIN, FON) birleştirip gösteriyoruz
        repository.getAssetsByPortfolioId(0) // Varsayılan portföy
            .onEach { assets ->
                _uiState.update { it.copy(prices = assets) }
                applyFilters()
            }
            .launchIn(viewModelScope)
    }

    fun refreshPrices() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            
            // Tüm API'leri paralel tetikle
            val cryptoFlow = repository.refreshCryptoPrices()
            val yahooFlow = repository.refreshYahooPrices()
            val fundFlow = repository.refreshFundPrices()

            // Hata takibi için basit birleştirme (Opsiyonel: Daha detaylı hata yönetimi yapılabilir)
            combine(cryptoFlow, yahooFlow, fundFlow) { c, y, f ->
                val error = when {
                    c is Resource.Error -> c.message
                    y is Resource.Error -> y.message
                    f is Resource.Error -> f.message
                    else -> null
                }
                error
            }.collect { error ->
                _uiState.update { it.copy(isLoading = false, errorMessage = error) }
            }
        }
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
            val filtered = state.prices.filter { asset ->
                val matchesType = state.selectedType == null || asset.assetType == state.selectedType
                val matchesSearch = asset.name.contains(state.searchQuery, ignoreCase = true) || 
                                   asset.symbol.contains(state.searchQuery, ignoreCase = true)
                matchesType && matchesSearch
            }
            state.copy(filteredPrices = filtered)
        }
    }
}
