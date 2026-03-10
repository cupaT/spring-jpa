package com.example.springjpa.application.port

import com.example.springjpa.domain.model.Restaurant

interface RestaurantRepositoryPort {
    fun create(restaurant: Restaurant): Restaurant
    fun findById(id: Long): Restaurant?
    fun findAll(): List<Restaurant>
    fun update(restaurant: Restaurant): Restaurant?
    fun deleteById(id: Long): Boolean
}
