package com.example.springjpa.domain.model

import java.math.BigDecimal

data class Dish(
    val id: Long,
    val name: String,
    val description: String,
    val price: BigDecimal,
    val isAvailable: Boolean,
)
