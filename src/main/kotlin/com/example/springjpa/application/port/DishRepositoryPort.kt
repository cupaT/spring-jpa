package com.example.springjpa.application.port

import com.example.springjpa.domain.model.Dish

interface DishRepositoryPort {
    fun create(dish: Dish): Dish
    fun findById(id: Long): Dish?
    fun findAll(namePart: String?): List<Dish>
    fun update(dish: Dish): Dish?
    fun deleteById(id: Long): Boolean
    fun findByName(name: String): Dish?
}
