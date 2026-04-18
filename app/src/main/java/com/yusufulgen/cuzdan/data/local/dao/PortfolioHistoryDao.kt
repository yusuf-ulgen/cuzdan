package com.yusufulgen.cuzdan.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.yusufulgen.cuzdan.data.local.entity.PortfolioHistory
import kotlinx.coroutines.flow.Flow

@Dao
interface PortfolioHistoryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(history: PortfolioHistory)

    @Query("SELECT * FROM portfolio_history WHERE portfolioId = :portfolioId AND date >= :startDate AND date <= :endDate ORDER BY date ASC")
    fun getHistoryRange(portfolioId: Long, startDate: Long, endDate: Long): Flow<List<PortfolioHistory>>

    @Query("SELECT * FROM portfolio_history WHERE portfolioId = :portfolioId ORDER BY date ASC")
    fun getAllHistory(portfolioId: Long): Flow<List<PortfolioHistory>>

    @Query("SELECT * FROM portfolio_history WHERE portfolioId = :portfolioId AND date < :date ORDER BY date DESC LIMIT 1")
    suspend fun getLatestBefore(portfolioId: Long, date: Long): PortfolioHistory?
}
