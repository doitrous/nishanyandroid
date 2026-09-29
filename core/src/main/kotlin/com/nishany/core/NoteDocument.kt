package com.nishany.core

import java.time.Instant
import java.util.UUID
import kotlinx.serialization.json.*

object NoteDocument {
    fun create(): JsonObject = json("id" to string("nb-${UUID.randomUUID()}"), "title" to string(""),
        "body" to string(""), "plainText" to string(""), "tags" to JsonArray(emptyList()),
        "updatedAt" to string(Instant.now().toString()), "editorJson" to editor(""))
    fun text(note: JsonObject): String {
        if (note["plainText"] is JsonPrimitive) return note["plainText"].str()
        val root = note["editorJson"].obj()["root"].obj()
        if (root["children"] is JsonArray) return root["children"].arr().joinToString("\n") { nodeText(it.obj()) }
        return note["body"].str()
    }
    private fun nodeText(node: JsonObject): String = if (node["text"] is JsonPrimitive) node["text"].str()
        else node["children"].arr().joinToString("") { nodeText(it.obj()) }

    /** Never flatten a website rich note into plain paragraphs. Metadata-only edits remain safe. */
    fun canEditBody(note: JsonObject): Boolean {
        if (note["editorJson"] == null || note["editorJson"] == JsonNull) return true
        val root = note["editorJson"].obj()["root"].obj()
        if (root["type"].str() != "root" || root["children"] !is JsonArray) return false
        if (root.keys.any { it !in setOf("type", "version", "children", "direction", "format", "indent") }) return false
        if (root["format"].str().isNotBlank() || (root["indent"].long() ?: 0) != 0L || root["direction"].str().isNotBlank()) return false
        return root["children"].arr().all { block ->
            val b = block.obj()
            b["type"].str() == "paragraph" && b["direction"].str().isBlank() && b["children"] is JsonArray && b["format"].str().isBlank() && (b["indent"].long() ?: 0) == 0L &&
                b.keys.all { it in setOf("type", "version", "children", "direction", "format", "indent", "textFormat", "textStyle") } &&
                (b["textFormat"].long() ?: 0) == 0L && b["textStyle"].str().isBlank() &&
                b["children"].arr().all { leaf ->
                    val l = leaf.obj()
                    l["type"].str() == "text" && (l["format"].long() ?: 0) == 0L && l["style"].str().isBlank() &&
                        l["mode"].str() in setOf("", "normal") && (l["detail"].long() ?: 0) == 0L &&
                        l.keys.all { it in setOf("type", "version", "text", "detail", "format", "mode", "style") }
                }
        }
    }
    fun edit(note: JsonObject, title: String, tags: List<String>, body: String): JsonObject {
        require(title.length <= 255 && tags.size <= 40 && tags.all { it.length <= 80 })
        var result = note.patch("title" to string(title), "tags" to JsonArray(tags.map(::string)),
            "updatedAt" to string(Instant.now().toString()))
        if (body != text(note)) {
            require(canEditBody(note)) { "Rich body must be preserved" }
            // Preserve the original markdown for older notes; don't remove attachments/unknown metadata.
            if (note["editorJson"] == null && note["legacyMarkdownSource"] == null) {
                result = result.patch("legacyMarkdownSource" to (note["body"] ?: string("")))
            }
            result = result.patch("body" to string(body), "plainText" to string(body), "editorJson" to editor(body))
        }
        return result
    }
    private fun editor(text: String): JsonObject = json("root" to json("type" to string("root"),
        "version" to JsonPrimitive(1), "direction" to JsonNull, "format" to string(""), "indent" to JsonPrimitive(0),
        "children" to JsonArray(text.replace("\r\n", "\n").split('\n').map { line ->
            json("type" to string("paragraph"), "version" to JsonPrimitive(1), "format" to string(""),
                "direction" to JsonNull, "indent" to JsonPrimitive(0), "textFormat" to JsonPrimitive(0), "textStyle" to string(""),
                "children" to JsonArray(listOf(json("type" to string("text"), "version" to JsonPrimitive(1),
                    "text" to string(line), "format" to JsonPrimitive(0), "detail" to JsonPrimitive(0),
                    "mode" to string("normal"), "style" to string("")))))
        })))
}
