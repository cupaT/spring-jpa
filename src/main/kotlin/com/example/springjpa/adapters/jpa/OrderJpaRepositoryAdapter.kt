package com.example.springjpa.adapters.jpa

import com.example.springjpa.adapters.jpa.entity.OrderEntity
import com.example.springjpa.adapters.jpa.entity.toDomain
import com.example.springjpa.adapters.jpa.repository.DishJpaRepository
import com.example.springjpa.adapters.jpa.repository.OrderJpaRepository
import com.example.springjpa.adapters.jpa.repository.UserJpaRepository
import com.example.springjpa.application.port.OrderRepositoryPort
import com.example.springjpa.domain.model.Order
import com.example.springjpa.domain.model.OrderStatus
import org.springframework.stereotype.Repository
import java.time.LocalDateTime

@Repository
class OrderJpaRepositoryAdapter(
    private val orderJpaRepository: OrderJpaRepository,
    private val userJpaRepository: UserJpaRepository,
    private val dishJpaRepository: DishJpaRepository,
) : OrderRepositoryPort {
    override fun create(userId: Long, dishIds: List<Long>, status: OrderStatus): Order {
        val user = userJpaRepository.findById(userId).orElseThrow {
            IllegalArgumentException("User with id=$userId not found")
        }
        val dishes = dishJpaRepository.findAllByIdIn(dishIds)
        if (dishes.size != dishIds.distinct().size) {
            throw IllegalArgumentException("One or more dishes not found")
        }

        val entity =
            OrderEntity(
                status = status,
                createdAt = LocalDateTime.now(),
                user = user,
                dishes = dishes.toMutableSet(),
            )

        val saved = orderJpaRepository.save(entity)
        return orderJpaRepository.findDetailedById(saved.id ?: 0)?.toDomain() ?: saved.toDomain()
    }

    override fun findById(id: Long): Order? = orderJpaRepository.findDetailedById(id)?.toDomain()

    override fun findAll(userId: Long?, status: OrderStatus?): List<Order> =
        orderJpaRepository.searchDetailed(userId, status).map { it.toDomain() }

    override fun findByStatusAndCreatedAtBefore(status: OrderStatus, createdBefore: LocalDateTime): List<Order> =
        orderJpaRepository.findDetailedByStatusAndCreatedAtBefore(status, createdBefore).map { it.toDomain() }

    override fun updateStatus(id: Long, status: OrderStatus): Order? {
        val existing = orderJpaRepository.findDetailedById(id) ?: return null
        existing.status = status
        orderJpaRepository.save(existing)
        return orderJpaRepository.findDetailedById(id)?.toDomain()
    }
}
