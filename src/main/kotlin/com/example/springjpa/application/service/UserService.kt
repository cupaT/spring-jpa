package com.example.springjpa.application.service

import com.example.springjpa.application.exception.NotFoundException
import com.example.springjpa.application.port.UserRepositoryPort
import com.example.springjpa.domain.model.User
import org.springframework.stereotype.Service

@Service
class UserService(
    private val userRepositoryPort: UserRepositoryPort,
) {
    fun list(): List<User> = userRepositoryPort.findAll()

    fun getById(id: Long): User =
        userRepositoryPort.findById(id) ?: throw NotFoundException("User with id=$id not found")

    fun createOrGetExisting(user: User): CreateResult<User> {
        val existing = userRepositoryPort.findByEmail(user.email)
        if (existing != null) {
            return CreateResult(value = existing, created = false)
        }
        val created = userRepositoryPort.create(user)
        return CreateResult(value = created, created = true)
    }

    fun update(id: Long, user: User): User {
        val updated = userRepositoryPort.update(user.copy(id = id))
        return updated ?: throw NotFoundException("User with id=$id not found")
    }

    fun delete(id: Long) {
        if (!userRepositoryPort.deleteById(id)) {
            throw NotFoundException("User with id=$id not found")
        }
    }
}
