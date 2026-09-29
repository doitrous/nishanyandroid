package com.nishany.core

import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import okhttp3.CookieJar
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Test
import kotlin.test.*

class ApiContractTest {
    @Test fun originAndJsonHeadersOnWritesAndRealLoginContract() = runBlocking<Unit> {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("{\"ok\":true,\"mfaPending\":true}"))
            NishanyApi(CookieJar.NO_COOKIES, server.url("/").toString(), true).use { api ->
                assertTrue(AccountRepository(api).login("student@example.invalid", "fixture-only"))
                val req = requireNotNull(server.takeRequest(1, TimeUnit.SECONDS))
                assertEquals("/api/auth/login", req.path); assertEquals("POST", req.method)
                assertEquals(server.url("/").toString().removeSuffix("/"), req.getHeader("Origin"))
                assertEquals("application/json; charset=utf-8", req.getHeader("Content-Type"))
                assertEquals("student@example.invalid", WireJson.parseToJsonElement(req.body.readUtf8()).obj()["email"].str())
            }
        }
    }
    @Test fun serverFailureIsNotRetriedAndClassifiedUncertain() = runBlocking<Unit> {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(503).setBody("{}"))
            NishanyApi(CookieJar.NO_COOKIES, server.url("/").toString(), true).use { api ->
                assertFailsWith<UncertainDelivery> { api.request("/api/auth/login", "POST", json()) }
                assertEquals(1, server.requestCount)
            }
        }
    }
    @Test fun redirectsNeverForwardCredentials() = runBlocking<Unit> {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(302).setHeader("Location", "https://example.invalid/"))
            NishanyApi(CookieJar.NO_COOKIES, server.url("/").toString(), true).use { api ->
                assertEquals(302, assertFailsWith<ApiFailure> { api.request("/api/session") }.status)
                assertEquals(1, server.requestCount)
            }
        }
    }
    @Test fun malformedSuccessfulWriteIsUncertain() = runBlocking<Unit> {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("not-json"))
            NishanyApi(CookieJar.NO_COOKIES, server.url("/").toString(), true).use { api ->
                assertFailsWith<UncertainDelivery> { api.request("/api/me/profile", "PUT", json()) }
            }
        }
    }
    @Test fun badPathsAndRemoteCleartextAreRejected() = runBlocking<Unit> {
        assertFailsWith<IllegalArgumentException> { NishanyApi(CookieJar.NO_COOKIES, "http://example.invalid", true) }
        NishanyApi(CookieJar.NO_COOKIES).use { api ->
            assertFailsWith<IllegalArgumentException> { api.request("https://example.invalid/api/me") }
            assertFailsWith<IllegalArgumentException> { api.request("/api/../../outside") }
        }
    }
    @Test fun encodedIdsStayOneSegment() { assertEquals("a%2Fb%20c", segment("a/b c")) }
    @Test fun notebookGateMatchesTrialLegacyAndAreaRules() {
        fun account(state: String, includes: JsonElement? = null): JsonObject = json("entitlement" to JsonObject(
            mapOf("state" to string(state)) + if (includes == null) emptyMap() else mapOf("includes" to includes)))
        assertFalse(Entitlements.canOpenNotebook(account("none")))
        assertTrue(Entitlements.canOpenNotebook(account("trialing")))
        assertTrue(Entitlements.canOpenNotebook(account("active", json("studyTools" to string("limited")))))
        assertFalse(Entitlements.canOpenNotebook(account("active", json())))
        assertFalse(Entitlements.canOpenNotebook(account("expired", json("studyTools" to string("full")))))
    }
}
