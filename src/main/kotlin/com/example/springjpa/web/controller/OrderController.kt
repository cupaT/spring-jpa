package com.example.springjpa.web.controller

import com.example.springjpa.application.service.OrderService
import com.example.springjpa.application.port.UserRepositoryPort
import com.example.springjpa.domain.model.OrderStatus
import com.example.springjpa.security.AuthenticatedUser
import com.example.springjpa.web.dto.OrderCreateRequest
import com.example.springjpa.web.dto.OrderResponse
import com.example.springjpa.web.dto.OrderStatusUpdateRequest
import com.example.springjpa.web.dto.toResponse
import jakarta.validation.Valid
import jakarta.validation.constraints.Min
import org.springframework.http.ResponseEntity
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.validation.annotation.Validated
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
@Validated
class OrderController(
    private val orderService: OrderService,
    private val userRepositoryPort: UserRepositoryPort,
) {
    @PostMapping
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    fun createOrder(
        @Valid @RequestBody request: OrderCreateRequest,
        @AuthenticationPrincipal principal: UserDetails,
    ): ResponseEntity<OrderResponse> =
        ResponseEntity.status(201).body(orderService.create(currentUserId(principal), request.dishIds).toResponse())

    @GetMapping("/{id}")
    @PreAuthorize("@orderSecurity.canViewOrder(#id, authentication)")
    fun getOrderById(@PathVariable @Min(1) id: Long): OrderResponse = orderService.getById(id).toResponse()

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    fun listOrders(
        @RequestParam(required = false) @Min(1) userId: Long?,
        @RequestParam(required = false) status: OrderStatus?,
    ): List<OrderResponse> = orderService.list(userId, status).map { it.toResponse() }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasRole('ADMIN')")
    fun updateOrderStatus(
        @PathVariable @Min(1) id: Long,
        @Valid @RequestBody request: OrderStatusUpdateRequest,
    ): OrderResponse = orderService.updateStatus(id, request.status).toResponse()

    private fun currentUserId(principal: UserDetails): Long =
        when (principal) {
            is AuthenticatedUser -> principal.id
            else -> userRepositoryPort.findByEmail(principal.username)?.id
        } ?: throw AccessDeniedException("Access denied")
}
