package com.example.springjpa.application.service

import com.example.springjpa.application.port.OrderRepositoryPort
import com.example.springjpa.application.port.UserRepositoryPort
import com.example.springjpa.domain.model.Dish
import com.example.springjpa.domain.model.Order
import com.example.springjpa.domain.model.OrderStatus
import com.example.springjpa.domain.model.User
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.junit.jupiter.MockitoExtension
import java.math.BigDecimal
import java.time.LocalDateTime

@ExtendWith(MockitoExtension::class)
class OrderSchedulerTest {
    @Mock
    lateinit var notificationService: NotificationService

    @Test
    fun `cancelStuckOrders cancels preparing orders and sends notifications`() {
        val stuck = order(10, OrderStatus.PREPARING, LocalDateTime.now().minusHours(2))
        val orderRepository = FakeOrderRepository(listOf(stuck))
        val userRepository = FakeUserRepository(mapOf(3L to user(3)))

        scheduler(orderRepository, userRepository).cancelStuckOrders()

        assertEquals(listOf(10L to OrderStatus.CANCELLED), orderRepository.updatedStatuses)
        verify(notificationService).sendOrderStatusUpdate("user3@example.com", 10, OrderStatus.CANCELLED)
    }

    @Test
    fun `cancelStuckOrders only queries preparing orders before threshold`() {
        val fresh = order(10, OrderStatus.PREPARING, LocalDateTime.now())
        val delivered = order(11, OrderStatus.DELIVERED, LocalDateTime.now().minusHours(2))
        val orderRepository = FakeOrderRepository(listOf(fresh, delivered))
        val userRepository = FakeUserRepository(emptyMap())

        scheduler(orderRepository, userRepository).cancelStuckOrders()

        assertEquals(OrderStatus.PREPARING, orderRepository.lastRequestedStatus)
        assertEquals(emptyList<Pair<Long, OrderStatus>>(), orderRepository.updatedStatuses)
        verifyNoInteractions(notificationService)
    }

    @Test
    fun `cancelStuckOrders skips notification when cancellation write returns null`() {
        val stuck = order(10, OrderStatus.PREPARING, LocalDateTime.now().minusHours(2))
        val orderRepository = FakeOrderRepository(listOf(stuck), failUpdates = true)
        val userRepository = FakeUserRepository(mapOf(3L to user(3)))

        scheduler(orderRepository, userRepository).cancelStuckOrders()

        assertEquals(listOf(10L to OrderStatus.CANCELLED), orderRepository.updatedStatuses)
        verifyNoInteractions(notificationService)
    }

    private fun scheduler(
        orderRepositoryPort: OrderRepositoryPort,
        userRepositoryPort: UserRepositoryPort,
    ) = OrderScheduler(orderRepositoryPort, userRepositoryPort, notificationService, 1)

    private class FakeOrderRepository(
        private val orders: List<Order>,
        private val failUpdates: Boolean = false,
    ) : OrderRepositoryPort {
        var lastRequestedStatus: OrderStatus? = null
        val updatedStatuses = mutableListOf<Pair<Long, OrderStatus>>()

        override fun create(userId: Long, dishIds: List<Long>, status: OrderStatus): Order =
            error("Not used in scheduler tests")

        override fun findById(id: Long): Order? = orders.firstOrNull { it.id == id }

        override fun findAll(userId: Long?, status: OrderStatus?): List<Order> =
            error("Not used in scheduler tests")

        override fun findByStatusAndCreatedAtBefore(status: OrderStatus, createdBefore: LocalDateTime): List<Order> {
            lastRequestedStatus = status
            return orders.filter { it.status == status && it.createdAt < createdBefore }
        }

        override fun updateStatus(id: Long, status: OrderStatus): Order? {
            updatedStatuses += id to status
            if (failUpdates) {
                return null
            }
            return findById(id)?.copy(status = status)
        }
    }

    private class FakeUserRepository(
        private val users: Map<Long, User>,
    ) : UserRepositoryPort {
        override fun create(user: User): User = error("Not used in scheduler tests")

        override fun findById(id: Long): User? = users[id]

        override fun findAll(): List<User> = error("Not used in scheduler tests")

        override fun update(user: User): User? = error("Not used in scheduler tests")

        override fun deleteById(id: Long): Boolean = error("Not used in scheduler tests")

        override fun findByEmail(email: String): User? = error("Not used in scheduler tests")

        override fun existsByEmail(email: String): Boolean = error("Not used in scheduler tests")
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
        createdAt: LocalDateTime,
    ) = Order(
        id = id,
        userId = 3,
        status = status,
        createdAt = createdAt,
        dishes = listOf(dish(1)),
    )
}
