package com.example.blacksmithproject.ui.replay

import com.example.blacksmithproject.R
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
import com.tinyblacksmith.core.combat.Tags
import com.tinyblacksmith.core.content.CombatContent
import com.tinyblacksmith.core.content.ContentCatalog
import com.tinyblacksmith.core.model.FactionId
import com.tinyblacksmith.core.model.GameState
import com.tinyblacksmith.core.model.HeroId

/** The picture of one fighter: a hero's own face, the faction figure an enemy unit truly is ([enemyFace]), else a plate with its [name]. */
@Immutable data class ReplayFace(val res: Int?, val name: String)

/**
 * Faces for everybody in [replay]. A defender is a hero of the save (a fallen one keeps their face); an enemy is drawn
 * by [enemyFace]. Without a picture the tile shows the fighter's recorded name on a plate, never an initial. Decorative
 * only: every portrait has its name and numbers beside it.
 */
fun replayFaces(replay: Replay, state: GameState, content: ContentCatalog): Map<String, ReplayFace> =
    replay.actors.associate { a ->
        val res = if (a.side == Side.PARTY) state.heroes[HeroId(a.key)]?.let { Sprites.portrait(it, small = true) }
        else a.unitId?.let { enemyFace(it, content) }
        a.key to ReplayFace(res, a.name.trim())
    }

/**
 * The bestiary units a faction's shared figure ([Sprites.faction]) truly depicts, each with the figure that does (true:
 * the faction's elite figure). Checked against the six drawables and the owner's bible
 * (docs/Tiny_Blacksmith_Peoples_Names_and_Siege_Bestiary.md, docs/ART_NEEDED_BESTIARY.md):
 *  - the Hollowbound shade is a hooded skeleton soldier with a sword and a shield: the Skeleton Spearman and Archer;
 *  - the Hollowbound wraith is a hooded skeletal spirit in cold mist with a staff and a lantern: the Wraith, and the
 *    Bellkeeper, a tall skeletal caster.
 * The units of [BESTIARY_ART] (the three engines, the hill giant, the bone colossus, the ash dragon) have their own portraits. Every other bestiary
 * unit gets a name plate until its own art exists. The Ashclaw raider and brute are beast-headed
 * raiders, and the Iron Banner fields goblins, hobgoblins, orcs, a bugbear, an ogre, a troll, deserters and a giant. The
 * wyvern is two-legged ("not a four-legged dragon"). The zombies, ghoul, wight, knights, colossus and banshee are not the
 * skeleton or the spirit drawn, the necromancer and the acolyte are living, and the rats are rats. The Covenant's wolves,
 * spider, serpent, harpy, minotaur, manticore, werewolf, treants, elementals and caller have no figure of their kind.
 * Engines ([Tags.ENGINE]) never get a shared figure.
 */
internal val BESTIARY_FIGURES: Map<String, Boolean> = mapOf(
    "pp_skeleton_guard" to false, "pp_skeleton_archer" to false,
    "pp_wraith" to true, "cap_lich" to true,
)

/**
 * Bestiary units with their own portrait (the owner's first six, 112 px): drawn before any shared figure or plate,
 * engines included, since the picture is of that very unit.
 */
internal val BESTIARY_ART: Map<String, Int> = mapOf(
    "eng_battering_ram" to R.drawable.eng_battering_ram, "eng_ballista" to R.drawable.eng_ballista,
    "eng_corpse_catapult" to R.drawable.eng_corpse_catapult, "boss_hill_giant" to R.drawable.boss_hill_giant,
    "boss_bone_colossus" to R.drawable.boss_bone_colossus, "boss_ash_dragon" to R.drawable.boss_ash_dragon,
)

/** The last catalog's unit-to-faction table, kept by identity (a catalog is immutable, and comparing two by value walks all of it). */
@Volatile private var factionTable: Pair<ContentCatalog, Map<String, FactionId>>? = null

/** Which faction's siege roster or preparation names each unit: a leader or elite's own faction, else the first that lists it. */
private fun factionsOf(content: ContentCatalog): Map<String, FactionId> {
    factionTable?.let { (c, table) -> if (c === content) return table }
    val factionOf = HashMap<String, FactionId>()
    content.guild?.let { g ->
        g.siegeLeaders.forEach { (f, u) -> factionOf[u] = f }
        g.siegeElites.forEach { (f, u) -> factionOf[u] = f }
        g.siegeGrunts.forEach { (f, units) -> units.forEach { factionOf.getOrPut(it) { f } } }
        g.preparations.forEach { p -> listOfNotNull(p.addUnit, p.gruntUnit).forEach { factionOf.getOrPut(it) { p.factionId } } }
    }
    factionTable = content to factionOf
    return factionOf
}

/**
 * The faction an enemy unit marches with: a bestiary unit's own host (`EnemyProfile.factionId`), else the faction whose
 * siege roster or preparation names it; null for a unit none of them names.
 */
fun enemyFaction(unitId: String, content: ContentCatalog): FactionId? =
    content.guild?.profileById?.get(unitId)?.factionId ?: factionsOf(content)[unitId]

/**
 * The picture of an enemy unit, or null for a name plate. A unit with its own portrait ([BESTIARY_ART]) has it. A bestiary unit has its faction's figure only where
 * [BESTIARY_FIGURES] says the figure depicts it; an engine never has one. An older roster unit has its faction's art,
 * the elite art for an elite or a leader.
 */
fun enemyFace(unitId: String, content: ContentCatalog): Int? {
    val faction = enemyFaction(unitId, content) ?: return null
    BESTIARY_ART[unitId]?.let { return it }
    val tags = content.combat?.unitById?.get(unitId)?.tags.orEmpty()
    if (Tags.ENGINE in tags) return null
    if (content.guild?.profileById?.containsKey(unitId) == true) return BESTIARY_FIGURES[unitId]?.let { Sprites.faction(faction, elite = it) }
    return Sprites.faction(faction, elite = CombatContent.TAG_ELITE in tags || CombatContent.TAG_LEADER in tags)
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
    Stat.BURN, Stat.BLEED, Stat.MARK, Stat.POISON -> FlawRed
    Stat.CHILL, Stat.WET -> Color(0xFF9CC8F0)
    Stat.REGEN -> BuffGreen
    Stat.CHARGE, Stat.SCRAP, Stat.STEAM -> CreamMuted
}

/** The order states are listed on a portrait: protection first, then what hurts, then stores. */
val STAT_ORDER = listOf(Stat.GUARD, Stat.THORNS, Stat.BURN, Stat.BLEED, Stat.POISON, Stat.MARK, Stat.CHILL, Stat.WET, Stat.REGEN, Stat.CHARGE, Stat.SCRAP, Stat.STEAM)

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
    Stat.POISON -> "At the end of each round the fighter takes 1 damage per Poison (ignores Armor), then loses 1 Poison. Healing the fighter receives is ${rules.poisonHealPercent}% smaller per Poison (at most ${rules.poisonHealMax}% smaller), and each heal on the fighter then removes 1 Poison."
} + " At most ${rules.cap(stat)}."

/** The short word under a pop's number: what kind of hit or mend it was. The rule's own name is said on the one whose rule it was. */
fun shortNote(note: String?): String? = note?.substringBefore(" · ")
