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
    fun commodityToEmoji(symbol: String): String? = when (symbol.uppercase()) {
        "GC=F", "GOLD"      -> "🥇"
        "GRAM_ALTIN"        -> "🏅"
        "SI=F", "SILVER"    -> "🥈"
        "HG=F"              -> "🟤"   // Copper (brown)
        "PL=F"              -> "⬜"   // Platinum (white)
        "PA=F"              -> "🔲"   // Palladium
        "CL=F"              -> "🛢️"   // Oil
        "NG=F"              -> "🔥"   // Natural Gas
        "ZW=F", "ZC=F"      -> "🌾"   // Wheat / Corn
        else                -> null
    }
}
