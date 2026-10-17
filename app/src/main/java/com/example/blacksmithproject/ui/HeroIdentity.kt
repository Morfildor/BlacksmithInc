package com.example.blacksmithproject.ui

import com.tinyblacksmith.core.engine.GameEngine
import com.tinyblacksmith.core.model.GameState
import com.tinyblacksmith.core.model.Hero
import com.tinyblacksmith.core.model.HeroClassId
import com.tinyblacksmith.core.model.HeroFate
import com.tinyblacksmith.core.model.HeroId
import com.tinyblacksmith.core.model.HookKind
import com.tinyblacksmith.core.model.OathKind

/*
 * Who a living hero is, in the words the screens use (living heroes final design 6, 7.3, 15): people and class, the
 * background they came from and their Hook, each said once and built only from the hero's own record. Pure: reads only.
 */

/** "Elf Duelist": the hero's people and class. The class alone for a hero whose people is not recorded (an era before living heroes). */
internal fun GameEngine.raceClass(h: Hero): String = raceClass(h.raceId, h.classId)

internal fun GameEngine.raceClass(raceId: String?, classId: HeroClassId): String {
    val cls = content.classById[classId]?.name ?: classId.value
    return raceId?.let { content.raceById[it]?.name }?.let { "$it $cls" } ?: cls
}

/** The background's name ("Former poacher"). Nothing in the rules reads a background yet, so nothing more is claimed of it. */
internal fun GameEngine.backgroundName(h: Hero): String? = h.backgroundId?.let { content.backgroundById[it]?.name }

/** The short label of a Hook on the hero sheet, beside its sentence. */
internal fun hookLabel(kind: HookKind): String = when (kind) {
    HookKind.GRUDGE -> "Grudge"
    HookKind.DEBT -> "Debt"
    HookKind.LOST_WEAPON -> "Heirloom"
    HookKind.OATH -> "Oath"
    HookKind.KIN -> "Family"
}

/**
 * The hero's Hook as one calm sentence from its record: "Owes 40 gold to Tobin Reed, due day 12.", "Will carry only Common
 * or Uncommon weapons.". An open Hook only. A settled or changed one says its recorded note, or nothing.
 */
internal fun GameEngine.hookSentence(state: GameState, h: Hero): String? {
    val hook = h.hook ?: return null
    if (!hook.isOpen) return hook.note
    return when (hook.kind) {
        HookKind.GRUDGE -> hook.factionId?.let { content.factionById[it] }?.let { f ->
            if (hook.background) "Carries an old grievance against the ${f.name}." else "Holds a grievance against the ${f.name}."
        }
        HookKind.DEBT -> {
            val creditor = hook.creditorHeroId?.let { state.heroes[it]?.fullName } ?: hook.creditor ?: "a creditor"
            "Owes ${hook.amount} gold to $creditor" + (hook.dueDay?.let { ", due day $it." } ?: ".")
        }
        HookKind.LOST_WEAPON -> {
            val name = hook.weaponId?.let { state.weapons[it]?.name }
            val weapon = name?.let { "A family weapon, the $it," } ?: "A family weapon"
            if (hook.background) "$weapon was lost before they came to Emberfall." else "$weapon is lost."
        }
        HookKind.OATH -> when (hook.oath) {
            OathKind.PLAIN_WORK -> "Will carry only Common or Uncommon weapons."
            OathKind.NO_GRAVE -> "Will not carry a Grave weapon."
            OathKind.FAVOURED_FAMILY -> hook.familyId?.let { content.familyById[it] }?.let { "Will carry only ${plural(it.name.lowercase())}." }
            null -> null
        }
        HookKind.KIN -> hook.kinHeroId?.let { kinSentence(state, it) }
    }
}

/** "Kin of Maren Voss.", or "Kin of Maren Voss, who fell on day 9." Null when the relative is not in the save. */
internal fun kinSentence(state: GameState, kinId: HeroId): String? = state.heroes[kinId]?.let { k ->
    "Kin of ${k.fullName}" + (if (k.fate == HeroFate.DEAD) k.diedOnDay?.let { ", who fell on day $it." } ?: ", who has fallen." else ".")
}

/**
 * Kin of this hero who did not come with a Hook of their own: the relative a newcomer named shares the family ID, so both
 * sheets say it. Empty for a hero whose own Hook already names their kin.
 */
internal fun kinOf(state: GameState, h: Hero): List<HeroId> {
    val family = h.kinId ?: return emptyList()
    val named = h.hook?.takeIf { it.kind == HookKind.KIN }?.kinHeroId
    return state.heroes.values.filter { it.id != h.id && it.id != named && it.kinId == family }.sortedBy { it.id.value }.map { it.id }
}

private fun plural(word: String): String = if (word.endsWith("x") || word.endsWith("s")) word + "es" else word + "s"
