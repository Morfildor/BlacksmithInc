package com.tinyblacksmith.core.crafting

import com.tinyblacksmith.core.combat.EffectDef
import com.tinyblacksmith.core.config.BalanceConfig
import com.tinyblacksmith.core.content.AffixKind
import com.tinyblacksmith.core.content.BlessingEffect
import com.tinyblacksmith.core.content.ContentCatalog
import com.tinyblacksmith.core.content.UpgradeEffect
import com.tinyblacksmith.core.engine.Command
import com.tinyblacksmith.core.engine.ResolutionContext
import com.tinyblacksmith.core.engine.Technique
import com.tinyblacksmith.core.model.GameState
import com.tinyblacksmith.core.model.KnowledgeState
import com.tinyblacksmith.core.model.Rarity

/**
 * What can honestly be said of a recipe before it is forged. [Outlook.certain] holds only rules every forge of this
 * recipe carries: the family's, the metal's, the element's, the catalyst's, and the element's buff when the recipe
 * forces a place for it. Everything a roll decides is in [Outlook.possible] and is never counted as a rule the weapon
 * has. A projection of `Forge.apply` from its public parts. `ForgeOutlookTest` forges many seeds and holds it to this.
 */
object ForgeOutlook {
    enum class Kind { ELEMENT_BUFF, OTHER_BUFF, FLAW, SIGNATURE }

    /** Something the roll may add. [rules] is what it would bring to a fight, empty where that depends on which one is rolled. */
    data class Possible(val kind: Kind, val name: String, val text: String, val rules: List<EffectDef> = emptyList())

    /** One chosen ingredient: what it is for and what choosing it costs. */
    data class Part(val source: WeaponRules.Source, val name: String, val role: String, val constraint: String?)

    data class Outlook(
        val role: String, val wielders: List<String>, val parts: List<Part>, val certain: WeaponRules.Composition, val possible: List<Possible>,
        /** By rule ID: what a certain rule still waits for from the company it was asked about. */
        val missing: Map<String, List<Need>>, val notes: List<CompositionNote>, val tradeoff: String?, val qualityLow: Int, val qualityHigh: Int,
    )

    /** The weapon's part in a party, from what its certain rules do. One label, most particular first. */
    fun role(facets: List<Facet>): String {
        fun gives(res: Res, test: (On) -> Boolean = { true }) = facets.any { it.verb == Verb.PROVIDES && it.res == res && test(it.on) }
        return when {
            gives(Res.GUARD) { it.others } || gives(Res.HEALING) { it.others } -> "Protects the party"
            gives(Res.GUARD) { it == On.WEAKEST_ALLY } -> "Shields whoever is weakest"
            facets.any { it.verb == Verb.SPENDS && it.res == Res.STEAM } -> "Turns heat and cold into Steam"
            gives(Res.FRACTURE) -> "Cracks to help the party"
            facets.any { it.verb == Verb.SPENDS && it.res == Res.CHARGE } -> "Builds to a burst"
            gives(Res.DAMAGE) { it.back } -> "Reaches the back of the line"
            gives(Res.GUARD) { it == On.SELF } || gives(Res.THORNS) -> "Holds the line"
            gives(Res.BURN) || gives(Res.BLEED) -> "Wears the enemy down"
            gives(Res.CHILL) -> "Weakens enemy attacks"
            gives(Res.MARK) -> "Marks a target for the next hit"
            gives(Res.REGEN) || gives(Res.HEALING) -> "Keeps its wielder standing"
            else -> "A plain striker"
        }
    }

    /** Pure and read-only: a context is opened on [state] and thrown away. Null when the catalog has no fights or the recipe names unknown content. */
    fun of(state: GameState, content: ContentCatalog, config: BalanceConfig, cmd: Command.Forge, company: Company = Company()): Outlook? {
        val combat = content.combat ?: return null
        val family = content.familyById[cmd.familyId] ?: return null
        val core = content.materialById[cmd.coreId] ?: return null
        val augment = content.materialById[cmd.augmentId] ?: return null
        val catalyst = cmd.catalystId?.let { content.materialById[it] }
        val ctx = ResolutionContext(state, content, config)
        val profile = config.risk.getValue(cmd.risk)

        // The same chances `Forge.apply` rolls against.
        fun upgrades(effect: UpgradeEffect) = content.upgrades.filter { it.effect == effect }.sumOf { it.magnitudePerLevel * state.legacy.upgradeLevel(it.id) }
        var exceptional = profile.exceptionalChance + (if (catalyst != null) config.catalystExceptionalBonus else 0.0)
        var defect = profile.defectChance - (if (catalyst != null) config.catalystExceptionalBonus else 0.0)
        when (cmd.technique) {
            Technique.TEMPER -> { defect -= config.temperDefectReduction; exceptional -= config.temperExceptionalReduction }
            Technique.ETCH -> defect += config.etchDefectIncrease
            Technique.QUENCH, null -> {}
        }
        exceptional = (exceptional + (ctx.blessingMagnitude(BlessingEffect.EXCEPTIONAL_CHANCE) + upgrades(UpgradeEffect.EXCEPTIONAL_CHANCE)) / 100.0).coerceIn(0.0, config.maxRollChance)
        defect = defect.coerceIn(0.0, config.maxRollChance)

        val centre = Forge.centreQuality(ctx, cmd)
        val spread = Forge.rollSpread(config, cmd.risk)
        val low = (centre - spread).coerceIn(1, 100)
        val high = (centre + spread).coerceIn(1, 100)
        val worst = (centre - spread - (if (defect > 0.0) config.qualityDefectPenalty else 0)).coerceIn(1, 100)
        val best = (centre + spread + (if (exceptional > 0.0) config.qualityExceptionalBonus else 0)).coerceIn(1, 100)

        // Places for a buff, as the forge counts them: the least and the most this recipe can come out with.
        fun places(r: Rarity) = when (r) { Rarity.COMMON, Rarity.UNCOMMON -> 0; Rarity.RARE -> 1; Rarity.EPIC -> 2; Rarity.LEGENDARY -> 3 }
        val fixed = (if (cmd.technique == Technique.ETCH) config.etchExtraAffixSlots else 0) + profile.extraSlots + (if (cmd.bellows) config.depth.bellowsExtraSlots else 0)
        val heartfireLeast = if (cmd.heartfire) minOf(config.depth.legend.missExtraSlots + places(Forge.forgedRarity(worst, config, core.tier)), places(Rarity.LEGENDARY)) else places(Forge.forgedRarity(worst, config, core.tier))
        val least = fixed + heartfireLeast
        val most = fixed + (if (exceptional > 0.0) 1 else 0) + (if (cmd.heartfire) maxOf(places(Rarity.LEGENDARY), places(Forge.forgedRarity(best, config, core.tier)) + config.depth.legend.missExtraSlots) else places(Forge.forgedRarity(best, config, core.tier)))

        val elementBuff = content.affixes.firstOrNull { it.kind == AffixKind.BENEFICIAL && it.element == augment.element }
        val elementBuffCertain = elementBuff != null && (least >= 1 || cmd.technique == Technique.QUENCH)
        val parts = WeaponRules.Parts(family.id, core.id, augment.element, catalyst?.id, listOfNotNull(elementBuff?.id.takeIf { elementBuffCertain }))
        val certain = WeaponRules.compose(parts, content)
        val facets = Capabilities.facets(certain, company.home)

        val possible = buildList {
            if (elementBuff != null && !elementBuffCertain && (most >= 1)) add(Possible(Kind.ELEMENT_BUFF, elementBuff.name,
                "It needs a free buff slot, and only a good enough result has one. The Quench technique always adds it.", combat.affixEffects[elementBuff.id].orEmpty()))
            val others = content.affixes.filter { it.kind == AffixKind.BENEFICIAL && it.element == null }
            if (most > (if (elementBuff != null) 1 else 0) && others.isNotEmpty()) add(Possible(Kind.OTHER_BUFF, "Another buff", "A good enough result can also add one of these: ${others.joinToString(", ") { it.name }}. Which one is random."))
            if (defect > 0.0) add(Possible(Kind.FLAW, "A flaw", "This forging can leave a flaw. Which flaw is random, and each flaw also has its own effect in a fight."))
            // A signature is said only when the journal already holds it. A clue stays a clue, in the notes.
            SignatureCatalog.forRecipe(cmd)?.takeIf { state.legacy.journal.state(it.journalKey) == KnowledgeState.SIGNATURE_DISCOVERED }?.let { sig ->
                val exact = sig.misses(cmd, best).isEmpty()
                add(Possible(Kind.SIGNATURE, sig.name, if (exact) "A signature weapon you have discovered. This is its exact recipe, so it usually succeeds." else "A signature weapon you have discovered, but this is not its exact recipe.", combat.signatureEffects[sig.id].orEmpty()))
            }
        }

        val rows = buildList {
            add(Part(WeaponRules.Source.FAMILY, family.name, combat.familyById[family.id]?.pattern.orEmpty(), null))
            combat.coreById[core.id]?.let { add(Part(WeaponRules.Source.CORE, core.name, it.role, it.constraint)) }
            combat.notes[augment.id]?.let { add(Part(WeaponRules.Source.ELEMENT, augment.name, it.role, it.constraint)) }
            if (catalyst != null) combat.notes[catalyst.id]?.let { add(Part(WeaponRules.Source.CATALYST, catalyst.name, it.role, it.constraint)) }
        }
        return Outlook(
            role = role(facets),
            wielders = content.classes.filter { (family.classFit[it.id] ?: 0.0) >= 1.0 }.map { it.name },
            parts = rows, certain = certain, possible = possible, missing = Capabilities.missing(certain, company), notes = Capabilities.notes(certain, company.home),
            // The choice that costs most to make says its price first: the catalyst, then the metal, then the augment.
            tradeoff = rows.lastOrNull { it.source == WeaponRules.Source.CATALYST }?.constraint ?: rows.firstOrNull { it.source == WeaponRules.Source.CORE }?.constraint ?: rows.firstOrNull { it.source == WeaponRules.Source.ELEMENT }?.constraint,
            qualityLow = low, qualityHigh = high,
        )
    }
}
