package com.example.springjpa.adapters.jpa.entity

import com.example.springjpa.domain.model.User
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.OneToMany
import jakarta.persistence.Table

@Entity
@Table(name = "users")
class UserEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @Column(name = "email", nullable = false, unique = true)
    var email: String = "",

    @Column(name = "first_name", nullable = false)
    var firstName: String = "",

    @Column(name = "last_name", nullable = false)
    var lastName: String = "",

    @Column(name = "is_active", nullable = false)
    var active: Boolean = true,

    @OneToMany(mappedBy = "user")
    var orders: MutableList<OrderEntity> = mutableListOf(),
)

fun UserEntity.toDomain(): User =
    User(
        id = this.id ?: 0,
        email = this.email,
        firstName = this.firstName,
        lastName = this.lastName,
        isActive = this.active,
    )

fun User.toEntity(): UserEntity =
    UserEntity(
        id = this.id.takeIf { it > 0 },
        email = this.email,
        firstName = this.firstName,
        lastName = this.lastName,
        active = this.isActive,
    )
