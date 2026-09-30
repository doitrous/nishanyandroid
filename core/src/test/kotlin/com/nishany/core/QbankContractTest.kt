package com.nishany.core

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.Test
import kotlin.test.*

class QbankContractTest {
    private fun row(id: String) = json("id" to string(id), "kind" to string("question"), "title" to string("Stem"),
        "questionData" to json("format" to string("mcq_single_best"), "correctAnswer" to string("B"),
            "answers" to JsonArray(listOf(
                json("label" to string("Z"), "text" to string("  ")),
                json("label" to string("A"), "text" to string("First")),
                json("label" to string("B"), "text" to string("Second"), "explanation" to string("Reason"))))))
    private class Fixture(val value: JsonElement) : Transport {
        val paths = mutableListOf<String>()
        override suspend fun request(path: String, method: String, body: JsonElement?): JsonElement {
            assertEquals("GET", method); paths += path; return value
        }
    }
    @Test fun hydrationPreservesSavedOrderAndFilteredAnswerIndices() = runBlocking {
        val api = Fixture(json("items" to JsonArray(listOf(row("b"), row("a")))))
        val result = QbankRepository(api).questions(listOf("a", "b"))
        assertEquals(listOf("a", "b"), result.map { it.id })
        assertEquals(1, result.first().correctIndex)
        assertEquals("Reason", result.first().explanation)
        assertTrue(api.paths.single().contains("?ids=a%2Cb"))
    }
    @Test fun unavailableQuestionDoesNotSilentlyShortenSession() = runBlocking {
        val api = Fixture(json("items" to JsonArray(listOf(row("a")))))
        assertFailsWith<IllegalArgumentException> { QbankRepository(api).questions(listOf("a", "missing")) }
        Unit
    }
    @Test fun duplicateManifestIsRejectedBeforeQuotaBearingRequest() = runBlocking {
        val api = Fixture(JsonNull)
        assertFailsWith<IllegalArgumentException> { QbankRepository(api).questions(listOf("a", "a")) }
        assertTrue(api.paths.isEmpty())
    }
    @Test fun malformedEnvelopeIsNotAnEmptyHistory() = runBlocking {
        assertFailsWith<NoSuchElementException> { QbankRepository(Fixture(json())).history() }
        Unit
    }
    @Test fun unknownFieldsAndStrikeStateSurviveAnswerPatch() {
        val session = QbankSession.create(listOf("a")).patch("future" to json("nested" to JsonPrimitive(4)),
            "struck" to json("a" to JsonArray(listOf(JsonPrimitive(0), JsonPrimitive(1)))))
        val after = QbankSession.answer(session, QbankQuestion(row("a")), 1)
        assertEquals(session["future"], after["future"])
        assertEquals(listOf(JsonPrimitive(0)), after["struck"].obj()["a"].arr())
        assertEquals(1L, after["answers"].obj()["a"].long())
    }
    @Test fun completedAndReviewSessionsCannotResume() {
        val session = QbankSession.create(listOf("a"))
        assertTrue(QbankSession.resumable(session))
        assertFalse(QbankSession.resumable(session.patch("submitted" to JsonPrimitive(true))))
        assertFalse(QbankSession.resumable(session.patch("reviewing" to JsonPrimitive(true))))
        assertFalse(QbankSession.resumable(session.patch("phase" to string("results"))))
    }
}
