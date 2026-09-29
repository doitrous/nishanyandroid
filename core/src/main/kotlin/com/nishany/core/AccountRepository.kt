package com.nishany.core

import kotlinx.serialization.json.*

class AccountRepository(private val api: Transport) {
    suspend fun login(email: String, password: String): Boolean = api.request("/api/auth/login", "POST",
        json("email" to string(email.trim()), "password" to string(password))).obj()["mfaPending"].bool()
    suspend fun session(): JsonObject = api.request("/api/session").obj()["user"].obj()
    suspend fun me(): JsonObject = api.request("/api/me").obj()
    suspend fun university(): JsonObject = api.request("/api/me/university").obj()
    suspend fun factors(): List<JsonObject> = api.request("/api/auth/mfa/factors").obj()["factors"].arr()
        .map { it.obj() }.filter { it["status"].str() == "verified" && it["factorType"].str() == "totp" }
    suspend fun verifyTotp(factorId: String, code: String) {
        val challenge = api.request("/api/auth/mfa/challenge", "POST", json("factorId" to string(factorId))).obj()["id"].str()
        require(challenge.isNotEmpty())
        api.request("/api/auth/mfa/verify", "POST", json("factorId" to string(factorId), "challengeId" to string(challenge), "code" to string(code)))
    }
    suspend fun recover(email: String) = api.request("/api/auth/recover", "POST", json("email" to string(email.trim())))
    // IMPORTANT: backend logout revokes ALL sessions, including web and iOS. UI must say so.
    suspend fun logoutEverywhere() = api.request("/api/auth/logout", "POST")
}
