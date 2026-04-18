package com.yusufulgen.cuzdan.data.local.entity

import android.os.Parcelable
import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.parcelize.Parcelize
import com.yusufulgen.cuzdan.data.local.converter.BigDecimalParceler
import kotlinx.parcelize.TypeParceler
import java.math.BigDecimal

@Parcelize
@TypeParceler<BigDecimal, BigDecimalParceler>
@Entity(tableName = "assets")
data class Asset(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val symbol: String,
    val name: String,
    val amount: BigDecimal,
    val averageBuyPrice: BigDecimal,
    val currentPrice: BigDecimal,
    val dailyChangePercentage: BigDecimal = BigDecimal.ZERO,
    val assetType: AssetType,
    val portfolioId: Long = 0, // Varsayılan portföy ID
    val currency: String = "TRY"
) : Parcelable

