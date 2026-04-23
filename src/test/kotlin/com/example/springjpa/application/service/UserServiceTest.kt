package com.example.springjpa.application.service

import com.example.springjpa.application.exception.NotFoundException
import com.example.springjpa.application.port.UserRepositoryPort
import com.example.springjpa.domain.model.User
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
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
class UserServiceTest {
    @Mock
    lateinit var userRepositoryPort: UserRepositoryPort

    @InjectMocks
    lateinit var userService: UserService

    @Test
    fun `list returns users from repository`() {
        val users =
            listOf(
                User(1, "one@example.com", "Ivan", "Petrov", true),
                User(2, "two@example.com", "Petr", "Sidorov", false),
            )
        `when`(userRepositoryPort.findAll()).thenReturn(users)

        val result = userService.list()

        assertEquals(users, result)
    }

    @Test
    fun `getById returns user when it exists`() {
        val user = User(1, "user@example.com", "Ivan", "Petrov", true)
        `when`(userRepositoryPort.findById(1)).thenReturn(user)

        val result = userService.getById(1)

        assertEquals(user, result)
    }

    @Test
    fun `getById throws NotFoundException when user does not exist`() {
        `when`(userRepositoryPort.findById(42)).thenReturn(null)

        assertThrows<NotFoundException> {
            userService.getById(42)
        }
    }

    @Test
    fun `createOrGetExisting returns existing user when email already exists`() {
        val existing = User(5, "existing@example.com", "Ivan", "Petrov", true)
        `when`(userRepositoryPort.findByEmail(existing.email)).thenReturn(existing)

        val result = userService.createOrGetExisting(existing.copy(id = 0))

        assertFalse(result.created)
        assertEquals(existing, result.value)
        verify(userRepositoryPort).findByEmail(existing.email)
        verifyNoMoreInteractions(userRepositoryPort)
    }

    @Test
    fun `createOrGetExisting creates new user when email is not used`() {
        val candidate = User(0, "new@example.com", "Anna", "Smirnova", true)
        val created = candidate.copy(id = 10)
        `when`(userRepositoryPort.findByEmail(candidate.email)).thenReturn(null)
        `when`(userRepositoryPort.create(candidate)).thenReturn(created)

        val result = userService.createOrGetExisting(candidate)

        assertTrue(result.created)
        assertEquals(created, result.value)
    }

    @Test
    fun `update returns updated user when repository updates successfully`() {
        val payload = User(0, "updated@example.com", "Anna", "Smirnova", false)
        val updated = User(7, payload.email, payload.firstName, payload.lastName, payload.isActive)
        `when`(userRepositoryPort.update(payload.copy(id = 7))).thenReturn(updated)

        val result = userService.update(7, payload)

        assertEquals(updated, result)
    }

    @Test
    fun `update throws NotFoundException when user does not exist`() {
        val payload = User(0, "updated@example.com", "Anna", "Smirnova", false)
        `when`(userRepositoryPort.update(payload.copy(id = 7))).thenReturn(null)

        assertThrows<NotFoundException> {
            userService.update(7, payload)
        }
    }

    @Test
    fun `delete removes user when repository confirms deletion`() {
        `when`(userRepositoryPort.deleteById(3)).thenReturn(true)

        userService.delete(3)

        verify(userRepositoryPort).deleteById(3)
    }

    @Test
    fun `delete throws NotFoundException when user does not exist`() {
        `when`(userRepositoryPort.deleteById(3)).thenReturn(false)

        assertThrows<NotFoundException> {
            userService.delete(3)
        }
    }
}
