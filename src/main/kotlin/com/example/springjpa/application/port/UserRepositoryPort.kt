package com.example.springjpa.application.port

import com.example.springjpa.domain.model.User

interface UserRepositoryPort {
    fun create(user: User): User
    fun findById(id: Long): User?
    fun findAll(): List<User>
    fun update(user: User): User?
    fun deleteById(id: Long): Boolean
    fun findByEmail(email: String): User?
    fun existsByEmail(email: String): Boolean
}
