package com.daylight.server

import kotlin.test.*
import java.nio.file.Files
import java.sql.DriverManager
import com.google.gson.Gson

class ApiTest {
    private fun json(vararg values: Pair<String, Any>) = Gson().toJson(mapOf(*values))
    private fun code(expected: Int, work: () -> Unit) { assertEquals(expected, assertFailsWith<ApiException> { work() }.status) }
    private fun withApi(work: (DaylightApi, String) -> Unit) {
        val path=Files.createTempFile("daylight-test", ".db")
        try { DaylightApi(path.toString()).use { work(it,path.toString()) } }
        finally { Files.deleteIfExists(path); Files.deleteIfExists(java.nio.file.Path.of("$path-wal")); Files.deleteIfExists(java.nio.file.Path.of("$path-shm")) }
    }
    @Test fun `registration stores salted hashes and no plain passwords`() = withApi { api,path ->
        val body=json("email" to "ada@example.com","password" to "CorrectHorse8","displayName" to "Ada")
        assertEquals(201,api.route("POST","/auth/register",body,null).first)
        code(409) { api.route("POST","/auth/register",body,null) }
        DriverManager.getConnection("jdbc:sqlite:$path").use { db ->
            db.createStatement().executeQuery("SELECT salt,password_hash FROM users").use { rs ->
                assertTrue(rs.next()); assertEquals(16,rs.getBytes(1).size); assertEquals(32,rs.getBytes(2).size)
                assertTrue(Passwords.matches("CorrectHorse8",rs.getBytes(1),rs.getBytes(2)))
                assertFalse(Passwords.matches("incorrect",rs.getBytes(1),rs.getBytes(2)))
            }
        }
    }
    @Test fun `login settings habits and logout persist across server restarts`() {
        val path=Files.createTempFile("daylight-persist", ".db")
        try {
            lateinit var token: String
            DaylightApi(path.toString()).use { api ->
                val reg=api.route("POST","/auth/register",json("email" to "a@example.com","password" to "password88","displayName" to "A"),null).second as Map<*,*>
                token=reg["token"] as String
                code(401) { api.route("GET","/me","",null) }
                code(401) { api.route("POST","/auth/login",json("email" to "a@example.com","password" to "wrongpass"),null) }
                assertEquals(200,api.route("PUT","/me/settings",json("displayName" to "Ada","dailyGoal" to 4,"darkMode" to true,"reminders" to false),token).first)
                code(400) { api.route("PUT","/me/settings",json("displayName" to "Ada","dailyGoal" to 99,"darkMode" to true,"reminders" to false),token) }
                val habits=api.route("GET","/habits","",token).second as Map<*,*>
                val id=((habits["habits"] as List<*>)[0] as Map<*,*>)["id"] as String
                assertEquals(200,api.route("PUT","/habits/$id",json("completed" to true),token).first)
                val other=api.route("POST","/auth/register",json("email" to "b@example.com","password" to "password88","displayName" to "B"),null).second as Map<*,*>
                code(404) { api.route("PUT","/habits/$id",json("completed" to false),other["token"] as String) }
            }
            DaylightApi(path.toString()).use { api ->
                val me=api.route("GET","/me","",token).second as Map<*,*>
                assertEquals("Ada",me["displayName"]); assertEquals(4,me["dailyGoal"]); assertEquals(true,me["darkMode"]); assertEquals(false,me["reminders"])
                val login=api.route("POST","/auth/login",json("email" to "A@EXAMPLE.COM","password" to "password88"),null).second as Map<*,*>
                val loginToken=login["token"] as String
                api.route("POST","/auth/logout","",loginToken)
                code(401) { api.route("GET","/me","",loginToken) }
            }
        } finally { Files.deleteIfExists(path) }
    }
    @Test fun `invalid inputs rejected and brute force throttled`() = withApi { api,_ ->
        code(400) { api.route("POST","/auth/register","not-json",null) }
        code(400) { api.route("POST","/auth/register",json("email" to "invalid","password" to "password8","displayName" to "A"),null) }
        repeat(10) { code(401) { api.route("POST","/auth/login",json("email" to "unknown@example.com","password" to "wrongpass"),null,"attacker") } }
        code(429) { api.route("POST","/auth/login",json("email" to "unknown@example.com","password" to "wrongpass"),null,"attacker") }
    }
}
