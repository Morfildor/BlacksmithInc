package com.tinyblacksmith.core.sim

import com.tinyblacksmith.core.config.BalanceConfig
import com.tinyblacksmith.core.content.BlessingEffect
import com.tinyblacksmith.core.content.ContentCatalog
import com.tinyblacksmith.core.content.LaunchContent
import com.tinyblacksmith.core.content.MaterialCategory
import com.tinyblacksmith.core.content.MaterialDef
import com.tinyblacksmith.core.content.SliceContent
import com.tinyblacksmith.core.content.WeaponFamilyDef
import com.tinyblacksmith.core.engine.Command
import com.tinyblacksmith.core.engine.CommandOutcome
import com.tinyblacksmith.core.engine.GameEngine
import com.tinyblacksmith.core.engine.Technique
import com.tinyblacksmith.core.model.*
import com.tinyblacksmith.core.rng.Rng
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.util.stream.Collectors
import java.util.stream.IntStream
import kotlin.math.roundToInt
import kotlin.system.exitProcess

/** Scripted shop policies for the headless harness (GDD 15.2). Policy randomness uses its own stream, never gameplay RNG. */
enum class Policy(
    val risk: Risk?, val priceFactor: Double, val overwork: Boolean, val synergy: Boolean = false, val invest: Boolean = false,
    /** Lists at the reputation-raised fair price: `suggestedPrice x (1 + min(reputation x reputationPricePerPoint, reputationPriceCap))`. */
    val reputed: Boolean = false,
    /** Uses the v3 shop actions: buys tools, hones the best unsold weapon, arms the town watch with surplus stock, salvages the rest. */
    val active: Boolean = false,
    /** The T0.7 bots ([BotRules]); null for the classic policies, which play exactly as before. */
    val rules: BotRules? = null,
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
    PASSIVE(null, 1.0, false),

    // The T0.7 bots (Policies.kt): BALANCED_FAIR (or SYNERGY) forging plus one extra behaviour, all through real commands.
    /** Advanced Forge with the cheapest catalyst it can get; Quick when no catalyst is at hand or energy is under 4. */
    ADVANCED_SMITH(Risk.BALANCED, 1.0, false, rules = BotRules(advanced = true, catalyst = true)),
    /** Advanced Forge with the Temper technique (fewer defects, fewer exceptional pieces); Quick on the spare energy. */
    TECHNIQUE_TEMPER(Risk.BALANCED, 1.0, false, rules = BotRules(technique = Technique.TEMPER)),
    /** Advanced Forge with the Quench technique (the augment's element affix is forced, at a quality cost). */
    TECHNIQUE_QUENCH(Risk.BALANCED, 1.0, false, rules = BotRules(technique = Technique.QUENCH)),
    /** Advanced Forge with the Etch technique (one more affix slot, more defects). */
    TECHNIQUE_ETCH(Risk.BALANCED, 1.0, false, rules = BotRules(technique = Technique.ETCH)),
    /** Commission-led: accepts every commission and forges the family, element and quality it asks for. */
    REQUEST_DRIVEN(Risk.BALANCED, 1.0, false, rules = BotRules(requests = true)),
    /** In the siege warning window forges the faction's weak element and arms the watch until the forecast holds. */
    SIEGE_PREP(Risk.BALANCED, 1.0, false, rules = BotRules(siegePrep = true)),
    /** Hunts hidden signature recipes (the cheapest that can reach the quality floor), undiscovered ones first. */
    SIGNATURE_PURSUIT(Risk.BALANCED, 1.0, false, rules = BotRules(signatures = true)),
    /** Buys and repeats the best recipe made of limited-stock materials, stocking up to a day's forges each morning. */
    SCARCE_RECIPE(Risk.BALANCED, 1.0, false, rules = BotRules(scarce = true)),
    /** The tripwire bot of plan 4.3: SYNERGY forging, counter-element stock in the warning window, commissions answered, Patronage when offered. */
    EXPERT(Risk.BALANCED, 1.0, false, synergy = true, rules = BotRules(requests = true, siegePrep = true, blessing = BlessingPref.PATRONAGE)),
    /** EXPERT plus the BALANCED_ACTIVE shop actions (tools, hone, watch, salvage): a harder-playing tripwire, reported beside EXPERT. */
    EXPERT_ACTIVE(Risk.BALANCED, 1.0, false, synergy = true, active = true, rules = BotRules(requests = true, siegePrep = true, blessing = BlessingPref.PATRONAGE)),
    /** Shock: spends every coin each morning on the dearest tool, then the best materials it can pay for. */
    SPENDTHRIFT(Risk.BALANCED, 1.0, false, invest = true, rules = BotRules(spendthrift = true)),
    /** Shock: a first-timer: random recipe and risk, two forges a day, fair prices, ignores commissions. */
    NOVICE(Risk.BALANCED, 1.0, false, rules = BotRules(novice = true, acceptsCommissions = false)),
    /** Shock: BALANCED_FAIR from a run that starts with no gold (the starting kit stays). */
    BROKE_START(Risk.BALANCED, 1.0, false, rules = BotRules(startingGold = 0)),
    /** Adverse: BALANCED_FAIR that lists everything at 0 gold, to see whether reputation or loyalty run away. */
    FREE_LISTINGS(Risk.BALANCED, 0.0, false, rules = BotRules());

    companion object {
        /** The GDD 15.2 policy list; the CLI runs it by default. */
        val GDD_SET: List<Policy> = listOf(RANDOM, SAFE_FAIR, RECKLESS_FAIR, BALANCED_CHEAP, BALANCED_EXPENSIVE, SYNERGY, OVERWORK, BALANCED_FAIR)
        /** The 14 policies that `--policy all` has always meant. */
        val CLASSIC: List<Policy> = entries.filter { it.rules == null }
        /** The T0.7 bots (`--policy bots`). */
        val BOTS: List<Policy> = entries.filter { it.rules != null }
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
    /** Days with no successful forge and an empty shelf when the policy reached its listing step; yesterday's leftover shelf, not a stuck state (`RecoveryProbe` measures that). */
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
    /** Browsing visits over the run by outcome code (`MarketVisit.reason`); patrons and the collector are counted as commissions and events. */
    val visitReasons: Map<String, Int> = emptyMap(),
    /** Elite encounters won, weapons shattered on a lost expedition, warlord-led sieges fought and won (v3 affix and foe sweeps). */
    val elitesSlain: Int = 0,
    val weaponsBroken: Int = 0,
    val warlordSieges: Int = 0,
    val warlordsDefeated: Int = 0,
    /** Tool levels at run end and the day each tool was first bought (`Policy.active` only). */
    val toolLevels: Map<String, Int> = emptyMap(),
    val toolFirstDay: Map<String, Int> = emptyMap(),
    /** Weapons forged in the run that carry each affix or flaw, counted as they are forged (pruning does not touch it). */
    val affixWeapons: Map<String, Int> = emptyMap(),
    /** Hero-days (heroes alive at End Day, summed over the run) by what the hero did that day (`SimulationDriver.activityOf`). */
    val activityDays: Map<String, Int> = emptyMap(),
    /** Hero level-ups and mentoring moments at a guild hall over the run; guilds standing at run end. */
    val heroLevelUps: Int = 0,
    val mentorings: Int = 0,
    val guilds: Int = 0,
    /** What became of the blades fallen heroes carried (GDD 7), counted from the events that tell it. MERCHANT counts blades a merchant took; each ends RESOLD or LOST unless the run ends first. */
    val weaponFates: Map<WeaponFate, Int> = emptyMap(),
    /**
     * Second yardsticks for upgrades that buy something other than days (DECISIONS.md "Balance v5"): the first siege
     * as town defense and outcome, the work done by that day, the day a weapon with a tier-4+ core first sold
     * (null = never), limited-stock supplier units bought and Legend Board blades returned.
     */
    val firstSiegeDefense: Int = 0,
    val firstSiegeHeld: Boolean = false,
    val forgedByFirstSiege: Int = 0,
    val soldByFirstSiege: Int = 0,
    val toolsByFirstSiege: Int = 0,
    val firstPremiumSaleDay: Int? = null,
    val rareMaterialsBought: Int = 0,
    val legendsReturned: Int = 0,
    /** Customer and identity counters (`--customers`, [CustomerCollector]); null unless the driver collects them. */
    val customers: RunCustomers? = null,
    /** Guild Patronage: times the blessing was taken, purchases a guild paid toward and the gold the guilds paid. */
    val patronageTaken: Int = 0,
    val stipendSales: Int = 0,
    val stipendGold: Int = 0,
    /** Sieges lost as a rout (`weaponFates.wallsRoutRatio`) and champions who died on the walls in them. */
    val routs: Int = 0,
    val wallDeaths: Int = 0,
    /** The clue ladder: rumours told, rungs earned (rumours, fragments and misses at a base recipe) and signatures found for the first time on the account. */
    val rumours: Int = 0,
    val clueRungs: Int = 0,
    val signatureFirsts: Int = 0,
    /** Returned legends ([legendsReturned]): their power as they came back (summed), how many the smith woke with a hone, how many left the shop in a hero's hands, and their power when they did (summed). */
    val legendReturnPower: Int = 0,
    val legendsWoken: Int = 0,
    val legendsHandedOver: Int = 0,
    val legendHandedPower: Int = 0,
    /** What a T0.7 bot did (forge modes, techniques, requests, signature tries, rejected commands, ...); null for the classic policies. */
    val bot: BotRunStats? = null,
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
    /** Observes every End Day for the customer metrics ([RunStats.customers]); read-only, so the run is the same with or without. */
    val customerMetrics: Boolean = false,
    /** The blessing taken when the town offers a choice (`--blessing`); null = the policy's own habit (the first offered, except EXPERT). */
    val blessing: BlessingPref? = null,
    /** Called after each resolved day with the new state and the End Day wall-clock nanoseconds. */
    val onDayResolved: ((GameState, Long) -> Unit)? = null,
) {
    companion object {
        /** Reserve sweep (DECISIONS.md, 10,000-seed review): 0 gold lives longest; the flag exists for sensitivity runs. */
        const val DEFAULT_RESERVE = 0
    }

    /** [from] continues a run another driver began (the transition sweep: a town raised under other numbers); null starts a new one. */
    fun playRun(legacy: LegacyProfile, seed: Long, policy: Policy, from: GameState? = null): Pair<RunStats, GameState> {
        var state = from ?: engine.newRun(legacy, seed)
        val policyRng = Rng(seed xor 0x5EEDL)
        val bots = policy.rules?.let { BotPlay(this, policy, it, policyRng) }
        if (bots != null) state = bots.start(state)
        var forged = 0
        var sold = 0
        var goldEarned = 0
        // Not a stuck state (G10): days after day 1 on which the policy forged nothing and nothing was on the shelf at that point, i.e. before the day's own listing step, so yesterday's leftover shelf. Stuck, drought and streaks are `RecoveryProbe`.
        var hardLocks = 0
        val rarity = Rarity.entries.associateWith { 0 }.toMutableMap()
        val affixWeapons = sortedMapOf<String, Int>()
        val materialSamples = ArrayList<Int>()
        val goldSamples = ArrayList<Int>()
        val visitReasons = sortedMapOf<String, Int>()
        var elitesSlain = 0
        var weaponsBroken = 0
        var warlordSieges = 0
        var warlordsDefeated = 0
        val warlordNames = engine.content.factions.mapNotNull { it.warlordName }
        val toolFirstDay = sortedMapOf<String, Int>()
        val activityDays = sortedMapOf<String, Int>()
        var heroLevelUps = 0
        var mentorings = 0
        val weaponFates = WeaponFate.entries.associateWith { 0 }.toMutableMap()
        val firstSiegeDay = engine.config.siegeInterval
        var firstSiegeDefense = 0
        var firstSiegeHeld = false
        var forgedByFirstSiege = 0
        var soldByFirstSiege = 0
        var toolsByFirstSiege = 0
        var firstPremiumSaleDay: Int? = null
        var rareMaterialsBought = 0
        var legendsReturned = 0
        var patronageTaken = 0
        var stipendSales = 0
        var stipendGold = 0
        var routs = 0
        var wallDeaths = 0
        var rumours = 0
        var clueRungs = 0
        var signatureFirsts = 0
        var legendReturnPower = 0
        var legendsWoken = 0
        var legendsHandedOver = 0
        var legendHandedPower = 0
        val collector = if (customerMetrics) CustomerCollector(engine) else null
        while (!state.isEnded && state.day <= maxDays) {
            collector?.morning(state)
            if (state.pendingBlessingOffer.isNotEmpty()) {
                val pick = (blessing ?: policy.rules?.blessing)?.choose(state.pendingBlessingOffer, engine.content) ?: state.pendingBlessingOffer.first()
                if (engine.content.blessing(pick).effect == BlessingEffect.GUILD_PATRONAGE) patronageTaken++
                state = engine.handle(state, Command.ChooseBlessing(pick)).state()
            }
            if (policy.rules?.acceptsCommissions != false) for (c in state.commissions.values.filter { it.status == CommissionStatus.OFFERED }) state = engine.handle(state, Command.AcceptCommission(c.id)).state()
            if (policy.active) {
                state = toolsAndHone(state)
                for (id in state.tools.keys) toolFirstDay.putIfAbsent(id, state.day)
            }
            if (bots != null) {
                state = bots.morning(state)
                for (id in state.tools.keys) toolFirstDay.putIfAbsent(id, state.day)
            }
            if (policy != Policy.PASSIVE) {
                var couldForge = false
                var forgesToday = 0
                while (maxForgesPerDay == null || forgesToday < maxForgesPerDay) {
                    if (policy == Policy.RANDOM && forgesToday > 0 && policyRng.chance(0.3)) break // random effort
                    val cmd = (if (bots != null) bots.forge(state) else chooseForge(state, policy, policyRng)) ?: break
                    val rareInStock = state.supplierStock.values.sum()
                    state = ensureMaterials(state, cmd)
                    rareMaterialsBought += rareInStock - state.supplierStock.values.sum()
                    when (val out = engine.handle(state, cmd)) {
                        is CommandOutcome.Accepted -> {
                            state = out.state; forged++; forgesToday++; couldForge = true
                            if (state.day <= firstSiegeDay) forgedByFirstSiege++
                            val w = state.weapon(out.forgedWeaponId!!)
                            rarity[w.rarity] = rarity.getValue(w.rarity) + 1
                            for (a in w.affixes + w.flaws) affixWeapons[a.value] = (affixWeapons[a.value] ?: 0) + 1
                            bots?.noteForge(cmd, w)
                        }
                        is CommandOutcome.Rejected -> { bots?.noteRejected(); break }
                    }
                }
                if (!couldForge && state.day > 1 && state.weapons.values.none { it.isListed }) hardLocks++
            }
            if (bots != null) state = bots.reserve(state)
            var listed = state.listedWeapons().size
            for (w in bots?.stockToList(state) ?: if (policy.active) state.storedWeapons().sortedByDescending { it.power } else state.storedWeapons()) {
                if (listed >= engine.shelfSlots(state)) break
                val factor = when {
                    policy == Policy.RANDOM -> 0.5 + policyRng.nextDouble() * 1.5
                    policy.reputed -> 1.0 + (state.reputation * engine.config.reputationPricePerPoint).coerceIn(0.0, engine.config.reputationPriceCap)
                    else -> policy.priceFactor
                }
                val price = bots?.wantPrice(state, w) ?: (engine.suggestedPrice(w) * factor).toInt()
                state = engine.handle(state, Command.ToggleShelf(w.id, true, price)).state()
                listed++
            }
            if (bots != null) state = bots.evening(state)
            if (policy.active) state = armWatchAndSalvage(state)
            materialSamples += state.materials.values.sum()
            goldSamples += state.gold
            if (state.day == firstSiegeDay) toolsByFirstSiege = state.tools.values.sum()
            collector?.beforeEndDay(state)
            val started = System.nanoTime()
            val outcome = engine.handle(state, Command.EndDay(CommandId("${state.runId.value}:day${state.day}")))
            val nanos = System.nanoTime() - started
            var out = outcome.state()
            val res = out.lastResolution
            if (res != null) {
                collector?.afterEndDay(state, out, res)
                bots?.observe(state, res)
                for (v in res.browsers) visitReasons[v.reason.name] = (visitReasons[v.reason.name] ?: 0) + 1
                val sales = res.events.filter { it.type == EventType.WEAPON_SOLD }
                sold += sales.size
                goldEarned += res.ledger?.income?.values?.sum() ?: 0  // every kind: sales, sale bonus, commissions, the collector, tribute
                sold += res.events.count { it.type == EventType.COMMISSION_COMPLETED }
                elitesSlain += res.events.count { it.type == EventType.ELITE_SLAIN }
                weaponsBroken += res.events.count { it.type == EventType.WEAPON_BROKEN }
                // The siege line names the warlord when one leads (a lost siege capitalises it); the WARLORD_DEFEATED milestone fires once per run, the tribute line on every warlord siege won.
                warlordSieges += res.events.count { (it.type == EventType.SIEGE_WON || it.type == EventType.SIEGE_LOST) && warlordNames.any { n -> it.text.contains(n, ignoreCase = true) } }
                warlordsDefeated += res.events.count { it.type == EventType.MILESTONE && "tribute" in it.data }
                for (h in state.aliveHeroes()) activityOf(h, res.events).let { activityDays[it] = (activityDays[it] ?: 0) + 1 }
                heroLevelUps += res.events.count { it.type == EventType.HERO_LEVELED }
                mentorings += res.events.count { it.type == EventType.GUILD_MENTORED }
                for (e in res.events) e.data[WeaponFate.KEY]?.let { f -> WeaponFate.valueOf(f).let { weaponFates[it] = weaponFates.getValue(it) + 1 } }
                val handedOver = res.events.filter { it.type == EventType.WEAPON_SOLD || it.type == EventType.COMMISSION_COMPLETED }
                if (res.day <= firstSiegeDay) soldByFirstSiege += handedOver.size
                // Both records carry the weapon as their second subject.
                if (firstPremiumSaleDay == null && handedOver.any { e -> e.subjectIds.getOrNull(1)?.let { out.weapons[WeaponId(it)] }?.let { engine.content.material(it.coreId).tier >= 4 } == true }) firstPremiumSaleDay = res.day
                if (res.day == firstSiegeDay) res.events.firstOrNull { it.type == EventType.SIEGE_WON || it.type == EventType.SIEGE_LOST }?.let {
                    firstSiegeDefense = it.data["townDefense"]?.toInt() ?: 0
                    firstSiegeHeld = it.type == EventType.SIEGE_WON
                }
                legendsReturned += res.events.count { it.type == EventType.ARTIFACT_RETURNED }
                legendReturnPower += res.events.filter { it.type == EventType.ARTIFACT_RETURNED }.sumOf { it.data["power"]?.toIntOrNull() ?: 0 }
                for (w in handedOver.mapNotNull { e -> e.subjectIds.getOrNull(1)?.let { out.weapons[WeaponId(it)] } }) if (w.legendKey != null) { legendsHandedOver++; legendHandedPower += w.power }
                legendsWoken += res.events.count { it.type == EventType.WEAPON_HONED && "woke" in it.data }   // counted by day: the log forgets ordinary records after a month
                stipendSales += res.visits.count { (it.sale?.stipend ?: 0) > 0 }
                stipendGold += res.ledger?.income?.get(IncomeKind.STIPEND) ?: 0
                routs += res.events.count { it.type == EventType.SIEGE_LOST && it.data["rout"] == "true" }
                wallDeaths += res.field.count { it.outcome == FieldOutcome.FELL_AT_THE_WALL }
                rumours += res.events.count { it.type == EventType.DISCOVERY && it.data["rumour"] == "true" }
                clueRungs += res.events.count { it.type == EventType.DISCOVERY && "rung" in it.data } + res.events.count { it.type == EventType.WORLD_EVENT && it.data["event"] == "weapon_fragment" }
                signatureFirsts += res.events.count { it.type == EventType.SIGNATURE_DISCOVERED && it.data["first"] == "true" }
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
            affixWeapons = affixWeapons,
            activityDays = activityDays, heroLevelUps = heroLevelUps, mentorings = mentorings, guilds = state.town.guilds.size,
            weaponFates = weaponFates,
            firstSiegeDefense = firstSiegeDefense, firstSiegeHeld = firstSiegeHeld, forgedByFirstSiege = forgedByFirstSiege, soldByFirstSiege = soldByFirstSiege,
            toolsByFirstSiege = toolsByFirstSiege, firstPremiumSaleDay = firstPremiumSaleDay, rareMaterialsBought = rareMaterialsBought + (bots?.stockpiled ?: 0), legendsReturned = legendsReturned,
            customers = collector?.finish(state), patronageTaken = patronageTaken, stipendSales = stipendSales, stipendGold = stipendGold, routs = routs, wallDeaths = wallDeaths,
            rumours = rumours, clueRungs = clueRungs, signatureFirsts = signatureFirsts,
            legendReturnPower = legendReturnPower, legendsWoken = legendsWoken,
            legendsHandedOver = legendsHandedOver, legendHandedPower = legendHandedPower, bot = bots?.finish(state),
        )
        return stats to state
    }

    /**
     * What a hero who was alive this morning did today, read from the day's records: AMBITION_<ambition> (a slayer's
     * hunt is an ambition day, not an expedition), GUILD, PATROL, REST (REST_WOUNDED when the hero was below the wounded
     * threshold and had no choice), else EXPEDITION (won, lost or fallen). NONE would be a living hero who did not act.
     */
    private fun activityOf(hero: Hero, events: List<EventRecord>): String {
        val own = events.filter { e -> e.subjectIds.firstOrNull { it.startsWith("h") } == hero.id.value }
        fun has(type: EventType) = own.any { it.type == type }
        return when {
            has(EventType.AMBITION_PURSUED) -> "AMBITION_" + own.first { it.type == EventType.AMBITION_PURSUED }.data["ambition"]
            has(EventType.GUILD_TRAINED) -> "GUILD"
            has(EventType.HERO_PATROLLED) -> "PATROL"
            has(EventType.HERO_RESTED) -> if (hero.health < engine.config.heroWoundedThreshold) "REST_WOUNDED" else "REST"
            has(EventType.EXPEDITION_WON) || has(EventType.ELITE_SLAIN) || has(EventType.EXPEDITION_LOST) || has(EventType.HERO_DIED) -> "EXPEDITION"
            else -> "NONE"
        }
    }

    /** Morning routine of [Policy.active]: one tool when affordable (cheapest first), then one hone (unhoned, or a worn trade-in) when the core is on hand. */
    private fun toolsAndHone(state: GameState): GameState {
        var s = state
        val tool = engine.content.tools.mapNotNull { t -> engine.toolCost(s, t.id)?.let { t.id to it } }.filter { it.second <= s.gold - reserve }.minByOrNull { it.second }
        if (tool != null) s = engine.handle(s, Command.BuyTool(tool.first)).state()
        // A returned legend sleeps until it is honed: it comes first, and its core is bought when there is none on hand.
        (s.listedWeapons() + s.storedWeapons()).firstOrNull { it.dormantAffixes.isNotEmpty() && (s.materials[it.coreId] ?: 0) == 0 }?.let { w ->
            if (s.energy >= engine.config.honeEnergy && engine.materialPrice(s, w.coreId) <= s.gold - reserve && (s.supplierStock[w.coreId] ?: 1) > 0) s = engine.handle(s, Command.BuyMaterial(w.coreId)).state()
        }
        val candidate = (s.listedWeapons() + s.storedWeapons())
            .filter { (!it.honed || it.condition < engine.config.wornConditionThreshold) && (s.materials[it.coreId] ?: 0) > 0 }
            .maxWithOrNull(compareBy<Weapon> { it.dormantAffixes.isNotEmpty() }.thenBy { it.power })
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

    internal fun chooseForge(state: GameState, policy: Policy, rng: Rng): Command.Forge? {
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
    internal fun bestInvestment(state: GameState, options: List<MaterialDef>, budget: Int): MaterialDef =
        options.filter { (state.materials[it.id] ?: 0) > 0 || (state.supplierStock[it.id] ?: 1) >= 1 && purchaseCost(state, it) <= budget }
            .maxByOrNull { it.tier } ?: options.minBy { it.price }

    /** Gold the next forge spends on [m]: 0 when a unit is already owned. */
    internal fun purchaseCost(state: GameState, m: MaterialDef): Int = if ((state.materials[m.id] ?: 0) > 0) 0 else engine.materialPrice(state, m.id)

    internal fun obtainable(state: GameState, vararg materials: MaterialDef): Boolean {
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
        for (m in listOfNotNull(cmd.coreId, cmd.augmentId, cmd.catalystId)) {
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
    /** Weapons forged per run carrying each affix or flaw. */
    val affixWeaponsPerRun: Map<String, Double> = emptyMap(),
    /** Hero-days per run and the share of them spent on each activity (`RunStats.activityDays`). */
    val heroDaysPerRun: Double = 0.0,
    val activityShare: Map<String, Double> = emptyMap(),
    val heroLevelUpsPerRun: Double = 0.0,
    val mentoringsPerRun: Double = 0.0,
    /** Guilds standing at run end per run, and the share of runs that end with at least one. */
    val guildsPerRun: Double = 0.0,
    val guildRunShare: Double = 0.0,
    /** Per run: what became of the blades fallen heroes carried. MERCHANT is in transit (it ends RESOLD or LOST, or the run ends first). */
    val weaponFatesPerRun: Map<WeaponFate, Double> = emptyMap(),
    /** GDD 15.2 artifact recovery: of those blades whose fate settled, the share that came back to Emberfall (forge, guildmate or merchant resale). */
    val artifactRecoveryRate: Double = 0.0,
    /** Customer and identity metrics (`--customers`); absent from the report otherwise. */
    val customers: CustomerSummary? = null,
    /** Guild Patronage per run: times taken, purchases a guild paid toward, gold the guilds paid, and that gold as a share of all gold earned. */
    val patronageTakenPerRun: Double = 0.0,
    val stipendSalesPerRun: Double = 0.0,
    val stipendGoldPerRun: Double = 0.0,
    val stipendShareOfIncome: Double = 0.0,
    /** Per run: sieges lost as a rout, and champions who died on the walls in them; the share of runs in which one did. */
    val routsPerRun: Double = 0.0,
    val wallDeathsPerRun: Double = 0.0,
    val wallDeathRunShare: Double = 0.0,
    /** The clue ladder per run: rumours, rungs earned, signatures first found; and the share of runs that found one. */
    val rumoursPerRun: Double = 0.0,
    val clueRungsPerRun: Double = 0.0,
    val signatureFirstsPerRun: Double = 0.0,
    val signatureFirstRunShare: Double = 0.0,
    /** Returned legends a run; of those returned: mean power as they came back, share woken by a hone, share handed to a hero, and their mean power then. */
    val legendsReturnedPerRun: Double = 0.0,
    val legendReturnPowerMean: Double = 0.0,
    val legendsWokenShare: Double = 0.0,
    val legendsHandedOverShare: Double = 0.0,
    val legendHandedPowerMean: Double = 0.0,
    /** What the T0.7 bots did, per run (`BotRunStats`); absent for the classic policies. */
    val bot: BotSummary? = null,
)

data class Report(val policy: Policy, val runs: List<RunStats>, val label: String = "new account") {
    fun summary(): PolicySummary {
        val days = runs.map { it.daysSurvived }
        val rarityTotals = Rarity.entries.associateWith { r -> runs.sumOf { it.rarity[r] ?: 0 } }
        val forged = rarityTotals.values.sum().coerceAtLeast(1)
        val siegesFought = runs.sumOf { it.siegesSurvived + it.siegesLost }
        val toolIds = runs.flatMap { it.toolLevels.keys }.toSortedSet()
        val heroDays = runs.sumOf { it.activityDays.values.sum() }
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
            heroDaysPerRun = heroDays.toDouble() / runs.size,
            activityShare = runs.flatMap { it.activityDays.keys }.toSortedSet().associateWith { k -> runs.sumOf { it.activityDays[k] ?: 0 }.toDouble() / heroDays.coerceAtLeast(1) },
            heroLevelUpsPerRun = runs.map { it.heroLevelUps }.average(), mentoringsPerRun = runs.map { it.mentorings }.average(),
            guildsPerRun = runs.map { it.guilds }.average(), guildRunShare = runs.count { it.guilds > 0 }.toDouble() / runs.size,
            weaponFatesPerRun = fates.mapValues { it.value.toDouble() / runs.size }, artifactRecoveryRate = if (settled == 0) 0.0 else returned.toDouble() / settled,
            customers = CustomerSummary.of(runs),
            patronageTakenPerRun = runs.map { it.patronageTaken }.average(), stipendSalesPerRun = runs.map { it.stipendSales }.average(), stipendGoldPerRun = runs.map { it.stipendGold }.average(),
            stipendShareOfIncome = runs.sumOf { it.stipendGold }.toDouble() / runs.sumOf { it.goldEarned }.coerceAtLeast(1),
            routsPerRun = runs.map { it.routs }.average(), wallDeathsPerRun = runs.map { it.wallDeaths }.average(), wallDeathRunShare = runs.count { it.wallDeaths > 0 }.toDouble() / runs.size,
            rumoursPerRun = runs.map { it.rumours }.average(), clueRungsPerRun = runs.map { it.clueRungs }.average(),
            signatureFirstsPerRun = runs.map { it.signatureFirsts }.average(), signatureFirstRunShare = runs.count { it.signatureFirsts > 0 }.toDouble() / runs.size,
            legendsReturnedPerRun = runs.map { it.legendsReturned }.average(),
            legendReturnPowerMean = runs.sumOf { it.legendReturnPower }.toDouble() / runs.sumOf { it.legendsReturned }.coerceAtLeast(1),
            legendsWokenShare = runs.sumOf { it.legendsWoken }.toDouble() / runs.sumOf { it.legendsReturned }.coerceAtLeast(1),
            legendsHandedOverShare = runs.sumOf { it.legendsHandedOver }.toDouble() / runs.sumOf { it.legendsReturned }.coerceAtLeast(1),
            legendHandedPowerMean = runs.sumOf { it.legendHandedPower }.toDouble() / runs.sumOf { it.legendsHandedOver }.coerceAtLeast(1),
            bot = BotSummary.of(runs),
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
            if (s.legendsReturnedPerRun > 0) appendLine("  returned legends/run: ${"%.3f".format(s.legendsReturnedPerRun)}  power as returned=${f1(s.legendReturnPowerMean)}  woken by a hone=${pct(s.legendsWokenShare)}  handed to a hero=${pct(s.legendsHandedOverShare)} (power then ${f1(s.legendHandedPowerMean)})")
            appendLine("  shop visits/run: " + s.visitsPerRun.entries.joinToString("  ") { "${it.key}=${f1(it.value)}" })
            appendLine("  elites slain/run: ${f1(s.elitesSlainPerRun)}  weapons broken/run: ${f1(s.weaponsBrokenPerRun)}  warlord sieges/run: ${f1(s.warlordSiegesPerRun)}  warlords defeated/run: ${f1(s.warlordsDefeatedPerRun)}")
            appendLine("  weapon fates on death/run: " + listOf(WeaponFate.RECOVERED, WeaponFate.INHERITED, WeaponFate.RESOLD, WeaponFate.SEIZED, WeaponFate.LOST).joinToString("  ") { "${it.name.lowercase()}=${"%.2f".format(s.weaponFatesPerRun[it] ?: 0.0)}" } +
                "  (taken by a merchant=${"%.2f".format(s.weaponFatesPerRun[WeaponFate.MERCHANT] ?: 0.0)})  artifact recovery: ${pct(s.artifactRecoveryRate)}")
            if (s.toolLevelPerRun.isNotEmpty()) appendLine("  tools (share of runs / mean level / first day): " + s.toolLevelPerRun.keys.joinToString("  ") { t -> "$t=${pct(s.toolBoughtShare.getValue(t))}/${f1(s.toolLevelPerRun.getValue(t))}/${f1(s.toolFirstDayMean.getValue(t))}" })
            appendLine("  affix weapons/run: " + s.affixWeaponsPerRun.entries.joinToString("  ") { "${it.key}=${f1(it.value)}" })
            appendLine("  hero-days/run: ${f1(s.heroDaysPerRun)}  activity shares: " + s.activityShare.entries.joinToString("  ") { "${it.key}=${"%.1f%%".format(100.0 * it.value)}" })
            appendLine("  level-ups/run: ${f1(s.heroLevelUpsPerRun)}  mentorings/run: ${f1(s.mentoringsPerRun)}  guilds/run: ${f1(s.guildsPerRun)}  runs with a guild: ${pct(s.guildRunShare)}")
            appendLine("  wall: routs/run=${"%.2f".format(s.routsPerRun)}  champions fallen/run=${"%.3f".format(s.wallDeathsPerRun)}  runs with a champion fallen=${"%.1f%%".format(100.0 * s.wallDeathRunShare)}")
            appendLine("  guild patronage/run: taken=${"%.2f".format(s.patronageTakenPerRun)}  purchases with a stipend=${"%.2f".format(s.stipendSalesPerRun)}  stipend gold=${f1(s.stipendGoldPerRun)} (${"%.1f%%".format(100.0 * s.stipendShareOfIncome)} of gold earned)")
            appendLine("  legacy points/run: median=${s.legacyPointsMedian}  discoveries/run: ${f1(s.discoveriesPerRun)}  signature discoveries/run: ${f1(s.signatureDiscoveriesPerRun)}  rumours/run: ${"%.2f".format(s.rumoursPerRun)}  clue rungs/run: ${"%.2f".format(s.clueRungsPerRun)}  signatures first found/run: ${"%.3f".format(s.signatureFirstsPerRun)} (runs with one ${"%.1f".format(100 * s.signatureFirstRunShare)}%)")
            s.bot?.let { append(it.render()) }
            s.customers?.let { append(it.render()) }
        }
    }

    fun yardsticks(): Yardsticks {
        val premiumDays = runs.mapNotNull { it.firstPremiumSaleDay }
        fun mean(f: (RunStats) -> Int) = runs.sumOf { f(it) }.toDouble() / runs.size
        return Yardsticks(
            firstSiegeDefense = mean { it.firstSiegeDefense }, firstSiegeHeld = runs.count { it.firstSiegeHeld }.toDouble() / runs.size,
            forgedByFirstSiege = mean { it.forgedByFirstSiege }, soldByFirstSiege = mean { it.soldByFirstSiege }, toolsByFirstSiege = mean { it.toolsByFirstSiege },
            premiumSaleShare = premiumDays.size.toDouble() / runs.size, premiumSaleDay = if (premiumDays.isEmpty()) 0.0 else premiumDays.average(),
            rareMaterialsBought = mean { it.rareMaterialsBought }, signatures = mean { it.signatureDiscoveries }, legendsReturned = mean { it.legendsReturned },
            forged = mean { it.weaponsForged }, sold = mean { it.weaponsSold }, legacyPoints = mean { it.legacyPoints },
        )
    }
}

/**
 * What an upgrade buys besides days, as means per run (DECISIONS.md "Balance v5"): town defense at the first siege and
 * the share of runs that held it, weapons forged and sold and tool levels owned by that day, premium (tier-4+ core)
 * sales, limited-stock supplier units bought, signature weapons, Legend Board blades returned, legacy points.
 */
@Serializable
data class Yardsticks(
    val firstSiegeDefense: Double, val firstSiegeHeld: Double, val forgedByFirstSiege: Double, val soldByFirstSiege: Double, val toolsByFirstSiege: Double,
    /** Share of runs that sold a premium weapon and the mean day of the first such sale among them. */
    val premiumSaleShare: Double, val premiumSaleDay: Double,
    val rareMaterialsBought: Double, val signatures: Double, val legendsReturned: Double, val forged: Double, val sold: Double, val legacyPoints: Double,
) {
    fun render(name: String): String = "  %-20s %7.1f %4.0f%% %7.1f %7.1f %7.1f   day %4.1f in %3.0f%% %7.1f %7.2f %7.2f %7.1f %7.1f %7.1f".format(
        name, firstSiegeDefense, 100 * firstSiegeHeld, forgedByFirstSiege, soldByFirstSiege, toolsByFirstSiege, premiumSaleDay, 100 * premiumSaleShare,
        rareMaterialsBought, signatures, legendsReturned, forged, sold, legacyPoints,
    )

    companion object {
        val HEADER: String = "  %-20s %7s %5s %7s %7s %7s   %16s %7s %7s %7s %7s %7s %7s".format(
            "upgrade (maxed)", "defense", "held", "forged", "sold", "tools", "premium sale", "rare", "signat.", "legends", "forged", "sold", "points",
        )
    }
}

/** Median is quantised to the 5-day siege rhythm, so the mean is reported too for sub-siege effects. */
@Serializable
data class UpgradeImpact(
    val upgradeId: String, val name: String, val level: Int, val daysMedian: Int, val deltaVsNone: Int, val daysMean: Double, val deltaMeanVsNone: Double,
    val yardsticks: Yardsticks? = null,
)

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
    /** `--eras N`: one account played through N eras per policy ([EraPlaySummary]); then `policies` and `upgradeImpact` are empty. */
    val eraPlay: List<EraPlaySummary>? = null,
)

object Simulator {
    fun run(
        runs: Int, baseSeed: Long, policies: List<Policy>, legacy: LegacyProfile = LegacyProfile(), config: BalanceConfig = BalanceConfig.DEFAULT,
        maxDays: Int = config.maxSimulatedDays, label: String = "new account", content: ContentCatalog = LaunchContent.catalog,
        reserve: Int = SimulationDriver.DEFAULT_RESERVE, customerMetrics: Boolean = false, blessing: BlessingPref? = null,
    ): List<Report> {
        val driver = SimulationDriver(GameEngine(content, config), maxDays = maxDays, reserve = reserve, customerMetrics = customerMetrics, blessing = blessing)
        // Runs are independent and the engine is pure, so seeds run in parallel; results are collected in seed order.
        return policies.map { p ->
            Report(p, IntStream.range(0, runs).parallel().mapToObj { i -> driver.playRun(legacy, baseSeed + i, p).first }.collect(Collectors.toList()), label)
        }
    }

    /** A legacy account with every upgrade maxed, to measure permanent progression impact. */
    fun maxedLegacy(engine: GameEngine): LegacyProfile =
        LegacyProfile(points = 0, upgrades = engine.content.upgrades.associate { it.id to it.maxLevel })

    /**
     * The Legend Board a veteran account carries: the blades remembered from [runs] new-account runs of [policy],
     * bounded like `Legacy.claim`. Only the board is taken (no journal, lineages or points), so `--legends` isolates
     * what returning blades do. The source runs are ten separate first eras, so their blades would share keys
     * ("era1-w5" in several of them, and again in the account's own first era); each key is marked with its run, as
     * one account's keys are unique by era.
     */
    fun veteranLegendBoard(engine: GameEngine, policy: Policy, baseSeed: Long, runs: Int = 10): List<LegendEntry> {
        val driver = SimulationDriver(engine)
        return (0 until runs).map { baseSeed + it to driver.playRun(LegacyProfile(), baseSeed + it, policy).second }.filter { it.second.isEnded }
            .flatMap { (seed, state) -> engine.closeRun(state).legends.map { it.copy(weaponKey = "${it.weaponKey}@$seed") } }.takeLast(engine.config.legacyTracks.legendBoardSize)
    }

    /**
     * Relative impact of each permanent upgrade: [policy] (BALANCED_FAIR by default) with that single upgrade maxed vs
     * [baselineMedianDays]. Every row carries [legendBoard], so the baseline must be run with the same board.
     */
    fun upgradeImpact(
        runs: Int, baseSeed: Long, config: BalanceConfig, baselineMedianDays: Int, baselineMeanDays: Double, maxDays: Int = config.maxSimulatedDays,
        content: ContentCatalog = LaunchContent.catalog, policy: Policy = Policy.BALANCED_FAIR, reserve: Int = SimulationDriver.DEFAULT_RESERVE,
        legendBoard: List<LegendEntry> = emptyList(), blessing: BlessingPref? = null,
    ): List<UpgradeImpact> {
        val engine = GameEngine(content, config)
        return engine.content.upgrades.map { u ->
            val legacy = LegacyProfile(upgrades = mapOf(u.id to u.maxLevel), legendBoard = legendBoard)
            val report = run(runs, baseSeed, listOf(policy), legacy, config, maxDays, label = u.name, content = content, reserve = reserve, blessing = blessing).single()
            val s = report.summary()
            UpgradeImpact(u.id.value, u.name, u.maxLevel, s.daysMedian, s.daysMedian - baselineMedianDays, s.daysMean, s.daysMean - baselineMeanDays, report.yardsticks())
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

/**
 * `--set key=value[,key=value]`: a hand-written allowlist (no reflection) of the customer, population and threat
 * numbers the balance sweeps vary. Throws [IllegalArgumentException] on an unknown key or a value that does not parse.
 */
internal fun applySet(base: BalanceConfig, arg: String): BalanceConfig {
    var c = base
    for (pair in arg.split(',')) {
        val key = pair.substringBefore('=')
        require('=' in pair) { "expected key=value, got '$pair'" }
        val v = pair.substringAfter('=')
        fun int() = requireNotNull(v.toIntOrNull()) { "$key needs an integer, got '$v'" }
        fun dbl() = requireNotNull(v.toDoubleOrNull()) { "$key needs a number, got '$v'" }
        c = when (key) {
            // The CustomerConfig names; the flat names they had before balance v7 still work.
            "shopCapacity", "maxCustomersPerDay" -> c.copy(customers = c.customers.copy(shopCapacity = int()))
            "baseVisitChance" -> c.copy(customers = c.customers.copy(baseVisitChance = dbl()))
            "festivalExtraSeats", "festivalExtraCustomers" -> c.copy(customers = c.customers.copy(festivalExtraSeats = int()))
            "maxTurnedAwayDays" -> c.copy(customers = c.customers.copy(maxTurnedAwayDays = int()))
            "classSeats" -> c.copy(customers = c.customers.copy(classSeats = int()))
            "shelfSlots" -> c.copy(shelfSlots = int())
            "startingHeroes", "startingHeroCount" -> c.copy(customers = c.customers.copy(startingHeroes = int()))
            "minHeroPopulation" -> c.copy(customers = c.customers.copy(minHeroPopulation = int()))
            "maxHeroPopulation" -> c.copy(customers = c.customers.copy(maxHeroPopulation = int()))
            "populationTarget" -> c.copy(customers = c.customers.copy(populationTarget = int()))
            "arrivalChancePerMissing" -> c.copy(customers = c.customers.copy(arrivalChancePerMissing = dbl()))
            "arrivalChanceMax" -> c.copy(customers = c.customers.copy(arrivalChanceMax = dbl()))
            "patronageStipend" -> c.copy(customers = c.customers.copy(patronageStipend = int()))
            "needWantMet" -> c.copy(customers = c.customers.copy(needWantMet = dbl()))
            "seatWantWeight" -> c.copy(customers = c.customers.copy(seatWantWeight = dbl()))
            "wantLapseDays" -> c.copy(customers = c.customers.copy(wantLapseDays = int()))
            "sidegradeTolerance" -> c.copy(customers = c.customers.copy(sidegradeTolerance = dbl()))
            "threatUtility" -> c.copy(customers = c.customers.copy(threatUtility = dbl()))
            "championSiegeWillingness" -> c.copy(customers = c.customers.copy(championSiegeWillingness = dbl()))
            "maxRumoursPerRun" -> c.copy(customers = c.customers.copy(maxRumoursPerRun = int()))
            "maxOpenCommissions" -> c.copy(customers = c.customers.copy(maxOpenCommissions = int()))
            "ordinaryWeight" -> c.copy(commissions = c.commissions.copy(ordinaryWeight = dbl()))
            "replacementWeight" -> c.copy(commissions = c.commissions.copy(replacementWeight = dbl()))
            "siegePrepWeight" -> c.copy(commissions = c.commissions.copy(siegePrepWeight = dbl()))
            "ambitionWeight" -> c.copy(commissions = c.commissions.copy(ambitionWeight = dbl()))
            "firstBladeWeight" -> c.copy(commissions = c.commissions.copy(firstBladeWeight = dbl()))
            "rumourCooldownDays" -> c.copy(customers = c.customers.copy(rumourCooldownDays = int()))
            "wallsRoutDamage" -> c.copy(weaponFates = c.weaponFates.copy(wallsRoutDamage = int()))
            "wallsRoutRatio" -> c.copy(weaponFates = c.weaponFates.copy(wallsRoutRatio = dbl()))
            "newAdventurerCount" -> c.copy(newAdventurerCount = int())
            "raidBase" -> c.copy(raidBase = dbl())
            "raidPerDay" -> c.copy(raidPerDay = dbl())
            "raidPerPressure" -> c.copy(raidPerPressure = dbl())
            "expeditionSuppression" -> c.copy(expeditionSuppression = int())
            "patrolSuppression" -> c.copy(patrolSuppression = int())
            "expeditionGoldMin" -> c.copy(expeditionGoldMin = int())
            "expeditionGoldMax" -> c.copy(expeditionGoldMax = int())
            "patrolGold" -> c.copy(patrolGold = int())
            "veteranGold" -> c.copy(veteranGold = int())
            "tradeInShare" -> c.copy(tradeInShare = dbl())
            "fairGoldPerPower" -> c.copy(fairGoldPerPower = int())
            else -> throw IllegalArgumentException("unknown key '$key'; allowed: shopCapacity, baseVisitChance, festivalExtraSeats, maxTurnedAwayDays, classSeats, shelfSlots, startingHeroes, minHeroPopulation, maxHeroPopulation, populationTarget, arrivalChancePerMissing, arrivalChanceMax, patronageStipend, wallsRoutDamage, wallsRoutRatio, newAdventurerCount, raidBase, raidPerDay, raidPerPressure, expeditionSuppression, patrolSuppression, expeditionGoldMin, expeditionGoldMax, patrolGold, veteranGold, tradeInShare, fairGoldPerPower")
        }
    }
    return c
}

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
 *      --upgrades id=level[,id=level]: the policy rows play that legacy account instead of a new one.
 *      --yardsticks: also prints the second yardsticks (first siege, premium sales, ...) for the policy rows.
 *      --legends: the maxed and impact runs carry a veteran's Legend Board, so famous blades can return.
 *      --knownNameGold N: coin each Known Name regular starts with (v5 sweep).
 *      --customers: adds the customer and identity metrics ([CustomerSummary]) to the policy rows, the text and the --json report.
 *      --set key=value[,key=value]: overrides allowlisted BalanceConfig numbers ([applySet]); an unknown key stops the run with exit code 2.
 *      --policy NAME[,NAME]|all|gdd|bots|every: `all` is the 14 classic policies (unchanged), `bots` the T0.7 bots, `every` both.
 *      --blessing first|energy|quality|sales|patronage|defense: the blessing every policy takes when offered (default: the first offered).
 *      --eras N [--buy cheapest|walls|track=ID]: plays N eras per seed on one account (claim, buy upgrades by the rule, carry journal, Legend Board, lineages).
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
    val policies = argMap["--policy"]?.let { p -> when (p) { "all" -> Policy.CLASSIC; "gdd" -> Policy.GDD_SET; "bots" -> Policy.BOTS; "every" -> Policy.entries.toList(); else -> p.split(',').map { Policy.valueOf(it) } } } ?: Policy.GDD_SET
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
            if (a.id.value in ids) a.copy(baneMultiplier = 1.0, eliteMultiplier = 1.0, healOnWin = 0, selfDamageOnWin = 0, lootChanceBonus = 0.0, scarceLoot = false, damageTakenMultiplier = 1.0, breakChanceOnLoss = 0.0) else a
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
    argMap["--knownNameGold"]?.let { config = config.copy(legacyTracks = config.legacyTracks.copy(knownNameRegularGold = it.toInt())); overrides["knownNameGold"] = it }
    argMap["--set"]?.let { arg ->
        try { config = applySet(config, arg) } catch (e: IllegalArgumentException) { System.err.println("--set: ${e.message}"); exitProcess(2) }
        overrides["set"] = arg
    }
    val customers = argMap["--customers"] == "true"
    val blessing = argMap["--blessing"]?.let { arg ->
        val pref = BlessingPref.entries.firstOrNull { it.name.equals(arg, ignoreCase = true) }
        if (pref == null) { System.err.println("--blessing: unknown '$arg'; allowed: ${BlessingPref.entries.joinToString(", ") { it.name.lowercase() }}"); exitProcess(2) }
        overrides["blessing"] = arg
        pref
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
    // --upgrades: the policy rows play this legacy account instead of a new one (per-level and combination sweeps).
    val upgrades = argMap["--upgrades"]?.let { arg ->
        overrides["upgrades"] = arg
        arg.split(',').associate { pair ->
            val def = requireNotNull(content.upgradeById[UpgradeId(pair.substringBefore('='))]) { "Unknown upgrade ${pair.substringBefore('=')}" }
            def.id to pair.substringAfter('=').toInt().coerceIn(0, def.maxLevel)
        }
    }
    argMap["--eras"]?.let { arg ->
        val eras = requireNotNull(arg.toIntOrNull()?.takeIf { it > 0 }) { "--eras needs a positive number, got '$arg'" }
        val rule = try { BuyRule.parse(argMap["--buy"] ?: "cheapest", content) } catch (e: IllegalArgumentException) { System.err.println("--buy: ${e.message}"); exitProcess(2) }
        overrides["eras"] = arg; overrides["buy"] = rule.label
        val board = if (argMap["--legends"] == "true") Simulator.veteranLegendBoard(GameEngine(content, config), policies.first(), seed) else emptyList()
        if (board.isNotEmpty()) println("== Every account starts with a veteran Legend Board: ${board.size} blades, mean power ${"%.0f".format(board.map { it.power }.average())} ==")
        val summaries = EraPlay.run(GameEngine(content, config), policies, runs, seed, eras, rule, LegacyProfile(upgrades = upgrades ?: emptyMap(), legendBoard = board), maxDays, reserve, blessing)
        summaries.forEach { println(it.render()) }
        val elapsedMs = (System.nanoTime() - start) / 1_000_000
        println("elapsed $elapsedMs ms")
        argMap["--json"]?.let { path ->
            val report = SimReport(
                contentVersion = engine.content.version, balanceVersion = engine.config.version, rulesVersion = GameEngine.RULES_VERSION,
                runs = runs, baseSeed = seed, maxDays = maxDays, overrides = overrides, reserve = reserve,
                policies = emptyList(), upgradeImpact = emptyList(), perf = null, elapsedMs = elapsedMs, eraPlay = summaries,
            )
            File(path).writeText(reportJson.encodeToString(SimReport.serializer(), report))
            println("report written to $path")
        }
        return
    }
    println(if (upgrades == null) "== New legacy account ==" else "== Legacy account with ${argMap["--upgrades"]} ==")
    val reports = Simulator.run(runs, seed, policies, legacy = LegacyProfile(upgrades = upgrades ?: emptyMap()), config = config, maxDays = maxDays, label = if (upgrades == null) "new account" else "upgraded account", content = content, reserve = reserve, customerMetrics = customers, blessing = blessing)
    reports.forEach { println(it.render()) }
    if (argMap["--yardsticks"] == "true") {
        println("== Second yardsticks per policy (means per run; columns as in the upgrade table) ==")
        println(Yardsticks.HEADER)
        reports.forEach { println(it.yardsticks().render(it.policy.name)) }
    }
    var maxed: List<Report> = emptyList()
    var impact: List<UpgradeImpact> = emptyList()
    if (argMap["--noImpact"] != "true") {
        // A new account has no Legend Board, so no blade can return; --legends gives the maxed and impact runs (baseline included) a veteran's board.
        val legends = if (argMap["--legends"] == "true") Simulator.veteranLegendBoard(engine, impactPolicy, seed) else emptyList()
        if (legends.isNotEmpty()) println("== Veteran Legend Board for the runs below: ${legends.size} blades, mean quality ${"%.0f".format(legends.map { it.quality }.average())}, mean power ${"%.0f".format(legends.map { it.power }.average())} ==")
        println("== Maxed legacy account (all upgrades) ==")
        maxed = Simulator.run(runs, seed, listOf(Policy.BALANCED_FAIR, impactPolicy).distinct(), legacy = Simulator.maxedLegacy(engine).copy(legendBoard = legends), config = config, maxDays = maxDays, label = "all upgrades maxed", content = content, reserve = reserve, customerMetrics = customers, blessing = blessing)
        maxed.forEach { println(it.render()) }
        val baseline = reports.firstOrNull { it.policy == impactPolicy }?.takeIf { upgrades == null && legends.isEmpty() }
            ?: Simulator.run(runs, seed, listOf(impactPolicy), legacy = LegacyProfile(legendBoard = legends), config = config, maxDays = maxDays, content = content, reserve = reserve, blessing = blessing).single()
        val baseSummary = baseline.summary()
        println("== Upgrade impact ($impactPolicy, single upgrade maxed vs none: median ${baseSummary.daysMedian} mean ${"%.1f".format(baseSummary.daysMean)} days) ==")
        impact = Simulator.upgradeImpact(runs, seed, config, baseSummary.daysMedian, baseSummary.daysMean, maxDays, content, impactPolicy, reserve, legends, blessing)
        impact.forEach { println("  ${it.name} (${it.upgradeId} L${it.level}): median=${it.daysMedian} (${"%+d".format(it.deltaVsNone)}) mean=${"%.1f".format(it.daysMean)} (${"%+.1f".format(it.deltaMeanVsNone)})") }
        val allMaxed = maxed.single { it.policy == impactPolicy }
        allMaxed.summary().let { println("  all maxed: median=${it.daysMedian} (${"%+d".format(it.daysMedian - baseSummary.daysMedian)}) mean=${"%.1f".format(it.daysMean)} (${"%+.1f".format(it.daysMean - baseSummary.daysMean)})") }
        println("== Second yardsticks ($impactPolicy, means per run: defense / held / forged / sold / tools at the day ${config.siegeInterval} siege, the first tier-4+ core sale, limited-stock units bought, signature weapons, legends returned, then run totals) ==")
        println(Yardsticks.HEADER)
        println(baseline.yardsticks().render("none"))
        impact.forEach { println(it.yardsticks!!.render(it.name)) }
        println(allMaxed.yardsticks().render("all maxed"))
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
