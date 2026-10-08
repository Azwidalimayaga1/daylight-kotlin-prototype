package com.daylight.server

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.security.MessageDigest
import java.security.SecureRandom
import java.sql.Connection
import java.sql.DriverManager
import java.sql.SQLException
import java.time.Instant
import java.util.Base64
import java.util.UUID
import java.util.concurrent.Executors
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

class ApiException(val status: Int, override val message: String) : RuntimeException(message)

object Passwords {
    const val ITERATIONS = 600_000
    private val random = SecureRandom()
    fun hash(password: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(password.toCharArray(), salt, ITERATIONS, 256)
        return try { SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded }
        finally { spec.clearPassword() }
    }
    fun create(password: String): Pair<ByteArray, ByteArray> {
        val salt = ByteArray(16).also(random::nextBytes)
        return salt to hash(password, salt)
    }
    fun matches(password: String, salt: ByteArray, expected: ByteArray) = MessageDigest.isEqual(hash(password, salt), expected)
}

class DaylightApi(dbPath: String) : AutoCloseable {
    private val db: Connection = DriverManager.getConnection("jdbc:sqlite:$dbPath")
    private val gson = Gson()
    private val random = SecureRandom()
    private val failures = mutableMapOf<String, Pair<Long, Int>>()
    init {
        db.createStatement().use { s ->
            s.execute("PRAGMA foreign_keys=ON")
            s.execute("PRAGMA journal_mode=WAL")
            s.execute("""CREATE TABLE IF NOT EXISTS users (
                id TEXT PRIMARY KEY, email TEXT UNIQUE NOT NULL, salt BLOB NOT NULL, password_hash BLOB NOT NULL,
                display_name TEXT NOT NULL, daily_goal INTEGER NOT NULL DEFAULT 3,
                dark_mode INTEGER NOT NULL DEFAULT 0, reminders INTEGER NOT NULL DEFAULT 1)""")
            s.execute("CREATE TABLE IF NOT EXISTS sessions (token_hash TEXT PRIMARY KEY, user_id TEXT NOT NULL REFERENCES users(id), expires INTEGER NOT NULL)")
            s.execute("CREATE TABLE IF NOT EXISTS habits (id TEXT PRIMARY KEY, user_id TEXT NOT NULL REFERENCES users(id), title TEXT NOT NULL, completed INTEGER NOT NULL DEFAULT 0)")
        }
    }
    @Synchronized
    fun route(method: String, path: String, body: String, bearer: String?, client: String = "local"): Pair<Int, Any> {
        if (method == "GET" && path == "/health") return 200 to mapOf("status" to "ok", "database" to "SQLite", "api" to "Daylight Kotlin REST API")
        val input = if (body.isBlank()) JsonObject() else try { JsonParser.parseString(body).asJsonObject } catch (_: Exception) { throw ApiException(400, "Invalid JSON object.") }
        fun string(key: String) = try { input.get(key)?.asString ?: "" } catch (_: Exception) { throw ApiException(400, "Invalid $key.") }
        if (method == "POST" && (path == "/auth/register" || path == "/auth/login")) {
            val now = Instant.now().epochSecond
            failures.entries.removeIf { now - it.value.first > 300 }
            val attempt = failures[client]
            if (attempt != null && attempt.second >= 10) throw ApiException(429, "Too many attempts. Try again in five minutes.")
            failures[client] = (attempt?.first ?: now) to ((attempt?.second ?: 0) + 1)
            val email = string("email").trim().lowercase()
            val password = string("password")
            if (email.length > 254 || !Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$").matches(email)) throw ApiException(400, "Enter a valid email address.")
            if (password.length !in 8..128) throw ApiException(400, "Use a password of 8–128 characters.")
            val userId: String
            if (path == "/auth/register") {
                val name = string("displayName").trim()
                if (name.length !in 1..60) throw ApiException(400, "Enter a name of 1–60 characters.")
                userId = UUID.randomUUID().toString()
                val (salt, hash) = Passwords.create(password)
                db.autoCommit = false
                try {
                    db.prepareStatement("INSERT INTO users(id,email,salt,password_hash,display_name) VALUES(?,?,?,?,?)").use {
                        it.setString(1, userId); it.setString(2, email); it.setBytes(3, salt); it.setBytes(4, hash); it.setString(5, name); it.executeUpdate()
                    }
                    listOf("Drink a glass of water", "Move for 15 minutes", "Read a few pages").forEach { title ->
                        db.prepareStatement("INSERT INTO habits(id,user_id,title) VALUES(?,?,?)").use {
                            it.setString(1, UUID.randomUUID().toString()); it.setString(2, userId); it.setString(3, title); it.executeUpdate()
                        }
                    }
                    db.commit()
                } catch (e: SQLException) { db.rollback(); if (e.message.orEmpty().contains("UNIQUE")) throw ApiException(409, "An account with this email already exists."); throw e }
                finally { db.autoCommit = true }
            } else {
                db.prepareStatement("SELECT id,salt,password_hash FROM users WHERE email=?").use {
                    it.setString(1, email)
                    it.executeQuery().use { rs ->
                        if (!rs.next()) { Passwords.hash(password, ByteArray(16)); throw ApiException(401, "Email or password is incorrect.") }
                        if (!Passwords.matches(password, rs.getBytes("salt"), rs.getBytes("password_hash"))) throw ApiException(401, "Email or password is incorrect.")
                        userId = rs.getString("id")
                    }
                }
            }
            failures.remove(client)
            val token = Base64.getUrlEncoder().withoutPadding().encodeToString(ByteArray(32).also(random::nextBytes))
            db.prepareStatement("DELETE FROM sessions WHERE expires<=?").use { it.setLong(1, now); it.executeUpdate() }
            db.prepareStatement("INSERT INTO sessions VALUES(?,?,?)").use {
                it.setString(1, digest(token)); it.setString(2, userId); it.setLong(3, now + 86400); it.executeUpdate()
            }
            return (if (path.endsWith("register")) 201 else 200) to mapOf("token" to token, "user" to user(userId))
        }
        val uid = authenticate(bearer)
        when {
            method == "POST" && path == "/auth/logout" -> {
                db.prepareStatement("DELETE FROM sessions WHERE token_hash=?").use { it.setString(1, digest(bearer!!)); it.executeUpdate() }
                return 200 to mapOf("message" to "Signed out.")
            }
            method == "GET" && path == "/me" -> return 200 to user(uid)
            method == "PUT" && path == "/me/settings" -> {
                val name = string("displayName").trim()
                val goal = try { input.get("dailyGoal").asInt } catch (_: Exception) { throw ApiException(400, "Daily goal must be a number.") }
                fun bool(k: String): Boolean {
                    val p = input.get(k)
                    if (p == null || !p.isJsonPrimitive || !p.asJsonPrimitive.isBoolean) throw ApiException(400, "$k must be true or false.")
                    return p.asBoolean
                }
                val dark = bool("darkMode"); val reminders = bool("reminders")
                if (name.length !in 1..60 || goal !in 1..10) throw ApiException(400, "Use a name of 1–60 characters and a goal of 1–10.")
                db.prepareStatement("UPDATE users SET display_name=?,daily_goal=?,dark_mode=?,reminders=? WHERE id=?").use {
                    it.setString(1, name); it.setInt(2, goal); it.setInt(3, if (dark) 1 else 0); it.setInt(4, if (reminders) 1 else 0); it.setString(5, uid); it.executeUpdate()
                }
                return 200 to user(uid)
            }
            method == "GET" && path == "/habits" -> {
                val habits = mutableListOf<Map<String, Any>>()
                db.prepareStatement("SELECT id,title,completed FROM habits WHERE user_id=? ORDER BY rowid").use {
                    it.setString(1, uid); it.executeQuery().use { rs -> while(rs.next()) habits.add(mapOf("id" to rs.getString(1), "title" to rs.getString(2), "completed" to (rs.getInt(3) == 1))) }
                }
                return 200 to mapOf("habits" to habits)
            }
            method == "POST" && path == "/habits" -> {
                val title = string("title").trim()
                if (title.length !in 1..80) throw ApiException(400, "Habit must be 1–80 characters.")
                val id = UUID.randomUUID().toString()
                db.prepareStatement("INSERT INTO habits(id,user_id,title) VALUES(?,?,?)").use { it.setString(1,id); it.setString(2,uid); it.setString(3,title); it.executeUpdate() }
                return 201 to mapOf("id" to id, "title" to title, "completed" to false)
            }
            method == "PUT" && Regex("^/habits/[a-f0-9-]+$").matches(path) -> {
                val completed = input.get("completed")
                if (completed == null || !completed.isJsonPrimitive || !completed.asJsonPrimitive.isBoolean) throw ApiException(400, "completed must be true or false.")
                db.prepareStatement("UPDATE habits SET completed=? WHERE id=? AND user_id=?").use {
                    it.setInt(1, if (completed.asBoolean) 1 else 0); it.setString(2, path.substringAfterLast('/')); it.setString(3, uid)
                    if (it.executeUpdate() == 0) throw ApiException(404, "Habit not found.")
                }
                return 200 to mapOf("message" to "Habit updated.")
            }
            else -> throw ApiException(404, "Endpoint not found.")
        }
    }
    private fun authenticate(token: String?): String {
        if (token.isNullOrBlank()) throw ApiException(401, "Please sign in.")
        db.prepareStatement("SELECT user_id FROM sessions WHERE token_hash=? AND expires>?").use {
            it.setString(1,digest(token)); it.setLong(2,Instant.now().epochSecond)
            it.executeQuery().use { rs -> if (rs.next()) return rs.getString(1) }
        }
        throw ApiException(401, "Session expired. Please sign in again.")
    }
    private fun user(id: String): Map<String, Any> {
        db.prepareStatement("SELECT id,email,display_name,daily_goal,dark_mode,reminders FROM users WHERE id=?").use {
            it.setString(1,id); it.executeQuery().use { rs ->
                if (!rs.next()) throw ApiException(404,"Account not found.")
                return mapOf("id" to rs.getString(1), "email" to rs.getString(2), "displayName" to rs.getString(3), "dailyGoal" to rs.getInt(4), "darkMode" to (rs.getInt(5)==1), "reminders" to (rs.getInt(6)==1))
            }
        }
    }
    fun handle(exchange: HttpExchange) {
        try {
            val bytes = exchange.requestBody.use { it.readNBytes(16_385) }
            if (bytes.size > 16_384) throw ApiException(413, "Request too large.")
            val header = exchange.requestHeaders.getFirst("Authorization")
            val token = header?.takeIf { it.startsWith("Bearer ") }?.removePrefix("Bearer ")
            val (status, result) = route(exchange.requestMethod, exchange.requestURI.path, bytes.toString(Charsets.UTF_8), token, exchange.remoteAddress.address.hostAddress)
            respond(exchange,status,result)
        } catch (e: ApiException) { respond(exchange,e.status,mapOf("error" to e.message)) }
        catch (e: Exception) { System.err.println("Request failed: ${e.javaClass.simpleName}"); respond(exchange,500,mapOf("error" to "Server error. Please try again.")) }
        finally { exchange.close() }
    }
    private fun respond(e: HttpExchange, status: Int, value: Any) {
        val bytes = gson.toJson(value).toByteArray(Charsets.UTF_8)
        e.responseHeaders.set("Content-Type","application/json; charset=utf-8")
        e.responseHeaders.set("Cache-Control","no-store")
        e.sendResponseHeaders(status,bytes.size.toLong()); e.responseBody.use { it.write(bytes) }
    }
    override fun close() = db.close()
}
private fun digest(value: String) = MessageDigest.getInstance("SHA-256").digest(value.toByteArray()).joinToString("") { "%02x".format(it) }

fun main() {
    val port = System.getenv("PORT")?.toIntOrNull() ?: 8080
    val api = DaylightApi(System.getenv("DATABASE_PATH") ?: "daylight.db")
    val server = HttpServer.create(InetSocketAddress(System.getenv("BIND_HOST") ?: "127.0.0.1",port), 0)
    server.createContext("/") { api.handle(it) }
    server.executor = Executors.newFixedThreadPool(8)
    Runtime.getRuntime().addShutdownHook(Thread { server.stop(1); api.close() })
    server.start()
    println("Daylight REST API ready on http://127.0.0.1:$port — SQLite connected")
}
