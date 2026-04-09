package com.example.cuzdan.data.remote.api

import com.example.cuzdan.data.remote.model.TefasHistoryResponse
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.POST

interface TefasApi {
    @FormUrlEncoded
    @POST("api/DB/BindHistoryInfo")
    suspend fun getFundHistory(
        @Field("fontip") fundType: String,
        @Field("fonkod") fundCode: String,
        @Field("bastarih") startDate: String,
        @Field("bittarih") endDate: String,
        @Field("sfontur") sFontur: String = "",
        @Field("fongrup") fonGrup: String = "",
        @Field("fonturkod") fonTurKod: String = "",
        @Field("fonunvantip") fonUnvanTip: String = "",
        @Field("kurucukod") kurucuKod: String = ""
    ): List<TefasHistoryResponse>
}
