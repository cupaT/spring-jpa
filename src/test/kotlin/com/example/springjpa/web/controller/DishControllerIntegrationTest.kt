package com.example.springjpa.web.controller

import com.example.springjpa.adapters.jpa.entity.DishEntity
import com.example.springjpa.adapters.jpa.entity.RestaurantEntity
import com.example.springjpa.adapters.jpa.repository.DishJpaRepository
import com.example.springjpa.adapters.jpa.repository.RestaurantJpaRepository
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
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
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.annotation.Transactional
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.math.BigDecimal

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@Testcontainers
class DishControllerIntegrationTest {
    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var dishJpaRepository: DishJpaRepository

    @Autowired
    lateinit var restaurantJpaRepository: RestaurantJpaRepository

    @BeforeEach
    fun cleanDatabase() {
        dishJpaRepository.deleteAll()
        restaurantJpaRepository.deleteAll()
    }

    @Test
    fun `GET dishes returns all dishes`() {
        val restaurant = restaurantJpaRepository.save(RestaurantEntity(name = "Pizza Place", address = "Lenina 1"))
        dishJpaRepository.save(
            DishEntity(
                name = "Margherita",
                description = "Classic pizza",
                price = BigDecimal("10.50"),
                available = true,
                restaurant = restaurant,
            )
        )

        mockMvc
            .perform(get("/api/v1/dishes"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0].name").value("Margherita"))
            .andExpect(jsonPath("$[0].description").value("Classic pizza"))
            .andExpect(jsonPath("$[0].price").value(10.50))
            .andExpect(jsonPath("$[0].isAvailable").value(true))
            .andExpect(jsonPath("$[0].restaurantId").value(restaurant.id!!.toInt()))
    }

    @Test
    fun `GET dishes returns validation error when namePart is too short`() {
        mockMvc
            .perform(get("/api/v1/dishes").param("namePart", "a"))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.status").value(400))
            .andExpect(jsonPath("$.message").value("Validation error"))
            .andExpect(jsonPath("$.errors.namePart").value("namePart must contain at least 2 characters"))
    }

    @Test
    fun `POST restaurant dish creates dish`() {
        val restaurant = restaurantJpaRepository.save(RestaurantEntity(name = "Pizza Place", address = "Lenina 1"))

        mockMvc
            .perform(
                post("/api/v1/restaurants/${restaurant.id}/dishes")
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
            .andExpect(jsonPath("$.name").value("Pepperoni"))
            .andExpect(jsonPath("$.description").value("Spicy pizza"))
            .andExpect(jsonPath("$.price").value(15.20))
            .andExpect(jsonPath("$.isAvailable").value(true))
            .andExpect(jsonPath("$.restaurantId").value(restaurant.id!!.toInt()))
    }

    @Test
    fun `POST restaurant dish returns not found when restaurant does not exist`() {
        mockMvc
            .perform(
                post("/api/v1/restaurants/999/dishes")
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
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.status").value(404))
            .andExpect(jsonPath("$.message").value("Restaurant with id=999 not found"))
    }

    @Test
    fun `GET dish by id returns dish`() {
        val restaurant = restaurantJpaRepository.save(RestaurantEntity(name = "Pizza Place", address = "Lenina 1"))
        val dish =
            dishJpaRepository.save(
                DishEntity(
                    name = "Margherita",
                    description = "Classic pizza",
                    price = BigDecimal("10.50"),
                    available = true,
                    restaurant = restaurant,
                )
            )

        mockMvc
            .perform(get("/api/v1/dishes/${dish.id}"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.id").value(dish.id!!.toInt()))
            .andExpect(jsonPath("$.name").value("Margherita"))
            .andExpect(jsonPath("$.restaurantId").value(restaurant.id!!.toInt()))
    }

    @Test
    fun `GET dish by id returns not found for unknown id`() {
        mockMvc
            .perform(get("/api/v1/dishes/999"))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.status").value(404))
            .andExpect(jsonPath("$.message").value("Dish with id=999 not found"))
    }

    @Test
    fun `PUT dish updates dish`() {
        val restaurant = restaurantJpaRepository.save(RestaurantEntity(name = "Pizza Place", address = "Lenina 1"))
        val dish =
            dishJpaRepository.save(
                DishEntity(
                    name = "Margherita",
                    description = "Classic pizza",
                    price = BigDecimal("10.50"),
                    available = true,
                    restaurant = restaurant,
                )
            )

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
            .andExpect(jsonPath("$.id").value(dish.id!!.toInt()))
            .andExpect(jsonPath("$.name").value("Quattro Formaggi"))
            .andExpect(jsonPath("$.description").value("Cheese pizza"))
            .andExpect(jsonPath("$.price").value(18.00))
            .andExpect(jsonPath("$.isAvailable").value(false))
            .andExpect(jsonPath("$.restaurantId").value(restaurant.id!!.toInt()))
    }

    @Test
    fun `PUT dish returns validation error for invalid body`() {
        val restaurant = restaurantJpaRepository.save(RestaurantEntity(name = "Pizza Place", address = "Lenina 1"))
        val dish =
            dishJpaRepository.save(
                DishEntity(
                    name = "Margherita",
                    description = "Classic pizza",
                    price = BigDecimal("10.50"),
                    available = true,
                    restaurant = restaurant,
                )
            )

        mockMvc
            .perform(
                put("/api/v1/dishes/${dish.id}")
                    .with(user("admin").roles("ADMIN"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "name": "",
                          "description": "",
                          "price": -1,
                          "isAvailable": true
                        }
                        """.trimIndent()
                    )
            )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.status").value(400))
            .andExpect(jsonPath("$.message").value("Validation error"))
            .andExpect(jsonPath("$.errors.name").value("Dish name must not be blank"))
            .andExpect(jsonPath("$.errors.description").value("Description must not be blank"))
            .andExpect(jsonPath("$.errors.price").value("Price must be greater than 0"))
    }

    @Test
    fun `DELETE dish returns no content when dish exists`() {
        val restaurant = restaurantJpaRepository.save(RestaurantEntity(name = "Pizza Place", address = "Lenina 1"))
        val dish =
            dishJpaRepository.save(
                DishEntity(
                    name = "Margherita",
                    description = "Classic pizza",
                    price = BigDecimal("10.50"),
                    available = true,
                    restaurant = restaurant,
                )
            )

        mockMvc
            .perform(delete("/api/v1/dishes/${dish.id}").with(user("admin").roles("ADMIN")))
            .andExpect(status().isNoContent)
    }

    @Test
    fun `DELETE dish returns not found when dish does not exist`() {
        mockMvc
            .perform(delete("/api/v1/dishes/999").with(user("admin").roles("ADMIN")))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.status").value(404))
            .andExpect(jsonPath("$.message").value("Dish with id=999 not found"))
    }

    @Test
    fun `POST restaurant dish without token returns unauthorized`() {
        val restaurant = restaurantJpaRepository.save(RestaurantEntity(name = "Pizza Place", address = "Lenina 1"))

        mockMvc
            .perform(
                post("/api/v1/restaurants/${restaurant.id}/dishes")
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
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.status").value(401))
    }

    @Test
    fun `POST restaurant dish as user returns forbidden`() {
        val restaurant = restaurantJpaRepository.save(RestaurantEntity(name = "Pizza Place", address = "Lenina 1"))

        mockMvc
            .perform(
                post("/api/v1/restaurants/${restaurant.id}/dishes")
                    .with(user("user").roles("USER"))
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
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.status").value(403))
    }

    companion object {
        @Container
        @JvmStatic
        val postgres = PostgreSQLContainer("postgres:17")
            .withDatabaseName("spring_jpa_test")
            .withUsername("postgres")
            .withPassword("postgres")

        @JvmStatic
        @DynamicPropertySource
        fun overrideProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url", postgres::getJdbcUrl)
            registry.add("spring.datasource.username", postgres::getUsername)
            registry.add("spring.datasource.password", postgres::getPassword)
        }
    }
}
