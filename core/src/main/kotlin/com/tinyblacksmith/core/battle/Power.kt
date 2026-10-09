package com.tinyblacksmith.core.battle

import com.tinyblacksmith.core.config.BalanceConfig
import com.tinyblacksmith.core.content.ContentCatalog
import com.tinyblacksmith.core.content.FactionDef
import com.tinyblacksmith.core.model.Hero
import com.tinyblacksmith.core.model.Weapon

/** GDD 6 PROPOSED: effectivePower = (base + weapon) * classFit * condition * matchup * traits. All factors bounded. */
object Power {
    fun classFit(hero: Hero, weapon: Weapon?, content: ContentCatalog, config: BalanceConfig): Double =
        weapon?.let { content.family(it.familyId).classFit[hero.classId] ?: config.offFamilyFit } ?: 1.0

    fun condition(hero: Hero): Double = 0.6 + 0.4 * (hero.health.coerceIn(0, 100) / 100.0)

    fun traitModifier(hero: Hero, content: ContentCatalog): Double =
        hero.traits.fold(1.0) { acc, t -> acc * content.trait(t).combatModifier }.coerceIn(0.8, 1.2)

    fun matchup(weapon: Weapon?, faction: FactionDef, config: BalanceConfig): Double {
        val el = weapon?.element ?: return 1.0
        return when (el) {
            faction.weakTo -> config.matchupWeakBonus
            faction.resists -> config.matchupResistPenalty
            else -> 1.0
        }
    }

    fun affixAttackMultiplier(weapon: Weapon?, content: ContentCatalog): Double =
        weapon?.let { w -> (w.affixes + w.flaws).fold(1.0) { acc, a -> acc * content.affix(a).attackMultiplier } } ?: 1.0

    fun affixDefenseMultiplier(weapon: Weapon?, content: ContentCatalog): Double =
        weapon?.let { w -> (w.affixes + w.flaws).fold(1.0) { acc, a -> acc * content.affix(a).defenseMultiplier } } ?: 1.0

    /** Affixes that single out a faction (bane) or an elite/warlord foe. */
    fun affixMatchup(weapon: Weapon?, faction: FactionDef, content: ContentCatalog, elite: Boolean): Double =
        weapon?.let { w ->
            (w.affixes + w.flaws).fold(1.0) { acc, a ->
                val d = content.affix(a)
                acc * (if (d.baneFaction == faction.id) d.baneMultiplier else 1.0) * (if (elite) d.eliteMultiplier else 1.0)
            }
        } ?: 1.0

    fun heroBase(hero: Hero, content: ContentCatalog): Int {
        val c = content.heroClass(hero.classId)
        return c.basePower + c.powerPerLevel * (hero.level - 1)
    }

    fun weaponPower(weapon: Weapon?, config: BalanceConfig): Int = weapon?.power ?: config.unarmedPower

    /** Weapon wear: 1.0 at condition 100 down to [BalanceConfig.conditionFloorFactor] at 0, linear; bare hands do not wear. */
    fun conditionFactor(weapon: Weapon?, config: BalanceConfig): Double =
        weapon?.let { 1.0 - (1.0 - config.conditionFloorFactor) * (100 - it.condition.coerceIn(0, 100)) / 100.0 } ?: 1.0

    fun attackPower(hero: Hero, weapon: Weapon?, faction: FactionDef, content: ContentCatalog, config: BalanceConfig, blessingPercent: Int = 0, elite: Boolean = false): Double =
        (heroBase(hero, content) + weaponPower(weapon, config) * conditionFactor(weapon, config)) *
            classFit(hero, weapon, content, config) * condition(hero) * matchup(weapon, faction, config) *
            traitModifier(hero, content) * affixAttackMultiplier(weapon, content) * affixMatchup(weapon, faction, content, elite) * (1.0 + blessingPercent / 100.0)

    fun defensePower(hero: Hero, weapon: Weapon?, faction: FactionDef, content: ContentCatalog, config: BalanceConfig, blessingPercent: Int = 0, elite: Boolean = false): Double {
        val familyWeight = weapon?.let { content.family(it.familyId).defensiveWeight } ?: 0.9
        return (heroBase(hero, content) + weaponPower(weapon, config) * conditionFactor(weapon, config)) *
            classFit(hero, weapon, content, config) * condition(hero) * matchup(weapon, faction, config) *
            traitModifier(hero, content) * affixDefenseMultiplier(weapon, content) * affixMatchup(weapon, faction, content, elite) * familyWeight * (1.0 + blessingPercent / 100.0)
    }
}
