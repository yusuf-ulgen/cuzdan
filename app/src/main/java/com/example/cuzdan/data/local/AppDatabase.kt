package com.example.cuzdan.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.example.cuzdan.data.local.converter.BigDecimalConverter
import com.example.cuzdan.data.local.dao.AssetDao
import com.example.cuzdan.data.local.dao.PortfolioDao
import com.example.cuzdan.data.local.entity.Asset
import com.example.cuzdan.data.local.entity.Portfolio

@Database(entities = [Asset::class, Portfolio::class], version = 4, exportSchema = false)
@TypeConverters(BigDecimalConverter::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun assetDao(): AssetDao
    abstract fun portfolioDao(): PortfolioDao
}
