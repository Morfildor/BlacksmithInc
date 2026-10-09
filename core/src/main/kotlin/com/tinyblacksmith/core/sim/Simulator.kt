package com.tinyblacksmith.core.sim

import com.tinyblacksmith.core.config.BalanceConfig
import com.tinyblacksmith.core.content.ContentCatalog
import com.tinyblacksmith.core.content.LaunchContent
import com.tinyblacksmith.core.content.MaterialCategory
import com.tinyblacksmith.core.content.MaterialDef
import com.tinyblacksmith.core.content.SliceContent
import com.tinyblacksmith.core.content.WeaponFamilyDef
import com.tinyblacksmith.core.engine.Command
import com.tinyblacksmith.core.engine.CommandOutcome
import com.tinyblacksmith.core.engine.GameEngine
import com.tinyblacksmith.core.model.*
import com.tinyblacksmith.core.rng.Rng
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.util.stream.Collectors
import java.util.stream.IntStream
import kotlin.math.roundToInt

/** Scripted shop policies for the headless harness (GDD 15.2). Policy randomness uses its own stream, never gameplay RNG. */
enum class Policy(
    val risk: Risk?, val priceFactor: Double, val overwork: Boolean, val synergy: Boolean = false, val invest: Boolean = false,
    /** Lists at the reputation-raised fair price: `suggestedPrice x (1 + min(reputation x reputationPricePerPoint, reputationPriceCap))`. */
    val reputed: Boolean = false,
    /** Uses the v3 shop actions: buys tools, hones the best unsold weapon, arms the town watch with surplus stock, salvages the rest. */
    val active: Boolean = false,
) {
    /** Random legal actions: random recipe, risk, price and forging effort. */
    RANDOM(null, 1.0, false),
    /** Safe forging, fair prices. */
    SAFE_FAIR(Risk.SAFE, 1.0, false),
    /** Reckless forging, fair prices. */
    RECKLESS_FAIR(Risk.RECKLESS, 1.0, false),
    /** Cheap shelves (70 % of the fair price). */
    BALANCED_CHEAP(Risk.BALANCED, 0.7, false),
    /** Expensive shelves (180 % of the fair price). */
    BALANCED_EXPENSIVE(Risk.BALANCED, 1.8, false),
    /** Best known core/augment/family affinity in the catalog, matching the dominant faction's weakness element. */
    SYNERGY(Risk.BALANCED, 1.0, false, synergy = true),
    /** Overworks every day. */
    OVERWORK(Risk.BALANCED, 1.0, true),
    /** Baseline: balanced risk, fair prices, no overworking. */
    BALANCED_FAIR(Risk.BALANCED, 1.0, false),
    /**
     * BALANCED_FAIR plus a purchasing rule: before each forge it buys the highest-tier core, then augment, whose
     * supplier price fits in `gold - reserve` ([SimulationDriver.reserve]); affinity-blind, so it is distinct from
     * SYNERGY. Intended to let the starting-gold and starting-energy upgrades register; they still measure ~0 because
     * sales are demand-bound (DECISIONS.md, "Balance review at 10,000 seeds").
     */
    BALANCED_INVEST(Risk.BALANCED, 1.0, false, invest = true),
    /** BALANCED_FAIR priced at the shop's reputation ceiling, so the reputation price bonus is visible to the sweep. */
    BALANCED_REPUTED(Risk.BALANCED, 1.0, false, reputed = true),
    /**
     * BALANCED_FAIR plus the v3 shop actions, in a fixed daily order: buy the cheapest affordable tool, hone the
     * strongest unhoned weapon in the shop, forge, list the strongest stock, give the surplus to the town watch until
     * the armory is full, then salvage what is left with any spare energy.
     */
    BALANCED_ACTIVE(Risk.BALANCED, 1.0, false, active = true),
    SAFE_CHEAP(Risk.SAFE, 0.7, false),
    RECKLESS_EXPENSIVE(Risk.RECKLESS, 1.8, false),
    /** Never forges: measures the floor. */
    PASSIVE(null, 1.0, false);

    companion object {
        /** The GDD 15.2 policy list; the CLI runs it by default. */
        val GDD_SET: List<Policy> = listOf(RANDOM, SAFE_FAIR, RECKLESS_FAIR, BALANCED_CHEAP, BALANCED_EXPENSIVE, SYNERGY, OVERWORK, BALANCED_FAIR)
    }
}

@Serializable
data class RunStats(
    val seed: Long,
    val daysSurvived: Int,
    val ended: Boolean,
    val weaponsForged: Int,
    val weaponsSold: Int,
    val goldEarned: Int,
    val finalGold: Int,
    val rarity: Map<Rarity, Int>,
    val heroDeaths: Int,
    val siegesSurvived: Int,
    val hardLockDays: Int,
    val legacyPoints: Int,
    val discoveries: Int,
    val siegesLost: Int = 0,
    val heroRetirements: Int = 0,
    /** Weapons transformed by a hidden signature recipe (GDD 4.5), counted at run end. */
    val signatureDiscoveries: Int = 0,
    /** Median over the run of the material units on hand at End Day. */
    val medianMaterialsOnHand: Int = 0,
    /** Median over the run of the gold on hand at End Day. */
    val medianGoldOnHand: Int = 0,
    /** Shop visits over the run by outcome code (`MarketVisit.reason`). */
    val visitReasons: Map<String, Int> = emptyMap(),
    /** Elite encounters won, weapons shattered on a lost expedition, warlord-led sieges fought and won (v3 affix and foe sweeps). */
    val elitesSlain: Int = 0,
    val weaponsBroken: Int = 0,
    val warlordSieges: Int = 0,
    val warlordsDefeated: Int = 0,
    /** Tool levels at run end and the day each tool was first bought (`Policy.active` only). */
    val toolLevels: Map<String, Int> = emptyMap(),
    val toolFirstDay: Map<String, Int> = emptyMap(),
    /** Weapons in the run that carry each affix or flaw, counted at run end. */
    val affixWeapons: Map<String, Int> = emptyMap(),
    /** What became of the blades fallen heroes carried (GDD 7), counted from the events that tell it. MERCHANT counts blades a merchant took; each ends RESOLD or LOST unless the run ends first. */
    val weaponFates: Map<WeaponFate, Int> = emptyMap(),
)

/**
 * Plays one full run with a policy until the forge falls or [maxDays]. Shared by tests and the CLI.
 *
 * [eventRetentionDays] is a sim-only trimming hook (not an engine rule): when > 0, `GameState.events` keeps only
 * the last N days after each End Day so multi-thousand-day soak runs stay bounded. The engine never reads past
 * events for logic (it only carries them forward), so trimming does not change outcomes.
 */
class SimulationDriver(
    val engine: GameEngine = GameEngine(),
    val maxDays: Int = engine.config.maxSimulatedDays,
    val eventRetentionDays: Int = 0,
    /** Caps forges per day (soak runs keep the weapon count bounded); null = as many as energy allows. */
    val maxForgesPerDay: Int? = null,
    /**
     * Gold the invest rule never spends on premium materials ([Policy.invest]). The cheapest fallback (iron + ember,
     * what every other policy restocks) is still bought below the reserve, so the bot forges whenever it can.
     */
    val reserve: Int = DEFAULT_RESERVE,
    /** Called after each resolved day with the new state and the End Day wall-clock nanoseconds. */
    val onDayResolved: ((GameState, Long) -> Unit)? = null,
) {
    companion object {
        /** Reserve sweep (DECISIONS.md, 10,000-seed review): 0 gold lives longest; the flag exists for sensitivity runs. */
        const val DEFAULT_RESERVE = 0
    }

    fun playRun(legacy: LegacyProfile, seed: Long, policy: Policy): Pair<RunStats, GameState> {
        var state = engine.newRun(legacy, seed)
        val policyRng = Rng(seed xor 0x5EEDL)
        var forged = 0
        var sold = 0
        var goldEarned = 0
        var hardLocks = 0
        val rarity = Rarity.entries.associateWith { 0 }.toMutableMap()
        val materialSamples = ArrayList<Int>()
        val goldSamples = ArrayList<Int>()
        val visitReasons = sortedMapOf<String, Int>()
        var elitesSlain = 0
        var weaponsBroken = 0
        var warlordSieges = 0
        var warlordsDefeated = 0
        val warlordNames = engine.content.factions.mapNotNull { it.warlordName }
        val toolFirstDay = sortedMapOf<String, Int>()
        val weaponFates = WeaponFate.entries.associateWith { 0 }.toMutableMap()
        while (!state.isEnded && state.day <= maxDays) {
            if (state.pendingBlessingOffer.isNotEmpty()) state = engine.handle(state, Command.ChooseBlessing(state.pendingBlessingOffer.first())).state()
            for (c in state.commissions.values.filter { it.status == CommissionStatus.OFFERED }) state = engine.handle(state, Command.AcceptCommission(c.id)).state()
            if (policy.active) {
                state = toolsAndHone(state)
                for (id in state.tools.keys) toolFirstDay.putIfAbsent(id, state.day)
            }
            if (policy != Policy.PASSIVE) {
                var couldForge = false
                var forgesToday = 0
                while (maxForgesPerDay == null || forgesToday < maxForgesPerDay) {
                    if (policy == Policy.RANDOM && forgesToday > 0 && policyRng.chance(0.3)) break // random effort
                    val cmd = chooseForge(state, policy, policyRng) ?: break
                    state = ensureMaterials(state, cmd)
                    when (val out = engine.handle(state, cmd)) {
                        is CommandOutcome.Accepted -> {
                            state = out.state; forged++; forgesToday++; couldForge = true
                            val w = state.weapon(out.forgedWeaponId!!)
                            rarity[w.rarity] = rarity.getValue(w.rarity) + 1
                        }
                        is CommandOutcome.Rejected -> break
                    }
                }
                if (!couldForge && state.day > 1 && state.weapons.values.none { it.isListed }) hardLocks++
            }
            var listed = state.listedWeapons().size
            for (w in if (policy.active) state.storedWeapons().sortedByDescending { it.power } else state.storedWeapons()) {
                if (listed >= engine.shelfSlots(state)) break
                val factor = when {
                    policy == Policy.RANDOM -> 0.5 + policyRng.nextDouble() * 1.5
                    policy.reputed -> 1.0 + (state.reputation * engine.config.reputationPricePerPoint).coerceIn(0.0, engine.config.reputationPriceCap)
                    else -> policy.priceFactor
                }
                val price = (engine.suggestedPrice(w) * factor).toInt()
                state = engine.handle(state, Command.ToggleShelf(w.id, true, price)).state()
                listed++
            }
            if (policy.active) state = armWatchAndSalvage(state)
            materialSamples += state.materials.values.sum()
            goldSamples += state.gold
            val started = System.nanoTime()
            val outcome = engine.handle(state, Command.EndDay(CommandId("${state.runId.value}:day${state.day}")))
            val nanos = System.nanoTime() - started
            var out = outcome.state()
            val res = out.lastResolution
            if (res != null) {
                for (v in res.visits) visitReasons[v.reason] = (visitReasons[v.reason] ?: 0) + 1
                val sales = res.events.filter { it.type == EventType.WEAPON_SOLD }
                sold += sales.size
                goldEarned += sales.sumOf { (it.data["price"]?.toInt() ?: 0) - (it.data["tradeIn"]?.toInt() ?: 0) }
                sold += res.events.count { it.type == EventType.COMMISSION_COMPLETED }
                elitesSlain += res.events.count { it.type == EventType.ELITE_SLAIN }
                weaponsBroken += res.events.count { it.type == EventType.WEAPON_BROKEN }
                // The siege line names the warlord when one leads (a lost siege capitalises it); the WARLORD_DEFEATED milestone fires once per run, the tribute line on every warlord siege won.
                warlordSieges += res.events.count { (it.type == EventType.SIEGE_WON || it.type == EventType.SIEGE_LOST) && warlordNames.any { n -> it.text.contains(n, ignoreCase = true) } }
                warlordsDefeated += res.events.count { it.type == EventType.MILESTONE && "tribute" in it.data }
                for (e in res.events) e.data[WeaponFate.KEY]?.let { f -> WeaponFate.valueOf(f).let { weaponFates[it] = weaponFates.getValue(it) + 1 } }
            }
            if (eventRetentionDays > 0) {
                val cutoff = out.day - eventRetentionDays
                out = out.copy(events = out.events.filter { it.day > cutoff })
            }
            onDayResolved?.invoke(out, nanos)
            state = out
        }
        val legacyPoints = if (state.isEnded) engine.closeRun(state).totalPoints else 0
        val stats = RunStats(
            seed = seed, daysSurvived = if (state.isEnded) state.day else state.day - 1, ended = state.isEnded, weaponsForged = forged, weaponsSold = sold,
            goldEarned = goldEarned, finalGold = state.gold, rarity = rarity, heroDeaths = state.heroes.values.count { it.fate == HeroFate.DEAD },
            siegesSurvived = state.town.siegesSurvived, hardLockDays = hardLocks, legacyPoints = legacyPoints, discoveries = state.discoveriesThisRun,
            siegesLost = state.town.siegesLost, heroRetirements = state.heroes.values.count { it.fate == HeroFate.RETIRED },
            signatureDiscoveries = state.weapons.values.count { it.signatureId != null },
            medianMaterialsOnHand = percentile(materialSamples, 0.5), medianGoldOnHand = percentile(goldSamples, 0.5),
            visitReasons = visitReasons,
            elitesSlain = elitesSlain, weaponsBroken = weaponsBroken, warlordSieges = warlordSieges, warlordsDefeated = warlordsDefeated,
            toolLevels = state.tools.toSortedMap(), toolFirstDay = toolFirstDay,
            affixWeapons = state.weapons.values.flatMap { it.affixes + it.flaws }.groupingBy { it.value }.eachCount().toSortedMap(),
            weaponFates = weaponFates,
        )
        return stats to state
    }

    /** Morning routine of [Policy.active]: one tool when affordable (cheapest first), then one hone (unhoned, or a worn trade-in) when the core is on hand. */
    private fun toolsAndHone(state: GameState): GameState {
        var s = state
        val tool = engine.content.tools.mapNotNull { t -> engine.toolCost(s, t.id)?.let { t.id to it } }.filter { it.second <= s.gold - reserve }.minByOrNull { it.second }
        if (tool != null) s = engine.handle(s, Command.BuyTool(tool.first)).state()
        val candidate = (s.listedWeapons() + s.storedWeapons())
            .filter { (!it.honed || it.condition < engine.config.wornConditionThreshold) && (s.materials[it.coreId] ?: 0) > 0 }.maxByOrNull { it.power }
        if (candidate != null && s.energy >= engine.config.honeEnergy) s = engine.handle(s, Command.Hone(candidate.id)).state()
        return s
    }

    /** Evening routine of [Policy.active]: unsold stock that did not fit on the shelf arms the watch, the rest is melted with spare energy. */
    private fun armWatchAndSalvage(state: GameState): GameState {
        var s = state
        for (w in s.storedWeapons().sortedByDescending { it.power }) {
            if (s.town.armory < engine.config.armoryMax) s = engine.handle(s, Command.DonateWeapon(w.id)).state()
            else if (s.energy >= engine.config.salvageEnergy) s = engine.handle(s, Command.Salvage(w.id)).state()
        }
        return s
    }

    private fun CommandOutcome.state(): GameState = when (this) {
        is CommandOutcome.Accepted -> state
        is CommandOutcome.Rejected -> error("Policy issued an invalid command: $error")
    }

    private fun chooseForge(state: GameState, policy: Policy, rng: Rng): Command.Forge? {
        val cfg = engine.config
        val cost = cfg.quickForgeEnergy
        val overworkRoom = cfg.maxOverworkPerDay - state.overworkToday
        val canAfford = state.energy >= cost || (policy.overwork && state.energy + overworkRoom >= cost)
        if (!canAfford) return null
        val content = engine.content
        val cores = content.materials(MaterialCategory.CORE)
        val augments = content.materials(MaterialCategory.AUGMENT)
        val synergy = if (policy.synergy) chooseSynergy(state) else null
        val family = synergy?.first ?: rng.pick(content.families)
        // RANDOM draws only among legal (obtainable) materials: launch cores cost up to 180 gold, and an unaffordable pick is not a hard-lock.
        val core = synergy?.second ?: if (policy == Policy.RANDOM) rng.pick(cores.filter { obtainable(state, it) }.ifEmpty { listOf(cores.minBy { it.price }) })
            else if (policy.invest) bestInvestment(state, cores, state.gold - reserve)
            else cores.filter { (state.materials[it.id] ?: 0) > 0 }.maxByOrNull { it.tier } ?: cores.minBy { it.price }
        val augment = synergy?.third ?: if (policy == Policy.RANDOM) rng.pick(augments.filter { obtainable(state, core, it) }.ifEmpty { listOf(augments.minBy { it.price }) })
            else if (policy.invest) bestInvestment(state, augments, state.gold - reserve - purchaseCost(state, core))
            else augments.filter { (state.materials[it.id] ?: 0) > 0 }.maxByOrNull { it.tier } ?: augments.minBy { it.price }
        val risk = policy.risk ?: rng.pick(Risk.entries)
        return Command.Forge(ForgeMode.QUICK, family.id, core.id, augment.id, null, risk)
    }

    /**
     * Synergy optimisation: among recipes the shop can obtain today, prefer an augment whose element the most
     * pressing faction is weak to, then the highest catalog affinity (core+augment and augment+family).
     * Returns null when nothing is obtainable so the generic cheap fallback applies.
     */
    private fun chooseSynergy(state: GameState): Triple<WeaponFamilyDef, MaterialDef, MaterialDef>? {
        val content = engine.content
        val target = state.factions.values.maxByOrNull { it.pressure }?.let { content.factionById[it.id]?.weakTo }
        var best: Triple<WeaponFamilyDef, MaterialDef, MaterialDef>? = null
        var bestScore = Int.MIN_VALUE
        for (core in content.materials(MaterialCategory.CORE)) {
            for (augment in content.materials(MaterialCategory.AUGMENT)) {
                if (!obtainable(state, core, augment)) continue
                val elementBonus = if (target != null && augment.element == target) 100 else 0
                for (family in content.families) {
                    val score = elementBonus + (content.coreAugmentAffinity[core.id to augment.id] ?: 0) + (content.augmentFamilyAffinity[augment.id to family.id] ?: 0)
                    if (score > bestScore) { bestScore = score; best = Triple(family, core, augment) }
                }
            }
        }
        return best
    }

    /** Highest tier that is owned, or in supplier stock and priced within [budget]; the cheapest when nothing qualifies. */
    private fun bestInvestment(state: GameState, options: List<MaterialDef>, budget: Int): MaterialDef =
        options.filter { (state.materials[it.id] ?: 0) > 0 || (state.supplierStock[it.id] ?: 1) >= 1 && purchaseCost(state, it) <= budget }
            .maxByOrNull { it.tier } ?: options.minBy { it.price }

    /** Gold the next forge spends on [m]: 0 when a unit is already owned. */
    private fun purchaseCost(state: GameState, m: MaterialDef): Int = if ((state.materials[m.id] ?: 0) > 0) 0 else engine.materialPrice(state, m.id)

    private fun obtainable(state: GameState, vararg materials: MaterialDef): Boolean {
        var cost = 0
        for (m in materials) {
            if ((state.materials[m.id] ?: 0) > 0) continue
            val stock = state.supplierStock[m.id]
            if (stock != null && stock < 1) return false
            cost += engine.materialPrice(state, m.id)
        }
        return cost <= state.gold
    }

    /** Buys missing materials when affordable; otherwise falls back to the cheapest reliably stocked ones. */
    private fun ensureMaterials(state: GameState, cmd: Command.Forge): GameState {
        var s = state
        for (m in listOf(cmd.coreId, cmd.augmentId)) {
            if ((s.materials[m] ?: 0) > 0) continue
            val out = engine.handle(s, Command.BuyMaterial(m, 1))
            if (out is CommandOutcome.Accepted) s = out.state
        }
        return s
    }
}

internal fun percentile(values: List<Int>, p: Double): Int {
    if (values.isEmpty()) return 0
    val sorted = values.sorted()
    return sorted[((sorted.size - 1) * p).toInt()]
}

/** Aggregated numbers for one policy; serialised to the `--json` report. */
@Serializable
data class PolicySummary(
    val policy: Policy,
    val label: String,
    val runs: Int,
    val daysP10: Int,
    val daysMedian: Int,
    val daysMean: Double,
    val daysP90: Int,
    val daysMax: Int,
    val ended: Int,
    val forgedPerRun: Double,
    val soldPerRun: Double,
    val sellRate: Double,
    val goldEarnedMedian: Int,
    val finalGoldMedian: Int,
    val goldOnHandMedian: Int,
    val materialsOnHandMedian: Int,
    val rarity: Map<Rarity, Double>,
    val heroDeathsPerRun: Double,
    val heroRetirementsPerRun: Double,
    val siegesSurvivedPerRun: Double,
    val siegesLostPerRun: Double,
    /** Sieges lost / sieges fought. */
    val factionWinProportion: Double,
    val hardLockDaysTotal: Int,
    val runsWithHardLock: Int,
    val legacyPointsMedian: Int,
    val discoveriesPerRun: Double,
    val signatureDiscoveriesPerRun: Double,
    /** Shop visits per run by outcome code. */
    val visitsPerRun: Map<String, Double> = emptyMap(),
    val elitesSlainPerRun: Double = 0.0,
    val weaponsBrokenPerRun: Double = 0.0,
    val warlordSiegesPerRun: Double = 0.0,
    val warlordsDefeatedPerRun: Double = 0.0,
    /** Per tool: share of runs that bought it, mean level at run end, mean day of the first purchase (over runs that bought it). */
    val toolBoughtShare: Map<String, Double> = emptyMap(),
    val toolLevelPerRun: Map<String, Double> = emptyMap(),
    val toolFirstDayMean: Map<String, Double> = emptyMap(),
    /** Weapons per run carrying each affix or flaw. */
    val affixWeaponsPerRun: Map<String, Double> = emptyMap(),
    /** Per run: what became of the blades fallen heroes carried. MERCHANT is in transit (it ends RESOLD or LOST, or the run ends first). */
    val weaponFatesPerRun: Map<WeaponFate, Double> = emptyMap(),
    /** GDD 15.2 artifact recovery: of those blades whose fate settled, the share that came back to Emberfall (forge, guildmate or merchant resale). */
    val artifactRecoveryRate: Double = 0.0,
)

data class Report(val policy: Policy, val runs: List<RunStats>, val label: String = "new account") {
    fun summary(): PolicySummary {
        val days = runs.map { it.daysSurvived }
        val rarityTotals = Rarity.entries.associateWith { r -> runs.sumOf { it.rarity[r] ?: 0 } }
        val forged = rarityTotals.values.sum().coerceAtLeast(1)
        val siegesFought = runs.sumOf { it.siegesSurvived + it.siegesLost }
        val toolIds = runs.flatMap { it.toolLevels.keys }.toSortedSet()
        val fates = WeaponFate.entries.associateWith { f -> runs.sumOf { it.weaponFates[f] ?: 0 } }
        val returned = fates.getValue(WeaponFate.RECOVERED) + fates.getValue(WeaponFate.INHERITED) + fates.getValue(WeaponFate.RESOLD)
        val settled = returned + fates.getValue(WeaponFate.SEIZED) + fates.getValue(WeaponFate.LOST)
        return PolicySummary(
            policy = policy, label = label, runs = runs.size,
            daysP10 = percentile(days, 0.1), daysMedian = percentile(days, 0.5), daysMean = days.average(), daysP90 = percentile(days, 0.9), daysMax = days.maxOrNull() ?: 0,
            ended = runs.count { it.ended },
            forgedPerRun = runs.map { it.weaponsForged }.average(), soldPerRun = runs.map { it.weaponsSold }.average(),
            sellRate = runs.sumOf { it.weaponsSold }.toDouble() / runs.sumOf { it.weaponsForged }.coerceAtLeast(1),
            goldEarnedMedian = percentile(runs.map { it.goldEarned }, 0.5), finalGoldMedian = percentile(runs.map { it.finalGold }, 0.5),
            goldOnHandMedian = percentile(runs.map { it.medianGoldOnHand }, 0.5), materialsOnHandMedian = percentile(runs.map { it.medianMaterialsOnHand }, 0.5),
            rarity = Rarity.entries.associateWith { r -> rarityTotals.getValue(r).toDouble() / forged },
            heroDeathsPerRun = runs.map { it.heroDeaths }.average(), heroRetirementsPerRun = runs.map { it.heroRetirements }.average(),
            siegesSurvivedPerRun = runs.map { it.siegesSurvived }.average(), siegesLostPerRun = runs.map { it.siegesLost }.average(),
            factionWinProportion = if (siegesFought == 0) 0.0 else runs.sumOf { it.siegesLost }.toDouble() / siegesFought,
            hardLockDaysTotal = runs.sumOf { it.hardLockDays }, runsWithHardLock = runs.count { it.hardLockDays > 0 },
            legacyPointsMedian = percentile(runs.map { it.legacyPoints }, 0.5), discoveriesPerRun = runs.map { it.discoveries }.average(),
            signatureDiscoveriesPerRun = runs.map { it.signatureDiscoveries }.average(),
            visitsPerRun = runs.flatMap { it.visitReasons.keys }.toSortedSet().associateWith { k -> runs.sumOf { it.visitReasons[k] ?: 0 }.toDouble() / runs.size },
            elitesSlainPerRun = runs.map { it.elitesSlain }.average(), weaponsBrokenPerRun = runs.map { it.weaponsBroken }.average(),
            warlordSiegesPerRun = runs.map { it.warlordSieges }.average(), warlordsDefeatedPerRun = runs.map { it.warlordsDefeated }.average(),
            toolBoughtShare = toolIds.associateWith { t -> runs.count { (it.toolLevels[t] ?: 0) > 0 }.toDouble() / runs.size },
            toolLevelPerRun = toolIds.associateWith { t -> runs.sumOf { it.toolLevels[t] ?: 0 }.toDouble() / runs.size },
            toolFirstDayMean = toolIds.associateWith { t -> runs.mapNotNull { it.toolFirstDay[t] }.average() },
            affixWeaponsPerRun = runs.flatMap { it.affixWeapons.keys }.toSortedSet().associateWith { a -> runs.sumOf { it.affixWeapons[a] ?: 0 }.toDouble() / runs.size },
            weaponFatesPerRun = fates.mapValues { it.value.toDouble() / runs.size }, artifactRecoveryRate = if (settled == 0) 0.0 else returned.toDouble() / settled,
        )
    }

    fun render(): String {
        val s = summary()
        fun pct(v: Double) = "%.0f%%".format(100.0 * v)
        fun f1(v: Double) = "%.1f".format(v)
        return buildString {
            appendLine("Policy $policy ($label) over ${s.runs} runs")
            appendLine("  survival days: p10=${s.daysP10} median=${s.daysMedian} mean=${f1(s.daysMean)} p90=${s.daysP90} max=${s.daysMax} ended=${s.ended}")
            appendLine("  forged/run: ${f1(s.forgedPerRun)}  sold/run: ${f1(s.soldPerRun)}  sell rate: ${pct(s.sellRate)}")
            appendLine("  gold earned/run: median=${s.goldEarnedMedian}  final gold median=${s.finalGoldMedian}  gold on hand median=${s.goldOnHandMedian}  materials on hand median=${s.materialsOnHandMedian}")
            appendLine("  rarity: " + Rarity.entries.joinToString(" ") { r -> "$r=${pct(s.rarity.getValue(r))}" })
            appendLine("  hero deaths/run: ${f1(s.heroDeathsPerRun)}  retirements/run: ${f1(s.heroRetirementsPerRun)}  sieges survived/run: ${f1(s.siegesSurvivedPerRun)}  lost/run: ${f1(s.siegesLostPerRun)}  faction win proportion: ${pct(s.factionWinProportion)}")
            appendLine("  hard-lock days total: ${s.hardLockDaysTotal}  runs with any hard-lock: ${s.runsWithHardLock}")
            appendLine("  shop visits/run: " + s.visitsPerRun.entries.joinToString("  ") { "${it.key}=${f1(it.value)}" })
            appendLine("  elites slain/run: ${f1(s.elitesSlainPerRun)}  weapons broken/run: ${f1(s.weaponsBrokenPerRun)}  warlord sieges/run: ${f1(s.warlordSiegesPerRun)}  warlords defeated/run: ${f1(s.warlordsDefeatedPerRun)}")
            appendLine("  weapon fates on death/run: " + listOf(WeaponFate.RECOVERED, WeaponFate.INHERITED, WeaponFate.RESOLD, WeaponFate.SEIZED, WeaponFate.LOST).joinToString("  ") { "${it.name.lowercase()}=${"%.2f".format(s.weaponFatesPerRun[it] ?: 0.0)}" } +
                "  (taken by a merchant=${"%.2f".format(s.weaponFatesPerRun[WeaponFate.MERCHANT] ?: 0.0)})  artifact recovery: ${pct(s.artifactRecoveryRate)}")
            if (s.toolLevelPerRun.isNotEmpty()) appendLine("  tools (share of runs / mean level / first day): " + s.toolLevelPerRun.keys.joinToString("  ") { t -> "$t=${pct(s.toolBoughtShare.getValue(t))}/${f1(s.toolLevelPerRun.getValue(t))}/${f1(s.toolFirstDayMean.getValue(t))}" })
            appendLine("  affix weapons/run: " + s.affixWeaponsPerRun.entries.joinToString("  ") { "${it.key}=${f1(it.value)}" })
            appendLine("  legacy points/run: median=${s.legacyPointsMedian}  discoveries/run: ${f1(s.discoveriesPerRun)}  signature discoveries/run: ${f1(s.signatureDiscoveriesPerRun)}")
        }
    }
}

/** Median is quantised to the 5-day siege rhythm, so the mean is reported too for sub-siege effects. */
@Serializable
data class UpgradeImpact(val upgradeId: String, val name: String, val level: Int, val daysMedian: Int, val deltaVsNone: Int, val daysMean: Double, val deltaMeanVsNone: Double)

/** End Day wall-clock timing over a long forced-survival run (GDD 15.3 target: p95 < 200 ms on mid-range Android). */
@Serializable
data class PerfSummary(val days: Int, val p50Ms: Double, val p95Ms: Double, val maxMs: Double, val weaponsAtEnd: Int, val heroesAtEnd: Int, val eventsAtEnd: Int)

/** One cell of the `--rarityTable` sweep: rarity shares for a core x augment pair at one risk. */
data class RarityRow(val coreId: String, val coreTier: Int, val augmentId: String, val augmentTier: Int, val risk: Risk, val forges: Int, val meanQuality: Double, val rarity: Map<Rarity, Double>) {
    fun render(): String = "%-10s(t%d) + %-15s(t%d) %-9s q=%5.1f  ".format(coreId, coreTier, augmentId, augmentTier, risk, meanQuality) +
        Rarity.entries.joinToString(" ") { r -> "%s=%3.0f%%".format(r.name.take(1), 100.0 * rarity.getValue(r)) }
}

@Serializable
data class SimReport(
    val contentVersion: Int,
    val balanceVersion: Int,
    val rulesVersion: Int,
    val runs: Int,
    val baseSeed: Long,
    val maxDays: Int,
    val overrides: Map<String, String>,
    val reserve: Int,
    val policies: List<PolicySummary>,
    val upgradeImpact: List<UpgradeImpact>,
    val perf: PerfSummary?,
    val elapsedMs: Long,
)

object Simulator {
    fun run(
        runs: Int, baseSeed: Long, policies: List<Policy>, legacy: LegacyProfile = LegacyProfile(), config: BalanceConfig = BalanceConfig.DEFAULT,
        maxDays: Int = config.maxSimulatedDays, label: String = "new account", content: ContentCatalog = LaunchContent.catalog,
        reserve: Int = SimulationDriver.DEFAULT_RESERVE,
    ): List<Report> {
        val driver = SimulationDriver(GameEngine(content, config), maxDays = maxDays, reserve = reserve)
        // Runs are independent and the engine is pure, so seeds run in parallel; results are collected in seed order.
        return policies.map { p ->
            Report(p, IntStream.range(0, runs).parallel().mapToObj { i -> driver.playRun(legacy, baseSeed + i, p).first }.collect(Collectors.toList()), label)
        }
    }

    /** A legacy account with every upgrade maxed, to measure permanent progression impact. */
    fun maxedLegacy(engine: GameEngine): LegacyProfile =
        LegacyProfile(points = 0, upgrades = engine.content.upgrades.associate { it.id to it.maxLevel })

    /** Relative impact of each permanent upgrade: [policy] (BALANCED_FAIR by default) with that single upgrade maxed vs [baselineMedianDays]. */
    fun upgradeImpact(
        runs: Int, baseSeed: Long, config: BalanceConfig, baselineMedianDays: Int, baselineMeanDays: Double, maxDays: Int = config.maxSimulatedDays,
        content: ContentCatalog = LaunchContent.catalog, policy: Policy = Policy.BALANCED_FAIR, reserve: Int = SimulationDriver.DEFAULT_RESERVE,
    ): List<UpgradeImpact> {
        val engine = GameEngine(content, config)
        return engine.content.upgrades.map { u ->
            val legacy = LegacyProfile(upgrades = mapOf(u.id to u.maxLevel))
            val s = run(runs, baseSeed, listOf(policy), legacy, config, maxDays, label = u.name, content = content, reserve = reserve).single().summary()
            UpgradeImpact(u.id.value, u.name, u.maxLevel, s.daysMedian, s.daysMedian - baselineMedianDays, s.daysMean, s.daysMean - baselineMeanDays)
        }
    }

    /**
     * Rarity distribution per core x augment pair and risk, forged through the real engine path ([Command.Forge] on a
     * fresh run with every material stocked). Forge i uses family i mod families.size so family affinity averages out;
     * energy is reset between forges. Only the gameplay CRAFTING stream is consumed; no driver RNG is involved.
     */
    fun rarityTable(
        content: ContentCatalog = LaunchContent.catalog, config: BalanceConfig = BalanceConfig.DEFAULT, forges: Int = 1000, seed: Long = 1,
        legacy: LegacyProfile = LegacyProfile(),
    ): List<RarityRow> {
        val engine = GameEngine(content, config)
        val rows = mutableListOf<RarityRow>()
        for (core in content.materials(MaterialCategory.CORE)) for (augment in content.materials(MaterialCategory.AUGMENT)) for (risk in Risk.entries) {
            var state = engine.newRun(legacy, seed).copy(materials = content.materials.associate { it.id to forges })
            val counts = Rarity.entries.associateWith { 0 }.toMutableMap()
            var qualitySum = 0L
            repeat(forges) { i ->
                val family = content.families[i % content.families.size]
                val out = engine.handle(state, Command.Forge(ForgeMode.QUICK, family.id, core.id, augment.id, null, risk))
                val accepted = out as? CommandOutcome.Accepted ?: error("rarity table forge rejected: ${(out as CommandOutcome.Rejected).error}")
                val w = accepted.state.weapon(accepted.forgedWeaponId!!)
                counts[w.rarity] = counts.getValue(w.rarity) + 1
                qualitySum += w.quality
                state = accepted.state.copy(energy = config.baseDailyEnergy, overworkToday = 0, weapons = emptyMap(), events = emptyList())
            }
            rows += RarityRow(core.id.value, core.tier, augment.id.value, augment.tier, risk, forges, qualitySum.toDouble() / forges, Rarity.entries.associateWith { counts.getValue(it).toDouble() / forges })
        }
        return rows
    }

    /** Raids never land (power 0, forge damage clamps to 0), so a run survives until the day cap. Used by soak and perf runs. */
    fun forcedSurvival(base: BalanceConfig = BalanceConfig.DEFAULT): BalanceConfig = base.copy(siegeModifier = 0.0)

    /** Times End Day over [days] days of one forced-survival run; includes JIT warm-up and grows with state size. */
    fun measureEndDay(
        days: Int = 1000, seed: Long = 1, config: BalanceConfig = forcedSurvival(), policy: Policy = Policy.BALANCED_FAIR, maxForgesPerDay: Int? = null,
        content: ContentCatalog = LaunchContent.catalog, reserve: Int = SimulationDriver.DEFAULT_RESERVE,
    ): PerfSummary {
        val samples = ArrayList<Long>(days)
        var last: GameState? = null
        val driver = SimulationDriver(GameEngine(content, config), maxDays = days, maxForgesPerDay = maxForgesPerDay, reserve = reserve) { s, nanos -> samples += nanos; last = s }
        driver.playRun(LegacyProfile(), seed, policy)
        val sorted = samples.sorted()
        fun ms(p: Double) = if (sorted.isEmpty()) 0.0 else sorted[((sorted.size - 1) * p).toInt()] / 1_000_000.0
        val end = last
        return PerfSummary(samples.size, ms(0.5), ms(0.95), (sorted.lastOrNull() ?: 0L) / 1_000_000.0, end?.weapons?.size ?: 0, end?.heroes?.size ?: 0, end?.events?.size ?: 0)
    }
}

private val reportJson = Json { prettyPrint = true }

private fun parseArgs(args: Array<String>): Map<String, String> {
    val map = mutableMapOf<String, String>()
    var i = 0
    while (i < args.size) {
        val a = args[i]
        if (a.startsWith("--")) {
            val next = args.getOrNull(i + 1)
            if (next != null && !next.startsWith("--")) { map[a] = next; i += 2 } else { map[a] = "true"; i += 1 }
        } else i += 1
    }
    return map
}

/**
 * CLI: --runs N --seed S --policy NAME|all|gdd --days CAP --json PATH --perf --content launch|slice --impactPolicy NAME
 *      --reserve N (gold the BALANCED_INVEST rule keeps back from premium purchases)
 *      [--siegeModifier X --recoveryCap N --forgeDamageBase X --forgeDamageSlope X --maxForgeDamage N]
 *      --rarityTable [N]: instead of runs, forges N weapons per core x augment x risk and prints the rarity shares.
 *      --noTool id[,id] / --toolCost id=mult[,id=mult]: catalog sweeps that drop a workshop tool or scale its costs.
 *      --noAffixEffect id[,id]|all: keeps the affix but neutralises its v3 effect (bane, elite, heal, loot, wound, shatter, self-harm).
 *      --noImpact: skips the maxed-legacy and per-upgrade runs (sweeps that only need the policy rows).
 *      --noFates: the v4 rules for a fallen hero's blade (road odds everywhere, no guild claim, no merchant); the same draws as v4.
 * Default: launch content, the GDD 15.2 policy set, maxed legacy accounts (BALANCED_FAIR and the impact policy) and
 * the per-upgrade impact sweep for the impact policy (BALANCED_FAIR by default).
 */
fun main(args: Array<String>) {
    val argMap = parseArgs(args)
    val runs = argMap["--runs"]?.toInt() ?: 1000
    val seed = argMap["--seed"]?.toLong() ?: 1L
    var content = when (val c = argMap["--content"] ?: "launch") {
        "launch" -> LaunchContent.catalog
        "slice" -> SliceContent.catalog
        else -> error("Unknown --content $c (launch|slice)")
    }
    val policies = argMap["--policy"]?.let { p -> when (p) { "all" -> Policy.entries; "gdd" -> Policy.GDD_SET; else -> listOf(Policy.valueOf(p)) } } ?: Policy.GDD_SET
    // Tuning overrides (balance sweeps only; defaults live in BalanceConfig).
    var config = BalanceConfig.DEFAULT
    val overrides = mutableMapOf<String, String>()
    // Catalog overrides (per-tool and per-affix sweeps; defaults live in LaunchContent).
    argMap["--noTool"]?.let { arg ->
        val ids = arg.split(',')
        ids.forEach { id -> requireNotNull(content.tool(id)) { "Unknown tool $id" } }
        content = content.copy(tools = content.tools.filterNot { it.id in ids }); overrides["noTool"] = arg
    }
    argMap["--toolCost"]?.let { arg ->
        val factors = arg.split(',').associate { pair -> pair.substringBefore('=') to pair.substringAfter('=').toDouble() }
        factors.keys.forEach { id -> requireNotNull(content.tool(id)) { "Unknown tool $id" } }
        content = content.copy(tools = content.tools.map { t -> factors[t.id]?.let { f -> t.copy(costPerLevel = t.costPerLevel.map { (it * f).roundToInt() }) } ?: t })
        overrides["toolCost"] = arg
    }
    argMap["--noAffixEffect"]?.let { arg ->
        val ids = if (arg == "all") content.affixes.map { it.id.value } else arg.split(',')
        ids.forEach { id -> require(AffixId(id) in content.affixById) { "Unknown affix $id" } }
        content = content.copy(affixes = content.affixes.map { a ->
            if (a.id.value in ids) a.copy(baneMultiplier = 1.0, eliteMultiplier = 1.0, healOnWin = 0, selfDamageOnWin = 0, lootChanceBonus = 0.0, damageTakenMultiplier = 1.0, breakChanceOnLoss = 0.0) else a
        })
        overrides["noAffixEffect"] = arg
    }
    content.validate().let { require(it.isEmpty()) { "Catalog overrides left the catalog invalid: $it" } }
    argMap["--siegeModifier"]?.let { config = config.copy(siegeModifier = it.toDouble()); overrides["siegeModifier"] = it }
    argMap["--recoveryCap"]?.let { config = config.copy(maxIntegrityRecoveryPerDay = it.toInt()); overrides["recoveryCap"] = it }
    argMap["--forgeDamageBase"]?.let { config = config.copy(forgeDamageBase = it.toDouble()); overrides["forgeDamageBase"] = it }
    argMap["--forgeDamageSlope"]?.let { config = config.copy(forgeDamageSlope = it.toDouble()); overrides["forgeDamageSlope"] = it }
    argMap["--maxForgeDamage"]?.let { config = config.copy(maxForgeDamagePerSiege = it.toInt()); overrides["maxForgeDamage"] = it }
    if (argMap["--noFates"] == "true") {
        config = config.copy(weaponFates = config.weaponFates.copy(
            wallsRecoveryChance = config.weaponRecoveryChance, wallsSeizureChance = config.weaponSeizureChance,
            eliteRecoveryChance = config.weaponRecoveryChance, eliteSeizureChance = config.weaponSeizureChance,
            guildInheritanceChance = 0.0, merchantBaseChance = 0.0, merchantChancePerFame = 0.0,
        ))
        overrides["noFates"] = "true"
    }
    val maxDays = argMap["--days"]?.toInt() ?: config.maxSimulatedDays
    val reserve = argMap["--reserve"]?.toInt() ?: SimulationDriver.DEFAULT_RESERVE
    val impactPolicy = argMap["--impactPolicy"]?.let { Policy.valueOf(it) } ?: Policy.BALANCED_FAIR
    val engine = GameEngine(content, config)
    println("Tiny Blacksmith headless simulator - content v${engine.content.version}, balance v${engine.config.version}, rules v${GameEngine.RULES_VERSION}")
    println("runs=$runs baseSeed=$seed days=$maxDays content=${argMap["--content"] ?: "launch"} siegeModifier=${config.siegeModifier} recoveryCap=${config.maxIntegrityRecoveryPerDay} forgeDamage=${config.forgeDamageBase}+${config.forgeDamageSlope}x(ratio-1) max ${config.maxForgeDamagePerSiege} reserve=$reserve impactPolicy=$impactPolicy threads=${Runtime.getRuntime().availableProcessors()}")
    println()
    val start = System.nanoTime()
    argMap["--rarityTable"]?.let { arg ->
        val forges = arg.toIntOrNull() ?: 1000
        println("== Rarity table ($forges forges per cell, families cycled, seed $seed) ==")
        val rows = Simulator.rarityTable(content, config, forges, seed)
        rows.forEach { println("  " + it.render()) }
        println("== Per core tier at each risk (mean over augments) ==")
        for (risk in Risk.entries) for (core in content.materials(MaterialCategory.CORE)) {
            val cell = rows.filter { it.coreId == core.id.value && it.risk == risk }
            val avg = Rarity.entries.joinToString(" ") { r -> "%s=%3.0f%%".format(r.name.take(1), 100.0 * cell.map { it.rarity.getValue(r) }.average()) }
            println("  %-9s %-10s(t%d) q=%5.1f  %s".format(risk, core.id.value, core.tier, cell.map { it.meanQuality }.average(), avg))
        }
        println("elapsed ${(System.nanoTime() - start) / 1_000_000} ms")
        return
    }
    println("== New legacy account ==")
    val reports = Simulator.run(runs, seed, policies, config = config, maxDays = maxDays, content = content, reserve = reserve)
    reports.forEach { println(it.render()) }
    var maxed: List<Report> = emptyList()
    var impact: List<UpgradeImpact> = emptyList()
    if (argMap["--noImpact"] != "true") {
        println("== Maxed legacy account (all upgrades) ==")
        maxed = Simulator.run(runs, seed, listOf(Policy.BALANCED_FAIR, impactPolicy).distinct(), legacy = Simulator.maxedLegacy(engine), config = config, maxDays = maxDays, label = "all upgrades maxed", content = content, reserve = reserve)
        maxed.forEach { println(it.render()) }
        val baseline = reports.firstOrNull { it.policy == impactPolicy }
            ?: Simulator.run(runs, seed, listOf(impactPolicy), config = config, maxDays = maxDays, content = content, reserve = reserve).single()
        val baseSummary = baseline.summary()
        println("== Upgrade impact ($impactPolicy, single upgrade maxed vs none: median ${baseSummary.daysMedian} mean ${"%.1f".format(baseSummary.daysMean)} days) ==")
        impact = Simulator.upgradeImpact(runs, seed, config, baseSummary.daysMedian, baseSummary.daysMean, maxDays, content, impactPolicy, reserve)
        impact.forEach { println("  ${it.name} (${it.upgradeId} L${it.level}): median=${it.daysMedian} (${"%+d".format(it.deltaVsNone)}) mean=${"%.1f".format(it.daysMean)} (${"%+.1f".format(it.deltaMeanVsNone)})") }
        maxed.single { it.policy == impactPolicy }.summary().let { println("  all maxed: median=${it.daysMedian} (${"%+d".format(it.daysMedian - baseSummary.daysMedian)}) mean=${"%.1f".format(it.daysMean)} (${"%+.1f".format(it.daysMean - baseSummary.daysMean)})") }
    }
    var perf: PerfSummary? = null
    if (argMap["--perf"] == "true") {
        perf = Simulator.measureEndDay(config = Simulator.forcedSurvival(config), seed = seed, content = content, reserve = reserve)
        println("== End Day timing (forced survival, ${perf.days} days, JVM) ==")
        println("  p50=${"%.2f".format(perf.p50Ms)} ms p95=${"%.2f".format(perf.p95Ms)} ms max=${"%.2f".format(perf.maxMs)} ms  state at end: weapons=${perf.weaponsAtEnd} heroes=${perf.heroesAtEnd} events=${perf.eventsAtEnd}")
    }
    val elapsedMs = (System.nanoTime() - start) / 1_000_000
    println("elapsed $elapsedMs ms")
    argMap["--json"]?.let { path ->
        val report = SimReport(
            contentVersion = engine.content.version, balanceVersion = engine.config.version, rulesVersion = GameEngine.RULES_VERSION,
            runs = runs, baseSeed = seed, maxDays = maxDays, overrides = overrides, reserve = reserve,
            policies = reports.map { it.summary() } + maxed.map { it.summary() }, upgradeImpact = impact, perf = perf, elapsedMs = elapsedMs,
        )
        File(path).writeText(reportJson.encodeToString(SimReport.serializer(), report))
        println("report written to $path")
    }
}
