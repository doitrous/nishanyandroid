package com.nishany.core

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.*

enum class DraftPhase { EDITING, SENDING, UNCERTAIN, CONFLICT }
data class NoteSnapshot(val note: JsonObject, val version: Long, val updatedAt: String)
data class NoteDraft(
    val owner: String, val note: JsonObject, val baseVersion: Long?,
    val phase: DraftPhase = DraftPhase.EDITING, val restoredFrom: String? = null,
) {
    val id: String get() = note["id"].str()
    fun encode(): String = json("owner" to string(owner), "note" to note,
        "baseVersion" to (baseVersion?.let(::JsonPrimitive) ?: JsonNull),
        "phase" to string(phase.name), "restoredFrom" to (restoredFrom?.let(::string) ?: JsonNull)).toString()
    companion object {
        fun decode(raw: String): NoteDraft {
            val o = WireJson.parseToJsonElement(raw).jsonObject
            return NoteDraft(o["owner"].str(), requireNotNull(o["note"]).jsonObject, o["baseVersion"].long(),
                DraftPhase.valueOf(o["phase"].str()), o["restoredFrom"].str().ifEmpty { null })
        }
    }
}

interface DraftStorage {
    suspend fun list(owner: String): List<NoteDraft>
    suspend fun put(draft: NoteDraft)
    suspend fun remove(owner: String, id: String)
}
class NoteConflict(val current: NoteSnapshot?) : Exception("Note changed or was deleted on another device")
class NeedsReconciliation : Exception("Check this pending write before sending again")

class NotebookRepository(private val api: Transport, private val storage: DraftStorage, val owner: String) {
    private val lock = Mutex()
    init { require(owner.isNotBlank()) }
    private fun path(id: String): String {
        require(Regex("[A-Za-z0-9._:-]{1,64}").matches(id))
        return "/api/notebook/notes/${segment(id)}"
    }
    suspend fun list(): List<JsonObject> = api.request("/api/notebook/notes").obj()["notes"].arr().map { it.obj() }
    suspend fun read(id: String): NoteSnapshot? = try {
        val data = api.request(path(id)).obj()
        val note = data["note"] as? JsonObject ?: error("Missing note body")
        require(note["id"].str() == id)
        NoteSnapshot(note, data["version"].long() ?: error("Missing note version"), data["updatedAt"].str())
    } catch (e: ApiFailure) { if (e.status == 404) null else throw e }
    suspend fun drafts(): List<NoteDraft> = storage.list(owner).also { require(it.all { d -> d.owner == owner }) }
    suspend fun stage(draft: NoteDraft) = lock.withLock {
        require(draft.owner == owner); path(draft.id)
        val old = storage.list(owner).find { it.id == draft.id }
        if (old != null && old.phase != DraftPhase.EDITING) throw NeedsReconciliation()
        require(draft.phase == DraftPhase.EDITING)
        storage.put(draft)
    }
    suspend fun save(draft: NoteDraft): NoteSnapshot = lock.withLock {
        require(draft.owner == owner); path(draft.id)
        val pending = storage.list(owner).find { it.id == draft.id }
        if (pending?.phase != DraftPhase.EDITING || pending.note != draft.note || draft.phase != DraftPhase.EDITING) {
            throw NeedsReconciliation()
        }
        // Detect deletion before sending. Deletion racing AFTER this GET can still resurrect a note:
        // the server accepts writes to tombstones regardless of baseVersion. This remains a release gate.
        val before = read(draft.id)
        if (before?.version != draft.baseVersion) {
            storage.put(draft.copy(phase = DraftPhase.CONFLICT)); throw NoteConflict(before)
        }
        storage.put(draft.copy(phase = DraftPhase.SENDING)) // durable BEFORE the request
        try {
            val payload = json("note" to draft.note,
                "baseVersion" to (draft.baseVersion?.let(::JsonPrimitive) ?: JsonNull),
                "restoredFrom" to (draft.restoredFrom?.let(::string) ?: JsonNull))
            val receipt = api.request(path(draft.id), "PUT", payload).obj()
            val version = receipt["version"].long() ?: throw NeedsReconciliation()
            val result = NoteSnapshot(draft.note, version, receipt["updatedAt"].str())
            storage.remove(owner, draft.id)
            result
        } catch (e: Exception) {
            val phase = if (e is ApiFailure && e.status == 409) DraftPhase.CONFLICT else DraftPhase.UNCERTAIN
            storage.put(draft.copy(phase = phase))
            if (e is ApiFailure && e.status == 409) {
                val remote = e.payload["current"].obj()
                val n = remote["note"] as? JsonObject
                throw NoteConflict(n?.let { NoteSnapshot(it, remote["version"].long() ?: 0, remote["updatedAt"].str()) })
            }
            throw e
        }
    }
    /** Read-only reconciliation. A mismatch NEVER triggers a replay, even if the base still matches. */
    suspend fun reconcile(id: String): NoteSnapshot? = lock.withLock {
        val draft = storage.list(owner).find { it.id == id } ?: return@withLock read(id)
        val remote = read(id)
        if (remote?.note == draft.note) {
            storage.remove(owner, id)
            remote
        } else {
            storage.put(draft.copy(phase = DraftPhase.CONFLICT))
            throw NoteConflict(remote)
        }
    }
    suspend fun versions(id: String): List<JsonObject> = api.request("${path(id)}/versions").obj()["versions"].arr().map { it.obj() }
    suspend fun version(id: String, versionId: String): JsonObject = api.request("${path(id)}/versions/${segment(versionId)}").obj()["note"].let { requireNotNull(it).jsonObject }
    /** Explicit user action only; never deletes the remote note or another account's draft. */
    suspend fun discardLocal(id: String) = lock.withLock { path(id); storage.remove(owner, id) }
    // No unconditional delete exposed: current server DELETE has no version precondition.
}
