package com.tinyblacksmith.core.text

import com.tinyblacksmith.core.content.ContentCatalog
import com.tinyblacksmith.core.model.*

/**
 * The player's sentences for living heroes (final design 15, Writing Guide): one calm line each, built from the record,
 * never from a guess. Screens show these and compose nothing of their own about a member's situation.
 */
object LivingText {
    /** "Elf Duelist", or the class alone for a hero of unknown ancestry. */
    fun people(hero: Hero, content: ContentCatalog): String =
        listOfNotNull(hero.raceId?.let { content.raceById[it]?.name }, content.heroClass(hero.classId).name).joinToString(" ")

    /** The Hook as one sentence, or null. [state] resolves names of creditors, kin and weapons. */
    fun hook(hero: Hero, state: GameState, content: ContentCatalog): String? {
        val h = hero.hook ?: return null
        if (!h.isOpen) return h.note
        return when (h.kind) {
            HookKind.GRUDGE -> "Carries an old grievance against the ${h.factionId?.let { content.factionById[it]?.name } ?: "raiders"}" + (if (h.background) ", from before they came to Emberfall." else ".")
            HookKind.DEBT -> "Owes ${h.amount} gold to ${h.creditor}, due on day ${h.dueDay}."
            HookKind.LOST_WEAPON -> "A family weapon, ${state.weapons[h.weaponId]?.name ?: "an old weapon"}, was lost before they came to Emberfall."
            HookKind.OATH -> oath(h, content)
            HookKind.KIN -> "Kin of ${h.kinHeroId?.let { state.heroes[it]?.fullName } ?: "someone in town"}."
        }
    }

    fun oath(h: Hook, content: ContentCatalog): String = when (h.oath) {
        OathKind.PLAIN_WORK -> "Has sworn to carry only Common or Uncommon weapons."
        OathKind.NO_GRAVE -> "Has sworn never to carry a Grave weapon."
        OathKind.FAVOURED_FAMILY -> "Has sworn to fight only with ${h.familyId?.let { content.familyById[it]?.name }?.let { "a ${it.lowercase()}" } ?: "one kind of weapon"}."
        null -> "Has sworn an oath."
    }

    /** The Speciality lean as the roster shows it: who, what, why, when. Uncertain until chosen. */
    fun lean(hero: Hero, m: GuildMember, content: ContentCatalog): String? {
        val l = m.lean ?: return null
        val names = l.options.mapNotNull { id -> content.guild?.speciality(id)?.name }
        val to = content.guild?.speciality(l.leaningTo)?.name ?: return null
        return "Leaning to $to (or ${names.filter { it != to }.joinToString(" or ")}): ${hero.name} ${l.reason}. The choice is made at the end of day ${l.resolveDay} or later, once ${hero.name} is home."
    }

    /** The announced loan return: the loan, the weapon they would rather carry, the reason and the safe timing. */
    fun loanReturn(hero: Hero, m: GuildMember, state: GameState): String? {
        val i = m.loanReturn ?: return null
        val loan = state.weapons[i.loanId]?.name ?: return null
        val own = state.weapons[i.ownedId]?.name ?: return null
        val why = when (i.reason) {
            ReturnReason.OATH -> "their oath forbids the loan"
            ReturnReason.WORN_LOAN -> "the loan is worn out"
            ReturnReason.THREAT -> "it answers the coming attackers"
            ReturnReason.CLASS_FIT -> "it suits their class better"
            ReturnReason.STRONGER -> "it is stronger in their hands"
        }
        return "Would rather fight with their own $own: $why. Your loan, $loan, returns to storage after the commitments of day ${i.resolveDay}, unless you lend something better."
    }

    /** A departure notice: the date, the concern and the remedy, calmly. */
    fun departure(hero: Hero, m: GuildMember): String? {
        val d = m.concern?.departure ?: return null
        return "Leaves the guild on the morning of day ${d.day} unless you ${d.remedy}."
    }

    /** A concern still short of a notice. */
    fun concern(hero: Hero, m: GuildMember): String? {
        val c = m.concern?.takeIf { it.departure == null } ?: return null
        return "Hopes you will ${c.remedy}."
    }

    /** What a resident would bring if recruited, from their background and Personality: an inclination, never a promise. */
    fun inclination(hero: Hero, content: ContentCatalog): String? {
        val bg = hero.backgroundId?.let { content.backgroundById[it] } ?: return null
        return "${bg.name}. Inclined toward ${bg.interest.lowercase()}."
    }
}
