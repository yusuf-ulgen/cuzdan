package com.example.cuzdan.data.remote.api

import com.example.cuzdan.data.remote.model.TefasRequest
import com.example.cuzdan.data.remote.model.TefasResponse
import retrofit2.http.Body
import retrofit2.http.POST

interface TefasApi {
    @POST("api/main/historicalvalues")
    suspend fun getFundPrices(
        @Body request: TefasRequest
    ): List<TefasResponse>
}
