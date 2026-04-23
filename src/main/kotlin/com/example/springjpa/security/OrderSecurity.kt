package com.example.springjpa.security

import com.example.springjpa.application.port.OrderRepositoryPort
import com.example.springjpa.application.port.UserRepositoryPort
import org.springframework.security.core.Authentication
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.stereotype.Component

@Component("orderSecurity")
class OrderSecurity(
    private val orderRepositoryPort: OrderRepositoryPort,
    private val userRepositoryPort: UserRepositoryPort,
) {
    fun canViewOrder(orderId: Long, authentication: Authentication): Boolean {
        if (authentication.authorities.any { it.authority == "ROLE_ADMIN" }) {
            return true
        }

        val currentUserId = currentUserId(authentication.principal) ?: return false
        val order = orderRepositoryPort.findById(orderId) ?: return false
        return order.userId == currentUserId
    }

    private fun currentUserId(principal: Any?): Long? =
        when (principal) {
            is AuthenticatedUser -> principal.id
            is UserDetails -> userRepositoryPort.findByEmail(principal.username)?.id
            is String -> userRepositoryPort.findByEmail(principal)?.id
            else -> null
        }
}
