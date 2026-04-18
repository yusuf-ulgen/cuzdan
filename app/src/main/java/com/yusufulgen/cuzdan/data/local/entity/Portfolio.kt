package com.yusufulgen.cuzdan.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.math.BigDecimal

@Entity(tableName = "portfolios")
data class Portfolio(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val isIncludedInTotal: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    // Portföye yatırılan toplam sermaye (TRY cinsinden, çekimler düşülmüş net tutar)
    val depositedAmount: BigDecimal = BigDecimal.ZERO
)
