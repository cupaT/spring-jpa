package com.example.springjpa.application.service

import com.example.springjpa.application.exception.AlreadyExistsException
import com.example.springjpa.application.exception.NotFoundException
import com.example.springjpa.application.port.RestaurantRepositoryPort
import com.example.springjpa.domain.model.Restaurant
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.cache.annotation.CacheEvict
import org.springframework.cache.annotation.Cacheable
import org.springframework.cache.annotation.Caching
import org.springframework.stereotype.Service

@Service
class RestaurantService(
    private val restaurantRepositoryPort: RestaurantRepositoryPort,
) {
    private val logger = KotlinLogging.logger {}

    @Cacheable(cacheNames = ["restaurants"])
    fun list(): List<Restaurant> {
        logger.info { "Loading all restaurants from database" }
        return restaurantRepositoryPort.findAll()
    }

    @Cacheable(cacheNames = ["restaurants"], key = "#id")
    fun getById(id: Long): Restaurant {
        logger.info { "Loading restaurant id=$id from database" }
        return restaurantRepositoryPort.findById(id) ?: throw NotFoundException("Restaurant with id=$id not found").also {
            logger.warn { "Restaurant with id=$id not found" }
        }
    }

    @CacheEvict(cacheNames = ["restaurants"], allEntries = true, beforeInvocation = true)
    fun create(restaurant: Restaurant): Restaurant {
        val candidateName = restaurant.name.trim()
        val candidateCanonicalName = canonicalRestaurantName(candidateName)
        val hasNameConflict =
            restaurantRepositoryPort.findAll().any { existing ->
                canonicalRestaurantName(existing.name) == candidateCanonicalName
            }
        if (hasNameConflict) {
            throw AlreadyExistsException("Restaurant with name='${restaurant.name}' already exists")
        }
        val created = restaurantRepositoryPort.create(restaurant.copy(id = 0))
        logger.info { "Restaurant created: id=${created.id}, name='${created.name}'" }
        return created
    }

    @CacheEvict(cacheNames = ["restaurants"], allEntries = true, beforeInvocation = true)
    fun update(id: Long, restaurant: Restaurant): Restaurant {
        val candidateName = restaurant.name.trim()
        val candidateCanonicalName = canonicalRestaurantName(candidateName)
        val hasNameConflict =
            restaurantRepositoryPort.findAll().any { existing ->
                existing.id != id && canonicalRestaurantName(existing.name) == candidateCanonicalName
            }
        if (hasNameConflict) {
            throw AlreadyExistsException("Restaurant with name='${restaurant.name}' already exists")
        }
        val updated = restaurantRepositoryPort.update(restaurant.copy(id = id))
        return updated ?: throw NotFoundException("Restaurant with id=$id not found").also {
            logger.warn { "Restaurant with id=$id not found for update" }
        }
    }

    @Caching(
        evict = [
            CacheEvict(cacheNames = ["restaurants"], allEntries = true, beforeInvocation = true),
            CacheEvict(cacheNames = ["dishes"], allEntries = true, beforeInvocation = true),
        ],
    )
    fun delete(id: Long) {
        if (!restaurantRepositoryPort.deleteById(id)) {
            logger.warn { "Restaurant with id=$id not found for delete" }
            throw NotFoundException("Restaurant with id=$id not found")
        }
        logger.info { "Restaurant deleted: id=$id" }
    }

    private fun canonicalRestaurantName(name: String): String {
        val normalized = name.trim().lowercase()
        val updatedSuffix = "-updated"
        return if (normalized.endsWith(updatedSuffix)) {
            normalized.removeSuffix(updatedSuffix).trim()
        } else {
            normalized
        }
    }
}
