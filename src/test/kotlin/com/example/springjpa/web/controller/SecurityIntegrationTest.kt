package com.example.springjpa.web.controller

import com.example.springjpa.adapters.jpa.entity.DishEntity
import com.example.springjpa.adapters.jpa.entity.RestaurantEntity
import com.example.springjpa.adapters.jpa.entity.UserEntity
import com.example.springjpa.adapters.jpa.repository.DishJpaRepository
import com.example.springjpa.adapters.jpa.repository.OrderJpaRepository
import com.example.springjpa.adapters.jpa.repository.RestaurantJpaRepository
import com.example.springjpa.adapters.jpa.repository.UserJpaRepository
import com.example.springjpa.domain.model.Role
import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
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
class SecurityIntegrationTest {
    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var objectMapper: ObjectMapper

    @Autowired
    lateinit var userJpaRepository: UserJpaRepository

    @Autowired
    lateinit var restaurantJpaRepository: RestaurantJpaRepository

    @Autowired
    lateinit var dishJpaRepository: DishJpaRepository

    @Autowired
    lateinit var orderJpaRepository: OrderJpaRepository

    @Autowired
    lateinit var passwordEncoder: PasswordEncoder

    @BeforeEach
    fun cleanDatabase() {
        orderJpaRepository.deleteAll()
        dishJpaRepository.deleteAll()
        restaurantJpaRepository.deleteAll()
        userJpaRepository.deleteAll()
    }

    @Test
    fun `register returns token and stores BCrypt password`() {
        mockMvc
            .perform(
                post("/auth/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"email":"user@example.com","password":"secret123","name":"Ivan"}""")
            )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.token").isNotEmpty)
            .andExpect(jsonPath("$.email").value("user@example.com"))
            .andExpect(jsonPath("$.role").value("USER"))

        val saved = userJpaRepository.findByEmail("user@example.com")!!
        assert(saved.password.startsWith("\$2"))
        assert(saved.password != "secret123")
    }

    @Test
    fun `register duplicate email returns conflict`() {
        createUser("user@example.com", "secret123", Role.USER)

        mockMvc
            .perform(
                post("/auth/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"email":"user@example.com","password":"secret123","name":"Ivan"}""")
            )
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.status").value(409))
    }

    @Test
    fun `login returns token and wrong password returns unauthorized`() {
        createUser("user@example.com", "secret123", Role.USER)

        mockMvc
            .perform(
                post("/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"email":"user@example.com","password":"secret123"}""")
            )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.token").isNotEmpty)

        mockMvc
            .perform(
                post("/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"email":"user@example.com","password":"wrong123"}""")
            )
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.status").value(401))
    }

    @Test
    fun `restaurant write endpoints require admin role`() {
        val userToken = tokenFor(createUser("user@example.com", "secret123", Role.USER), "secret123")
        val adminToken = tokenFor(createUser("admin@example.com", "secret123", Role.ADMIN), "secret123")
        val payload = """{"name":"Pizza Place","address":"Lenina 1"}"""

        mockMvc.perform(get("/api/v1/restaurants"))
            .andExpect(status().isOk)

        mockMvc
            .perform(post("/api/v1/restaurants").contentType(MediaType.APPLICATION_JSON).content(payload))
            .andExpect(status().isUnauthorized)

        mockMvc
            .perform(
                post("/api/v1/restaurants")
                    .header("Authorization", "Bearer $userToken")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(payload)
            )
            .andExpect(status().isForbidden)

        mockMvc
            .perform(
                post("/api/v1/restaurants")
                    .header("Authorization", "Bearer $adminToken")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(payload)
            )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.name").value("Pizza Place"))

        mockMvc
            .perform(
                post("/api/v1/restaurants")
                    .header("Authorization", "Bearer invalid-token")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"name":"Burger House","address":"Mira 5"}""")
            )
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `valid token for deleted user returns unauthorized`() {
        val user = createUser("deleted@example.com", "secret123", Role.USER)
        val token = tokenFor(user, "secret123")
        userJpaRepository.delete(user)

        mockMvc
            .perform(get("/api/v1/orders").header("Authorization", "Bearer $token"))
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.status").value(401))
    }

    @Test
    fun `orders are created for current user and visible to owner or admin`() {
        val owner = createUser("owner@example.com", "secret123", Role.USER)
        val other = createUser("other@example.com", "secret123", Role.USER)
        val admin = createUser("admin@example.com", "secret123", Role.ADMIN)
        val ownerToken = tokenFor(owner, "secret123")
        val otherToken = tokenFor(other, "secret123")
        val adminToken = tokenFor(admin, "secret123")
        val dish = createDish()

        val createResult = mockMvc
            .perform(
                post("/api/v1/orders")
                    .header("Authorization", "Bearer $ownerToken")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"dishIds":[${dish.id}]}""")
            )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.userId").value(owner.id!!.toInt()))
            .andReturn()

        val orderId = objectMapper.readTree(createResult.response.contentAsString)["id"].asLong()

        mockMvc
            .perform(get("/api/v1/orders/$orderId").header("Authorization", "Bearer $ownerToken"))
            .andExpect(status().isOk)

        mockMvc
            .perform(get("/api/v1/orders/$orderId").header("Authorization", "Bearer $otherToken"))
            .andExpect(status().isForbidden)

        mockMvc
            .perform(get("/api/v1/orders/$orderId").header("Authorization", "Bearer $adminToken"))
            .andExpect(status().isOk)

        mockMvc
            .perform(
                patch("/api/v1/orders/$orderId/status")
                    .header("Authorization", "Bearer $ownerToken")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"status":"CONFIRMED"}""")
            )
            .andExpect(status().isForbidden)

        mockMvc
            .perform(
                patch("/api/v1/orders/$orderId/status")
                    .header("Authorization", "Bearer $adminToken")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"status":"CONFIRMED"}""")
            )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("CONFIRMED"))
    }

    private fun createUser(email: String, password: String, role: Role): UserEntity =
        userJpaRepository.save(
            UserEntity(
                email = email,
                firstName = email.substringBefore("@"),
                lastName = "Test",
                password = passwordEncoder.encode(password) ?: "",
                role = role,
            )
        )

    private fun createDish(): DishEntity {
        val restaurant = restaurantJpaRepository.save(RestaurantEntity(name = "Pizza Place", address = "Lenina 1"))
        return dishJpaRepository.save(
            DishEntity(
                name = "Margherita",
                description = "Classic pizza",
                price = BigDecimal("10.50"),
                available = true,
                restaurant = restaurant,
            )
        )
    }

    private fun tokenFor(user: UserEntity, password: String): String {
        val result = mockMvc
            .perform(
                post("/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"email":"${user.email}","password":"$password"}""")
            )
            .andExpect(status().isOk)
            .andReturn()

        return objectMapper.readTree(result.response.contentAsString)["token"].asText()
    }

    companion object {
        @Container
        @JvmStatic
        val postgres = PostgreSQLContainer("postgres:17")
            .withDatabaseName("spring_jpa_security_test")
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
