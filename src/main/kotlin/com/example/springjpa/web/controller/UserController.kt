package com.example.springjpa.web.controller

import com.example.springjpa.application.service.UserService
import com.example.springjpa.web.dto.UserCreateRequest
import com.example.springjpa.web.dto.UserResponse
import com.example.springjpa.web.dto.UserUpdateRequest
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
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/users")
class UserController(
    private val userService: UserService,
) {
    @GetMapping
    fun listUsers(): List<UserResponse> = userService.list().map { it.toResponse() }

    @PostMapping
    fun createUser(@Valid @RequestBody request: UserCreateRequest): ResponseEntity<UserResponse> {
        val result = userService.createOrGetExisting(request.toDomain())
        val status = if (result.created) HttpStatus.CREATED else HttpStatus.OK
        return ResponseEntity.status(status).body(result.value.toResponse())
    }

    @GetMapping("/{id}")
    fun getUserById(@PathVariable id: Long): UserResponse = userService.getById(id).toResponse()

    @PutMapping("/{id}")
    fun updateUser(
        @PathVariable id: Long,
        @Valid @RequestBody request: UserUpdateRequest,
    ): UserResponse = userService.update(id, request.toDomain(id)).toResponse()

    @DeleteMapping("/{id}")
    fun deleteUser(@PathVariable id: Long): ResponseEntity<Void> {
        userService.delete(id)
        return ResponseEntity.noContent().build()
    }
}
