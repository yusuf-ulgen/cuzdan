package com.yusufulgen.cuzdan.data.repository

import com.yusufulgen.cuzdan.data.local.dao.AssetDao
import com.yusufulgen.cuzdan.data.local.dao.PortfolioDao
import com.yusufulgen.cuzdan.data.local.entity.Asset
import com.yusufulgen.cuzdan.data.local.entity.AssetType
import com.yusufulgen.cuzdan.data.remote.api.BinanceApi
import com.yusufulgen.cuzdan.data.remote.api.YahooFinanceApi
import com.yusufulgen.cuzdan.data.remote.api.TefasApi
import java.util.Locale
import com.google.gson.Gson
import com.yusufulgen.cuzdan.data.remote.model.TefasWrapper
import com.yusufulgen.cuzdan.data.remote.model.TefasHistoryResponse
import com.yusufulgen.cuzdan.data.remote.model.YahooQuote
import com.yusufulgen.cuzdan.data.remote.model.YahooFinanceResponse
import com.yusufulgen.cuzdan.data.remote.model.YahooSearchResponse
import com.yusufulgen.cuzdan.data.local.dao.MarketAssetDao
import com.yusufulgen.cuzdan.data.local.entity.PortfolioHistory
import com.yusufulgen.cuzdan.data.local.dao.PortfolioHistoryDao
import com.yusufulgen.cuzdan.data.local.entity.PriceAlert
import com.yusufulgen.cuzdan.data.local.dao.PriceAlertDao
import com.yusufulgen.cuzdan.data.local.entity.PriceAlertCondition
import com.yusufulgen.cuzdan.data.local.entity.MarketAsset
import com.yusufulgen.cuzdan.util.Resource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import java.math.BigDecimal
import java.math.RoundingMode
import android.util.Log
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AssetRepository @Inject constructor(
    private val assetDao: AssetDao,
    private val marketAssetDao: MarketAssetDao,
    private val portfolioHistoryDao: PortfolioHistoryDao,
    private val binanceApi: BinanceApi,
    private val yahooFinanceApi: YahooFinanceApi,
    private val tefasApi: TefasApi,
    private val portfolioDao: PortfolioDao,
    private val priceAlertDao: PriceAlertDao
) {
    private var bistJob: Job? = null
    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val gson = Gson()
    /**
     * Tüm kripto varlıkları Flow olarak döner.
     */
    fun getCryptoAssets(): Flow<List<Asset>> {
        return assetDao.getAssetsByTypes(listOf(AssetType.KRIPTO))
    }

    /**
     * Fon varlıklarını döner.
     */
    fun getFundAssets(): Flow<List<Asset>> {
        return assetDao.getAssetsByTypes(listOf(AssetType.FON))
    }

    /**
     * BIST, Döviz ve Altın varlıklarını döner.
     */
    fun getOtherAssets(): Flow<List<Asset>> {
        return assetDao.getAssetsByTypes(listOf(AssetType.BIST, AssetType.DOVIZ, AssetType.EMTIA))
    }

    /**
     * Belirli bir portföye ait varlıkları döner.
     */
    fun getAssetsByPortfolioId(portfolioId: Long): Flow<List<Asset>> {
        return assetDao.getAssetsByPortfolioId(portfolioId)
    }

    /**
     * Tüm varlıkları döner.
     */
    fun getAllAssets(): Flow<List<Asset>> {
        return assetDao.getAllAssets()
    }

    /**
     * Piyasa verilerini DB'den Flow olarak döner.
     */
    fun getMarketAssetsFlow(type: AssetType?): Flow<List<MarketAsset>> {
        return if (type == null) {
            marketAssetDao.getAllMarketAssetsFlow()
        } else {
            marketAssetDao.getMarketAssetsByType(type)
        }
    }

    /**
     * Sembole göre tüm piyasa verisini Flow olarak döner.
     */
    fun getMarketAssetBySymbolFlow(symbol: String): Flow<MarketAsset?> {
        return marketAssetDao.getMarketAssetBySymbol(symbol)
    }

    /**
     * Sembole göre en güncel fiyatı DB'den Flow olarak döner.
     */
    fun getLatestPrice(symbol: String): Flow<BigDecimal?> {
        return marketAssetDao.getMarketAssetBySymbol(symbol).map { it?.currentPrice }
    }

    /**
     * Yahoo Finance API'den tek bir sembolün fiyatını döner.
     */
    suspend fun getYahooPriceOnce(symbol: String): BigDecimal? {
        return try {
            val response = yahooFinanceApi.getQuotes(symbol)
            val quote = response.quoteResponse.result?.firstOrNull()
            quote?.regularMarketPrice
        } catch (e: Exception) {
            null
        }
    }

    suspend fun refreshCryptoPrices(): Flow<Resource<Unit>> = flow {
        emit(Resource.Loading())
        try {
            val tickers = binanceApi.getAllTickers()
            val currentAssets = getCryptoAssets().first()

            currentAssets.forEach { asset ->
                val ticker = tickers.find { it.symbol == asset.symbol }
                ticker?.let {
                    assetDao.updateAsset(asset.copy(
                        currentPrice = BigDecimal(it.lastPrice),
                        dailyChangePercentage = BigDecimal(it.priceChangePercent),
                        currency = "USD"
                    ))
                }
            }
            emit(Resource.Success(Unit))
        } catch (e: Exception) {
            e.printStackTrace()
            emit(Resource.Error(e.message ?: "Kripto fiyatları güncellenemedi"))
        }
    }

    private fun toYahooSymbol(symbol: String, type: AssetType): String {
        val clean = symbol.uppercase()
        return when (type) {
            AssetType.BIST -> if (clean.endsWith(".IS")) clean else "$clean.IS"
            AssetType.DOVIZ -> {
                if (clean == "USD") "USDTRY=X"
                else if (clean == "EUR") "EURTRY=X"
                else if (clean.contains("TRY=X")) clean 
                else "${clean}TRY=X"
            }
            AssetType.EMTIA -> when (clean) {
                "GOLD", "ONS" -> "GC=F"
                "SILVER", "GUMUS" -> "SI=F"
                "PLATINUM", "PLATIN" -> "PL=F"
                "PALLADIUM", "PALADYUM" -> "PA=F"
                "COPPER", "BAKIR" -> "HG=F"
                "GAS", "DOGALGAZ" -> "NG=F"
                "OIL", "PETROL" -> "CL=F"
                "BRENT" -> "BZ=F"
                else -> symbol
            }
            else -> symbol
        }
    }

    /**
     * Yahoo Finance API'den BIST, Döviz ve Altın fiyatlarını günceller.
     * v7/quote endpoint'i 401 hatası verdiği için v8/chart endpoint'ine geçildi.
     */
    suspend fun refreshYahooPrices(): Flow<Resource<Unit>> = flow {
        val TAG = "CUZDAN_LOG"
        emit(Resource.Loading())
        try {
            val otherAssets = getOtherAssets().first()
            Log.d(TAG, ">>> Refresh Yahoo Prices Started (v8-parallel). Total local assets: ${otherAssets.size}")
            
            if (otherAssets.isEmpty()) {
                Log.d(TAG, "No assets found in DB, adding defaults...")
                val defaultOther = listOf(
                    Asset(symbol = "THYAO", name = "Türk Hava Yolları", amount = BigDecimal.ZERO, averageBuyPrice = BigDecimal.ZERO, currentPrice = BigDecimal.ZERO, dailyChangePercentage = BigDecimal.ZERO, assetType = AssetType.BIST),
                    Asset(symbol = "USD", name = "Amerikan Doları", amount = BigDecimal.ZERO, averageBuyPrice = BigDecimal.ZERO, currentPrice = BigDecimal.ZERO, dailyChangePercentage = BigDecimal.ZERO, assetType = AssetType.DOVIZ),
                    Asset(symbol = "GOLD", name = "Altın (Ons)", amount = BigDecimal.ZERO, averageBuyPrice = BigDecimal.ZERO, currentPrice = BigDecimal.ZERO, dailyChangePercentage = BigDecimal.ZERO, assetType = AssetType.EMTIA),
                    Asset(symbol = "GRAM_ALTIN", name = "Gram Altın", amount = BigDecimal.ZERO, averageBuyPrice = BigDecimal.ZERO, currentPrice = BigDecimal.ZERO, dailyChangePercentage = BigDecimal.ZERO, assetType = AssetType.EMTIA)
                )
                defaultOther.forEach { assetDao.insertAsset(it) }
            }

            val currentOtherAssets = getOtherAssets().first()
            val symbolMap = currentOtherAssets.filter { it.symbol != "GRAM_ALTIN" }.associate { it.symbol to toYahooSymbol(it.symbol, it.assetType) }
            val symbolsToFetch = symbolMap.values.distinct()
            Log.d(TAG, "Requesting parallel chart data for: $symbolsToFetch")
            
            if (symbolsToFetch.isNotEmpty()) {
                // Fetch all symbols in parallel using the more reliable chart endpoint
                val marketAssetsResultsMap = coroutineScope {
                    symbolsToFetch.map { sym ->
                        async { 
                            try {
                                val originalAsset = currentOtherAssets.find { toYahooSymbol(it.symbol, it.assetType) == sym }
                                if (originalAsset != null) {
                                    val ma = fetchYahooMarketAsset(sym, originalAsset.assetType)
                                    if (ma != null) sym to ma else null
                                } else null
                            } catch (e: Exception) {
                                Log.e(TAG, "Parallel fetch failed for $sym: ${e.message}")
                                null
                            }
                        }
                    }.awaitAll().filterNotNull().toMap()
                }

                Log.d(TAG, "Received ${marketAssetsResultsMap.size} market asset results.")

                var onsPrice: BigDecimal? = null
                var usdTryPrice: BigDecimal? = null
                var onsChange: BigDecimal = BigDecimal.ZERO
                var usdTryChange: BigDecimal = BigDecimal.ZERO

                currentOtherAssets.forEach { asset ->
                    if (asset.symbol == "GRAM_ALTIN") return@forEach
                    val yahooSymbol = symbolMap[asset.symbol]
                    val marketAsset = marketAssetsResultsMap[yahooSymbol]
                    
                    if (marketAsset != null) {
                        Log.d(TAG, "[MATCH] Asset: ${asset.symbol} -> Price: ${marketAsset.currentPrice} ${marketAsset.currency} | Change: ${marketAsset.dailyChangePercentage}%")

                        if (yahooSymbol == "GC=F" || yahooSymbol == "GOLD") { 
                            onsPrice = marketAsset.currentPrice; onsChange = marketAsset.dailyChangePercentage 
                        }
                        if (yahooSymbol == "USDTRY=X") { 
                            usdTryPrice = marketAsset.currentPrice; usdTryChange = marketAsset.dailyChangePercentage 
                        }

                        assetDao.updateAsset(asset.copy(
                            currentPrice = marketAsset.currentPrice, 
                            dailyChangePercentage = marketAsset.dailyChangePercentage, 
                            currency = marketAsset.currency
                        ))
                    } else {
                        Log.w(TAG, "[MISS] No data for asset: ${asset.symbol} (Yahoo: $yahooSymbol)")
                    }
                }

                // Update MarketAsset table for ALL results fetched (not just portfolio assets)
                marketAssetsResultsMap.values.forEach { marketAsset ->
                    marketAssetDao.insertMarketAsset(marketAsset)
                }

                // 4. Calculate Gram Gold separately with accurate Change %
                if (onsPrice != null && usdTryPrice != null && onsPrice!! > BigDecimal.ZERO && usdTryPrice!! > BigDecimal.ZERO) {
                    val gramGoldPrice = onsPrice!!.divide(BigDecimal("31.1035"), 8, RoundingMode.HALF_UP).multiply(usdTryPrice!!)
                    
                    // Gram Gold Change % = ((1 + OnsChange/100) * (1 + UsdTryChange/100) - 1) * 100
                    val onsFactor = BigDecimal.ONE.add(onsChange.divide(BigDecimal("100"), 8, RoundingMode.HALF_UP))
                    val usdFactor = BigDecimal.ONE.add(usdTryChange.divide(BigDecimal("100"), 8, RoundingMode.HALF_UP))
                    val gramGoldChange = onsFactor.multiply(usdFactor).subtract(BigDecimal.ONE).multiply(BigDecimal("100")).setScale(2, RoundingMode.HALF_UP)
                    
                    Log.d(TAG, "Gram Gold Calc: Ons($onsPrice) / 31.1035 * USDTRY($usdTryPrice) = $gramGoldPrice | Change: $gramGoldChange%")
                    
                    assetDao.getAssetBySymbol("GRAM_ALTIN")?.let { asset ->
                        assetDao.updateAsset(asset.copy(currentPrice = gramGoldPrice, dailyChangePercentage = gramGoldChange))
                        
                        // Also update MarketAsset table for Gram Gold consistency
                        val gramMarket = marketAssetDao.getMarketAssetBySymbolAndTypeOnce("GRAM_ALTIN", AssetType.EMTIA)
                        if (gramMarket != null) {
                            marketAssetDao.insertMarketAsset(gramMarket.copy(
                                currentPrice = gramGoldPrice,
                                dailyChangePercentage = gramGoldChange,
                                lastUpdated = System.currentTimeMillis()
                            ))
                        }
                        Log.d(TAG, "Gram Gold updated in DB.")
                    }
                }
            }
            Log.d(TAG, "<<< Refresh Yahoo Prices Finished Successfully")
            emit(Resource.Success(Unit))
        } catch (e: Exception) {
            Log.e(TAG, "!!! refreshYahooPrices CRASHED: ${e.message}", e)
            emit(Resource.Error(e.message ?: "Yahoo verileri güncellenemedi"))
        }
    }

    suspend fun refreshOwnedFundPrices(): Flow<Resource<Unit>> = flow {
        emit(Resource.Loading())
        try {
            val fundAssets = getFundAssets().first()
            if (fundAssets.isEmpty()) {
                emit(Resource.Success(Unit))
                return@flow
            }
            val sdf = java.text.SimpleDateFormat("dd.MM.yyyy", Locale("tr", "TR"))
            val fundTypes = listOf("YAT", "BYF", "HEK", "EMK")
            
            val calendar = java.util.Calendar.getInstance()
            val dayOfWeek = calendar.get(java.util.Calendar.DAY_OF_WEEK)
            val startDayOffset = when (dayOfWeek) {
                java.util.Calendar.SUNDAY -> 2
                java.util.Calendar.SATURDAY -> 1
                else -> 0
            }
            
            try {
                Log.d("TEFAS_SONUC", "Establishing Tefas session (warm-up)...")
                tefasApi.warmUpSession()
                delay(1000L) // Wait a bit after warm-up
            } catch (e: Exception) {
                Log.w("TEFAS_SONUC", "Session warm-up failed: ${e.message}")
            }

            Log.d("TEFAS_SONUC", "Refreshing ${fundAssets.size} owned funds. 2.5s delay aktif.")

            fundAssets.forEachIndexed { index, asset ->
                delay(index * 2500L) // 2.5 seconds stagger between funds
                var price = BigDecimal.ZERO
                var prevPrice = BigDecimal.ZERO
                var foundPriceDate: String? = null

                // Son 30 günü tara (Hafta sonu atlanarak)
                outer@ for (dayOffset in startDayOffset..30) {
                    val cal = java.util.Calendar.getInstance()
                    cal.add(java.util.Calendar.DATE, -dayOffset)
                    val dateStr = sdf.format(cal.time)

                    for (ft in fundTypes) {
                        try {
                            delay(300L) // Delay between types
                            val rawBody = tefasApi.getFundHistory(
                                fundType = ft,
                                fundCode = asset.symbol.uppercase(),
                                startDate = dateStr,
                                endDate = dateStr
                            ).string()
                            val entry = parseTefasFullResponse(rawBody).firstOrNull()
                            if (entry != null) {
                                val parsed = parseTefasPrice(entry.price)
                                if (parsed > BigDecimal.ZERO) {
                                    price = parsed
                                    foundPriceDate = dateStr
                                    break@outer
                                }
                            }
                        } catch (e: Exception) {
                            val msg = e.message ?: ""
                            if (msg.contains("End of input") || msg.contains("malformed JSON")) {
                                Log.v("TEFAS_SONUC", "Type $ft not found for ${asset.symbol}")
                            } else {
                                Log.w("TEFAS_SONUC", "Error for ${asset.symbol} ($ft): $msg")
                            }
                        }
                    }
                }

                // Önceki işlem gününün fiyatını bul (günlük değişim için)
                if (foundPriceDate != null) {
                    // foundPriceDate'in bir önceki takvim günü değil, bir önceki işlem günü
                    for (dayOffset in 1..30) {
                        val cal = java.text.SimpleDateFormat("dd.MM.yyyy", Locale("tr", "TR"))
                            .parse(foundPriceDate!!)?.let {
                                java.util.Calendar.getInstance().apply { time = it }
                            } ?: continue
                        cal.add(java.util.Calendar.DATE, -dayOffset)
                        val prevDateStr = sdf.format(cal.time)
                        var found = false
                        for (ft in fundTypes) {
                            try {
                                delay(100L)
                                val rawPrevBody = tefasApi.getFundHistory(
                                    fundType = ft,
                                    fundCode = asset.symbol.uppercase(),
                                    startDate = prevDateStr,
                                    endDate = prevDateStr
                                ).string()
                                val prevEntry = parseTefasFullResponse(rawPrevBody).firstOrNull()
                                if (prevEntry != null) {
                                    val parsed = parseTefasPrice(prevEntry.price)
                                    if (parsed > BigDecimal.ZERO) {
                                        prevPrice = parsed
                                        found = true
                                        break
                                    }
                                }
                            } catch (e: Exception) { /* ignore */ }
                        }
                        if (found) break
                    }
                }

                if (price > BigDecimal.ZERO) {
                    val dailyChange = if (prevPrice > BigDecimal.ZERO) {
                        price.subtract(prevPrice).divide(prevPrice, 6, RoundingMode.HALF_UP)
                            .multiply(BigDecimal("100")).setScale(2, RoundingMode.HALF_UP)
                    } else BigDecimal.ZERO
                    assetDao.updateAsset(asset.copy(currentPrice = price, dailyChangePercentage = dailyChange, currency = "TRY"))
                    Log.d("TEFAS_SONUC", "Owned fund updated ${asset.symbol}: price=$price change=${dailyChange}%")
                } else {
                    Log.w("TEFAS_SONUC", "Owned fund price stayed 0: ${asset.symbol}. Keeping existing price=${asset.currentPrice.setScale(4, RoundingMode.HALF_UP)}")
                }
            }
            emit(Resource.Success(Unit))
        } catch (e: Exception) {
            emit(Resource.Error(e.message ?: "Fon fiyatları güncellenemedi"))
        }
    }


    private fun parseTefasPrice(rawPriceAny: Any?): BigDecimal {
        return when (rawPriceAny) {
            is Number -> BigDecimal(rawPriceAny.toString())
            is String -> {
                val cleanStr = rawPriceAny.replace("\u00A0", "").trim()
                try {
                    if (cleanStr.contains(",") && cleanStr.contains(".")) {
                        BigDecimal(cleanStr.replace(".", "").replace(",", "."))
                    } else if (cleanStr.contains(",")) {
                        BigDecimal(cleanStr.replace(",", "."))
                    } else BigDecimal(cleanStr)
                } catch (e: Exception) { BigDecimal.ZERO }
            }
            else -> BigDecimal.ZERO
        }
    }

    private fun parseTefasFullResponse(rawResponse: String): List<TefasHistoryResponse> {
        if (rawResponse.isBlank()) return emptyList()
        return try {
            // Handle cases where the response is a JSON string e.g. "{\"d\": ...}"
            var json = rawResponse.trim()
            if (json.startsWith("\"") && json.endsWith("\"")) {
                try {
                    json = gson.fromJson(json, String::class.java)
                } catch (e: Exception) { /* fallback to original */ }
            }
            
            val wrapper = gson.fromJson(json, TefasWrapper::class.java)
            wrapper.data ?: emptyList()
        } catch (e: Exception) {
            // Fallback for direct list responses [...]
            try {
                val listType = object : com.google.gson.reflect.TypeToken<List<TefasHistoryResponse>>() {}.type
                gson.fromJson(rawResponse, listType)
            } catch (e2: Exception) {
                Log.v("TEFAS_SONUC", "Parse failed for JSON: ${rawResponse.take(50)}...")
                emptyList()
            }
        }
    }

    suspend fun refreshMarketAssets(type: AssetType?): Flow<Resource<Unit>> = flow {
        emit(Resource.Loading())
        try {
            val typesToRefresh = if (type == null) {
                listOf(AssetType.BIST, AssetType.KRIPTO, AssetType.DOVIZ, AssetType.EMTIA, AssetType.FON, AssetType.NAKIT)
            } else listOf(type)

            coroutineScope {
                typesToRefresh.forEach { currentType ->
                    launch { refreshMarketAssetsInternal(this@flow, currentType) }
                }
            }
            emit(Resource.Success(Unit))
        } catch (e: Exception) {
            Log.e("AssetRepo", "refreshMarketAssets Error: ${e.message}")
            emit(Resource.Error(e.message ?: "Piyasa verileri güncellenemedi"))
        }
    }

    private suspend fun refreshMarketAssetsInternal(collector: FlowCollector<Resource<Unit>>, type: AssetType) {
        try {
            val marketAssets = mutableListOf<MarketAsset>()
            when (type) {
                AssetType.KRIPTO -> {
                    // CRITICAL: First, delete all old TRY-based crypto pairs to clean the database
                    marketAssetDao.deleteNonUsdtCrypto()
                    
                    val tickers = binanceApi.getAllTickers().filter { it.symbol.endsWith("USDT") }
                    val existingAssets = marketAssetDao.getMarketAssetsByTypeOnce(AssetType.KRIPTO)
                    val favoriteMap = existingAssets.filter { it.isFavorite }.associateBy { it.symbol }
                    
                    tickers.forEach { ticker ->
                        val symbol = ticker.symbol
                        marketAssets.add(MarketAsset(
                            symbol = symbol,
                            name = symbol.replace("USDT", ""),
                            fullName = symbol.replace("USDT", ""),
                            currentPrice = BigDecimal(ticker.lastPrice),
                            dailyChangePercentage = BigDecimal(ticker.priceChangePercent).setScale(2, RoundingMode.HALF_UP),
                            assetType = AssetType.KRIPTO,
                            currency = "USD", // Binance USDT prices are USD base
                            isFavorite = favoriteMap.containsKey(symbol)
                        ))
                    }
                }
                AssetType.FON -> {
                    val symbols = listOf(
                        "TTE", "IJP", "MAC", "GSP", "AFT", "KOC", "IPV", "OPI", "RPD", "TAU", "YAY", "TI1", "GMR", "TE3", "HVS", 
                        "TDF", "IKL", "NJR", "BUY", "NNF", "BGP", "KZT", "ZPE", "OJT", "IDL", "KDV", "GPA", "RTG", "OTJ", "ZPF", 
                        "YZG", "HKH", "ZHB", "AFO", "GL1", "IVY", "YAS", "IHK", "EID", "ST1", "GAY", "DBH", "YHS", "ZPC", "AES", 
                        "IPJ", "GUH", "IEY", "YTD", "YEG", "ZPF", "ZRE", "KDJ", "KRA", "OJK", "AME", "OKT", "HAY", "TUK", "TUA", 
                        "TPZ", "TUT", "TID", "TIE", "TIG", "TIV", "TKF", "TLA", "TLM", "TLE", "TMS", "TMG"
                    )
                    val fundTypeList = listOf("YAT", "BYF", "HEK", "EMK")
                    Log.d("TEFAS_SONUC", "Market Funds Refresh: ${symbols.size} symbols. (Hafta sonu kontrolü aktif)")
                    
                    val calendar = java.util.Calendar.getInstance()
                    val dayOfWeek = calendar.get(java.util.Calendar.DAY_OF_WEEK)
                    val startDayOffset = when (dayOfWeek) {
                        java.util.Calendar.SUNDAY -> 2
                        java.util.Calendar.SATURDAY -> 1
                        else -> 0
                    }
                    
                    val fundAssets = coroutineScope {
                        try {
                            Log.d("TEFAS_SONUC", "Establishing Tefas session for Market Refresh...")
                            tefasApi.warmUpSession()
                            delay(1000L)
                        } catch (e: Exception) { 
                            Log.w("TEFAS_SONUC", "Market session warm-up failed: ${e.message}")
                        }

                        symbols.mapIndexed { index, symbol ->
                            async {
                                try {
                                    // Use 2.5s delay to prevent IP blocking and DNS issues
                                    delay(index * 2500L)
                                } catch (e: Exception) { /* ignore delay cancellation */ }

                                var price = BigDecimal.ZERO
                                var fundName = "$symbol Fonu"
                                val sdf = java.text.SimpleDateFormat("dd.MM.yyyy", Locale("tr", "TR"))

                                // Bugünden geriye doğru 30 gün tara (Hafta sonu atlanarak)
                                outer@ for (dayOffset in startDayOffset..30) {
                                    val cal = java.util.Calendar.getInstance()
                                    cal.add(java.util.Calendar.DATE, -dayOffset)
                                    val dateStr = sdf.format(cal.time)
                                    for (ft in fundTypeList) {
                                        try {
                                            delay(300L) // Secondary stagger between types
                                            val rawBody = tefasApi.getFundHistory(
                                                fundType = ft,
                                                fundCode = symbol,
                                                startDate = dateStr,
                                                endDate = dateStr
                                            ).string()
                                            
                                            val entries = parseTefasFullResponse(rawBody)
                                            entries.firstOrNull()?.let { entry ->
                                                fundName = entry.fundName ?: fundName
                                                val parsed = parseTefasPrice(entry.price)
                                                if (parsed > BigDecimal.ZERO) {
                                                    Log.d("TEFAS_SONUC", "FOUND for $symbol on $dateStr: name=$fundName, rawPrice=${entry.price}, parsed=$parsed")
                                                    price = parsed
                                                    return@async Triple(symbol, fundName, price)
                                                }
                                            }
                                        } catch (e: Exception) {
                                            // Silencing common non-critical errors (fund type doesn't exist for this symbol)
                                            val msg = e.message ?: ""
                                            if (msg.contains("End of input") || msg.contains("malformed JSON")) {
                                                // Ignore, just means this combo doesn't exist
                                            } else if (dayOffset == startDayOffset) {
                                                Log.w("TEFAS_SONUC", "Request Error for $symbol ($ft): $msg")
                                            }
                                        }
                                    }
                                }
                                if (price == BigDecimal.ZERO) {
                                    Log.e("TEFAS_SONUC", "FAILED to find price for $symbol after 30 days of checking.")
                                }
                                Triple(symbol, fundName, price)
                            }
                        }.awaitAll()
                    }
                    fundAssets.forEach { (symbol, name, price) ->
                        // Even if TEFAS fails, keep the fund visible with last known/zero price.
                        val existing = marketAssetDao.getMarketAssetBySymbolAndTypeOnce(symbol, AssetType.FON)
                        val finalPrice = if (price > BigDecimal.ZERO) price else (existing?.currentPrice ?: BigDecimal.ZERO)
                        val finalName = if (name.isNotBlank() && name != "$symbol Fonu") name else (existing?.name ?: "$symbol Fonu")
                        marketAssets.add(
                            MarketAsset(
                                symbol = symbol,
                                name = finalName,
                                fullName = finalName,
                                currentPrice = finalPrice,
                                dailyChangePercentage = existing?.dailyChangePercentage ?: BigDecimal.ZERO,
                                assetType = AssetType.FON,
                                currency = "TRY",
                                isFavorite = existing?.isFavorite ?: false
                            )
                        )
                    }
                }
                AssetType.NAKIT -> {
                    val cashPairs = listOf("USD", "EUR", "GBP", "CHF", "JPY")
                    marketAssets.add(MarketAsset("TRY", "Türk Lirası", "Türk Lirası", BigDecimal.ONE, BigDecimal.ZERO, AssetType.NAKIT, "TRY"))
                    val results = coroutineScope {
                        cashPairs.map { code ->
                            async {
                                try {
                                    val result = yahooFinanceApi.getChartData("${code}TRY=X", range = "1d", interval = "1d").chart.result?.firstOrNull()?.meta
                                    val price = result?.regularMarketPrice ?: BigDecimal.ZERO
                                    val prev = result?.previousClose ?: BigDecimal.ZERO
                                    val change = if (prev > BigDecimal.ZERO) (price - prev).divide(prev, 4, RoundingMode.HALF_UP).multiply(BigDecimal("100")) else BigDecimal.ZERO
                                    val existing = marketAssetDao.getMarketAssetBySymbolAndTypeOnce(code, AssetType.NAKIT)
                                    MarketAsset("${code}TRY=X", code, code, price.setScale(4, RoundingMode.HALF_UP), change.setScale(2, RoundingMode.HALF_UP), AssetType.NAKIT, "TRY", existing?.isFavorite ?: false)
                                } catch (e: Exception) { null }
                            }
                        }.awaitAll().filterNotNull()
                    }
                    marketAssets.addAll(results)
                }
                AssetType.BIST, AssetType.DOVIZ, AssetType.EMTIA -> {
                    if (type == AssetType.DOVIZ) {
                        marketAssetDao.deleteAed()
                    }
                    if (type == AssetType.BIST) {
                        refreshBistIncrementally()
                        return
                    }

                    android.util.Log.d("CuzdanDebug", "refreshMarketAssets: start for type=$type")
                    val symbols = when(type) {
                        AssetType.DOVIZ -> listOf(
                            "USDTRY=X", "EURTRY=X", "GBPTRY=X", "CHFTRY=X", "JPYTRY=X",
                            "AUDTRY=X", "CADTRY=X", "SARTRY=X", "QARTRY=X", "RUBTRY=X",
                            "CNYTRY=X", "AZNTRY=X", "SGDTRY=X", "NOKTRY=X", "SEKTRY=X",
                            "DKKTRY=X", "NZDTRY=X", "MXNTRY=X", "BRLTRY=X", "INRTRY=X",
                            "KRWTRY=X", "HKDTRY=X", "PLNTRY=X", "CZKTRY=X", "HUFTRY=X",
                            "RONTRY=X", "ILSTRY=X", "KWDTRY=X", "AEDTRY=X", "OMRTRY=X",
                            "BHDTRY=X", "THBTRY=X", "MYRTRY=X", "IDRTRY=X", "PHPTRY=X",
                            "EGPTRY=X", "ZARTRY=X", "MADTRY=X", "GELTRY=X", "UAHTRY=X",
                            "BGNTRY=X", "ISKTRY=X", "KAZTRY=X", "VNDDTRY=X", "PKRTRY=X"
                        )
                        else -> listOf(
                            "GC=F", "SI=F", "PL=F", "PA=F", "HG=F", // Ana Metaller
                            "ALI=F", "NI=F", "ZN=F", "PB=F", "SN=F", // Diğer Metaller
                            "CL=F", "BZ=F", "NG=F", "RB=F", "HO=F", // Enerji
                            "KC=F", "CC=F", "CT=F", "SB=F", "ZC=F", "ZW=F", "ZS=F", // Tarım
                            "LBS=F" // Orman Ürünleri
                        )
                    }
                    android.util.Log.d("CuzdanDebug", "Symbols to fetch count: ${symbols.size}")
                    
                    val symbolsToFetch = symbols.filter { it != "GRAM_ALTIN" }
                    
                    if (type == AssetType.DOVIZ) {
                        marketAssetDao.deleteAed()
                    }
                    
                    
                    // IMPORTANT: We switch ALL Doviz/Emtia to getChartData pattern because v7/quote is returning 401 Unauthorized
                    val results = coroutineScope {
                        symbols.mapIndexed { index, sym ->
                            async {
                                try {
                                    // Add small staggered delay to avoid Yahoo rate limits
                                    delay(index * 150L) 
                                    
                                    // Use range=5d to ensure we have a previous close for low-volume assets
                                    val response = yahooFinanceApi.getChartData(sym, range = "5d", interval = "1d")
                                    val result = response.chart.result?.firstOrNull()?.meta
                                    
                                    if (result != null) {
                                        val current = result.regularMarketPrice ?: BigDecimal.ZERO
                                        if (current.compareTo(BigDecimal.ZERO) == 0) return@async null
                                        
                                        var change = result.regularMarketChangePercent?.let { BigDecimal.valueOf(it) }
                                        val prev = result.previousClose ?: result.chartPreviousClose
                                        
                                        if (change == null && prev != null && prev.compareTo(BigDecimal.ZERO) > 0) {
                                            change = (current - prev).divide(prev, 10, RoundingMode.HALF_UP).multiply(BigDecimal("100"))
                                        }
                                        
                                        val finalChange = change?.setScale(2, RoundingMode.HALF_UP) ?: BigDecimal.ZERO
                                        
                                        android.util.Log.d("CUZDAN_LOG", "FetchMarket($sym) -> Price: $current, Change: $finalChange%, Prev: ${result.previousClose}, ChartPrev: ${result.chartPreviousClose}, MetaPct: ${result.regularMarketChangePercent}")

                                        val exist = marketAssetDao.getMarketAssetBySymbolAndTypeOnce(sym, type)
                                        
                                        // Commodities and Forex from Yahoo are natively in USD (mostly)
                                        val itemCurrency = if (type == AssetType.BIST) "TRY" else "USD"
                                        
                                        cleanMarketAssetNaming(MarketAsset(
                                            symbol = sym, 
                                            name = result.shortName ?: result.longName ?: sym, 
                                            fullName = result.longName ?: result.shortName ?: sym, 
                                            currentPrice = current.setScale(4, RoundingMode.HALF_UP), 
                                            dailyChangePercentage = finalChange, 
                                            assetType = type, 
                                            currency = itemCurrency, 
                                            isFavorite = exist?.isFavorite ?: false,
                                            lastUpdated = System.currentTimeMillis()
                                        ), type)
                                    } else null
                                } catch (e: Exception) {
                                    android.util.Log.e("CuzdanDebug", "Resilient fetch FAILED for $sym: ${e.message}")
                                    null
                                }
                            }
                        }.awaitAll().filterNotNull()
                    }
                    marketAssets.addAll(results)

                    if (type == AssetType.EMTIA) {
                        // Safe Gram Altin Calculation using getChartData for USDTRY as well
                        val usdTryPrice = try {
                            val res = yahooFinanceApi.getChartData("USDTRY=X", range = "1d", interval = "1d").chart.result?.firstOrNull()?.meta
                            res?.regularMarketPrice ?: BigDecimal.ZERO
                        } catch(e: Exception) {
                            // Last resort fallback to DB
                            marketAssetDao.getMarketAssetBySymbolAndTypeOnce("USDTRY=X", AssetType.DOVIZ)?.currentPrice ?: BigDecimal.ZERO
                        }
                        
                        val ons = marketAssets.find { it.symbol == "GOLD" || it.symbol == "GC=F" || it.symbol == "XAUUSD=X" }
                        android.util.Log.d("CuzdanDebug", "EMTIA processing: onsFound=${ons != null}, usdTryPrice=$usdTryPrice")
                        
                        if (ons != null && usdTryPrice > BigDecimal.ZERO) {
                            val gp = ons.currentPrice.divide(BigDecimal("31.1035"), 8, RoundingMode.HALF_UP).multiply(usdTryPrice)
                            marketAssets.add(MarketAsset("GRAM_ALTIN", "Gram Altın", "Gram Altın", gp.setScale(2, RoundingMode.HALF_UP), ons.dailyChangePercentage, AssetType.EMTIA, "TRY"))
                        } else if (marketAssets.find { it.symbol == "GRAM_ALTIN" } == null) {
                            marketAssets.add(MarketAsset("GRAM_ALTIN", "Gram Altın", "Gram Altın", BigDecimal.ZERO, BigDecimal.ZERO, AssetType.EMTIA, "TRY"))
                        }
                    }
                    android.util.Log.d("CuzdanDebug", "Final results to save for $type: ${marketAssets.size}")
                }
            }
            if (marketAssets.isNotEmpty()) {
                // To avoid duplicates when symbols change (e.g. GC=F -> GOLD), 
                // we clear the type but preserve favorites.
                val currentFavorites = marketAssetDao.getMarketAssetsByTypeOnce(type)
                    .filter { it.isFavorite }
                    .associateBy { it.symbol }

                val deduplicatedAssets = marketAssets.map { asset ->
                    if (currentFavorites.containsKey(asset.symbol)) {
                        asset.copy(isFavorite = true)
                    } else asset
                }

                marketAssetDao.deleteMarketAssetsByType(type)
                marketAssetDao.insertMarketAssets(deduplicatedAssets)
                android.util.Log.d("CuzdanDebug", "Successfully saved to Database")
            }
        } catch (e: Exception) { android.util.Log.e("CuzdanDebug", "Internal error: ${e.message}") }
    }

    suspend fun searchAssets(query: String, type: AssetType): List<MarketAsset> {
        if (query.isBlank()) return emptyList()
        try {
            // STEP 1: Search local database first (Much faster with SQL LIKE)
            val localResults = marketAssetDao.searchMarketAssetsOnce(query, type)
            
            // If we have many local results for non-crypto types, return them (they are more standard)
            if (type != AssetType.KRIPTO && localResults.size >= 10) {
                return localResults
            }

            // STEP 2: Remote search
            val remoteResults = when (type) {
                AssetType.KRIPTO -> {
                    try {
                        binanceApi.getAllTickers()
                            .filter { it.symbol.endsWith("USDT") && it.symbol.contains(query, ignoreCase = true) }
                            .take(20).map { ticker ->
                                MarketAsset(
                                    symbol = ticker.symbol,
                                    name = ticker.symbol.replace("USDT", ""),
                                    fullName = ticker.symbol.replace("USDT", ""),
                                    currentPrice = ticker.lastPrice.toBigDecimalOrNull() ?: BigDecimal.ZERO,
                                    dailyChangePercentage = ticker.priceChangePercent.toBigDecimalOrNull()?.setScale(2, RoundingMode.HALF_UP) ?: BigDecimal.ZERO,
                                    assetType = AssetType.KRIPTO,
                                    currency = "USD"
                                )
                            }
                    } catch (e: Exception) { emptyList() }
                }
                AssetType.BIST -> {
                    coroutineScope {
                        // Parallel search: Static list matches + Yahoo API search
                        val staticSymbols = BistSymbols.all.filter { it.contains(query, ignoreCase = true) }.take(10)
                        
                        val yahooSearch = try { yahooFinanceApi.search(query) } catch (e: Exception) { null }
                        val remoteBistSymbols = yahooSearch?.quotes?.filter { it.symbol.endsWith(".IS") }?.map { it.symbol } ?: emptyList()
                        
                        val allCandidateSymbols = (staticSymbols + remoteBistSymbols).distinct().take(15)
                        
                        allCandidateSymbols.map { sym ->
                            async { fetchYahooMarketAsset(sym, AssetType.BIST) }
                        }.awaitAll().filterNotNull()
                    }
                }
                else -> {
                    try {
                        val response = yahooFinanceApi.search(query)
                        val symbols = response.quotes?.map { it.symbol }?.take(15) ?: emptyList()
                        if (symbols.isEmpty()) emptyList()
                        else {
                            val quotes = yahooFinanceApi.getQuotes(symbols.joinToString(",")).quoteResponse.result ?: emptyList()
                            quotes.map { quote ->
                                MarketAsset(
                                    symbol = quote.symbol,
                                    name = quote.shortName ?: quote.longName ?: quote.symbol,
                                    fullName = quote.longName ?: quote.shortName ?: quote.symbol,
                                    currentPrice = (quote.regularMarketPrice ?: BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP),
                                    dailyChangePercentage = (quote.regularMarketChangePercent ?: BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP),
                                    assetType = type,
                                    currency = quote.currency ?: "USD"
                                )
                            }
                        }
                    } catch (e: Exception) { emptyList() }
                }
            }
            
            val totalResults = (localResults + remoteResults).distinctBy { it.symbol }.map { cleanMarketAssetNaming(it, type) }
            
            // Persist newly discovered assets to local Market database asynchronously
            if (remoteResults.isNotEmpty()) {
                repositoryScope.launch {
                    try {
                        val toInsert = remoteResults.map { asset ->
                            val existing = marketAssetDao.getMarketAssetBySymbolAndTypeOnce(asset.symbol, asset.assetType)
                            asset.copy(isFavorite = existing?.isFavorite ?: false)
                        }
                        marketAssetDao.insertMarketAssets(toInsert)
                    } catch (e: Exception) {
                        Log.e("AssetRepo", "Error persisting search results: ${e.message}")
                    }
                }
            }
            
            return totalResults
        } catch (e: Exception) {
            Log.e("AssetRepo", "searchAssets failed for query=$query: ${e.message}")
            return emptyList()
        }
    }

    suspend fun getMarketAssetsOnce(type: AssetType): List<MarketAsset> {
        val assets = marketAssetDao.getMarketAssetsByTypeOnce(type)
        return if (type == AssetType.KRIPTO) {
            assets.filter { it.symbol.endsWith("USDT") }
        } else if (type == AssetType.DOVIZ) {
            assets.filter { it.symbol != "AEDTRY=X" }
        } else {
            assets
        }
    }

    /**
     * Varlığı veritabanına kaydeder veya mevcutsa günceller.
     * Miktarı toplamaz, gönderilen varlık nesnesi nihai haldir.
     */
    /**
     * Varlığı veritabanına kaydeder veya mevcutsa günceller.
     * Miktarı toplamaz, gönderilen varlık nesnesi nihai haldir.
     * Var olan TÜM mükerrer kayıtları temizleyerek tekil bir kayıt bırakır (Bug 1 Fix).
     */
    suspend fun getMarketAssetBySymbolAndTypeOnce(symbol: String, type: AssetType): MarketAsset? {
        return marketAssetDao.getMarketAssetBySymbolAndTypeOnce(symbol, type)
    }

    suspend fun addAsset(asset: Asset) {
        val portfolioAssets = assetDao.getAssetsByPortfolioIdOnce(asset.portfolioId)
        val existingMatches = portfolioAssets.filter { it.symbol == asset.symbol }
        
        if (existingMatches.isNotEmpty()) {
            // İlkini güncelle, diğerlerini sil (mükerrer kayıtları temizle)
            val primary = existingMatches.first()
            assetDao.updateAsset(asset.copy(id = primary.id))
            
            if (existingMatches.size > 1) {
                existingMatches.drop(1).forEach { assetDao.deleteAsset(it) }
            }
        } else {
            assetDao.insertAsset(asset)
        }
    }

    suspend fun upsertAsset(asset: Asset) {
        val existing = assetDao.getAssetBySymbolAndPortfolioId(asset.symbol, asset.portfolioId)
        if (existing != null) assetDao.updateAsset(existing.copy(currentPrice = asset.currentPrice, dailyChangePercentage = asset.dailyChangePercentage)) else assetDao.insertAsset(asset)
    }

    suspend fun getAssetHistory(symbol: String, range: String = "1d", interval: String = "1m"): List<Pair<Long, Double>> {
        if (symbol == "TRY" || symbol == "TL") {
            return listOf(
                System.currentTimeMillis() - 86400000 to 1.0,
                System.currentTimeMillis() to 1.0
            )
        }

        // Special synthetic symbol: Gram Altın (TRY) = (Ons Altın (USD) / 31.1) * USDTRY
        if (symbol.uppercase() == "GRAM_ALTIN") {
            return try {
                val ons = yahooFinanceApi.getChartData("GC=F", range, interval).chart.result?.firstOrNull()
                val usdTry = yahooFinanceApi.getChartData("TRY=X", range, interval).chart.result?.firstOrNull()

                val onsTs = ons?.timestamp ?: emptyList()
                val onsPrices = ons?.indicators?.quote?.firstOrNull()?.close ?: emptyList()
                val usdTs = usdTry?.timestamp ?: emptyList()
                val usdPrices = usdTry?.indicators?.quote?.firstOrNull()?.close ?: emptyList()

                if (onsTs.isEmpty() || usdTs.isEmpty()) return emptyList()

                val onsSeries = onsTs.zip(onsPrices).mapNotNull { (ts, p) -> p?.let { ts * 1000 to it.toDouble() } }
                val usdSeries = usdTs.zip(usdPrices).mapNotNull { (ts, p) -> p?.let { ts * 1000 to it.toDouble() } }

                if (onsSeries.isEmpty() || usdSeries.isEmpty()) return emptyList()

                val allTs = (onsSeries.map { it.first } + usdSeries.map { it.first }).distinct().sorted()
                fun lastValueAt(series: List<Pair<Long, Double>>, t: Long): Double? =
                    series.lastOrNull { it.first <= t }?.second ?: series.firstOrNull()?.second

                val ozToGram = 31.1
                allTs.mapNotNull { t ->
                    val onsP = lastValueAt(onsSeries, t)
                    val usdP = lastValueAt(usdSeries, t)
                    if (onsP == null || usdP == null) null else t to (onsP / ozToGram) * usdP
                }
            } catch (_: Exception) {
                emptyList()
            }
        }

        return try {
            val clean = symbol.uppercase()
            val target = when {
                // Binance-style symbols stored as e.g. BTCUSDT, ETHUSDT...
                clean.endsWith("USDT") && clean.length > 4 -> "${clean.dropLast(4)}-USD"
                // Common crypto tickers without suffix
                !clean.contains(".") && !clean.contains("=X") && !clean.contains("-USD") &&
                    clean.all { it.isLetterOrDigit() } && clean.length in 2..6 ->
                    "$clean-USD"
                // BIST tickers should use .IS, but avoid forcing it for synthetic/other symbols
                !clean.contains(".") && !clean.contains("=X") && !clean.contains("-USD") &&
                    clean.all { it.isUpperCase() || it.isDigit() } && !clean.contains("_") ->
                    "$clean.IS"
                else -> symbol
            }

            val result = yahooFinanceApi.getChartData(target, range, interval).chart.result?.firstOrNull()
            val timestamps = result?.timestamp ?: emptyList()
            val prices = result?.indicators?.quote?.firstOrNull()?.close ?: emptyList()
            timestamps.zip(prices).mapNotNull { (ts, p) -> p?.let { ts * 1000 to it.toDouble() } }
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun getPortfolioById(id: Long) = portfolioDao.getPortfolioById(id)
    suspend fun getAssetBySymbolAndPortfolioId(s: String, pId: Long): Asset? = assetDao.getAssetBySymbolAndPortfolioId(s, pId)
    suspend fun deleteAsset(a: Asset) = assetDao.deleteAsset(a)
    suspend fun toggleFavorite(s: String, t: AssetType, f: Boolean) = marketAssetDao.updateFavorite(s, t, f)

    fun getAllPriceAlerts(): Flow<List<PriceAlert>> = priceAlertDao.getAllAlerts()
    fun getAlertsForAsset(s: String, t: AssetType): Flow<List<PriceAlert>> = priceAlertDao.getAlertsForAsset(s, t)
    suspend fun insertPriceAlert(a: PriceAlert) = priceAlertDao.insertAlert(a)
    suspend fun updatePriceAlert(a: PriceAlert) = priceAlertDao.updateAlert(a)
    suspend fun deletePriceAlert(a: PriceAlert) = priceAlertDao.deleteAlert(a)
    suspend fun markAlertAsTriggered(id: Long) = priceAlertDao.markAsTriggered(id)
    suspend fun getActivePriceAlerts() = priceAlertDao.getActiveAlerts()

    suspend fun recordPortfolioSnapshot(pId: Long, v: BigDecimal, c: String) = portfolioHistoryDao.insert(PortfolioHistory(portfolioId = pId, date = System.currentTimeMillis(), totalValue = v, currency = c))
    suspend fun getLatestHistoryBefore(pId: Long, date: Long): PortfolioHistory? = portfolioHistoryDao.getLatestBefore(pId, date)
    fun getPortfolioHistory(pId: Long): Flow<List<PortfolioHistory>> = portfolioHistoryDao.getAllHistory(pId)

    suspend fun reconstructPortfolioHistory(pId: Long, range: String): List<PortfolioHistory> = coroutineScope {
        val assets = if (pId == -1L) portfolioDao.getIncludedPortfoliosOnce().flatMap { assetDao.getAssetsByPortfolioIdOnce(it.id) }.groupBy { it.symbol }.map { (_, sAssets) ->
            val totalAmt = sAssets.fold(BigDecimal.ZERO) { acc, a -> acc + a.amount }
            val totalCost = sAssets.fold(BigDecimal.ZERO) { acc, a -> acc + (a.amount * a.averageBuyPrice) }
            sAssets.first().copy(amount = totalAmt, averageBuyPrice = if (totalAmt > BigDecimal.ZERO) totalCost.divide(totalAmt, 8, RoundingMode.HALF_UP) else BigDecimal.ZERO)
        } else assetDao.getAssetsByPortfolioIdOnce(pId)

        if (assets.isEmpty()) return@coroutineScope emptyList()
        val interval = if (range == "7d") "1h" else "1d"
        val histories = assets.map { a -> async { a to getAssetHistory(a.symbol, range, interval) } }.awaitAll()
        val usdHistory = getAssetHistory("USDTRY=X", range, interval)
        val eurHistory = getAssetHistory("EURTRY=X", range, interval)
        val allTs = (histories.flatMap { it.second.map { p -> p.first } } + usdHistory.map { it.first } + eurHistory.map { it.first }).distinct().sorted()

        // Total cost base = sum of (amount * averageBuyPrice * exchangeRate) for all assets
        var totalCostBase = BigDecimal.ZERO
        assets.forEach { a ->
            val rate = when(a.currency) { "USD" -> usdHistory.lastOrNull()?.second ?: 44.52; "EUR" -> eurHistory.lastOrNull()?.second ?: 35.2; else -> 1.0 }
            totalCostBase += (a.amount * a.averageBuyPrice * BigDecimal(rate.toString()))
        }

        allTs.map { ts ->
            var dayVal = BigDecimal.ZERO
            histories.forEach { (a, h) ->
                val p = h.lastOrNull { it.first <= ts }?.second ?: h.firstOrNull()?.second ?: 0.0
                val rate = when(a.currency) { "USD" -> usdHistory.lastOrNull { it.first <= ts }?.second ?: 44.52; "EUR" -> eurHistory.lastOrNull { it.first <= ts }?.second ?: 35.0; else -> 1.0 }
                dayVal += (a.amount * BigDecimal(p.toString()) * BigDecimal(rate.toString()))
            }
            // profitLoss = current market value at this timestamp - total cost of all assets
            PortfolioHistory(portfolioId = pId, date = ts, totalValue = dayVal, currency = "TRY", profitLoss = dayVal - totalCostBase)
        }
    }

    private fun cleanMarketAssetNaming(asset: MarketAsset, type: AssetType): MarketAsset {
        var name = asset.name
        var symbol = asset.symbol
        var fullName = asset.fullName ?: asset.name
        val cleanSymbol = symbol.uppercase().replace(".IS", "").replace("=F", "").replace("=X", "")

        when {
            type == AssetType.BIST -> {
                // User wants Ticker as Title (name) and Company as Subtitle (fullName)
                val ticker = symbol.replace(".IS", "").replace(".is", "").trim().uppercase()
                // Use the longer name as the company name to avoid losing data
                val companyName = if (fullName.length > name.length) fullName else name
                fullName = companyName.replace(".IS", "").replace(".is", "").trim()
                name = ticker
                symbol = ticker
                android.util.Log.d("CUZDAN_LOG", "CleanBIST: Sym=$symbol, Name=$name, Full=$fullName")
            }
            type == AssetType.KRIPTO -> {
                symbol = symbol.replace("USDT", "").replace("usdt", "").trim()
                name = symbol
            }
            type == AssetType.DOVIZ || type == AssetType.NAKIT || (type == AssetType.EMTIA && (cleanSymbol == "TRY" || cleanSymbol == "USDTRY")) -> {
                val localized = when {
                    cleanSymbol.contains("USDTRY") || cleanSymbol == "USD" -> "Amerikan Doları"
                    cleanSymbol == "TRY"  -> "Türk Lirası"
                    cleanSymbol.contains("EURTRY") || cleanSymbol == "EUR" -> "Euro"
                    cleanSymbol.contains("GBPTRY") || cleanSymbol == "GBP" -> "İngiliz Sterlini"
                    cleanSymbol.contains("CHFTRY") || cleanSymbol == "CHF" -> "İsviçre Frangı"
                    cleanSymbol.contains("JPYTRY") || cleanSymbol == "JPY" -> "Japon Yeni"
                    cleanSymbol.contains("AUDTRY") || cleanSymbol == "AUD" -> "Avustralya Doları"
                    cleanSymbol.contains("CADTRY") || cleanSymbol == "CAD" -> "Kanada Doları"
                    cleanSymbol.contains("NZDTRY") || cleanSymbol == "NZD" -> "Y. Zelanda Doları"
                    cleanSymbol.contains("SGDTRY") || cleanSymbol == "SGD" -> "Singapur Doları"
                    cleanSymbol.contains("AEDTRY") || cleanSymbol == "AED" -> "BAE Dirhemi"
                    else -> fullName
                }
                fullName = localized
                name = localized
                symbol = when {
                    cleanSymbol.contains("USD") -> "USD"
                    cleanSymbol.contains("EUR") -> "EUR"
                    cleanSymbol.contains("GBP") -> "GBP"
                    cleanSymbol.contains("CHF") -> "CHF"
                    cleanSymbol.contains("JPY") -> "JPY"
                    cleanSymbol.contains("AUD") -> "AUD"
                    cleanSymbol.contains("CAD") -> "CAD"
                    cleanSymbol.contains("AED") -> "AED"
                    else -> symbol
                }
            }
            type == AssetType.EMTIA -> {
                val naming = when {
                    asset.symbol == "GC=F" -> "Altın (Ons)" to "GOLD"
                    asset.symbol == "SI=F" -> "Gümüş (Ons)" to "SILVER"
                    asset.symbol == "HG=F" -> "Bakır" to "HG"
                    asset.symbol == "CL=F" -> "Ham Petrol" to "CL"
                    asset.symbol == "BZ=F" -> "Brent Petrol" to "BZ"
                    asset.symbol == "NG=F" -> "Doğal Gaz" to "NG"
                    asset.symbol == "ALI=F" -> "Alüminyum" to "ALI"
                    asset.symbol == "NI=F" -> "Nikel" to "NI"
                    asset.symbol == "ZN=F" -> "Çinko" to "ZN"
                    asset.symbol == "PA=F" -> "Paladyum" to "PA"
                    asset.symbol == "PL=F" -> "Platin" to "PL"
                    asset.symbol == "KC=F" -> "Kahve" to "KC"
                    asset.symbol == "CC=F" -> "Kakao" to "CC"
                    asset.symbol == "CT=F" -> "Pamuk" to "CT"
                    asset.symbol == "SB=F" -> "Şeker" to "SB"
                    asset.symbol == "ZC=F" -> "Mısır" to "ZC"
                    asset.symbol == "ZW=F" -> "Buğday" to "ZW"
                    asset.symbol == "ZS=F" -> "Soya Fasulyesi" to "ZS"
                    asset.symbol == "LBS=F" -> "Kereste" to "LBS"
                    asset.symbol == "RB=F" -> "RBOB Benzin" to "RB"
                    asset.symbol == "HO=F" -> "Isınma Yakıtı" to "HO"
                    asset.symbol == "GRAM_ALTIN" -> "Gram Altın" to "GRAM_ALTIN"
                    else -> name.replace(" Futures", "") to symbol.replace("=F", "")
                }
                name = naming.first
                symbol = naming.second
                fullName = name
            }
        }
        return asset.copy(name = name, fullName = fullName, symbol = symbol)
    }

    private fun cleanAssetNaming(asset: Asset, type: AssetType): Asset {
        var name = asset.name; var symbol = asset.symbol; val cleanSymbol = symbol.uppercase()
        when {
            type == AssetType.BIST -> {
                name = name.replace(".IS", "").replace(".is", "").trim()
                symbol = symbol.replace(".IS", "").replace(".is", "").trim()
            }
            type == AssetType.KRIPTO -> {
                symbol = symbol.replace("USDT", "").replace("usdt", "").trim()
                if (name.contains("USDT", ignoreCase = true)) {
                    name = name.replace("USDT", "", ignoreCase = true).trim()
                }
            }
            type == AssetType.DOVIZ || type == AssetType.NAKIT || (type == AssetType.EMTIA && (cleanSymbol == "TRY=X" || cleanSymbol == "USDTRY=X")) -> {
                name = when { 
                    cleanSymbol.contains("USD") -> "Amerikan Doları"
                    cleanSymbol.contains("EUR") -> "Euro"
                    cleanSymbol.contains("GBP") -> "İngiliz Sterlini"
                    cleanSymbol.contains("CHF") -> "İsviçre Frangı"
                    cleanSymbol.contains("JPY") -> "Japon Yeni"
                    cleanSymbol.contains("AUD") -> "Avustralya Doları"
                    cleanSymbol.contains("CAD") -> "Kanada Doları"
                    cleanSymbol.contains("AED") -> "Birleşik Arap Emirlikleri Dirhemi"
                    else -> name 
                }
                symbol = when { 
                    cleanSymbol.contains("USD") -> "USD"
                    cleanSymbol.contains("EUR") -> "EUR"
                    cleanSymbol.contains("GBP") -> "GBP"
                    cleanSymbol.contains("CHF") -> "CHF"
                    cleanSymbol.contains("JPY") -> "JPY"
                    cleanSymbol.contains("AUD") -> "AUD"
                    cleanSymbol.contains("CAD") -> "CAD"
                    cleanSymbol.contains("AED") -> "AED"
                    else -> symbol 
                }
            }
            type == AssetType.EMTIA -> {
                name = when { 
                    cleanSymbol.startsWith("GC=F") || cleanSymbol == "GOLD" -> "Altın (Ons)"
                    cleanSymbol.startsWith("SI=F") || cleanSymbol == "SILVER" -> "Gümüş"
                    cleanSymbol.startsWith("PL=F") -> "Platin"
                    cleanSymbol.startsWith("PA=F") -> "Paladyum"
                    cleanSymbol.startsWith("HG=F") -> "Bakır"
                    cleanSymbol.startsWith("GRAM_ALTIN") -> "Gram Altın"
                    else -> name 
                }
                name = name.replace(Regex("\\s+[A-Za-z]{3}\\s+\\d{2}$"), "").trim()
                if (cleanSymbol.startsWith("GC=F")) symbol = "GOLD"
                if (cleanSymbol.startsWith("SI=F")) symbol = "SILVER"
            }
        }
        return asset.copy(name = name, symbol = symbol)
    }

    private fun refreshBistIncrementally() {
        if (bistJob?.isActive == true) return
        
        bistJob = repositoryScope.launch {
            try {
                // Her yenilemede eski .IS uzantılı hatalı verileri temizle
                marketAssetDao.cleanStaleBistSymbols()
                
                val dbSymbols = assetDao.getAllAssets().first()
                    .filter { it.assetType == AssetType.BIST }
                    .map { if (it.symbol.endsWith(".IS")) it.symbol else "${it.symbol}.IS" }

                val allSymbols = (BistSymbols.all + dbSymbols).distinct()
                val popularSymbols = BistSymbols.popular
                
                // 1. Önce popüler hisseleri hemen çek (İlk yükleme hissi için)
                val initialResults = coroutineScope {
                    popularSymbols.map { sym ->
                        async { fetchYahooMarketAsset(sym, AssetType.BIST) }
                    }.awaitAll().filterNotNull()
                }
                if (initialResults.isNotEmpty()) {
                    marketAssetDao.insertMarketAssets(initialResults)
                    // Cleanup old .IS symbols if they exist
                    initialResults.forEach { 
                        if (!it.symbol.endsWith(".IS")) {
                            marketAssetDao.deleteMarketAssetBySymbolAndType("${it.symbol}.IS", AssetType.BIST)
                        }
                    }
                }

                // 2. Geri kalan hisseleri 20'şerli gruplar halinde her saniye çek
                val remainingSymbols = allSymbols.filter { it !in popularSymbols }
                remainingSymbols.chunked(20).forEach { chunk ->
                    val chunkResults = coroutineScope {
                        chunk.map { sym ->
                            async { fetchYahooMarketAsset(sym, AssetType.BIST) }
                        }.awaitAll().filterNotNull()
                    }
                    
                    if (chunkResults.isNotEmpty()) {
                        marketAssetDao.insertMarketAssets(chunkResults)
                        // Cleanup old .IS symbols in chunks
                        chunkResults.forEach { 
                            if (!it.symbol.endsWith(".IS")) {
                                marketAssetDao.deleteMarketAssetBySymbolAndType("${it.symbol}.IS", AssetType.BIST)
                            }
                        }
                    }
                    delay(1000) // Her 20'li gruptan sonra 1 saniye bekle
                }
            } catch (e: Exception) {
                Log.e("AssetRepository", "Incremental BIST load failed: ${e.message}")
            }
        }
    }

    private suspend fun fetchYahooMarketAsset(sym: String, type: AssetType): MarketAsset? {
        return try {
            val response = yahooFinanceApi.getChartData(sym)
            val result = response.chart.result?.firstOrNull()?.meta
            if (result != null) {
                val current = result.regularMarketPrice ?: BigDecimal.ZERO
                
                // Prioritize regularMarketChangePercent if available
                var change = result.regularMarketChangePercent?.let { BigDecimal.valueOf(it).setScale(2, RoundingMode.HALF_UP) }
                
                if (change == null) {
                    val prev = result.previousClose ?: result.chartPreviousClose ?: current
                    change = if (prev > BigDecimal.ZERO) {
                        current.subtract(prev).divide(prev, 4, RoundingMode.HALF_UP).multiply(BigDecimal("100")).setScale(2, RoundingMode.HALF_UP)
                    } else BigDecimal.ZERO
                }
                
                Log.d("CUZDAN_LOG", "FetchMarket($sym) -> Price: $current, Change: $change%, Prev: ${result.previousClose}, ChartPrev: ${result.chartPreviousClose}, MetaPct: ${result.regularMarketChangePercent}")

                val exist = marketAssetDao.getMarketAssetBySymbolAndTypeOnce(sym, type)
                cleanMarketAssetNaming(MarketAsset(
                    symbol = sym, 
                    name = result.shortName ?: result.longName ?: sym, 
                    fullName = result.longName ?: result.shortName ?: sym, 
                    currentPrice = current.setScale(4, RoundingMode.HALF_UP), 
                    dailyChangePercentage = change ?: BigDecimal.ZERO, 
                    assetType = type, 
                    currency = result.currency ?: "USD", 
                    isFavorite = exist?.isFavorite ?: false,
                    lastUpdated = System.currentTimeMillis()
                ), type)
            } else null
        } catch (e: Exception) { 
            Log.e("CUZDAN_LOG", "fetchYahooMarketAsset Error for $sym: ${e.message}")
            null 
        }
    }
}
