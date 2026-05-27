package com.example.springjpa.application.exception

class OrderCreationException(
    val reason: String,
    message: String,
) : AppException(message)
