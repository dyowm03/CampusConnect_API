package com.example

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.callloging.CallLogging
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.plugins.statuspages.StatusPagesConfig
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.request.*
import org.slf4j.event.Level
import kotlinx.serialization.Serializable

object Config {
    const val JWT_SECRET = "college_management_secret_key_123"
    const val JWT_ISSUER = "college-management"
    const val JWT_AUDIENCE = "students,faculty,admin"
    const val JWT_REALM = "College Management System"
    const val PORT = 8080
}


@Serializable
data class RegisterRequest(
    val name: String,
    val email: String,
    val password: String
)

@Serializable
data class MarkAttendanceRequest(
    val date: String,
    val isPresent: Boolean
)

@Serializable
data class CreateAnnouncementRequest(
    val title: String,
    val content: String
)

@Serializable
data class ErrorResponse(
    val error: String,
    val details: String? = null
) {
    constructor(error: String) : this(error, null)
}

fun main() {
    DatabaseFactory.init()
    
    embeddedServer(Netty, port = Config.PORT) {
        install(ContentNegotiation) {
            json()
        }
        
        install(CallLogging) {
            level = Level.INFO
            filter { call -> call.request.path().startsWith("/") }
        }
        
        install(StatusPages) {
            exception<Throwable> { call, cause ->
                call.application.environment.log.error("Unhandled exception", cause)
                call.respond(HttpStatusCode.InternalServerError, mapOf("error" to "Internal server error"))
            }
            
            status(HttpStatusCode.NotFound) { call, status ->
                call.respond(status, ErrorResponse("Not Found"))
            }
            
            status(HttpStatusCode.Unauthorized) { call, status ->
                call.respond(status, ErrorResponse("Unauthorized"))
            }
            
            status(HttpStatusCode.BadRequest) { call, status ->
                call.respond(status, ErrorResponse("Bad Request"))
            }
        }
        
        configureRouting()
        
    }.start(wait = true)
}



suspend fun ApplicationCall.respondSuccess(data: Any) {
    respond(HttpStatusCode.OK, data)
}

suspend fun ApplicationCall.respondCreated() {
    respond(HttpStatusCode.Created)
}

suspend fun ApplicationCall.respondBadRequest(message: String) {
    respond(HttpStatusCode.BadRequest, ErrorResponse(message))
}

suspend fun ApplicationCall.respondUnauthorized(message: String = "Unauthorized") {
    respond(HttpStatusCode.Unauthorized, ErrorResponse(message))
}

suspend fun ApplicationCall.respondNotFound(message: String = "Resource not found") {
    respond(HttpStatusCode.NotFound, ErrorResponse(message))
}

suspend fun ApplicationCall.respondInternalServerError(message: String = "Internal server error") {
    respond(HttpStatusCode.InternalServerError, ErrorResponse(message))
}

fun ApplicationCall.getUserId(): Int? = principal<JWTPrincipal>()?.payload?.getClaim("userId")?.asInt()
