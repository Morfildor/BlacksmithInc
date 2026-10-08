package com.example.blacksmithproject.ui

import com.tinyblacksmith.core.content.ContentCatalog
import com.tinyblacksmith.core.engine.Technique
import com.tinyblacksmith.core.model.Hero
import com.tinyblacksmith.core.model.Rarity
import com.tinyblacksmith.core.model.Risk
import com.tinyblacksmith.core.model.Weapon

/** Descriptive, never numeric, player-facing labels (GDD round 9 disclosure rule). */
object Labels {
    fun quality(q: Int): String = when {
        q >= 85 -> "masterwork"
        q >= 70 -> "superb"
        q >= 50 -> "fine"
        q >= 35 -> "decent"
        else -> "crude"
    }

    fun rarity(r: Rarity): String = when (r) {
        Rarity.COMMON -> "Common ◇"
        Rarity.UNCOMMON -> "Uncommon ◈"
        Rarity.RARE -> "Rare ◆"
        Rarity.EPIC -> "Epic ★"
        Rarity.LEGENDARY -> "Legendary ✦"
    }

    fun risk(r: Risk): String = when (r) {
        Risk.SAFE -> "Safe — steady work, few surprises"
        Risk.BALANCED -> "Balanced — some flaws, some brilliance"
        Risk.RECKLESS -> "Reckless — brilliance or flaws, often"
    }

    fun technique(t: Technique): String = when (t) {
        Technique.TEMPER -> "Temper"
        Technique.QUENCH -> "Quench"
        Technique.ETCH -> "Etch"
    }

    fun techniqueExplanation(t: Technique): String = when (t) {
        Technique.TEMPER -> "Patient heating: fewer flaws, fewer flashes of brilliance."
        Technique.QUENCH -> "A hard quench binds the augment's element into the blade, at a small cost to finish."
        Technique.ETCH -> "Etching makes room for one more property, but the needle slips more often."
    }

    fun health(h: Hero): String = when {
        !h.isAlive -> "dead"
        h.health >= 85 -> "hale"
        h.health >= 50 -> "bruised"
        h.health >= 20 -> "wounded"
        else -> "grave"
    }

    fun weaponSummary(w: Weapon, content: ContentCatalog): String = buildString {
        append(rarity(w.rarity)); append(" · "); append(quality(w.quality))
        if (w.affixes.isNotEmpty()) append(" · ").append(w.affixes.joinToString { content.affix(it).name })
        if (w.flaws.isNotEmpty()) append(" · flaw: ").append(w.flaws.joinToString { content.affix(it).name })
        w.title?.let { append(" · \"").append(it).append('"') }
    }
}
