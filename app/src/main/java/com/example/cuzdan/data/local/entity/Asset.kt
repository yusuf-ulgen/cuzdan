package com.example.cuzdan.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.math.BigDecimal

@Entity(tableName = "assets")
data class Asset(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val symbol: String,
    val name: String,
    val amount: BigDecimal,
    val averageBuyPrice: BigDecimal,
    val currentPrice: BigDecimal,
    val assetType: AssetType,
    val portfolioId: Long = 0 // Varsayılan portföy ID
)
