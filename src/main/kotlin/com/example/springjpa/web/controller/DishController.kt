package com.example.springjpa.web.controller

import com.example.springjpa.application.service.DishService
import com.example.springjpa.web.dto.DishCreateRequest
import com.example.springjpa.web.dto.DishResponse
import com.example.springjpa.web.dto.DishUpdateRequest
import com.example.springjpa.web.dto.toDomain
import com.example.springjpa.web.dto.toResponse
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
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
@RequestMapping("/api/v1/dishes")
class DishController(
    private val dishService: DishService,
) {
    @GetMapping
    fun listDishes(@RequestParam(required = false) namePart: String?): List<DishResponse> =
        dishService.list(namePart).map { it.toResponse() }

    @PostMapping
    fun createDish(@Valid @RequestBody request: DishCreateRequest): ResponseEntity<DishResponse> {
        val result = dishService.createOrGetExisting(request.toDomain())
        val status = if (result.created) HttpStatus.CREATED else HttpStatus.OK
        return ResponseEntity.status(status).body(result.value.toResponse())
    }

    @GetMapping("/{id}")
    fun getDishById(@PathVariable id: Long): DishResponse = dishService.getById(id).toResponse()

    @PutMapping("/{id}")
    fun updateDish(
        @PathVariable id: Long,
        @Valid @RequestBody request: DishUpdateRequest,
    ): DishResponse = dishService.update(id, request.toDomain(id)).toResponse()

    @DeleteMapping("/{id}")
    fun deleteDish(@PathVariable id: Long): ResponseEntity<Void> {
        dishService.delete(id)
        return ResponseEntity.noContent().build()
    }
}
