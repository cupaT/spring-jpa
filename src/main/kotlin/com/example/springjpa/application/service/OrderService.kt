package com.example.springjpa.application.service

import com.example.springjpa.application.exception.NotFoundException
import com.example.springjpa.application.port.DishRepositoryPort
import com.example.springjpa.application.port.OrderRepositoryPort
import com.example.springjpa.application.port.UserRepositoryPort
import com.example.springjpa.domain.model.Order
import com.example.springjpa.domain.model.OrderStatus
import org.springframework.stereotype.Service

@Service
class OrderService(
    private val orderRepositoryPort: OrderRepositoryPort,
    private val userRepositoryPort: UserRepositoryPort,
    private val dishRepositoryPort: DishRepositoryPort,
) {
    fun create(userId: Long, dishIds: List<Long>): Order {
        if (dishIds.isEmpty()) {
            throw IllegalArgumentException("dishIds must not be empty")
        }
        if (userRepositoryPort.findById(userId) == null) {
            throw IllegalArgumentException("User with id=$userId not found")
        }

        val uniqueDishIds = dishIds.distinct()
        val existingDishes = dishRepositoryPort.findAllByIds(uniqueDishIds)
        if (existingDishes.size != uniqueDishIds.size) {
            throw IllegalArgumentException("One or more dishes not found")
        }

        return orderRepositoryPort.create(userId, uniqueDishIds, OrderStatus.PENDING)
    }

    fun getById(id: Long): Order =
        orderRepositoryPort.findById(id) ?: throw NotFoundException("Order with id=$id not found")

    fun list(userId: Long?, status: OrderStatus?): List<Order> = orderRepositoryPort.findAll(userId, status)

    fun updateStatus(id: Long, newStatus: OrderStatus): Order {
        val existing = orderRepositoryPort.findById(id) ?: throw NotFoundException("Order with id=$id not found")
        if (!isTransitionAllowed(existing.status, newStatus)) {
            throw IllegalArgumentException("Invalid status transition: ${existing.status} -> $newStatus")
        }
        return orderRepositoryPort.updateStatus(id, newStatus)
            ?: throw NotFoundException("Order with id=$id not found")
    }

    private fun isTransitionAllowed(current: OrderStatus, next: OrderStatus): Boolean =
        when (current) {
            OrderStatus.PENDING -> next == OrderStatus.PENDING || next == OrderStatus.CONFIRMED || next == OrderStatus.CANCELLED
            OrderStatus.CONFIRMED -> next == OrderStatus.CONFIRMED || next == OrderStatus.DELIVERED || next == OrderStatus.CANCELLED
            OrderStatus.DELIVERED -> next == OrderStatus.DELIVERED
            OrderStatus.CANCELLED -> next == OrderStatus.CANCELLED
        }
}
