package com.example.springjpa.web.dto

import com.example.springjpa.domain.model.Order
import com.example.springjpa.domain.model.OrderStatus
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.NotNull
import java.time.LocalDateTime

data class OrderCreateRequest(
    @field:NotNull
    val userId: Long,
    @field:NotEmpty
    val dishIds: List<Long>,
)

data class OrderStatusUpdateRequest(
    @field:NotNull
    val status: OrderStatus,
)

data class OrderResponse(
    val id: Long,
    val userId: Long,
    val status: OrderStatus,
    val createdAt: LocalDateTime,
    val dishes: List<DishResponse>,
)

fun Order.toResponse(): OrderResponse =
    OrderResponse(
        id = this.id,
        userId = this.userId,
        status = this.status,
        createdAt = this.createdAt,
        dishes = this.dishes.map { it.toResponse() },
    )
