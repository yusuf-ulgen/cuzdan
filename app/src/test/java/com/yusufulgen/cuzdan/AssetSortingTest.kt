package com.yusufulgen.cuzdan

import com.yusufulgen.cuzdan.data.local.entity.Asset
import com.yusufulgen.cuzdan.data.local.entity.AssetType
import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigDecimal

class AssetSortingTest {

    @Test
    fun testNakitSorting() {
        val assets = listOf(
            Asset(symbol = "USD", name = "Amerikan Doları", amount = BigDecimal.ONE, averageBuyPrice = BigDecimal.ZERO, currentPrice = BigDecimal.ZERO, assetType = AssetType.NAKIT),
            Asset(symbol = "EUR", name = "Euro", amount = BigDecimal.ONE, averageBuyPrice = BigDecimal.ZERO, currentPrice = BigDecimal.ZERO, assetType = AssetType.NAKIT),
            Asset(symbol = "TRY", name = "Türk Lirası", amount = BigDecimal.ONE, averageBuyPrice = BigDecimal.ZERO, currentPrice = BigDecimal.ZERO, assetType = AssetType.NAKIT),
            Asset(symbol = "GBP", name = "İngiliz Sterlini", amount = BigDecimal.ONE, averageBuyPrice = BigDecimal.ZERO, currentPrice = BigDecimal.ZERO, assetType = AssetType.NAKIT)
        )

        val order = listOf("TRY", "TL", "USD", "EUR", "GBP", "CHF", "JPY", "GBPUSD=X")
        val sorted = assets.sortedWith(compareBy<Asset> { asset ->
            val symbol = asset.symbol.uppercase()
            if (symbol == "TRY" || symbol == "TL" || asset.name == "Türk Lirası") {
                -1
            } else {
                val index = order.indexOf(symbol)
                if (index == -1) Int.MAX_VALUE else index
            }
        })

        assertEquals("TRY", sorted[0].symbol)
        assertEquals("USD", sorted[1].symbol)
        assertEquals("EUR", sorted[2].symbol)
        assertEquals("GBP", sorted[3].symbol)
    }

    @Test
    fun testCryptoSortingByValue() {
        val usdRate = BigDecimal("32.5")
        val assets = listOf(
            Asset(symbol = "ETH", name = "Ethereum", amount = BigDecimal("2"), currentPrice = BigDecimal("3000"), currency = "USD", assetType = AssetType.KRIPTO, averageBuyPrice = BigDecimal.ZERO), // Value: 6000 USD
            Asset(symbol = "BTC", name = "Bitcoin", amount = BigDecimal("0.5"), currentPrice = BigDecimal("60000"), currency = "USD", assetType = AssetType.KRIPTO, averageBuyPrice = BigDecimal.ZERO), // Value: 30000 USD
            Asset(symbol = "SOL", name = "Solana", amount = BigDecimal("10"), currentPrice = BigDecimal("150"), currency = "USD", assetType = AssetType.KRIPTO, averageBuyPrice = BigDecimal.ZERO) // Value: 1500 USD
        )

        val sorted = assets.sortedByDescending { asset ->
            val assetRate = when (asset.currency) {
                "USD" -> usdRate
                else -> BigDecimal.ONE
            }
            asset.amount.multiply(asset.currentPrice).multiply(assetRate)
        }

        assertEquals("BTC", sorted[0].symbol)
        assertEquals("ETH", sorted[1].symbol)
        assertEquals("SOL", sorted[2].symbol)
    }
}
