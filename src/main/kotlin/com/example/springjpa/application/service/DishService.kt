package com.example.springjpa.application.service

import com.example.springjpa.application.exception.NotFoundException
import com.example.springjpa.application.port.DishRepositoryPort
import com.example.springjpa.application.port.RestaurantRepositoryPort
import com.example.springjpa.domain.model.Dish
import org.springframework.stereotype.Service

@Service
class DishService(
    private val dishRepositoryPort: DishRepositoryPort,
    private val restaurantRepositoryPort: RestaurantRepositoryPort,
) {
    fun list(namePart: String?): List<Dish> {
        val normalized = namePart?.trim()?.takeIf { it.isNotBlank() }
        return dishRepositoryPort.findAll(normalized)
    }

    fun listByRestaurantId(restaurantId: Long): List<Dish> {
        if (restaurantRepositoryPort.findById(restaurantId) == null) {
            throw NotFoundException("Restaurant with id=$restaurantId not found")
        }
        return dishRepositoryPort.findAllByRestaurantId(restaurantId)
    }

    fun getById(id: Long): Dish =
        dishRepositoryPort.findById(id) ?: throw NotFoundException("Dish with id=$id not found")

    fun createInRestaurant(restaurantId: Long, dish: Dish): Dish {
        if (restaurantRepositoryPort.findById(restaurantId) == null) {
            throw NotFoundException("Restaurant with id=$restaurantId not found")
        }
        return dishRepositoryPort.create(dish.copy(id = 0, restaurantId = restaurantId))
    }

    fun update(id: Long, dish: Dish): Dish {
        val existing = dishRepositoryPort.findById(id) ?: throw NotFoundException("Dish with id=$id not found")
        val updated = dishRepositoryPort.update(
            dish.copy(
                id = id,
                restaurantId = existing.restaurantId,
            )
        )
        return updated ?: throw NotFoundException("Dish with id=$id not found")
    }

    fun delete(id: Long) {
        if (!dishRepositoryPort.deleteById(id)) {
            throw NotFoundException("Dish with id=$id not found")
        }
    }
}
