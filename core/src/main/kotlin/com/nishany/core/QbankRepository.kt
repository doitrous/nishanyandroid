package com.nishany.core

import java.time.Instant
import java.util.UUID
import kotlinx.serialization.json.*
import okhttp3.HttpUrl

object QbankKeys {
    const val ACTIVE = "nishany.qbank.activeSession.v1"
    const val SITTINGS = "nishany.sittings.v1"
}
data class QuestionSummary(val id: String, val subject: String, val topic: String, val difficulty: String, val format: String)
data class QbankQuestion(val raw: JsonObject) {
    val id get() = raw["id"].str()
    val data get() = raw["questionData"].obj()
    val options get() = data["answers"].arr().map { it.jsonObject }.filter { it["text"].str().isNotBlank() }
    val correctIndex get() = options.indexOfFirst { it["label"].str() == data["correctAnswer"].str() }
    val explanation get() = raw["fields"].obj()["Explanation"].str().ifBlank { options[correctIndex]["explanation"].str() }
}
class QbankRepository(private val api: Transport) {
    suspend fun catalogue(): List<QuestionSummary> = api.request("/api/content/questions?view=summary").jsonObject
        .getValue("items").jsonArray.mapNotNull { row ->
            val o = row.jsonObject
            if (o["kind"].str() != "question") return@mapNotNull null
            val d = o["questionData"].obj(); val tags = d["tags"].obj(); val fields = o["fields"].obj()
            QuestionSummary(o.getValue("id").jsonPrimitive.content, o["subjectId"].str(),
                tags["topic"].str().ifBlank { fields["Topic"].str().ifBlank { "General" } },
                tags["intendedDifficulty"].str().ifBlank { fields["Difficulty"].str() },
                d["format"].str().ifBlank { "mcq_single_best" })
        }
    /** Explicit start/resume only: GET can consume the server's weekly content allowance. */
    suspend fun questions(ids: List<String>): List<QbankQuestion> {
        require(ids.isNotEmpty() && ids.size <= 200 && ids.distinct().size == ids.size && ids.all { it.isNotBlank() && ',' !in it })
        val query = HttpUrl.Builder().scheme("https").host("nishany.com")
            .addQueryParameter("ids", ids.joinToString(",")).build().encodedQuery
        val rows = api.request("/api/content/questions?$query").jsonObject.getValue("items").jsonArray
            .map { it.jsonObject }.filter { it["kind"].str() == "question" }
        require(rows.map { it["id"].str() }.distinct().size == rows.size) { "Duplicate question response" }
        val byId = rows.associateBy { it["id"].str() }
        return ids.map { id ->
            val q = QbankQuestion(requireNotNull(byId[id]) { "Session is incomplete; no questions were skipped" })
            require(q.data["format"].str().ifBlank { "mcq_single_best" } == "mcq_single_best") { "Unsupported question format" }
            require(q.options.size >= 2 && q.correctIndex >= 0) { "Invalid question answer key" }
            q
        }
    }
    suspend fun history(): List<JsonObject> {
        val value = api.request("/api/user-state/${segment(QbankKeys.SITTINGS)}").jsonObject.getValue("value")
        if (value == JsonNull) return emptyList()
        return value.jsonObject.getValue("sittings").jsonArray.map { it.jsonObject }
    }
}

/** Immutable patches retain newer web/iOS fields, checked answers and existing timing. */
object QbankSession {
    fun ids(session: JsonObject) = session.getValue("questionIds").jsonArray.map { it.jsonPrimitive.content }
    fun resumable(value: JsonElement): Boolean = value is JsonObject &&
        value["phase"].str() == "running" && !value["submitted"].bool() && !value["reviewing"].bool() &&
        value["questionIds"].arr().isNotEmpty()
    fun create(ids: List<String>): JsonObject {
        require(ids.isNotEmpty() && ids.distinct().size == ids.size)
        return json("questionIds" to JsonArray(ids.map(::string)), "idx" to JsonPrimitive(0),
            "answers" to json(), "checked" to json(), "mode" to string("tutor"),
            "sessionId" to string(UUID.randomUUID().toString()), "elapsed" to JsonPrimitive(0),
            "questionSeconds" to json(), "visited" to JsonArray(listOf(JsonPrimitive(0))), "struck" to json(),
            "reviewing" to JsonPrimitive(false), "name" to string("Android study session"),
            "phase" to string("running"), "submitted" to JsonPrimitive(false), "startedAt" to string(Instant.now().toString()))
    }
    fun answer(session: JsonObject, question: QbankQuestion, index: Int): JsonObject {
        require(resumable(session) && session["mode"].str() == "tutor")
        require(question.id in ids(session) && index in question.options.indices && !session["checked"].obj()[question.id].bool())
        return session.patch("answers" to session["answers"].obj().patch(question.id to JsonPrimitive(index)),
            "struck" to session["struck"].obj().patch(question.id to JsonArray(
                session["struck"].obj()[question.id].arr().filter { it.long() != index.toLong() })))
    }
    fun navigate(session: JsonObject, index: Int): JsonObject {
        require(index in ids(session).indices)
        return session.patch("idx" to JsonPrimitive(index), "visited" to JsonArray(
            (session["visited"].arr() + JsonPrimitive(index)).distinct()))
    }
}
