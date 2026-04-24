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
        "Accept: application/json, text/javascript, */*; q=0.01",
        "Accept-Language: tr-TR,tr;q=0.9,en-US;q=0.8,en;q=0.7",
        "Cache-Control: no-cache",
        "Origin: https://www.tefas.gov.tr",
        "Pragma: no-cache",
        "Referer: https://www.tefas.gov.tr/TarihselVeriler.aspx",
        "Sec-Fetch-Dest: empty",
        "Sec-Fetch-Mode: cors",
        "Sec-Fetch-Site: same-origin",
        "User-Agent: Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/123.0.0.0 Safari/537.36",
        "X-Requested-With: XMLHttpRequest"
    )
    @FormUrlEncoded
    @POST("api/DB/BindHistoryInfo")
    suspend fun getFundHistory(
        @Field("fontip") fundType: String,
        @Field("bastarih") startDate: String,
        @Field("bittarih") endDate: String,
        @Field("fonunvantip") fundTitleType: String = "",
        @Field("fonkod") fundCode: String
    ): okhttp3.ResponseBody
}
