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

/**
 * What a status does, with the fight's own numbers ([FightRules] is `BalanceConfig.guild.fight`): the same rules the fight
 * ran on, in the words of docs/WRITING_GUIDE.md section 2.2. Every status ends with the fight.
 */
fun statMeaning(stat: Stat, rules: FightRules): String = when (stat) {
    Stat.GUARD -> "Absorbs damage before Health, point for point, until it is used up. Blocks Burn. Bleed and damage that ignores Armor pass. Armor breaks when a hit empties it."
    Stat.CHILL -> "The fighter's next attack deals ${rules.chillPercent}% less damage, then 1 Chill is used up. Only the first damage of that ability is weakened."
    Stat.BURN -> "At the end of each round the fighter takes 1 damage per Burn (Armor blocks it), then loses 1 Burn."
    Stat.BLEED -> "Before the fighter acts, it takes 1 damage per Bleed (ignores Armor), then loses 1 Bleed."
    Stat.REGEN -> "At the end of each round the fighter heals ${rules.regenHeal}, then loses 1 Regeneration. More Regeneration lasts longer, it does not heal more."
    Stat.MARK -> "The party attacks a marked enemy first. The next hit on it deals ${rules.markBonus} extra damage and removes the Mark."
    Stat.THORNS -> "The next enemy attack that hits this fighter, even into Armor, takes all the Thorns as damage, and the Thorns are used up. Effects, Burn and Bleed never set it off."
    Stat.CHARGE -> "Stored energy. A Discharge spends all of it at 3 or more for 8 storm damage."
    Stat.WET -> "A storm hit on a Wet fighter deals ${rules.wetStormBonus} extra damage and uses up 1 Wet. Loses 1 Wet each round."
    Stat.STEAM -> "When a fighter puts Burn on an enemy with Chill or Wet, or Chill on an enemy with Burn, they cancel one for one and the giver gains that much Steam."
    Stat.SCRAP -> "Left (2) when a weapon cracks in a fight. Some relics spend it."
} + " At most ${rules.cap(stat)}."

/** The short word under a pop's number: what kind of hit or mend it was. The rule's own name is said on the one whose rule it was. */
fun shortNote(note: String?): String? = note?.substringBefore(" · ")
