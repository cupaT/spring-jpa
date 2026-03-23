package com.example.springjpa.adapters.jpa.entity

import com.example.springjpa.domain.model.Dish
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToMany
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.math.BigDecimal

@Entity
@Table(name = "dishes")
class DishEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @Column(name = "name", nullable = false)
    var name: String = "",

    @Column(name = "description", nullable = false)
    var description: String = "",

    @Column(name = "price", nullable = false, precision = 14, scale = 2)
    var price: BigDecimal = BigDecimal.ZERO,

    @Column(name = "is_available", nullable = false)
    var available: Boolean = true,

    @ManyToOne(optional = false)
    @JoinColumn(name = "restaurant_id", nullable = false)
    var restaurant: RestaurantEntity? = null,

    @ManyToMany(mappedBy = "dishes")
    var orders: MutableSet<OrderEntity> = linkedSetOf(),
)

fun DishEntity.toDomain(): Dish =
    Dish(
        id = this.id ?: 0,
        name = this.name,
        description = this.description,
        price = this.price,
        isAvailable = this.available,
        restaurantId = this.restaurant?.id ?: 0,
    )

fun Dish.toEntity(restaurant: RestaurantEntity): DishEntity =
    DishEntity(
        id = this.id.takeIf { it > 0 },
        name = this.name,
        description = this.description,
        price = this.price,
        available = this.isAvailable,
        restaurant = restaurant,
    )
