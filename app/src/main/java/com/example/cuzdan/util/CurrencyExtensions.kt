package com.example.cuzdan.util

import android.content.Context
import com.example.cuzdan.R
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat
import java.util.Locale

/**
 * Finansal hesaplamalar için BigDecimal uzantı fonksiyonları.
 * Double/Float yerine BigDecimal kullanarak küsürat hatalarını engeller.
 */

// Varsayılan yuvarlama modu ve hassasiyeti
private const val DEFAULT_PRECISION = 2
private const val CRYPTO_PRECISION = 8

/**
 * Ana sayfa / raporlar para birimi butonu: `currency_usd`, `currency_eur`, `currency_try` PNG varsa onları kullanır.
 */
fun Context.resolveCurrencySwitcherIcon(currencyCode: String): Int {
    val customName = when (currencyCode) {
        "USD" -> "currency_usd"
        "EUR" -> "currency_eur"
        "TL", "TRY" -> "currency_try"
        else -> null
    }
    if (customName != null) {
        val id = resources.getIdentifier(customName, "drawable", packageName)
        if (id != 0) return id
    }
    return when (currencyCode) {
        "USD" -> R.drawable.ic_usd
        "EUR" -> R.drawable.ic_eur
        else -> R.drawable.ic_tl
    }
}

fun BigDecimal.formatCurrency(currencyCode: String = "TL", showSign: Boolean = false): String {
    val format = NumberFormat.getCurrencyInstance(Locale("tr", "TR"))
    try {
        val currency = java.util.Currency.getInstance(if (currencyCode == "TL") "TRY" else currencyCode)
        format.currency = currency
    } catch (e: Exception) {
        // Fallback
    }
    val formatted = format.format(this)
    return if (showSign && this > BigDecimal.ZERO) "+$formatted" else formatted
}

fun BigDecimal.roundForUI(precision: Int = DEFAULT_PRECISION): BigDecimal {
    return this.setScale(precision, RoundingMode.HALF_UP)
}

fun BigDecimal.roundForCrypto(): BigDecimal {
    return this.setScale(CRYPTO_PRECISION, RoundingMode.HALF_UP)
}

/**
 * Kar/Zarar yüzdesini hesaplar.
 */
fun calculateProfitLossPercentage(currentPrice: BigDecimal, averagePrice: BigDecimal): BigDecimal {
    if (averagePrice.compareTo(BigDecimal.ZERO) == 0) return BigDecimal.ZERO
    
    return currentPrice.subtract(averagePrice)
        .divide(averagePrice, 4, RoundingMode.HALF_UP)
        .multiply(BigDecimal(100))
        .setScale(2, RoundingMode.HALF_UP)
}
