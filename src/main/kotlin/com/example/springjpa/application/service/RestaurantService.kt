package com.example.springjpa.application.service

import com.example.springjpa.application.exception.NotFoundException
import com.example.springjpa.application.port.RestaurantRepositoryPort
import com.example.springjpa.domain.model.Restaurant
import org.springframework.stereotype.Service

@Service
class RestaurantService(
    private val restaurantRepositoryPort: RestaurantRepositoryPort,
) {
    fun list(): List<Restaurant> = restaurantRepositoryPort.findAll()

    fun getById(id: Long): Restaurant =
        restaurantRepositoryPort.findById(id) ?: throw NotFoundException("Restaurant with id=$id not found")

    fun create(restaurant: Restaurant): Restaurant = restaurantRepositoryPort.create(restaurant.copy(id = 0))

    fun update(id: Long, restaurant: Restaurant): Restaurant {
        val updated = restaurantRepositoryPort.update(restaurant.copy(id = id))
        return updated ?: throw NotFoundException("Restaurant with id=$id not found")
    }

    fun delete(id: Long) {
        if (!restaurantRepositoryPort.deleteById(id)) {
            throw NotFoundException("Restaurant with id=$id not found")
        }
    }
}
