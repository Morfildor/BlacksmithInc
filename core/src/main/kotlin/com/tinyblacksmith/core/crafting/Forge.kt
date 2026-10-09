package com.tinyblacksmith.core.crafting

import com.tinyblacksmith.core.content.AffixKind
import com.tinyblacksmith.core.content.BlessingEffect
import com.tinyblacksmith.core.content.MaterialCategory
import com.tinyblacksmith.core.content.UpgradeEffect
import com.tinyblacksmith.core.engine.Command
import com.tinyblacksmith.core.engine.GameError
import com.tinyblacksmith.core.engine.ResolutionContext
import com.tinyblacksmith.core.engine.Technique
import com.tinyblacksmith.core.model.*
import com.tinyblacksmith.core.rng.RngStream

/** Crafting resolver (GDD 4.3/4.4). Every valid attempt yields a usable weapon; materials/energy are consumed exactly once. */
object Forge {

    sealed interface Validation {
        data class Ok(val energyCost: Int, val overworkNeeded: Int) : Validation
        data class Error(val error: GameError) : Validation
    }

    fun validate(ctx: ResolutionContext, cmd: Command.Forge): Validation {
        val content = ctx.content
        val config = ctx.config
        if (ctx.phase == Phase.ENDED) return Validation.Error(GameError.RunEnded)
        content.familyById[cmd.familyId] ?: return Validation.Error(GameError.UnknownContent(cmd.familyId.value))
        val core = content.materialById[cmd.coreId] ?: return Validation.Error(GameError.UnknownContent(cmd.coreId.value))
        val augment = content.materialById[cmd.augmentId] ?: return Validation.Error(GameError.UnknownContent(cmd.augmentId.value))
        if (core.category != MaterialCategory.CORE) return Validation.Error(GameError.WrongMaterialCategory(cmd.coreId, "CORE"))
        if (augment.category != MaterialCategory.AUGMENT) return Validation.Error(GameError.WrongMaterialCategory(cmd.augmentId, "AUGMENT"))
        if (cmd.catalystId != null) {
            val cat = content.materialById[cmd.catalystId] ?: return Validation.Error(GameError.UnknownContent(cmd.catalystId.value))
            if (cat.category != MaterialCategory.CATALYST) return Validation.Error(GameError.WrongMaterialCategory(cmd.catalystId, "CATALYST"))
            if (cmd.mode != ForgeMode.ADVANCED) return Validation.Error(GameError.CatalystRequiresAdvanced(cmd.catalystId))
        }
        if (cmd.technique != null && cmd.mode != ForgeMode.ADVANCED) return Validation.Error(GameError.TechniqueRequiresAdvanced(cmd.technique))
        val needed = listOfNotNull(cmd.coreId, cmd.augmentId, cmd.catalystId)
        for (m in needed) if ((ctx.materials[m] ?: 0) < 1) return Validation.Error(GameError.MissingMaterial(m))
        val cost = if (cmd.mode == ForgeMode.QUICK) config.quickForgeEnergy else config.advancedForgeEnergy
        val overworkAvailable = config.maxOverworkPerDay - ctx.overworkToday
        val shortfall = maxOf(0, cost - ctx.energy)
        if (shortfall > overworkAvailable) return Validation.Error(GameError.NotEnoughEnergy(cost, ctx.energy, overworkAvailable))
        return Validation.Ok(cost, shortfall)
    }

    /** Applies a validated forge. Returns the new weapon. */
    fun apply(ctx: ResolutionContext, cmd: Command.Forge, ok: Validation.Ok): Weapon {
        val content = ctx.content
        val config = ctx.config
        val rng = ctx.rng(RngStream.CRAFTING)
        val family = content.family(cmd.familyId)
        val core = content.material(cmd.coreId)
        val augment = content.material(cmd.augmentId)
        val catalyst = cmd.catalystId?.let { content.material(it) }

        // Consume exactly once.
        ctx.energy -= (ok.energyCost - ok.overworkNeeded)
        ctx.overworkToday += ok.overworkNeeded
        for (m in listOfNotNull(cmd.coreId, cmd.augmentId, cmd.catalystId)) ctx.materials[m] = (ctx.materials[m] ?: 0) - 1
        // Thrifty Hands (MATERIAL_EFFICIENCY): percent chance per level that the augment survives the forge.
        val efficiency = upgradeTotal(ctx, UpgradeEffect.MATERIAL_EFFICIENCY)
        val augmentSaved = efficiency > 0 && rng.chance(efficiency / 100.0)
        if (augmentSaved) ctx.materials[cmd.augmentId] = (ctx.materials[cmd.augmentId] ?: 0) + 1

        val profile = config.risk.getValue(cmd.risk)
        var exceptionalChance = profile.exceptionalChance
        var defectChance = profile.defectChance
        if (catalyst != null) {
            exceptionalChance += config.catalystExceptionalBonus
            defectChance -= config.catalystExceptionalBonus
        }
        when (cmd.technique) {
            Technique.TEMPER -> { defectChance -= config.temperDefectReduction; exceptionalChance -= config.temperExceptionalReduction }
            Technique.ETCH -> defectChance += config.etchDefectIncrease
            Technique.QUENCH, null -> {}
        }
        exceptionalChance += (ctx.blessingMagnitude(BlessingEffect.EXCEPTIONAL_CHANCE) + upgradeTotal(ctx, UpgradeEffect.EXCEPTIONAL_CHANCE)) / 100.0
        exceptionalChance = exceptionalChance.coerceIn(0.0, config.maxRollChance)
        defectChance = defectChance.coerceIn(0.0, config.maxRollChance)

        val exceptional = rng.chance(exceptionalChance)
        val defect = rng.chance(defectChance)
        val roll = rng.nextInt(-config.qualityRollSpread, config.qualityRollSpread)

        val affinity = (content.coreAugmentAffinity[core.id to augment.id] ?: 0) +
            (content.augmentFamilyAffinity[augment.id to family.id] ?: 0)
        val mastery = masteryBonus(ctx) + (if (catalyst != null) config.catalystQualityBonus else 0)

        val quality = (config.qualityBase + config.qualityPerCoreTier * core.tier + config.qualityPerAugmentTier * augment.tier +
            affinity + mastery + roll + (if (exceptional) config.qualityExceptionalBonus else 0) -
            (if (defect) config.qualityDefectPenalty else 0) -
            (if (cmd.technique == Technique.QUENCH) config.quenchQualityPenalty else 0)).coerceIn(1, 100)
        val rarity = rarityFor(quality, config)

        val affixes = mutableListOf<AffixId>()
        val flaws = mutableListOf<AffixId>()
        val elementAffix = content.affixes.firstOrNull { it.kind == AffixKind.BENEFICIAL && it.element == augment.element }
        val neutralAffixes = content.affixes.filter { it.kind == AffixKind.BENEFICIAL && it.element == null }
        var affixSlots = when (rarity) { Rarity.COMMON -> 0; Rarity.UNCOMMON -> 0; Rarity.RARE -> 1; Rarity.EPIC -> 2; Rarity.LEGENDARY -> 3 } + (if (exceptional) 1 else 0)
        if (cmd.technique == Technique.ETCH) affixSlots += config.etchExtraAffixSlots
        // QUENCH forces the element affix even on a weapon that rolled no slot (the baseline already leads with it when a slot exists).
        if (cmd.technique == Technique.QUENCH && affixSlots == 0 && elementAffix != null) affixSlots = 1
        if (affixSlots > 0 && elementAffix != null) affixes += elementAffix.id
        while (affixes.size < affixSlots && neutralAffixes.any { it.id !in affixes }) {
            affixes += rng.pick(neutralAffixes.filter { it.id !in affixes }).id
        }
        if (defect) flaws += rng.pick(content.affixes.filter { it.kind == AffixKind.FLAW }).id

        // Signature transformation (GDD 4.5): exact recipe + conditions + quality floor, then a capped roll on the crafting stream.
        val recipe = SignatureCatalog.forRecipe(cmd)
        val miss = recipe?.missing(cmd, quality)
        var signature: SignatureDef? = null
        if (recipe != null && miss == null) {
            val chance = (config.signatureBaseChance + masteryBonus(ctx) * config.signatureChancePerMastery).coerceIn(0.0, config.signatureMaxChance)
            if (rng.chance(chance)) signature = recipe
        }
        if (signature != null) for (a in signature.grantedAffixes) if (a !in affixes) affixes += a

        val affixPower = (affixes + flaws).sumOf { content.affix(it).power }
        val power = maxOf(1, family.basePower + config.powerPerCoreTier * core.tier + quality / config.powerPerQualityDivisor + affixPower + (signature?.bonusPower ?: 0))

        val prefix = affixes.joinToString(" ") { content.affix(it).name }
        val name = signature?.name ?: buildString {
            if (prefix.isNotEmpty()) append(prefix).append(' ')
            append(core.name).append(' ').append(family.name)
        }
        val techniqueNote = cmd.technique?.let { ", ${it.name.lowercase()}ed" } ?: ""
        val id = ctx.newWeaponId()
        val weapon = Weapon(
            id = id, name = name, familyId = family.id, coreId = core.id, augmentId = augment.id, catalystId = catalyst?.id,
            mode = cmd.mode, risk = cmd.risk, quality = quality, rarity = rarity, power = power, element = augment.element,
            affixes = affixes, flaws = flaws, location = WeaponLocation.Storage, forgedEra = ctx.era, forgedDay = ctx.day,
            history = listOfNotNull(
                HistoryEntry(ctx.era, ctx.day, "FORGED", "Forged from ${core.name} and ${augment.name} (${cmd.risk.name.lowercase()} risk$techniqueNote)."),
                signature?.let { HistoryEntry(ctx.era, ctx.day, "SIGNATURE", "${it.name}: ${it.flavor}") },
            ),
            signatureId = signature?.id,
        )
        ctx.updateWeapon(weapon)
        ctx.emit(
            EventType.WEAPON_FORGED, 1,
            "The smith forged $name (${rarity.name.lowercase()}, quality $quality)${if (flaws.isNotEmpty()) " with a flaw: ${flaws.joinToString { content.affix(it).name }}" else ""}.",
            subjects = listOf(id.value),
            data = mapOf("quality" to quality.toString(), "rarity" to rarity.name, "exceptional" to exceptional.toString(), "defect" to defect.toString(), "augmentSaved" to augmentSaved.toString(),
                "technique" to (cmd.technique?.name ?: "")),
        )
        if (rarity == Rarity.LEGENDARY) ctx.milestone("LEGENDARY_FORGED", "A legendary weapon, $name, left the anvil.")
        else if (rarity == Rarity.EPIC) ctx.milestone("EPIC_FORGED", "An epic weapon, $name, left the anvil.")

        if (signature != null) {
            val first = Journal.recordSignatureDiscovered(ctx, signature)
            ctx.emit(
                EventType.SIGNATURE_DISCOVERED, 6,
                if (first) "The metal answered: ${signature.name} was born on the anvil. ${signature.flavor}" else "${signature.name} rose from the anvil once more.",
                subjects = listOf(id.value), data = mapOf("key" to signature.journalKey, "first" to first.toString()),
            )
        } else if (recipe != null) {
            Journal.recordSignatureClue(ctx, recipe, miss)
        }
        Journal.recordExperiment(ctx, core.id, augment.id, family.id, affinity)
        return weapon
    }

    fun masteryBonus(ctx: ResolutionContext): Int =
        upgradeTotal(ctx, UpgradeEffect.QUALITY_BONUS) + ctx.blessingMagnitude(BlessingEffect.QUALITY_BONUS) + ctx.toolTotal(com.tinyblacksmith.core.content.ToolEffect.QUALITY_BONUS)

    private fun upgradeTotal(ctx: ResolutionContext, effect: UpgradeEffect): Int =
        ctx.content.upgrades.filter { it.effect == effect }.sumOf { it.magnitudePerLevel * ctx.legacy.upgradeLevel(it.id) }

    fun rarityFor(quality: Int, config: com.tinyblacksmith.core.config.BalanceConfig): Rarity = when {
        quality >= config.legendaryMin -> Rarity.LEGENDARY
        quality >= config.epicMin -> Rarity.EPIC
        quality >= config.rareMin -> Rarity.RARE
        quality >= config.uncommonMin -> Rarity.UNCOMMON
        else -> Rarity.COMMON
    }

}
