package com.example.springjpa.application.exception

sealed class AppException(message: String) : RuntimeException(message)
