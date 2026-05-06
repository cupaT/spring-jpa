package com.example.springjpa.application.port

import com.example.springjpa.domain.model.Order
import com.example.springjpa.domain.model.OrderStatus
import java.time.LocalDateTime

interface OrderRepositoryPort {
    fun create(userId: Long, dishIds: List<Long>, status: OrderStatus): Order
    fun findById(id: Long): Order?
    fun findAll(userId: Long?, status: OrderStatus?): List<Order>
    fun findByStatusAndCreatedAtBefore(status: OrderStatus, createdBefore: LocalDateTime): List<Order>
    fun updateStatus(id: Long, status: OrderStatus): Order?
}
