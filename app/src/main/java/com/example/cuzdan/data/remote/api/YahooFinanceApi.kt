package com.example.cuzdan.data.remote.api

import com.example.cuzdan.data.remote.model.YahooFinanceResponse
import com.example.cuzdan.data.remote.model.YahooQuoteResponse
import com.example.cuzdan.data.remote.model.YahooSearchResponse
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface YahooFinanceApi {
    @GET("v8/finance/chart/{symbol}")
    suspend fun getChartData(
        @Path("symbol") symbol: String,
        @Query("range") range: String = "1d",
        @Query("interval") interval: String = "1m"
    ): YahooFinanceResponse

    @GET("v1/finance/search")
    suspend fun search(
        @Query("q") query: String,
        @Query("quotesCount") quotesCount: Int = 10,
        @Query("newsCount") newsCount: Int = 0
    ): YahooSearchResponse

    @GET("v7/finance/quote")
    suspend fun getQuotes(
        @Query("symbols") symbols: String
    ): YahooQuoteResponse
}
