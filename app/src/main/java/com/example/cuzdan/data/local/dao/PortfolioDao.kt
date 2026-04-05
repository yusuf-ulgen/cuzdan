package com.example.cuzdan.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.cuzdan.data.local.entity.Portfolio
import kotlinx.coroutines.flow.Flow

@Dao
interface PortfolioDao {
    @Query("SELECT * FROM portfolios")
    fun getAllPortfolios(): Flow<List<Portfolio>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPortfolio(portfolio: Portfolio): Long

    @androidx.room.Update
    suspend fun updatePortfolio(portfolio: Portfolio)

    @Delete
    suspend fun deletePortfolio(portfolio: Portfolio)

    @Query("SELECT * FROM portfolios WHERE id = :id")
    suspend fun getPortfolioById(id: Long): Portfolio?

    @Query("SELECT * FROM portfolios WHERE isIncludedInTotal = 1")
    fun getIncludedPortfolios(): Flow<List<Portfolio>>

    @Query("SELECT * FROM portfolios WHERE isIncludedInTotal = 1")
    suspend fun getIncludedPortfoliosOnce(): List<Portfolio>

    @Query("DELETE FROM portfolios")
    suspend fun deleteAllPortfolios()
}
