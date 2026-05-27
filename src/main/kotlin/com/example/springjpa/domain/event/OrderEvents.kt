package com.example.springjpa.domain.event

import com.example.springjpa.domain.model.OrderStatus
import java.time.LocalDateTime

data class OrderCreatedEvent(
    val orderId: Long,
    val userId: Long,
    val userEmail: String,
    val dishIds: List<Long>,
    val createdAt: LocalDateTime = LocalDateTime.now(),
)

data class OrderStatusChangedEvent(
    val orderId: Long,
    val userId: Long,
    val userEmail: String,
    val oldStatus: OrderStatus,
    val newStatus: OrderStatus,
    val changedAt: LocalDateTime = LocalDateTime.now(),
)
