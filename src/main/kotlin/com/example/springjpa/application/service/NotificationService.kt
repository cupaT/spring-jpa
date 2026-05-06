package com.example.springjpa.application.service

import com.example.springjpa.domain.model.OrderStatus
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.springframework.mail.SimpleMailMessage
import org.springframework.mail.javamail.JavaMailSender
import org.springframework.stereotype.Service

@Service
class NotificationService(
    private val mailSender: JavaMailSender,
    private val scope: CoroutineScope,
) {
    private val logger = KotlinLogging.logger {}

    fun sendOrderStatusUpdate(to: String, orderId: Long, status: OrderStatus) {
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    mailSender.send(
                        SimpleMailMessage().apply {
                            setTo(to)
                            subject = "Order #$orderId: status updated"
                            text = "Your order #$orderId status changed to: $status"
                        },
                    )
                }
            }.onSuccess {
                logger.info { "Order notification sent: orderId=$orderId, to=$to, status=$status" }
            }.onFailure { ex ->
                logger.error(ex) { "Failed to send order notification: orderId=$orderId, to=$to" }
            }
        }
    }
}
