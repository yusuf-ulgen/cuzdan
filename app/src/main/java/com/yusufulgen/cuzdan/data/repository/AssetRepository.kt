package com.yusufulgen.cuzdan.data.repository

import com.yusufulgen.cuzdan.data.local.dao.AssetDao
import com.yusufulgen.cuzdan.data.local.dao.PortfolioDao
import com.yusufulgen.cuzdan.data.local.entity.Asset
import com.yusufulgen.cuzdan.data.local.entity.AssetType
import com.yusufulgen.cuzdan.data.remote.api.BinanceApi
import com.yusufulgen.cuzdan.data.remote.api.YahooFinanceApi
import com.yusufulgen.cuzdan.data.remote.api.TefasApi
import java.util.Locale
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

    /**
     * Yahoo Finance API'den BIST, Döviz ve Altın fiyatlarını günceller.
     */
    suspend fun refreshYahooPrices(): Flow<Resource<Unit>> = flow {
        emit(Resource.Loading())
        try {
            val otherAssets = getOtherAssets().first()
            if (otherAssets.isEmpty()) {
                val defaultOther = listOf(
                    Asset(symbol = "THYAO.IS", name = "Türk Hava Yolları", amount = BigDecimal.ZERO, averageBuyPrice = BigDecimal.ZERO, currentPrice = BigDecimal.ZERO, dailyChangePercentage = BigDecimal.ZERO, assetType = AssetType.BIST),
                    Asset(symbol = "TRY=X", name = "USD/TRY", amount = BigDecimal.ZERO, averageBuyPrice = BigDecimal.ZERO, currentPrice = BigDecimal.ZERO, dailyChangePercentage = BigDecimal.ZERO, assetType = AssetType.DOVIZ),
                    Asset(symbol = "GC=F", name = "Ons Altın", amount = BigDecimal.ZERO, averageBuyPrice = BigDecimal.ZERO, currentPrice = BigDecimal.ZERO, dailyChangePercentage = BigDecimal.ZERO, assetType = AssetType.EMTIA),
                    Asset(symbol = "GRAM_ALTIN", name = "Gram Altın", amount = BigDecimal.ZERO, averageBuyPrice = BigDecimal.ZERO, currentPrice = BigDecimal.ZERO, dailyChangePercentage = BigDecimal.ZERO, assetType = AssetType.EMTIA)
                )
                defaultOther.forEach { assetDao.insertAsset(it) }
            }

            val currentOtherAssets = getOtherAssets().first()
            val symbolsToFetch = currentOtherAssets.map { it.symbol }.filter { it != "GRAM_ALTIN" }
            
            if (symbolsToFetch.isNotEmpty()) {
                val response = yahooFinanceApi.getQuotes(symbolsToFetch.joinToString(","))
                val quotes = response.quoteResponse.result ?: emptyList()

                var onsPrice: BigDecimal? = null
                var usdTryPrice: BigDecimal? = null
                var onsChange: BigDecimal = BigDecimal.ZERO
                var usdTryChange: BigDecimal = BigDecimal.ZERO

                currentOtherAssets.forEach { asset ->
                    if (asset.symbol == "GRAM_ALTIN") return@forEach
                    val quote = quotes.find { it.symbol == asset.symbol }
                    if (quote != null) {
                        val price = quote.regularMarketPrice ?: BigDecimal.ZERO
                        val changePerc = quote.regularMarketChangePercent ?: BigDecimal.ZERO
                        val currency = quote.currency ?: "USD"

                        if (asset.symbol == "GC=F") { onsPrice = price; onsChange = changePerc }
                        if (asset.symbol == "TRY=X") { usdTryPrice = price; usdTryChange = changePerc }

                        assetDao.updateAsset(asset.copy(
                            currentPrice = price, 
                            dailyChangePercentage = changePerc, 
                            currency = currency
                        ))
                    }
                }

                if (onsPrice != null && usdTryPrice != null) {
                    val gramGoldPrice = onsPrice!!.divide(BigDecimal("31.1035"), 8, RoundingMode.HALF_UP).multiply(usdTryPrice!!)
                    val gramGoldChange = onsChange.add(usdTryChange)
                    assetDao.getAssetBySymbol("GRAM_ALTIN")?.let { asset ->
                        assetDao.updateAsset(asset.copy(currentPrice = gramGoldPrice, dailyChangePercentage = gramGoldChange))
                    }
                }
            }
            emit(Resource.Success(Unit))
        } catch (e: Exception) {
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

            fundAssets.forEach { asset ->
                var price = BigDecimal.ZERO
                var prevPrice = BigDecimal.ZERO
                var foundPriceDate: String? = null

                // Son 30 günü tara – en son çalışan günü bul
                outer@ for (dayOffset in 0..30) {
                    val cal = java.util.Calendar.getInstance()
                    cal.add(java.util.Calendar.DATE, -dayOffset)
                    val dateStr = sdf.format(cal.time)

                    for (ft in fundTypes) {
                        try {
                            val history = tefasApi.getFundHistory(
                                fundType = ft,
                                fundCode = asset.symbol.uppercase(),
                                startDate = dateStr,
                                endDate = dateStr
                            )
                            val entry = history.firstOrNull()
                            if (entry != null) {
                                val parsed = parseTefasPrice(entry.price)
                                if (parsed > BigDecimal.ZERO) {
                                    price = parsed
                                    foundPriceDate = dateStr
                                    break@outer
                                }
                            }
                        } catch (e: Exception) {
                            Log.w("TEFAS", "Fund [${asset.symbol}] type=$ft date=$dateStr: ${e.message}")
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
                                val prevHistory = tefasApi.getFundHistory(
                                    fundType = ft,
                                    fundCode = asset.symbol.uppercase(),
                                    startDate = prevDateStr,
                                    endDate = prevDateStr
                                )
                                val prevEntry = prevHistory.firstOrNull()
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
                    Log.d("TEFAS", "Owned fund updated ${asset.symbol}: price=$price change=${dailyChange}%")
                } else {
                    Log.w("TEFAS", "Owned fund price stayed 0: ${asset.symbol}. Keeping existing price=${asset.currentPrice.setScale(4, RoundingMode.HALF_UP)}")
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
                    val fundAssets = coroutineScope {
                        symbols.map { symbol ->
                            async {
                                var price = BigDecimal.ZERO
                                var fundName = "$symbol Fonu"
                                val sdf = java.text.SimpleDateFormat("dd.MM.yyyy", Locale("tr", "TR"))

                                // Bugünden geriye doğru 30 gün tara
                                outer@ for (dayOffset in 0..30) {
                                    val cal = java.util.Calendar.getInstance()
                                    cal.add(java.util.Calendar.DATE, -dayOffset)
                                    val dateStr = sdf.format(cal.time)
                                    for (ft in fundTypeList) {
                                        try {
                                            val history = tefasApi.getFundHistory(
                                                fundType = ft,
                                                fundCode = symbol,
                                                startDate = dateStr,
                                                endDate = dateStr
                                            )
                                            history.firstOrNull()?.let { entry ->
                                                fundName = entry.fundName ?: fundName
                                                val parsed = parseTefasPrice(entry.price)
                                                if (parsed > BigDecimal.ZERO) {
                                                    price = parsed
                                                    return@async Triple(symbol, fundName, price)
                                                }
                                            }
                                        } catch (e: Exception) {
                                            Log.w("TEFAS", "Market fund $symbol type=$ft date=$dateStr: ${e.message}")
                                        }
                                    }
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
                                    val result = yahooFinanceApi.getChartData("${code}TRY=X").chart.result?.firstOrNull()?.meta
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
                            "GC=F", "XAUUSD=X", "SI=F", "XAGUSD=X", "PL=F", "PA=F", "HG=F", // Ana Metaller
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
                    
                    // Manually add Turkish Lira to DOVIZ category too
                    val exist = marketAssetDao.getMarketAssetBySymbolAndTypeOnce("TRY", AssetType.DOVIZ)
                    if (exist == null) {
                        marketAssets.add(MarketAsset("TRY", "Türk Lirası", "Türk Lirası", BigDecimal.ONE, BigDecimal.ZERO, AssetType.DOVIZ, "TRY", false))
                    } else {
                        marketAssets.add(exist.copy(currentPrice = BigDecimal.ONE, dailyChangePercentage = BigDecimal.ZERO))
                    }
                    
                    // IMPORTANT: We switch ALL Doviz/Emtia to getChartData pattern because v7/quote is returning 401 Unauthorized
                    val results = coroutineScope {
                        symbols.mapIndexed { index, sym ->
                            async {
                                try {
                                    // Add small staggered delay to avoid Yahoo rate limits
                                    delay(index * 150L) 
                                    val result = yahooFinanceApi.getChartData(sym).chart.result?.firstOrNull()?.meta
                                    if (result != null) {
                                        val current = result.regularMarketPrice ?: BigDecimal.ZERO
                                        val prev = result.previousClose ?: BigDecimal.ZERO
                                        val change = if (prev > BigDecimal.ZERO) (current - prev).divide(prev, 10, RoundingMode.HALF_UP).multiply(BigDecimal("100")) else BigDecimal.ZERO
                                        val exist = marketAssetDao.getMarketAssetBySymbolAndTypeOnce(sym, type)
                                        cleanMarketAssetNaming(MarketAsset(sym, sym, result.shortName ?: sym, current.setScale(4, RoundingMode.HALF_UP), change.setScale(2, RoundingMode.HALF_UP), type, "TRY", exist?.isFavorite ?: false), type)
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
                            val res = yahooFinanceApi.getChartData("USDTRY=X").chart.result?.firstOrNull()?.meta
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
                marketAssetDao.insertMarketAssets(marketAssets)
                android.util.Log.d("CuzdanDebug", "Successfully saved to Database")
            }
        } catch (e: Exception) { android.util.Log.e("CuzdanDebug", "Internal error: ${e.message}") }
    }

    suspend fun searchAssets(query: String, type: AssetType): List<MarketAsset> {
        if (query.isBlank()) return emptyList()
        try {
            // STEP 1: Search local database first (especially important for BIST/FON/DOVIZ/EMTIA)
            val localResults = marketAssetDao.getMarketAssetsByTypeOnce(type).filter { 
                it.symbol.contains(query, ignoreCase = true) || 
                it.name.contains(query, ignoreCase = true) || 
                (it.fullName?.contains(query, ignoreCase = true) == true)
            }
            
            // If we have local results for non-crypto types, return them (they are more standard)
            if (type != AssetType.KRIPTO && localResults.isNotEmpty()) {
                return localResults
            }

            // STEP 2: Remote search if local yields nothing or for KRIPTO (to find new tokens)
            val remoteResults = when (type) {
                AssetType.KRIPTO -> {
                    binanceApi.getAllTickers()
                        .filter { it.symbol.endsWith("USDT") && it.symbol.contains(query, ignoreCase = true) }
                        .take(20).map { MarketAsset(it.symbol, it.symbol.replace("USDT", ""), it.symbol.replace("USDT", ""), BigDecimal(it.lastPrice), BigDecimal(it.priceChangePercent).setScale(2, RoundingMode.HALF_UP), AssetType.KRIPTO, "USD") }
                }
                AssetType.BIST -> {
                    // For BIST, searching Yahoo without .IS doesn't work well, so we use our local BIST list
                    BistSymbols.all.filter { it.contains(query, ignoreCase = true) }
                        .take(20).mapNotNull { sym -> fetchYahooMarketAsset(sym, AssetType.BIST) }
                }
                else -> {
                    val response = yahooFinanceApi.search(query)
                    val symbols = response.quotes?.map { it.symbol } ?: emptyList()
                    if (symbols.isEmpty()) emptyList()
                    else yahooFinanceApi.getQuotes(symbols.joinToString(",")).quoteResponse.result?.map { quote ->
                        MarketAsset(quote.symbol, quote.shortName ?: quote.longName ?: quote.symbol, quote.longName ?: quote.shortName ?: quote.symbol, (quote.regularMarketPrice ?: BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP), (quote.regularMarketChangePercent ?: BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP), type, quote.currency ?: "USD")
                    } ?: emptyList()
                }
            }
            
            val totalResults = (localResults + remoteResults).distinctBy { it.symbol }.map { cleanMarketAssetNaming(it, type) }
            return totalResults
        } catch (e: Exception) { return emptyList() }
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
        var name = asset.name; var fullName = asset.fullName ?: asset.name; var symbol = asset.symbol; val cleanSymbol = symbol.uppercase()
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
                val localized = when {
                    cleanSymbol.contains("USDTRY") || cleanSymbol == "USD" -> "Amerikan Doları"
                    cleanSymbol == "TRY=X" || cleanSymbol == "TRY"  -> "Türk Lirası"
                    cleanSymbol.contains("EURTRY") || cleanSymbol == "EUR" -> "Euro"
                    cleanSymbol.contains("GBPTRY") || cleanSymbol == "GBP" -> "İngiliz Sterlini"
                    cleanSymbol.contains("CHFTRY") || cleanSymbol == "CHF" -> "İsviçre Frangı"
                    cleanSymbol.contains("JPYTRY") || cleanSymbol == "JPY" -> "Japon Yeni"
                    cleanSymbol.contains("AUDTRY") || cleanSymbol == "AUD" -> "Avustralya Doları"
                    cleanSymbol.contains("CADTRY") || cleanSymbol == "CAD" -> "Kanada Doları"
                    cleanSymbol.contains("AEDTRY") || cleanSymbol == "AED" -> "BAE Dirhemi"
                    cleanSymbol.contains("SARTRY") || cleanSymbol == "SAR" -> "Suudi Arabistan Riyali"
                    cleanSymbol.contains("QARTRY") || cleanSymbol == "QAR" -> "Katar Riyali"
                    cleanSymbol.contains("RUBTRY") || cleanSymbol == "RUB" -> "Rus Rublesi"
                    cleanSymbol.contains("CNYTRY") || cleanSymbol == "CNY" -> "Çin Yuanı"
                    cleanSymbol.contains("AZNTRY") || cleanSymbol == "AZN" -> "Azerbaycan Manatı"
                    cleanSymbol.contains("SGDTRY") || cleanSymbol == "SGD" -> "Singapur Doları"
                    cleanSymbol.contains("NOKTRY") || cleanSymbol == "NOK" -> "Norveç Kronu"
                    cleanSymbol.contains("SEKTRY") || cleanSymbol == "SEK" -> "İsveç Kronu"
                    cleanSymbol.contains("DKKTRY") || cleanSymbol == "DKK" -> "Danimarka Kronu"
                    cleanSymbol.contains("NZDTRY") || cleanSymbol == "NZD" -> "Yeni Zelanda Doları"
                    cleanSymbol.contains("MXNTRY") || cleanSymbol == "MXN" -> "Meksika Pesosu"
                    cleanSymbol.contains("BRLTRY") || cleanSymbol == "BRL" -> "Brezilya Reali"
                    cleanSymbol.contains("INRTRY") || cleanSymbol == "INR" -> "Hint Rupisi"
                    cleanSymbol.contains("KRWTRY") || cleanSymbol == "KRW" -> "Güney Kore Wonu"
                    cleanSymbol.contains("HKDTRY") || cleanSymbol == "HKD" -> "Hong Kong Doları"
                    cleanSymbol.contains("PLNTRY") || cleanSymbol == "PLN" -> "Polonya Zlotisi"
                    cleanSymbol.contains("CZKTRY") || cleanSymbol == "CZK" -> "Çek Korunası"
                    cleanSymbol.contains("HUFTRY") || cleanSymbol == "HUF" -> "Macar Forinti"
                    cleanSymbol.contains("RONTRY") || cleanSymbol == "RON" -> "Rumen Leyi"
                    cleanSymbol.contains("ILSTRY") || cleanSymbol == "ILS" -> "İsrail Şekeli"
                    cleanSymbol.contains("KWDTRY") || cleanSymbol == "KWD" -> "Kuveyt Dinarı"
                    cleanSymbol.contains("OMRTRY") || cleanSymbol == "OMR" -> "Umman Riyali"
                    cleanSymbol.contains("BHDTRY") || cleanSymbol == "BHD" -> "Bahreyn Dinarı"
                    cleanSymbol.contains("THBTRY") || cleanSymbol == "THB" -> "Tayland Bahtı"
                    cleanSymbol.contains("MYRTRY") || cleanSymbol == "MYR" -> "Malezya Ringgiti"
                    cleanSymbol.contains("IDRTRY") || cleanSymbol == "IDR" -> "Endonezya Rupisi"
                    cleanSymbol.contains("PHPTRY") || cleanSymbol == "PHP" -> "Filipin Pesosu"
                    cleanSymbol.contains("PKRTRY") || cleanSymbol == "PKR" -> "Pakistan Rupisi"
                    cleanSymbol.contains("EGPTRY") || cleanSymbol == "EGP" -> "Mısır Lirası"
                    cleanSymbol.contains("ZARTRY") || cleanSymbol == "ZAR" -> "G. Afrika Randı"
                    cleanSymbol.contains("MADTRY") || cleanSymbol == "MAD" -> "Fas Dirhemi"
                    cleanSymbol.contains("GELTRY") || cleanSymbol == "GEL" -> "Gürcistan Larisi"
                    cleanSymbol.contains("UAHTRY") || cleanSymbol == "UAH" -> "Ukrayna Grivnası"
                    cleanSymbol.contains("BGNTRY") || cleanSymbol == "BGN" -> "Bulgar Levası"
                    cleanSymbol.contains("ISKTRY") || cleanSymbol == "ISK" -> "İzlanda Kronu"
                    cleanSymbol.contains("KAZTRY") || cleanSymbol == "KZT" -> "Kazakistan Tengesi"
                    cleanSymbol.contains("VNDDTRY") || cleanSymbol == "VND" -> "Vietnam Dongu"
                    cleanSymbol == "LBS=F" -> "Kereste"
                    cleanSymbol == "RB=F" -> "RBOB Benzin"
                    cleanSymbol == "HO=F" -> "Isınma Yakıtı"
                    cleanSymbol == "ALI=F" -> "Alüminyum"
                    cleanSymbol == "NI=F" -> "Nikel"
                    cleanSymbol == "ZN=F" -> "Çinko"
                    cleanSymbol == "PB=F" -> "Kurşun"
                    cleanSymbol == "SN=F" -> "Kalay"
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
                    cleanSymbol.contains("SAR") -> "SAR"
                    cleanSymbol.contains("QAR") -> "QAR"
                    cleanSymbol.contains("RUB") -> "RUB"
                    cleanSymbol.contains("CNY") -> "CNY"
                    cleanSymbol.contains("AZN") -> "AZN"
                    cleanSymbol.contains("SGD") -> "SGD"
                    cleanSymbol.contains("NOK") -> "NOK"
                    cleanSymbol.contains("SEK") -> "SEK"
                    cleanSymbol.contains("DKK") -> "DKK"
                    cleanSymbol.contains("NZD") -> "NZD"
                    cleanSymbol.contains("MXN") -> "MXN"
                    cleanSymbol.contains("BRL") -> "BRL"
                    cleanSymbol.contains("INR") -> "INR"
                    cleanSymbol.contains("KRW") -> "KRW"
                    cleanSymbol.contains("HKD") -> "HKD"
                    cleanSymbol.contains("PLN") -> "PLN"
                    cleanSymbol.contains("CZK") -> "CZK"
                    cleanSymbol.contains("HUF") -> "HUF"
                    cleanSymbol.contains("RON") -> "RON"
                    cleanSymbol.contains("ILS") -> "ILS"
                    cleanSymbol.contains("KWD") -> "KWD"
                    cleanSymbol.contains("OMR") -> "OMR"
                    cleanSymbol.contains("BHD") -> "BHD"
                    cleanSymbol.contains("THB") -> "THB"
                    cleanSymbol.contains("MYR") -> "MYR"
                    cleanSymbol.contains("IDR") -> "IDR"
                    cleanSymbol.contains("PHP") -> "PHP"
                    cleanSymbol.contains("PKR") -> "PKR"
                    cleanSymbol.contains("EGP") -> "EGP"
                    cleanSymbol.contains("ZAR") -> "ZAR"
                    cleanSymbol.contains("MAD") -> "MAD"
                    cleanSymbol.contains("GEL") -> "GEL"
                    cleanSymbol.contains("UAH") -> "UAH"
                    cleanSymbol.contains("BGN") -> "BGN"
                    cleanSymbol.contains("ISK") -> "ISK"
                    cleanSymbol.contains("KAZ") || cleanSymbol.contains("KZT") -> "KZT"
                    cleanSymbol.contains("VND") -> "VND"
                    else -> symbol
                }
            }
            type == AssetType.EMTIA -> {
                name = when { 
                    cleanSymbol.contains("GC=F") || cleanSymbol == "GOLD" || cleanSymbol == "XAUUSD=X" -> "Altın (Ons)"
                    cleanSymbol.contains("SI=F") || cleanSymbol == "SILVER" || cleanSymbol == "XAGUSD=X" -> "Gümüş"
                    cleanSymbol.contains("PL=F") -> "Platin"
                    cleanSymbol.contains("PA=F") -> "Paladyum"
                    cleanSymbol.contains("HG=F") -> "Bakır"
                    cleanSymbol.contains("GRAM_ALTIN") -> "Gram Altın"
                    cleanSymbol.contains("ALI=F") -> "Alüminyum"
                    cleanSymbol.contains("NI=F") -> "Nikel"
                    cleanSymbol.contains("ZN=F") -> "Çinko"
                    cleanSymbol.contains("PB=F") -> "Kurşun"
                    cleanSymbol.contains("SN=F") -> "Kalay"
                    cleanSymbol.contains("CL=F") -> "Ham Petrol"
                    cleanSymbol.contains("BZ=F") -> "Brent Petrol"
                    cleanSymbol.contains("NG=F") -> "Doğalgaz"
                    cleanSymbol.contains("KC=F") -> "Kahve"
                    cleanSymbol.contains("CC=F") -> "Kakao"
                    cleanSymbol.contains("CT=F") -> "Pamuk"
                    cleanSymbol.contains("SB=F") -> "Şeker"
                    cleanSymbol.contains("ZC=F") -> "Mısır"
                    cleanSymbol.contains("ZW=F") -> "Buğday"
                    cleanSymbol.contains("ZS=F") -> "Soya Fasulyesi"
                    cleanSymbol.contains("LBS=F") -> "Kereste"
                    else -> name 
                }
                fullName = name.replace(Regex("\\s+[A-Za-z]{3}\\s+\\d{2}$"), "").trim()
                name = fullName
                if (cleanSymbol.contains("GC=F")) symbol = "GOLD"
                if (cleanSymbol.contains("SI=F")) symbol = "SILVER"
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
                val allSymbols = BistSymbols.all
                val popularSymbols = BistSymbols.popular
                
                // 1. Önce popüler hisseleri hemen çek (İlk yükleme hissi için)
                val initialResults = coroutineScope {
                    popularSymbols.map { sym ->
                        async { fetchYahooMarketAsset(sym, AssetType.BIST) }
                    }.awaitAll().filterNotNull()
                }
                if (initialResults.isNotEmpty()) marketAssetDao.insertMarketAssets(initialResults)

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
                val prev = result.previousClose ?: BigDecimal.ZERO
                val change = if (prev > BigDecimal.ZERO) {
                    current.subtract(prev).divide(prev, 4, RoundingMode.HALF_UP).multiply(BigDecimal("100"))
                } else BigDecimal.ZERO
                val exist = marketAssetDao.getMarketAssetBySymbolAndTypeOnce(sym, type)
                cleanMarketAssetNaming(MarketAsset(
                    symbol = sym, 
                    name = sym, 
                    fullName = result.longName ?: result.shortName, 
                    currentPrice = current.setScale(2, RoundingMode.HALF_UP), 
                    dailyChangePercentage = change.setScale(2, RoundingMode.HALF_UP), 
                    assetType = type, 
                    currency = "TRY", 
                    isFavorite = exist?.isFavorite ?: false
                ), type)
            } else null
        } catch (e: Exception) { null }
    }
}
