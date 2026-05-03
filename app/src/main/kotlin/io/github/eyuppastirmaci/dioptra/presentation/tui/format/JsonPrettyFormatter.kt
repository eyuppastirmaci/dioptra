package io.github.eyuppastirmaci.dioptra.presentation.tui.format

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonParser
object JsonPrettyFormatter {

    data class Result(
        val compact: String,
        val pretty: String,
    )

    private val compactGson = Gson()
    private val prettyGson = GsonBuilder().setPrettyPrinting().create()

    /**
     * Parses [text] as JSON when possible and returns compact and pretty-printed forms.
     * On failure, both strings equal [text].
     */
    fun format(text: String): Result {
        val trimmed = text.trim()
        return try {
            val element = JsonParser.parseString(trimmed)
            Result(
                compact = compactGson.toJson(element),
                pretty = prettyGson.toJson(element),
            )
        } catch (_: Exception) {
            Result(text, text)
        }
    }
}
