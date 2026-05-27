package com.example.springjpa.web.dto

import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class RegisterRequest(
    @field:Email(message = "Incorrect email format")
    @field:NotBlank(message = "Email is required")
    val email: String,

    @field:NotBlank(message = "Password is required")
    @field:Size(min = 6, message = "Password must contain at least 6 characters")
    val password: String,

    @field:NotBlank(message = "Name is required")
    val name: String,
)

data class LoginRequest(
    @field:Email(message = "Incorrect email format")
    @field:NotBlank(message = "Email is required")
    val email: String,

    @field:NotBlank(message = "Password is required")
    val password: String,
)

data class AuthResponse(
    val token: String,
    val email: String,
    val role: String,
)
