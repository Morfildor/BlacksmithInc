package com.tinyblacksmith.core.crafting

import com.tinyblacksmith.core.content.AffixKind
import com.tinyblacksmith.core.content.BlessingEffect
import com.tinyblacksmith.core.content.MaterialCategory
import com.tinyblacksmith.core.content.UpgradeEffect
import com.tinyblacksmith.core.engine.Command
import com.tinyblacksmith.core.engine.GameError
import com.tinyblacksmith.core.engine.Relics
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
        if (cmd.heartfire && cmd.mode != ForgeMode.ADVANCED) return Validation.Error(GameError.HeartfireRequiresAdvanced)
        if (cmd.heartfire && ctx.heartfire < 1) return Validation.Error(GameError.NoHeartfire)
        val needed = listOfNotNull(cmd.coreId, cmd.augmentId, cmd.catalystId)
        for (m in needed) if ((ctx.materials[m] ?: 0) < 1) return Validation.Error(GameError.MissingMaterial(m))
        val cost = energyCost(config, cmd)
        val overworkAvailable = config.maxOverworkPerDay - ctx.overworkToday
        val shortfall = maxOf(0, cost - ctx.energy)
        if (shortfall > overworkAvailable) return Validation.Error(GameError.NotEnoughEnergy(cost, ctx.energy, overworkAvailable))
        if (cmd.bellows) Relics.bellowsError(ctx, cost, shortfall)?.let { return Validation.Error(it) }
        return Validation.Ok(cost, shortfall)
    }

    fun energyCost(config: com.tinyblacksmith.core.config.BalanceConfig, cmd: Command.Forge): Int =
        (if (cmd.mode == ForgeMode.QUICK) config.quickForgeEnergy else config.advancedForgeEnergy) + (if (cmd.heartfire) config.depth.legend.heartfireEnergy else 0)

    /** The quality roll's reach either way under [risk]. */
    fun rollSpread(config: com.tinyblacksmith.core.config.BalanceConfig, risk: Risk): Int = config.risk.getValue(risk).rollSpread ?: config.qualityRollSpread

    /**
     * The quality this forge lands on before luck: materials, affinity, mastery, catalyst, ledger and technique. The
     * roll moves it by [rollSpread] either way, an exceptional result adds to it and a defect takes from it.
     */
    fun centreQuality(ctx: ResolutionContext, cmd: Command.Forge): Int {
        val config = ctx.config
        val core = ctx.content.material(cmd.coreId)
        val augment = ctx.content.material(cmd.augmentId)
        val affinity = (ctx.content.coreAugmentAffinity[core.id to augment.id] ?: 0) + (ctx.content.augmentFamilyAffinity[augment.id to cmd.familyId] ?: 0)
        return config.qualityBase + config.qualityPerCoreTier * core.tier + config.qualityPerAugmentTier * augment.tier + affinity + masteryBonus(ctx) +
            (if (cmd.catalystId != null) config.catalystQualityBonus else 0) + Relics.ledgerBonus(ctx, cmd.familyId) - (if (cmd.technique == Technique.QUENCH) config.quenchQualityPenalty else 0)
    }

    /** The chance that a Heartfire forging of [quality] breaks through to Legendary: none under the Epic floor. */
    fun breakthroughChance(ctx: ResolutionContext, cmd: Command.Forge, quality: Int): Double {
        val cfg = ctx.config.depth.legend
        if (quality < ctx.config.epicMin || !holdsEpic(ctx.content.material(cmd.coreId).tier, ctx.config)) return 0.0
        val known = SignatureCatalog.forRecipe(cmd)?.let { ctx.legacy.journal.state(it.journalKey) == KnowledgeState.SIGNATURE_DISCOVERED } == true
        return (cfg.breakthroughBase + cfg.breakthroughPerQuality * (quality - ctx.config.epicMin) + (if (known) cfg.knownRecipeBonus else 0.0) +
            when (cmd.risk) { Risk.SAFE -> cfg.safeBonus; Risk.BALANCED -> 0.0; Risk.RECKLESS -> cfg.recklessBonus }).coerceIn(0.0, cfg.breakthroughMax)
    }

    /**
     * What a Heartfire forging of [cmd] would be, for the workbench: everything shown before the smith confirms.
     * [qualityLow]..[qualityHigh] is the plain roll (no exceptional result, no defect); the chances are at those two ends.
     */
    data class HeartfireOutlook(val held: Int, val energy: Int, val floor: Int, val qualityLow: Int, val qualityHigh: Int, val chanceLow: Double, val chanceHigh: Double, val missSlots: Int,
        /** False when the recipe's metal cannot hold Epic work: Heartfire does not take in it at any quality. */
        val fineMetal: Boolean = true)

    fun heartfireOutlook(ctx: ResolutionContext, cmd: Command.Forge): HeartfireOutlook {
        val centre = centreQuality(ctx, cmd)
        val spread = rollSpread(ctx.config, cmd.risk)
        val low = (centre - spread).coerceIn(1, 100)
        val high = (centre + spread).coerceIn(1, 100)
        return HeartfireOutlook(ctx.heartfire, energyCost(ctx.config, cmd.copy(mode = ForgeMode.ADVANCED, heartfire = true)), ctx.config.epicMin, low, high,
            breakthroughChance(ctx, cmd, low), breakthroughChance(ctx, cmd, high), ctx.config.depth.legend.missExtraSlots, holdsEpic(ctx.content.material(cmd.coreId).tier, ctx.config))
    }

    /** Only the fine metals (`LegendConfig.fineCoreTier` and above) hold Epic work. In any other core, quality stops at Rare. */
    fun holdsEpic(coreTier: Int, config: com.tinyblacksmith.core.config.BalanceConfig): Boolean = coreTier >= config.depth.legend.fineCoreTier

    /**
     * The rarity a forged weapon's quality gives it in a core of [coreTier]: never past Epic, and never past Rare in a
     * metal that does not hold Epic work. Legendary is earned (`Legends`), not rolled. Without a tier only the first holds
     * (a legend that comes back from the board).
     */
    fun forgedRarity(quality: Int, config: com.tinyblacksmith.core.config.BalanceConfig, coreTier: Int? = null): Rarity =
        minOf(rarityFor(quality, config), if (coreTier == null || holdsEpic(coreTier, config)) Rarity.EPIC else Rarity.RARE)

    /** The rarity of [weapon] at a new [quality] (a hone): it never falls, a Legendary weapon stays one, no other becomes one. */
    fun rarityAfter(weapon: Weapon, quality: Int, config: com.tinyblacksmith.core.config.BalanceConfig, coreTier: Int): Rarity =
        maxOf(weapon.rarity, forgedRarity(quality, config, coreTier))

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
        if (cmd.heartfire) ctx.heartfire -= 1
        // Thrifty Hands (MATERIAL_EFFICIENCY): percent chance per level that the augment survives the forge.
        val efficiency = upgradeTotal(ctx, UpgradeEffect.MATERIAL_EFFICIENCY)
        val augmentSaved = efficiency > 0 && rng.chance(efficiency / 100.0)
        if (augmentSaved) ctx.materials[cmd.augmentId] = (ctx.materials[cmd.augmentId] ?: 0) + 1

        // Ashen Bellows: the debt joins today's overwork, after the forge's own; the Tempering Ledger rewards a family the streak has not seen.
        val bellowsSlots = if (cmd.bellows) Relics.blow(ctx) else 0
        val ledger = Relics.ledgerBonus(ctx, family.id)
        Relics.noteForge(ctx, family.id)

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
        // One draw whatever the temper: a narrower temper scales the same roll, so the stream is spent alike.
        val roll = rng.nextInt(-config.qualityRollSpread, config.qualityRollSpread) * rollSpread(config, cmd.risk) / config.qualityRollSpread

        val affinity = (content.coreAugmentAffinity[core.id to augment.id] ?: 0) +
            (content.augmentFamilyAffinity[augment.id to family.id] ?: 0)
        val mastery = masteryBonus(ctx) + (if (catalyst != null) config.catalystQualityBonus else 0)

        val quality = (config.qualityBase + config.qualityPerCoreTier * core.tier + config.qualityPerAugmentTier * augment.tier +
            affinity + mastery + ledger + roll + (if (exceptional) config.qualityExceptionalBonus else 0) -
            (if (defect) config.qualityDefectPenalty else 0) -
            (if (cmd.technique == Technique.QUENCH) config.quenchQualityPenalty else 0)).coerceIn(1, 100)
        // Quality alone stops at Epic. A Heartfire forging at the Epic floor or above may break through.
        val breakthrough = cmd.heartfire && rng.chance(breakthroughChance(ctx, cmd, quality))
        val rarity = if (breakthrough) Rarity.LEGENDARY else forgedRarity(quality, config, core.tier)

        val affixes = mutableListOf<AffixId>()
        val flaws = mutableListOf<AffixId>()
        val elementAffix = content.affixes.firstOrNull { it.kind == AffixKind.BENEFICIAL && it.element == augment.element }
        val neutralAffixes = content.affixes.filter { it.kind == AffixKind.BENEFICIAL && it.element == null }
        var affixSlots = when (rarity) { Rarity.COMMON -> 0; Rarity.UNCOMMON -> 0; Rarity.RARE -> 1; Rarity.EPIC -> 2; Rarity.LEGENDARY -> 3 } + (if (exceptional) 1 else 0)
        if (cmd.technique == Technique.ETCH) affixSlots += config.etchExtraAffixSlots
        affixSlots += profile.extraSlots + (if (cmd.heartfire && !breakthrough) config.depth.legend.missExtraSlots else 0)
        affixSlots += bellowsSlots
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
            // Anvil Lore (RECIPE_ODDS) adds to the chance, never past the cap: a signature is not a certainty.
            // A signature the journal already holds is knowledge: the exact recipe yields it most of the time.
            val known = ctx.legacy.journal.state(recipe.journalKey) == KnowledgeState.SIGNATURE_DISCOVERED
            val chance = if (known) config.depth.legend.knownSignatureChance else (config.signatureBaseChance + masteryBonus(ctx) * config.signatureChancePerMastery +
                upgradeTotal(ctx, UpgradeEffect.RECIPE_ODDS) * config.legacyTracks.recipeOddsPerLevel).coerceIn(0.0, config.signatureMaxChance)
            if (rng.chance(chance)) signature = recipe
        }
        if (signature != null) for (a in signature.grantedAffixes) if (a !in affixes) affixes += a

        val power = powerOf(content, config, family.id, core.id, quality, affixes + flaws, signature?.bonusPower ?: 0)

        val name = weaponName(content, family.id, core.id, affixes, signature?.id)
        val techniqueNote = cmd.technique?.let { ", ${it.name.lowercase()}ed" } ?: ""
        val id = ctx.newWeaponId()
        val weapon = Weapon(
            id = id, name = name, familyId = family.id, coreId = core.id, augmentId = augment.id, catalystId = catalyst?.id,
            mode = cmd.mode, risk = cmd.risk, quality = quality, rarity = rarity, power = power, element = augment.element,
            affixes = affixes, flaws = flaws, location = WeaponLocation.Storage, forgedEra = ctx.era, forgedDay = ctx.day,
            history = listOfNotNull(
                HistoryEntry(ctx.era, ctx.day, "FORGED", "Forged from ${core.name} and ${augment.name} (${cmd.risk.name.lowercase()} risk$techniqueNote)." + (if (cmd.heartfire) " Heartfire went into it." else "")),
                HistoryEntry(ctx.era, ctx.day, Legends.HISTORY_LEGEND, "The Heartfire took. It left the anvil a legend.").takeIf { breakthrough },
                signature?.let { HistoryEntry(ctx.era, ctx.day, "SIGNATURE", "${it.name}: ${it.flavor}") },
            ),
            signatureId = signature?.id, legend = LegendOrigin.HEARTFIRE.takeIf { breakthrough }, craft = WeaponRules.CRAFT,
        )
        ctx.updateWeapon(weapon)
        ctx.emit(
            EventType.WEAPON_FORGED, 1,
            "Forged $name. ${rarity.name.lowercase().replaceFirstChar { it.uppercase() }}. Quality $quality${if (flaws.isNotEmpty()) ". Flaws include ${flaws.joinToString { content.affix(it).name }}" else ""}.",
            subjects = listOf(id.value),
            data = mapOf("quality" to quality.toString(), "rarity" to rarity.name, "exceptional" to exceptional.toString(), "defect" to defect.toString(), "augmentSaved" to augmentSaved.toString(),
                "technique" to (cmd.technique?.name ?: "")) + (if (cmd.bellows) mapOf("bellows" to "true") else emptyMap()) + (if (ledger > 0) mapOf("ledger" to ledger.toString()) else emptyMap()) +
                (if (cmd.heartfire) mapOf("heartfire" to "true", "breakthrough" to breakthrough.toString()) else emptyMap()),
        )
        if (rarity == Rarity.LEGENDARY) ctx.milestone("LEGENDARY_FORGED", "Forged a legendary weapon. $name.")
        else if (rarity == Rarity.EPIC) ctx.milestone("EPIC_FORGED", "Forged an epic weapon. $name.")

        if (signature != null) {
            val first = Journal.recordSignatureDiscovered(ctx, signature)
            ctx.emit(
                EventType.SIGNATURE_DISCOVERED, 6,
                if (first) "New signature discovered. ${signature.name}. ${signature.flavor}" else "Forged ${signature.name} again.",
                subjects = listOf(id.value), data = mapOf("key" to signature.journalKey, "first" to first.toString()),
            )
        } else if (recipe != null) {
            Journal.recordSignatureClue(ctx, recipe, recipe.misses(cmd, quality))
        }
        Journal.recordExperiment(ctx, core.id, augment.id, family.id, affinity)
        Interactions.learn(ctx, weapon)
        return weapon
    }

    /**
     * What a blade is called: a signature's own name, else its core and family behind at most one affix (the first: the
     * element affix leads when there is one). The rest of its affixes are in the item sheet, not in the name.
     */
    fun weaponName(content: com.tinyblacksmith.core.content.ContentCatalog, familyId: WeaponFamilyId, coreId: MaterialId, affixes: List<AffixId>, signatureId: String? = null): String =
        signatureId?.let { SignatureCatalog.byId[it]?.name }
            ?: listOfNotNull(affixes.firstOrNull()?.let { content.affix(it).name }, content.material(coreId).name, content.family(familyId).name).joinToString(" ")

    /**
     * A blade earns its title: the first one stays for good, takes the place of the affix in its name (a signature keeps
     * its name) and is written into its history, so the ledger has a line for it.
     */
    fun entitle(ctx: ResolutionContext, weaponId: WeaponId, title: String) {
        val w = ctx.weapon(weaponId)
        if (w.title != null) return
        ctx.updateWeapon(w.copy(title = title, name = weaponName(ctx.content, w.familyId, w.coreId, emptyList(), w.signatureId)))
        ctx.addWeaponHistory(weaponId, "TITLED", "Earned the name \"$title\".")
    }

    /** A blade's power from what it is made of; the one formula for a forged blade and one that reaches the shop ready-made. */
    fun powerOf(content: com.tinyblacksmith.core.content.ContentCatalog, config: com.tinyblacksmith.core.config.BalanceConfig, familyId: WeaponFamilyId, coreId: MaterialId, quality: Int, affixes: List<AffixId>, bonus: Int = 0): Int =
        maxOf(1, content.family(familyId).basePower + config.powerPerCoreTier * content.material(coreId).tier + quality / config.powerPerQualityDivisor + affixes.sumOf { content.affix(it).power } + bonus)

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
