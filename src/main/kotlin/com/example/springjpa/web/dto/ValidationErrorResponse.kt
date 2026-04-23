package com.example.springjpa.web.dto

import java.time.LocalDateTime

class ValidationErrorResponse(
    status: Int,
    message: String? = null,
    val errors: Map<String, String>,
    timestamp: LocalDateTime = LocalDateTime.now(),
) : ErrorResponse(status, message, timestamp)
