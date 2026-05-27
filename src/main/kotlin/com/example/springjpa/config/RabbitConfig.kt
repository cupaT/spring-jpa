package com.example.springjpa.config

import org.springframework.amqp.core.Binding
import org.springframework.amqp.core.BindingBuilder
import org.springframework.amqp.core.Queue
import org.springframework.amqp.core.QueueBuilder
import org.springframework.amqp.core.TopicExchange
import org.springframework.amqp.rabbit.connection.ConnectionFactory
import org.springframework.amqp.rabbit.core.RabbitTemplate
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter
import org.springframework.amqp.support.converter.MessageConverter
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class RabbitConfig {
    companion object {
        const val EXCHANGE = "order.exchange"
        const val ORDER_STATUS_QUEUE = "order.status.changed.queue"
        const val ORDER_STATUS_DLQ = "order.status.changed.dlq"
        const val ORDER_STATUS_ROUTING_KEY = "order.status.changed"
        const val ORDER_CREATED_QUEUE = "order.created.queue"
        const val ORDER_CREATED_ROUTING_KEY = "order.created"
    }

    @Bean
    fun orderExchange(): TopicExchange = TopicExchange(EXCHANGE)

    @Bean
    fun orderStatusQueue(): Queue =
        QueueBuilder.durable(ORDER_STATUS_QUEUE)
            .withArgument("x-dead-letter-exchange", "")
            .withArgument("x-dead-letter-routing-key", ORDER_STATUS_DLQ)
            .build()

    @Bean
    fun orderStatusDlq(): Queue = QueueBuilder.durable(ORDER_STATUS_DLQ).build()

    @Bean
    fun orderCreatedQueue(): Queue = QueueBuilder.durable(ORDER_CREATED_QUEUE).build()

    @Bean
    fun orderStatusBinding(): Binding =
        BindingBuilder.bind(orderStatusQueue())
            .to(orderExchange())
            .with(ORDER_STATUS_ROUTING_KEY)

    @Bean
    fun orderCreatedBinding(): Binding =
        BindingBuilder.bind(orderCreatedQueue())
            .to(orderExchange())
            .with(ORDER_CREATED_ROUTING_KEY)

    @Bean
    fun messageConverter(): MessageConverter = Jackson2JsonMessageConverter()

    @Bean
    fun rabbitTemplate(
        connectionFactory: ConnectionFactory,
        messageConverter: MessageConverter,
    ): RabbitTemplate = RabbitTemplate(connectionFactory).apply {
        this.messageConverter = messageConverter
    }
}
