package com.example.cuzdan.data.repository

import com.example.cuzdan.data.local.dao.PortfolioDao
import com.example.cuzdan.data.local.entity.Portfolio
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PortfolioRepository @Inject constructor(
    private val portfolioDao: PortfolioDao
) {
    fun getAllPortfolios(): Flow<List<Portfolio>> {
        return portfolioDao.getAllPortfolios()
    }

    suspend fun getOrCreateDefaultPortfolioId(): Long {
        val all = portfolioDao.getAllPortfolios().first()
        return if (all.isEmpty()) {
            portfolioDao.insertPortfolio(Portfolio(name = "Ana Portföy"))
        } else {
            all.first().id
        }
    }

    suspend fun insertPortfolio(portfolio: Portfolio): Long {
        return portfolioDao.insertPortfolio(portfolio)
    }

    suspend fun updatePortfolio(portfolio: Portfolio) {
        portfolioDao.updatePortfolio(portfolio)
    }

    suspend fun deletePortfolio(portfolio: Portfolio) {
        portfolioDao.deletePortfolio(portfolio)
    }
    
    suspend fun getPortfolioById(id: Long): Portfolio? {
        return portfolioDao.getPortfolioById(id)
    }

    fun getIncludedPortfolios(): Flow<List<Portfolio>> {
        return portfolioDao.getIncludedPortfolios()
    }
}
