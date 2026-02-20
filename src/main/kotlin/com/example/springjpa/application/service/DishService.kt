package com.example.springjpa.application.service

import com.example.springjpa.application.exception.NotFoundException
import com.example.springjpa.application.port.DishRepositoryPort
import com.example.springjpa.domain.model.Dish
import org.springframework.stereotype.Service

@Service
class DishService(
    private val dishRepositoryPort: DishRepositoryPort,
) {
    fun list(namePart: String?): List<Dish> = dishRepositoryPort.findAll(namePart)

    fun getById(id: Long): Dish =
        dishRepositoryPort.findById(id) ?: throw NotFoundException("Dish with id=$id not found")

    fun createOrGetExisting(dish: Dish): CreateResult<Dish> {
        val existing = dishRepositoryPort.findByName(dish.name)
        if (existing != null) {
            return CreateResult(value = existing, created = false)
        }
        val created = dishRepositoryPort.create(dish)
        return CreateResult(value = created, created = true)
    }

    fun update(id: Long, dish: Dish): Dish {
        val updated = dishRepositoryPort.update(dish.copy(id = id))
        return updated ?: throw NotFoundException("Dish with id=$id not found")
    }

    fun delete(id: Long) {
        if (!dishRepositoryPort.deleteById(id)) {
            throw NotFoundException("Dish with id=$id not found")
        }
    }
}
