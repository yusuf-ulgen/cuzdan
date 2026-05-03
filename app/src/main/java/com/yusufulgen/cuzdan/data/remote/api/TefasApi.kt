package com.yusufulgen.cuzdan.data.remote.api

import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.POST

interface TefasApi {
    @GET("TarihselVeriler.aspx")
    suspend fun warmUpSession(): okhttp3.ResponseBody

    @Headers(
        "Accept: application/json, text/plain, */*",
        "Content-Type: application/json",
        "Origin: https://www.tefas.gov.tr",
        "Referer: https://www.tefas.gov.tr/",
        "User-Agent: Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/123.0.0.0 Safari/537.36"
    )
    @POST("api/funds/fonFiyatBilgiGetir")
    suspend fun getFundHistory(
        @retrofit2.http.Body request: com.yusufulgen.cuzdan.data.remote.model.TefasNewRequest
    ): com.yusufulgen.cuzdan.data.remote.model.TefasNewWrapper
}
