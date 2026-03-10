package com.example.springjpa.adapters.jpa.repository

import com.example.springjpa.adapters.jpa.entity.DishEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface DishJpaRepository : JpaRepository<DishEntity, Long> {
    @EntityGraph(attributePaths = ["restaurant"])
    fun findAllByOrderByIdAsc(): List<DishEntity>

    @EntityGraph(attributePaths = ["restaurant"])
    fun findAllByRestaurantIdOrderById(restaurantId: Long): List<DishEntity>

    @EntityGraph(attributePaths = ["restaurant"])
    fun findAllByIdIn(ids: List<Long>): List<DishEntity>

    @EntityGraph(attributePaths = ["restaurant"])
    @Query(
        """
        select d
        from DishEntity d
        where lower(d.name) like lower(concat('%', :namePart, '%'))
        order by d.id asc
        """
    )
    fun searchByNamePart(@Param("namePart") namePart: String): List<DishEntity>
}
