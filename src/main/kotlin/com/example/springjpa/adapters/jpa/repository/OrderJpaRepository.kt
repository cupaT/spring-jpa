package com.example.springjpa.adapters.jpa.repository

import com.example.springjpa.adapters.jpa.entity.OrderEntity
import com.example.springjpa.domain.model.OrderStatus
import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.LocalDateTime

interface OrderJpaRepository : JpaRepository<OrderEntity, Long> {
    @EntityGraph(attributePaths = ["user", "dishes", "dishes.restaurant"])
    @Query("select o from OrderEntity o where o.id = :id")
    fun findDetailedById(@Param("id") id: Long): OrderEntity?

    @EntityGraph(attributePaths = ["user", "dishes", "dishes.restaurant"])
    @Query(
        """
        select distinct o
        from OrderEntity o
        where (:userId is null or o.user.id = :userId)
          and (:status is null or o.status = :status)
        order by o.id asc
        """
    )
    fun searchDetailed(
        @Param("userId") userId: Long?,
        @Param("status") status: OrderStatus?,
    ): List<OrderEntity>

    @EntityGraph(attributePaths = ["user", "dishes", "dishes.restaurant"])
    @Query(
        """
        select distinct o
        from OrderEntity o
        where o.status = :status
          and o.createdAt < :createdBefore
        order by o.id asc
        """
    )
    fun findDetailedByStatusAndCreatedAtBefore(
        @Param("status") status: OrderStatus,
        @Param("createdBefore") createdBefore: LocalDateTime,
    ): List<OrderEntity>
}
