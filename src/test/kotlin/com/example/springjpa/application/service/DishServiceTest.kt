package com.example.springjpa.application.service

import com.example.springjpa.application.exception.NotFoundException
import com.example.springjpa.application.port.DishRepositoryPort
import com.example.springjpa.application.port.RestaurantRepositoryPort
import com.example.springjpa.domain.model.Dish
import com.example.springjpa.domain.model.Restaurant
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.mockito.junit.jupiter.MockitoExtension
import java.math.BigDecimal

@ExtendWith(MockitoExtension::class)
class DishServiceTest {
    @Mock
    lateinit var dishRepositoryPort: DishRepositoryPort

    @Mock
    lateinit var restaurantRepositoryPort: RestaurantRepositoryPort

    @InjectMocks
    lateinit var dishService: DishService

    @Test
    fun `list trims blank filter to null`() {
        val dishes = listOf(dish(id = 1))
        `when`(dishRepositoryPort.findAll(null)).thenReturn(dishes)

        val result = dishService.list("   ")

        assertEquals(dishes, result)
    }

    @Test
    fun `listByRestaurantId throws NotFoundException when restaurant does not exist`() {
        `when`(restaurantRepositoryPort.findById(7)).thenReturn(null)

        assertThrows<NotFoundException> {
            dishService.listByRestaurantId(7)
        }
    }

    @Test
    fun `listByRestaurantId returns dishes when restaurant exists`() {
        val restaurant = Restaurant(7, "Pizza Place", "Lenina 1")
        val dishes = listOf(dish(id = 1, restaurantId = 7))
        `when`(restaurantRepositoryPort.findById(7)).thenReturn(restaurant)
        `when`(dishRepositoryPort.findAllByRestaurantId(7)).thenReturn(dishes)

        val result = dishService.listByRestaurantId(7)

        assertEquals(dishes, result)
    }

    @Test
    fun `getById throws NotFoundException when dish does not exist`() {
        `when`(dishRepositoryPort.findById(10)).thenReturn(null)

        assertThrows<NotFoundException> {
            dishService.getById(10)
        }
    }

    @Test
    fun `createInRestaurant throws NotFoundException when restaurant does not exist`() {
        `when`(restaurantRepositoryPort.findById(3)).thenReturn(null)

        assertThrows<NotFoundException> {
            dishService.createInRestaurant(3, dish(id = 9, restaurantId = 99))
        }
    }

    @Test
    fun `createInRestaurant creates dish with zero id and restaurant id from path`() {
        val restaurant = Restaurant(3, "Pizza Place", "Lenina 1")
        val payload = dish(id = 9, restaurantId = 99)
        val created = dish(id = 1, restaurantId = 3)
        `when`(restaurantRepositoryPort.findById(3)).thenReturn(restaurant)
        `when`(dishRepositoryPort.create(payload.copy(id = 0, restaurantId = 3))).thenReturn(created)

        val result = dishService.createInRestaurant(3, payload)

        assertEquals(created, result)
    }

    @Test
    fun `update throws NotFoundException when dish does not exist before update`() {
        `when`(dishRepositoryPort.findById(8)).thenReturn(null)

        assertThrows<NotFoundException> {
            dishService.update(8, dish(id = 0, restaurantId = 77))
        }
    }

    @Test
    fun `update preserves restaurant id from existing dish`() {
        val existing = dish(id = 8, restaurantId = 5)
        val payload = dish(id = 0, restaurantId = 99, name = "Updated")
        val updated = payload.copy(id = 8, restaurantId = 5)
        `when`(dishRepositoryPort.findById(8)).thenReturn(existing)
        `when`(dishRepositoryPort.update(payload.copy(id = 8, restaurantId = 5))).thenReturn(updated)

        val result = dishService.update(8, payload)

        assertEquals(updated, result)
    }

    @Test
    fun `update throws NotFoundException when repository returns null after write`() {
        val existing = dish(id = 8, restaurantId = 5)
        val payload = dish(id = 0, restaurantId = 99, name = "Updated")
        `when`(dishRepositoryPort.findById(8)).thenReturn(existing)
        `when`(dishRepositoryPort.update(payload.copy(id = 8, restaurantId = 5))).thenReturn(null)

        assertThrows<NotFoundException> {
            dishService.update(8, payload)
        }
    }

    @Test
    fun `delete removes dish when repository confirms deletion`() {
        `when`(dishRepositoryPort.deleteById(11)).thenReturn(true)

        dishService.delete(11)

        verify(dishRepositoryPort).deleteById(11)
    }

    @Test
    fun `delete throws NotFoundException when dish does not exist`() {
        `when`(dishRepositoryPort.deleteById(11)).thenReturn(false)

        assertThrows<NotFoundException> {
            dishService.delete(11)
        }
    }

    private fun dish(
        id: Long,
        restaurantId: Long = 1,
        name: String = "Margherita",
    ) = Dish(
        id = id,
        name = name,
        description = "Classic pizza",
        price = BigDecimal("10.50"),
        isAvailable = true,
        restaurantId = restaurantId,
    )
}
