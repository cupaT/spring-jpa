package com.example.springjpa.adapters.jpa

import com.example.springjpa.adapters.jpa.entity.toDomain
import com.example.springjpa.adapters.jpa.entity.toEntity
import com.example.springjpa.adapters.jpa.repository.DishJpaRepository
import com.example.springjpa.adapters.jpa.repository.RestaurantJpaRepository
import com.example.springjpa.application.port.DishRepositoryPort
import com.example.springjpa.domain.model.Dish
import org.springframework.stereotype.Repository

@Repository
class DishJpaRepositoryAdapter(
    private val dishJpaRepository: DishJpaRepository,
    private val restaurantJpaRepository: RestaurantJpaRepository,
) : DishRepositoryPort {
    override fun create(dish: Dish): Dish {
        val restaurant = restaurantJpaRepository.findById(dish.restaurantId).orElseThrow {
            IllegalArgumentException("Restaurant with id=${dish.restaurantId} not found")
        }
        return dishJpaRepository.save(dish.toEntity(restaurant)).toDomain()
    }

    override fun findById(id: Long): Dish? = dishJpaRepository.findById(id).orElse(null)?.toDomain()

    override fun findAll(namePart: String?): List<Dish> =
        if (namePart == null) {
            dishJpaRepository.findAllByOrderByIdAsc().map { it.toDomain() }
        } else {
            dishJpaRepository.searchByNamePart(namePart).map { it.toDomain() }
        }

    override fun findAllByIds(ids: List<Long>): List<Dish> {
        if (ids.isEmpty()) {
            return emptyList()
        }
        return dishJpaRepository.findAllByIdIn(ids).map { it.toDomain() }
    }

    override fun findAllByRestaurantId(restaurantId: Long): List<Dish> =
        dishJpaRepository.findAllByRestaurantIdOrderById(restaurantId).map { it.toDomain() }

    override fun update(dish: Dish): Dish? {
        if (!dishJpaRepository.existsById(dish.id)) {
            return null
        }
        val restaurant = restaurantJpaRepository.findById(dish.restaurantId).orElseThrow {
            IllegalArgumentException("Restaurant with id=${dish.restaurantId} not found")
        }
        return dishJpaRepository.save(dish.toEntity(restaurant)).toDomain()
    }

    override fun deleteById(id: Long): Boolean {
        if (!dishJpaRepository.existsById(id)) {
            return false
        }
        dishJpaRepository.deleteById(id)
        return true
    }
}
