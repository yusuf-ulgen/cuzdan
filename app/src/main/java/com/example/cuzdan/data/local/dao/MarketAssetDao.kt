package com.example.cuzdan.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.cuzdan.data.local.entity.AssetType
import com.example.cuzdan.data.local.entity.MarketAsset

@Dao
interface MarketAssetDao {
    @Query("SELECT * FROM market_assets WHERE assetType = :type ORDER BY name ASC")
    fun getMarketAssetsByType(type: AssetType): kotlinx.coroutines.flow.Flow<List<MarketAsset>>

    @Query("SELECT * FROM market_assets WHERE assetType = :type ORDER BY name ASC")
    suspend fun getMarketAssetsByTypeOnce(type: AssetType): List<MarketAsset>


    @Query("SELECT * FROM market_assets WHERE (symbol LIKE '%' || :query || '%' OR name LIKE '%' || :query || '%') AND assetType = :type LIMIT 50")
    fun searchMarketAssets(query: String, type: AssetType): kotlinx.coroutines.flow.Flow<List<MarketAsset>>


    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMarketAssets(assets: List<MarketAsset>)

    @Query("DELETE FROM market_assets WHERE assetType = :type")
    suspend fun deleteMarketAssetsByType(type: AssetType)
    
    @Query("SELECT COUNT(*) FROM market_assets WHERE assetType = :type")
    suspend fun getCountByType(type: AssetType): Int

    @Query("SELECT * FROM market_assets WHERE symbol = :symbol AND assetType = :type LIMIT 1")
    suspend fun getMarketAssetBySymbolAndTypeOnce(symbol: String, type: AssetType): MarketAsset?

    @Query("UPDATE market_assets SET isFavorite = :isFav WHERE symbol = :symbol AND assetType = :type")
    suspend fun updateFavorite(symbol: String, type: AssetType, isFav: Boolean)

    @Query("SELECT * FROM market_assets WHERE assetType = :type AND isFavorite = 1 ORDER BY name ASC")
    fun getFavoritesByType(type: AssetType): kotlinx.coroutines.flow.Flow<List<MarketAsset>>

    @Query("SELECT * FROM market_assets WHERE symbol = :symbol LIMIT 1")
    fun getMarketAssetBySymbol(symbol: String): kotlinx.coroutines.flow.Flow<MarketAsset?>
}

