package com.example.cuzdan.ui.reports

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.cuzdan.data.local.entity.Asset
import com.example.cuzdan.data.local.entity.Portfolio
import com.example.cuzdan.data.repository.AssetRepository
import com.example.cuzdan.data.repository.PortfolioRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.math.RoundingMode
import javax.inject.Inject

data class ReportsUiState(
    val portfolios: List<Portfolio> = emptyList(),
    val selectedPortfolioIndex: Int = 0,
    val assets: List<Asset> = emptyList(),
    val totalValue: BigDecimal = BigDecimal.ZERO,
    val totalProfitLoss: BigDecimal = BigDecimal.ZERO,
    val dailyChangeAbs: BigDecimal = BigDecimal.ZERO,
    val dailyChangePerc: BigDecimal = BigDecimal.ZERO,
    val categories: List<ReportCategory> = emptyList(),
    val isLoading: Boolean = false
)

@HiltViewModel
class ReportsViewModel @Inject constructor(
    private val assetRepository: AssetRepository,
    private val portfolioRepository: PortfolioRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ReportsUiState())
    val uiState: StateFlow<ReportsUiState> = _uiState.asStateFlow()

    init {
        loadPortfolios()
    }

    private fun loadPortfolios() {
        viewModelScope.launch {
            portfolioRepository.getAllPortfolios().collect { portfolios ->
                if (portfolios.isEmpty()) {
                    // Eğer hiç portföy yoksa varsayılan bir tane oluştur
                    portfolioRepository.insertPortfolio(Portfolio(name = "Ana Portföy"))
                    // Tekrar yüklemesi için beklemeye gerek yok, akış tetiklenecek
                } else {
                    _uiState.update { it.copy(portfolios = portfolios) }
                    loadAssetsForPortfolio(portfolios[_uiState.value.selectedPortfolioIndex].id)
                }
            }
        }
    }

    fun selectNextPortfolio() {
        val nextIndex = (_uiState.value.selectedPortfolioIndex + 1) % _uiState.value.portfolios.size
        _uiState.update { it.copy(selectedPortfolioIndex = nextIndex) }
        loadAssetsForPortfolio(_uiState.value.portfolios[nextIndex].id)
    }

    fun selectPrevPortfolio() {
        var prevIndex = _uiState.value.selectedPortfolioIndex - 1
        if (prevIndex < 0) prevIndex = _uiState.value.portfolios.size - 1
        _uiState.update { it.copy(selectedPortfolioIndex = prevIndex) }
        loadAssetsForPortfolio(_uiState.value.portfolios[prevIndex].id)
    }

    private fun loadAssetsForPortfolio(portfolioId: Long) {
        viewModelScope.launch {
            assetRepository.getAssetsByPortfolioId(portfolioId).collect { assets ->
                calculateStats(assets)
            }
        }
    }

    private fun calculateStats(assets: List<Asset>) {
        var totalValue = BigDecimal.ZERO
        var totalCost = BigDecimal.ZERO

        assets.forEach { asset ->
            val value = asset.amount.multiply(asset.currentPrice)
            val cost = asset.amount.multiply(asset.averageBuyPrice)
            totalValue = totalValue.add(value)
            totalCost = totalCost.add(cost)
        }

        val totalProfitLoss = totalValue.subtract(totalCost)
        
        // Kategori bazlı özetleri hesapla
        val categories = assets.groupBy { it.assetType }.map { (type, typeAssets) ->
            var catValue = BigDecimal.ZERO
            var catCost = BigDecimal.ZERO
            typeAssets.forEach { 
                catValue = catValue.add(it.amount.multiply(it.currentPrice))
                catCost = catCost.add(it.amount.multiply(it.averageBuyPrice))
            }
            
            val catProfitLoss = catValue.subtract(catCost)
            val catProfitPerc = if (catCost > BigDecimal.ZERO) {
                catProfitLoss.divide(catCost, 4, RoundingMode.HALF_UP).multiply(BigDecimal("100"))
            } else {
                BigDecimal.ZERO
            }

            ReportCategory(
                name = type.name,
                value = String.format("%.2f TL", catValue),
                changePerc = String.format("%%%+.1f", catProfitPerc),
                changeAbs = String.format("%.2f TL", catProfitLoss),
                isPositive = catProfitLoss >= BigDecimal.ZERO
            )
        }

        _uiState.update { 
            it.copy(
                assets = assets,
                totalValue = totalValue,
                totalProfitLoss = totalProfitLoss,
                categories = categories
            )
        }
    }
}
