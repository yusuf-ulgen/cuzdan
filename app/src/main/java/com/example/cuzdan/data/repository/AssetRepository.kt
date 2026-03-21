package com.example.cuzdan.data.repository

import com.example.cuzdan.data.local.dao.AssetDao
import com.example.cuzdan.data.local.dao.PortfolioDao
import com.example.cuzdan.data.local.entity.Asset
import com.example.cuzdan.data.local.entity.AssetType
import com.example.cuzdan.data.remote.api.BinanceApi
import com.example.cuzdan.data.remote.api.YahooFinanceApi
import com.example.cuzdan.data.remote.api.TefasApi
import com.example.cuzdan.data.remote.model.TefasRequest
import com.example.cuzdan.data.remote.model.YahooQuote
import com.example.cuzdan.data.local.dao.MarketAssetDao
import com.example.cuzdan.data.local.dao.PortfolioHistoryDao
import com.example.cuzdan.data.local.entity.PortfolioHistory
import com.example.cuzdan.data.local.entity.MarketAsset
import com.example.cuzdan.util.Resource
import kotlinx.coroutines.flow.Flow
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
    private val portfolioDao: PortfolioDao
) {
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
    fun getMarketAssetsFlow(type: AssetType): Flow<List<MarketAsset>> {
        return marketAssetDao.getMarketAssetsByType(type)
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
     * Binance API'den kripto fiyatlarını günceller.
     */
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

    /**
     * Yahoo Finance API'den BIST, Döviz ve Altın fiyatlarını günceller.
     */
    suspend fun refreshYahooPrices(): Flow<Resource<Unit>> = flow {
        emit(Resource.Loading())
        try {
            val otherAssets = getOtherAssets().first()
            if (otherAssets.isEmpty()) {
                // Varsayılan BIST, Döviz ve Altın varlıklarını ekle
                val defaultOther = listOf(
                    Asset(symbol = "THYAO.IS", name = "Türk Hava Yolları", amount = BigDecimal.ZERO, averageBuyPrice = BigDecimal.ZERO, currentPrice = BigDecimal.ZERO, dailyChangePercentage = BigDecimal.ZERO, assetType = AssetType.BIST),
                    Asset(symbol = "TRY=X", name = "USD/TRY", amount = BigDecimal.ZERO, averageBuyPrice = BigDecimal.ZERO, currentPrice = BigDecimal.ZERO, dailyChangePercentage = BigDecimal.ZERO, assetType = AssetType.DOVIZ),
                    Asset(symbol = "GC=F", name = "Ons Altın", amount = BigDecimal.ZERO, averageBuyPrice = BigDecimal.ZERO, currentPrice = BigDecimal.ZERO, dailyChangePercentage = BigDecimal.ZERO, assetType = AssetType.EMTIA),
                    Asset(symbol = "GRAM_ALTIN", name = "Gram Altın", amount = BigDecimal.ZERO, averageBuyPrice = BigDecimal.ZERO, currentPrice = BigDecimal.ZERO, dailyChangePercentage = BigDecimal.ZERO, assetType = AssetType.EMTIA)
                )
                defaultOther.forEach { assetDao.insertAsset(it) }
            }

            // Gram Altın hesabı için gerekli veriler
            var onsPrice: BigDecimal? = null
            var usdTryPrice: BigDecimal? = null
            var onsChange: BigDecimal = BigDecimal.ZERO
            var usdTryChange: BigDecimal = BigDecimal.ZERO

            // Güncel listeyi al (yeni eklenenler dahil)
            val currentOtherAssets = getOtherAssets().first()

            currentOtherAssets.forEach { asset ->
                if (asset.symbol == "GRAM_ALTIN") return@forEach // Gram altın hesabı sonda yapılacak

                try {
                    val response = yahooFinanceApi.getChartData(asset.symbol)
                    val result = response.chart.result?.firstOrNull()?.meta
                    val price = result?.regularMarketPrice ?: BigDecimal.ZERO
                    val prevClose = result?.previousClose ?: BigDecimal.ZERO
                    
                    val changePerc = if (prevClose > BigDecimal.ZERO) {
                        price.subtract(prevClose)
                            .divide(prevClose, 4, RoundingMode.HALF_UP)
                            .multiply(BigDecimal("100"))
                    } else {
                        BigDecimal.ZERO
                    }
                    val currency = result?.currency ?: "USD"

                    
                    if (asset.symbol == "GC=F") {

                        onsPrice = price
                        onsChange = changePerc
                    }
                    if (asset.symbol == "TRY=X") {
                        usdTryPrice = price
                        usdTryChange = changePerc
                    }

                    assetDao.updateAsset(asset.copy(
                        currentPrice = price,
                        dailyChangePercentage = changePerc,
                        currency = currency
                    ))

                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            // Gram Altın Hesaplaması: (Ons / 31.1) * USD/TRY
            if (onsPrice != null && usdTryPrice != null) {
                val gramGoldPrice = onsPrice!!
                    .divide(BigDecimal("31.1"), 8, RoundingMode.HALF_UP)
                    .multiply(usdTryPrice!!)
                
                // Gram Altın Değişimi (Yaklaşık olarak Ons + USD/TRY değişimi)
                val gramGoldChange = onsChange.add(usdTryChange)
                
                assetDao.getAssetBySymbol("GRAM_ALTIN")?.let { asset ->
                    assetDao.updateAsset(asset.copy(
                        currentPrice = gramGoldPrice,
                        dailyChangePercentage = gramGoldChange
                    ))
                }
            }

            emit(Resource.Success(Unit))
        } catch (e: Exception) {
            emit(Resource.Error(e.message ?: "Yahoo verileri güncellenemedi"))
        }
    }

    /**
     * TEFAS API'den sahip olunan fonların fiyatlarını günceller.
     */
    suspend fun refreshOwnedFundPrices(): Flow<Resource<Unit>> = flow {
        emit(Resource.Loading())
        try {
            val fundAssets = getFundAssets().first()
            if (fundAssets.isEmpty()) {
                Log.d("AssetRepo", "[FON] No owned funds to refresh.")
                emit(Resource.Success(Unit))
                return@flow
            }

            Log.d("AssetRepo", "[FON] Refreshing ${fundAssets.size} owned funds...")
            val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
            
            fundAssets.forEach { asset ->
                val calendar = java.util.Calendar.getInstance()
                var price = BigDecimal.ZERO
                // Son 5 gün içinde fiyat ara (Hafta sonu/Tatil kontrolü)
                Log.d("AssetRepo", "[FON] Fetching price for ${asset.symbol} (${asset.name})")
                for (i in 0..5) {
                    try {
                        val dateStr = sdf.format(calendar.time)
                        val response = tefasApi.getFundPrices(TefasRequest(fundType = asset.symbol, date = dateStr))
                        val entry = response.firstOrNull()
                        if (entry != null) {
                            val rawPrice = entry.price
                            val parsedPrice = parseTefasPrice(rawPrice)
                            if (parsedPrice > BigDecimal.ZERO) {
                                price = parsedPrice
                                Log.d("AssetRepo", "[FON] Success: ${asset.symbol} price=$price (Date: $dateStr)")
                                break
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("AssetRepo", "[FON] Try $i failed for ${asset.symbol}: ${e.message}")
                    }
                    calendar.add(java.util.Calendar.DATE, -1)
                }

                if (price > BigDecimal.ZERO) {
                    assetDao.updateAsset(asset.copy(
                        currentPrice = price,
                        dailyChangePercentage = BigDecimal.ZERO, 
                        currency = "TRY"
                    ))
                } else {
                    Log.w("AssetRepo", "[FON] Could not find any price for ${asset.symbol} in last 5 days.")
                }
            }
            emit(Resource.Success(Unit))
        } catch (e: Exception) {
            Log.e("AssetRepo", "[FON] Global error refreshing funds: ${e.message}")
            emit(Resource.Error(e.message ?: "Fon fiyatları güncellenemedi"))
        }
    }

    private fun parseTefasPrice(rawPriceAny: Any?): BigDecimal {
        Log.v("AssetRepo", "[FON] Parsing price: $rawPriceAny (${rawPriceAny?.javaClass?.simpleName})")
        return when (rawPriceAny) {
            is Number -> BigDecimal(rawPriceAny.toString())
            is String -> {
                val cleanStr = rawPriceAny.replace("\u00A0", "").trim()
                try {
                    if (cleanStr.contains(",") && cleanStr.contains(".")) {
                        BigDecimal(cleanStr.replace(".", "").replace(",", "."))
                    } else if (cleanStr.contains(",")) {
                        BigDecimal(cleanStr.replace(",", "."))
                    } else {
                        BigDecimal(cleanStr)
                    }
                } catch (e: Exception) {
                    Log.e("AssetRepo", "[FON] Parse error for string '$rawPriceAny': ${e.message}")
                    BigDecimal.ZERO
                }
            }
            else -> {
                if (rawPriceAny != null) Log.w("AssetRepo", "[FON] Unknown price type: ${rawPriceAny.javaClass.simpleName}")
                BigDecimal.ZERO
            }
        }
    }

    /**
     * Piyasa verilerini API'den çekip DB'ye kaydeder.
     */
    suspend fun refreshMarketAssets(type: AssetType): Flow<Resource<Unit>> = flow {
        emit(Resource.Loading())
        try {
            val assets = when (type) {
                AssetType.KRIPTO -> {
                    binanceApi.getAllTickers()
                        .filter { it.symbol.endsWith("USDT") }
                        .map { ticker ->
                            val symbol = ticker.symbol
                            val existing = marketAssetDao.getMarketAssetBySymbolAndTypeOnce(symbol, AssetType.KRIPTO)
                            MarketAsset(
                                symbol = symbol,
                                name = symbol.replace("USDT", ""),
                                fullName = symbol.replace("USDT", ""),
                                currentPrice = BigDecimal(ticker.lastPrice),
                                dailyChangePercentage = BigDecimal(ticker.priceChangePercent).setScale(2, RoundingMode.HALF_UP),
                                assetType = AssetType.KRIPTO,
                                currency = "USD",
                                isFavorite = existing?.isFavorite ?: false
                            )
                        }
                }
                AssetType.FON -> {
                    val symbols = listOf(
                        "TTE", "IJP", "MAC", "GSP", "AFT", "KOC", "IPV", "OPI", "RPD", "TAU", "YAY", "TI1", "GMR",
                        "TE3", "HVS", "TDF", "IKL", "NJR", "BUY", "NNF", "BGP", "KZT", "ZPE", "OJT", "IDL", "KDV",
                        "GPA", "RTG", "OTJ", "ZPF", "YZG", "HKH", "ZHB", "AFO", "GL1", "IVY", "YAS", "IHK",
                        "EID", "ST1", "GAY", "DBH", "YHS", "ZPC", "AES", "IPJ", "GUH", "IEY", "YTD"
                    )
                    
                    kotlinx.coroutines.coroutineScope {
                        symbols.map { symbol ->
                            async {
                                val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
                                var price = BigDecimal.ZERO
                                var fundName = "$symbol Fonu"
                                val calendar = java.util.Calendar.getInstance()
                                
                                for (i in 0..5) {
                                    try {
                                        val dateStr = sdf.format(calendar.time)
                                        val response = tefasApi.getFundPrices(TefasRequest(fundType = symbol, date = dateStr))
                                        val entry = response.firstOrNull()
                                        if (entry != null) {
                                            fundName = entry.fundName ?: fundName
                                            val rawPriceAny = entry.price
                                            val parsedPrice = when (rawPriceAny) {
                                                is Number -> BigDecimal(rawPriceAny.toString())
                                                is String -> {
                                                    val cleanStr = rawPriceAny.replace("\u00A0", "").trim()
                                                    try {
                                                        if (cleanStr.contains(",") && cleanStr.contains(".")) {
                                                            BigDecimal(cleanStr.replace(".", "").replace(",", "."))
                                                        } else if (cleanStr.contains(",")) {
                                                            BigDecimal(cleanStr.replace(",", "."))
                                                        } else {
                                                            BigDecimal(cleanStr)
                                                        }
                                                    } catch (e: Exception) {
                                                        BigDecimal.ZERO
                                                    }
                                                }
                                                else -> BigDecimal.ZERO
                                            }
                                            if (parsedPrice > BigDecimal.ZERO) {
                                                price = parsedPrice.setScale(4, RoundingMode.HALF_UP)
                                                break
                                            }
                                        }
                                    } catch (e: Exception) {
                                        // Silent for individual dates
                                    }
                                    calendar.add(java.util.Calendar.DATE, -1)
                                }
                                
                                val existing = marketAssetDao.getMarketAssetBySymbolAndTypeOnce(symbol, AssetType.FON)
                                MarketAsset(
                                    symbol = symbol,
                                    name = fundName,
                                    fullName = fundName,
                                    currentPrice = price,
                                    dailyChangePercentage = BigDecimal.ZERO,
                                    assetType = AssetType.FON,
                                    currency = "TRY",
                                    isFavorite = existing?.isFavorite ?: false
                                )
                            }
                        }.awaitAll()
                    }
                }
                AssetType.NAKIT -> {
                    Log.d("AssetRepo", "[NAKIT] Refreshing...")
                    val cashPairs = listOf("USDTRY=X", "EURTRY=X", "GBPTRY=X", "CHFTRY=X", "JPYTRY=X", "GBPUSD=X")
                    val marketAssets = mutableListOf<MarketAsset>()
                    marketAssets.add(MarketAsset(
                        symbol = "TRY",
                        name = "Türk Lirası",
                        fullName = "Türk Lirası",
                        currentPrice = BigDecimal.ONE,
                        dailyChangePercentage = BigDecimal.ZERO,
                        assetType = AssetType.NAKIT,
                        currency = "TRY"
                    ))
                    
                    kotlinx.coroutines.coroutineScope {
                        cashPairs.map { symbol ->
                            async {
                                try {
                                    val response = yahooFinanceApi.getChartData(symbol)
                                    val result = response.chart.result?.firstOrNull()?.meta
                                    Log.d("AssetRepo", "[NAKIT] Fetched $symbol: ${result?.regularMarketPrice}")
                                    val code = if (symbol.contains("TRY")) symbol.take(3) else symbol.replace("=X", "")
                                    val price = result?.regularMarketPrice ?: BigDecimal.ONE
                                    val prevClose = result?.previousClose ?: price
                                    val change = if (prevClose > BigDecimal.ZERO) {
                                        price.subtract(prevClose).divide(prevClose, 4, RoundingMode.HALF_UP).multiply(BigDecimal("100"))
                                    } else BigDecimal.ZERO

                                    val existing = marketAssetDao.getMarketAssetBySymbolAndTypeOnce(code, AssetType.NAKIT)
                                    MarketAsset(
                                        symbol = code,
                                        name = when(code) {
                                            "USD" -> "Amerikan Doları"
                                            "EUR" -> "Euro"
                                            "GBP" -> "İngiliz Sterlini"
                                            "CHF" -> "İsviçre Frangı"
                                            "JPY" -> "Japon Yeni"
                                            "GBPUSD" -> "GBP/USD"
                                            else -> code
                                        },
                                        fullName = when(code) {
                                            "USD" -> "Amerikan Doları"
                                            "EUR" -> "Euro"
                                            "GBP" -> "İngiliz Sterlini"
                                            "CHF" -> "İsviçre Frangı"
                                            "JPY" -> "Japon Yeni"
                                            "GBPUSD" -> "GBP/USD"
                                            else -> code
                                        },
                                        currentPrice = price.setScale(4, RoundingMode.HALF_UP),
                                        dailyChangePercentage = change.setScale(2, RoundingMode.HALF_UP),
                                        assetType = AssetType.NAKIT,
                                        currency = "TRY",
                                        isFavorite = existing?.isFavorite ?: false
                                    )
                                } catch (e: Exception) { 
                                    Log.e("AssetRepo", "[NAKIT] Error fetching $symbol: ${e.message}")
                                    null 
                                }
                            }
                        }.awaitAll().filterNotNull().forEach { marketAssets.add(it) }
                    }
                    Log.d("AssetRepo", "[NAKIT] Refresh DONE. Total: ${marketAssets.size}")
                    marketAssets
                }
                else -> {
                    val symbols = when (type) {
                        AssetType.BIST -> ALL_BIST_SYMBOLS
                        AssetType.DOVIZ -> listOf("USDTRY=X", "EURTRY=X", "GBPTRY=X", "CHFTRY=X", "JPYTRY=X", "AUDTRY=X", "CADTRY=X")
                        AssetType.EMTIA -> listOf("GC=F", "SI=F", "PL=F", "PA=F", "HG=F", "GRAM_ALTIN", "USDTRY=X")
                        else -> emptyList()
                    }

                    // Check if fresh (< 30 mins)
                    if (type == AssetType.BIST || type == AssetType.DOVIZ) {
                        val existing = marketAssetDao.getMarketAssetsByTypeOnce(type)
                        val lastUpdate = existing.firstOrNull()?.lastUpdated ?: 0L
                        if (System.currentTimeMillis() - lastUpdate < 30 * 60 * 1000 && existing.size >= symbols.size / 2) {
                            Log.d("AssetRepo", "[$type] Skipping refresh, data is fresh (${existing.size} assets).")
                            emit(Resource.Success(Unit))
                            return@flow
                        }
                    }

                    val yahooSymbols = symbols.filter { it != "GRAM_ALTIN" }
                    val marketAssets = mutableListOf<MarketAsset>()

                    if (type == AssetType.BIST) {
                        Log.d("AssetRepo", "[BIST] Phase 1 START: Top 40 using v8 (Immediate)")
                        kotlinx.coroutines.coroutineScope {
                            yahooSymbols.take(40).map { sym ->
                                async {
                                    try {
                                        val resp = yahooFinanceApi.getChartData(sym)
                                        val m = resp.chart.result?.firstOrNull()?.meta
                                        if (m != null) {
                                            val current = m.regularMarketPrice
                                            val prev = m.previousClose
                                            val change = if (prev > BigDecimal.ZERO) {
                                                (current - prev).divide(prev, 4, RoundingMode.HALF_UP).multiply(BigDecimal("100"))
                                            } else BigDecimal.ZERO

                                            val existing = marketAssetDao.getMarketAssetBySymbolAndTypeOnce(sym, type)
                                            marketAssets.add(cleanMarketAssetNaming(MarketAsset(
                                                symbol = sym,
                                                name = sym.replace(".IS", ""),
                                                fullName = m.longName ?: m.shortName,
                                                currentPrice = current.setScale(2, RoundingMode.HALF_UP),
                                                dailyChangePercentage = change.setScale(2, RoundingMode.HALF_UP), 
                                                assetType = type, 
                                                currency = "TRY",
                                                isFavorite = existing?.isFavorite ?: false
                                            ), type))
                                        }
                                    } catch (e: Exception) { 
                                        Log.e("AssetRepo", "[BIST Phase 1] Error for $sym: ${e.message}")
                                    }
                                    Unit
                                }
                            }.awaitAll()
                        }
                        
                        Log.d("AssetRepo", "[BIST] Phase 1 DONE. Assets count: ${marketAssets.size}")
                        if (marketAssets.isNotEmpty()) {
                            // Don't delete anymore, just upsert.
                            marketAssetDao.insertMarketAssets(marketAssets)
                        }

                        val remaining = if (yahooSymbols.size > 40) yahooSymbols.drop(40) else emptyList()
                        if (remaining.isNotEmpty()) {
                            Log.d("AssetRepo", "[BIST] Phase 2 (Background) START for ${remaining.size} symbols using v8 Throttled")
                            // background incremental load with throttling
                            kotlinx.coroutines.MainScope().launch {
                                val semaphore = kotlinx.coroutines.sync.Semaphore(1)
                                remaining.forEach { sym ->
                                    kotlinx.coroutines.delay(300) // strict delay
                                    semaphore.withPermit {
                                        try {
                                            var targetSym = sym
                                            var resp = try {
                                                yahooFinanceApi.getChartData(targetSym)
                                            } catch (e: retrofit2.HttpException) {
                                                if (e.code() == 404 && targetSym.endsWith(".IS")) {
                                                    // Fallback check: try without .IS suffix
                                                    val fallbackSym = targetSym.replace(".IS", "")
                                                    yahooFinanceApi.getChartData(fallbackSym)
                                                } else throw e
                                            }

                                            val m = resp.chart.result?.firstOrNull()?.meta
                                            if (m != null) {
                                                val current = m.regularMarketPrice
                                                val prev = m.previousClose
                                                val change = if (prev > BigDecimal.ZERO) {
                                                    (current - prev).divide(prev, 4, RoundingMode.HALF_UP).multiply(BigDecimal("100"))
                                                } else BigDecimal.ZERO

                                                val existing = marketAssetDao.getMarketAssetBySymbolAndTypeOnce(sym, type)
                                                val asset = cleanMarketAssetNaming(MarketAsset(
                                                    symbol = sym,
                                                    name = sym.replace(".IS", ""),
                                                    fullName = m.longName ?: m.shortName,
                                                    currentPrice = current.setScale(2, RoundingMode.HALF_UP),
                                                    dailyChangePercentage = change.setScale(2, RoundingMode.HALF_UP),
                                                    assetType = type,
                                                    currency = "TRY",
                                                    isFavorite = existing?.isFavorite ?: false
                                                ), type)
                                                marketAssetDao.insertMarketAssets(listOf(asset))
                                                // Log.v("AssetRepo", "[BIST Background] Added: $sym")
                                            }
                                        } catch (e: Exception) { 
                                            Log.e("AssetRepo", "[BIST Phase 2] Error for $sym: ${e.message}")
                                        }
                                    }
                                }
                                Log.d("AssetRepo", "[BIST] Phase 2 background loading finished")
                            }
                        }
                    } else {
                        // Diğerleri için klasik paralel fetch
                        kotlinx.coroutines.coroutineScope {
                            yahooSymbols.map { symbol ->
                                async {
                                    try {
                                        val response = yahooFinanceApi.getChartData(symbol)
                                        val result = response.chart.result?.firstOrNull()?.meta
                                        if (result != null) {
                                            val existing = marketAssetDao.getMarketAssetBySymbolAndTypeOnce(symbol, type)
                                            marketAssets.add(cleanMarketAssetNaming(MarketAsset(
                                                symbol = symbol,
                                                name = symbol,
                                                fullName = result.longName ?: result.shortName,
                                                currentPrice = result.regularMarketPrice.setScale(2, RoundingMode.HALF_UP),
                                                dailyChangePercentage = BigDecimal.ZERO,
                                                assetType = type,
                                                currency = "TRY",
                                                isFavorite = existing?.isFavorite ?: false
                                            ), type))
                                        }
                                    } catch (e: Exception) { }
                                }
                            }.awaitAll()
                        }
                    }

                    if (type == AssetType.EMTIA && symbols.contains("GRAM_ALTIN")) {
                        val onsAsset = marketAssets.find { it.symbol == "GC=F" }
                        val usdTryAsset = marketAssets.find { it.symbol == "USDTRY=X" }
                        if (onsAsset != null && usdTryAsset != null) {
                            val gramPrice = onsAsset.currentPrice.divide(BigDecimal("31.1035"), 8, RoundingMode.HALF_UP).multiply(usdTryAsset.currentPrice)
                            marketAssets.add(MarketAsset(
                                symbol = "GRAM_ALTIN",
                                name = "Gram Altın",
                                fullName = "Gram Altın",
                                currentPrice = gramPrice.setScale(2, RoundingMode.HALF_UP),
                                dailyChangePercentage = onsAsset.dailyChangePercentage,
                                assetType = AssetType.EMTIA,
                                currency = "TRY"
                            ))
                        }
                    }
                    if (type == AssetType.EMTIA) {
                        marketAssets.removeAll { it.symbol == "USDTRY=X" }
                    }
                    marketAssets
                }
            }
            
            if (assets.isNotEmpty()) {
                if (type != AssetType.BIST) {
                    // Try to avoid full delete if possible, but for NAKIT and FON it's small enough.
                    // However, we want to PRESERVE favorites, so we use REPLACE-based insert
                    // and we already merged the isFavorite flag above.
                    marketAssetDao.insertMarketAssets(assets)
                }
                Log.d("AssetRepo", "[$type] Final update DONE with ${assets.size} assets")
            } else if (assets.isEmpty()) {
                Log.e("AssetRepo", "[$type] Fetched empty list, not updating DB to prevent data loss.")
            }
            emit(Resource.Success(Unit))
        } catch (e: Exception) {
            Log.e("AssetRepo", "refreshMarketAssets Error: ${e.message}")
            emit(Resource.Error(e.message ?: "Piyasa verileri güncellenemedi"))
        }
    }

    /**
     * Canlı arama yapar.
     */
    suspend fun searchAssets(query: String, type: AssetType): List<MarketAsset> {

        Log.d("AssetRepo", "searchAssets: query=$query, type=$type")
        if (query.isBlank()) return emptyList()

        return try {
            val results = when (type) {
                AssetType.KRIPTO -> {
                    val allTickers = binanceApi.getAllTickers()
                    allTickers.filter { it.symbol.contains(query, ignoreCase = true) }
                        .take(20)
                        .map {
                            MarketAsset(
                                symbol = it.symbol,
                                name = it.symbol.replace("USDT", ""),
                                fullName = it.symbol.replace("USDT", ""),
                                currentPrice = BigDecimal(it.lastPrice),
                                dailyChangePercentage = BigDecimal(it.priceChangePercent).setScale(2, RoundingMode.HALF_UP),
                                assetType = AssetType.KRIPTO
                            )
                        }
                }
                else -> {
                    // Yahoo search for BIST, DOVIZ, EMTIA
                    Log.d("AssetRepo", "Searching Yahoo for: $query")
                    val response = yahooFinanceApi.search(query)
                    val symbols = response.quotes?.map { it.symbol } ?: emptyList()
                    Log.d("AssetRepo", "Yahoo search results: $symbols")
                    
                    if (symbols.isEmpty()) return emptyList()
                    
                    // Arama sonuçları için fiyatları toplu çek
                    try {
                        val quotes = yahooFinanceApi.getQuotes(symbols.joinToString(","))
                        quotes.quoteResponse.result?.map { quote ->
                            MarketAsset(
                                symbol = quote.symbol,
                                name = quote.shortName ?: quote.longName ?: quote.symbol,
                                fullName = quote.longName ?: quote.shortName ?: quote.symbol,
                                currentPrice = (quote.regularMarketPrice ?: BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP),
                                dailyChangePercentage = (quote.regularMarketChangePercent ?: BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP),
                                assetType = type,
                                currency = quote.currency ?: "USD"
                            )
                        } ?: emptyList()


                    } catch (e: Exception) {
                        Log.e("AssetRepo", "Bulk quote fetch failed, trying individual fallback: ${e.message}")
                        emptyList()
                    }
                }
            }
            
            // İsimleri ve sembolleri temizle
            results.map { cleanMarketAssetNaming(it, type) }
        } catch (e: Exception) {
            Log.e("AssetRepo", "searchAssets Major Error: ${e.message}", e)
            emptyList()
        }
    }

    /**
     * Tek seferlik piyasa verilerini DB'den çeker.
     */
    suspend fun getMarketAssetsOnce(type: AssetType): List<MarketAsset> {
        return marketAssetDao.getMarketAssetsByTypeOnce(type)
    }


    suspend fun upsertAsset(asset: Asset) {
        val existingAsset = assetDao.getAssetBySymbolAndPortfolioId(asset.symbol, asset.portfolioId)
        if (existingAsset != null) {
            // Ortalama maliyet hesabı
            val totalAmount = existingAsset.amount + asset.amount
            val totalCost = (existingAsset.amount * existingAsset.averageBuyPrice) + (asset.amount * asset.averageBuyPrice)
            val newAveragePrice = if (totalAmount > BigDecimal.ZERO) {
                totalCost.divide(totalAmount, 8, RoundingMode.HALF_UP)
            } else BigDecimal.ZERO

            val updatedAsset = existingAsset.copy(
                amount = totalAmount,
                averageBuyPrice = newAveragePrice,
                currentPrice = asset.currentPrice,
                dailyChangePercentage = asset.dailyChangePercentage,
                currency = asset.currency
            )
            assetDao.updateAsset(updatedAsset)
        } else {
            assetDao.insertAsset(asset)
        }
    }

    suspend fun getAssetHistory(
        symbol: String,
        range: String = "1d",
        interval: String = "1m"
    ): List<Pair<Long, Double>> {
        if (symbol == "TRY" || symbol == "TL") {
            val now = System.currentTimeMillis()
            val start = now - (24 * 60 * 60 * 1000)
            return listOf(start to 1.0, now to 1.0)
        }
        return try {

            var targetSymbol = symbol
            // BIST sembolleri için suffix kontrolü (Madde 4)
            if (!symbol.contains(".") && !symbol.contains("=X") && !symbol.contains("-USD")) {
                // Eğer büyük harf ve 3-6 karakter arasıysa muhtemelen BIST hissestidir
                if (symbol.all { it.isUpperCase() || it.isDigit() }) {
                    targetSymbol = "$symbol.IS"
                }
            }

            val response = yahooFinanceApi.getChartData(targetSymbol, range, interval)
            val result = response.chart.result?.firstOrNull()
            val timestamps = result?.timestamp ?: emptyList()
            val closePrices = result?.indicators?.quote?.firstOrNull()?.close ?: emptyList()
            
            timestamps.zip(closePrices).mapNotNull { (ts, price) ->
                if (price != null) ts * 1000 to price.toDouble() else null
            }

        } catch (e: Exception) {
            Log.e("AssetRepo", "History fetch failed for $symbol: ${e.message}")
            emptyList()
        }
    }

    suspend fun addAsset(asset: Asset) {
        assetDao.insertAsset(asset)
    }

    suspend fun getAssetBySymbolAndPortfolioId(symbol: String, portfolioId: Long): Asset? {
        return assetDao.getAssetBySymbolAndPortfolioId(symbol, portfolioId)
    }

    suspend fun deleteAsset(asset: Asset) {
        assetDao.deleteAsset(asset)
    }

    suspend fun toggleFavorite(symbol: String, type: AssetType, isFavorite: Boolean) {
        marketAssetDao.updateFavorite(symbol, type, isFavorite)
    }

    /**
     * Portföy için tarihsel bir veri noktası kaydeder.
     */
    suspend fun recordPortfolioSnapshot(portfolioId: Long, totalValue: BigDecimal, currency: String) {
        val now = System.currentTimeMillis()
        portfolioHistoryDao.insert(PortfolioHistory(
            portfolioId = portfolioId,
            date = now,
            totalValue = totalValue,
            currency = currency
        ))
    }

    /**
     * Portföyün yerel olarak kaydedilmiş geçmişini döner.
     */
    fun getPortfolioHistory(portfolioId: Long): Flow<List<PortfolioHistory>> {
        return portfolioHistoryDao.getAllHistory(portfolioId)
    }

    /**
     * Geçmiş verisi olmayan durumlar için Yahoo verileriyle portföy geçmişini rekonstrukte eder.
     */
    suspend fun reconstructPortfolioHistory(portfolioId: Long, range: String): List<PortfolioHistory> = coroutineScope {
        val assets = assetDao.getAssetsByPortfolioId(portfolioId).first()
        if (assets.isEmpty()) return@coroutineScope emptyList()

        val interval = when(range) {
            "7d" -> "1h"
            "1mo" -> "1d"
            else -> "1d"
        }

        // Tüm varlıkların geçmiş fiyatlarını çek
        val histories = assets.map { asset ->
            async {
                asset to getAssetHistory(asset.symbol, range, interval)
            }
        }.awaitAll()

        // USD ve EUR kurlarını çek (Kuru normalize etmek için)
        val usdHistory = getAssetHistory("TRY=X", range, interval)
        val eurHistory = getAssetHistory("EURTRY=X", range, interval)

        // Ortak bir zaman çizelgesi (Timestamps) oluştur (En çok veri noktası olanı baz al)
        val allTimestamps = histories.flatMap { it.second.map { p -> p.first } }.distinct().sorted()

        allTimestamps.map { ts ->
            var totalValueBase = BigDecimal.ZERO
            
            histories.forEach { (asset, history) ->
                val priceAtTs = history.find { it.first == ts }?.second 
                    ?: history.findLast { it.first <= ts }?.second // Veri yoksa bir önceki değeri al
                    ?: 0.0
                
                val rateAtTs = when(asset.currency) {
                    "USD" -> usdHistory.find { it.first == ts }?.second ?: usdHistory.findLast { it.first <= ts }?.second ?: 32.5
                    "EUR" -> eurHistory.find { it.first == ts }?.second ?: eurHistory.findLast { it.first <= ts }?.second ?: 35.2
                    else -> 1.0
                }
                
                val value = asset.amount.multiply(BigDecimal(priceAtTs.toString())).multiply(BigDecimal(rateAtTs.toString()))
                totalValueBase = totalValueBase.add(value)
            }

            PortfolioHistory(
                portfolioId = portfolioId,
                date = ts,
                totalValue = totalValueBase,
                currency = "TRY"
            )
        }
    }

    private fun cleanMarketAssetNaming(asset: MarketAsset, type: AssetType): MarketAsset {
        var name = asset.name
        var fullName = asset.fullName ?: asset.name
        var symbol = asset.symbol
        val cleanSymbol = symbol.uppercase()

        when {
            type == AssetType.BIST -> {
                name = name.replace(".IS", "").trim()
            }
            type == AssetType.DOVIZ || type == AssetType.NAKIT || (type == AssetType.EMTIA && (cleanSymbol == "TRY=X" || cleanSymbol == "USDTRY=X")) -> {
                // Set clean full name first
                val localizedFullName = when {
                    cleanSymbol.startsWith("USDTRY") || cleanSymbol == "TRY=X" -> "Amerikan Doları"
                    cleanSymbol.startsWith("EURTRY") -> "Euro"
                    cleanSymbol.startsWith("GBPTRY") -> "İngiliz Sterlini"
                    cleanSymbol.startsWith("CHFTRY") -> "İsviçre Frangı"
                    cleanSymbol.startsWith("JPYTRY") -> "Japon Yeni"
                    cleanSymbol.startsWith("AUDTRY") -> "Avustralya Doları"
                    cleanSymbol.startsWith("CADTRY") -> "Kanada Doları"
                    else -> fullName
                }
                fullName = localizedFullName

                name = when {
                    cleanSymbol.startsWith("USDTRY") || cleanSymbol == "TRY=X" -> if (type == AssetType.NAKIT) "Amerikan Doları" else "USD/TRY"
                    cleanSymbol.startsWith("EURTRY") -> if (type == AssetType.NAKIT) "Euro" else "EUR/TRY"
                    cleanSymbol.startsWith("GBPTRY") -> if (type == AssetType.NAKIT) "İngiliz Sterlini" else "GBP/TRY"
                    cleanSymbol.startsWith("CHFTRY") -> if (type == AssetType.NAKIT) "İsviçre Frangı" else "CHF/TRY"
                    cleanSymbol.startsWith("JPYTRY") -> if (type == AssetType.NAKIT) "Japon Yeni" else "JPY/TRY"
                    cleanSymbol.startsWith("AUDTRY") -> if (type == AssetType.NAKIT) "Avustralya Doları" else "AUD/TRY"
                    cleanSymbol.startsWith("CADTRY") -> if (type == AssetType.NAKIT) "Kanada Doları" else "CAD/TRY"
                    else -> name.replace("=X", "").replace("TRY", "/TRY").trim()
                }
                
                if (type == AssetType.NAKIT) {
                    symbol = when {
                        cleanSymbol.startsWith("USDTRY") || cleanSymbol == "TRY=X" -> "USD"
                        cleanSymbol.startsWith("EURTRY") -> "EUR"
                        cleanSymbol.startsWith("GBPTRY") -> "GBP"
                        cleanSymbol.startsWith("CHFTRY") -> "CHF"
                        cleanSymbol.startsWith("JPYTRY") -> "JPY"
                        else -> symbol.replace("TRY=X", "").replace("TRY", "")
                    }
                }
            }
            type == AssetType.EMTIA -> {
                name = when {
                    cleanSymbol.startsWith("GC=F") || cleanSymbol == "GOLD" -> "Altın (Ons)"
                    cleanSymbol.startsWith("SI=F") || cleanSymbol == "SILVER" -> "Gümüş"
                    cleanSymbol.startsWith("PL=F") -> "Platin"
                    cleanSymbol.startsWith("PA=F") -> "Paladyum"
                    cleanSymbol.startsWith("HG=F") -> "Bakır"
                    cleanSymbol == "GRAM_ALTIN" -> "Gram Altın"
                    else -> name
                }
                fullName = name // Remove dates/technical info
            }
        }

        return asset.copy(name = name, fullName = fullName, symbol = symbol)
    }

    private fun cleanAssetNaming(asset: Asset, type: AssetType): Asset {
        var name = asset.name
        var symbol = asset.symbol
        val cleanSymbol = symbol.uppercase()

        when {
            type == AssetType.BIST -> {
                name = name.replace(".IS", "").trim()
            }
            type == AssetType.DOVIZ || type == AssetType.NAKIT || (type == AssetType.EMTIA && (cleanSymbol == "TRY=X" || cleanSymbol == "USDTRY=X")) -> {
                name = when {
                    cleanSymbol.startsWith("USDTRY") || cleanSymbol == "TRY=X" -> if (type == AssetType.NAKIT) "Amerikan Doları" else "USD/TRY"
                    cleanSymbol.startsWith("EURTRY") -> if (type == AssetType.NAKIT) "Euro" else "EUR/TRY"
                    cleanSymbol.startsWith("GBPTRY") -> if (type == AssetType.NAKIT) "İngiliz Sterlini" else "GBP/TRY"
                    cleanSymbol.startsWith("CHFTRY") -> if (type == AssetType.NAKIT) "İsviçre Frangı" else "CHF/TRY"
                    cleanSymbol.startsWith("JPYTRY") -> if (type == AssetType.NAKIT) "Japon Yeni" else "JPY/TRY"
                    cleanSymbol.startsWith("AUDTRY") -> if (type == AssetType.NAKIT) "Avustralya Doları" else "AUD/TRY"
                    cleanSymbol.startsWith("CADTRY") -> if (type == AssetType.NAKIT) "Kanada Doları" else "CAD/TRY"
                    else -> name.replace("=X", "").replace("TRY", "/TRY").trim()
                }
                
                if (type == AssetType.NAKIT) {
                    symbol = when {
                        cleanSymbol.startsWith("USDTRY") || cleanSymbol == "TRY=X" -> "USD"
                        cleanSymbol.startsWith("EURTRY") -> "EUR"
                        cleanSymbol.startsWith("GBPTRY") -> "GBP"
                        cleanSymbol.startsWith("CHFTRY") -> "CHF"
                        cleanSymbol.startsWith("JPYTRY") -> "JPY"
                        else -> symbol.replace("USDTRY=X", "").replace("TRY=X", "").replace("TRY", "")
                    }
                }
            }
            type == AssetType.EMTIA -> {
                name = when {
                    cleanSymbol.startsWith("GC=F") || cleanSymbol == "GOLD" -> "Altın (Ons)"
                    cleanSymbol.startsWith("SI=F") || cleanSymbol == "SILVER" -> "Gümüş"
                    cleanSymbol.startsWith("PL=F") -> "Platin"
                    cleanSymbol.startsWith("PA=F") -> "Paladyum"
                    cleanSymbol.startsWith("HG=F") -> "Bakır"
                    cleanSymbol == "GRAM_ALTIN" -> "Gram Altın"
                    else -> name
                }
            }
        }

        return asset.copy(name = name, symbol = symbol)
    }

    suspend fun getPortfolioById(id: Long) = portfolioDao.getPortfolioById(id)

    companion object {
        private val ALL_BIST_SYMBOLS = listOf(
            "THYAO.IS", "TUPRS.IS", "ASELS.IS", "AKBNK.IS", "EREGL.IS", "BIMAS.IS", "KCHOL.IS", "SAHOL.IS", "SISE.IS", "GARAN.IS", "YKBNK.IS", "ISCTR.IS",
            "A1CAP.IS", "A1YEN.IS", "ACSEL.IS", "ADEL.IS", "ADESE.IS", "ADGYO.IS", "AEFES.IS", "AFYON.IS", "AGESA.IS", "AGHOL.IS", "AGROT.IS", "AGYO.IS", "AHGAZ.IS", "AHSGY.IS", "AKCNS.IS", "AKENR.IS", "AKFGY.IS", "AKFIS.IS", "AKFYE.IS", "AKGRT.IS", "AKHAN.IS", "AKMGY.IS", "AKSA.IS", "AKSEN.IS", "AKSUE.IS", "AKYHO.IS", "ALARK.IS", "ALBRK.IS", "ALCAR.IS", "ALCTL.IS", "ALDOC.IS", "ALGGY.IS", "ALGYO.IS", "ALKA.IS", "ALKIM.IS", "ALKLC.IS", "ALTNY.IS", "ALVES.IS", "ANELE.IS", "ANGEN.IS", "ANHYT.IS", "ANSGR.IS", "ARASE.IS", "ARCLK.IS", "ARDYZ.IS", "ARENA.IS", "ARFYE.IS", "ARMGD.IS", "ARSAN.IS", "ARTMS.IS", "ARZUM.IS", "ASGYO.IS", "ASTOR.IS", "ASUZU.IS", "ATAGY.IS", "ATAKP.IS", "ATATP.IS", "ATEKS.IS", "ATLAS.IS", "ATSYH.IS", "AVGYO.IS", "AVHOL.IS", "AVOD.IS", "AVPGY.IS", "AVTUR.IS", "AYCES.IS", "AYDEM.IS", "AYEN.IS", "AYES.IS", "AYGAZ.IS", "AZTEK.IS",
            "BAGFS.IS", "BAHKM.IS", "BAKAB.IS", "BALAT.IS", "BALSU.IS", "BNTAS.IS", "BANVT.IS", "BARMA.IS", "BASGZ.IS", "BASCM.IS", "BAYRK.IS", "BEGYO.IS", "BERA.IS", "BESLR.IS", "BESTE.IS", "BEYAZ.IS", "BFREN.IS", "BIENY.IS", "BIGCH.IS", "BIMAS.IS", "BINBN.IS", "BINHO.IS", "BIOEN.IS", "BIZIM.IS", "BJKAS.IS", "BLCYT.IS", "BLUME.IS", "BMSCH.IS", "BMSTL.IS", "BOBET.IS", "BORLS.IS", "BORSK.IS", "BOSSA.IS", "BRISA.IS", "BRKO.IS", "BRKSN.IS", "BRKVY.IS", "BRLSM.IS", "BRMEN.IS", "BRSAN.IS", "BRYAT.IS", "BSOKE.IS", "BTCIM.IS", "BUCIM.IS", "BURCE.IS", "BURVA.IS", "BVSAN.IS", "BYDNR.IS",
            "CANTE.IS", "CASA.IS", "CATES.IS", "CCOLA.IS", "CELHA.IS", "CEMAS.IS", "CEMTS.IS", "CEMZY.IS", "CEOEM.IS", "CGCAM.IS", "CIMSA.IS", "CLEBI.IS", "CMBTN.IS", "CMENT.IS", "CONSE.IS", "COSMO.IS", "CRDFA.IS", "CRFSA.IS", "CUSAN.IS", "CVKMD.IS", "CWENE.IS",
            "DAGHL.IS", "DAGI.IS", "DAPGM.IS", "DARDL.IS", "DCTTR.IS", "DENGE.IS", "DERHS.IS", "DERIM.IS", "DESA.IS", "DESPC.IS", "DEVA.IS", "DGGYO.IS", "DGNMO.IS", "DIRIT.IS", "DITAS.IS", "DMSAS.IS", "DNZGY.IS", "DOAS.IS", "DOBUR.IS", "DOCO.IS", "DOGUB.IS", "DOHOL.IS", "DOKTA.IS", "DURDO.IS", "DYOBY.IS", "DZGYO.IS",
            "EBEBK.IS", "ECILC.IS", "ECZYT.IS", "EDATA.IS", "EDIP.IS", "EFORV.IS", "EGEEN.IS", "EGGUB.IS", "EGPRO.IS", "EGSER.IS", "EKGYO.IS", "EKIZ.IS", "EKSUN.IS", "ELITE.IS", "EMKEL.IS", "ENERY.IS", "ENJSA.IS", "ENKAI.IS", "ENTRA.IS", "ERBOS.IS", "EREGL.IS", "ERSU.IS", "ESCOM.IS", "ESEN.IS", "ETILR.IS", "EUHOL.IS", "EUKYO.IS", "EUPWR.IS", "EUREN.IS", "EYGYO.IS",
            "FADE.IS", "FENER.IS", "FLAP.IS", "FONET.IS", "FORMT.IS", "FRIGO.IS", "FROTO.IS",
            "GARAN.IS", "GENTS.IS", "GEREL.IS", "GESAN.IS", "GIPTA.IS", "GLBMD.IS", "GLRYH.IS", "GLYHO.IS", "GOKNR.IS", "GOLTS.IS", "GOODY.IS", "GOZDE.IS", "GRNYO.IS", "GRSEL.IS", "GRTRK.IS", "GSDDE.IS", "GSDHO.IS", "GSRAY.IS", "GUBRF.IS", "GWIND.IS", "GZNMI.IS",
            "HALKB.IS", "HATEK.IS", "HATSN.IS", "HDFGS.IS", "HEDEF.IS", "HEKTS.IS", "HKTM.IS", "HLGYO.IS", "HTTBT.IS", "HUBVC.IS", "HUNER.IS", "HURGZ.IS",
            "ICBCT.IS", "ICUGS.IS", "IDGYO.IS", "IHEVA.IS", "IHLGM.IS", "IHGZT.IS", "IHLAS.IS", "IHYAY.IS", "IMASM.IS", "INDES.IS", "INFO.IS", "INGRM.IS", "INTEM.IS", "INVEO.IS", "IPEKE.IS", "ISATR.IS", "ISBTR.IS", "ISCTR.IS", "ISDMR.IS", "ISFIN.IS", "ISGSY.IS", "ISGYO.IS", "ISMEN.IS", "ISSEN.IS", "ISYAT.IS", "IZENR.IS", "IZFAS.IS", "IZMDC.IS",
            "JANTS.IS",
            "KAPLM.IS", "KAREL.IS", "KARSN.IS", "KARTN.IS", "KAYSE.IS", "KBTAS.IS", "KCAER.IS", "KCHOL.IS", "KENT.IS", "KERVN.IS", "KFEIN.IS", "KGYO.IS", "KIMMR.IS", "KLGYO.IS", "KLMSN.IS", "KLNMA.IS", "KLRHO.IS", "KLSYN.IS", "KMPUR.IS", "KNFRT.IS", "KOCAER.IS", "KONTR.IS", "KONYA.IS", "KORDS.IS", "KOZAA.IS", "KOZAL.IS", "KRDMA.IS", "KRDMB.IS", "KRDMD.IS", "KRONT.IS", "KRPLS.IS", "KRSTL.IS", "KRTEK.IS", "KRVGD.IS", "KSTUR.IS", "KTSKR.IS", "KUTPO.IS", "KUVVA.IS", "KUYAS.IS", "KZBGY.IS", "KZGYO.IS",
            "LIDER.IS", "LIDFA.IS", "LILAK.IS", "LINK.IS", "LKMNH.IS", "LMKDC.IS", "LOGO.IS", "LUKSK.IS",
            "MAALT.IS", "MACKO.IS", "MAGEN.IS", "MAKIM.IS", "MAKTK.IS", "MANAS.IS", "MARKA.IS", "MARTI.IS", "MAVI.IS", "MEDTR.IS", "MEGAP.IS", "MEGMT.IS", "MEPET.IS", "MERCN.IS", "MERIT.IS", "METRO.IS", "METUR.IS", "MHRGY.IS", "MIATK.IS", "MIPAZ.IS", "MMCAS.IS", "MNDRS.IS", "MNDTR.IS", "MOBTL.IS", "MOGAN.IS", "MPARK.IS", "MRGYO.IS", "MRSHL.IS", "MSGYO.IS", "MTRKS.IS", "MTRYO.IS", "MZHLD.IS",
            "NETAS.IS", "NIBAS.IS", "NTGAZ.IS", "NTHOL.IS", "NUGYO.IS", "NUHCM.IS",
            "OBAMS.IS", "OBASE.IS", "ODAS.IS", "ODINE.IS", "OFSYM.IS", "ONCSM.IS", "ORCAY.IS", "ORGE.IS", "ORMA.IS", "OSMEN.IS", "OSTIM.IS", "OTKAR.IS", "OYAKC.IS", "OYAYO.IS", "OYLUM.IS", "OYYAT.IS", "OZGYO.IS", "OZKGY.IS", "OZRDN.IS", "OZSUB.IS",
            "PAGYO.IS", "PAMEL.IS", "PAPIL.IS", "PARSN.IS", "PASEU.IS", "PATEK.IS", "PCILT.IS", "PEGYO.IS", "PEKGY.IS", "PENTA.IS", "PETKM.IS", "PETUN.IS", "PGSUS.IS", "PINSU.IS", "PKART.IS", "PKENT.IS", "PNLSN.IS", "PNSUT.IS", "POLHO.IS", "POLTK.IS", "PRKAB.IS", "PRKME.IS", "PRZMA.IS", "PSGYO.IS",
            "QUAGR.IS",
            "RALYH.IS", "RAYFA.IS", "RAYSG.IS", "REEDR.IS", "RNPOL.IS", "RODRG.IS", "ROYAL.IS", "RTALB.IS", "RUBNS.IS", "RYGYO.IS", "RYSAS.IS",
            "SAFKR.IS", "SAHOL.IS", "SAMAT.IS", "SANEL.IS", "SANFM.IS", "SANKO.IS", "SARKY.IS", "SASA.IS", "SAYAS.IS", "SDTTR.IS", "SEKFK.IS", "SEKUR.IS", "SELEC.IS", "SELGD.IS", "SERVE.IS", "SEYKM.IS", "SILVR.IS", "SISE.IS", "SKBNK.IS", "SKTAS.IS", "SMCRT.IS", "SNGYO.IS", "SNKRN.IS", "SNPAM.IS", "SODSN.IS", "SOKM.IS", "SONME.IS", "SRVGY.IS", "SUMAS.IS", "SUNTK.IS", "SURGY.IS", "SUWEN.IS",
            "TABGD.IS", "TAPDI.IS", "TARKM.IS", "TATGD.IS", "TAVHL.IS", "TBTAS.IS", "TCPAL.IS", "TEKTU.IS", "TERA.IS", "TETMT.IS", "TEZOL.IS", "THYAO.IS", "MONDI.IS", "TKFEN.IS", "TKNSA.IS", "TMSN.IS", "TOASO.IS", "TRGYO.IS", "TRILC.IS", "TSKB.IS", "TSGYO.IS", "TUCLK.IS", "TUKAS.IS", "TUPRS.IS", "TUREX.IS", "TURSG.IS",
            "UFUK.IS", "ULAS.IS", "ULUFA.IS", "ULUSE.IS", "ULUUN.IS", "UMPAS.IS", "USAK.IS",
            "VAKBN.IS", "VAKFN.IS", "VAKKO.IS", "VANGD.IS", "VBTYZ.IS", "VERTU.IS", "VERUS.IS", "VESBE.IS", "VESTL.IS", "VKFYO.IS", "VKGYO.IS", "VKING.IS", "VRGYO.IS",
            "YAPRK.IS", "YAYLA.IS", "YEOTK.IS", "YESIL.IS", "YGGYO.IS", "YGYO.IS", "YKBNK.IS", "YKSLN.IS", "YONGA.IS", "YUNSA.IS",
            "ZEDUR.IS", "ZOREN.IS", "ZRGYO.IS"
        )
    }
}
