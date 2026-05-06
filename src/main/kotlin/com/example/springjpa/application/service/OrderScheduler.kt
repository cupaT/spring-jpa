package com.example.springjpa.application.service

import com.example.springjpa.application.port.OrderRepositoryPort
import com.example.springjpa.application.port.UserRepositoryPort
import com.example.springjpa.domain.model.OrderStatus
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.LocalDateTime

@Component
class OrderScheduler(
    private val orderRepositoryPort: OrderRepositoryPort,
    private val userRepositoryPort: UserRepositoryPort,
    private val notificationService: NotificationService,
    @Value("\${app.scheduler.stuck-order-threshold-hours}") private val stuckOrderThresholdHours: Long,
) {
    private val logger = KotlinLogging.logger {}

    @Scheduled(fixedDelayString = "\${app.scheduler.stuck-order-interval-ms}")
    fun cancelStuckOrders() {
        val threshold = LocalDateTime.now().minusHours(stuckOrderThresholdHours)
        val stuckOrders = orderRepositoryPort.findByStatusAndCreatedAtBefore(OrderStatus.PREPARING, threshold)

        logger.info { "Found ${stuckOrders.size} stuck orders in status ${OrderStatus.PREPARING}" }

        stuckOrders.forEach { order ->
            val cancelled = orderRepositoryPort.updateStatus(order.id, OrderStatus.CANCELLED)
            if (cancelled == null) {
                logger.warn { "Stuck order id=${order.id} was not found during cancellation" }
                return@forEach
            }

            logger.info { "Cancelled stuck order: id=${cancelled.id}, userId=${cancelled.userId}" }
            val user = userRepositoryPort.findById(cancelled.userId)
            if (user == null) {
                logger.warn { "User with id=${cancelled.userId} not found while notifying about stuck order cancellation" }
                return@forEach
            }
            notificationService.sendOrderStatusUpdate(user.email, cancelled.id, cancelled.status)
        }
    }
}
