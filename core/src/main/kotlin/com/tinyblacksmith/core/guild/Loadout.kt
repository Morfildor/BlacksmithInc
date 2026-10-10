package com.tinyblacksmith.core.guild

import com.tinyblacksmith.core.battle.Power
import com.tinyblacksmith.core.combat.Combatant
import com.tinyblacksmith.core.combat.EffectDef
import com.tinyblacksmith.core.combat.Fight
import com.tinyblacksmith.core.combat.Side
import com.tinyblacksmith.core.combat.UnitDef
import com.tinyblacksmith.core.config.BalanceConfig
import com.tinyblacksmith.core.content.CombatCatalog
import com.tinyblacksmith.core.content.CombatContent
import com.tinyblacksmith.core.content.ContentCatalog
import com.tinyblacksmith.core.model.Hero
import com.tinyblacksmith.core.model.Rarity
import com.tinyblacksmith.core.model.Weapon
import kotlin.math.roundToInt

/** The one place a hero and a blade become a fighter of the interaction engine, and a unit of the catalog an enemy. */
object Loadout {
    /** Where the fight is: some rules differ at home (a siege) and on the road (a mission). */
    enum class Field { ROAD, HOME }

    /**
     * Every rule [weapon] brings to a fight, in the order the fight asks them: family, element, affixes and flaws, catalyst,
     * signature. A rule two sources share (the Charge burst) is listed once.
     */
    fun weaponEffects(weapon: Weapon?, combat: CombatCatalog, field: Field = Field.ROAD): List<EffectDef> {
        if (weapon == null) return emptyList()
        val situational = if (field == Field.HOME) combat.affixHomeEffects else combat.affixRoadEffects
        return (combat.familyById[weapon.familyId]?.effects.orEmpty() +
            weapon.element?.let { combat.elementEffects[it] }.orEmpty() +
            (weapon.affixes + weapon.flaws).flatMap { combat.affixEffects[it].orEmpty() + situational[it].orEmpty() } +
            weapon.catalystId?.let { combat.catalystEffects[it] }.orEmpty() +
            weapon.signatureId?.let { combat.signatureEffects[it] }.orEmpty()).distinctBy { it.id }
    }

    /** What the blade adds to a strike in [hero]'s hands: its worn power, the class's fit for the family, its affixes' edge and its fame. */
    fun weaponStrike(hero: Hero, weapon: Weapon?, content: ContentCatalog, config: BalanceConfig, combat: CombatCatalog): Int =
        (Power.weaponPower(weapon, config) * Power.conditionFactor(weapon, config) * Power.classFit(hero, weapon, content, config) * Power.affixAttackMultiplier(weapon, content) *
            Power.fameFactor(weapon, config) / combat.powerPerStrike).roundToInt()

    fun maxHealth(hero: Hero, combat: CombatCatalog): Int = combat.kitByClass.getValue(hero.classId).let { it.health + it.healthPerLevel * (hero.level - 1) }

    fun combatant(hero: Hero, weapon: Weapon?, content: ContentCatalog, config: BalanceConfig, field: Field = Field.ROAD, extraTags: Set<String> = emptySet()): Combatant {
        val combat = content.combat ?: error("This catalog has no combat content")
        val kit = combat.kitByClass.getValue(hero.classId)
        val family = weapon?.let { combat.familyById[it.familyId] }
        val max = maxHealth(hero, combat)
        val base = kit.strike + kit.strikePerLevel * (hero.level - 1) + weaponStrike(hero, weapon, content, config, combat)
        val strike = maxOf(1, (Fight.pct(base, family?.strikePercent ?: 100) * Power.traitModifier(hero, content, config)).roundToInt())
        val support = Fight.pct(kit.support + kit.supportPerLevel * (hero.level - 1), family?.supportPercent ?: 100)
        val effects = weaponEffects(weapon, combat, field)
        val tags = buildSet {
            addAll(extraTags)
            if (weapon == null || weapon.rarity == Rarity.COMMON) add(CombatContent.TAG_COMMON_GEAR)
            if (effects.any { it.id == CombatContent.CHARGE_BURST.id }) add(CombatContent.TAG_CHARGE)
        }
        return Combatant(
            key = hero.id.value, name = hero.name, side = Side.PARTY, kit = kit.kit, maxHealth = max,
            // A wounded hero enters with the same share of the fight's health; never with none.
            health = maxOf(1, (max * hero.health + 99) / 100), strikeMin = strike, support = support, element = weapon?.element, effects = effects, tags = tags, weaponName = weapon?.name,
        )
    }

    /** A fight's health back on the hero's 0..100 scale; a fighter who was not downed keeps at least 1. */
    fun heroHealth(health: Int, maxHealth: Int): Int = if (health <= 0) 0 else maxOf(1, (health * 100 + maxHealth / 2) / maxHealth).coerceAtMost(100)

    /** An enemy as it stands on the field: the unit's numbers scaled by [percent] (100 = as written). */
    fun enemy(unit: UnitDef, key: String, percent: Int = 100, name: String = unit.name): Combatant = Combatant(
        key = key, name = name, side = Side.ENEMY, kit = unit.kit, maxHealth = maxOf(1, Fight.pct(unit.health, percent)), strikeMin = Fight.pct(unit.strikeMin, percent),
        strikeMax = Fight.pct(unit.strikeMax, percent), support = Fight.pct(unit.support, percent), weakTo = unit.weakTo, resists = unit.resists, effects = unit.effects, tags = unit.tags, tickResist = unit.tickResist,
    )
}
