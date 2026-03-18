package com.example.cuzdan.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.cuzdan.data.local.entity.Asset
import com.example.cuzdan.data.local.entity.AssetType
import kotlinx.coroutines.flow.Flow

@Dao
interface AssetDao {
    @Query("SELECT * FROM assets")
    fun getAllAssets(): Flow<List<Asset>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAsset(asset: Asset)

    @Update
    suspend fun updateAsset(asset: Asset)

    @Delete
    suspend fun deleteAsset(asset: Asset)

    @Query("SELECT * FROM assets WHERE symbol = :symbol LIMIT 1")
    suspend fun getAssetBySymbol(symbol: String): Asset?

    @Query("SELECT * FROM assets WHERE symbol = :symbol AND portfolioId = :portfolioId LIMIT 1")
    suspend fun getAssetBySymbolAndPortfolioId(symbol: String, portfolioId: Long): Asset?

    @Query("SELECT * FROM assets WHERE assetType IN (:types)")
    fun getAssetsByTypes(types: List<AssetType>): Flow<List<Asset>>

    @Query("SELECT * FROM assets WHERE portfolioId = :portfolioId")
    fun getAssetsByPortfolioId(portfolioId: Long): Flow<List<Asset>>

    @Query("DELETE FROM assets")
    suspend fun deleteAllAssets()
}
