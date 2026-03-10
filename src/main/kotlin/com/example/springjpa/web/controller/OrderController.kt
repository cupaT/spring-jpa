package com.example.springjpa.web.controller

import com.example.springjpa.application.service.OrderService
import com.example.springjpa.domain.model.OrderStatus
import com.example.springjpa.web.dto.OrderCreateRequest
import com.example.springjpa.web.dto.OrderResponse
import com.example.springjpa.web.dto.OrderStatusUpdateRequest
import com.example.springjpa.web.dto.toResponse
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/orders")
class OrderController(
    private val orderService: OrderService,
) {
    @PostMapping
    fun createOrder(@Valid @RequestBody request: OrderCreateRequest): ResponseEntity<OrderResponse> =
        ResponseEntity.status(201).body(orderService.create(request.userId, request.dishIds).toResponse())

    @GetMapping("/{id}")
    fun getOrderById(@PathVariable id: Long): OrderResponse = orderService.getById(id).toResponse()

    @GetMapping
    fun listOrders(
        @RequestParam(required = false) userId: Long?,
        @RequestParam(required = false) status: OrderStatus?,
    ): List<OrderResponse> = orderService.list(userId, status).map { it.toResponse() }

    @PatchMapping("/{id}/status")
    fun updateOrderStatus(
        @PathVariable id: Long,
        @Valid @RequestBody request: OrderStatusUpdateRequest,
    ): OrderResponse = orderService.updateStatus(id, request.status).toResponse()
}
