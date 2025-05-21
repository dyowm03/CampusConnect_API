package com.example

import kotlinx.serialization.Serializable
import org.jetbrains.exposed.sql.Table

enum class UserRole {
    ADMIN, FACULTY, STUDENT, TEACHER
}

@Serializable
data class LoginRequest(
    val email: String, 
    val password: String
)

@Serializable
data class AuthResponse(
    val token: String, 
    val userId: Int, 
    val role: UserRole
)

@Serializable
data class User(
    val id: Int,
    val name: String,
    val email: String,
    val role: UserRole
)

@Serializable
data class Announcement(
    val id: Int,
    val title: String,
    val content: String,
    val authorId: Int,
    val createdAt: String
)

@Serializable
data class Attendance(
    val id: Int,
    val studentId: Int,
    val date: String,
    val isPresent: Boolean,
    val markedBy: Int? = null
)
