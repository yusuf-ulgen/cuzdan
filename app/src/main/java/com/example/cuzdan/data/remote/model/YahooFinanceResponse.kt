package com.example.cuzdan.data.remote.model

import com.google.gson.annotations.SerializedName
import java.math.BigDecimal


data class YahooFinanceResponse(
    @SerializedName("chart")
    val chart: Chart
)

data class Chart(
    @SerializedName("result")
    val result: List<ChartResult>?,
    @SerializedName("error")
    val error: YahooError?
)

data class ChartResult(
    @SerializedName("meta")
    val meta: Meta,
    @SerializedName("timestamp")
    val timestamp: List<Long>?,
    @SerializedName("indicators")
    val indicators: Indicators?
)

data class Indicators(
    @SerializedName("quote")
    val quote: List<Quote>?
)

data class Quote(
    @SerializedName("close")
    val close: List<BigDecimal?>?
)


data class Meta(
    @SerializedName("regularMarketPrice")
    val regularMarketPrice: BigDecimal,
    @SerializedName("previousClose")
    val previousClose: BigDecimal,
    @SerializedName("symbol")
    val symbol: String,
    @SerializedName("currency")
    val currency: String,
    @SerializedName("shortName")
    val shortName: String? = null,
    @SerializedName("longName")
    val longName: String? = null
)


data class YahooError(
    @SerializedName("code")
    val code: String,
    @SerializedName("description")
    val description: String
)
