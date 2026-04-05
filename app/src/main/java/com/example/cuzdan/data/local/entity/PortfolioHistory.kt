package com.example.cuzdan.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.math.BigDecimal

@Entity(tableName = "portfolio_history")
data class PortfolioHistory(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val portfolioId: Long,
    val date: Long, // timestamp
    val totalValue: BigDecimal,
    val currency: String,
    val profitLoss: BigDecimal = BigDecimal.ZERO
)
