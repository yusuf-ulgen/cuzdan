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
    @SerializedName("fonKod")
    val fonKod: String,
    @SerializedName("s1")
    val s1: Boolean = true,
    @SerializedName("s2")
    val s2: Boolean = true,
    @SerializedName("s3")
    val s3: Boolean = true,
    @SerializedName("dil")
    val dil: String = "TR",
    @SerializedName("periyod")
    val periyod: String = "1"
)

