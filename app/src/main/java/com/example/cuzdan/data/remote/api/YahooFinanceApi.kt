package com.example.cuzdan.data.remote.api

import com.example.cuzdan.data.remote.model.YahooFinanceResponse
import retrofit2.http.GET
import retrofit2.http.Path

interface YahooFinanceApi {
    @GET("v8/finance/chart/{symbol}")
    suspend fun getChartData(
        @Path("symbol") symbol: String
    ): YahooFinanceResponse
}
