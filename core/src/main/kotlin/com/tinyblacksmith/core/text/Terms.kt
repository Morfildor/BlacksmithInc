package com.tinyblacksmith.core.text

import com.tinyblacksmith.core.combat.Posture
import com.tinyblacksmith.core.combat.Stat
import com.tinyblacksmith.core.content.Element
import com.tinyblacksmith.core.content.MissionArchetype
import com.tinyblacksmith.core.engine.Technique
import com.tinyblacksmith.core.model.Danger
import com.tinyblacksmith.core.model.ForgeMode
import com.tinyblacksmith.core.model.Rarity
import com.tinyblacksmith.core.model.Risk
import com.tinyblacksmith.core.model.WoundKind

/**
 * The player's word for every enum a screen or a record shows (docs/WRITING_GUIDE.md section 2). Enum constants are
 * save keys and are never renamed, so no text is built from `.name`: it goes through here. Lower case where a word
 * sits inside a sentence uses Kotlin's locale-invariant `lowercase()`.
 */
object Terms {
    fun stat(s: Stat): String = when (s) {
        Stat.GUARD -> "Armor"; Stat.CHARGE -> "Charge"; Stat.MARK -> "Mark"; Stat.BURN -> "Burn"; Stat.REGEN -> "Regeneration"
        Stat.CHILL -> "Chill"; Stat.WET -> "Wet"; Stat.BLEED -> "Bleed"; Stat.SCRAP -> "Scrap"; Stat.THORNS -> "Thorns"; Stat.STEAM -> "Steam"; Stat.POISON -> "Poison"
    }

    fun element(e: Element): String = when (e) {
        Element.FIRE -> "Fire"; Element.FROST -> "Frost"; Element.STORM -> "Storm"
        Element.GRAVE -> "Grave"; Element.VERDANT -> "Verdant"; Element.SUN -> "Sun"
    }

    fun rarity(r: Rarity): String = when (r) {
        Rarity.COMMON -> "Common"; Rarity.UNCOMMON -> "Uncommon"; Rarity.RARE -> "Rare"; Rarity.EPIC -> "Epic"; Rarity.LEGENDARY -> "Legendary"
    }

    /** Forge risk. Never the retreat rule's words (Cautious, Balanced, Reckless) or quest danger's "Safe". */
    fun risk(r: Risk): String = when (r) {
        Risk.SAFE -> "Careful"; Risk.BALANCED -> "Standard"; Risk.RECKLESS -> "Daring"
    }

    fun technique(t: Technique): String = when (t) {
        Technique.TEMPER -> "Temper"; Technique.QUENCH -> "Quench"; Technique.ETCH -> "Etch"
    }

    /** What the technique did, for a weapon's history: "tempered". */
    fun techniqueDone(t: Technique): String = when (t) {
        Technique.TEMPER -> "tempered"; Technique.QUENCH -> "quenched"; Technique.ETCH -> "etched"
    }

    fun forgeMode(m: ForgeMode): String = when (m) {
        ForgeMode.QUICK -> "Quick forge"; ForgeMode.ADVANCED -> "Advanced forge"
    }

    fun wound(k: WoundKind): String = when (k) {
        WoundKind.EXHAUSTED -> "Exhausted"; WoundKind.INJURED -> "Injured"; WoundKind.SCARRED -> "Scarred"
    }

    fun danger(d: Danger): String = when (d) {
        Danger.SAFE -> "Safe"; Danger.DANGEROUS -> "Dangerous"; Danger.LETHAL -> "Lethal"
    }

    /** The retreat rule (code: posture). */
    fun posture(p: Posture): String = when (p) {
        Posture.CAUTIOUS -> "Cautious"; Posture.BALANCED -> "Balanced"; Posture.RECKLESS -> "Reckless"
    }

    /** A kind of quest, inside a sentence: "They prefer hunt quests." */
    fun archetype(a: MissionArchetype): String = when (a) {
        MissionArchetype.SUPPLY -> "supply"; MissionArchetype.HUNT -> "hunt"; MissionArchetype.SABOTAGE -> "sabotage"
        MissionArchetype.ESCORT -> "escort"; MissionArchetype.RESCUE -> "rescue"; MissionArchetype.RECOVERY -> "recovery"
        MissionArchetype.DELVE -> "delve"; MissionArchetype.EMERGENCY -> "emergency"; MissionArchetype.OPPORTUNITY -> "opportunity"
    }

    /** [stored] is a `MissionArchetype` constant name kept as text (`RivalGuild.wants`); an unknown one reads as itself. */
    fun archetype(stored: String): String =
        MissionArchetype.entries.firstOrNull { it.name == stored }?.let { archetype(it) } ?: stored.lowercase()
}
