package com.example.springjpa.adapters.jpa

import com.example.springjpa.adapters.jpa.entity.toDomain
import com.example.springjpa.adapters.jpa.entity.toEntity
import com.example.springjpa.adapters.jpa.repository.RestaurantJpaRepository
import com.example.springjpa.application.port.RestaurantRepositoryPort
import com.example.springjpa.domain.model.Restaurant
import org.springframework.stereotype.Repository

@Repository
class RestaurantJpaRepositoryAdapter(
    private val restaurantJpaRepository: RestaurantJpaRepository,
) : RestaurantRepositoryPort {
    override fun create(restaurant: Restaurant): Restaurant =
        restaurantJpaRepository.save(restaurant.toEntity()).toDomain()

    override fun findById(id: Long): Restaurant? = restaurantJpaRepository.findById(id).orElse(null)?.toDomain()

    override fun existsByName(name: String): Boolean = restaurantJpaRepository.existsByNameIgnoreCase(name)

    override fun findAll(): List<Restaurant> = restaurantJpaRepository.findAll().map { it.toDomain() }

    override fun update(restaurant: Restaurant): Restaurant? {
        if (!restaurantJpaRepository.existsById(restaurant.id)) {
            return null
        }
        return restaurantJpaRepository.save(restaurant.toEntity()).toDomain()
    }

    override fun deleteById(id: Long): Boolean {
        if (!restaurantJpaRepository.existsById(id)) {
            return false
        }
        restaurantJpaRepository.deleteById(id)
        return true
    }
}
