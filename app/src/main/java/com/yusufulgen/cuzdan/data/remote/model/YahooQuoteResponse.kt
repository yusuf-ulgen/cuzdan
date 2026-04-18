package com.yusufulgen.cuzdan.data.remote.model

import com.google.gson.annotations.SerializedName
import java.math.BigDecimal


data class YahooQuoteResponse(
    @SerializedName("quoteResponse") val quoteResponse: QuoteResponseWrapper
)

data class QuoteResponseWrapper(
    @SerializedName("result") val result: List<YahooQuote>? = null
)

data class YahooQuote(
    @SerializedName("symbol") val symbol: String,
    @SerializedName("regularMarketPrice") val regularMarketPrice: BigDecimal? = null,
    @SerializedName("regularMarketChangePercent") val regularMarketChangePercent: BigDecimal? = null,

    @SerializedName("shortName") val shortName: String? = null,
    @SerializedName("longName") val longName: String? = null,
    @SerializedName("currency") val currency: String? = null
)
