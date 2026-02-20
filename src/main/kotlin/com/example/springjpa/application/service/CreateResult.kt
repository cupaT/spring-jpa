package com.example.springjpa.application.service

data class CreateResult<T>(
    val value: T,
    val created: Boolean,
)
