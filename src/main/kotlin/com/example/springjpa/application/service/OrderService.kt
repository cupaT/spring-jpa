package com.example.springjpa.application.service

import com.example.springjpa.application.exception.InvalidOrderStateException
import com.example.springjpa.application.exception.NotFoundException
import com.example.springjpa.application.port.DishRepositoryPort
import com.example.springjpa.application.port.OrderRepositoryPort
import com.example.springjpa.application.port.UserRepositoryPort
import com.example.springjpa.domain.model.Order
import com.example.springjpa.domain.model.OrderStatus
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service

@Service
class OrderService(
    private val orderRepositoryPort: OrderRepositoryPort,
    private val userRepositoryPort: UserRepositoryPort,
    private val dishRepositoryPort: DishRepositoryPort,
) {
    private val logger = KotlinLogging.logger {}

    fun create(userId: Long, dishIds: List<Long>): Order {
        if (userRepositoryPort.findById(userId) == null) {
            logger.warn { "User with id=$userId not found while creating order" }
            throw IllegalArgumentException("User with id=$userId not found")
        }

        val uniqueDishIds = dishIds.distinct()
        val existingDishes = dishRepositoryPort.findAllByIds(uniqueDishIds)
        if (existingDishes.size != uniqueDishIds.size) {
            logger.warn { "One or more dishes not found while creating order. requested=$uniqueDishIds" }
            throw IllegalArgumentException("One or more dishes not found")
        }

        val created = orderRepositoryPort.create(userId, uniqueDishIds, OrderStatus.PENDING)
        logger.info { "Order created: id=${created.id}, userId=${created.userId}, dishes=${uniqueDishIds.size}" }
        return created
    }

    fun getById(id: Long): Order =
        orderRepositoryPort.findById(id) ?: throw NotFoundException("Order with id=$id not found").also {
            logger.warn { "Order with id=$id not found" }
        }

    fun list(userId: Long?, status: OrderStatus?): List<Order> = orderRepositoryPort.findAll(userId, status)

    fun updateStatus(id: Long, newStatus: OrderStatus): Order {
        val existing = orderRepositoryPort.findById(id) ?: throw NotFoundException("Order with id=$id not found").also {
            logger.warn { "Order with id=$id not found for status update" }
        }
        if (!isTransitionAllowed(existing.status, newStatus)) {
            throw InvalidOrderStateException("Invalid status transition: ${existing.status} -> $newStatus")
        }
        val updated = orderRepositoryPort.updateStatus(id, newStatus)
            ?: throw NotFoundException("Order with id=$id not found").also {
                logger.warn { "Order with id=$id not found during status update write" }
            }
        logger.info { "Order status updated: id=$id, from=${existing.status}, to=${updated.status}" }
        return updated
    }

    private fun isTransitionAllowed(current: OrderStatus, next: OrderStatus): Boolean =
        when (current) {
            OrderStatus.PENDING -> next == OrderStatus.PENDING || next == OrderStatus.CONFIRMED || next == OrderStatus.CANCELLED
            OrderStatus.CONFIRMED -> next == OrderStatus.CONFIRMED || next == OrderStatus.DELIVERED || next == OrderStatus.CANCELLED
            OrderStatus.DELIVERED -> next == OrderStatus.DELIVERED
            OrderStatus.CANCELLED -> next == OrderStatus.CANCELLED
        }
}
