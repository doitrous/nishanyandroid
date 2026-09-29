package com.nishany.core

import kotlinx.serialization.json.*

val WireJson = Json { ignoreUnknownKeys = true }
fun JsonElement?.obj(): JsonObject = this as? JsonObject ?: JsonObject(emptyMap())
fun JsonElement?.arr(): List<JsonElement> = (this as? JsonArray)?.toList().orEmpty()
fun JsonElement?.str(): String = (this as? JsonPrimitive)?.contentOrNull.orEmpty()
fun JsonElement?.long(): Long? = (this as? JsonPrimitive)?.longOrNull
fun JsonElement?.bool(): Boolean = (this as? JsonPrimitive)?.booleanOrNull == true
fun json(vararg fields: Pair<String, JsonElement>): JsonObject = JsonObject(mapOf(*fields))
fun string(value: String): JsonPrimitive = JsonPrimitive(value)
fun JsonObject.patch(vararg fields: Pair<String, JsonElement>): JsonObject = JsonObject(this + fields)
