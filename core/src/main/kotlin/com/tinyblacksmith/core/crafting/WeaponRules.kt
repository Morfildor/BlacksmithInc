package com.tinyblacksmith.core.crafting

import com.tinyblacksmith.core.combat.EffectDef
import com.tinyblacksmith.core.combat.Fight
import com.tinyblacksmith.core.content.ContentCatalog
import com.tinyblacksmith.core.content.Element
import com.tinyblacksmith.core.model.AffixId
import com.tinyblacksmith.core.model.MaterialId
import com.tinyblacksmith.core.model.Weapon
import com.tinyblacksmith.core.model.WeaponFamilyId

/**
 * The one place a weapon's parts become the rules it follows in a fight. The fight (`Loadout`), the weapon sheet, the
 * forge outlook, the bench and the capability evaluator all read a [Composition]; none of them reads the catalog's
 * rule tables for a weapon by itself.
 *
 * Order, which a fight can observe because a holder's rules are asked in the order listed: family, metal, element,
 * each buff and then each flaw in stored order (its everyday rule before its home or road one), catalyst, signature.
 * The metal stands before the element on purpose: a metal's rule that waits for a state on the target does not see
 * the state its own element puts there with the same hit.
 *
 * A rule that two parts bring (the Charge burst of Stormcharged and of Binding Salt) is listed once, under the first,
 * and the other part is named in [Rule.alsoFrom]. Display names never decide anything: every lookup is by ID.
 */
object WeaponRules {
    /** The composition a forge writes on a new weapon today. */
    const val CRAFT = 2
    /** From this composition on, the metal has a rule and a share of strike and support. */
    const val CRAFT_CORES = 2

    enum class Source(val word: String) { FAMILY("weapon"), CORE("metal"), ELEMENT("element"), AFFIX("buff"), FLAW("flaw"), CATALYST("catalyst"), SIGNATURE("signature") }

    /** Where a rule holds: everywhere, only on a contract, or only on the town's wall. */
    enum class Where { ANYWHERE, ROAD, HOME }

    /** What a weapon is made of, as far as a fight cares. [craft] is the composition it was forged under. */
    data class Parts(
        val familyId: WeaponFamilyId, val coreId: MaterialId, val element: Element?, val catalystId: MaterialId? = null,
        val affixes: List<AffixId> = emptyList(), val flaws: List<AffixId> = emptyList(), val signatureId: String? = null, val craft: Int = CRAFT,
    )

    data class Rule(val effect: EffectDef, val source: Source, val sourceId: String, val sourceName: String, val where: Where = Where.ANYWHERE, val alsoFrom: List<String> = emptyList()) {
        val id: String get() = effect.id
    }

    data class Composition(val parts: Parts, val strikePercent: Int, val supportPercent: Int, val pattern: String?, val rules: List<Rule>) {
        fun rules(home: Boolean): List<Rule> = rules.filter { it.where == Where.ANYWHERE || (it.where == Where.HOME) == home }
        fun effects(home: Boolean): List<EffectDef> = rules(home).map { it.effect }
        fun rule(id: String): Rule? = rules.firstOrNull { it.id == id }
    }

    fun parts(weapon: Weapon): Parts = Parts(weapon.familyId, weapon.coreId, weapon.element, weapon.catalystId, weapon.affixes, weapon.flaws, weapon.signatureId, weapon.craft)

    fun of(weapon: Weapon, content: ContentCatalog): Composition = compose(parts(weapon), content)

    fun compose(parts: Parts, content: ContentCatalog): Composition {
        val combat = content.combat ?: error("This catalog has no combat content")
        val family = combat.familyById[parts.familyId]
        val core = combat.coreById[parts.coreId].takeIf { parts.craft >= CRAFT_CORES }
        val listed = ArrayList<Rule>()
        fun add(effects: List<EffectDef>?, source: Source, id: String, name: String, where: Where = Where.ANYWHERE) = effects.orEmpty().forEach { listed += Rule(it, source, id, name, where) }
        add(family?.effects, Source.FAMILY, parts.familyId.value, content.familyById[parts.familyId]?.name ?: parts.familyId.value)
        add(core?.effects, Source.CORE, parts.coreId.value, content.materialById[parts.coreId]?.name ?: parts.coreId.value)
        parts.element?.let { add(combat.elementEffects[it], Source.ELEMENT, it.name, it.name.lowercase().replaceFirstChar { c -> c.uppercase() }) }
        fun affix(a: AffixId, source: Source) {
            val name = content.affixById[a]?.name ?: a.value
            add(combat.affixEffects[a], source, a.value, name)
            add(combat.affixRoadEffects[a], source, a.value, name, Where.ROAD)
            add(combat.affixHomeEffects[a], source, a.value, name, Where.HOME)
        }
        parts.affixes.forEach { affix(it, Source.AFFIX) }
        parts.flaws.forEach { affix(it, Source.FLAW) }
        parts.catalystId?.let { add(combat.catalystEffects[it], Source.CATALYST, it.value, content.materialById[it]?.name ?: it.value) }
        parts.signatureId?.let { add(combat.signatureEffects[it], Source.SIGNATURE, it, SignatureCatalog.byId[it]?.name ?: it) }

        // One rule, one entry: the first part to bring it keeps it and the others are named beside it.
        val rules = LinkedHashMap<String, Rule>()
        for (r in listed) {
            val first = rules[r.id]
            rules[r.id] = if (first == null) r else if (r.sourceName == first.sourceName || r.sourceName in first.alsoFrom) first else first.copy(alsoFrom = first.alsoFrom + r.sourceName)
        }
        return Composition(
            parts, Fight.pct(family?.strikePercent ?: 100, core?.strikePercent ?: 100), Fight.pct(family?.supportPercent ?: 100, core?.supportPercent ?: 100),
            family?.pattern, rules.values.toList(),
        )
    }
}
