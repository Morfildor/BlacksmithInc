package com.example.blacksmithproject

import com.tinyblacksmith.core.combat.Posture
import com.tinyblacksmith.core.model.GameState
import com.tinyblacksmith.core.model.HeroId

/** UI-only intent. Never remove an invalid member while restoring or validating a draft. */
data class PartyDraft(
    val runId: String,
    val day: Int,
    val offerId: String,
    val members: List<HeroId> = emptyList(),
    val posture: Posture = Posture.BALANCED,
    val equipment: Map<HeroId, String> = emptyMap(),
) {
    fun move(id: HeroId, offset: Int): PartyDraft {
        val from = members.indexOf(id)
        val to = from + offset
        if (from < 0 || to !in members.indices) return this
        return copy(members = members.toMutableList().apply { add(to, removeAt(from)) })
    }

    fun toggle(state: GameState, id: HeroId): PartyDraft = if (id in members)
        copy(members = members - id, equipment = equipment - id)
    else copy(members = members + id, equipment = equipment + (id to equipmentStamp(state, id)))

    fun reviewEquipment(state: GameState): PartyDraft = copy(equipment = members.associateWith { equipmentStamp(state, it) })

    fun equipmentChanges(state: GameState): List<HeroId> = members.filter { equipment[it] != equipmentStamp(state, it) }
}

/** Include condition and quality so a repair as well as a changed loan requires a fresh look. */
private fun equipmentStamp(state: GameState, id: HeroId): String =
    (state.loanOf(id) ?: state.equippedWeapon(id))?.let { "${it.id.value}:${it.condition}:${it.quality}" } ?: "unarmed"
