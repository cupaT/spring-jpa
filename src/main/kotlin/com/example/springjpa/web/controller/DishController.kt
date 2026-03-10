package com.example.springjpa.web.controller

import com.example.springjpa.application.service.DishService
import com.example.springjpa.web.dto.DishCreateRequest
import com.example.springjpa.web.dto.DishResponse
import com.example.springjpa.web.dto.DishUpdateRequest
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
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1")
class DishController(
    private val dishService: DishService,
) {
    @GetMapping("/dishes")
    fun listDishes(@RequestParam(required = false) namePart: String?): List<DishResponse> =
        dishService.list(namePart).map { it.toResponse() }

    @PostMapping("/restaurants/{restaurantId}/dishes")
    fun createDishInRestaurant(
        @PathVariable restaurantId: Long,
        @Valid @RequestBody request: DishCreateRequest,
    ): ResponseEntity<DishResponse> =
        ResponseEntity
            .status(201)
            .body(dishService.createInRestaurant(restaurantId, request.toDomain(restaurantId)).toResponse())

    @GetMapping("/dishes/{id}")
    fun getDishById(@PathVariable id: Long): DishResponse = dishService.getById(id).toResponse()

    @PutMapping("/dishes/{id}")
    fun updateDish(
        @PathVariable id: Long,
        @Valid @RequestBody request: DishUpdateRequest,
    ): DishResponse {
        val existing = dishService.getById(id)
        return dishService.update(id, request.toDomain(id, existing.restaurantId)).toResponse()
    }

    @DeleteMapping("/dishes/{id}")
    fun deleteDish(@PathVariable id: Long): ResponseEntity<Void> {
        dishService.delete(id)
        return ResponseEntity.noContent().build()
    }
}
