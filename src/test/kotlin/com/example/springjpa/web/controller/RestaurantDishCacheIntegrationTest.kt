package com.example.springjpa.web.controller

import com.example.springjpa.adapters.jpa.entity.DishEntity
import com.example.springjpa.adapters.jpa.entity.RestaurantEntity
import com.example.springjpa.adapters.jpa.repository.DishJpaRepository
import com.example.springjpa.adapters.jpa.repository.RestaurantJpaRepository
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.cache.CacheManager
import org.springframework.cache.interceptor.SimpleKey
import org.springframework.http.MediaType
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.math.BigDecimal

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
class RestaurantDishCacheIntegrationTest {
    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var cacheManager: CacheManager

    @Autowired
    lateinit var dishJpaRepository: DishJpaRepository

    @Autowired
    lateinit var restaurantJpaRepository: RestaurantJpaRepository

    @BeforeEach
    fun cleanState() {
        cacheManager.cacheNames.forEach { cacheName ->
            cacheManager.getCache(cacheName)?.clear()
        }
        dishJpaRepository.deleteAll()
        restaurantJpaRepository.deleteAll()
    }

    @Test
    fun `repeated GET restaurants stores restaurants cache entry`() {
        restaurantJpaRepository.save(RestaurantEntity(name = "Pizza Place", address = "Lenina 1"))

        mockMvc.perform(get("/api/v1/restaurants")).andExpect(status().isOk)
        mockMvc.perform(get("/api/v1/restaurants")).andExpect(status().isOk)

        assertNotNull(cacheManager.getCache("restaurants")?.get(SimpleKey.EMPTY))
    }

    @Test
    fun `creating restaurant evicts restaurants list cache entry`() {
        restaurantJpaRepository.save(RestaurantEntity(name = "Pizza Place", address = "Lenina 1"))

        mockMvc.perform(get("/api/v1/restaurants")).andExpect(status().isOk)
        assertNotNull(cacheManager.getCache("restaurants")?.get(SimpleKey.EMPTY))

        mockMvc
            .perform(
                post("/api/v1/restaurants")
                    .with(user("admin").roles("ADMIN"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"name":"Burger House","address":"Mira 5"}""")
            )
            .andExpect(status().isCreated)

        assertNull(cacheManager.getCache("restaurants")?.get(SimpleKey.EMPTY))
    }

    @Test
    fun `dish create update and delete evict dishes cache entry`() {
        val restaurant = restaurantJpaRepository.save(RestaurantEntity(name = "Pizza Place", address = "Lenina 1"))
        val restaurantId = restaurant.id!!
        val dish = dishJpaRepository.save(dishEntity(restaurant, "Margherita"))

        cacheRestaurantDishes(restaurantId)

        mockMvc
            .perform(
                post("/api/v1/restaurants/$restaurantId/dishes")
                    .with(user("admin").roles("ADMIN"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "name": "Pepperoni",
                          "description": "Spicy pizza",
                          "price": 15.20,
                          "isAvailable": true
                        }
                        """.trimIndent()
                    )
            )
            .andExpect(status().isCreated)
        assertNull(cacheManager.getCache("dishes")?.get(restaurantId))

        cacheRestaurantDishes(restaurantId)

        mockMvc
            .perform(
                put("/api/v1/dishes/${dish.id}")
                    .with(user("admin").roles("ADMIN"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "name": "Quattro Formaggi",
                          "description": "Cheese pizza",
                          "price": 18.00,
                          "isAvailable": false
                        }
                        """.trimIndent()
                    )
            )
            .andExpect(status().isOk)
        assertNull(cacheManager.getCache("dishes")?.get(restaurantId))

        cacheRestaurantDishes(restaurantId)
        mockMvc.perform(get("/api/v1/restaurants")).andExpect(status().isOk)
        assertNotNull(cacheManager.getCache("restaurants")?.get(SimpleKey.EMPTY))

        mockMvc
            .perform(delete("/api/v1/dishes/${dish.id}").with(user("admin").roles("ADMIN")))
            .andExpect(status().isNoContent)
        assertNull(cacheManager.getCache("dishes")?.get(restaurantId))
        assertNull(cacheManager.getCache("restaurants")?.get(SimpleKey.EMPTY))
    }

    private fun cacheRestaurantDishes(restaurantId: Long) {
        mockMvc.perform(get("/api/v1/restaurants/$restaurantId/dishes")).andExpect(status().isOk)
        assertNotNull(cacheManager.getCache("dishes")?.get(restaurantId))
    }

    private fun dishEntity(
        restaurant: RestaurantEntity,
        name: String,
    ): DishEntity =
        DishEntity(
            name = name,
            description = "Classic pizza",
            price = BigDecimal("10.50"),
            available = true,
            restaurant = restaurant,
        )

    companion object {
        @Container
        @JvmStatic
        val postgres = PostgreSQLContainer("postgres:17")
            .withDatabaseName("spring_jpa_cache_test")
            .withUsername("postgres")
            .withPassword("postgres")

        @Container
        @JvmStatic
        val redis = GenericContainer("redis:7-alpine")
            .withExposedPorts(6379)

        @JvmStatic
        @DynamicPropertySource
        fun overrideProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url", postgres::getJdbcUrl)
            registry.add("spring.datasource.username", postgres::getUsername)
            registry.add("spring.datasource.password", postgres::getPassword)
            registry.add("spring.data.redis.host", redis::getHost)
            registry.add("spring.data.redis.port") { redis.firstMappedPort }
            registry.add("spring.cache.type") { "redis" }
        }
    }
}
