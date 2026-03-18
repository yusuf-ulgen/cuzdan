package com.example.cuzdan.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.example.cuzdan.data.local.converter.BigDecimalConverter
import com.example.cuzdan.data.local.dao.AssetDao
import com.example.cuzdan.data.local.dao.PortfolioDao
import com.example.cuzdan.data.local.entity.Asset
import com.example.cuzdan.data.local.entity.Portfolio
import com.example.cuzdan.data.local.entity.MarketAsset
import com.example.cuzdan.data.local.dao.MarketAssetDao


@Database(entities = [Asset::class, Portfolio::class, MarketAsset::class], version = 6, exportSchema = false)

@TypeConverters(BigDecimalConverter::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun assetDao(): AssetDao
    abstract fun portfolioDao(): PortfolioDao
    abstract fun marketAssetDao(): MarketAssetDao
}

