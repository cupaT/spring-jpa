package com.example.springjpa.application.service

import com.example.springjpa.application.exception.InvalidOrderStateException
import com.example.springjpa.application.exception.NotFoundException
import com.example.springjpa.application.exception.OrderCreationException
import com.example.springjpa.application.event.OrderEventPublisher
import com.example.springjpa.application.port.DishRepositoryPort
import com.example.springjpa.application.port.OrderRepositoryPort
import com.example.springjpa.application.port.UserRepositoryPort
import com.example.springjpa.domain.event.OrderCreatedEvent
import com.example.springjpa.domain.event.OrderStatusChangedEvent
import com.example.springjpa.domain.model.Order
import com.example.springjpa.domain.model.OrderStatus
import com.example.springjpa.monitoring.TrackOrderProcessing
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service

@Service
class OrderService(
    private val orderRepositoryPort: OrderRepositoryPort,
    private val userRepositoryPort: UserRepositoryPort,
    private val dishRepositoryPort: DishRepositoryPort,
    private val orderEventPublisher: OrderEventPublisher,
) {
    private val logger = KotlinLogging.logger {}

    @TrackOrderProcessing
    fun create(userId: Long, dishIds: List<Long>): Order {
        if (dishIds.isEmpty()) {
            logger.warn { "Empty dish list while creating order for userId=$userId" }
            throw IllegalArgumentException("Dish list must not be empty")
        }

        val user = userRepositoryPort.findById(userId)
        if (user == null) {
            logger.warn { "User with id=$userId not found while creating order" }
            throw IllegalArgumentException("User with id=$userId not found")
        }

        val uniqueDishIds = dishIds.distinct()
        val existingDishes = dishRepositoryPort.findAllByIds(uniqueDishIds)
        if (existingDishes.size != uniqueDishIds.size) {
            logger.warn { "One or more dishes not found while creating order. requested=$uniqueDishIds" }
            throw IllegalArgumentException("One or more dishes not found")
        }
        if (existingDishes.any { !it.isAvailable }) {
            logger.warn { "One or more dishes unavailable while creating order. requested=$uniqueDishIds" }
            throw OrderCreationException("stock_empty", "One or more dishes are unavailable")
        }

        val created = orderRepositoryPort.create(userId, uniqueDishIds, OrderStatus.PENDING)
        logger.info { "Order created: id=${created.id}, userId=${created.userId}, dishes=${uniqueDishIds.size}" }
        orderEventPublisher.publishOrderCreated(
            OrderCreatedEvent(
                orderId = created.id,
                userId = created.userId,
                userEmail = user.email,
                dishIds = uniqueDishIds,
                createdAt = created.createdAt,
            ),
        )
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
        val user = userRepositoryPort.findById(updated.userId)
            ?: throw NotFoundException("User with id=${updated.userId} not found").also {
                logger.warn { "User with id=${updated.userId} not found for status event" }
            }
        orderEventPublisher.publishOrderStatusChanged(
            OrderStatusChangedEvent(
                orderId = updated.id,
                userId = updated.userId,
                userEmail = user.email,
                oldStatus = existing.status,
                newStatus = updated.status,
            ),
        )
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
