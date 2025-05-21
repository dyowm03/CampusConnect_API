package com.example

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.sql.*
import java.util.*

fun Application.configureRouting() {
    val authService = AuthService(Config.JWT_SECRET, Config.JWT_ISSUER, Config.JWT_AUDIENCE)
    val collegeService = CollegeService

    install(Authentication) {
        jwt("auth-jwt") {
            realm = Config.JWT_REALM
            verifier(
                JWT
                    .require(Algorithm.HMAC256(Config.JWT_SECRET))
                    .withAudience(*Config.JWT_AUDIENCE.split(",").toTypedArray())
                    .withIssuer(Config.JWT_ISSUER)
                    .build()
            )
            validate { credential ->
                if (credential.payload.getClaim("userId").asInt() != null) {
                    JWTPrincipal(credential.payload)
                } else {
                    null
                }
            }
        }
    }

    routing {
        get("/") {
            call.respondText("College Management System API is running!")
        }

        route("/auth") {
            post("/login") {
                try {
                    println("Received login request")
                    val request = call.receive<LoginRequest>()
                    println("Login attempt for email: ${request.email}")
                    
                    val response = authService.login(request.email, request.password)
                    
                    if (response != null) {
                        println("Login successful for user: ${request.email}")
                        call.respond(response)
                    } else {
                        println("Login failed for user: ${request.email} - Invalid credentials")
                        call.respond(HttpStatusCode.Unauthorized, ErrorResponse("Invalid email or password"))
                    }
                } catch (e: Exception) {
                    println("Error in login endpoint: ${e.message}")
                    e.printStackTrace()
                    call.respond(HttpStatusCode.InternalServerError, ErrorResponse("An error occurred during login"))
                }
            }

            post("/register") {
                val request = call.receive<RegisterRequest>()
                val user = authService.register(
                    name = request.name,
                    email = request.email,
                    password = request.password,
                    role = UserRole.STUDENT
                )
                
                if (user != null) {
                    call.respond(HttpStatusCode.Created, user)
                } else {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse("Registration failed. Email may already be in use."))
                }
            }
            
            post("/register/teacher") {
                val request = call.receive<RegisterRequest>()
                val user = authService.register(
                    name = request.name,
                    email = request.email,
                    password = request.password,
                    role = UserRole.TEACHER
                )
                
                if (user != null) {
                    call.respond(HttpStatusCode.Created, user)
                } else {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse("Teacher registration failed. Email may already be in use."))
                }
            }
        }

        authenticate("auth-jwt") {
            route("/student") {
                post("/attendance") {
                    val principal = call.principal<JWTPrincipal>()
                    val studentId = principal?.payload?.getClaim("userId")?.asInt()
                        ?: return@post call.respond(HttpStatusCode.Unauthorized)
                    
                    val request = call.receive<MarkAttendanceRequest>()
                    val success = collegeService.markAttendance(
                        studentId = studentId,
                        date = request.date,
                        isPresent = request.isPresent
                    )
                    
                    if (success) {
                        call.respond(HttpStatusCode.OK, mapOf("message" to "Attendance marked successfully"))
                    } else {
                        call.respond(HttpStatusCode.BadRequest, ErrorResponse("Failed to mark attendance"))
                    }
                }

                get("/attendance") {
                    val principal = call.principal<JWTPrincipal>()
                    val studentId = principal?.payload?.getClaim("userId")?.asInt()
                        ?: return@get call.respond(HttpStatusCode.Unauthorized)
                    
                    val attendance = collegeService.getStudentAttendance(studentId)
                    call.respond(attendance)
                }

                get("/grades") {
                    val principal = call.principal<JWTPrincipal>()
                    principal?.payload?.getClaim("userId")?.asInt()
                        ?: return@get call.respond(HttpStatusCode.Unauthorized)
                    
                    val grades = emptyList<Any>()
                    call.respond(grades)
                }
            }

            route("/faculty") {
                post("/attendance/{studentId}") {
                    call.principal<JWTPrincipal>()?.payload?.getClaim("userId")?.asInt()
                        ?: return@post call.respond(HttpStatusCode.Unauthorized)
                    
                    val studentId = call.parameters["studentId"]?.toIntOrNull()
                        ?: return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid student ID"))
                    
                    val request = call.receive<MarkAttendanceRequest>()
                    val success = collegeService.markAttendance(
                        studentId = studentId,
                        date = request.date,
                        isPresent = request.isPresent
                    )
                    
                    if (success) {
                        call.respond(HttpStatusCode.OK, mapOf("message" to "Attendance marked successfully"))
                    } else {
                        call.respond(HttpStatusCode.BadRequest, ErrorResponse("Failed to mark attendance"))
                    }
                }

                post("/announcements") {
                    val facultyId = call.principal<JWTPrincipal>()?.payload?.getClaim("userId")?.asInt()
                        ?: return@post call.respond(HttpStatusCode.Unauthorized)
                    
                    val request = call.receive<CreateAnnouncementRequest>()
                    val announcement = collegeService.createAnnouncement(
                        title = request.title,
                        content = request.content,
                        authorId = facultyId
                    )
                    
                    if (announcement != null) {
                        call.respond(HttpStatusCode.Created, announcement)
                    } else {
                        call.respond(HttpStatusCode.InternalServerError, ErrorResponse("Failed to create announcement"))
                    }
                }
            }

            route("/admin") {
                get("/dashboard") {
                    call.respond(mapOf("message" to "Admin dashboard"))
                }
            }

            route("/teacher") {
                get("/dashboard") {
                    val principal = call.principal<JWTPrincipal>()
                    val teacherId = principal?.payload?.getClaim("userId")?.asInt()
                        ?: return@get call.respond(HttpStatusCode.Unauthorized)
                    
                    call.respond(mapOf(
                        "message" to "Teacher dashboard",
                        "teacherId" to teacherId
                    ))
                }

                get("/students") {
                    val principal = call.principal<JWTPrincipal>()
                    val teacherId = principal?.payload?.getClaim("userId")?.asInt()
                        ?: return@get call.respond(HttpStatusCode.Unauthorized)
                    
                    val students = CollegeService.getStudentsForTeacher(teacherId)
                    call.respond(students)
                }

                post("/attendance/{studentId}") {
                    val principal = call.principal<JWTPrincipal>()
                    val teacherId = principal?.payload?.getClaim("userId")?.asInt()
                        ?: return@post call.respond(HttpStatusCode.Unauthorized)
                    
                    val studentId = call.parameters["studentId"]?.toIntOrNull()
                        ?: return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid student ID"))
                    
                    val request = call.receive<MarkAttendanceRequest>()
                    val success = CollegeService.markAttendance(
                        studentId = studentId,
                        date = request.date,
                        isPresent = request.isPresent,
                        markedBy = teacherId
                    )
                    
                    if (success) {
                        call.respond(HttpStatusCode.OK, mapOf("message" to "Attendance marked successfully"))
                    } else {
                        call.respond(HttpStatusCode.BadRequest, ErrorResponse("Failed to mark attendance"))
                    }
                }
            }
        }
    }
}
