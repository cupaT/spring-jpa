package com.example.springjpa.adapters.jpa.entity

import com.example.springjpa.domain.model.Restaurant
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.OneToMany
import jakarta.persistence.Table

@Entity
@Table(name = "restaurants")
class RestaurantEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @Column(name = "name", nullable = false)
    var name: String = "",

    @Column(name = "address", nullable = false)
    var address: String = "",

    @OneToMany(mappedBy = "restaurant")
    var dishes: MutableList<DishEntity> = mutableListOf(),
)

fun RestaurantEntity.toDomain(): Restaurant =
    Restaurant(
        id = this.id ?: 0,
        name = this.name,
        address = this.address,
    )

fun Restaurant.toEntity(): RestaurantEntity =
    RestaurantEntity(
        id = this.id.takeIf { it > 0 },
        name = this.name,
        address = this.address,
    )
