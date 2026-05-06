package com.example.springjpa

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.cache.annotation.EnableCaching

@SpringBootApplication
@EnableCaching
class SpringJpaApplication

fun main(args: Array<String>) {
    runApplication<SpringJpaApplication>(*args)
}
