package com.example.springjpa.application.service

import com.example.springjpa.domain.event.OrderStatusChangedEvent
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.mail.SimpleMailMessage
import org.springframework.mail.javamail.JavaMailSender
import org.springframework.stereotype.Service

@Service
class NotificationService(
    private val mailSender: JavaMailSender,
) {
    private val logger = KotlinLogging.logger {}

    fun sendOrderStatusUpdate(event: OrderStatusChangedEvent) {
        try {
            val message = SimpleMailMessage().apply {
                setTo(event.userEmail)
                subject = "Order #${event.orderId} status changed"
                text = "Your order status changed from ${event.oldStatus} to ${event.newStatus}."
            }
            mailSender.send(message)
            logger.info { "Status update email sent for order ${event.orderId} to ${event.userEmail}" }
        } catch (exception: Exception) {
            logger.error(exception) { "Failed to send status update email for order ${event.orderId}" }
            throw exception
        }
    }
}
