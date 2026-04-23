package com.example.springjpa.web.dto

import com.example.springjpa.domain.model.Restaurant
import jakarta.validation.constraints.NotBlank

data class RestaurantCreateRequest(
    @field:NotBlank
    val name: String,
    @field:NotBlank
    val address: String,
)

data class RestaurantUpdateRequest(
    @field:NotBlank
    val name: String,
    @field:NotBlank
    val address: String,
)

data class RestaurantResponse(
    val id: Long,
    val name: String,
    val address: String,
)

fun RestaurantCreateRequest.toDomain(): Restaurant =
    Restaurant(
        id = 0,
        name = this.name,
        address = this.address,
    )

fun RestaurantUpdateRequest.toDomain(id: Long): Restaurant =
    Restaurant(
        id = id,
        name = this.name,
        address = this.address,
    )

fun Restaurant.toResponse(): RestaurantResponse =
    RestaurantResponse(
        id = this.id,
        name = this.name,
        address = this.address,
    )
