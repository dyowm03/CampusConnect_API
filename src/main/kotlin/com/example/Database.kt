package com.example

import at.favre.lib.crypto.bcrypt.BCrypt
import kotlinx.coroutines.Dispatchers
import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import org.jetbrains.exposed.sql.javatime.datetime
import com.example.migrations.AddMarkedByToAttendances
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.sql.ReferenceOption

// Database Tables
object Users : IntIdTable("users") {
    val name = varchar("name", 255)
    val email = varchar("email", 255).uniqueIndex()
    val passwordHash = varchar("password", 255)
    val role = varchar("role", 20)
    val createdAt = varchar("created_at", 50)
    
    init {
        // Add any table constraints or indices here if needed
    }
}

object Announcements : IntIdTable("announcements") {
    val title = text("title")
    val content = text("content")
    val authorId = integer("author_id").references(Users.id, onDelete = ReferenceOption.CASCADE)
    val createdAt = varchar("created_at", 50)
    
    init {
        // Add any table constraints or indices here if needed
    }
}

object Attendances : IntIdTable("attendances") {
    val studentId = integer("student_id").references(Users.id, onDelete = ReferenceOption.CASCADE)
    val date = varchar("date", 50)
    val isPresent = bool("is_present").default(false)
    val markedBy = integer("marked_by").references(Users.id, onDelete = ReferenceOption.SET_NULL).nullable()
    
    init {
        // Add any table constraints or indices here if needed
    }
}

// Database setup
object DatabaseFactory {
    fun init() {
        // H2 in-memory database configuration
        val config = HikariConfig().apply {
            jdbcUrl = "jdbc:h2:mem:test;DB_CLOSE_DELAY=-1;MODE=PostgreSQL"
            driverClassName = "org.h2.Driver"
            username = "sa"
            password = ""
            maximumPoolSize = 10
            isAutoCommit = false
            validate()
        }
        
        val dataSource = HikariDataSource(config)
        
        // Connect to the database
        Database.connect(dataSource)
        
        // Create tables
        transaction {
            // Create tables if they don't exist
            SchemaUtils.create(Users)
            SchemaUtils.create(Announcements)
            SchemaUtils.create(Attendances)
            
            // Add admin user if not exists
            if (Users.selectAll().empty()) {
                val hashedPassword = BCrypt.withDefaults()
                    .hashToString(10, "admin123".toCharArray())
                
                Users.insert {
                    it[name] = "Admin User"
                    it[email] = "admin@college.edu"
                    it[passwordHash] = hashedPassword
                    it[role] = UserRole.ADMIN.name
                    it[createdAt] = LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME)
                }
                
                // Add a sample student for testing
                val studentPassword = BCrypt.withDefaults()
                    .hashToString(10, "student123".toCharArray())
                    
                Users.insert {
                    it[name] = "John Doe"
                    it[email] = "student@college.edu"
                    it[passwordHash] = studentPassword
                    it[role] = UserRole.STUDENT.name
                    it[createdAt] = LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME)
                }
            }
            
            // Run migrations
            AddMarkedByToAttendances.migrate()
            
            // Add a sample announcement if none exists
            if (Announcements.selectAll().empty()) {
                val adminId = Users.select { Users.email eq "admin@college.edu" }
                    .map { it[Users.id] }
                    .firstOrNull()
                
                adminId?.let { id ->
                    Announcements.insert {
                        it[title] = "Welcome to College Management System"
                        it[content] = "This is a sample announcement. More features coming soon!"
                        it[authorId] = id.value
                        it[createdAt] = LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME)
                    }
                }
            }
        }
    }
}

// Password hashing utility
object BCryptUtil {
    fun hash(password: String): String {
        return BCrypt.withDefaults().hashToString(12, password.toCharArray())
    }
    
    fun verify(password: String, hash: String): Boolean {
        return BCrypt.verifyer().verify(password.toCharArray(), hash).verified
    }
}
