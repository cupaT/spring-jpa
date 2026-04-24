package com.example.springjpa.web.controller

import com.example.springjpa.application.service.DishService
import com.example.springjpa.application.service.RestaurantService
import com.example.springjpa.web.dto.RestaurantCreateRequest
import com.example.springjpa.web.dto.RestaurantResponse
import com.example.springjpa.web.dto.RestaurantUpdateRequest
import com.example.springjpa.web.dto.toDomain
import com.example.springjpa.web.dto.toResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import jakarta.validation.constraints.Min
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.validation.annotation.Validated
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
@Validated
@Tag(name = "Restaurants", description = "Управление ресторанами")
class RestaurantController(
    private val restaurantService: RestaurantService,
    private val dishService: DishService,
) {
    @GetMapping
    @Operation(summary = "Получить список ресторанов")
    @ApiResponse(responseCode = "200", description = "Список ресторанов получен")
    fun listRestaurants(): List<RestaurantResponse> = restaurantService.list().map { it.toResponse() }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Создать ресторан")
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "201", description = "Ресторан создан"),
            ApiResponse(responseCode = "403", description = "Недостаточно прав"),
        ],
    )
    fun createRestaurant(@Valid @RequestBody request: RestaurantCreateRequest): ResponseEntity<RestaurantResponse> =
        ResponseEntity.status(201).body(restaurantService.create(request.toDomain()).toResponse())

    @GetMapping("/{id}")
    @Operation(summary = "Получить ресторан по ID")
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Ресторан найден"),
            ApiResponse(responseCode = "404", description = "Ресторан не найден"),
        ],
    )
    fun getRestaurantById(@PathVariable @Min(1) id: Long): RestaurantResponse = restaurantService.getById(id).toResponse()

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    fun updateRestaurant(
        @PathVariable @Min(1) id: Long,
        @Valid @RequestBody request: RestaurantUpdateRequest,
    ): RestaurantResponse = restaurantService.update(id, request.toDomain(id)).toResponse()

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    fun deleteRestaurant(@PathVariable @Min(1) id: Long): ResponseEntity<Void> {
        restaurantService.delete(id)
        return ResponseEntity.noContent().build()
    }

    @GetMapping("/{id}/dishes")
    fun getRestaurantDishes(@PathVariable @Min(1) id: Long) = dishService.listByRestaurantId(id).map { it.toResponse() }
}
