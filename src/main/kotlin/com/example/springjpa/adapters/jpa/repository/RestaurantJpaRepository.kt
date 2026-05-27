package com.example.springjpa.adapters.jpa.repository

import com.example.springjpa.adapters.jpa.entity.RestaurantEntity
import org.springframework.data.jpa.repository.JpaRepository

interface RestaurantJpaRepository : JpaRepository<RestaurantEntity, Long> {
    fun existsByNameIgnoreCase(name: String): Boolean
}
