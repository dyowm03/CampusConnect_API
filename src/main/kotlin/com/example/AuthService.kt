package com.example

import at.favre.lib.crypto.bcrypt.BCrypt
import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import io.ktor.http.*
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.javatime.*
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.javatime.datetime

class AuthService(private val jwtSecret: String, private val jwtIssuer: String, private val jwtAudience: String) {
    
    fun login(email: String, password: String): AuthResponse? {
        return transaction {
            try {
                println("Attempting login for email: $email")
                
                val userRow = Users.select { Users.email eq email }.firstOrNull()
                    ?: return@transaction null.also { println("User not found with email: $email") }
                
                val storedHash = userRow[Users.passwordHash]
                println("Found user: ${userRow[Users.name]}, role: ${userRow[Users.role]}")
                
                val verifyer = BCrypt.verifyer()
                val result = verifyer.verify(password.toCharArray(), storedHash)
                if (!result.verified) {
                    println("Password verification failed for user: $email")
                    return@transaction null
                }
                
                val userId = userRow[Users.id].value
                val roleString = userRow[Users.role].toString()
                val userRole = try {
                    UserRole.valueOf(roleString)
                } catch (e: IllegalArgumentException) {
                    UserRole.STUDENT
                }
                
                val token = generateToken(
                    userId = userId,
                    role = userRole
                )
                
                AuthResponse(
                    token = token,
                    userId = userId,
                    role = userRole
                )
            } catch (e: Exception) {
                println("Error during login for email $email: ${e.message}")
                e.printStackTrace()
                null
            }
        }
    }
    
    fun register(name: String, email: String, password: String, role: UserRole): User? {
        return try {
            transaction {
                if (Users.select { Users.email eq email }.count() > 0) {
                    return@transaction null
                }
                
                val hashedPassword = BCrypt.withDefaults().hashToString(12, password.toCharArray())
                
                val result = Users.insert {
                    it[Users.name] = name
                    it[Users.email] = email
                    it[Users.passwordHash] = hashedPassword
                    it[Users.role] = role.name
                    it[Users.createdAt] = LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME)
                }
                
                val userId = result[Users.id].value
                User(
                    id = userId,
                    name = name,
                    email = email,
                    role = role
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
    
    private fun generateToken(userId: Int, role: UserRole): String {
        return JWT.create()
            .withAudience(*jwtAudience.split(",").toTypedArray())
            .withIssuer(jwtIssuer)
            .withClaim("userId", userId)
            .withClaim("role", role.name)
            .withExpiresAt(java.util.Date(System.currentTimeMillis() + 24 * 60 * 60 * 1000)) // 1 day
            .sign(Algorithm.HMAC256(jwtSecret))
    }
    
    fun validateToken(token: String): JWTTokenInfo? {
        return try {
            val algorithm = Algorithm.HMAC256(jwtSecret)
            val verifier = JWT.require(algorithm)
                .withIssuer(jwtIssuer)
                .build()
            
            val jwt = verifier.verify(token)
            val userId = jwt.getClaim("userId").asInt()
            val role = UserRole.valueOf(jwt.getClaim("role").asString())
            
            JWTTokenInfo(userId, role)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}

data class JWTTokenInfo(val userId: Int, val role: UserRole)
