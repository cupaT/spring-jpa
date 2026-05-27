package com.example.springjpa.application.event

import com.example.springjpa.adapters.jpa.entity.ProcessedEventEntity
import com.example.springjpa.adapters.jpa.repository.ProcessedEventJpaRepository
import com.example.springjpa.application.service.NotificationService
import com.example.springjpa.domain.event.OrderCreatedEvent
import com.example.springjpa.domain.event.OrderStatusChangedEvent
import com.example.springjpa.domain.model.OrderStatus
import com.rabbitmq.client.Channel
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentMatchers.any
import org.mockito.Mock
import org.mockito.Mockito.doThrow
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.mockito.junit.jupiter.MockitoExtension
import org.springframework.amqp.core.Message
import org.springframework.amqp.core.MessageProperties

@ExtendWith(MockitoExtension::class)
class NotificationConsumerTest {
    @Mock
    lateinit var notificationService: NotificationService

    @Mock
    lateinit var processedEventJpaRepository: ProcessedEventJpaRepository

    @Mock
    lateinit var channel: Channel

    @Test
    fun `handleOrderStatusChanged skips duplicate event and acks message`() {
        val event = statusEvent()
        `when`(processedEventJpaRepository.existsByOrderIdAndNewStatus(10, OrderStatus.CONFIRMED)).thenReturn(true)

        consumer().handleOrderStatusChanged(event, message(), channel)

        verify(notificationService, never()).sendOrderStatusUpdate(event)
        verify(channel).basicAck(42, false)
    }

    @Test
    fun `handleOrderStatusChanged sends email saves marker and acks message`() {
        val event = statusEvent()
        `when`(processedEventJpaRepository.existsByOrderIdAndNewStatus(10, OrderStatus.CONFIRMED)).thenReturn(false)

        consumer().handleOrderStatusChanged(event, message(), channel)

        verify(notificationService).sendOrderStatusUpdate(event)
        verify(processedEventJpaRepository).save(any(ProcessedEventEntity::class.java))
        verify(channel).basicAck(42, false)
    }

    @Test
    fun `handleOrderStatusChanged nacks message to dlq on processing error`() {
        val event = statusEvent()
        `when`(processedEventJpaRepository.existsByOrderIdAndNewStatus(10, OrderStatus.CONFIRMED)).thenReturn(false)
        doThrow(IllegalStateException("mail failed")).`when`(notificationService).sendOrderStatusUpdate(event)

        consumer().handleOrderStatusChanged(event, message(), channel)

        verify(channel).basicNack(42, false, false)
    }

    @Test
    fun `handleOrderCreated logs event and acks message`() {
        val event = OrderCreatedEvent(
            orderId = 10,
            userId = 3,
            userEmail = "user3@example.com",
            dishIds = listOf(1, 2),
        )

        consumer().handleOrderCreated(event, message(), channel)

        verify(channel).basicAck(42, false)
    }

    private fun consumer() = NotificationConsumer(notificationService, processedEventJpaRepository)

    private fun statusEvent() =
        OrderStatusChangedEvent(
            orderId = 10,
            userId = 3,
            userEmail = "user3@example.com",
            oldStatus = OrderStatus.PENDING,
            newStatus = OrderStatus.CONFIRMED,
        )

    private fun message(): Message {
        val properties = MessageProperties()
        properties.deliveryTag = 42
        return Message(ByteArray(0), properties)
    }
}
