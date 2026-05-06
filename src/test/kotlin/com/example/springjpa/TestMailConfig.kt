package com.example.springjpa

import org.mockito.Mockito.mock
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary
import org.springframework.context.annotation.Profile
import org.springframework.mail.javamail.JavaMailSender

@Configuration
@Profile("test")
class TestMailConfig {
    @Bean
    @Primary
    fun testMailSender(): JavaMailSender = mock(JavaMailSender::class.java)
}
