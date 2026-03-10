package com.example.springjpa.web.controller

import com.example.springjpa.application.service.DishService
import com.example.springjpa.application.service.RestaurantService
import com.example.springjpa.web.dto.RestaurantCreateRequest
import com.example.springjpa.web.dto.RestaurantResponse
import com.example.springjpa.web.dto.RestaurantUpdateRequest
import com.example.springjpa.web.dto.toDomain
import com.example.springjpa.web.dto.toResponse
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/restaurants")
class RestaurantController(
    private val restaurantService: RestaurantService,
    private val dishService: DishService,
) {
    @GetMapping
    fun listRestaurants(): List<RestaurantResponse> = restaurantService.list().map { it.toResponse() }

    @PostMapping
    fun createRestaurant(@Valid @RequestBody request: RestaurantCreateRequest): ResponseEntity<RestaurantResponse> =
        ResponseEntity.status(201).body(restaurantService.create(request.toDomain()).toResponse())

    @GetMapping("/{id}")
    fun getRestaurantById(@PathVariable id: Long): RestaurantResponse = restaurantService.getById(id).toResponse()

    @PutMapping("/{id}")
    fun updateRestaurant(
        @PathVariable id: Long,
        @Valid @RequestBody request: RestaurantUpdateRequest,
    ): RestaurantResponse = restaurantService.update(id, request.toDomain(id)).toResponse()

    @DeleteMapping("/{id}")
    fun deleteRestaurant(@PathVariable id: Long): ResponseEntity<Void> {
        restaurantService.delete(id)
        return ResponseEntity.noContent().build()
    }

    @GetMapping("/{id}/dishes")
    fun getRestaurantDishes(@PathVariable id: Long) = dishService.listByRestaurantId(id).map { it.toResponse() }
}
