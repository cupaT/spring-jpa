package com.example.springjpa.adapters.jpa.repository

import com.example.springjpa.adapters.jpa.entity.ProcessedEventEntity
import com.example.springjpa.domain.model.OrderStatus
import org.springframework.data.jpa.repository.JpaRepository

interface ProcessedEventJpaRepository : JpaRepository<ProcessedEventEntity, Long> {
    fun existsByOrderIdAndNewStatus(orderId: Long, newStatus: OrderStatus): Boolean
}
