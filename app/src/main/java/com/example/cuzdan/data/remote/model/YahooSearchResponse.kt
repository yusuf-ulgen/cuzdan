package com.example.cuzdan.data.remote.model

import com.google.gson.annotations.SerializedName

data class YahooSearchResponse(
    @SerializedName("quotes") val quotes: List<YahooSearchQuote>? = null
)

data class YahooSearchQuote(
    @SerializedName("symbol") val symbol: String,
    @SerializedName("shortname") val shortName: String? = null,
    @SerializedName("longname") val longName: String? = null,
    @SerializedName("quoteType") val quoteType: String? = null,
    @SerializedName("exchange") val exchange: String? = null,
    @SerializedName("index") val index: String? = null,
    @SerializedName("typeDisp") val typeDisp: String? = null
)
