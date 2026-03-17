package com.example.cuzdan.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.cuzdan.data.local.entity.Asset
import com.example.cuzdan.data.local.entity.AssetType
import com.example.cuzdan.data.local.entity.Portfolio
import com.example.cuzdan.data.repository.AssetRepository
import com.example.cuzdan.data.repository.PortfolioRepository
import com.example.cuzdan.util.PreferenceManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.math.RoundingMode
import javax.inject.Inject

data class WalletUiState(
    val portfolios: List<Portfolio> = emptyList(),
    val selectedPortfolioId: Long = 1, // 1: Ana Portföy, -1: Portföyler Toplamı
    val selectedPortfolioName: String = "",
    val totalBalance: BigDecimal = BigDecimal.ZERO,
    val dailyChangeAbs: BigDecimal = BigDecimal.ZERO,
    val dailyChangePerc: BigDecimal = BigDecimal.ZERO,
    val categorySummaries: List<WalletCategorySummary> = emptyList(),
    val isLoading: Boolean = false,
    val currency: String = "TL"
)

data class WalletCategorySummary(
    val type: AssetType,
    val title: String,
    val totalValue: BigDecimal,
    val totalProfitLoss: BigDecimal,
    val profitLossPerc: BigDecimal,
    val assets: List<Asset> = emptyList(),
    val isExpanded: Boolean = false
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val assetRepository: AssetRepository,
    private val portfolioRepository: PortfolioRepository,
    private val prefManager: PreferenceManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(WalletUiState())
    val uiState: StateFlow<WalletUiState> = _uiState.asStateFlow()

    private val _selectedPortfolioId = MutableStateFlow(prefManager.getSelectedPortfolioId())

    init {
        observePortfolios()
        observeAssets()
        listenToCurrencyChanges()
    }

    private fun listenToCurrencyChanges() {
        viewModelScope.launch {
            _uiState.update { it.copy(currency = prefManager.getCurrency()) }
        }
    }

    private fun observePortfolios() {
        viewModelScope.launch {
            portfolioRepository.getAllPortfolios().collect { portfolios ->
                if (portfolios.isNotEmpty()) {
                    val currentId = _selectedPortfolioId.value
                    val selectedPortfolio = if (currentId == -1L) null else portfolios.find { it.id == currentId } ?: portfolios.first()
                    
                    val newId = if (currentId == -1L) -1L else selectedPortfolio?.id ?: 1L
                    val newName = if (currentId == -1L) "Portföyler Toplamı" else selectedPortfolio?.name ?: ""
                    
                    _selectedPortfolioId.value = newId
                    _uiState.update { it.copy(
                        portfolios = portfolios,
                        selectedPortfolioId = newId,
                        selectedPortfolioName = newName
                    ) }
                } else {
                    portfolioRepository.getOrCreateDefaultPortfolioId()
                }
            }
        }
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private fun observeAssets() {
        viewModelScope.launch {
            _selectedPortfolioId.flatMapLatest { id ->
                if (id == -1L) {
                    portfolioRepository.getIncludedPortfolios().flatMapLatest { included ->
                        if (included.isEmpty()) {
                            flowOf(emptyList<Asset>())
                        } else {
                            val flows = included.map { p -> assetRepository.getAssetsByPortfolioId(p.id) }
                            combine(flows) { lists ->
                                lists.flatMap { it }.let { mergeDuplicateAssets(it) }
                            }
                        }
                    }
                } else {
                    assetRepository.getAssetsByPortfolioId(id)
                }
            }.collect { assets ->
                calculateWeights(assets)
            }
        }
    }

    fun selectPortfolio(id: Long) {
        val name = if (id == -1L) "Portföyler Toplamı" else _uiState.value.portfolios.find { it.id == id }?.name ?: ""
        _selectedPortfolioId.value = id
        prefManager.setSelectedPortfolioId(id)
        _uiState.update { it.copy(selectedPortfolioId = id, selectedPortfolioName = name) }
    }

    fun selectNextPortfolio() {
        val portfolios = _uiState.value.portfolios
        if (portfolios.isEmpty()) return
        
        val currentIndex = if (_selectedPortfolioId.value == -1L) -1 else portfolios.indexOfFirst { it.id == _selectedPortfolioId.value }
        val nextIndex = currentIndex + 1
        
        if (nextIndex >= portfolios.size) {
            selectPortfolio(-1L)
        } else {
            selectPortfolio(portfolios[nextIndex].id)
        }
    }

    fun selectPrevPortfolio() {
        val portfolios = _uiState.value.portfolios
        if (portfolios.isEmpty()) return
        
        val currentIndex = if (_selectedPortfolioId.value == -1L) -1 else portfolios.indexOfFirst { it.id == _selectedPortfolioId.value }
        
        if (currentIndex == -1) {
            selectPortfolio(portfolios.last().id)
        } else if (currentIndex == 0) {
            selectPortfolio(-1L)
        } else {
            selectPortfolio(portfolios[currentIndex - 1].id)
        }
    }

    private fun mergeDuplicateAssets(assets: List<Asset>): List<Asset> {
        return assets.groupBy { it.symbol }.map { (symbol, symbolAssets) ->
            if (symbolAssets.size == 1) return@map symbolAssets.first()
            
            var totalAmount = BigDecimal.ZERO
            var totalCost = BigDecimal.ZERO
            symbolAssets.forEach {
                totalAmount = totalAmount.add(it.amount)
                totalCost = totalCost.add(it.amount.multiply(it.averageBuyPrice))
            }
            
            val avgPrice = if (totalAmount > BigDecimal.ZERO) totalCost.divide(totalAmount, 8, RoundingMode.HALF_UP) else BigDecimal.ZERO
            
            symbolAssets.first().copy(
                amount = totalAmount,
                averageBuyPrice = avgPrice
            )
        }
    }

    private fun calculateWeights(assets: List<Asset>) {
        viewModelScope.launch {
            val currency = prefManager.getCurrency()
            var exchangeRate = BigDecimal.ONE

            if (currency != "TL") {
                val rateSymbol = if (currency == "USD") "TRY=X" else "EURTRY=X"
                val rateAsset = assetRepository.getOtherAssets().first().find { it.symbol == rateSymbol }
                if (rateAsset != null && rateAsset.currentPrice > BigDecimal.ZERO) {
                    exchangeRate = rateAsset.currentPrice
                }
            }

            var totalBalance = BigDecimal.ZERO
            var totalCost = BigDecimal.ZERO

            assets.forEach { asset ->
                totalBalance = totalBalance.add(asset.amount.multiply(asset.currentPrice))
                totalCost = totalCost.add(asset.amount.multiply(asset.averageBuyPrice))
            }

            // Kur dönüşümü uygula
            val convertedBalance = totalBalance.divide(exchangeRate, 2, RoundingMode.HALF_UP)
            val convertedCost = totalCost.divide(exchangeRate, 2, RoundingMode.HALF_UP)

            val totalProfitLoss = convertedBalance.subtract(convertedCost)
            val totalProfitPerc = if (totalCost > BigDecimal.ZERO) {
                totalBalance.subtract(totalCost).divide(totalCost, 4, RoundingMode.HALF_UP).multiply(BigDecimal("100"))
            } else {
                BigDecimal.ZERO
            }

            val categories = assets.groupBy { it.assetType }.map { (type, typeAssets) ->
                var catValue = BigDecimal.ZERO
                var catCost = BigDecimal.ZERO
                typeAssets.forEach {
                    catValue = catValue.add(it.amount.multiply(it.currentPrice))
                    catCost = catCost.add(it.amount.multiply(it.averageBuyPrice))
                }
                
                // Kategori dönüşümü
                val convCatValue = catValue.divide(exchangeRate, 2, RoundingMode.HALF_UP)
                val convCatCost = catCost.divide(exchangeRate, 2, RoundingMode.HALF_UP)
                
                val catPL = convCatValue.subtract(convCatCost)
                val catPLPerc = if (catCost > BigDecimal.ZERO) {
                    catValue.subtract(catCost).divide(catCost, 4, RoundingMode.HALF_UP).multiply(BigDecimal("100"))
                } else {
                    BigDecimal.ZERO
                }

                WalletCategorySummary(
                    type = type,
                    title = when(type) {
                        AssetType.KRIPTO -> "Kripto"
                        AssetType.BIST -> "BIST"
                        AssetType.DOVIZ -> "Döviz"
                        AssetType.EMTIA -> "Emtia"
                        AssetType.NAKIT -> "Nakit"
                        AssetType.FON -> "Fon"
                    },
                    totalValue = convCatValue,
                    totalProfitLoss = catPL,
                    profitLossPerc = catPLPerc,
                    assets = typeAssets
                )
            }

            _uiState.update { 
                it.copy(
                    totalBalance = convertedBalance,
                    dailyChangeAbs = totalProfitLoss,
                    dailyChangePerc = totalProfitPerc,
                    categorySummaries = categories,
                    currency = currency
                )
            }
        }
    }
}