package com.example.cuzdan.ui.home

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

data class PortfolioWithBalance(
    val portfolio: Portfolio,
    val balance: BigDecimal = BigDecimal.ZERO,
    val dailyChangeAbs: BigDecimal = BigDecimal.ZERO,
    val dailyChangePerc: BigDecimal = BigDecimal.ZERO,
    val totalCost: BigDecimal = BigDecimal.ZERO
)

data class WalletUiState(
    val portfolios: List<PortfolioWithBalance> = emptyList(),
    val selectedPortfolioId: Long = 1,
    val selectedPortfolioName: String = "",
    val totalBalance: BigDecimal = BigDecimal.ZERO,
    val dailyChangeAbs: BigDecimal = BigDecimal.ZERO,
    val dailyChangePerc: BigDecimal = BigDecimal.ZERO,
    val categorySummaries: List<WalletCategorySummary> = emptyList(),
    val donutSegments: List<DonutChartView.Segment> = emptyList(),
    val donutCenterLabel: String = "Dağılım",
    val donutCenterPercent: String = "%100",
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
    private val prefManager: PreferenceManager,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(WalletUiState())
    val uiState: StateFlow<WalletUiState> = _uiState.asStateFlow()

    private val _homeCurrency = MutableStateFlow(prefManager.getHomeCurrency())
    private val _selectedPortfolioId = MutableStateFlow(prefManager.getSelectedPortfolioId())
    private val _expandedCategory = MutableStateFlow<AssetType?>(null)
    private val _currentAssets = MutableStateFlow<List<Asset>>(emptyList())

    init {
        observePortfolios()
        observeAssets()
    }

    private fun observePortfolios() {
        viewModelScope.launch {
            combine(
                portfolioRepository.getAllPortfolios(),
                assetRepository.getAllAssets(),
                _homeCurrency
            ) { portfolios, allAssets, currency ->
                val portfolioList = portfolios.map { p ->
                    val assets = allAssets.filter { it.portfolioId == p.id }
                    var balance = BigDecimal.ZERO
                    var cost = BigDecimal.ZERO
                    assets.forEach { asset ->
                        balance = balance.add(asset.amount.multiply(asset.currentPrice))
                        cost = cost.add(asset.amount.multiply(asset.averageBuyPrice))
                    }
                    val profitLoss = balance.subtract(cost)
                    val profitPerc = if (cost.compareTo(BigDecimal.ZERO) > 0) {
                        profitLoss.divide(cost, 4, RoundingMode.HALF_UP).multiply(BigDecimal(100))
                    } else BigDecimal.ZERO

                    PortfolioWithBalance(p, balance, profitLoss, profitPerc, cost)
                }

                if (portfolios.isNotEmpty()) {
                    val currentId = _selectedPortfolioId.value
                    val selectedPortfolio = if (currentId == -1L) null else portfolios.find { it.id == currentId } ?: portfolios.firstOrNull()

                    val newId = if (currentId == -1L) -1L else selectedPortfolio?.id ?: 1L
                    val localizedName = if (newId == -1L) context.getString(R.string.total_portfolios) else selectedPortfolio?.name ?: ""

                    _selectedPortfolioId.value = newId
                    _uiState.update { it.copy(
                        portfolios = portfolioList,
                        selectedPortfolioId = newId,
                        selectedPortfolioName = localizedName,
                        currency = currency
                    )}

                    calculateStats(allAssets, _expandedCategory.value)
                }
            }.collect()
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
                _expandedCategory,
                _homeCurrency
            ) { assets, expanded, _ ->
                _currentAssets.value = assets
                calculateStats(assets, expanded)
            }.collect()
        }
    }

    fun toggleCategoryExpansion(type: AssetType) {
        _expandedCategory.value = if (_expandedCategory.value == type) null else type
    }

    fun selectPortfolio(id: Long) {
        val name = if (id == -1L) context.getString(R.string.total_portfolios) else _uiState.value.portfolios.find { it.portfolio.id == id }?.portfolio?.name ?: ""
        _selectedPortfolioId.value = id
        prefManager.setSelectedPortfolioId(id)
        _uiState.update { it.copy(selectedPortfolioId = id, selectedPortfolioName = name) }
    }

    fun selectNextPortfolio() {
        val portfolios = _uiState.value.portfolios
        if (portfolios.isEmpty()) return
        val currentIndex = if (_selectedPortfolioId.value == -1L) -1 else portfolios.indexOfFirst { it.portfolio.id == _selectedPortfolioId.value }
        val nextIndex = currentIndex + 1
        if (nextIndex >= portfolios.size) selectPortfolio(-1L) else selectPortfolio(portfolios[nextIndex].portfolio.id)
    }

    fun selectPrevPortfolio() {
        val portfolios = _uiState.value.portfolios
        if (portfolios.isEmpty()) return
        val currentIndex = if (_selectedPortfolioId.value == -1L) -1 else portfolios.indexOfFirst { it.portfolio.id == _selectedPortfolioId.value }
        if (currentIndex == -1) selectPortfolio(portfolios.last().portfolio.id)
        else if (currentIndex == 0) selectPortfolio(-1L)
        else selectPortfolio(portfolios[currentIndex - 1].portfolio.id)
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

    private fun calculateStats(assets: List<Asset>, expandedCategory: AssetType?, resolveContext: Context = context) {
        val currency = prefManager.getHomeCurrency()
        var exchangeRate = BigDecimal.ONE

        if (currency == "USD") exchangeRate = BigDecimal("32.5")
        else if (currency == "EUR") exchangeRate = BigDecimal("35.2")

        var totalBalance = BigDecimal.ZERO
        var totalCost = BigDecimal.ZERO

        assets.forEach { asset ->
            totalBalance = totalBalance.add(asset.amount.multiply(asset.currentPrice))
            totalCost = totalCost.add(asset.amount.multiply(asset.averageBuyPrice))
        }

        val convertedBalance = totalBalance.divide(exchangeRate, 2, RoundingMode.HALF_UP)
        val convertedCost = totalCost.divide(exchangeRate, 2, RoundingMode.HALF_UP)
        val totalProfitLoss = convertedBalance.subtract(convertedCost)
        val totalProfitPerc = if (totalCost > BigDecimal.ZERO) {
            totalBalance.subtract(totalCost).divide(totalCost, 4, RoundingMode.HALF_UP).multiply(BigDecimal("100"))
        } else BigDecimal.ZERO

        val categorySummaries = assets.groupBy { it.assetType }.map { (type, typeAssets) ->
            var catValue = BigDecimal.ZERO
            var catCost = BigDecimal.ZERO
            typeAssets.forEach {
                catValue = catValue.add(it.amount.multiply(it.currentPrice))
                catCost = catCost.add(it.amount.multiply(it.averageBuyPrice))
            }
            val convCatValue = catValue.divide(exchangeRate, 2, RoundingMode.HALF_UP)
            val convCatCost = catCost.divide(exchangeRate, 2, RoundingMode.HALF_UP)
            val catPLPerc = if (catCost > BigDecimal.ZERO) {
                catValue.subtract(catCost).divide(catCost, 4, RoundingMode.HALF_UP).multiply(BigDecimal("100"))
            } else BigDecimal.ZERO

             WalletCategorySummary(
                type = type,
                title = getLocalizedAssetTypeName(type, resolveContext),
                totalValue = convCatValue,
                totalProfitLoss = convCatValue.subtract(convCatCost),
                profitLossPerc = catPLPerc,
                assets = typeAssets,
                isExpanded = expandedCategory == type
            )
        }

        val segments = mutableListOf<DonutChartView.Segment>()
        var centerLabel = resolveContext.getString(com.example.cuzdan.R.string.label_distribution)
        var centerPercent = "%100"

        if (expandedCategory != null) {
            val catAssets = assets.filter { it.assetType == expandedCategory }
            val catTotal = catAssets.sumOf { it.amount.multiply(it.currentPrice) }
            centerLabel = getLocalizedAssetTypeName(expandedCategory, resolveContext)
            
            if (catTotal > BigDecimal.ZERO) {
                catAssets.forEachIndexed { index, asset ->
                    val assetValue = asset.amount.multiply(asset.currentPrice)
                    val weight = assetValue.divide(catTotal, 4, RoundingMode.HALF_UP).toFloat()
                    segments.add(DonutChartView.Segment(weight, getAssetColor(index), asset.symbol))
                }
            }
        } else {
            if (totalBalance > BigDecimal.ZERO) {
                categorySummaries.forEach { summary ->
                    val weight = (summary.totalValue.multiply(exchangeRate)).divide(totalBalance, 4, RoundingMode.HALF_UP).toFloat()
                    segments.add(DonutChartView.Segment(weight, getCategoryColor(summary.type), summary.title))
                }
            }
        }

        _uiState.update { 
            it.copy(
                totalBalance = convertedBalance,
                dailyChangeAbs = totalProfitLoss,
                dailyChangePerc = totalProfitPerc,
                categorySummaries = categorySummaries,
                donutSegments = segments,
                donutCenterLabel = centerLabel,
                donutCenterPercent = centerPercent,
                currency = currency
            )
        }
    }

    private fun getLocalizedAssetTypeName(type: AssetType, resolveContext: Context = context): String {
        return resolveContext.getString(when(type) {
            AssetType.KRIPTO -> R.string.asset_type_crypto
            AssetType.BIST -> R.string.asset_type_stocks
            AssetType.DOVIZ -> R.string.asset_type_currency
            AssetType.EMTIA -> R.string.asset_type_commodity
            AssetType.NAKIT -> R.string.asset_type_cash
            AssetType.FON -> R.string.asset_type_fund
        })
    }

    private fun getCategoryColor(type: AssetType): Int {
        return when(type) {
            AssetType.KRIPTO -> 0xFFE91E63.toInt()
            AssetType.BIST -> 0xFF2196F3.toInt()
            AssetType.DOVIZ -> 0xFF4CAF50.toInt()
            AssetType.EMTIA -> 0xFFFF9800.toInt()
            AssetType.NAKIT -> 0xFF9C27B0.toInt()
            AssetType.FON -> 0xFF00BCD4.toInt()
        }
    }

    private fun getAssetColor(index: Int): Int {
        val colors = listOf(0xFF2196F3, 0xFF4CAF50, 0xFFFF9800, 0xFFE91E63, 0xFF9C27B0, 0xFF009688)
        return colors[index % colors.size].toInt()
    }

    suspend fun getPortfolioById(id: Long) = portfolioRepository.getPortfolioById(id)
    suspend fun updatePortfolio(id: Long, name: String, isIncluded: Boolean) {
        portfolioRepository.getPortfolioById(id)?.let {
            portfolioRepository.updatePortfolio(it.copy(name = name, isIncludedInTotal = isIncluded))
        }
    }
    suspend fun deletePortfolio(id: Long) {
        portfolioRepository.getPortfolioById(id)?.let {
            portfolioRepository.deletePortfolio(it)
            if (_selectedPortfolioId.value == id) selectPortfolio(-1L)
        }
    }

    fun setCurrency(currency: String) {
        prefManager.setHomeCurrency(currency)
        _homeCurrency.value = currency
        _uiState.update { it.copy(currency = currency) }
        calculateStats(_currentAssets.value, _expandedCategory.value)
    }

    fun refreshLocalization(activityContext: Context) {
        val currentId = _selectedPortfolioId.value
        val portfolios = _uiState.value.portfolios
        val name = if (currentId == -1L) activityContext.getString(R.string.total_portfolios) 
                   else portfolios.find { it.portfolio.id == currentId }?.portfolio?.name ?: ""
        
        _uiState.update { it.copy(selectedPortfolioName = name) }
        calculateStats(_currentAssets.value, _expandedCategory.value, activityContext)
    }
}