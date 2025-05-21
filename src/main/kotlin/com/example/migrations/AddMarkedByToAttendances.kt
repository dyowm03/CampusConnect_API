package com.example.migrations

import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import com.example.Attendances

object AddMarkedByToAttendances {
    fun migrate() {
        transaction {
            try {

                exec("""
                    ALTER TABLE attendances 
                    ADD COLUMN IF NOT EXISTS marked_by INT 
                    REFERENCES users(id) 
                    ON DELETE SET NULL
                """.trimIndent())
            } catch (e: Exception) {

                println("Migration AddMarkedByToAttendances: ${e.message}")
            }
        }
    }
}
