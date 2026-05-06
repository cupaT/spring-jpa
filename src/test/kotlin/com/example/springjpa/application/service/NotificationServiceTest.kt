package com.example.springjpa.application.service

import com.example.springjpa.domain.model.OrderStatus
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentMatchers.any
import org.mockito.Mock
import org.mockito.Mockito.doThrow
import org.mockito.Mockito.verify
import org.mockito.junit.jupiter.MockitoExtension
import org.springframework.mail.SimpleMailMessage
import org.springframework.mail.javamail.JavaMailSender

@ExtendWith(MockitoExtension::class)
class NotificationServiceTest {
    @Mock
    lateinit var mailSender: JavaMailSender

    @Test
    fun `sendOrderStatusUpdate sends simple mail message`() = runBlocking {
        val service = NotificationService(mailSender, this)

        service.sendOrderStatusUpdate("user@example.com", 10, OrderStatus.PREPARING)
    }.also {
        verify(mailSender).send(any(SimpleMailMessage::class.java))
    }

    @Test
    fun `sendOrderStatusUpdate does not throw when mail sender fails`() = runBlocking {
        doThrow(RuntimeException("SMTP is unavailable")).`when`(mailSender).send(any(SimpleMailMessage::class.java))
        val service = NotificationService(mailSender, this)

        service.sendOrderStatusUpdate("user@example.com", 10, OrderStatus.CANCELLED)
    }.also {
        verify(mailSender).send(any(SimpleMailMessage::class.java))
    }
}
