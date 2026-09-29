package com.nishany.core

import kotlinx.serialization.json.JsonObject

/** Mirrors the website's current student gate; the server remains authoritative. */
object Entitlements {
    fun canOpenNotebook(me: JsonObject): Boolean {
        val entitlement = me["entitlement"].obj()
        if (entitlement["state"].str() !in setOf("active", "trialing")) return false
        val includes = entitlement["includes"] as? JsonObject ?: return true
        return includes["studyTools"].str() in setOf("full", "limited")
    }
}
