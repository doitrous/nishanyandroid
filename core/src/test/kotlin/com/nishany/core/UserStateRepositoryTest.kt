package com.nishany.core

import java.io.IOException
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.Test
import kotlin.test.*

class UserStateRepositoryTest {
    private class Disk : StateEditStorage {
        val entries = mutableMapOf<Pair<String, String>, StateEdit>()
        override suspend fun get(owner: String, key: String) = entries[owner to key]
        override suspend fun put(edit: StateEdit) { entries[edit.owner to edit.key] = edit }
        override suspend fun remove(owner: String, key: String) { entries.remove(owner to key) }
    }
    private class Server : Transport {
        var value: JsonElement = JsonNull
        var puts = 0
        var loseResponse = false
        var beforePut: () -> Unit = {}
        override suspend fun request(path: String, method: String, body: JsonElement?): JsonElement {
            if (method == "GET") return json("value" to value)
            assertEquals("PUT", method); beforePut(); puts++
            value = body!!.jsonObject.getValue("value")
            if (loseResponse) throw UncertainDelivery(IOException("lost response"))
            return json("ok" to JsonPrimitive(true))
        }
    }
    @Test fun staleBaseCannotOverwriteRemote() = runBlocking {
        val server = Server(); val disk = Disk(); val repo = UserStateRepository(server, disk, "alice")
        repo.stage("doc", JsonNull, string("local")); server.value = string("other device")
        assertFailsWith<StateConflict> { repo.save("doc") }
        assertEquals(0, server.puts); assertEquals(string("local"), repo.local("doc")!!.value)
    }
    @Test fun crashJournalPrecedesDeliveryAndLostResponseIsNotReplayed() = runBlocking {
        val server = Server(); val disk = Disk(); val repo = UserStateRepository(server, disk, "alice")
        repo.stage("doc", JsonNull, string("local"))
        server.beforePut = { assertTrue(disk.entries["alice" to "doc"]!!.pending) }
        server.loseResponse = true
        assertFailsWith<UncertainDelivery> { repo.save("doc") }
        val relaunched = UserStateRepository(server, disk, "alice")
        assertFailsWith<StatePending> { relaunched.save("doc") }
        assertEquals(string("local"), relaunched.reconcile("doc"))
        assertEquals(1, server.puts); assertNull(relaunched.local("doc"))
    }
    @Test fun mismatchAfterUncertainDeliveryRemainsBlocked() = runBlocking {
        val server = Server(); val disk = Disk(); val repo = UserStateRepository(server, disk, "alice")
        repo.stage("doc", JsonNull, string("local")); server.loseResponse = true
        assertFailsWith<UncertainDelivery> { repo.save("doc") }
        server.value = string("changed again")
        assertFailsWith<StateConflict> { repo.reconcile("doc") }
        assertFailsWith<StatePending> { repo.stage("doc", server.value, string("replacement")) }
        assertEquals(1, server.puts)
    }
    @Test fun ownerIsolationAndFirstBaseSurviveLocalEdits() = runBlocking {
        val server = Server(); val disk = Disk(); val alice = UserStateRepository(server, disk, "alice")
        alice.stage("doc", JsonNull, string("first")); alice.stage("doc", string("wrong new base"), string("second"))
        assertEquals(JsonNull, alice.local("doc")!!.base)
        assertNull(UserStateRepository(server, disk, "bob").local("doc"))
        assertEquals(alice.local("doc"), StateEdit.decode(alice.local("doc")!!.encode()))
    }
}
