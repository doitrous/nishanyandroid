package com.nishany.core

import kotlinx.serialization.json.JsonObject

/** The existing inbox endpoint returns an array, not a {notifications: ...} envelope. */
class NotificationRepository(private val api: Transport) {
    suspend fun inbox(): List<JsonObject> = api.request("/api/notifications/inbox").arr().map { it.obj() }
}
