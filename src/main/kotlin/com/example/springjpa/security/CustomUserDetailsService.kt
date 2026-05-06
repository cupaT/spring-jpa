package com.example.springjpa.security

import com.example.springjpa.application.port.UserRepositoryPort
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.core.userdetails.UsernameNotFoundException
import org.springframework.stereotype.Service

@Service
class CustomUserDetailsService(
    private val userRepositoryPort: UserRepositoryPort,
) : UserDetailsService {
    override fun loadUserByUsername(username: String): UserDetails {
        val user = userRepositoryPort.findByEmail(username)
            ?: throw UsernameNotFoundException("User not found")

        return AuthenticatedUser(
            id = user.id,
            email = user.email,
            passwordHash = user.password,
            role = user.role,
        )
    }
}
