package com.yusufulgen.cuzdan.data.remote.api

import com.yusufulgen.cuzdan.data.remote.model.BinancePriceResponse
import com.yusufulgen.cuzdan.data.remote.model.BinanceTickerResponse
import retrofit2.http.GET
import retrofit2.http.Query

interface BinanceApi {
    @GET("api/v3/ticker/24hr")
    suspend fun getTicker(
        @Query("symbol") symbol: String
    ): BinanceTickerResponse

    @GET("api/v3/ticker/24hr")
    suspend fun getAllTickers(): List<BinanceTickerResponse>
}
