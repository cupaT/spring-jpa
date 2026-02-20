package com.example.springjpa.adapters.mock

import com.example.springjpa.application.port.UserRepositoryPort
import com.example.springjpa.domain.model.User
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Repository
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

@Repository
@ConditionalOnProperty(name = ["app.data-provider"], havingValue = "mock")
class UserMockRepositoryAdapter : UserRepositoryPort {
    private val sequence = AtomicLong(1)
    private val storage = ConcurrentHashMap<Long, User>()

    override fun create(user: User): User {
        val id = sequence.getAndIncrement()
        val saved = user.copy(id = id)
        storage[id] = saved
        return saved
    }

    override fun findById(id: Long): User? = storage[id]

    override fun findAll(): List<User> = storage.values.sortedBy { it.id }

    override fun update(user: User): User? {
        if (!storage.containsKey(user.id)) {
            return null
        }
        storage[user.id] = user
        return user
    }

    override fun deleteById(id: Long): Boolean = storage.remove(id) != null

    override fun findByEmail(email: String): User? = storage.values.firstOrNull { it.email == email }
}
