package com.example.cuzdan.data.remote.api

import com.example.cuzdan.data.remote.model.BinancePriceResponse
import retrofit2.http.GET
import retrofit2.http.Query

interface BinanceApi {
    @GET("api/v3/ticker/price")
    suspend fun getPrice(
        @Query("symbol") symbol: String
    ): BinancePriceResponse

    @GET("api/v3/ticker/price")
    suspend fun getAllPrices(): List<BinancePriceResponse>
}
