package com.example.springjpa.adapters.jpa.repository

import com.example.springjpa.adapters.jpa.entity.DishEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface DishJpaRepository : JpaRepository<DishEntity, Long> {
    fun findByName(name: String): DishEntity?

    @Query(
        """
        select d
        from DishEntity d
        where (:namePart is null or trim(:namePart) = '' or lower(d.name) like lower(concat('%', :namePart, '%')))
        order by d.id asc
        """
    )
    fun searchByNamePart(@Param("namePart") namePart: String?): List<DishEntity>
}
