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
                    else -> R.drawable.doviz
                }
            }
            AssetType.KRIPTO -> {
                when {
                    sym.contains("BTC") || sym.contains("BITCOIN") -> R.drawable.kripto
                    else -> R.drawable.kripto
                }
            }
            AssetType.BIST -> R.drawable.borsa
            AssetType.FON -> R.drawable.fon
            AssetType.EMTIA -> {
                when {
                    sym.contains("GOLD") || sym.contains("ALTIN") -> R.drawable.emtia
                    sym.contains("SILVER") || sym.contains("GUMUS") || sym.contains("SI=F") || sym.contains("XAG") -> R.drawable.ic_commodity
                    sym.contains("OIL") || sym.contains("PETROL") || sym.contains("CL=F") || sym.contains("BZ=F") -> R.drawable.ic_commodity
                    sym.contains("GAS") || sym.contains("GAZ") || sym.contains("NG=F") -> R.drawable.ic_commodity
                    sym.contains("ALUMIN") || sym.contains("ALI=F") || sym.contains("NICKEL") || sym.contains("NI=F") || sym.contains("ZINC") || sym.contains("ZN=F") -> R.drawable.ic_commodity
                    else -> R.drawable.emtia
                }
            }
        }
    }
}
