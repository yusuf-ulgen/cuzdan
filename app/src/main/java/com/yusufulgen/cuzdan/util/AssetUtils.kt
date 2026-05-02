package com.yusufulgen.cuzdan.util

import com.yusufulgen.cuzdan.R
import com.yusufulgen.cuzdan.data.local.entity.AssetType

object AssetUtils {
    fun getAssetIcon(symbol: String, type: AssetType): Int {
        val sym = symbol.uppercase()
        return when (type) {
            AssetType.DOVIZ, AssetType.NAKIT -> {
                when {
                    sym.contains("USD") -> R.drawable.ic_usd
                    sym.contains("EUR") -> R.drawable.ic_eur
                    sym.contains("TRY") || sym == "TL" -> R.drawable.ic_try
                    sym.contains("GBP") -> R.drawable.ic_gbp
                    else -> R.drawable.ic_currency
                }
            }
            AssetType.KRIPTO -> R.drawable.ic_crypto
            AssetType.BIST -> R.drawable.ic_bist
            AssetType.FON -> R.drawable.ic_funds
            AssetType.EMTIA -> {
                when {
                    sym.contains("GOLD") || sym == "GC=F" || sym == "GRAM_ALTIN" -> R.drawable.commodity_gold
                    sym.contains("SILVER") || sym == "SI=F" || sym.contains("GUMUS") -> R.drawable.commodity_silver
                    else -> R.drawable.ic_commodity
                }
            }
        }
    }
}
