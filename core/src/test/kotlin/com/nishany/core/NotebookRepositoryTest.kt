package com.nishany.core

import java.io.IOException
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.Test
import kotlin.test.*

class MemoryDrafts : DraftStorage {
    val entries = mutableMapOf<Pair<String, String>, NoteDraft>()
    override suspend fun list(owner: String) = entries.filterKeys { it.first == owner }.values.toList()
    override suspend fun put(draft: NoteDraft) { entries[draft.owner to draft.id] = draft }
    override suspend fun remove(owner: String, id: String) { entries.remove(owner to id) }
}

class NoteServer : Transport {
    var remote: JsonObject? = null
    var version = 0L
    var writes = 0
    var loseResponse = false
    var conflictDuringPut = false
    override suspend fun request(path: String, method: String, body: JsonElement?): JsonElement {
        require(path.startsWith("/api/notebook/notes/"))
        if (method == "GET") return remote?.let { json("note" to it, "version" to JsonPrimitive(version)) }
            ?: throw ApiFailure(404, json("error" to string("not_found")))
        require(method == "PUT")
        writes++
        if (conflictDuringPut || (remote != null && body.obj()["baseVersion"].long() != version)) {
            throw ApiFailure(409, json("current" to json("note" to (remote ?: JsonNull), "version" to JsonPrimitive(version))))
        }
        remote = body.obj()["note"].obj(); version++
        if (loseResponse) throw UncertainDelivery(IOException("lost response"))
        return json("version" to JsonPrimitive(version))
    }
}

class NotebookRepositoryTest {
    @Test fun createsUsingCurrentPerNoteApi() = runBlocking<Unit> {
        val server = NoteServer(); val disk = MemoryDrafts(); val repo = NotebookRepository(server, disk, "a")
        val draft = NoteDraft("a", NoteDocument.create().patch("futureField" to json("keep" to JsonPrimitive(true))), null)
        repo.stage(draft); val saved = repo.save(draft)
        assertEquals(1L, saved.version); assertEquals(draft.note, server.remote); assertTrue(repo.drafts().isEmpty())
    }
    @Test fun lostResponseIsReconciledWithoutSecondPut() = runBlocking<Unit> {
        val server = NoteServer().apply { loseResponse = true }; val disk = MemoryDrafts()
        val repo = NotebookRepository(server, disk, "a"); val draft = NoteDraft("a", NoteDocument.create(), null)
        repo.stage(draft); assertFailsWith<UncertainDelivery> { repo.save(draft) }
        assertEquals(DraftPhase.UNCERTAIN, repo.drafts().single().phase)
        val relaunched = NotebookRepository(server, disk, "a")
        assertNotNull(relaunched.reconcile(draft.id)); assertEquals(1, server.writes); assertTrue(relaunched.drafts().isEmpty())
    }
    @Test fun processDeathAfterSendingRequiresReconciliation() = runBlocking<Unit> {
        val server = NoteServer(); val disk = MemoryDrafts(); val draft = NoteDraft("a", NoteDocument.create(), null)
        disk.put(draft.copy(phase = DraftPhase.SENDING))
        val repo = NotebookRepository(server, disk, "a")
        assertFailsWith<NeedsReconciliation> { repo.save(draft) }
        assertFailsWith<NoteConflict> { repo.reconcile(draft.id) }
        assertEquals(0, server.writes)
    }
    @Test fun staleEditDoesNotOverwriteRemote() = runBlocking<Unit> {
        val note = NoteDocument.create(); val server = NoteServer().apply { remote = note; version = 3 }
        val disk = MemoryDrafts(); val repo = NotebookRepository(server, disk, "a")
        val draft = NoteDraft("a", note.patch("title" to string("local")), 2)
        repo.stage(draft); assertFailsWith<NoteConflict> { repo.save(draft) }
        assertEquals(note, server.remote); assertEquals(0, server.writes)
        assertEquals(draft.note, repo.drafts().single().note)
    }
    @Test fun atomicServerConflictAfterPreflightPreservesDraft() = runBlocking<Unit> {
        val note = NoteDocument.create(); val server = NoteServer().apply { remote = note; version = 1; conflictDuringPut = true }
        val repo = NotebookRepository(server, MemoryDrafts(), "a")
        val draft = NoteDraft("a", note.patch("title" to string("local")), 1)
        repo.stage(draft); assertFailsWith<NoteConflict> { repo.save(draft) }
        assertEquals(DraftPhase.CONFLICT, repo.drafts().single().phase)
    }
    @Test fun deletedNoteIsNotAutomaticallyResurrected() = runBlocking<Unit> {
        val server = NoteServer(); val repo = NotebookRepository(server, MemoryDrafts(), "a")
        val draft = NoteDraft("a", NoteDocument.create(), 2)
        repo.stage(draft); assertFailsWith<NoteConflict> { repo.save(draft) }; assertEquals(0, server.writes)
    }
    @Test fun accountIsolationAndWrongOwnerRefusal() = runBlocking<Unit> {
        val disk = MemoryDrafts(); val a = NotebookRepository(NoteServer(), disk, "a")
        val b = NotebookRepository(NoteServer(), disk, "b"); val draft = NoteDraft("a", NoteDocument.create(), null)
        a.stage(draft); assertTrue(b.drafts().isEmpty())
        assertFailsWith<IllegalArgumentException> { b.stage(draft) }; assertEquals(1, a.drafts().size)
    }
    @Test fun unsafeIdsNeverReachTransport() = runBlocking<Unit> {
        val repo = NotebookRepository(NoteServer(), MemoryDrafts(), "a")
        assertFailsWith<IllegalArgumentException> { repo.read("../me") }
    }
    @Test fun encodingRoundtripPreservesUnknownFieldsAndPhase() {
        val draft = NoteDraft("owner", NoteDocument.create().patch("future" to JsonArray(listOf(JsonNull))), 8, DraftPhase.SENDING, "version-id")
        assertEquals(draft, NoteDraft.decode(draft.encode()))
    }
    @Test fun richMetadataEditPreservesExactBodyAndUnknownFields() {
        val rich = NoteDocument.create().patch("editorJson" to json("root" to json("children" to JsonArray(listOf(
            json("type" to string("image"), "src" to string("/api/my-documents/example/file")))))), "attachmentFuture" to string("keep"))
        val edited = NoteDocument.edit(rich, "Renamed", listOf("tag"), NoteDocument.text(rich))
        assertEquals(rich["editorJson"], edited["editorJson"]); assertEquals(rich["attachmentFuture"], edited["attachmentFuture"])
        assertFalse(NoteDocument.canEditBody(rich))
        assertFailsWith<IllegalArgumentException> { NoteDocument.edit(rich, "Bad", emptyList(), "flattened") }
    }
    @Test fun legacyMarkdownKeptWhenBodyChanges() {
        val legacy = json("id" to string("legacy"), "body" to string("# Original"))
        val edited = NoteDocument.edit(legacy, "Title", emptyList(), "New")
        assertEquals("# Original", edited["legacyMarkdownSource"].str()); assertEquals("New", NoteDocument.text(edited))
    }
    @Test fun allWriteRetriesBlockedWhileUncertain() = runBlocking<Unit> {
        val server = NoteServer().apply { loseResponse = true }; val repo = NotebookRepository(server, MemoryDrafts(), "a")
        val d = NoteDraft("a", NoteDocument.create(), null); repo.stage(d)
        assertFailsWith<UncertainDelivery> { repo.save(d) }
        assertFailsWith<NeedsReconciliation> { repo.save(d) }
        assertFailsWith<NeedsReconciliation> { repo.stage(d.copy(note = d.note.patch("title" to string("new")))) }
        assertEquals(1, server.writes)
    }
}
