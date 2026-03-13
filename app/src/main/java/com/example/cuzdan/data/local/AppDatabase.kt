package com.example.cuzdan.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.example.cuzdan.data.local.converter.BigDecimalConverter
import com.example.cuzdan.data.local.dao.AssetDao
import com.example.cuzdan.data.local.entity.Asset

@Database(entities = [Asset::class], version = 1, exportSchema = false)
@TypeConverters(BigDecimalConverter::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun assetDao(): AssetDao
}
