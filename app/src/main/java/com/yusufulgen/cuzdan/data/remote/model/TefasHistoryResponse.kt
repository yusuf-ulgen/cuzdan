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

data class TefasWrapper(
    @SerializedName("d")
    val data: List<TefasHistoryResponse> = emptyList()
)

data class TefasRequest(
    val fontip: String,
    val fonkod: String,
    val bastarih: String,
    val bittarih: String,
    val sfontur: String = "",
    val fongrup: String = "",
    val fonturkod: String = "",
    val fonunvantip: String = "",
    val kurucukod: String = "",
    val isin: String = "",
    val sorunlu: String = "",
    val datedes: String = "",
    val fontur: String = "",
    val unvan: String = "",
    val fankod: String = ""
)

