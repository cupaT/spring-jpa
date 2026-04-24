package com.example.springjpa.application.service

import com.example.springjpa.application.exception.NotFoundException
import com.example.springjpa.application.port.UserRepositoryPort
import com.example.springjpa.domain.model.User
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service

@Service
class UserService(
    private val userRepositoryPort: UserRepositoryPort,
) {
    private val logger = KotlinLogging.logger {}

    fun list(): List<User> = userRepositoryPort.findAll()

    fun getById(id: Long): User =
        userRepositoryPort.findById(id) ?: throw NotFoundException("User with id=$id not found").also {
            logger.warn { "User with id=$id not found" }
        }

    fun createOrGetExisting(user: User): CreateResult<User> {
        val existing = userRepositoryPort.findByEmail(user.email)
        if (existing != null) {
            logger.info { "User already exists, returning existing: email='${user.email}', id=${existing.id}" }
            return CreateResult(value = existing, created = false)
        }
        val created = userRepositoryPort.create(user)
        logger.info { "User created: id=${created.id}, email='${created.email}'" }
        return CreateResult(value = created, created = true)
    }

    fun update(id: Long, user: User): User {
        val updated = userRepositoryPort.update(user.copy(id = id))
        return updated ?: throw NotFoundException("User with id=$id not found").also {
            logger.warn { "User with id=$id not found for update" }
        }
    }

    fun delete(id: Long) {
        if (!userRepositoryPort.deleteById(id)) {
            logger.warn { "User with id=$id not found for delete" }
            throw NotFoundException("User with id=$id not found")
        }
        logger.info { "User deleted: id=$id" }
    }
}
