package com.nishany.android

import androidx.test.platform.app.InstrumentationRegistry
import com.nishany.core.*
import kotlinx.coroutines.runBlocking
import okhttp3.Cookie
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Test
import org.junit.Assert.*

class SecureStorageTest {
    @Test fun encryptedDraftSurvivesRepositoryRecreationAndIsOwnerScoped() = runBlocking<Unit> {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val vault = SecureVault(context, "instrumented-v1")
        val first = EncryptedDrafts(vault)
        val draft = NoteDraft("instrumented-owner-a", NoteDocument.create(), null)
        first.put(draft)
        try {
            val reopened = EncryptedDrafts(SecureVault(context, "instrumented-v1"))
            assertTrue(reopened.list("instrumented-owner-b").isEmpty())
            assertEquals(draft, reopened.list(draft.owner).first { it.id == draft.id })
            assertNull(vault.read("nonexistent-key"))
        } finally { first.remove(draft.owner, draft.id) }
    }
    @Test fun cookieCannotLeakToAnotherHostOrReturnAfterDeactivation() {
        val vault = SecureVault(InstrumentationRegistry.getInstrumentation().targetContext, "instrumented-v1")
        val jar = SessionCookies(vault)
        val origin = "https://nishany.com/api/session".toHttpUrl()
        val fake = requireNotNull(Cookie.parse(origin, "nsid=fixture-only; Path=/; Secure; HttpOnly; Max-Age=60"))
        jar.saveFromResponse(origin, listOf(fake))
        assertTrue(jar.loadForRequest("https://other.example.invalid/".toHttpUrl()).isEmpty())
        jar.clearAndDeactivate()
        jar.saveFromResponse(origin, listOf(fake))
        assertTrue(jar.loadForRequest(origin).isEmpty())
        assertNull(vault.read("session.cookies"))
    }
}
