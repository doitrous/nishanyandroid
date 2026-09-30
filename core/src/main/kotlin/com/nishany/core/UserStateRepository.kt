package com.nishany.core

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.*

/** The server has no conditional-update API. This detects, but cannot eliminate, races. */
data class StateEdit(val owner: String, val key: String, val base: JsonElement,
    val value: JsonElement, val pending: Boolean = false) {
    fun encode() = json("owner" to string(owner), "key" to string(key), "base" to base,
        "value" to value, "pending" to JsonPrimitive(pending)).toString()
    companion object {
        fun decode(raw: String): StateEdit {
            val o = WireJson.parseToJsonElement(raw).jsonObject
            return StateEdit(o.getValue("owner").jsonPrimitive.content, o.getValue("key").jsonPrimitive.content,
                o.getValue("base"), o.getValue("value"), o["pending"].bool())
        }
    }
}
interface StateEditStorage {
    suspend fun get(owner: String, key: String): StateEdit?
    suspend fun put(edit: StateEdit)
    suspend fun remove(owner: String, key: String)
}
class StateConflict(val remote: JsonElement) : IllegalStateException("The shared document changed; local edits retained")
class StatePending : IllegalStateException("Read back the pending write; never resend automatically")

class UserStateRepository(private val api: Transport, private val disk: StateEditStorage, val owner: String) {
    private val mutex = Mutex()
    init { require(owner.isNotBlank()) }
    suspend fun read(key: String): JsonElement {
        val response = api.request("/api/user-state/${segment(key)}").jsonObject
        return response.getValue("value") // Missing envelope is a protocol error, not an empty document.
    }
    suspend fun local(key: String): StateEdit? = disk.get(owner, key)?.also { require(it.owner == owner && it.key == key) }
    suspend fun stage(key: String, base: JsonElement, value: JsonElement): StateEdit = mutex.withLock {
        val old = local(key)
        if (old?.pending == true) throw StatePending()
        StateEdit(owner, key, old?.base ?: base, value).also { disk.put(it) }
    }
    suspend fun save(key: String): JsonElement = mutex.withLock {
        val edit = requireNotNull(local(key))
        if (edit.pending) throw StatePending()
        val current = read(key)
        if (current != edit.base) throw StateConflict(current)
        // Persist BEFORE attempting delivery. Even cancellation or a crash leaves a blocked journal.
        disk.put(edit.copy(pending = true))
        api.request("/api/user-state/${segment(key)}", "PUT", json("value" to edit.value))
        val after = read(key)
        if (after != edit.value) throw StateConflict(after)
        disk.remove(owner, key)
        after
    }
    suspend fun reconcile(key: String): JsonElement = mutex.withLock {
        val edit = requireNotNull(local(key))
        val current = read(key)
        // Equality proves the desired content is present, not which writer placed it there.
        if (current != edit.value) throw StateConflict(current)
        disk.remove(owner, key)
        current
    }
    /** User-selected discard only. It does not undo a possibly completed server write. */
    suspend fun discard(key: String) = mutex.withLock { disk.remove(owner, key) }
}
