package com.example.springjpa.adapters.jpa.entity

import com.example.springjpa.domain.model.User
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table

@Entity
@Table(name = "users")
class UserEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @Column(nullable = false, unique = true)
    var email: String = "",

    @Column(nullable = false)
    var firstName: String = "",

    @Column(nullable = false)
    var lastName: String = "",

    @Column(nullable = false)
    var active: Boolean = true,
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
