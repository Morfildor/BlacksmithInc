package com.example.blacksmithproject.ui

import com.tinyblacksmith.core.content.ContentCatalog
import com.tinyblacksmith.core.config.BalanceConfig
import com.tinyblacksmith.core.engine.Technique
import com.tinyblacksmith.core.market.Commissions
import com.tinyblacksmith.core.market.QualityBand
import com.tinyblacksmith.core.model.Commission
import com.tinyblacksmith.core.model.Hero
import com.tinyblacksmith.core.model.Rarity
import com.tinyblacksmith.core.model.Risk
import com.tinyblacksmith.core.model.Weapon

/** Descriptive, never numeric, player-facing labels (GDD round 9 disclosure rule). */
object Labels {
    /** The band word of the core rule ([QualityBand]); the app's engine runs the default balance. */
    fun quality(q: Int): String = QualityBand.of(q, BalanceConfig.DEFAULT).word

    /** A request in the terms End Day checks: "Fine frost Spear (quality 50+)". */
    fun request(c: Commission, content: ContentCatalog, config: BalanceConfig): String =
        Commissions.describe(c, content, config).replaceFirstChar { it.uppercase() }

    /** "fits", or the one thing [w] lacks for [c], by [Commissions.fit]. */
    fun fit(w: Weapon, c: Commission, content: ContentCatalog): String = when (Commissions.fit(w, c)) {
        Commissions.Fit.OK -> "fits"
        Commissions.Fit.FAMILY -> "not a ${content.family(c.familyId).name}"
        Commissions.Fit.ELEMENT -> "not ${c.element?.name?.lowercase()}"
        Commissions.Fit.QUALITY -> "quality ${w.quality}, needs ${c.minQuality}"
    }

    /** Before End Day: the blade an accepted request will take ([Commissions.pick]), or what the nearest blade in the shop lacks. */
    fun readiness(c: Commission, weapons: Collection<Weapon>, content: ContentCatalog, config: BalanceConfig): String {
        Commissions.pick(weapons, c, config)?.let { return "Ready: ${it.name} will be handed over at End Day." }
        val family = content.family(c.familyId).name
        val nearest = weapons.filter { it.isInStorage || it.isListed }.maxWithOrNull(compareBy<Weapon> { Commissions.fit(it, c).ordinal }.thenBy { it.quality })
        val lacks = nearest?.let { Commissions.fit(it, c) }
        return "Nothing fits yet: " + when {
            nearest != null && lacks == Commissions.Fit.QUALITY -> "${nearest.name} is quality ${nearest.quality}, needs ${c.minQuality}."
            lacks == Commissions.Fit.ELEMENT -> "no ${c.element?.name?.lowercase()} $family in the shop."
            else -> "no $family in the shop."
        }
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

    /** Wear in words; null while the blade is still sound. "Worn" is the engine's own line (`BalanceConfig.wornConditionThreshold`), "battered" half of it. */
    fun condition(condition: Int): String? = when {
        condition < WORN_BELOW / 2 -> "battered"
        condition < WORN_BELOW -> "worn"
        else -> null
    }
    fun condition(w: Weapon): String? = condition(w.condition)
    private val WORN_BELOW = BalanceConfig().wornConditionThreshold

    /** Fame bands, descriptive only: the first starts at the Legend Board threshold (3), the last at the cap on the fame effect (BalanceConfig.weaponFameCap, 10). */
    fun fame(f: Int): String? = when {
        f >= 10 -> "renowned"
        f >= 6 -> "famed"
        f >= 3 -> "storied"
        else -> null
    }

    fun weaponSummary(w: Weapon, content: ContentCatalog): String = buildString {
        append(rarity(w.rarity)); append(" · "); append(quality(w.quality))
        condition(w)?.let { append(" · ").append(it) }
        if (w.affixes.isNotEmpty()) append(" · ").append(w.affixes.joinToString { content.affix(it).name })
        if (w.flaws.isNotEmpty()) append(" · flaw: ").append(w.flaws.joinToString { content.affix(it).name })
        fame(w.fame)?.let { append(" · ").append(it) }
        w.title?.let { append(" · \"").append(it).append('"') }
    }
}
