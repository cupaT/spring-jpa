package com.example.springjpa.web.dto

import com.example.springjpa.domain.model.Dish
import com.fasterxml.jackson.annotation.JsonProperty
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Positive
import java.math.BigDecimal

data class DishCreateRequest(
    @field:NotBlank(message = "Dish name must not be blank")
    val name: String,
    @field:NotBlank(message = "Description must not be blank")
    val description: String,
    @field:Positive(message = "Price must be greater than 0")
    val price: BigDecimal,
    @field:JsonProperty("isAvailable")
    val isAvailable: Boolean,
)

data class DishUpdateRequest(
    @field:NotBlank(message = "Dish name must not be blank")
    val name: String,
    @field:NotBlank(message = "Description must not be blank")
    val description: String,
    @field:Positive(message = "Price must be greater than 0")
    val price: BigDecimal,
    @field:JsonProperty("isAvailable")
    val isAvailable: Boolean,
)

data class DishResponse(
    val id: Long,
    val name: String,
    val description: String,
    val price: BigDecimal,
    @field:JsonProperty("isAvailable")
    val isAvailable: Boolean,
    val restaurantId: Long,
)

fun DishCreateRequest.toDomain(restaurantId: Long): Dish =
    Dish(
        id = 0,
        name = this.name,
        description = this.description,
        price = this.price,
        isAvailable = this.isAvailable,
        restaurantId = restaurantId,
    )

fun DishUpdateRequest.toDomain(id: Long, restaurantId: Long): Dish =
    Dish(
        id = id,
        name = this.name,
        description = this.description,
        price = this.price,
        isAvailable = this.isAvailable,
        restaurantId = restaurantId,
    )

fun Dish.toResponse(): DishResponse =
    DishResponse(
        id = this.id,
        name = this.name,
        description = this.description,
        price = this.price,
        isAvailable = this.isAvailable,
        restaurantId = this.restaurantId,
    )
