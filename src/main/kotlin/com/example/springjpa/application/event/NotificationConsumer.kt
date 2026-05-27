package com.example.springjpa.application.event

import com.example.springjpa.adapters.jpa.entity.ProcessedEventEntity
import com.example.springjpa.adapters.jpa.repository.ProcessedEventJpaRepository
import com.example.springjpa.application.service.NotificationService
import com.example.springjpa.config.RabbitConfig
import com.example.springjpa.domain.event.OrderCreatedEvent
import com.example.springjpa.domain.event.OrderStatusChangedEvent
import com.rabbitmq.client.Channel
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.amqp.core.Message
import org.springframework.amqp.rabbit.annotation.RabbitListener
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class NotificationConsumer(
    private val notificationService: NotificationService,
    private val processedEventJpaRepository: ProcessedEventJpaRepository,
) {
    private val logger = KotlinLogging.logger {}

    @RabbitListener(queues = [RabbitConfig.ORDER_STATUS_QUEUE])
    @Transactional
    fun handleOrderStatusChanged(
        event: OrderStatusChangedEvent,
        message: Message,
        channel: Channel,
    ) {
        val deliveryTag = message.messageProperties.deliveryTag
        try {
            if (processedEventJpaRepository.existsByOrderIdAndNewStatus(event.orderId, event.newStatus)) {
                logger.warn { "Duplicate status event for order ${event.orderId} and status ${event.newStatus}, skipping" }
                channel.basicAck(deliveryTag, false)
                return
            }

            logger.info { "Processing status event: order ${event.orderId} -> ${event.newStatus}" }
            notificationService.sendOrderStatusUpdate(event)
            processedEventJpaRepository.save(
                ProcessedEventEntity(
                    orderId = event.orderId,
                    newStatus = event.newStatus,
                ),
            )
            channel.basicAck(deliveryTag, false)
        } catch (exception: DataIntegrityViolationException) {
            logger.warn(exception) { "Duplicate status event detected while saving processed marker for order ${event.orderId}" }
            channel.basicAck(deliveryTag, false)
        } catch (exception: Exception) {
            logger.error(exception) { "Failed to process status event for order ${event.orderId}" }
            channel.basicNack(deliveryTag, false, false)
        }
    }

    @RabbitListener(queues = [RabbitConfig.ORDER_CREATED_QUEUE])
    fun handleOrderCreated(
        event: OrderCreatedEvent,
        message: Message,
        channel: Channel,
    ) {
        val deliveryTag = message.messageProperties.deliveryTag
        try {
            logger.info {
                "Processing created event: order ${event.orderId}, user ${event.userId}, dishes=${event.dishIds.size}"
            }
            channel.basicAck(deliveryTag, false)
        } catch (exception: Exception) {
            logger.error(exception) { "Failed to process created event for order ${event.orderId}" }
            channel.basicNack(deliveryTag, false, false)
        }
    }
}
