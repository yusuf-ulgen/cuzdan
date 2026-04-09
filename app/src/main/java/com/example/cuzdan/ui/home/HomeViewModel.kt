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
    val totalCost: BigDecimal = BigDecimal.ZERO,
    val depositedAmount: BigDecimal = BigDecimal.ZERO
)

data class WalletUiState(
    val portfolios: List<PortfolioWithBalance> = emptyList(),
    val selectedPortfolioId: Long = -1,
    val selectedPortfolioName: String = "",
    val totalBalance: BigDecimal = BigDecimal.ZERO,
    val cashBalance: BigDecimal = BigDecimal.ZERO,
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
    val totalValue: java.math.BigDecimal,
    val totalProfitLoss: java.math.BigDecimal,
    val profitLossPerc: java.math.BigDecimal,
    val assets: List<Asset> = emptyList(),
    val isExpanded: Boolean = false,
    val iconRes: Int = 0
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

    private val _usdRate = assetRepository.getLatestPrice("USDTRY=X")
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), BigDecimal("44.52"))
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
                    val convCost = costBase.divide(exchangeRate, 2, RoundingMode.HALF_UP)
                    
                    // Boştaki nakit (TRY): Yatırılan para - Varlıkların alış maliyeti
                    val idleCashInTry = if (p.depositedAmount > BigDecimal.ZERO) {
                        (p.depositedAmount - costBase).coerceAtLeast(BigDecimal.ZERO)
                    } else {
                        BigDecimal.ZERO
                    }
                    
                    // Toplam bakiye (Seçili para biriminde): (Varlıkların değeri + Boştaki nakit) / Kur
                    val totalValueBase = balanceBase.add(idleCashInTry)
                    val convBalance = totalValueBase.divide(exchangeRate, 2, RoundingMode.HALF_UP)

                    // depositedAmount varsa kâr = (anlık değer - yatırılan sermaye)
                    // depositedAmount yoksa kâr = (anlık değer - maliyet)
                    val depositConv = p.depositedAmount.divide(exchangeRate, 2, RoundingMode.HALF_UP)
                    val effectiveCost = if (p.depositedAmount > BigDecimal.ZERO) depositConv else convCost
                    val profitLoss = convBalance.subtract(effectiveCost)
                    val profitBase = if (p.depositedAmount > BigDecimal.ZERO) p.depositedAmount else costBase
                    val profitPerc = if (profitBase > BigDecimal.ZERO) {
                        totalValueBase.subtract(profitBase).divide(profitBase, 4, RoundingMode.HALF_UP).multiply(BigDecimal(100))
                    } else BigDecimal.ZERO

                    PortfolioWithBalance(p, convBalance, profitLoss, profitPerc, convCost, p.depositedAmount)
                }


                if (portfolios.isNotEmpty()) {
                    val currentId = _selectedPortfolioId.value
                    // IMPORTANT: Do not auto-select a portfolio.
                    // If the user is in "total / none selected" (-1), keep it that way. This prevents
                    // silently attaching new assets to the first portfolio on fresh installs.
                    val newId = when {
                        currentId == -1L -> -1L
                        portfolios.any { it.id == currentId } -> currentId
                        else -> -1L
                    }
                    val selectedPortfolio = portfolios.find { it.id == newId }
                    val localizedName =
                        if (newId == -1L) context.getString(R.string.total_portfolios)
                        else selectedPortfolio?.name.orEmpty()

                    _selectedPortfolioId.value = newId
                    prefManager.setSelectedPortfolioId(newId)
                    
                    // Fetch start of day balance when portfolio changes
                    fetchStartOfDayBalance(newId)

                    _uiState.update { it.copy(
                        portfolios = portfolioList,
                        selectedPortfolioId = newId,
                        selectedPortfolioName = localizedName,
                        currency = currency
                    )}
                    calculateStats(_currentAssets.value, _expandedCategory.value, currency, usdRate, eurRate)
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

    private var _startOfDayBalanceValue = BigDecimal.ZERO
    private var _lastStartOfDayMillis: Long = 0L

    private fun fetchStartOfDayBalance(id: Long) {
        viewModelScope.launch {
            val calendar = java.util.Calendar.getInstance().apply {
                set(java.util.Calendar.HOUR_OF_DAY, 0)
                set(java.util.Calendar.MINUTE, 0)
                set(java.util.Calendar.SECOND, 0)
                set(java.util.Calendar.MILLISECOND, 0)
            }
            val startOfToday = calendar.timeInMillis
            
            val history = if (id == -1L) {
                val portfolios = portfolioRepository.getIncludedPortfolios().first()
                var total = BigDecimal.ZERO
                portfolios.forEach { p ->
                    val h = assetRepository.getLatestHistoryBefore(p.id, startOfToday)
                    total = total.add(h?.totalValue ?: p.depositedAmount)
                }
                total
            } else {
                assetRepository.getLatestHistoryBefore(id, startOfToday)?.totalValue
            }
            
            _startOfDayBalanceValue = history ?: BigDecimal.ZERO
            
            // If no history exists, use the initial deposited amount as a starting point 
            // so that NEW portfolios don't show +10000% on day one.
            if (_startOfDayBalanceValue == BigDecimal.ZERO) {
                val p = if (id == -1L) {
                    portfolioRepository.getIncludedPortfolios().first().sumOf { it.depositedAmount }
                } else {
                    portfolioRepository.getPortfolioById(id)?.depositedAmount ?: BigDecimal.ZERO
                }
                _startOfDayBalanceValue = p
            }

            // Record snapshot if it's the first time today for this portfolio
            if (id != -1L) {
                val today = java.time.LocalDate.now().atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
                val snapshotToday = assetRepository.getLatestHistoryBefore(id, today + 86400000)?.let { 
                    it.date >= today 
                } ?: false
                
                if (!snapshotToday) {
                    // This is the first time today. Record history.
                    // We record the 'finalTotalBalance' from calculateStats but wait...
                    // calculateStats is not yet finished. We'll record it in calculateStats itself 
                    // if it's the first time today.
                }
            }

            calculateStats(_currentAssets.value, _expandedCategory.value)
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
        // Build correct locale context for labels even if flow triggers this from background
        val langContext = com.example.cuzdan.util.LocaleHelper.setLocale(context, prefManager.getLanguage())
        var exchangeRate = BigDecimal.ONE

        if (currency == "USD") exchangeRate = usdRate ?: BigDecimal("32.5")
        else if (currency == "EUR") exchangeRate = eurRate ?: BigDecimal("35.2")


        var totalBalanceBase = BigDecimal.ZERO
        // totalCostBase: only cost of the ASSETS in the current view (selected portfolio)
        // This is the cost of what was purchased from deposited funds.
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

        // 1. Calculate Idle Cash in Base Currency (TRY)
        // Idle Cash = Total Deposited Amount (as requested by user)
        val currentId = _selectedPortfolioId.value
        val portfolios = _uiState.value.portfolios
        val totalDepositedBase = if (currentId == -1L) {
            portfolios.filter { it.portfolio.isIncludedInTotal }.sumOf { it.portfolio.depositedAmount }
        } else {
            portfolios.find { it.portfolio.id == currentId }?.portfolio?.depositedAmount ?: BigDecimal.ZERO
        }

        // idleCashTry: stays as deposited amount, ignoring asset costs
        val idleCashTry = totalDepositedBase
        val idleCashConv = idleCashTry.divide(exchangeRate, 2, RoundingMode.HALF_UP)
        
        // 2. Final Total Balance = Market value of all current assets + Idle Cash
        val convertedAssetValue = totalBalanceBase.divide(exchangeRate, 2, RoundingMode.HALF_UP)
        val finalTotalBalance = convertedAssetValue.add(idleCashConv)
        
        // 3. Daily Change calculation
        // Reset at 00:00: on the first calculation after day changes, baseline becomes "now" and daily change becomes 0.
        val todayStart = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, 0)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }.timeInMillis

        if (_lastStartOfDayMillis != todayStart) {
            _lastStartOfDayMillis = todayStart
            // Store baseline in TRY to match PortfolioHistory semantics
            _startOfDayBalanceValue = finalTotalBalance.multiply(exchangeRate)
        }

        val startOfTodayBalance = _startOfDayBalanceValue
        val startOfTodayConv = if (startOfTodayBalance > BigDecimal.ZERO) {
            startOfTodayBalance.divide(exchangeRate, 2, RoundingMode.HALF_UP)
        } else null

        val dailyChangeAbs = if (startOfTodayConv != null) {
            finalTotalBalance.subtract(startOfTodayConv)
        } else BigDecimal.ZERO

        val dailyChangePerc = if (startOfTodayBalance > BigDecimal.ZERO) {
            dailyChangeAbs.multiply(BigDecimal("100"))
                .divide(startOfTodayBalance.divide(exchangeRate, 2, RoundingMode.HALF_UP), 2, RoundingMode.HALF_UP)
        } else BigDecimal.ZERO

        // Record a history snapshot if none exists for today (for tomorrow's reference)
        viewModelScope.launch {
            if (currentId != -1L && finalTotalBalance > BigDecimal.ZERO) {
                // Get snapshots taken TODAY
                val snapshotToday = assetRepository.getLatestHistoryBefore(currentId, todayStart + 86400000)?.let { 
                    it.date >= todayStart 
                } ?: false
                
                if (!snapshotToday) {
                    // Record FIRST snapshot of the day (Total Value in TRY)
                    assetRepository.recordPortfolioSnapshot(currentId, finalTotalBalance.multiply(exchangeRate), "TRY")
                }
            }
        }

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
                title = getLocalizedAssetTypeName(type, langContext),
                totalValue = convCatValue,
                totalProfitLoss = convCatValue.subtract(convCatCost),
                profitLossPerc = catPLPerc,
                assets = sortedAssets,
                isExpanded = expandedCategory == type,
                iconRes = getCategoryIcon(type)
            )
        }.toMutableList()

        // Filter out empty categories. Keep categories that have assets even if their
        // computed value is temporarily 0 (e.g. funds while TEFAS price refresh is pending).
        categorySummaries.removeAll { it.assets.isEmpty() }

        // Inject 'Nakit' (Idle Cash) category as the first item if there is idle cash
        if (idleCashTry > BigDecimal.ZERO) {
            // Remove any existing NAKIT from assets (those are actual fx holdings)
            // The idle cash Nakit is a separate concept: uninvested deposited money
            val existingNakitIndex = categorySummaries.indexOfFirst { it.type == AssetType.NAKIT }
            if (existingNakitIndex != -1) {
                // There are actual Nakit-type assets; add idle cash on top
                val existing = categorySummaries[existingNakitIndex]
                categorySummaries[existingNakitIndex] = existing.copy(
                    totalValue = existing.totalValue.add(idleCashConv),
                    // Keep P/L for actual assets but mark as Nakit
                    totalProfitLoss = existing.totalProfitLoss,
                    profitLossPerc = existing.profitLossPerc
                )
            } else {
                // Pure idle cash card — no assets, no P/L
                categorySummaries.add(0, WalletCategorySummary(
                    type = AssetType.NAKIT,
                    title = getLocalizedAssetTypeName(AssetType.NAKIT, langContext),
                    totalValue = idleCashConv,
                    totalProfitLoss = BigDecimal.ZERO,
                    profitLossPerc = BigDecimal.ZERO,
                    assets = emptyList(),
                    isExpanded = false,
                    iconRes = R.drawable.nakit
                ))
            }
        }

        val segments = mutableListOf<DonutChartView.Segment>()
        var centerLabel = langContext.getString(com.example.cuzdan.R.string.label_distribution)
        var centerPercent = "%100"

        if (expandedCategory != null) {
            val catAssets = assets.filter { it.assetType == expandedCategory }
            val catTotalRaw = catAssets.sumOf { it.amount.multiply(it.currentPrice) }
            
            // If Nakit is expanded, we need to include idle cash in the distribution
            val catTotal = if (expandedCategory == AssetType.NAKIT) catTotalRaw.add(idleCashTry) else catTotalRaw

            centerLabel = getLocalizedAssetTypeName(expandedCategory, langContext)
            
            if (catTotal > BigDecimal.ZERO) {
                // Add regular assets in this category
                catAssets.forEachIndexed { index, asset ->
                    val assetValue = asset.amount.multiply(asset.currentPrice)
                    val weight = assetValue.divide(catTotal, 4, RoundingMode.HALF_UP).toFloat()
                    segments.add(DonutChartView.Segment(weight, getAssetColor(index), asset.symbol))
                }
                // If it's Nakit, add the idle cash portion as a segment
                if (expandedCategory == AssetType.NAKIT && idleCashTry > BigDecimal.ZERO) {
                    val weight = idleCashTry.divide(catTotal, 4, RoundingMode.HALF_UP).toFloat()
                    segments.add(DonutChartView.Segment(weight, getAssetColor(catAssets.size), langContext.getString(R.string.asset_type_cash)))
                }
            }
        } else {
            val totalValueWithNakitTry = totalBalanceBase.add(idleCashTry)
            if (totalValueWithNakitTry > BigDecimal.ZERO) {
                categorySummaries.forEach { summary ->
                    val weight = (summary.totalValue.multiply(exchangeRate)).divide(totalValueWithNakitTry, 4, RoundingMode.HALF_UP).toFloat()
                    segments.add(DonutChartView.Segment(weight, getCategoryColor(summary.type), summary.title))
                }
            }
        }

        _uiState.update { 
            it.copy(
                totalBalance = finalTotalBalance,
                cashBalance = idleCashConv,
                dailyChangeAbs = dailyChangeAbs,
                dailyChangePerc = dailyChangePerc,
                categorySummaries = categorySummaries,
                donutSegments = segments,
                donutCenterLabel = centerLabel,
                donutCenterPercent = centerPercent,
                currency = currency
            )
        }
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

    private fun getCategoryIcon(type: AssetType): Int {
        return when(type) {
            AssetType.KRIPTO -> R.drawable.kripto
            AssetType.BIST -> R.drawable.borsa
            AssetType.DOVIZ -> R.drawable.doviz
            AssetType.EMTIA -> R.drawable.emtia
            AssetType.NAKIT -> R.drawable.nakit
            AssetType.FON -> R.drawable.fon
        }
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

    /**
     * Seçili portföye sermaye yatır veya çek.
     * amountInSelectedCurrency: Kullanıcının girdiği tutar (seçilen dövizde)
     * currency: "TL", "USD" veya "EUR"
     * isWithdraw: true ise çekme işlemi
     */
    fun depositOrWithdraw(amountInSelectedCurrency: BigDecimal, currency: String, isWithdraw: Boolean) {
        val portfolioId = _selectedPortfolioId.value
        if (portfolioId == -1L) return // Tüm portföyler modunda işlem yapılamaz
        viewModelScope.launch {
            val usdRate = _usdRate.value ?: BigDecimal("32.5")
            val eurRate = _eurRate.value ?: BigDecimal("35.2")
            // Para birimini TRY'ye çevir
            val amountInTry = when (currency) {
                "USD" -> amountInSelectedCurrency.multiply(usdRate)
                "EUR" -> amountInSelectedCurrency.multiply(eurRate)
                else -> amountInSelectedCurrency // TL
            }
            val signedAmount = if (isWithdraw) amountInTry.negate() else amountInTry
            portfolioRepository.updateDepositedAmount(portfolioId, signedAmount)
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