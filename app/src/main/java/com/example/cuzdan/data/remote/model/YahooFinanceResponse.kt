package com.example.cuzdan.data.remote.model

import com.google.gson.annotations.SerializedName

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
    val meta: Meta
)

data class Meta(
    @SerializedName("regularMarketPrice")
    val regularMarketPrice: Double,
    @SerializedName("previousClose")
    val previousClose: Double,
    @SerializedName("symbol")
    val symbol: String,
    @SerializedName("currency")
    val currency: String
)

data class YahooError(
    @SerializedName("code")
    val code: String,
    @SerializedName("description")
    val description: String
)
