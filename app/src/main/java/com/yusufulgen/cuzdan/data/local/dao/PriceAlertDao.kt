package com.yusufulgen.cuzdan.data.local.dao

import androidx.room.*
import com.yusufulgen.cuzdan.data.local.entity.PriceAlert
import kotlinx.coroutines.flow.Flow

@Dao
interface PriceAlertDao {
    @Query("SELECT * FROM price_alerts ORDER BY createdAt DESC")
    fun getAllAlerts(): Flow<List<PriceAlert>>

    @Query("SELECT * FROM price_alerts WHERE isEnabled = 1 AND isTriggered = 0")
    suspend fun getActiveAlerts(): List<PriceAlert>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAlert(alert: PriceAlert)

    @Update
    suspend fun updateAlert(alert: PriceAlert)

    @Delete
    suspend fun deleteAlert(alert: PriceAlert)

    @Query("UPDATE price_alerts SET isTriggered = 1 WHERE id = :alertId")
    suspend fun markAsTriggered(alertId: Long)

    @Query("SELECT * FROM price_alerts WHERE symbol = :symbol AND assetType = :assetType")
    fun getAlertsForAsset(symbol: String, assetType: com.yusufulgen.cuzdan.data.local.entity.AssetType): Flow<List<PriceAlert>>
}
