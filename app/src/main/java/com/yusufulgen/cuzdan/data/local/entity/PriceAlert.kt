package com.yusufulgen.cuzdan.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.math.BigDecimal

@Entity(tableName = "price_alerts")
data class PriceAlert(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val symbol: String,
    val name: String,
    val assetType: AssetType,
    val targetPrice: BigDecimal,
    val condition: PriceAlertCondition, // "ABOVE" or "BELOW"
    val isEnabled: Boolean = true,
    val isTriggered: Boolean = false,
    val baselinePrice: BigDecimal? = null,
    val createdAt: Long = System.currentTimeMillis()
)

enum class PriceAlertCondition {
    ABOVE, EQUALS, BELOW
}
