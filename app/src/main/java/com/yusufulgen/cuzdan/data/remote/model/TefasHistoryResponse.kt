package com.yusufulgen.cuzdan.data.remote.model

import com.google.gson.annotations.SerializedName

data class TefasNewHistoryResponse(
    @SerializedName("tarih")
    val tarih: String? = null,
    @SerializedName("fonKodu")
    val fonKodu: String? = null,
    @SerializedName("fonUnvan")
    val fundName: String? = null,
    @SerializedName("fiyat")
    val price: Double? = null
)

data class TefasNewWrapper(
    @SerializedName("resultList")
    val resultList: List<TefasNewHistoryResponse>? = null
)

data class TefasNewRequest(
    val fonKodu: String,
    val dil: String = "TR",
    val periyod: Int = 1
)

