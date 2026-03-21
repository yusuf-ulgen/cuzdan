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
import com.example.cuzdan.util.PriceSyncManager
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
    val currency: String = "TL",
    val lastUpdated: String? = null,
    val isOffline: Boolean = false
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
    private val priceSyncManager: PriceSyncManager,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(WalletUiState())
    val uiState: StateFlow<WalletUiState> = _uiState.asStateFlow()

    private val _homeCurrency = MutableStateFlow(prefManager.getHomeCurrency())
    private val _selectedPortfolioId = MutableStateFlow(prefManager.getSelectedPortfolioId())
    private val _expandedCategory = MutableStateFlow<AssetType?>(null)
    private val _currentAssets = MutableStateFlow<List<Asset>>(emptyList())

    private val _usdRate = assetRepository.getLatestPrice("TRY=X")
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), BigDecimal("32.5"))
    private val _eurRate = assetRepository.getLatestPrice("EURTRY=X")
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), BigDecimal("35.2"))


    init {
        observePortfolios()
        observeAssets()
    }

    private fun observePortfolios() {
        viewModelScope.launch {
            combine(
                portfolioRepository.getAllPortfolios(),
                assetRepository.getAllAssets(),
                _homeCurrency,
                _usdRate,
                _eurRate
            ) { portfolios, allAssets, currency, usdRate, eurRate ->
                val exchangeRate = when (currency) {
                    "USD" -> usdRate ?: BigDecimal("32.5")
                    "EUR" -> eurRate ?: BigDecimal("35.2")
                    else -> BigDecimal.ONE
                }

                val portfolioList = portfolios.map { p ->
                    val assets = allAssets.filter { it.portfolioId == p.id }
                    var balanceBase = BigDecimal.ZERO
                    var costBase = BigDecimal.ZERO
                    assets.forEach { asset ->
                        val assetRate = when (asset.currency) {
                            "USD" -> usdRate ?: BigDecimal("32.5")
                            "EUR" -> eurRate ?: BigDecimal("35.2")
                            else -> BigDecimal.ONE
                        }
                        balanceBase = balanceBase.add(asset.amount.multiply(asset.currentPrice).multiply(assetRate))
                        costBase = costBase.add(asset.amount.multiply(asset.averageBuyPrice).multiply(assetRate))
                    }
                    val convBalance = balanceBase.divide(exchangeRate, 2, RoundingMode.HALF_UP)
                    val convCost = costBase.divide(exchangeRate, 2, RoundingMode.HALF_UP)
                    val profitLoss = convBalance.subtract(convCost)
                    val profitPerc = if (costBase > BigDecimal.ZERO) {
                        balanceBase.subtract(costBase).divide(costBase, 4, RoundingMode.HALF_UP).multiply(BigDecimal(100))
                    } else BigDecimal.ZERO

                    PortfolioWithBalance(p, convBalance, profitLoss, profitPerc, convCost)
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

                    calculateStats(allAssets, _expandedCategory.value, currency, _usdRate.value, _eurRate.value)
                }
            }.collect()

        }
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private fun observeAssets() {
        val dateFormat = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())

        viewModelScope.launch {
            // İlk 5 Flow'u combine ediyoruz
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
                _homeCurrency,
                _usdRate,
                _eurRate
            ) { assets, expanded, currency, usdRate, eurRate ->
                _currentAssets.value = assets
                calculateStats(assets, expanded, currency, usdRate, eurRate)
            }.collect() // <--- SADECE İLK 5'İNİ DİNLİYORUZ
        }

        // SyncStatus'u (6. Flow'u) Ayrı Bir Yerde Dinliyoruz
        viewModelScope.launch {
            priceSyncManager.syncStatus.collect { syncStatus ->
                val timeStr = if (syncStatus.lastUpdate > 0) dateFormat.format(java.util.Date(syncStatus.lastUpdate)) else null
                _uiState.update { it.copy(
                    lastUpdated = timeStr,
                    isOffline = syncStatus.isOffline
                )}
            }
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
        return assets.groupBy { it.symbol }.map { (_, symbolAssets) ->
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

    private fun calculateStats(
        assets: List<Asset>, 
        expandedCategory: AssetType?, 
        currency: String = prefManager.getHomeCurrency(),
        usdRate: BigDecimal? = _usdRate.value,
        eurRate: BigDecimal? = _eurRate.value,
        resolveContext: Context = context
    ) {
        var exchangeRate = BigDecimal.ONE

        if (currency == "USD") exchangeRate = usdRate ?: BigDecimal("32.5")
        else if (currency == "EUR") exchangeRate = eurRate ?: BigDecimal("35.2")


        var totalBalanceBase = BigDecimal.ZERO
        var totalCostBase = BigDecimal.ZERO

        assets.forEach { asset ->
            val assetRate = when (asset.currency) {
                "USD" -> usdRate ?: BigDecimal("32.5")
                "EUR" -> eurRate ?: BigDecimal("35.2")
                else -> BigDecimal.ONE
            }
            totalBalanceBase = totalBalanceBase.add(asset.amount.multiply(asset.currentPrice).multiply(assetRate))
            totalCostBase = totalCostBase.add(asset.amount.multiply(asset.averageBuyPrice).multiply(assetRate))
        }

        val convertedBalance = totalBalanceBase.divide(exchangeRate, 2, RoundingMode.HALF_UP)
        val convertedCost = totalCostBase.divide(exchangeRate, 2, RoundingMode.HALF_UP)
        val totalProfitLoss = convertedBalance.subtract(convertedCost)
        val totalProfitPerc = if (totalCostBase > BigDecimal.ZERO) {
            totalBalanceBase.subtract(totalCostBase).divide(totalCostBase, 4, RoundingMode.HALF_UP).multiply(BigDecimal("100"))
        } else BigDecimal.ZERO


        val categorySummaries = assets.groupBy { it.assetType }.map { (type, typeAssets) ->
            var catValueBase = BigDecimal.ZERO
            var catCostBase = BigDecimal.ZERO
            typeAssets.forEach { asset ->
                val assetRate = when (asset.currency) {
                    "USD" -> usdRate ?: BigDecimal("32.5")
                    "EUR" -> eurRate ?: BigDecimal("35.2")
                    else -> BigDecimal.ONE
                }
                catValueBase = catValueBase.add(asset.amount.multiply(asset.currentPrice).multiply(assetRate))
                catCostBase = catCostBase.add(asset.amount.multiply(asset.averageBuyPrice).multiply(assetRate))
            }
            val convCatValue = catValueBase.divide(exchangeRate, 2, RoundingMode.HALF_UP)
            val convCatCost = catCostBase.divide(exchangeRate, 2, RoundingMode.HALF_UP)
            val catPLPerc = if (catCostBase > BigDecimal.ZERO) {
                catValueBase.subtract(catCostBase).divide(catCostBase, 4, RoundingMode.HALF_UP).multiply(BigDecimal("100"))
            } else BigDecimal.ZERO


            val sortedAssets = when (type) {
                AssetType.NAKIT -> {
                    val order = listOf("TRY", "TL", "USD", "EUR", "GBP", "CHF", "JPY", "GBPUSD=X")
                    typeAssets.sortedWith(compareBy<com.example.cuzdan.data.local.entity.Asset> { asset ->
                        val symbol = asset.symbol.uppercase()
                        val name = asset.name.lowercase()
                        if (symbol == "TRY" || symbol == "TL" || symbol == "₺" || symbol.contains("TRY") || symbol.contains("TL") || symbol.contains("₺") || 
                            name.contains("türk lirası") || name.contains("tl") || name.contains("türk") || name == "türk lirasi") {
                            -1
                        } else {
                            val index = order.indexOf(symbol)
                            if (index == -1) Int.MAX_VALUE else index
                        }
                    })
                }
                AssetType.KRIPTO -> {
                    typeAssets.sortedByDescending { asset ->
                        val assetRate = when (asset.currency) {
                            "USD" -> usdRate ?: BigDecimal("32.5")
                            "EUR" -> eurRate ?: BigDecimal("35.2")
                            else -> BigDecimal.ONE
                        }
                        asset.amount.multiply(asset.currentPrice).multiply(assetRate)
                    }
                }
                else -> typeAssets
            }

             WalletCategorySummary(
                type = type,
                title = getLocalizedAssetTypeName(type, resolveContext),
                totalValue = convCatValue,
                totalProfitLoss = convCatValue.subtract(convCatCost),
                profitLossPerc = catPLPerc,
                assets = sortedAssets,
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
            if (totalBalanceBase > BigDecimal.ZERO) {
                categorySummaries.forEach { summary ->
                    val weight = (summary.totalValue.multiply(exchangeRate)).divide(totalBalanceBase, 4, RoundingMode.HALF_UP).toFloat()
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
            AssetType.KRIPTO -> 0xFF8B5CF6.toInt() // Violet
            AssetType.BIST -> 0xFFDDD6FE.toInt()   // Lavender
            AssetType.DOVIZ -> 0xFF93C5FD.toInt()  // Blue
            AssetType.EMTIA -> 0xFF818CF8.toInt()  // Indigo
            AssetType.NAKIT -> 0xFFF472B6.toInt()  // Pink
            AssetType.FON -> 0xFFC084FC.toInt()    // Bright Purple
        }
    }

    private fun getAssetColor(index: Int): Int {
        val colors = listOf(0xFF8B5CF6, 0xFFDDD6FE, 0xFF93C5FD, 0xFF818CF8, 0xFFF472B6, 0xFFC084FC)
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
        calculateStats(_currentAssets.value, _expandedCategory.value, currency, _usdRate.value, _eurRate.value)
    }


    fun refreshLocalization(activityContext: Context) {
        val currentId = _selectedPortfolioId.value
        val portfolios = _uiState.value.portfolios
        val name = if (currentId == -1L) activityContext.getString(R.string.total_portfolios) 
                   else portfolios.find { it.portfolio.id == currentId }?.portfolio?.name ?: ""
        
        _uiState.update { it.copy(selectedPortfolioName = name) }
        calculateStats(_currentAssets.value, _expandedCategory.value, _homeCurrency.value, _usdRate.value, _eurRate.value, activityContext)
    }

}