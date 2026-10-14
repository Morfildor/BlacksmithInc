package com.example.blacksmithproject.ui.replay

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import com.example.blacksmithproject.ui.Sprites
import com.example.blacksmithproject.ui.theme.BuffGreen
import com.example.blacksmithproject.ui.theme.CreamMuted
import com.example.blacksmithproject.ui.theme.Ember
import com.example.blacksmithproject.ui.theme.FlawRed
import com.example.blacksmithproject.ui.theme.Gold
import com.example.blacksmithproject.ui.theme.Iron
import com.tinyblacksmith.core.combat.FightRules
import com.tinyblacksmith.core.combat.PopKind
import com.tinyblacksmith.core.combat.Replay
import com.tinyblacksmith.core.combat.Side
import com.tinyblacksmith.core.combat.Stat
import com.tinyblacksmith.core.content.CombatContent
import com.tinyblacksmith.core.content.ContentCatalog
import com.tinyblacksmith.core.model.FactionId
import com.tinyblacksmith.core.model.GameState
import com.tinyblacksmith.core.model.HeroId

/** The picture of one fighter: a hero's own face, the art of the faction an enemy unit marches with, else its initial on a plate. */
@Immutable data class ReplayFace(val res: Int?, val initial: String)

/**
 * Faces for everybody in [replay]. A defender is a hero of the save (a fallen one keeps their face); an enemy is drawn
 * by the faction whose siege roster or preparation names its unit, elite art for an elite or a leader. Decorative only:
 * every portrait has its name and numbers beside it.
 */
fun replayFaces(replay: Replay, state: GameState, content: ContentCatalog): Map<String, ReplayFace> {
    val factionOf = HashMap<String, FactionId>()
    content.guild?.let { g ->
        g.siegeLeaders.forEach { (f, u) -> factionOf[u] = f }
        g.siegeElites.forEach { (f, u) -> factionOf[u] = f }
        g.siegeGrunts.forEach { (f, units) -> units.forEach { factionOf.getOrPut(it) { f } } }
        g.preparations.forEach { p -> listOfNotNull(p.addUnit, p.gruntUnit).forEach { factionOf.getOrPut(it) { p.factionId } } }
    }
    val units = content.combat?.unitById.orEmpty()
    return replay.actors.associate { a ->
        val res = if (a.side == Side.PARTY) state.heroes[HeroId(a.key)]?.let { Sprites.portrait(it, small = true) }
        else a.unitId?.let { u ->
            val tags = units[u]?.tags.orEmpty()
            factionOf[u]?.let { f -> Sprites.faction(f, elite = CombatContent.TAG_ELITE in tags || CombatContent.TAG_LEADER in tags) }
        }
        a.key to ReplayFace(res, a.name.trim().take(1).uppercase())
    }
}

/** The colour of a pop's plate edge and big line. Never alone: the sign and the word on the plate say the same. */
fun popColor(kind: PopKind): Color = when (kind) {
    PopKind.DAMAGE, PopKind.PAID, PopKind.DOWN -> FlawRed
    PopKind.HEAL -> BuffGreen
    PopKind.BLOCK, PopKind.GUARD, PopKind.GUARD_BROKEN -> Iron
    PopKind.STATUS, PopKind.SPENT -> Ember
    PopKind.JOINED, PopKind.CRACKED -> Gold
}

/** The colour of a state's chip, by what it does to its holder. */
fun statColor(stat: Stat): Color = when (stat) {
    Stat.GUARD, Stat.THORNS -> Iron
    Stat.BURN, Stat.BLEED, Stat.MARK -> FlawRed
    Stat.CHILL, Stat.WET -> Color(0xFF9CC8F0)
    Stat.REGEN -> BuffGreen
    Stat.CHARGE, Stat.SCRAP, Stat.STEAM -> CreamMuted
}

/** The order states are listed on a portrait: protection first, then what hurts, then stores. */
val STAT_ORDER = listOf(Stat.GUARD, Stat.THORNS, Stat.BURN, Stat.BLEED, Stat.MARK, Stat.CHILL, Stat.WET, Stat.REGEN, Stat.CHARGE, Stat.SCRAP, Stat.STEAM)

/** What a state does, with the fight's own numbers ([FightRules] is `BalanceConfig.guild.fight`): the same rules the fight ran on. */
fun statMeaning(stat: Stat, rules: FightRules): String = when (stat) {
    Stat.GUARD -> "Blows hit Guard before health."
    Stat.BURN -> "At the end of each round: loses 1 health per stack, then one stack fades."
    Stat.BLEED -> "Before acting: loses 1 health per stack, past Guard, then one stack fades."
    Stat.REGEN -> "At the end of each round: mends ${rules.regenHeal}, then one stack fades."
    Stat.CHILL -> "Its next strike is ${rules.chillPercent}% weaker and uses up one stack."
    Stat.MARK -> "The next direct hit on it deals ${rules.markBonus} more and uses the Mark."
    Stat.WET -> "Storm hits on it deal ${rules.wetStormBonus} more. One stack dries each round."
    Stat.THORNS -> "The next blow it takes hurts the attacker for this much, once."
    Stat.CHARGE, Stat.SCRAP, Stat.STEAM -> "Stored for a rule that spends it."
}

/** The short word under a pop's number: what kind of hit or mend it was. The rule's own name is said on the one whose rule it was. */
fun shortNote(note: String?): String? = note?.substringBefore(" · ")
