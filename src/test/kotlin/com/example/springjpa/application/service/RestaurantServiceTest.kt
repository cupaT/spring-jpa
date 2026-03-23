package com.example.springjpa.application.service

import com.example.springjpa.application.exception.AlreadyExistsException
import com.example.springjpa.application.exception.NotFoundException
import com.example.springjpa.application.port.RestaurantRepositoryPort
import com.example.springjpa.domain.model.Restaurant
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoMoreInteractions
import org.mockito.Mockito.`when`
import org.mockito.junit.jupiter.MockitoExtension

@ExtendWith(MockitoExtension::class)
class RestaurantServiceTest {
    @Mock
    lateinit var restaurantRepositoryPort: RestaurantRepositoryPort

    @InjectMocks
    lateinit var restaurantService: RestaurantService

    @Test
    fun `list returns restaurants from repository`() {
        val restaurants =
            listOf(
                Restaurant(1, "Pizza Place", "Lenina 1"),
                Restaurant(2, "Burger House", "Mira 5"),
            )
        `when`(restaurantRepositoryPort.findAll()).thenReturn(restaurants)

        val result = restaurantService.list()

        assertEquals(restaurants, result)
    }

    @Test
    fun `getById returns restaurant when it exists`() {
        val restaurant = Restaurant(1, "Pizza Place", "Lenina 1")
        `when`(restaurantRepositoryPort.findById(1)).thenReturn(restaurant)

        val result = restaurantService.getById(1)

        assertEquals(restaurant, result)
    }

    @Test
    fun `getById throws NotFoundException when restaurant does not exist`() {
        `when`(restaurantRepositoryPort.findById(99)).thenReturn(null)

        assertThrows<NotFoundException> {
            restaurantService.getById(99)
        }
    }

    @Test
    fun `create throws AlreadyExistsException when canonical name already exists`() {
        val candidate = Restaurant(0, "Pizza Place-Updated", "Mira 5")
        `when`(restaurantRepositoryPort.findAll()).thenReturn(listOf(Restaurant(1, " pizza place ", "Lenina 1")))

        assertThrows<AlreadyExistsException> {
            restaurantService.create(candidate)
        }

        verify(restaurantRepositoryPort).findAll()
        verifyNoMoreInteractions(restaurantRepositoryPort)
    }

    @Test
    fun `create stores restaurant with zero id when name is unique`() {
        val candidate = Restaurant(15, "Burger House", "Mira 5")
        val created = Restaurant(1, candidate.name, candidate.address)
        `when`(restaurantRepositoryPort.findAll()).thenReturn(emptyList())
        `when`(restaurantRepositoryPort.create(candidate.copy(id = 0))).thenReturn(created)

        val result = restaurantService.create(candidate)

        assertEquals(created, result)
    }

    @Test
    fun `update throws AlreadyExistsException when another restaurant has same canonical name`() {
        val payload = Restaurant(0, "Pizza Place", "Updated address")
        `when`(restaurantRepositoryPort.findAll()).thenReturn(listOf(Restaurant(1, "Pizza Place-Updated", "Lenina 1")))

        assertThrows<AlreadyExistsException> {
            restaurantService.update(2, payload)
        }

        verify(restaurantRepositoryPort).findAll()
        verifyNoMoreInteractions(restaurantRepositoryPort)
    }

    @Test
    fun `update returns updated restaurant when repository updates successfully`() {
        val payload = Restaurant(0, "Burger House", "Mira 5")
        val updated = Restaurant(2, payload.name, payload.address)
        `when`(restaurantRepositoryPort.findAll()).thenReturn(listOf(Restaurant(2, "Burger House", "Old address")))
        `when`(restaurantRepositoryPort.update(payload.copy(id = 2))).thenReturn(updated)

        val result = restaurantService.update(2, payload)

        assertEquals(updated, result)
    }

    @Test
    fun `update throws NotFoundException when restaurant does not exist`() {
        val payload = Restaurant(0, "Burger House", "Mira 5")
        `when`(restaurantRepositoryPort.findAll()).thenReturn(emptyList())
        `when`(restaurantRepositoryPort.update(payload.copy(id = 2))).thenReturn(null)

        assertThrows<NotFoundException> {
            restaurantService.update(2, payload)
        }
    }

    @Test
    fun `delete throws NotFoundException when restaurant does not exist`() {
        `when`(restaurantRepositoryPort.deleteById(4)).thenReturn(false)

        assertThrows<NotFoundException> {
            restaurantService.delete(4)
        }
    }

    @Test
    fun `delete removes restaurant when repository confirms deletion`() {
        `when`(restaurantRepositoryPort.deleteById(4)).thenReturn(true)

        restaurantService.delete(4)

        verify(restaurantRepositoryPort).deleteById(4)
    }
}
