package com.example.springjpa.application.service

import com.example.springjpa.application.exception.InvalidOrderStateException
import com.example.springjpa.application.exception.NotFoundException
import com.example.springjpa.application.port.DishRepositoryPort
import com.example.springjpa.application.port.OrderRepositoryPort
import com.example.springjpa.application.port.UserRepositoryPort
import com.example.springjpa.domain.model.Dish
import com.example.springjpa.domain.model.Order
import com.example.springjpa.domain.model.OrderStatus
import com.example.springjpa.domain.model.User
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.Mockito.`when`
import org.mockito.junit.jupiter.MockitoExtension
import java.math.BigDecimal
import java.time.LocalDateTime

@ExtendWith(MockitoExtension::class)
class OrderServiceTest {
    @Mock
    lateinit var orderRepositoryPort: OrderRepositoryPort

    @Mock
    lateinit var userRepositoryPort: UserRepositoryPort

    @Mock
    lateinit var dishRepositoryPort: DishRepositoryPort

    @Mock
    lateinit var notificationService: NotificationService

    lateinit var orderService: OrderService

    @BeforeEach
    fun setUp() {
        orderService = OrderService(orderRepositoryPort, userRepositoryPort, dishRepositoryPort, notificationService)
    }

    @Test
    fun `create throws IllegalArgumentException when user does not exist`() {
        `when`(userRepositoryPort.findById(3)).thenReturn(null)

        assertThrows<IllegalArgumentException> {
            orderService.create(3, listOf(1, 2))
        }

        verifyNoInteractions(dishRepositoryPort, orderRepositoryPort)
    }

    @Test
    fun `create throws IllegalArgumentException when one of dishes does not exist`() {
        `when`(userRepositoryPort.findById(3)).thenReturn(user(3))
        `when`(dishRepositoryPort.findAllByIds(listOf(1L, 2L))).thenReturn(listOf(dish(1)))

        assertThrows<IllegalArgumentException> {
            orderService.create(3, listOf(1, 2))
        }

        verifyNoInteractions(orderRepositoryPort)
    }

    @Test
    fun `create removes duplicate dish ids and creates pending order`() {
        val dishes = listOf(dish(1), dish(2))
        val created = order(id = 10, status = OrderStatus.PENDING, dishes = dishes)
        `when`(userRepositoryPort.findById(3)).thenReturn(user(3))
        `when`(dishRepositoryPort.findAllByIds(listOf(1L, 2L))).thenReturn(dishes)
        `when`(orderRepositoryPort.create(3, listOf(1L, 2L), OrderStatus.PENDING)).thenReturn(created)

        val result = orderService.create(3, listOf(1, 2, 1))

        assertEquals(created, result)
    }

    @Test
    fun `getById throws NotFoundException when order does not exist`() {
        `when`(orderRepositoryPort.findById(7)).thenReturn(null)

        assertThrows<NotFoundException> {
            orderService.getById(7)
        }
    }

    @Test
    fun `list delegates filters to repository`() {
        val orders = listOf(order(1, OrderStatus.CONFIRMED, listOf(dish(1))))
        `when`(orderRepositoryPort.findAll(5, OrderStatus.CONFIRMED)).thenReturn(orders)

        val result = orderService.list(5, OrderStatus.CONFIRMED)

        assertEquals(orders, result)
    }

    @Test
    fun `updateStatus throws NotFoundException when order does not exist before validation`() {
        `when`(orderRepositoryPort.findById(9)).thenReturn(null)

        assertThrows<NotFoundException> {
            orderService.updateStatus(9, OrderStatus.CONFIRMED)
        }

        verifyNoInteractions(notificationService)
    }

    @Test
    fun `updateStatus throws InvalidOrderStateException for forbidden transition`() {
        `when`(orderRepositoryPort.findById(9)).thenReturn(order(9, OrderStatus.DELIVERED, listOf(dish(1))))

        assertThrows<InvalidOrderStateException> {
            orderService.updateStatus(9, OrderStatus.CANCELLED)
        }

        verify(orderRepositoryPort, never()).updateStatus(9, OrderStatus.CANCELLED)
        verifyNoInteractions(notificationService)
    }

    @Test
    fun `updateStatus throws NotFoundException when repository returns null after update`() {
        `when`(orderRepositoryPort.findById(9)).thenReturn(order(9, OrderStatus.PENDING, listOf(dish(1))))
        `when`(orderRepositoryPort.updateStatus(9, OrderStatus.CONFIRMED)).thenReturn(null)

        assertThrows<NotFoundException> {
            orderService.updateStatus(9, OrderStatus.CONFIRMED)
        }

        verifyNoInteractions(notificationService)
    }

    @Test
    fun `updateStatus returns updated order and sends notification for allowed transition`() {
        val existing = order(9, OrderStatus.PENDING, listOf(dish(1)))
        val updated = existing.copy(status = OrderStatus.CONFIRMED)
        `when`(orderRepositoryPort.findById(9)).thenReturn(existing)
        `when`(orderRepositoryPort.updateStatus(9, OrderStatus.CONFIRMED)).thenReturn(updated)
        `when`(userRepositoryPort.findById(3)).thenReturn(user(3))

        val result = orderService.updateStatus(9, OrderStatus.CONFIRMED)

        assertEquals(updated, result)
        verify(notificationService).sendOrderStatusUpdate("user3@example.com", 9, OrderStatus.CONFIRMED)
    }

    @Test
    fun `updateStatus allows confirmed to preparing transition`() {
        val existing = order(9, OrderStatus.CONFIRMED, listOf(dish(1)))
        val updated = existing.copy(status = OrderStatus.PREPARING)
        `when`(orderRepositoryPort.findById(9)).thenReturn(existing)
        `when`(orderRepositoryPort.updateStatus(9, OrderStatus.PREPARING)).thenReturn(updated)
        `when`(userRepositoryPort.findById(3)).thenReturn(user(3))

        val result = orderService.updateStatus(9, OrderStatus.PREPARING)

        assertEquals(updated, result)
        verify(notificationService).sendOrderStatusUpdate("user3@example.com", 9, OrderStatus.PREPARING)
    }

    @Test
    fun `updateStatus skips notification when user does not exist after update`() {
        val existing = order(9, OrderStatus.PENDING, listOf(dish(1)))
        val updated = existing.copy(status = OrderStatus.CONFIRMED)
        `when`(orderRepositoryPort.findById(9)).thenReturn(existing)
        `when`(orderRepositoryPort.updateStatus(9, OrderStatus.CONFIRMED)).thenReturn(updated)
        `when`(userRepositoryPort.findById(3)).thenReturn(null)

        val result = orderService.updateStatus(9, OrderStatus.CONFIRMED)

        assertEquals(updated, result)
        verifyNoInteractions(notificationService)
    }

    private fun user(id: Long) =
        User(
            id = id,
            email = "user$id@example.com",
            firstName = "Ivan",
            lastName = "Petrov",
            isActive = true,
        )

    private fun dish(id: Long) =
        Dish(
            id = id,
            name = "Dish $id",
            description = "Description $id",
            price = BigDecimal("12.50"),
            isAvailable = true,
            restaurantId = 1,
        )

    private fun order(
        id: Long,
        status: OrderStatus,
        dishes: List<Dish>,
    ) = Order(
        id = id,
        userId = 3,
        status = status,
        createdAt = LocalDateTime.of(2025, 1, 1, 12, 0),
        dishes = dishes,
    )
}
