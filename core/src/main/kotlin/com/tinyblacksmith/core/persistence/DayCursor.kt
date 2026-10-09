package com.tinyblacksmith.core.persistence

import kotlinx.serialization.Serializable

/**
 * How far the player has watched the last resolved day. Presentation only: stored as its own row beside the save,
 * never a field of GameState, and never trusted on its own (a cursor naming another day counts as unwatched).
 */
@Serializable
data class DayCursor(val commandId: String, val stage: Stage, val index: Int = 0) {
    enum class Stage { COUNTER, AFTERMATH, TOMORROW, DONE }

    fun encode(): String = SaveCodec.json.encodeToString(serializer(), this)

    companion object {
        fun decode(text: String): DayCursor = SaveCodec.json.decodeFromString(serializer(), text)
    }
}
