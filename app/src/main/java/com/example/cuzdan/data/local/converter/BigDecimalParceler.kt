package com.example.cuzdan.data.local.converter

import android.os.Parcel
import kotlinx.parcelize.Parceler
import java.math.BigDecimal

object BigDecimalParceler : Parceler<BigDecimal> {
    override fun create(parcel: Parcel): BigDecimal {
        return BigDecimal(parcel.readString() ?: "0")
    }

    override fun BigDecimal.write(parcel: Parcel, flags: Int) {
        parcel.writeString(this.toPlainString())
    }
}
