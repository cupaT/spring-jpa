package com.example.springjpa.web.dto

import java.time.LocalDateTime

open class ErrorResponse(
    val status: Int,
    val message: String? = null,
    val timestamp: LocalDateTime = LocalDateTime.now(),
)
