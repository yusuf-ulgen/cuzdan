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
import com.example.cuzdan.data.remote.model.YahooFinanceResponse
import com.example.cuzdan.data.remote.model.YahooSearchResponse
import com.example.cuzdan.data.local.dao.MarketAssetDao
import com.example.cuzdan.data.local.entity.PortfolioHistory
import com.example.cuzdan.data.local.dao.PortfolioHistoryDao
import com.example.cuzdan.data.local.entity.PriceAlert
import com.example.cuzdan.data.local.dao.PriceAlertDao
import com.example.cuzdan.data.local.entity.PriceAlertCondition
import com.example.cuzdan.data.local.entity.MarketAsset
import com.example.cuzdan.util.Resource
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

            var onsPrice: BigDecimal? = null
            var usdTryPrice: BigDecimal? = null
            var onsChange: BigDecimal = BigDecimal.ZERO
            var usdTryChange: BigDecimal = BigDecimal.ZERO

            val currentOtherAssets = getOtherAssets().first()
            currentOtherAssets.forEach { asset ->
                if (asset.symbol == "GRAM_ALTIN") return@forEach
                try {
                    val response = yahooFinanceApi.getChartData(asset.symbol)
                    val result = response.chart.result?.firstOrNull()?.meta
                    val price = result?.regularMarketPrice ?: BigDecimal.ZERO
                    val prevClose = result?.previousClose ?: BigDecimal.ZERO
                    val changePerc = if (prevClose > BigDecimal.ZERO) {
                        price.subtract(prevClose).divide(prevClose, 4, RoundingMode.HALF_UP).multiply(BigDecimal("100"))
                    } else BigDecimal.ZERO
                    val currency = result?.currency ?: "USD"

                    if (asset.symbol == "GC=F") { onsPrice = price; onsChange = changePerc }
                    if (asset.symbol == "TRY=X") { usdTryPrice = price; usdTryChange = changePerc }

                    assetDao.updateAsset(asset.copy(currentPrice = price, dailyChangePercentage = changePerc, currency = currency))
                } catch (e: Exception) { e.printStackTrace() }
            }

            if (onsPrice != null && usdTryPrice != null) {
                val gramGoldPrice = onsPrice!!.divide(BigDecimal("31.1"), 8, RoundingMode.HALF_UP).multiply(usdTryPrice!!)
                val gramGoldChange = onsChange.add(usdTryChange)
                assetDao.getAssetBySymbol("GRAM_ALTIN")?.let { asset ->
                    assetDao.updateAsset(asset.copy(currentPrice = gramGoldPrice, dailyChangePercentage = gramGoldChange))
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
            val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
            fundAssets.forEach { asset ->
                val calendar = java.util.Calendar.getInstance()
                var price = BigDecimal.ZERO
                for (i in 0..5) {
                    try {
                        val dateStr = sdf.format(calendar.time)
                        val response = tefasApi.getFundPrices(TefasRequest(fundType = asset.symbol, date = dateStr))
                        val entry = response.firstOrNull()
                        if (entry != null) {
                            val parsedPrice = parseTefasPrice(entry.price)
                            if (parsedPrice > BigDecimal.ZERO) { price = parsedPrice; break }
                        }
                    } catch (e: Exception) { }
                    calendar.add(java.util.Calendar.DATE, -1)
                }
                if (price > BigDecimal.ZERO) {
                    assetDao.updateAsset(asset.copy(currentPrice = price, dailyChangePercentage = BigDecimal.ZERO, currency = "TRY"))
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
                    val symbols = listOf("TTE", "IJP", "MAC", "GSP", "AFT", "KOC", "IPV", "OPI", "RPD", "TAU", "YAY", "TI1", "GMR", "TE3", "HVS", "TDF", "IKL", "NJR", "BUY", "NNF", "BGP", "KZT", "ZPE", "OJT", "IDL", "KDV", "GPA", "RTG", "OTJ", "ZPF", "YZG", "HKH", "ZHB", "AFO", "GL1", "IVY", "YAS", "IHK", "EID", "ST1", "GAY", "DBH", "YHS", "ZPC", "AES", "IPJ", "GUH", "IEY", "YTD")
                    val fundAssets = coroutineScope {
                        symbols.map { symbol ->
                            async {
                                var price = BigDecimal.ZERO
                                var fundName = "$symbol Fonu"
                                val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
                                val calendar = java.util.Calendar.getInstance()
                                for (i in 0..5) {
                                    try {
                                        val response = tefasApi.getFundPrices(TefasRequest(fundType = symbol, date = sdf.format(calendar.time)))
                                        response.firstOrNull()?.let { entry ->
                                            fundName = entry.fundName ?: fundName
                                            val parsed = parseTefasPrice(entry.price)
                                            if (parsed > BigDecimal.ZERO) { price = parsed; return@async symbol to Triple(price, fundName, true) }
                                        }
                                    } catch (e: Exception) { }
                                    calendar.add(java.util.Calendar.DATE, -1)
                                }
                                symbol to Triple(price, fundName, false)
                            }
                        }.awaitAll()
                    }
                    fundAssets.forEach { (symbol, data) ->
                        // Even if TEFAS fails, keep the fund visible with last known/zero price.
                        val existing = marketAssetDao.getMarketAssetBySymbolAndTypeOnce(symbol, AssetType.FON)
                        val price = if (data.third) data.first else (existing?.currentPrice ?: BigDecimal.ZERO)
                        val name = if (data.second.isNotBlank()) data.second else (existing?.name ?: "$symbol Fonu")
                        marketAssets.add(
                            MarketAsset(
                                symbol = symbol,
                                name = name,
                                fullName = name,
                                currentPrice = price,
                                dailyChangePercentage = BigDecimal.ZERO,
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
                        // CRITICAL: Delete legacy AED symbol if it exists
                        marketAssetDao.deleteAed()
                    }
                    if (type == AssetType.BIST) {
                        refreshBistIncrementally()
                        return
                    }

                    val symbols = when(type) {
                        AssetType.DOVIZ -> listOf(
                            "USDTRY=X", "EURTRY=X", "GBPTRY=X", "CHFTRY=X", "JPYTRY=X", "AUDTRY=X", "CADTRY=X", 
                            "SARTRY=X", "QARTRY=X", "RUBTRY=X", "CNYTRY=X", "AZNTRY=X"
                        )
                        else -> listOf("GC=F", "SI=F", "PL=F", "PA=F", "HG=F", "GRAM_ALTIN", "USDTRY=X")
                    }
                    
                    val existing = marketAssetDao.getMarketAssetsByTypeOnce(type)
                    val lastUpdate = existing.firstOrNull()?.lastUpdated ?: 0L
                    if (System.currentTimeMillis() - lastUpdate < 30 * 60 * 1000 && existing.isNotEmpty()) return

                    val results = coroutineScope {
                        symbols.filter { it != "GRAM_ALTIN" }.map { sym ->
                            async {
                                try {
                                    val result = yahooFinanceApi.getChartData(sym).chart.result?.firstOrNull()?.meta
                                    if (result != null) {
                                        val current = result.regularMarketPrice
                                        val prev = result.previousClose
                                        val change = if (prev > BigDecimal.ZERO) (current - prev).divide(prev, 4, RoundingMode.HALF_UP).multiply(BigDecimal("100")) else BigDecimal.ZERO
                                        val exist = marketAssetDao.getMarketAssetBySymbolAndTypeOnce(sym, type)
                                        cleanMarketAssetNaming(MarketAsset(sym, sym, result.longName ?: result.shortName, current.setScale(2, RoundingMode.HALF_UP), change.setScale(2, RoundingMode.HALF_UP), type, "TRY", exist?.isFavorite ?: false), type)
                                    } else null
                                } catch (e: Exception) { null }
                            }
                        }.awaitAll().filterNotNull()
                    }
                    marketAssets.addAll(results)

                    if (type == AssetType.EMTIA && symbols.contains("GRAM_ALTIN")) {
                        val ons = marketAssets.find { it.symbol == "GC=F" }
                        val usd = marketAssets.find { it.symbol == "USDTRY=X" }
                        if (ons != null && usd != null) {
                            val gp = ons.currentPrice.divide(BigDecimal("31.1035"), 8, RoundingMode.HALF_UP).multiply(usd.currentPrice)
                            marketAssets.add(MarketAsset("GRAM_ALTIN", "Gram Altın", "Gram Altın", gp.setScale(2, RoundingMode.HALF_UP), ons.dailyChangePercentage, AssetType.EMTIA, "TRY"))
                        }
                        marketAssets.removeAll { it.symbol == "USDTRY=X" }
                    }
                }
            }
            if (marketAssets.isNotEmpty()) marketAssetDao.insertMarketAssets(marketAssets)
        } catch (e: Exception) { Log.e("AssetRepo", "Internal error: ${e.message}") }
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

        var totalCostBase = BigDecimal.ZERO
        assets.forEach { a ->
            val rate = when(a.currency) { "USD" -> usdHistory.lastOrNull()?.second ?: 44.52; "EUR" -> eurHistory.lastOrNull()?.second ?: 35.2; else -> 1.0 }
            totalCostBase += (a.amount * a.averageBuyPrice * BigDecimal(rate.toString()))
        }
        val port = if (pId != -1L) portfolioDao.getPortfolioById(pId) else null
        val effCost = if (port != null && port.depositedAmount > totalCostBase) port.depositedAmount else totalCostBase

        allTs.map { ts ->
            var dayVal = BigDecimal.ZERO
            histories.forEach { (a, h) ->
                val p = h.find { it.first <= ts }?.second ?: h.firstOrNull()?.second ?: 0.0
                val rate = when(a.currency) { "USD" -> usdHistory.find { it.first <= ts }?.second ?: 44.52; "EUR" -> eurHistory.find { it.first <= ts }?.second ?: 35.0; else -> 1.0 }
                dayVal += (a.amount * BigDecimal(p.toString()) * BigDecimal(rate.toString()))
            }
            PortfolioHistory(portfolioId = pId, date = ts, totalValue = dayVal, currency = "TRY", profitLoss = dayVal - effCost)
        }
    }

    private fun cleanMarketAssetNaming(asset: MarketAsset, type: AssetType): MarketAsset {
        var name = asset.name; var fullName = asset.fullName ?: asset.name; var symbol = asset.symbol; val cleanSymbol = symbol.uppercase()
        when {
            type == AssetType.BIST -> name = name.replace(".IS", "").trim()
            type == AssetType.DOVIZ || type == AssetType.NAKIT || (type == AssetType.EMTIA && (cleanSymbol == "TRY=X" || cleanSymbol == "USDTRY=X")) -> {
                val localized = when { 
                    cleanSymbol.startsWith("USDTRY") || cleanSymbol == "TRY=X" -> "Amerikan Doları"
                    cleanSymbol.startsWith("EURTRY") -> "Euro"
                    cleanSymbol.startsWith("GBPTRY") -> "İngiliz Sterlini"
                    cleanSymbol.startsWith("CHFTRY") -> "İsviçre Frangı"
                    cleanSymbol.startsWith("JPYTRY") -> "Japon Yeni"
                    cleanSymbol.startsWith("AUDTRY") -> "Avustralya Doları"
                    cleanSymbol.startsWith("CADTRY") -> "Kanada Doları"
                    cleanSymbol.startsWith("AEDTRY") -> "Birleşik Arap Emirlikleri Dirhemi"
                    else -> fullName 
                }
                fullName = localized
                name = localized
                if (type == AssetType.NAKIT) symbol = when { 
                    cleanSymbol.startsWith("USDTRY") || cleanSymbol == "TRY=X" -> "USD"
                    cleanSymbol.startsWith("EURTRY") -> "EUR"
                    cleanSymbol.startsWith("GBPTRY") -> "GBP"
                    cleanSymbol.startsWith("AEDTRY") -> "AED"
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
                // Strip "Month Year" if it contains patterns like "May 26", "Jun 26"
                fullName = name.replace(Regex("\\s+[A-Za-z]{3}\\s+\\d{2}$"), "").trim()
                name = fullName
            }
        }
        return asset.copy(name = name, fullName = fullName, symbol = symbol)
    }

    private fun cleanAssetNaming(asset: Asset, type: AssetType): Asset {
        var name = asset.name; var symbol = asset.symbol; val cleanSymbol = symbol.uppercase()
        when {
            type == AssetType.BIST -> name = name.replace(".IS", "").trim()
            type == AssetType.DOVIZ || type == AssetType.NAKIT || (type == AssetType.EMTIA && (cleanSymbol == "TRY=X" || cleanSymbol == "USDTRY=X")) -> {
                name = when { 
                    cleanSymbol.startsWith("USDTRY") || cleanSymbol == "TRY=X" -> "Amerikan Doları"
                    cleanSymbol.startsWith("EURTRY") -> "Euro"
                    cleanSymbol.startsWith("GBPTRY") -> "İngiliz Sterlini"
                    cleanSymbol.startsWith("CHFTRY") -> "İsviçre Frangı"
                    cleanSymbol.startsWith("JPYTRY") -> "Japon Yeni"
                    cleanSymbol.startsWith("AUDTRY") -> "Avustralya Doları"
                    cleanSymbol.startsWith("CADTRY") -> "Kanada Doları"
                    cleanSymbol.startsWith("AEDTRY") -> "Birleşik Arap Emirlikleri Dirhemi"
                    else -> name 
                }
                if (type == AssetType.NAKIT) symbol = when { 
                    cleanSymbol.startsWith("USDTRY") || cleanSymbol == "TRY=X" -> "USD"
                    cleanSymbol.startsWith("EURTRY") -> "EUR"
                    cleanSymbol.startsWith("GBPTRY") -> "GBP"
                    cleanSymbol.startsWith("AEDTRY") -> "AED"
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
