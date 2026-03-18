package com.example.cuzdan.ui.reports

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.cuzdan.R
import com.example.cuzdan.data.local.entity.Asset
import com.example.cuzdan.data.local.entity.AssetType
import com.example.cuzdan.data.local.entity.Portfolio
import com.example.cuzdan.data.repository.AssetRepository
import com.example.cuzdan.data.repository.PortfolioRepository
import com.example.cuzdan.util.PreferenceManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.math.RoundingMode
import javax.inject.Inject

data class ReportsUiState(
    val categories: List<ReportCategory> = emptyList(),
    val totalValue: BigDecimal = BigDecimal.ZERO,
    val totalProfitLoss: BigDecimal = BigDecimal.ZERO,
    val currency: String = "TL",
    val portfolios: List<Portfolio> = emptyList(),
    val isLoading: Boolean = false
)

@HiltViewModel
class ReportsViewModel @Inject constructor(
    private val assetRepository: AssetRepository,
    private val portfolioRepository: PortfolioRepository,
    private val prefManager: PreferenceManager,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(ReportsUiState())
    val uiState: StateFlow<ReportsUiState> = _uiState.asStateFlow()

    private val _currentCurrency = MutableStateFlow(prefManager.getReportsCurrency())
    private val _selectedPortfolioId = MutableStateFlow(prefManager.getSelectedPortfolioId())
    private var lastAssets: List<Asset> = emptyList()

    init {
        observePortfolios()
        observeAssets()
    }

    private fun observePortfolios() {
        viewModelScope.launch {
            portfolioRepository.getAllPortfolios().collect { portfolios ->
                _uiState.update { it.copy(portfolios = portfolios) }
            }
        }
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private fun observeAssets() {
        viewModelScope.launch {
            combine(
                _selectedPortfolioId.flatMapLatest { id ->
                    if (id == -1L) {
                        portfolioRepository.getIncludedPortfolios().flatMapLatest { included ->
                            if (included.isEmpty()) flowOf(emptyList<Asset>())
                            else {
                                val flows = included.map { p -> assetRepository.getAssetsByPortfolioId(p.id) }
                                combine(flows) { lists -> lists.flatMap { it }.let { mergeDuplicateAssets(it) } }
                            }
                        }
                    } else assetRepository.getAssetsByPortfolioId(id)
                },
                _currentCurrency
            ) { assets, currency ->
                lastAssets = assets
                calculateReports(assets, currency)
            }.collect()
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
            symbolAssets.first().copy(amount = totalAmount, averageBuyPrice = avgPrice)
        }
    }

    private fun calculateReports(assets: List<Asset>, currency: String, resolveContext: Context = context) {
        var exchangeRate = BigDecimal.ONE
        if (currency == "USD") exchangeRate = BigDecimal("32.5")
        else if (currency == "EUR") exchangeRate = BigDecimal("35.2")

        var totalValue = BigDecimal.ZERO
        var totalProfitLoss = BigDecimal.ZERO

        assets.forEach {
            val assetValue = it.amount.multiply(it.currentPrice)
            val assetCost = it.amount.multiply(it.averageBuyPrice)
            totalValue = totalValue.add(assetValue)
            totalProfitLoss = totalProfitLoss.add(assetValue.subtract(assetCost))
        }

        val convTotalValue = totalValue.divide(exchangeRate, 2, RoundingMode.HALF_UP)
        val convTotalProfitLoss = totalProfitLoss.divide(exchangeRate, 2, RoundingMode.HALF_UP)

        val reportCategories = assets.groupBy { it.assetType }.map { (type, typeAssets) ->
            var catValue = BigDecimal.ZERO
            var catCost = BigDecimal.ZERO
            typeAssets.forEach {
                catValue = catValue.add(it.amount.multiply(it.currentPrice))
                catCost = catCost.add(it.amount.multiply(it.averageBuyPrice))
            }
            
            val convCatValue = catValue.divide(exchangeRate, 2, RoundingMode.HALF_UP)
            val convCatCost = catCost.divide(exchangeRate, 2, RoundingMode.HALF_UP)
            val catPLAbs = convCatValue.subtract(convCatCost)
            val catPLPerc = if (catCost > BigDecimal.ZERO) {
                catValue.subtract(catCost).divide(catCost, 4, RoundingMode.HALF_UP).multiply(BigDecimal(100))
            } else BigDecimal.ZERO

            ReportCategory(
                name = getLocalizedAssetTypeName(type, resolveContext),
                totalValue = convCatValue,
                changePerc = catPLPerc,
                changeAbs = catPLAbs,
                assets = typeAssets
            )
        }

        _uiState.update { it.copy(
            categories = reportCategories,
            totalValue = convTotalValue,
            totalProfitLoss = convTotalProfitLoss,
            currency = currency
        )}
    }

    fun selectPortfolio(id: Long) {
        _selectedPortfolioId.value = id
        prefManager.setSelectedPortfolioId(id)
    }

    fun selectNextPortfolio() {
        val portfolios = _uiState.value.portfolios
        if (portfolios.isEmpty()) return
        val currentId = _selectedPortfolioId.value
        val currentIndex = if (currentId == -1L) -1 else portfolios.indexOfFirst { it.id == currentId }
        val nextIndex = currentIndex + 1
        if (nextIndex >= portfolios.size) selectPortfolio(-1L) else selectPortfolio(portfolios[nextIndex].id)
    }

    fun selectPrevPortfolio() {
        val portfolios = _uiState.value.portfolios
        if (portfolios.isEmpty()) return
        val currentId = _selectedPortfolioId.value
        val currentIndex = if (currentId == -1L) -1 else portfolios.indexOfFirst { it.id == currentId }
        if (currentIndex == -1) selectPortfolio(portfolios.last().id)
        else if (currentIndex == 0) selectPortfolio(-1L)
        else selectPortfolio(portfolios[currentIndex - 1].id)
    }

    fun setCurrency(currency: String) {
        prefManager.setReportsCurrency(currency)
        _currentCurrency.value = currency
    }

    fun refreshLocalization(activityContext: Context) {
        calculateReports(lastAssets, _currentCurrency.value, activityContext)
    }

    private fun getLocalizedAssetTypeName(type: AssetType, resolveContext: Context): String {
        return resolveContext.getString(when(type) {
            AssetType.KRIPTO -> R.string.asset_type_crypto
            AssetType.BIST -> R.string.asset_type_stocks
            AssetType.DOVIZ -> R.string.asset_type_currency
            AssetType.EMTIA -> R.string.asset_type_commodity
            AssetType.NAKIT -> R.string.asset_type_cash
            AssetType.FON -> R.string.asset_type_fund
        })
    }
}
