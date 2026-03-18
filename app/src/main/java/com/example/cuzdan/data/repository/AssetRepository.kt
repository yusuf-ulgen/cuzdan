package com.example.cuzdan.data.repository

import com.example.cuzdan.data.local.dao.AssetDao
import com.example.cuzdan.data.local.entity.Asset
import com.example.cuzdan.data.local.entity.AssetType
import com.example.cuzdan.data.remote.api.BinanceApi
import com.example.cuzdan.data.remote.api.YahooFinanceApi
import com.example.cuzdan.data.remote.api.TefasApi
import com.example.cuzdan.data.remote.model.TefasRequest
import com.example.cuzdan.data.remote.model.YahooQuote
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
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AssetRepository @Inject constructor(
    private val assetDao: AssetDao,
    private val binanceApi: BinanceApi,
    private val yahooFinanceApi: YahooFinanceApi,
    private val tefasApi: TefasApi
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
     * Binance API'den kripto fiyatlarını günceller.
     */
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
                        dailyChangePercentage = BigDecimal(it.priceChangePercent)
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
                    val price = result?.regularMarketPrice?.let { BigDecimal(it) } ?: BigDecimal.ZERO
                    val prevClose = result?.previousClose?.let { BigDecimal(it) } ?: BigDecimal.ZERO
                    
                    val changePerc = if (prevClose > BigDecimal.ZERO) {
                        price.subtract(prevClose)
                            .divide(prevClose, 4, RoundingMode.HALF_UP)
                            .multiply(BigDecimal("100"))
                    } else {
                        BigDecimal.ZERO
                    }

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
                        dailyChangePercentage = changePerc
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
     * TEFAS API'den fon fiyatlarını günceller.
     */
    suspend fun refreshFundPrices(): Flow<Resource<Unit>> = flow {
        emit(Resource.Loading())
        try {
            val fundAssets = getFundAssets().first()
            if (fundAssets.isEmpty()) {
                emit(Resource.Success(Unit))
                return@flow
            }

            // Bugünün tarihini formatla (YYYY-MM-DD)
            val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
            val dateStr = sdf.format(java.util.Date())

            fundAssets.forEach { asset ->
                try {
                    val request = TefasRequest(fundType = asset.symbol, date = dateStr)
                    val response = tefasApi.getFundPrices(request)
                    
                    // Safe parse Any? price
                    val rawPrice = response.firstOrNull()?.price?.toString() ?: "0"
                    val latestPrice = rawPrice.replace(",", ".").toDoubleOrNull()?.let { BigDecimal(it) }
                    
                    latestPrice?.let {
                        assetDao.updateAsset(asset.copy(currentPrice = it))
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            emit(Resource.Success(Unit))
        } catch (e: Exception) {
            emit(Resource.Error(e.message ?: "Fon verileri güncellenemedi"))
        }
    }

    /**
     * Canlı arama yapar.
     */
    suspend fun searchAssets(query: String, type: AssetType): List<Asset> {
        Log.d("AssetRepo", "searchAssets: query=$query, type=$type")
        if (query.isBlank()) return emptyList()

        return try {
            val results = when (type) {
                AssetType.KRIPTO -> {
                    val allTickers = binanceApi.getAllTickers()
                    allTickers.filter { it.symbol.contains(query, ignoreCase = true) }
                        .take(20)
                        .map {
                            Asset(
                                symbol = it.symbol,
                                name = it.symbol.replace("USDT", ""),
                                amount = BigDecimal.ZERO,
                                averageBuyPrice = BigDecimal.ZERO,
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
                            Asset(
                                symbol = quote.symbol,
                                name = quote.shortName ?: quote.longName ?: quote.symbol,
                                amount = BigDecimal.ZERO,
                                averageBuyPrice = BigDecimal.ZERO,
                                currentPrice = BigDecimal(quote.regularMarketPrice ?: 0.0).setScale(2, RoundingMode.HALF_UP),
                                dailyChangePercentage = BigDecimal(quote.regularMarketChangePercent ?: 0.0).setScale(2, RoundingMode.HALF_UP),
                                assetType = type
                            )
                        } ?: emptyList()
                    } catch (e: Exception) {
                        Log.e("AssetRepo", "Bulk quote fetch failed, trying chart fallback: ${e.message}")
                        // Fallback: Individual chart calls
                        symbols.take(5).map { symbol ->
                            try {
                                val chartResponse = yahooFinanceApi.getChartData(symbol)
                                val meta = chartResponse.chart.result?.firstOrNull()?.meta
                                val price = BigDecimal(meta?.regularMarketPrice ?: 0.0)
                                val prevClose = meta?.previousClose ?: 0.0
                                val change = if (prevClose > 0) {
                                    BigDecimal((meta!!.regularMarketPrice - prevClose) / prevClose * 100).setScale(2, RoundingMode.HALF_UP)
                                } else BigDecimal.ZERO

                                Asset(
                                    symbol = symbol,
                                    name = symbol, // Chart meta doesn't have shortName
                                    amount = BigDecimal.ZERO,
                                    averageBuyPrice = BigDecimal.ZERO,
                                    currentPrice = price,
                                    dailyChangePercentage = change,
                                    assetType = type
                                )
                            } catch (e: Exception) {
                                Asset(symbol = symbol, name = symbol, amount = BigDecimal.ZERO, averageBuyPrice = BigDecimal.ZERO, currentPrice = BigDecimal.ZERO, dailyChangePercentage = BigDecimal.ZERO, assetType = type)
                            }
                        }
                    }
                }
            }
            
            // İsimleri ve sembolleri temizle
            results.map { cleanAssetNaming(it, type) }
        } catch (e: Exception) {
            Log.e("AssetRepo", "searchAssets Error: ${e.message}", e)
            emptyList()
        }
    }

    /**
     * Piyasa ekranı için tüm varlıkları döner.
     */
    suspend fun getMarketAssets(type: AssetType): List<Asset> {
        Log.d("AssetRepo", "getMarketAssets: type=$type")
        return try {
            when (type) {
                AssetType.KRIPTO -> {
                    Log.d("AssetRepo", "Fetching Crypto")
                    binanceApi.getAllTickers()
                        .filter { it.symbol.endsWith("USDT") }
                        .sortedByDescending { it.lastPrice.toDouble() }
                        .map {
                            Asset(
                                symbol = it.symbol,
                                name = it.symbol.replace("USDT", ""),
                                amount = BigDecimal.ZERO,
                                averageBuyPrice = BigDecimal.ZERO,
                                currentPrice = BigDecimal(it.lastPrice),
                                dailyChangePercentage = BigDecimal(it.priceChangePercent).setScale(2, RoundingMode.HALF_UP),
                                assetType = AssetType.KRIPTO
                            )
                        }
                }
                AssetType.FON -> {
                    Log.d("AssetRepo", "Fetching Fonlar (TEFAS)")
                    val symbols = listOf(
                        "TTE", "IJP", "MAC", "GSP", "AFT", "KOC", "IPV", "OPI", "RPD", "TAU", "YAY", "TI1", "GMR",
                        "TE3", "HVS", "TDF", "IKL", "NJR", "BUY", "NNF", "BGP", "KZT", "ZPE", "OJT", "IDL", "KDV",
                        "GPA", "RTG", "OTJ", "ZPF", "YZG", "HKH", "ZHB", "AFO", "GL1", "IVY", "YAS", "GMR", "IHK",
                        "EID", "ST1", "GAY", "DBH", "YHS", "ZPC", "AES", "IPJ", "GUH", "IEY", "YTD"
                    )
                    val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
                    val calendar = java.util.Calendar.getInstance()
                    
                    symbols.map { symbol ->
                        var price = BigDecimal.ZERO
                        var fundName = "$symbol Fonu"
                        for (i in 0..5) {
                            try {
                                val dateStr = sdf.format(calendar.time)
                                val response = tefasApi.getFundPrices(TefasRequest(fundType = symbol, date = dateStr))
                                val entry = response.firstOrNull()
                                if (entry != null) {
                                    fundName = entry.fundName ?: fundName
                                    val rawPriceAny = entry.price
                                    
                                    Log.d("AssetRepo", "TEFAS Debug: symbol=$symbol, rawPrice=$rawPriceAny, type=${rawPriceAny?.javaClass?.simpleName}")
                                    
                                    val parsedPrice = when (rawPriceAny) {
                                        is Number -> rawPriceAny.toDouble()
                                        is String -> {
                                            val cleanStr = rawPriceAny.replace("\u00A0", "").trim()
                                            if (cleanStr.contains(",") && cleanStr.contains(".")) {
                                                cleanStr.replace(".", "").replace(",", ".").toDoubleOrNull() ?: 0.0
                                            } else if (cleanStr.contains(",")) {
                                                cleanStr.replace(",", ".").toDoubleOrNull() ?: 0.0
                                            } else {
                                                cleanStr.toDoubleOrNull() ?: 0.0
                                            }
                                        }
                                        else -> 0.0
                                    }
                                    
                                    if (parsedPrice > 0) {
                                        price = BigDecimal(parsedPrice)
                                        Log.d("AssetRepo", "Found price for $symbol: $price")
                                        break
                                    }
                                }
                            } catch (e: Exception) {
                                Log.e("AssetRepo", "TEFAS error for $symbol on day $i: ${e.message}")
                            }
                            calendar.add(java.util.Calendar.DATE, -1)
                        }
                        calendar.setTime(java.util.Date())
                        
                        Asset(
                            symbol = symbol,
                            name = fundName ?: symbol,
                            amount = BigDecimal.ZERO,
                            averageBuyPrice = BigDecimal.ZERO,
                            currentPrice = price,
                            dailyChangePercentage = BigDecimal.ZERO,
                            assetType = AssetType.FON
                        )
                    }
                }
                else -> {
                    val symbols = when (type) {
                        AssetType.BIST -> ALL_BIST_SYMBOLS
                        AssetType.DOVIZ -> listOf("TRY=X", "EURTRY=X", "GBPTRY=X", "CHFTRY=X", "JPYTRY=X", "AUDTRY=X", "CADTRY=X")
                        AssetType.NAKIT -> listOf("TRY=X", "EURTRY=X", "GBPTRY=X", "CHFTRY=X", "JPYTRY=X")
                        AssetType.EMTIA -> listOf("GC=F", "SI=F", "PL=F", "PA=F", "HG=F", "GRAM_ALTIN", "TRY=X")
                        else -> emptyList()
                    }
                    Log.d("AssetRepo", "Fetching Yahoo/Other for type $type: ${symbols.size} symbols")
                    
                    val yahooSymbols = symbols.filter { it != "GRAM_ALTIN" }
                    val assets = mutableListOf<Asset>()
                    
                    try {
                        // Chunked fetch for Yahoo
                        val chunks = yahooSymbols.chunked(10)
                        Log.d("AssetRepo", "Fetching ${chunks.size} chunks (size 10) of Yahoo symbols")
                        
                        chunks.forEachIndexed { index, chunk ->
                            try {
                                val quotes = yahooFinanceApi.getQuotes(chunk.joinToString(","))
                                val result = quotes.quoteResponse.result ?: emptyList()
                                Log.d("AssetRepo", "Chunk $index: fetched ${result.size} symbols")
                                
                                result.forEach { quote ->
                                    val name = quote.shortName ?: quote.longName ?: quote.symbol
                                    val symbol = quote.symbol
                                    
                                    assets.add(cleanAssetNaming(Asset(
                                        symbol = symbol,
                                        name = name,
                                        amount = BigDecimal.ZERO,
                                        averageBuyPrice = BigDecimal.ZERO,
                                        currentPrice = BigDecimal(quote.regularMarketPrice ?: 0.0).setScale(2, RoundingMode.HALF_UP),
                                        dailyChangePercentage = BigDecimal(quote.regularMarketChangePercent ?: 0.0).setScale(2, RoundingMode.HALF_UP),
                                        assetType = type
                                    ), type))
                                }
                            } catch (e: Exception) {
                                Log.e("AssetRepo", "Chunk $index fetch failed: ${e.message}")
                            }
                        }

                        if (assets.isEmpty() && yahooSymbols.isNotEmpty()) {
                             throw Exception("All granular chunks failed to return valid data")
                        }
                    } catch (e: Exception) {
                        Log.e("AssetRepo", "Yahoo Granular Chunk Fetch failed: ${e.message}, trying UNLIMITED individual fallback")
                        // Use coroutines to fetch all in parallel to avoid long sequential waits
                        coroutineScope {
                            val deferredAssets = yahooSymbols.map { s ->
                                async {
                                    try {
                                        val resp = yahooFinanceApi.getChartData(s)
                                        val meta = resp.chart.result?.firstOrNull()?.meta
                                        val price = BigDecimal(meta?.regularMarketPrice ?: 0.0)
                                        val prevClose = meta?.previousClose ?: 0.0
                                        val change = if (prevClose > 0) {
                                            BigDecimal((meta!!.regularMarketPrice - prevClose) / prevClose * 100).setScale(2, RoundingMode.HALF_UP)
                                        } else BigDecimal.ZERO

                                        cleanAssetNaming(Asset(
                                            symbol = s,
                                            name = s,
                                            amount = BigDecimal.ZERO,
                                            averageBuyPrice = BigDecimal.ZERO,
                                            currentPrice = price,
                                            dailyChangePercentage = change,
                                            assetType = type
                                        ), type)
                                    } catch (e2: Exception) {
                                        Asset(symbol = s, name = s, amount = BigDecimal.ZERO, averageBuyPrice = BigDecimal.ZERO, currentPrice = BigDecimal.ZERO, dailyChangePercentage = BigDecimal.ZERO, assetType = type)
                                    }
                                }
                            }
                            assets.addAll(deferredAssets.awaitAll())
                        }
                    }
                    
                    if (type == AssetType.NAKIT) {
                        assets.add(0, Asset(
                            symbol = "TRY",
                            name = "Türk Lirası",
                            amount = BigDecimal.ZERO,
                            averageBuyPrice = BigDecimal.ZERO,
                            currentPrice = BigDecimal.ONE,
                            dailyChangePercentage = BigDecimal.ZERO,
                            assetType = AssetType.NAKIT
                        ))
                    }
                    
                    if (type == AssetType.EMTIA && symbols.contains("GRAM_ALTIN")) {
                        // Look for GC=F and TRY=X (USD/TRY)
                        val onsAsset = assets.find { it.symbol == "GC=F" }
                        val usdTryAsset = assets.find { it.symbol == "TRY=X" }
                        
                        val onsPrice = onsAsset?.currentPrice ?: BigDecimal.ZERO
                        val usdTryPrice = usdTryAsset?.currentPrice ?: BigDecimal.ZERO
                        
                        Log.d("AssetRepo", "Gram Gold Calc: Ons=$onsPrice, USDTRY=$usdTryPrice")
                        
                        if (onsPrice > BigDecimal.ZERO && usdTryPrice > BigDecimal.ZERO) {
                            val gramPrice = onsPrice.divide(BigDecimal("31.1035"), 8, RoundingMode.HALF_UP).multiply(usdTryPrice)
                            assets.add(Asset(
                                symbol = "GRAM_ALTIN",
                                name = "Gram Altın",
                                amount = BigDecimal.ZERO,
                                averageBuyPrice = BigDecimal.ZERO,
                                currentPrice = gramPrice.setScale(2, RoundingMode.HALF_UP),
                                dailyChangePercentage = onsAsset?.dailyChangePercentage ?: BigDecimal.ZERO,
                                assetType = AssetType.EMTIA
                            ))
                        }
                    }
                    if (type == AssetType.EMTIA) {
                        assets.removeAll { it.symbol == "TRY=X" }
                    }
                    Log.d("AssetRepo", "Returning ${assets.size} assets for $type")
                    assets
                }
            }
        } catch (e: Exception) {
            Log.e("AssetRepo", "getMarketAssets Major Error: ${e.message}", e)
            emptyList()
        }
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
                dailyChangePercentage = asset.dailyChangePercentage
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
                if (price != null) ts * 1000 to price else null
            }
        } catch (e: Exception) {
            Log.e("AssetRepo", "History fetch failed for $symbol: ${e.message}")
            emptyList()
        }
    }

    suspend fun addAsset(asset: Asset) {
        assetDao.insertAsset(asset)
    }

    private fun cleanAssetNaming(asset: Asset, type: AssetType): Asset {
        var name = asset.name
        var symbol = asset.symbol
        val cleanSymbol = symbol.uppercase()

        when {
            type == AssetType.BIST -> {
                name = name.replace(".IS", "").trim()
            }
            type == AssetType.DOVIZ || type == AssetType.NAKIT || (type == AssetType.EMTIA && (cleanSymbol == "TRY=X" || cleanSymbol.startsWith("USDTRY"))) -> {
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
                    cleanSymbol.startsWith("GC=F") -> "Altın (Ons)"
                    cleanSymbol.startsWith("SI=F") -> "Gümüş"
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

    companion object {
        private val ALL_BIST_SYMBOLS = listOf(
            "A1CAP.IS", "A1YEN.IS", "ACSEL.IS", "ADEL.IS", "ADESE.IS", "ADGYO.IS", "AEFES.IS", "AFYON.IS", "AGESA.IS", "AGHOL.IS", "AGROT.IS", "AGYO.IS", "AHGAZ.IS", "AHSGY.IS", "AKBNK.IS", "AKCNS.IS", "AKENR.IS", "AKFGY.IS", "AKFIS.IS", "AKFYE.IS", "AKGRT.IS", "AKHAN.IS", "AKMGY.IS", "AKSA.IS", "AKSEN.IS", "AKSUE.IS", "AKYHO.IS", "ALARK.IS", "ALBRK.IS", "ALCAR.IS", "ALCTL.IS", "ALDO.IS", "ALGGY.IS", "ALGYO.IS", "ALKA.IS", "ALKIM.IS", "ALKLC.IS", "ALTNY.IS", "ALVES.IS", "ANELE.IS", "ANGEN.IS", "ANHYT.IS", "ANSGR.IS", "ARASE.IS", "ARCLK.IS", "ARDYZ.IS", "ARENA.IS", "ARFYE.IS", "ARMGD.IS", "ARSAN.IS", "ARTMS.IS", "ARZUM.IS", "ASELS.IS", "ASGYO.IS", "ASTOR.IS", "ASUZU.IS", "ATAGY.IS", "ATAKP.IS", "ATATP.IS", "ATEKS.IS", "ATLAS.IS", "ATSYH.IS", "AVGYO.IS", "AVHOL.IS", "AVOD.IS", "AVPGY.IS", "AVTUR.IS", "AYCES.IS", "AYDEM.IS", "AYEN.IS", "AYES.IS", "AYGAZ.IS", "AZTEK.IS",
            "BAGFS.IS", "BAHKM.IS", "BAKAB.IS", "BALAT.IS", "BALSU.IS", "BNTAS.IS", "BANVT.IS", "BARMA.IS", "BASGZ.IS", "BASCM.IS", "BAYRK.IS", "BEGYO.IS", "BERA.IS", "BESLR.IS", "BESTE.IS", "BEYAZ.IS", "BFREN.IS", "BIENY.IS", "BIGCH.IS", "BIMAS.IS", "BINBN.IS", "BINHO.IS", "BIOEN.IS", "BIZIM.IS", "BJKAS.IS", "BLCYT.IS", "BLUME.IS", "BMSCH.IS", "BMSTL.IS", "BOBET.IS", "BORLS.IS", "BORSK.IS", "BOSSA.IS", "BRISA.IS", "BRKO.IS", "BRKSN.IS", "BRKVY.IS", "BRLSM.IS", "BRMEN.IS", "BRSAN.IS", "BRYAT.IS", "BSOKE.IS", "BTCIM.IS", "BUCIM.IS", "BURCE.IS", "BURVA.IS", "BVSAN.IS", "BYDNR.IS",
            "CANTE.IS", "CASA.IS", "CATES.IS", "CCOLA.IS", "CELHA.IS", "CEMAS.IS", "CEMTS.IS", "CEMZY.IS", "CEOEM.IS", "CGCAM.IS", "CIMSA.IS", "CLEBI.IS", "CMBTN.IS", "CMENT.IS", "CONSE.IS", "COSMO.IS", "CRDFA.IS", "CRFSA.IS", "CUSAN.IS", "CVKMD.IS", "CWENE.IS",
            "DAGHL.IS", "DAGI.IS", "DAPGM.IS", "DARDL.IS", "DCTTR.IS", "DENGE.IS", "DERAS.IS", "DERIM.IS", "DESA.IS", "DESPC.IS", "DEVA.IS", "DGGYO.IS", "DGNMO.IS", "DIRIT.IS", "DITAS.IS", "DMSAS.IS", "DNZGY.IS", "DOAS.IS", "DOBUR.IS", "DOCO.IS", "DOGUB.IS", "DOHOL.IS", "DOKTA.IS", "DURDO.IS", "DYOBY.IS", "DZGYO.IS",
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
            "TABGD.IS", "TAPDI.IS", "TARKM.IS", "TATGD.IS", "TAVHL.IS", "TBTAS.IS", "TCPAL.IS", "TEKTU.IS", "TERA.IS", "TETMT.IS", "TEZOL.IS", "THYAO.IS", "TIRE.IS", "TKFEN.IS", "TKNSA.IS", "TMSN.IS", "TOASO.IS", "TRGYO.IS", "TRILC.IS", "TSKB.IS", "TSGYO.IS", "TUCLK.IS", "TUKAS.IS", "TUPRS.IS", "TUREX.IS", "TURSG.IS",
            "UFUK.IS", "ULAS.IS", "ULUFA.IS", "ULUSE.IS", "ULUUN.IS", "UMPAS.IS", "USAK.IS",
            "VAKBN.IS", "VAKFN.IS", "VAKKO.IS", "VANGD.IS", "VBTYZ.IS", "VERTU.IS", "VERUS.IS", "VESBE.IS", "VESTL.IS", "VKFYO.IS", "VKGYO.IS", "VKING.IS", "VRGYO.IS",
            "YAPRK.IS", "YAYLA.IS", "YEOTK.IS", "YESIL.IS", "YGGYO.IS", "YGYO.IS", "YKBNK.IS", "YKSLN.IS", "YONGA.IS", "YUNSA.IS",
            "ZEDUR.IS", "ZOREN.IS", "ZRGYO.IS"
        )
    }
}
