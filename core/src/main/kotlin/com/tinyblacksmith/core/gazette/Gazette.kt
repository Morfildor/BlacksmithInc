package com.tinyblacksmith.core.gazette

import com.tinyblacksmith.core.model.EventRecord

/** The Emberfall Gazette (GDD 11): headlines derive only from real event records, ordered by priority. */
object Gazette {
    const val MAX_HEADLINES = 5

    fun headlines(dayEvents: List<EventRecord>): List<String> =
        dayEvents.filter { it.priority >= 2 }
            .sortedWith(compareByDescending<EventRecord> { it.priority }.thenBy { it.id.drop(1).toIntOrNull() ?: 0 })
            .take(MAX_HEADLINES)
            .map { it.text }

    fun masthead(day: Int): String = "EMBERFALL GAZETTE — DAY $day"
}
