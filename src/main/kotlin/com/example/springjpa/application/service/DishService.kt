package com.example.springjpa.application.service

import com.example.springjpa.application.exception.NotFoundException
import com.example.springjpa.application.port.DishRepositoryPort
import com.example.springjpa.application.port.RestaurantRepositoryPort
import com.example.springjpa.domain.model.Dish
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service

@Service
class DishService(
    private val dishRepositoryPort: DishRepositoryPort,
    private val restaurantRepositoryPort: RestaurantRepositoryPort,
) {
    private val logger = KotlinLogging.logger {}

    fun list(namePart: String?): List<Dish> {
        val normalized = namePart?.trim()?.takeIf { it.isNotBlank() }
        return dishRepositoryPort.findAll(normalized)
    }

    fun listByRestaurantId(restaurantId: Long): List<Dish> {
        if (restaurantRepositoryPort.findById(restaurantId) == null) {
            logger.warn { "Restaurant with id=$restaurantId not found while loading dishes" }
            throw NotFoundException("Restaurant with id=$restaurantId not found")
        }
        return dishRepositoryPort.findAllByRestaurantId(restaurantId)
    }

    fun getById(id: Long): Dish =
        dishRepositoryPort.findById(id) ?: throw NotFoundException("Dish with id=$id not found").also {
            logger.warn { "Dish with id=$id not found" }
        }

    fun createInRestaurant(restaurantId: Long, dish: Dish): Dish {
        if (restaurantRepositoryPort.findById(restaurantId) == null) {
            logger.warn { "Restaurant with id=$restaurantId not found while creating dish" }
            throw NotFoundException("Restaurant with id=$restaurantId not found")
        }
        val created = dishRepositoryPort.create(dish.copy(id = 0, restaurantId = restaurantId))
        logger.info { "Dish created: id=${created.id}, restaurantId=$restaurantId, name='${created.name}'" }
        return created
    }

    fun update(id: Long, dish: Dish): Dish {
        val existing = dishRepositoryPort.findById(id) ?: throw NotFoundException("Dish with id=$id not found").also {
            logger.warn { "Dish with id=$id not found for update" }
        }
        val updated = dishRepositoryPort.update(
            dish.copy(
                id = id,
                restaurantId = existing.restaurantId,
            )
        )
        return updated ?: throw NotFoundException("Dish with id=$id not found").also {
            logger.warn { "Dish with id=$id not found during update write" }
        }
    }

    fun delete(id: Long) {
        if (!dishRepositoryPort.deleteById(id)) {
            logger.warn { "Dish with id=$id not found for delete" }
            throw NotFoundException("Dish with id=$id not found")
        }
        logger.info { "Dish deleted: id=$id" }
    }
}
