package com.example.cuzdan.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "portfolios")
data class Portfolio(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String
)
