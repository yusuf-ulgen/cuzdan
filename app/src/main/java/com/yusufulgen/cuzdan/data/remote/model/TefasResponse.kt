package com.yusufulgen.cuzdan.data.remote.model

import com.google.gson.annotations.SerializedName

data class TefasRequest(
    @SerializedName("fontipi")
    val fundType: String,
    @SerializedName("tarih")
    val date: String
)

data class TefasResponse(
    @SerializedName("Fontip")
    val fundType: String?,
    @SerializedName("FonunAd")
    val fundName: String?,
    @SerializedName("Fiyat")
    val price: Any?,
    @SerializedName("Tarih")
    val date: String?
)
