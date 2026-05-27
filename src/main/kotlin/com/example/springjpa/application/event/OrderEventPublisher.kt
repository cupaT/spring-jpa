package com.example.springjpa.application.event

import com.example.springjpa.config.RabbitConfig
import com.example.springjpa.domain.event.OrderCreatedEvent
import com.example.springjpa.domain.event.OrderStatusChangedEvent
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.amqp.AmqpException
import org.springframework.amqp.rabbit.core.RabbitTemplate
import org.springframework.stereotype.Service

@Service
class OrderEventPublisher(
    private val rabbitTemplate: RabbitTemplate,
) {
    private val logger = KotlinLogging.logger {}

    fun publishOrderCreated(event: OrderCreatedEvent) {
        publish(RabbitConfig.ORDER_CREATED_ROUTING_KEY, event) {
            "Published OrderCreated event for order ${event.orderId}"
        }
    }

    fun publishOrderStatusChanged(event: OrderStatusChangedEvent) {
        publish(RabbitConfig.ORDER_STATUS_ROUTING_KEY, event) {
            "Published OrderStatusChanged event: ${event.oldStatus} -> ${event.newStatus}"
        }
    }

    private fun publish(
        routingKey: String,
        event: Any,
        successMessage: () -> String,
    ) {
        try {
            rabbitTemplate.convertAndSend(RabbitConfig.EXCHANGE, routingKey, event)
            logger.info { successMessage() }
        } catch (exception: AmqpException) {
            logger.error(exception) { "Failed to publish order event with routingKey=$routingKey" }
        }
    }
}
