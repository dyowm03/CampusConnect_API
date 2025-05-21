package com.example

import at.favre.lib.crypto.bcrypt.BCrypt
import kotlinx.coroutines.Dispatchers
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.javatime.datetime
import org.jetbrains.exposed.dao.id.EntityID
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import com.example.Announcement
import com.example.Attendance
import com.example.User
import com.example.UserRole

object CollegeService {
    fun createAnnouncement(title: String, content: String, authorId: Int): Announcement? {
        return try {
            transaction {
                val now = LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME)
                val id = Announcements.insertAndGetId {
                    it[Announcements.title] = title
                    it[Announcements.content] = content
                    it[Announcements.authorId] = authorId
                    it[Announcements.createdAt] = now
                }
                
                Announcement(
                    id = id.value,
                    title = title,
                    content = content,
                    authorId = authorId,
                    createdAt = now
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
    
    fun getAnnouncements(): List<Announcement> {
        return try {
            transaction {
                Announcements.selectAll()
                    .orderBy(Announcements.createdAt to SortOrder.DESC)
                    .map { row ->
                        Announcement(
                            id = row[Announcements.id].value,
                            title = row[Announcements.title],
                            content = row[Announcements.content],
                            authorId = row[Announcements.authorId],
                            createdAt = row[Announcements.createdAt]
                        )
                    }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }
    
    fun markAttendance(studentId: Int, date: String, isPresent: Boolean, markedBy: Int? = null): Boolean {
        return try {
            transaction {
                val existing = Attendances.select {
                    (Attendances.studentId eq studentId) and (Attendances.date eq date)
                }.firstOrNull()

                if (existing != null) {
                    Attendances.update({ (Attendances.studentId eq studentId) and (Attendances.date eq date) }) {
                        it[Attendances.isPresent] = isPresent
                        it[Attendances.markedBy] = markedBy
                    } > 0
                } else {
                    val result = Attendances.insertAndGetId {
                        it[Attendances.studentId] = studentId
                        it[Attendances.date] = date
                        it[Attendances.isPresent] = isPresent
                        it[Attendances.markedBy] = markedBy
                    }
                    result.value > 0
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
    
    fun getStudentAttendance(studentId: Int): List<Attendance> {
        return try {
            transaction {
                Attendances.select { Attendances.studentId eq studentId }
                    .orderBy(Attendances.date to SortOrder.DESC)
                    .map { row ->
                        Attendance(
                            id = row[Attendances.id].value,
                            studentId = row[Attendances.studentId],
                            date = row[Attendances.date],
                            isPresent = row[Attendances.isPresent],
                            markedBy = row[Attendances.markedBy]
                        )
                    }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }
    
    fun getUserById(userId: Int): User? {
        return try {
            transaction {
                Users.select { Users.id eq userId }
                    .map { row ->
                        User(
                            id = row[Users.id].value,
                            name = row[Users.name],
                            email = row[Users.email],
                            role = UserRole.valueOf(row[Users.role])
                        )
                    }.singleOrNull()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
    
    fun getStudentsForTeacher(teacherId: Int): List<User> {
        return try {
            transaction {
                @Suppress("UNUSED_VARIABLE")
                val teacherExists = Users.select { Users.id eq teacherId and (Users.role eq UserRole.TEACHER.name) }
                    .count() > 0
                if (!teacherExists) return@transaction emptyList()
                
                
                
                Users.select { Users.role eq UserRole.STUDENT.name }
                    .map { row ->
                        User(
                            id = row[Users.id].value,
                            name = row[Users.name],
                            email = row[Users.email],
                            role = UserRole.STUDENT
                        )
                    }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }
}
