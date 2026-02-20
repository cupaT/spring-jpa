package com.example.springjpa.web.dto

import com.example.springjpa.domain.model.Dish
import com.fasterxml.jackson.annotation.JsonProperty
import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.NotBlank
import java.math.BigDecimal

data class DishCreateRequest(
    @field:NotBlank
    val name: String,
    @field:NotBlank
    val description: String,
    @field:DecimalMin(value = "0.0", inclusive = true)
    val price: BigDecimal,
    @field:JsonProperty("isAvailable")
    val isAvailable: Boolean,
)

data class DishUpdateRequest(
    @field:NotBlank
    val name: String,
    @field:NotBlank
    val description: String,
    @field:DecimalMin(value = "0.0", inclusive = true)
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
)

fun DishCreateRequest.toDomain(): Dish =
    Dish(
        id = 0,
        name = this.name,
        description = this.description,
        price = this.price,
        isAvailable = this.isAvailable,
    )

fun DishUpdateRequest.toDomain(id: Long): Dish =
    Dish(
        id = id,
        name = this.name,
        description = this.description,
        price = this.price,
        isAvailable = this.isAvailable,
    )

fun Dish.toResponse(): DishResponse =
    DishResponse(
        id = this.id,
        name = this.name,
        description = this.description,
        price = this.price,
        isAvailable = this.isAvailable,
    )
