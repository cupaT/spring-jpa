package com.example.springjpa.application.service

import com.example.springjpa.application.exception.AlreadyExistsException
import com.example.springjpa.application.port.UserRepositoryPort
import com.example.springjpa.domain.model.Role
import com.example.springjpa.domain.model.User
import com.example.springjpa.security.JwtService
import com.example.springjpa.web.dto.AuthResponse
import com.example.springjpa.web.dto.LoginRequest
import com.example.springjpa.web.dto.RegisterRequest
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service

@Service
class AuthService(
    private val userRepositoryPort: UserRepositoryPort,
    private val passwordEncoder: PasswordEncoder,
    private val jwtService: JwtService,
    private val authenticationManager: AuthenticationManager,
) {
    private val logger = KotlinLogging.logger {}

    fun register(request: RegisterRequest): AuthResponse {
        if (userRepositoryPort.existsByEmail(request.email)) {
            throw AlreadyExistsException("User with email ${request.email} already exists")
        }

        val saved = userRepositoryPort.create(
            User(
                id = 0,
                email = request.email,
                firstName = request.name,
                lastName = "-",
                isActive = true,
                password = passwordEncoder.encode(request.password) ?: "",
                role = Role.USER,
            )
        )
        logger.info { "User registered: email=${saved.email}" }
        return toAuthResponse(saved)
    }

    fun login(request: LoginRequest): AuthResponse {
        authenticationManager.authenticate(
            UsernamePasswordAuthenticationToken(request.email, request.password)
        )
        val user = userRepositoryPort.findByEmail(request.email)
            ?: throw org.springframework.security.authentication.BadCredentialsException("Bad credentials")

        logger.info { "User logged in: email=${user.email}" }
        return toAuthResponse(user)
    }

    private fun toAuthResponse(user: User): AuthResponse {
        val token = jwtService.generateToken(user.email, user.role.name)
        return AuthResponse(token = token, email = user.email, role = user.role.name)
    }
}
