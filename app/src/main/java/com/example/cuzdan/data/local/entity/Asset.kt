package com.example.cuzdan.data.local.entity

import android.os.Parcelable
import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.parcelize.Parcelize
import java.math.BigDecimal

@Parcelize
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
    val portfolioId: Long = 0 // Varsayılan portföy ID
) : Parcelable
