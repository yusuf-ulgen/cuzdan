package com.yusufulgen.cuzdan.data.remote.model

import com.google.gson.annotations.SerializedName

data class TefasHistoryResponse(
    @SerializedName("FONUNVAN")
    val fundName: String? = null,
    @SerializedName("FIYAT")
    val price: Any? = null,
    @SerializedName("TARIH")
    val date: Long? = null
)

