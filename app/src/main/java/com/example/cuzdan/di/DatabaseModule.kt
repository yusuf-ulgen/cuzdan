package com.example.cuzdan.di

import android.content.Context
import androidx.room.Room
import com.example.cuzdan.data.local.AppDatabase
import com.example.cuzdan.data.local.dao.AssetDao
import com.example.cuzdan.data.local.dao.PortfolioDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "cuzdan_db"
        )
        .fallbackToDestructiveMigration()
        .build()
    }

    @Provides
    fun provideAssetDao(database: AppDatabase): AssetDao {
        return database.assetDao()
    }

    @Provides
    fun providePortfolioDao(database: AppDatabase): PortfolioDao {
        return database.portfolioDao()
    }
}
