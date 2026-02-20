package com.example.springjpa.adapters.jpa.entity

import com.example.springjpa.domain.model.Dish
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.math.BigDecimal

@Entity
@Table(name = "dishes")
class DishEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @Column(nullable = false, unique = true)
    var name: String = "",

    @Column(nullable = false)
    var description: String = "",

    @Column(nullable = false, precision = 14, scale = 2)
    var price: BigDecimal = BigDecimal.ZERO,

    @Column(nullable = false)
    var available: Boolean = true,
)

fun DishEntity.toDomain(): Dish =
    Dish(
        id = this.id ?: 0,
        name = this.name,
        description = this.description,
        price = this.price,
        isAvailable = this.available,
    )

fun Dish.toEntity(): DishEntity =
    DishEntity(
        id = this.id.takeIf { it > 0 },
        name = this.name,
        description = this.description,
        price = this.price,
        available = this.isAvailable,
    )
