package com.example.cuzdan.data.remote.model

import com.google.gson.annotations.SerializedName

data class BinancePriceResponse(
    @SerializedName("symbol")
    val symbol: String,
    @SerializedName("price")
    val price: String
)
