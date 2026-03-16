package com.example.cuzdan.data.remote.model

import com.google.gson.annotations.SerializedName

data class YahooQuoteResponse(
    @SerializedName("quoteResponse") val quoteResponse: QuoteResponseWrapper
)

data class QuoteResponseWrapper(
    @SerializedName("result") val result: List<YahooQuote>? = null
)

data class YahooQuote(
    @SerializedName("symbol") val symbol: String,
    @SerializedName("regularMarketPrice") val regularMarketPrice: Double? = null,
    @SerializedName("regularMarketChangePercent") val regularMarketChangePercent: Double? = null,
    @SerializedName("shortName") val shortName: String? = null,
    @SerializedName("longName") val longName: String? = null
)
