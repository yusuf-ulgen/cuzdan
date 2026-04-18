package com.yusufulgen.cuzdan.data.remote.model

import com.google.gson.annotations.SerializedName

data class BinanceTickerResponse(
    @SerializedName("symbol")
    val symbol: String,
    @SerializedName("lastPrice")
    val lastPrice: String,
    @SerializedName("priceChangePercent")
    val priceChangePercent: String
)
