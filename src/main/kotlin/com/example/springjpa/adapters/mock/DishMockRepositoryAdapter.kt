package com.example.springjpa.adapters.mock

import com.example.springjpa.application.port.DishRepositoryPort
import com.example.springjpa.domain.model.Dish
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Repository
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

@Repository
@ConditionalOnProperty(name = ["app.data-provider"], havingValue = "mock")
class DishMockRepositoryAdapter : DishRepositoryPort {
    private val sequence = AtomicLong(1)
    private val storage = ConcurrentHashMap<Long, Dish>()

    override fun create(dish: Dish): Dish {
        val id = sequence.getAndIncrement()
        val saved = dish.copy(id = id)
        storage[id] = saved
        return saved
    }

    override fun findById(id: Long): Dish? = storage[id]

    override fun findAll(namePart: String?): List<Dish> {
        val normalized = namePart?.trim()?.lowercase()
        return storage.values
            .asSequence()
            .filter {
                normalized.isNullOrBlank() || it.name.lowercase().contains(normalized)
            }
            .sortedBy { it.id }
            .toList()
    }

    override fun update(dish: Dish): Dish? {
        if (!storage.containsKey(dish.id)) {
            return null
        }
        storage[dish.id] = dish
        return dish
    }

    override fun deleteById(id: Long): Boolean = storage.remove(id) != null

    override fun findByName(name: String): Dish? = storage.values.firstOrNull { it.name == name }
}
