package com.example.blacksmithproject.ui

import com.tinyblacksmith.core.engine.GameEngine
import com.tinyblacksmith.core.model.GameState
import com.tinyblacksmith.core.model.Hero
import com.tinyblacksmith.core.model.HeroClassId
import com.tinyblacksmith.core.model.HeroFate
import com.tinyblacksmith.core.model.HeroId
import com.tinyblacksmith.core.model.HookKind
import com.tinyblacksmith.core.text.LivingText

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

/** A living era (`GameState.livingRevision`): members decide for themselves; a legacy era keeps every old screen. */
internal val GameState.living: Boolean get() = livingRevision >= com.tinyblacksmith.core.model.LivingRevision.CURRENT

/**
 * The Speciality pair the smith may choose for a member: only in a legacy era. In a living era the member leans and
 * chooses on their own (`Agency`, `GameError.SpecialityChosenByMember`), so nothing is offered to the smith.
 */
internal fun GameEngine.choosableBranches(state: GameState, heroId: HeroId) = if (state.living) emptyList() else openBranches(state, heroId)

/** Why [h]'s oath refuses [w] as a loan, before the smith tries it (`Agency.oathAllows`, `LivingText.oath`); null when it may be lent. */
internal fun GameEngine.oathRefuses(h: Hero, w: com.tinyblacksmith.core.model.Weapon): String? {
    val hook = h.hook?.takeIf { it.kind == HookKind.OATH && it.isOpen } ?: return null
    if (com.tinyblacksmith.core.guild.Agency.oathAllows(h, w)) return null
    return "${h.name} ${LivingText.oath(hook, content).replaceFirstChar { it.lowercase() }}"
}

/** A sentence with a number kept beside its word ("day 8", "level 3"), so a line never starts with a lone number. */
internal fun String.keepNumbersTogether(): String = replace(Regex("\\b(day|days|level|gold) (\\d+)")) { "${it.groupValues[1]} ${it.groupValues[2]}" }
    .replace(Regex("(\\d+) (gold|days|day|health)\\b")) { "${it.groupValues[1]} ${it.groupValues[2]}" }

/** The short label of a Hook on the hero sheet, beside its sentence. */
internal fun hookLabel(kind: HookKind): String = when (kind) {
    HookKind.GRUDGE -> "Grudge"
    HookKind.DEBT -> "Debt"
    HookKind.LOST_WEAPON -> "Heirloom"
    HookKind.OATH -> "Oath"
    HookKind.KIN -> "Family"
}

/**
 * The hero's Hook as one calm sentence from its record, in core's words (`LivingText.hook`): screens compose nothing of
 * their own about a hero's situation. An open Hook only; a settled or changed one says its recorded note, or nothing.
 */
internal fun GameEngine.hookSentence(state: GameState, h: Hero): String? = LivingText.hook(h, state, content)

/**
 * A member's actionable situation beyond the Hook, most urgent first (final design 15): a concern,
 * a Speciality lean, an announced loan return. Label and core's sentence (`LivingText`); empty for anyone else.
 */
internal fun GameEngine.situation(state: GameState, h: Hero): List<Pair<String, String>> {
    val m = state.guild?.member(h.id) ?: return emptyList()
    return listOfNotNull(
        LivingText.concern(h, m)?.let { "Concern" to it },
        LivingText.lean(h, m, content)?.let { "Speciality" to it },
        LivingText.loanReturn(h, m, state)?.let { "Loan" to it },
    )
}

/**
 * The name split from a byname of residence ("of Willow Crossing", content `bynames`) when the stored name ends with one,
 * so a row can say it on a quieter line. The stored identity is never changed, and no other surname is split.
 */
internal fun GameEngine.nameParts(h: Hero): Pair<String, String?> = nameParts(h.fullName)

internal fun GameEngine.nameParts(fullName: String): Pair<String, String?> {
    val by = content.bynames.firstOrNull { fullName.endsWith(" $it") && fullName.length > it.length + 1 } ?: return fullName to null
    return fullName.removeSuffix(" $by") to by
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

