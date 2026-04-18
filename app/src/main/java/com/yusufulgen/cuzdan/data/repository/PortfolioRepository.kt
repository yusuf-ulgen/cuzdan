package com.yusufulgen.cuzdan.data.repository

import com.yusufulgen.cuzdan.data.local.dao.AssetDao
import com.yusufulgen.cuzdan.data.local.dao.PortfolioDao
import com.yusufulgen.cuzdan.data.local.entity.Portfolio
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import java.math.BigDecimal
import java.math.RoundingMode
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PortfolioRepository @Inject constructor(
    private val portfolioDao: PortfolioDao,
    private val assetDao: AssetDao
) {
    fun getAllPortfolios(): Flow<List<Portfolio>> {
        return portfolioDao.getAllPortfolios()
    }

    suspend fun getOrCreateDefaultPortfolioId(): Long {
        val all = portfolioDao.getAllPortfolios().first()
        // Do not auto-create a portfolio on first open. Force explicit creation in UI.
        return all.firstOrNull()?.id ?: -1L
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

    suspend fun getIncludedPortfoliosOnce(): List<Portfolio> {
        return portfolioDao.getIncludedPortfoliosOnce()
    }

    /**
     * Portföye sermaye yatır (TRY cinsinden).
     * amountInTry: Yatırılacak tutar (pozitif = yatırma, negatif = çekme)
     * Çekimde depositedAmount 0'ın altına düşmez.
     */
    suspend fun updateDepositedAmount(portfolioId: Long, amountInTry: BigDecimal) {
        val portfolio = portfolioDao.getPortfolioById(portfolioId) ?: return
        val newAmount = (portfolio.depositedAmount + amountInTry)
            .coerceAtLeast(BigDecimal.ZERO)
            .setScale(2, RoundingMode.HALF_UP)
        portfolioDao.updatePortfolio(portfolio.copy(depositedAmount = newAmount))
    }

    suspend fun clearAllData() {
        assetDao.deleteAllAssets()
        portfolioDao.deleteAllPortfolios()
    }
}

