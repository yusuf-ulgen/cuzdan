package com.yusufulgen.cuzdan.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.util.TypedValue

object EmojiDrawableHelper {

    /** Converts a unicode flag emoji (or any emoji) to a Drawable that can be set on ImageView. */
    fun emojiToDrawable(context: Context, emoji: String, sizeDp: Float = 36f): Drawable {
        val sizePx = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, sizeDp, context.resources.displayMetrics
        ).toInt().coerceAtLeast(1)

        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = sizePx * 0.78f
            textAlign = Paint.Align.CENTER
        }
        val yPos = (sizePx / 2f) - ((paint.descent() + paint.ascent()) / 2f)
        canvas.drawText(emoji, sizePx / 2f, yPos, paint)
        return BitmapDrawable(context.resources, bitmap)
    }

    /** Returns the country flag emoji for a given ISO 4217 currency code. */
    fun currencyToFlagEmoji(currencyCode: String): String? = when (currencyCode.uppercase()) {
        "TRY", "TL"  -> "🇹🇷"
        "USD"         -> "🇺🇸"
        "EUR"         -> "🇪🇺"
        "GBP"         -> "🇬🇧"
        "CHF"         -> "🇨🇭"
        "JPY"         -> "🇯🇵"
        "AUD"         -> "🇦🇺"
        "CAD"         -> "🇨🇦"
        "SAR"         -> "🇸🇦"
        "QAR"         -> "🇶🇦"
        "AED"         -> "🇦🇪"
        "KWD"         -> "🇰🇼"
        "BHD"         -> "🇧🇭"
        "OMR"         -> "🇴🇲"
        "RUB"         -> "🇷🇺"
        "CNY", "RMB"  -> "🇨🇳"
        "AZN"         -> "🇦🇿"
        "SGD"         -> "🇸🇬"
        "NOK"         -> "🇳🇴"
        "SEK"         -> "🇸🇪"
        "DKK"         -> "🇩🇰"
        "BGN"         -> "🇧🇬"
        "RON"         -> "🇷🇴"
        "ILS"         -> "🇮🇱"
        "THB"         -> "🇹🇭"
        "MYR"         -> "🇲🇾"
        "IDR"         -> "🇮🇩"
        "PHP"         -> "🇵🇭"
        "PKR"         -> "🇵🇰"
        "EGP"         -> "🇪🇬"
        "ZAR"         -> "🇿🇦"
        "MAD"         -> "🇲🇦"
        "GEL"         -> "🇬🇪"
        "UAH"         -> "🇺🇦"
        "ISK"         -> "🇮🇸"
        "KZT", "KAZ"  -> "🇰🇿"
        "VND"         -> "🇻🇳"
        "AUD"         -> "🇦🇺"
        "CAD"         -> "🇨🇦"
        "NZD"         -> "🇳🇿"
        "KRW"         -> "🇰🇷"
        "HKD"         -> "🇭🇰"
        "MXN"         -> "🇲🇽"
        "BRL"         -> "🇧🇷"
        "INR"         -> "🇮🇳"
        "PLN"         -> "🇵🇱"
        "CZK"         -> "🇨🇿"
        "HUF"         -> "🇭🇺"
        else          -> null
    }

    /** Icon emoji for commodity assets */
    fun commodityToEmoji(symbol: String, name: String? = null): String? {
        val sym = symbol.uppercase()
        val nm = name?.uppercase() ?: ""
        
        fun String.matches(vararg keywords: String): Boolean {
            return keywords.any { this.contains(it) }
        }

        return when {
            // Energy
            sym.matches("CL=F", "BZ=F", "OIL", "PETROL") || nm.matches("PETROL", "BRENT", "CRUDE") -> "🛢️"
            sym.matches("NG=F", "GAS", "GAZ") || nm.matches("GAZ", "NATURAL GAS") -> "🔥"
            sym.matches("RB=F", "GASOLINE", "BENZIN") || nm.matches("BENZIN", "GASOLINE") -> "⛽"
            sym.matches("HO=F", "HEATING", "YAKIT") || nm.matches("YAKIT", "HEATING") -> "🌡️"
            
            // Agriculture
            sym.matches("ZW=F", "WHEAT", "BUGDAY") || nm.matches("BUGDAY", "BUĞDAY", "WHEAT") -> "🌾"
            sym.matches("ZC=F", "CORN", "MISIR") || nm.matches("MISIR", "CORN") -> "🌽"
            sym.matches("KC=F", "COFFEE", "KAHVE") || nm.matches("KAHVE", "COFFEE") -> "☕"
            sym.matches("CC=F", "COCOA", "KAKAO") || nm.matches("KAKAO", "COCOA") -> "🍫"
            sym.matches("CT=F", "COTTON", "PAMUK") || nm.matches("PAMUK", "COTTON") -> "☁️"
            sym.matches("ZS=F", "SOYBEAN", "SOYA") || nm.matches("SOYA", "SOYBEAN") -> "🫘"
            sym.matches("SB=F", "SUGAR", "SEKER") || nm.matches("SEKER", "ŞEKER", "SUGAR") -> "🍬"
            sym.matches("LBS=F", "LUMBER", "KERESTE") || nm.matches("KERESTE", "LUMBER", "WOOD") -> "🪵"
            
            // Metals (General)
            sym.matches("HG=F", "COPPER", "BAKIR") || nm.matches("BAKIR", "COPPER") -> "🧱"
            sym.matches("ALI=F", "ALUMIN") || nm.matches("ALUMIN", "ALÜM") -> "🥈"
            sym.matches("PL=F", "PLATIN") || nm.matches("PLATIN", "PLATİN") -> "💍"
            sym.matches("PA=F", "PALAD") || nm.matches("PALAD", "PALADYUM") -> "💎"
            sym.matches("ZN=F", "ZNC", "CINKO") || nm.matches("CINK", "ÇİNK", "ÇINK") -> "🔩"
            
            else -> null
        }
    }
}
