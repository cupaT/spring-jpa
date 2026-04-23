package com.example.springjpa.domain.model

import java.time.LocalDateTime

data class Order(
    val id: Long,
    val userId: Long,
    val status: OrderStatus,
    val createdAt: LocalDateTime,
    val dishes: List<Dish>,
)
