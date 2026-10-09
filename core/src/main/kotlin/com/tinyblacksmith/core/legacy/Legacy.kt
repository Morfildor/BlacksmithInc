package com.tinyblacksmith.core.legacy

import com.tinyblacksmith.core.config.BalanceConfig
import com.tinyblacksmith.core.content.ContentCatalog
import com.tinyblacksmith.core.content.LaunchContent
import com.tinyblacksmith.core.content.UpgradeEffect
import com.tinyblacksmith.core.engine.GameError
import com.tinyblacksmith.core.heroes.Appearance
import com.tinyblacksmith.core.model.*

/** Run end summary; carries the legacy profile as it stood when the forge fell (journal included). */
data class RunEndResult(
    val runId: RunId,
    val era: Int,
    val daysSurvived: Int,
    val cause: String,
    val basePoints: Int,
    val survivalPoints: Int,
    val discoveryPoints: Int,
    val milestonePoints: Int,
    val milestones: List<String>,
    val legends: List<LegendEntry>,
    val lineage: LineageAnchor?,
    val legacyAtEnd: LegacyProfile,
) {
    val totalPoints: Int get() = basePoints + survivalPoints + discoveryPoints + milestonePoints
}

sealed interface LegacyOutcome {
    data class Updated(val legacy: LegacyProfile) : LegacyOutcome
    data class Rejected(val error: GameError, val legacy: LegacyProfile) : LegacyOutcome
}

/**
 * What buying [level] of an upgrade changes in the next era: the typed [before] and [after] values (in the unit [text] shows)
 * and the sentence rendered from them. Chances are worded as counts ("2 forges in 100"), never as percentages.
 */
data class UpgradePreview(val upgradeId: UpgradeId, val level: Int, val before: Int, val after: Int, val text: String)

/** Baseline + milestone legacy rewards, claim-once, permanent upgrades (GDD 9). */
object Legacy {
    /** PROPOSED milestone bonus table (round 9: baseline rewards plus achievements). */
    val milestonePoints: Map<String, Int> = mapOf(
        "FIRST_SALE" to 1,
        "SIEGE_SURVIVED" to 2,
        "CHAMPION_ARMED" to 2,
        "EPIC_FORGED" to 1,
        "LEGENDARY_FORGED" to 3,
        "HERO_LEVEL_5" to 1,
        "WEAPON_FIVE_KILLS" to 2,
        "ELITE_SLAIN" to 1,
        "WARLORD_DEFEATED" to 3,
        "AMBITION_FULFILLED" to 1,
    )

    fun closeRun(state: GameState, content: ContentCatalog, config: BalanceConfig): RunEndResult {
        require(state.isEnded) { "Run has not ended" }
        val legends = state.weapons.values
            .filter { it.fame >= config.legendFameThreshold }
            .sortedByDescending { it.fame }
            .take(3)
            .map { w ->
                val owners = w.history.filter { it.kind == "SOLD" || it.kind == "COMMISSION" }.flatMap { it.subjectIds }.distinct()
                    .mapNotNull { state.heroes[HeroId(it)]?.fullName }
                LegendEntry(
                    state.era, w.name, w.title ?: "${w.name} of Era ${state.era}", w.kills, w.fame, owners,
                    familyId = w.familyId, coreId = w.coreId, augmentId = w.augmentId, quality = w.quality, power = w.power, element = w.element,
                )
            }
        val anchorHero = state.heroes.values.filter { it.fame > 0 }.maxWithOrNull(compareBy<Hero> { it.fame }.thenBy(IdOrder.numeric) { it.id.value })
        val lineage = anchorHero?.let {
            LineageAnchor(state.era, it.fullName, it.surname, it.classId, it.fame, when (it.fate) {
                HeroFate.ALIVE -> "survived the fall of the forge"
                HeroFate.RETIRED -> "retired on day ${it.retiredOnDay}"
                HeroFate.DEAD -> "died on day ${it.diedOnDay}"
            }, id = "era${state.era}-${it.id.value}", appearance = Appearance.keyOf(it))
        }
        val discovery = minOf(config.legacyDiscoveryPointCap, state.discoveriesThisRun)
        val milestoneBonus = state.milestones.sumOf { milestonePoints[it] ?: 0 }
        return RunEndResult(
            runId = state.runId, era = state.era, daysSurvived = state.day, cause = state.endCause ?: "The forge fell.",
            basePoints = config.legacyBasePoints, survivalPoints = state.day / config.legacyDaysPerPoint,
            discoveryPoints = discovery, milestonePoints = milestoneBonus, milestones = state.milestones.sorted(),
            legends = legends, lineage = lineage, legacyAtEnd = state.legacy,
        )
    }

    /** Idempotent: a second claim for the same run is rejected and the profile is unchanged. */
    fun claim(current: LegacyProfile, runEnd: RunEndResult): LegacyOutcome {
        if (runEnd.runId.value in current.claimedRunIds) return LegacyOutcome.Rejected(GameError.AlreadyClaimed(runEnd.runId), current)
        val merged = current.copy(
            journal = mergeJournal(current.journal, runEnd.legacyAtEnd.journal),
            points = current.points + runEnd.totalPoints,
            totalPointsEarned = current.totalPointsEarned + runEnd.totalPoints,
            claimedRunIds = current.claimedRunIds + runEnd.runId.value,
            legendBoard = (current.legendBoard + runEnd.legends).takeLast(20),
            lineages = (current.lineages + listOfNotNull(runEnd.lineage)).takeLast(10),
            eras = current.eras + EraSummary(runEnd.era, runEnd.daysSurvived, runEnd.totalPoints, runEnd.cause),
        )
        return LegacyOutcome.Updated(merged)
    }

    private fun mergeJournal(a: Journal, b: Journal): Journal = Journal(
        interactions = (a.interactions.keys + b.interactions.keys).associateWith { k -> maxOf(a.state(k), b.state(k)) },
        experiments = (a.experiments.keys + b.experiments.keys).associateWith { k -> maxOf(a.experiments[k] ?: 0, b.experiments[k] ?: 0) },
    )

    fun purchaseUpgrade(current: LegacyProfile, upgradeId: UpgradeId, content: ContentCatalog): LegacyOutcome {
        val def = content.upgradeById[upgradeId] ?: return LegacyOutcome.Rejected(GameError.UnknownContent(upgradeId.value), current)
        val level = current.upgradeLevel(upgradeId)
        if (level >= def.maxLevel) return LegacyOutcome.Rejected(GameError.UpgradeMaxed(upgradeId), current)
        val cost = def.costPerLevel[level]
        if (current.points < cost) return LegacyOutcome.Rejected(GameError.NotEnoughLegacyPoints(cost, current.points), current)
        return LegacyOutcome.Updated(current.copy(points = current.points - cost, upgrades = current.upgrades + (upgradeId to level + 1)))
    }

    /**
     * The concrete next-era change of buying [level] (1..maxLevel) of [upgradeId], or null for a level the track does not have.
     * Every number comes from the def and config values the engine adds up (GameEngine.upgradeTotal, BalanceConfig.legacyTracks).
     */
    fun preview(upgradeId: UpgradeId, level: Int, content: ContentCatalog = LaunchContent.catalog, config: BalanceConfig = BalanceConfig.DEFAULT): UpgradePreview? {
        val def = content.upgradeById[upgradeId] ?: return null
        if (level !in 1..def.maxLevel) return null
        val m = def.magnitudePerLevel
        val tracks = config.legacyTracks
        val prev = m * (level - 1)
        val next = m * level
        fun make(before: Int, after: Int, text: String) = UpgradePreview(upgradeId, level, before, after, "Next era: $text")
        // Chance-like tracks are worded as forges in 10 (or in 100 when the numbers do not fall on tens), never as a percentage.
        fun forgesIn(what: (before: Int, after: Int, unit: Int) -> String): UpgradePreview {
            val tens = prev % 10 == 0 && next % 10 == 0
            val scale = if (tens) 10 else 1
            return make(prev / scale, next / scale, what(prev / scale, next / scale, 100 / scale))
        }
        return when (def.effect) {
            UpgradeEffect.STARTING_ENERGY -> (config.baseDailyEnergy + prev).let { b -> make(b, b + m, "${b + m} starting energy instead of $b") }
            UpgradeEffect.STARTING_GOLD -> (config.startingGold + prev).let { b -> make(b, b + m, "${b + m} starting gold instead of $b") }
            UpgradeEffect.STARTING_INTEGRITY -> (config.startingForgeIntegrity + prev).let { b -> make(b, b + m, "the forge starts with ${b + m} integrity instead of $b") }
            UpgradeEffect.QUALITY_BONUS -> make(prev, next, "+$next quality on every forge instead of +$prev")
            UpgradeEffect.STARTING_MATERIALS -> make(prev, next, "start with $next extra of every starting material instead of $prev")
            UpgradeEffect.MATERIAL_EFFICIENCY -> forgesIn { b, a, u -> "$a in $u forges spare their augment, up from $b" }
            UpgradeEffect.EXCEPTIONAL_CHANCE -> forgesIn { b, a, u -> "$a in $u forges come out exceptional on top of the usual odds, up from $b" }
            UpgradeEffect.STARTING_REPUTATION ->
                make(prev, next, "$next starting reputation and $level ${if (level == 1) "regular" else "regulars"} with ${tracks.knownNameRegularGold} gold saved, up from $prev and ${level - 1}")
            UpgradeEffect.CATALOG_ACCESS -> {
                val before = prev * tracks.catalogStockPerLevel
                val after = next * tracks.catalogStockPerLevel
                make(before, after, "the supplier keeps $after extra of every limited material in stock each day instead of $before")
            }
            UpgradeEffect.RECIPE_ODDS -> {
                val before = Math.round(prev * tracks.recipeOddsPerLevel * 100).toInt()
                val after = Math.round(next * tracks.recipeOddsPerLevel * 100).toInt()
                make(before, after, "signature recipes land $after more times in 100, up from $before, never for certain")
            }
            UpgradeEffect.LEGACY_ARTIFACTS -> {
                val base = config.returnedLegendQualityFactor
                fun strength(steps: Int) = Math.round(100 * maxOf(base, minOf(tracks.returnedLegendQualityFactorMax, base + steps * tracks.legendQualityFactorPerLevel))).toInt()
                make(strength(prev), strength(next), "a returning legend keeps ${strength(next)} of every 100 strength points instead of ${strength(prev)}, and returns sooner")
            }
        }
    }
}
