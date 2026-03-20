package com.example.cuzdan.di

import android.content.Context
import androidx.room.Room
import com.example.cuzdan.data.local.AppDatabase
import com.example.cuzdan.data.local.dao.AssetDao
import com.example.cuzdan.data.local.dao.PortfolioDao
import com.example.cuzdan.data.local.dao.MarketAssetDao
import com.example.cuzdan.data.local.dao.PortfolioHistoryDao

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import javax.inject.Inject
import javax.inject.Singleton

val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE assets ADD COLUMN currency TEXT NOT NULL DEFAULT 'TRY'")
        db.execSQL("ALTER TABLE market_assets ADD COLUMN currency TEXT NOT NULL DEFAULT 'TRY'")
    }
}

val MIGRATION_8_9 = object : Migration(8, 9) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE portfolios ADD COLUMN createdAt INTEGER NOT NULL DEFAULT " + System.currentTimeMillis())
    }
}

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
        .addMigrations(MIGRATION_5_6, MIGRATION_8_9)
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

    @Provides
    fun provideMarketAssetDao(database: AppDatabase): MarketAssetDao {
        return database.marketAssetDao()
    }

    @Provides
    fun providePortfolioHistoryDao(database: AppDatabase): PortfolioHistoryDao {
        return database.portfolioHistoryDao()
    }
}

