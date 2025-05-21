package com.example

import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.transactions.transaction

@Serializable
data class ExposedUser(val id: Int = 0, val name: String, val age: Int)

class UserService(private val database: Database) {
    object Users : IntIdTable("users") {
        val name = varchar("name", 50)
        val age = integer("age")
    }

    init {
        transaction(database) {
            SchemaUtils.create(Users)
        }
    }

    private suspend fun <T> dbQuery(block: suspend () -> T): T {
        return newSuspendedTransaction(Dispatchers.IO, database) {
            block()
        }
    }

    suspend fun create(user: ExposedUser): Int = dbQuery {
        Users.insert {
            it[name] = user.name
            it[age] = user.age
        }[Users.id].value
    }

    suspend fun read(id: Int): ExposedUser? = dbQuery {
        Users.select { Users.id eq id }
            .map { ExposedUser(it[Users.id].value, it[Users.name], it[Users.age]) }
            .singleOrNull()
    }

    suspend fun update(id: Int, user: ExposedUser) {
        dbQuery {
            Users.update({ Users.id eq id }) {
                it[name] = user.name
                it[age] = user.age
            }
        }
    }

    suspend fun delete(id: Int) {
        dbQuery {
            Users.deleteWhere { Users.id eq id }
        }
    }

    suspend fun getAll(): List<ExposedUser> = dbQuery {
        Users.selectAll()
            .map { ExposedUser(it[Users.id].value, it[Users.name], it[Users.age]) }
    }
}
