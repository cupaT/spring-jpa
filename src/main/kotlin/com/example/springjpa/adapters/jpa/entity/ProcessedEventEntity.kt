package com.example.springjpa.adapters.jpa.entity

import com.example.springjpa.domain.model.OrderStatus
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.time.LocalDateTime

@Entity
@Table(
    name = "processed_events",
    uniqueConstraints = [
        UniqueConstraint(
            name = "uk_processed_events_order_status",
            columnNames = ["order_id", "new_status"],
        ),
    ],
)
class ProcessedEventEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @Column(name = "order_id", nullable = false)
    var orderId: Long = 0,

    @Enumerated(EnumType.STRING)
    @Column(name = "new_status", nullable = false)
    var newStatus: OrderStatus = OrderStatus.PENDING,

    @Column(name = "processed_at", nullable = false)
    var processedAt: LocalDateTime = LocalDateTime.now(),
)
