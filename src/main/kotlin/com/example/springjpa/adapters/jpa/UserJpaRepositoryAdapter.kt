package com.example.springjpa.adapters.jpa

import com.example.springjpa.adapters.jpa.entity.toDomain
import com.example.springjpa.adapters.jpa.entity.toEntity
import com.example.springjpa.adapters.jpa.repository.UserJpaRepository
import com.example.springjpa.application.port.UserRepositoryPort
import com.example.springjpa.domain.model.User
import org.springframework.stereotype.Repository

@Repository
class UserJpaRepositoryAdapter(
    private val userJpaRepository: UserJpaRepository,
) : UserRepositoryPort {
    override fun create(user: User): User = userJpaRepository.save(user.toEntity()).toDomain()

    override fun findById(id: Long): User? = userJpaRepository.findById(id).orElse(null)?.toDomain()

    override fun findAll(): List<User> = userJpaRepository.findAll().map { it.toDomain() }

    override fun update(user: User): User? {
        if (!userJpaRepository.existsById(user.id)) {
            return null
        }
        return userJpaRepository.save(user.toEntity()).toDomain()
    }

    override fun deleteById(id: Long): Boolean {
        if (!userJpaRepository.existsById(id)) {
            return false
        }
        userJpaRepository.deleteById(id)
        return true
    }

    override fun findByEmail(email: String): User? = userJpaRepository.findByEmail(email)?.toDomain()

    override fun existsByEmail(email: String): Boolean = userJpaRepository.existsByEmail(email)
}
