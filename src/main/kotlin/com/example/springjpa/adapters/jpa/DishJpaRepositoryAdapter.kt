package com.example.springjpa.adapters.jpa

import com.example.springjpa.adapters.jpa.entity.toDomain
import com.example.springjpa.adapters.jpa.entity.toEntity
import com.example.springjpa.adapters.jpa.repository.DishJpaRepository
import com.example.springjpa.application.port.DishRepositoryPort
import com.example.springjpa.domain.model.Dish
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Repository

@Repository
@ConditionalOnProperty(name = ["app.data-provider"], havingValue = "db", matchIfMissing = true)
class DishJpaRepositoryAdapter(
    private val dishJpaRepository: DishJpaRepository,
) : DishRepositoryPort {
    override fun create(dish: Dish): Dish = dishJpaRepository.save(dish.toEntity()).toDomain()

    override fun findById(id: Long): Dish? = dishJpaRepository.findById(id).orElse(null)?.toDomain()

    override fun findAll(namePart: String?): List<Dish> =
        dishJpaRepository.searchByNamePart(namePart).map { it.toDomain() }

    override fun update(dish: Dish): Dish? {
        if (!dishJpaRepository.existsById(dish.id)) {
            return null
        }
        return dishJpaRepository.save(dish.toEntity()).toDomain()
    }

    override fun deleteById(id: Long): Boolean {
        if (!dishJpaRepository.existsById(id)) {
            return false
        }
        dishJpaRepository.deleteById(id)
        return true
    }

    override fun findByName(name: String): Dish? = dishJpaRepository.findByName(name)?.toDomain()
}
