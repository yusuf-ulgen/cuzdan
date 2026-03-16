package com.example.cuzdan.data.repository

import com.example.cuzdan.data.local.dao.AssetDao
import com.example.cuzdan.data.local.entity.Asset
import com.example.cuzdan.data.local.entity.AssetType
import com.example.cuzdan.data.remote.api.BinanceApi
import com.example.cuzdan.data.remote.api.YahooFinanceApi
import com.example.cuzdan.data.remote.api.TefasApi
import com.example.cuzdan.data.remote.model.TefasRequest
import com.example.cuzdan.util.Resource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import java.math.BigDecimal
import java.math.RoundingMode
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
        return assetDao.getAssetsByTypes(listOf(AssetType.BIST, AssetType.DOVIZ, AssetType.ALTIN))
    }

    /**
     * Belirli bir portföye ait varlıkları döner.
     */
    fun getAssetsByPortfolioId(portfolioId: Long): Flow<List<Asset>> {
        return assetDao.getAssetsByPortfolioId(portfolioId)
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
                    Asset(symbol = "GC=F", name = "Ons Altın", amount = BigDecimal.ZERO, averageBuyPrice = BigDecimal.ZERO, currentPrice = BigDecimal.ZERO, dailyChangePercentage = BigDecimal.ZERO, assetType = AssetType.ALTIN),
                    Asset(symbol = "GRAM_ALTIN", name = "Gram Altın", amount = BigDecimal.ZERO, averageBuyPrice = BigDecimal.ZERO, currentPrice = BigDecimal.ZERO, dailyChangePercentage = BigDecimal.ZERO, assetType = AssetType.ALTIN)
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
                    
                    // TEFAS bazen o gün için veri dönmeyebilir (hafta sonu vs), ama son fiyatı alabiliriz.
                    val latestPrice = response.firstOrNull()?.price?.let { BigDecimal(it) }
                    
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
}
