package com.example.springjpa.web.dto

import com.example.springjpa.domain.model.User
import com.fasterxml.jackson.annotation.JsonProperty
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank

data class UserCreateRequest(
    @field:Email
    @field:NotBlank
    val email: String,
    @field:NotBlank
    val firstName: String,
    @field:NotBlank
    val lastName: String,
    @field:JsonProperty("isActive")
    val isActive: Boolean = true,
)

data class UserUpdateRequest(
    @field:Email
    @field:NotBlank
    val email: String,
    @field:NotBlank
    val firstName: String,
    @field:NotBlank
    val lastName: String,
    @field:JsonProperty("isActive")
    val isActive: Boolean,
)

data class UserResponse(
    val id: Long,
    val email: String,
    val firstName: String,
    val lastName: String,
    @field:JsonProperty("isActive")
    val isActive: Boolean,
)

fun UserCreateRequest.toDomain(): User =
    User(
        id = 0,
        email = this.email,
        firstName = this.firstName,
        lastName = this.lastName,
        isActive = this.isActive,
    )

fun UserUpdateRequest.toDomain(id: Long): User =
    User(
        id = id,
        email = this.email,
        firstName = this.firstName,
        lastName = this.lastName,
        isActive = this.isActive,
    )

fun User.toResponse(): UserResponse =
    UserResponse(
        id = this.id,
        email = this.email,
        firstName = this.firstName,
        lastName = this.lastName,
        isActive = this.isActive,
    )
