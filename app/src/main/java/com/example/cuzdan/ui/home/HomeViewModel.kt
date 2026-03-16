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
    val selectedPortfolioIndex: Int = 0,
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

    init {
        loadPortfolios()
        listenToCurrencyChanges()
    }

    private fun listenToCurrencyChanges() {
        viewModelScope.launch {
            // Basit bir trigger için periyodik kontrol veya değişim anında tetikleme mekanizması
            // Şimdilik init ve her portföy değişiminde kur güncellenecek
            _uiState.update { it.copy(currency = prefManager.getCurrency()) }
        }
    }

    private fun loadPortfolios() {
        viewModelScope.launch {
            portfolioRepository.getAllPortfolios().collect { portfolios ->
                if (portfolios.isNotEmpty()) {
                    _uiState.update { it.copy(portfolios = portfolios) }
                    loadAssetsForPortfolio(portfolios[_uiState.value.selectedPortfolioIndex].id)
                }
            }
        }
    }

    fun selectNextPortfolio() {
        val size = _uiState.value.portfolios.size
        if (size == 0) return
        val nextIndex = (_uiState.value.selectedPortfolioIndex + 1) % size
        _uiState.update { it.copy(selectedPortfolioIndex = nextIndex) }
        loadAssetsForPortfolio(_uiState.value.portfolios[nextIndex].id)
    }

    fun selectPrevPortfolio() {
        val size = _uiState.value.portfolios.size
        if (size == 0) return
        var prevIndex = _uiState.value.selectedPortfolioIndex - 1
        if (prevIndex < 0) prevIndex = size - 1
        _uiState.update { it.copy(selectedPortfolioIndex = prevIndex) }
        loadAssetsForPortfolio(_uiState.value.portfolios[prevIndex].id)
    }

    private fun loadAssetsForPortfolio(portfolioId: Long) {
        viewModelScope.launch {
            assetRepository.getAssetsByPortfolioId(portfolioId).collect { assets ->
                calculateWeights(assets)
            }
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