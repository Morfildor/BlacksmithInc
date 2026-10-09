package com.tinyblacksmith.core.sim

import com.tinyblacksmith.core.content.BlessingEffect
import com.tinyblacksmith.core.content.ContentCatalog
import com.tinyblacksmith.core.content.LaunchContent
import com.tinyblacksmith.core.content.MaterialCategory
import com.tinyblacksmith.core.content.MaterialDef
import com.tinyblacksmith.core.content.ToolEffect
import com.tinyblacksmith.core.content.UpgradeEffect
import com.tinyblacksmith.core.content.WeaponFamilyDef
import com.tinyblacksmith.core.crafting.SignatureCatalog
import com.tinyblacksmith.core.engine.Command
import com.tinyblacksmith.core.engine.CommandOutcome
import com.tinyblacksmith.core.engine.GameEngine
import com.tinyblacksmith.core.engine.Technique
import com.tinyblacksmith.core.legacy.LegacyOutcome
import com.tinyblacksmith.core.market.Commissions
import com.tinyblacksmith.core.model.*
import com.tinyblacksmith.core.rng.Rng
import kotlinx.serialization.Serializable
import java.util.EnumMap
import java.util.stream.Collectors
import java.util.stream.IntStream

/**
 * T0.7 bots: headless players that use the 0.6.0 mechanics the classic policies never touch (Advanced Forge,
 * techniques, catalysts, commissions, the siege warning, signature recipes, limited-stock materials, several eras).
 * Every action is a real engine [Command]; a command the engine rejects is counted ([BotCounter.REJECTED]) and the tests
 * assert it stays zero. The one non-command step is [BotRules.startingGold], a scenario setup before day 1.
 */

/** Which blessing a run takes when the town offers a choice (`--blessing`). [FIRST] is the old habit: the first one offered. */
enum class BlessingPref(private val effects: List<BlessingEffect>) {
    FIRST(emptyList()),
    ENERGY(listOf(BlessingEffect.EXTRA_ENERGY)),
    QUALITY(listOf(BlessingEffect.QUALITY_BONUS)),
    SALES(listOf(BlessingEffect.SALE_GOLD_BONUS)),
    PATRONAGE(listOf(BlessingEffect.GUILD_PATRONAGE)),
    /** Heroes fight a tenth stronger (it counts in the siege forecast), else the town repairs faster. */
    DEFENSE(listOf(BlessingEffect.HERO_POWER, BlessingEffect.INTEGRITY_RECOVERY));

    /** The offered blessing with the preferred effect, else the first offered. */
    fun choose(offer: List<BlessingId>, content: ContentCatalog): BlessingId =
        effects.firstNotNullOfOrNull { e -> offer.firstOrNull { content.blessing(it).effect == e } } ?: offer.first()
}

/** What a bot does on top of the classic forging of its [Policy] (BALANCED_FAIR, or SYNERGY / INVEST where the policy says so). */
data class BotRules(
    /** Forge in Advanced mode with the cheapest catalyst at hand (Quick when none is, or when energy is under the Advanced cost). */
    val advanced: Boolean = false,
    val catalyst: Boolean = false,
    /** Forge in Advanced mode with this technique (Quick on the spare energy). */
    val technique: Technique? = null,
    /** Forge what accepted commissions ask for (family, element, quality) before anything else, then what heroes left the counter wanting (`Hero.want`). */
    val requests: Boolean = false,
    /** In the siege warning window, forge the faction's weak element and give stock to the watch until the forecast holds. */
    val siegePrep: Boolean = false,
    val signatures: Boolean = false,
    val scarce: Boolean = false,
    val spendthrift: Boolean = false,
    val novice: Boolean = false,
    val acceptsCommissions: Boolean = true,
    /** Scenario setup before day 1 (BROKE_START). */
    val startingGold: Int? = null,
    /** The blessing this bot prefers when `--blessing` is not given. */
    val blessing: BlessingPref? = null,
)

/** What a bot counted in one run. */
enum class BotCounter {
    ADVANCED_FORGES, QUICK_FORGES, TEMPER_FORGES, QUENCH_FORGES, ETCH_FORGES, CATALYST_FORGES,
    /** Forges made for an accepted commission, for a hero's standing want, for the siege window, for a signature recipe, for the scarce recipe. */
    REQUEST_FORGES, WANT_FORGES, SIEGE_FORGES, SIGNATURE_FORGES, SCARCE_FORGES,
    COMMISSIONS_COMPLETED, COMMISSIONS_EXPIRED,
    /** Forges whose recipe, catalyst, risk and quality all qualified for a signature, and how many of them transformed. */
    SIGNATURE_TRIES, SIGNATURE_HITS,
    /** Limited-stock units bought by the morning stockpile. */
    SCARCE_UNITS_BOUGHT,
    /** Weapons given to the watch for a siege. */
    SIEGE_DONATIONS,
    /** Expedition spoils that were catalysts or tier 3+ materials; spoils of heroes whose weapon (before End Day) carried Lucky; both. */
    SCARCE_LOOT, LUCKY_LOOT, LUCKY_SCARCE_LOOT,
    FINAL_REPUTATION, MAX_LOYALTY,
    /** Commands the engine rejected. Must stay 0. */
    REJECTED,
}

/** The non-zero [BotCounter] values of one run. */
@Serializable
data class BotRunStats(val counters: Map<BotCounter, Int> = emptyMap()) {
    operator fun get(c: BotCounter): Int = counters[c] ?: 0
}

/** Mean of each [BotCounter] per run. */
@Serializable
data class BotSummary(val perRun: Map<BotCounter, Double>) {
    fun render(): String = "  bot per run: " + perRun.entries.filter { it.value != 0.0 }.joinToString("  ") { "${it.key.name.lowercase()}=${"%.2f".format(it.value)}" } + "\n"

    companion object {
        fun of(runs: List<RunStats>): BotSummary? {
            if (runs.none { it.bot != null }) return null
            return BotSummary(BotCounter.entries.associateWith { c -> runs.sumOf { it.bot?.get(c) ?: 0 }.toDouble() / runs.size })
        }
    }
}

/** One bot's play inside [SimulationDriver.playRun]: a fresh instance per run, so it holds the run's small amount of state. */
internal class BotPlay(private val d: SimulationDriver, private val policy: Policy, private val rules: BotRules, private val rng: Rng) {
    private val engine = d.engine
    private val content = engine.content
    private val cfg = engine.config
    private val counts = EnumMap<BotCounter, Int>(BotCounter::class.java)
    private val attempts = HashMap<String, Int>()
    private var reserved: Set<WeaponId> = emptySet()
    private var day = 0
    private var forgesToday = 0
    private var source: BotCounter? = null
    private var rare: Triple<WeaponFamilyDef, MaterialDef, MaterialDef>? = null

    /** Limited-stock units the morning stockpile bought; the driver adds them to `rareMaterialsBought`. */
    val stockpiled: Int get() = counts[BotCounter.SCARCE_UNITS_BOUGHT] ?: 0

    private fun inc(c: BotCounter) { counts[c] = (counts[c] ?: 0) + 1 }

    private fun send(state: GameState, cmd: Command): GameState = when (val out = engine.handle(state, cmd)) {
        is CommandOutcome.Accepted -> out.state
        is CommandOutcome.Rejected -> { inc(BotCounter.REJECTED); state }
    }

    fun noteRejected() = inc(BotCounter.REJECTED)

    fun start(state: GameState): GameState = rules.startingGold?.let { state.copy(gold = it) } ?: state

    /** Before the forge: the spendthrift's tools, the scarce bot's stockpile. */
    fun morning(state: GameState): GameState {
        var s = state
        if (rules.spendthrift) {
            while (true) {
                val tool = content.tools.mapNotNull { t -> engine.toolCost(s, t.id)?.let { t.id to it } }.filter { it.second <= s.gold }.maxByOrNull { it.second } ?: break
                val next = send(s, Command.BuyTool(tool.first))
                if (next === s) break
                s = next
            }
        }
        if (rules.scarce) s = stockpile(s)
        return s
    }

    // ---- forging ----------------------------------------------------------------------------------------------

    private fun advancedOk(state: GameState) = state.energy >= cfg.advancedForgeEnergy

    private fun cost(cmd: Command.Forge) = if (cmd.mode == ForgeMode.ADVANCED) cfg.advancedForgeEnergy else cfg.quickForgeEnergy

    private fun mats(cmd: Command.Forge): Array<MaterialDef> = listOfNotNull(cmd.coreId, cmd.augmentId, cmd.catalystId).map { content.material(it) }.toTypedArray()

    /** Energy, materials on hand or in stock, and gold: the command the engine will accept. */
    private fun feasible(state: GameState, cmd: Command.Forge) = state.energy >= cost(cmd) && d.obtainable(state, *mats(cmd))

    private fun affinity(core: MaterialDef, augment: MaterialDef, family: WeaponFamilyDef) =
        (content.coreAugmentAffinity[core.id to augment.id] ?: 0) + (content.augmentFamilyAffinity[augment.id to family.id] ?: 0)

    /** The quality a forge averages (the roll is uniform in plus or minus the spread): formula of `Forge.apply` without roll, exceptional or defect. */
    private fun expectedQuality(state: GameState, family: WeaponFamilyDef, core: MaterialDef, augment: MaterialDef, catalyst: Boolean): Int =
        cfg.qualityBase + cfg.qualityPerCoreTier * core.tier + cfg.qualityPerAugmentTier * augment.tier + affinity(core, augment, family) +
            engine.upgradeTotal(state.legacy, UpgradeEffect.QUALITY_BONUS) + engine.toolTotal(state, ToolEffect.QUALITY_BONUS) + (if (catalyst) cfg.catalystQualityBonus else 0)

    /** Chance the roll lifts [expected] to [floor]. */
    private fun reachChance(expected: Int, floor: Int) = ((cfg.qualityRollSpread + expected - floor + 1) / (2.0 * cfg.qualityRollSpread + 1)).coerceIn(0.0, 1.0)

    fun forge(state: GameState): Command.Forge? {
        if (state.day != day) { day = state.day; forgesToday = 0 }
        if (rules.novice && forgesToday >= NOVICE_FORGES_PER_DAY) return null
        if (state.energy < cfg.quickForgeEnergy) return null
        source = null
        val cmd = (if (rules.novice) noviceForge(state) else null)
            ?: (if (rules.requests) requestForge(state) else null)
            ?: (if (rules.siegePrep && inWindow(state)) counterForge(state) else null)
            ?: (if (rules.signatures) signatureForge(state) else null)
            ?: (if (rules.scarce) scarceForge(state) else null)
            ?: d.chooseForge(state, policy, rng)?.let { upgraded(state, it) }
        return cmd?.takeIf { feasible(state, it) }
    }

    fun noteForge(cmd: Command.Forge, w: Weapon) {
        forgesToday++
        inc(if (cmd.mode == ForgeMode.ADVANCED) BotCounter.ADVANCED_FORGES else BotCounter.QUICK_FORGES)
        when (cmd.technique) {
            Technique.TEMPER -> inc(BotCounter.TEMPER_FORGES)
            Technique.QUENCH -> inc(BotCounter.QUENCH_FORGES)
            Technique.ETCH -> inc(BotCounter.ETCH_FORGES)
            null -> {}
        }
        if (cmd.catalystId != null) inc(BotCounter.CATALYST_FORGES)
        source?.let { inc(it) }
        if (SignatureCatalog.eligible(cmd, w.quality) != null) inc(BotCounter.SIGNATURE_TRIES)
        if (w.signatureId != null) inc(BotCounter.SIGNATURE_HITS)
    }

    /** ADVANCED_SMITH and the technique bots: the classic recipe, forged in Advanced mode when energy allows. */
    private fun upgraded(state: GameState, base: Command.Forge): Command.Forge {
        if (!rules.advanced && rules.technique == null) return base
        if (!advancedOk(state)) return base
        val catalyst = if (rules.catalyst) catalystFor(state, base) else null
        if (rules.catalyst && catalyst == null && rules.technique == null) return base
        val advanced = base.copy(mode = ForgeMode.ADVANCED, catalystId = catalyst, technique = rules.technique)
        return if (feasible(state, advanced)) advanced else base
    }

    /** A catalyst on hand, else the cheapest one the supplier has in stock that the gold still covers. */
    private fun catalystFor(state: GameState, base: Command.Forge): MaterialId? {
        val catalysts = content.materials(MaterialCategory.CATALYST).sortedBy { it.price }
        return (catalysts.firstOrNull { (state.materials[it.id] ?: 0) > 0 }
            ?: catalysts.firstOrNull { d.obtainable(state, content.material(base.coreId), content.material(base.augmentId), it) })?.id
    }

    // ---- requests (commissions and standing wants) -----------------------------------------------------------------

    /** A commission ([want] null: a quality floor, perhaps an element, the blade is kept off the shelf) or a hero's want (a power floor and a price the hero can pay). */
    private inner class Request(val key: String, val familyId: WeaponFamilyId, val minQuality: Int, val element: com.tinyblacksmith.core.content.Element?, private val commission: Commission?, val want: Want?) {
        fun fits(w: Weapon) = (w.isInStorage || w.isListed) && (
            if (commission != null) Commissions.fit(w, commission) == Commissions.Fit.OK
            else w.familyId == familyId && w.power >= want!!.minPower && engine.suggestedPrice(w) * policy.priceFactor * WANT_PRICE_FLOOR <= want.budget)
    }

    /**
     * "Price it inside her budget": the shelf price of a blade that answers a standing want whose asker cannot pay the
     * usual price but can pay most of it: the deepest such purse. Null for every other blade (the policy's own price).
     */
    fun wantPrice(state: GameState, w: Weapon): Int? {
        if (!rules.requests) return null
        val usual = (engine.suggestedPrice(w) * policy.priceFactor).toInt()
        val purses = state.aliveHeroes().mapNotNull { it.want }.filter { it.familyId == w.familyId && w.power >= it.minPower }.map { it.budget }
        if (purses.any { it >= usual }) return null
        return purses.filter { it >= usual * WANT_PRICE_FLOOR }.maxOrNull()
    }

    /** What the shop is asked for: accepted commissions, most urgent first, then the wants of the living, deepest purse first. Nothing else in the bot reads either directly. */
    private fun requests(state: GameState): List<Request> =
        state.commissions.values.filter { it.status == CommissionStatus.ACCEPTED }.sortedBy { it.deadlineDay }.map { Request(it.id.value, it.familyId, it.minQuality, it.element, it, null) } +
            state.aliveHeroes().mapNotNull { h -> h.want?.let { Request("want:${h.id.value}:${it.sinceDay}", it.familyId, 0, null, null, it) } }.sortedByDescending { it.want!!.budget }

    /** The power a forge averages: formula of `Forge.apply` without affixes or a signature. */
    private fun expectedPower(family: WeaponFamilyDef, core: MaterialDef, quality: Int) = family.basePower + cfg.powerPerCoreTier * core.tier + quality / cfg.powerPerQualityDivisor

    /**
     * The cheapest recipe (owned materials cost nothing) whose average quality clears the request, forged Quick and
     * Balanced, a few tries per request. For a want: whose average power clears it and whose going rate the hero can pay.
     */
    private fun requestForge(state: GameState): Command.Forge? {
        for (r in requests(state)) {
            if (state.weapons.values.any { r.fits(it) } || (attempts[r.key] ?: 0) >= (if (r.want == null) MAX_REQUEST_FORGES else MAX_WANT_FORGES)) continue
            val family = content.familyById[r.familyId] ?: continue
            val best = content.materials(MaterialCategory.CORE).flatMap { core ->
                content.materials(MaterialCategory.AUGMENT).filter { r.element == null || it.element == r.element }.map { core to it }
            }.filter { (core, aug) ->
                val quality = expectedQuality(state, family, core, aug, false)
                val power = expectedPower(family, core, quality)
                d.obtainable(state, core, aug) && quality >= r.minQuality + REQUEST_QUALITY_MARGIN &&
                    (r.want == null || (power >= r.want.minPower + WANT_POWER_MARGIN && power * cfg.fairGoldPerPower * policy.priceFactor * WANT_PRICE_FLOOR <= r.want.budget))
            }.minByOrNull { (core, aug) -> d.purchaseCost(state, core) + d.purchaseCost(state, aug) } ?: continue
            attempts[r.key] = (attempts[r.key] ?: 0) + 1
            source = if (r.want == null) BotCounter.REQUEST_FORGES else BotCounter.WANT_FORGES
            return Command.Forge(ForgeMode.QUICK, family.id, best.first.id, best.second.id, null, Risk.BALANCED)
        }
        return null
    }

    /** Unlists the best blade for each open commission and keeps it off the shelf: a browser must not walk off with it. A want's blade belongs on the shelf. */
    fun reserve(state: GameState): GameState {
        if (!rules.requests) return state
        var s = state
        val ids = mutableSetOf<WeaponId>()
        for (r in requests(s).filter { it.want == null }) {
            val w = s.weapons.values.filter { r.fits(it) }.maxByOrNull { it.quality } ?: continue
            ids += w.id
            if (w.isListed) s = send(s, Command.ToggleShelf(w.id, false))
        }
        reserved = ids
        // A blade somebody asked for must reach the shelf: when it is full, the weakest listed blade nobody asked for makes room.
        var waiting = s.storedWeapons().count { it.id !in ids && asked(s, it) } - (engine.shelfSlots(s) - s.listedWeapons().size)
        for (w in s.listedWeapons().filter { !asked(s, it) }.sortedBy { it.power }) {
            if (waiting-- <= 0) break
            s = send(s, Command.ToggleShelf(w.id, false))
        }
        return s
    }

    /** Some living hero's standing want is for a blade like [w], at a price the bot would come down to. */
    private fun asked(state: GameState, w: Weapon): Boolean {
        val floor = engine.suggestedPrice(w) * policy.priceFactor * WANT_PRICE_FLOOR
        return state.aliveHeroes().any { h -> h.want?.let { it.familyId == w.familyId && w.power >= it.minPower && floor <= it.budget } == true }
    }

    /** The stored weapons in listing order, without the ones kept for a request; a requests bot lists what was asked for first. */
    fun stockToList(state: GameState): List<Weapon> {
        val stored = state.storedWeapons().filter { it.id !in reserved }
        val ordered = if (policy.active || (rules.siegePrep && inWindow(state))) stored.sortedByDescending { it.power } else stored
        return if (rules.requests) ordered.sortedByDescending { asked(state, it) } else ordered
    }

    // ---- siege --------------------------------------------------------------------------------------------------

    /** The mornings the siege warning covers: two days out, one day out, the day itself (`Battle.warnOfSiege` warns at two and one). */
    private fun inWindow(state: GameState) = state.town.nextSiegeDay - state.day in 0..2

    /** The best recipe with an augment of the leading faction's weak element that the shop can obtain today. */
    private fun counterForge(state: GameState): Command.Forge? {
        val target = state.factions.values.maxByOrNull { it.pressure }?.let { content.factionById[it.id]?.weakTo } ?: return null
        var best: Command.Forge? = null
        var bestQuality = Int.MIN_VALUE
        for (augment in content.materials(MaterialCategory.AUGMENT).filter { it.element == target }) for (core in content.materials(MaterialCategory.CORE)) {
            if (!d.obtainable(state, core, augment)) continue
            for (family in content.families) {
                val q = expectedQuality(state, family, core, augment, false)
                if (q > bestQuality) { bestQuality = q; best = Command.Forge(ForgeMode.QUICK, family.id, core.id, augment.id, null, Risk.BALANCED) }
            }
        }
        if (best != null) source = BotCounter.SIEGE_FORGES
        return best
    }

    /** Evening of a warning day: stock the shelf could not use goes to the watch, strongest first, until the forecast holds with a margin. */
    fun evening(state: GameState): GameState {
        if (!rules.siegePrep || !inWindow(state)) return state
        var s = state
        for (w in s.storedWeapons().filter { it.id !in reserved }.sortedByDescending { it.power }) {
            if (s.town.armory >= cfg.armoryMax) break
            val outlook = engine.siegeForecast(s) ?: break
            if (outlook.townDefense >= outlook.raidPower * SIEGE_MARGIN) break
            val next = send(s, Command.DonateWeapon(w.id))
            if (next !== s) inc(BotCounter.SIEGE_DONATIONS)
            s = next
        }
        return s
    }

    // ---- signatures ---------------------------------------------------------------------------------------------

    /**
     * Follows the journal's clue ladder (`crafting.Journal.rungs`) and nothing else: a signature is tried only once its
     * first rung is held, with the catalyst and the temper only where their rungs say them (no catalyst and Balanced
     * until then), and judged against its quality floor only once that rung is held. Unfound signatures first, the ones
     * with more rungs before the others, then the cheapest. Every miss earns the next rung. With no clue at all it forges
     * as its policy does (and the plain iron and ember sword is itself a base recipe).
     */
    private fun signatureForge(state: GameState): Command.Forge? {
        val journal = state.legacy.journal
        var best: Command.Forge? = null
        var bestKey = Triple(false, 0, 0.0)
        for (sig in SignatureCatalog.all) {
            val family = content.familyById[sig.familyId] ?: continue
            val core = content.materialById[sig.coreId] ?: continue
            val augment = content.materialById[sig.augmentId] ?: continue
            val have = com.tinyblacksmith.core.crafting.Journal.rungs(journal, sig)
            if (com.tinyblacksmith.core.crafting.ClueRung.RECIPE !in have) continue
            val catalyst = sig.catalystId?.takeIf { com.tinyblacksmith.core.crafting.ClueRung.CATALYST in have }?.let { content.materialById[it] ?: return@let null }
            if (sig.catalystId != null && com.tinyblacksmith.core.crafting.ClueRung.CATALYST in have && catalyst == null) continue
            val risk = (if (com.tinyblacksmith.core.crafting.ClueRung.TEMPER in have) sig.risk else null) ?: Risk.BALANCED
            val cmd = Command.Forge(if (catalyst != null) ForgeMode.ADVANCED else ForgeMode.QUICK, family.id, core.id, augment.id, catalyst?.id, risk)
            if (!feasible(state, cmd)) continue
            val chance = if (com.tinyblacksmith.core.crafting.ClueRung.QUALITY in have) reachChance(expectedQuality(state, family, core, augment, catalyst != null), sig.minQuality) else 1.0
            if (chance < MIN_SIGNATURE_REACH) continue
            val gold = listOfNotNull(core, augment, catalyst).sumOf { d.purchaseCost(state, it) }
            val key = Triple(journal.state(sig.journalKey) != KnowledgeState.SIGNATURE_DISCOVERED, have.size, chance / (gold + 20))
            if (best == null || compareValuesBy(key, bestKey, { it.first }, { it.second }, { it.third }) > 0) { best = cmd; bestKey = key }
        }
        if (best != null) source = BotCounter.SIGNATURE_FORGES
        return best
    }

    // ---- scarce recipe ------------------------------------------------------------------------------------------

    private fun budgetFor(s: GameState, mats: List<MaterialDef>): Boolean =
        mats.all { (s.materials[it.id] ?: 0) > 0 || (s.supplierStock[it.id] ?: 1) >= 1 } && mats.sumOf { d.purchaseCost(s, it) } * STOCKPILE_GOLD_DIVISOR <= s.gold - d.reserve

    /** The best recipe made of limited-stock materials (affinity plus tier) that costs at most half the gold; sticky until it stops being affordable. */
    private fun rareRecipe(s: GameState): Triple<WeaponFamilyDef, MaterialDef, MaterialDef>? {
        rare?.let { (_, core, augment) -> if (budgetFor(s, listOf(core, augment))) return rare }
        var best: Triple<WeaponFamilyDef, MaterialDef, MaterialDef>? = null
        var bestScore = Int.MIN_VALUE
        for (core in content.materials(MaterialCategory.CORE).filter { it.dailySupplierStock != null }) for (augment in content.materials(MaterialCategory.AUGMENT).filter { it.dailySupplierStock != null }) {
            if (!budgetFor(s, listOf(core, augment))) continue
            for (family in content.families) {
                val score = affinity(core, augment, family) + 2 * (core.tier + augment.tier)
                if (score > bestScore) { bestScore = score; best = Triple(family, core, augment) }
            }
        }
        rare = best
        return best
    }

    /** Morning: buy up to the day's Quick forges of the rare recipe's core and augment, as far as stock and the gold rule allow. */
    private fun stockpile(state: GameState): GameState {
        var s = state
        val (_, core, augment) = rareRecipe(s) ?: return s
        for (k in 1..s.energy / cfg.quickForgeEnergy) {
            val missing = listOf(core, augment).filter { (s.materials[it.id] ?: 0) < k }
            if (missing.isEmpty()) continue
            if (missing.any { (s.supplierStock[it.id] ?: 1) < 1 } || missing.sumOf { engine.materialPrice(s, it.id) } * STOCKPILE_GOLD_DIVISOR > s.gold - d.reserve) break
            for (m in missing) {
                val next = send(s, Command.BuyMaterial(m.id, 1))
                if (next !== s && m.dailySupplierStock != null) inc(BotCounter.SCARCE_UNITS_BOUGHT)
                s = next
            }
        }
        return s
    }

    private fun scarceForge(state: GameState): Command.Forge? {
        val (family, core, augment) = rareRecipe(state) ?: return null
        if ((state.materials[core.id] ?: 0) < 1 || (state.materials[augment.id] ?: 0) < 1) return null
        source = BotCounter.SCARCE_FORGES
        return Command.Forge(ForgeMode.QUICK, family.id, core.id, augment.id, null, Risk.BALANCED)
    }

    // ---- novice -------------------------------------------------------------------------------------------------

    /** A first-timer: random family, random affordable materials, random risk. */
    private fun noviceForge(state: GameState): Command.Forge? {
        val cores = content.materials(MaterialCategory.CORE)
        val augments = content.materials(MaterialCategory.AUGMENT)
        val family = rng.pick(content.families)
        val core = rng.pick(cores.filter { d.obtainable(state, it) }.ifEmpty { listOf(cores.minBy { it.price }) })
        val augment = rng.pick(augments.filter { d.obtainable(state, core, it) }.ifEmpty { listOf(augments.minBy { it.price }) })
        return Command.Forge(ForgeMode.QUICK, family.id, core.id, augment.id, null, rng.pick(Risk.entries))
    }

    // ---- observation --------------------------------------------------------------------------------------------

    /** After End Day: commissions closed, and the spoils heroes brought back (Lucky's observable). [pre] is the state End Day started from. */
    fun observe(pre: GameState, res: DayResolution) {
        for (e in res.events) when (e.type) {
            EventType.COMMISSION_COMPLETED -> inc(BotCounter.COMMISSIONS_COMPLETED)
            EventType.COMMISSION_EXPIRED -> inc(BotCounter.COMMISSIONS_EXPIRED)
            EventType.EXPEDITION_WON -> e.data["material"]?.let { id ->
                val m = content.materialById[MaterialId(id)]
                val scarce = m != null && (m.category == MaterialCategory.CATALYST || m.tier >= 3)
                val lucky = e.subjectIds.firstOrNull()?.let { pre.equippedWeapon(HeroId(it)) }?.affixes?.contains(LaunchContent.LUCKY) == true
                if (scarce) inc(BotCounter.SCARCE_LOOT)
                if (lucky) { inc(BotCounter.LUCKY_LOOT); if (scarce) inc(BotCounter.LUCKY_SCARCE_LOOT) }
            }
            else -> {}
        }
    }

    fun finish(state: GameState): BotRunStats {
        counts[BotCounter.FINAL_REPUTATION] = state.reputation
        counts[BotCounter.MAX_LOYALTY] = state.heroes.values.maxOfOrNull { it.loyalty } ?: 0
        return BotRunStats(counts.filterValues { it != 0 })
    }

    companion object {
        /** Bot habits, not game rules. */
        const val NOVICE_FORGES_PER_DAY = 2
        const val MAX_REQUEST_FORGES = 3
        /** One forge per want: it stands three days and the blade stays on the shelf for the next asker. */
        const val MAX_WANT_FORGES = 1
        const val WANT_POWER_MARGIN = 2
        /** The bot comes down to this share of its usual price to meet an asker's purse, no further. */
        const val WANT_PRICE_FLOOR = 0.75
        const val REQUEST_QUALITY_MARGIN = 4
        const val MIN_SIGNATURE_REACH = 0.2
        const val SIEGE_MARGIN = 1.1
        /** A stockpile set is bought only while it costs at most 1 / this of the gold on hand. */
        const val STOCKPILE_GOLD_DIVISOR = 2
    }
}

/** How a multi-era account spends legacy points between eras (`--buy`): the named upgrade first (saving for it), then the cheapest next level. */
class BuyRule private constructor(val label: String, private val first: UpgradeId?) {
    /** Buys until the next purchase is unaffordable or nothing is left. */
    fun spend(engine: GameEngine, legacy: LegacyProfile): LegacyProfile {
        var l = legacy
        while (true) {
            val open = engine.content.upgrades.filter { l.upgradeLevel(it.id) < it.maxLevel }
            val pick = first?.let { id -> open.firstOrNull { it.id == id } } ?: open.minByOrNull { it.costPerLevel[l.upgradeLevel(it.id)] } ?: return l
            l = (engine.purchaseUpgrade(l, pick.id) as? LegacyOutcome.Updated)?.legacy ?: return l
        }
    }

    companion object {
        val CHEAPEST = BuyRule("cheapest", null)

        /** `cheapest`, `walls` (Stalwart Walls first), or `track=<upgrade id or name>`. */
        fun parse(arg: String, content: ContentCatalog): BuyRule {
            fun upgrade(key: String) = requireNotNull(content.upgrades.firstOrNull { it.id.value.equals(key, true) || it.name.equals(key, true) }) { "unknown upgrade '$key'" }
            return when {
                arg == "cheapest" -> CHEAPEST
                arg == "walls" -> BuyRule("walls", upgrade(LaunchContent.UPG_WALLS.value).id)
                arg.startsWith("track=") -> upgrade(arg.substringAfter('=')).let { BuyRule("track=${it.id.value}", it.id) }
                else -> throw IllegalArgumentException("expected cheapest, walls or track=<upgrade>, got '$arg'")
            }
        }
    }
}

/** One era of an account: the run, the account at its start, and the artifacts that came back during it. */
class EraRun(val era: Int, val stats: RunStats, val boardAtStart: Int, val levelsAtStart: Int, val returns: Int, val genuineReturns: Int, val ended: Boolean, val signaturesKnown: Int = 0)

@Serializable
data class EraRow(
    val era: Int, val runs: Int, val daysP10: Int, val daysMedian: Int, val daysMean: Double, val daysP90: Int, val daysMax: Int,
    val pointsMean: Double, val levelsAtStartMean: Double, val boardAtStartMean: Double,
    val returnsPerRun: Double, val genuineReturnsPerRun: Double, val runsWithGenuineReturn: Double, val unfinished: Int,
    /** Signatures in the account's journal when the era ends (mean), the share of accounts with at least one by then, and rumours a run. */
    val signaturesKnownMean: Double = 0.0, val accountsWithSignature: Double = 0.0, val rumoursPerRun: Double = 0.0,
    /** Of the legends that returned in this era: mean power as returned, share woken by a hone, share handed to a hero and their mean power then; the board's size when the era ends. */
    val returnPowerMean: Double = 0.0, val returnsWoken: Double = 0.0, val returnsHandedOver: Double = 0.0, val handedPowerMean: Double = 0.0,
)

/** [policy] played [eras] eras on one account per seed. A return is genuine when the artifact's era is earlier than the run's and the blade is on the Legend Board the run started with. */
@Serializable
data class EraPlaySummary(
    val policy: Policy, val buy: String, val accounts: Int, val eras: List<EraRow>,
    val genuineReturnsPerAccount: Double, val accountsWithGenuineReturn: Double,
) {
    fun render(): String = buildString {
        fun pct(v: Double) = "%.1f%%".format(100.0 * v)
        appendLine("Policy $policy over $accounts accounts of ${eras.size} eras, upgrades bought by '$buy'")
        for (r in eras) appendLine(
            "  era ${r.era}: days p10=${r.daysP10} median=${r.daysMedian} mean=${"%.1f".format(r.daysMean)} p90=${r.daysP90} max=${r.daysMax}  runs=${r.runs} unfinished=${r.unfinished}  " +
                "points/run=${"%.1f".format(r.pointsMean)}  upgrade levels at start=${"%.1f".format(r.levelsAtStartMean)}  legend board at start=${"%.1f".format(r.boardAtStartMean)}  " +
                "artifact returns/run=${"%.3f".format(r.returnsPerRun)} (genuine ${"%.3f".format(r.genuineReturnsPerRun)}, runs with one ${pct(r.runsWithGenuineReturn)})  " +
                "signatures known at the end=${"%.2f".format(r.signaturesKnownMean)} (accounts with one ${pct(r.accountsWithSignature)})  rumours/run=${"%.2f".format(r.rumoursPerRun)}  " +
                "returned legends: power as returned=${"%.1f".format(r.returnPowerMean)} woken=${pct(r.returnsWoken)} handed to a hero=${pct(r.returnsHandedOver)} (power then ${"%.1f".format(r.handedPowerMean)})",
        )
        appendLine("  genuine cross-era artifact returns per account=${"%.3f".format(genuineReturnsPerAccount)}  accounts with at least one=${pct(accountsWithGenuineReturn)}")
    }

    companion object {
        fun of(policy: Policy, buy: String, accounts: List<List<EraRun>>): EraPlaySummary {
            val rows = (1..(accounts.maxOfOrNull { it.size } ?: 0)).map { era ->
                val runs = accounts.mapNotNull { it.getOrNull(era - 1) }
                val days = runs.map { it.stats.daysSurvived }
                EraRow(
                    era, runs.size, percentile(days, 0.1), percentile(days, 0.5), days.average(), percentile(days, 0.9), days.max(),
                    runs.map { it.stats.legacyPoints }.average(), runs.map { it.levelsAtStart }.average(), runs.map { it.boardAtStart }.average(),
                    runs.map { it.returns }.average(), runs.map { it.genuineReturns }.average(), runs.count { it.genuineReturns > 0 }.toDouble() / runs.size, runs.count { !it.ended },
                    // An account whose earlier era did not end has no later era: it counts with what it knew then.
                    signaturesKnownMean = runs.map { it.signaturesKnown }.average(), accountsWithSignature = runs.count { it.signaturesKnown > 0 }.toDouble() / runs.size,
                    rumoursPerRun = runs.map { it.stats.rumours }.average(),
                    returnPowerMean = runs.sumOf { it.stats.legendReturnPower }.toDouble() / runs.sumOf { it.stats.legendsReturned }.coerceAtLeast(1),
                    returnsWoken = runs.sumOf { it.stats.legendsWoken }.toDouble() / runs.sumOf { it.stats.legendsReturned }.coerceAtLeast(1),
                    returnsHandedOver = runs.sumOf { it.stats.legendsHandedOver }.toDouble() / runs.sumOf { it.stats.legendsReturned }.coerceAtLeast(1),
                    handedPowerMean = runs.sumOf { it.stats.legendHandedPower }.toDouble() / runs.sumOf { it.stats.legendsHandedOver }.coerceAtLeast(1),
                )
            }
            return EraPlaySummary(
                policy, buy, accounts.size, rows,
                accounts.map { a -> a.sumOf { it.genuineReturns } }.average(), accounts.count { a -> a.any { it.genuineReturns > 0 } }.toDouble() / accounts.size,
            )
        }
    }
}

/** Several eras on one account: claim the run, buy upgrades by a fixed rule, carry the journal, Legend Board and lineages (`Legacy.claim`). */
object EraPlay {
    /** Seed offset between the eras of one account, so each era opens on a different world. */
    const val SEED_STRIDE = 1_000_000L

    fun account(driver: SimulationDriver, policy: Policy, seed: Long, eras: Int, rule: BuyRule, start: LegacyProfile = LegacyProfile()): List<EraRun> {
        val engine = driver.engine
        var legacy = start
        val result = ArrayList<EraRun>()
        for (e in 0 until eras) {
            val (stats, state) = driver.playRun(legacy, seed + e * SEED_STRIDE, policy)
            val returned = state.events.filter { it.type == EventType.ARTIFACT_RETURNED }
            val genuine = returned.count { ev ->
                val from = ev.data["era"]?.toIntOrNull()
                from != null && from < state.era && legacy.legendBoard.any { it.key == ev.data["legend"] }
            }
            val known = state.legacy.journal.interactions.count { it.key.startsWith("sig:") && it.value == KnowledgeState.SIGNATURE_DISCOVERED }
            result += EraRun(state.era, stats, legacy.legendBoard.size, legacy.upgrades.values.sum(), returned.size, genuine, state.isEnded, known)
            if (!state.isEnded) break
            val claimed = engine.claimLegacy(legacy, engine.closeRun(state)) as LegacyOutcome.Updated
            legacy = rule.spend(engine, claimed.legacy)
        }
        return result
    }

    fun run(
        engine: GameEngine, policies: List<Policy>, accounts: Int, baseSeed: Long, eras: Int, rule: BuyRule, start: LegacyProfile = LegacyProfile(),
        maxDays: Int = engine.config.maxSimulatedDays, reserve: Int = SimulationDriver.DEFAULT_RESERVE, blessing: BlessingPref? = null,
    ): List<EraPlaySummary> {
        val driver = SimulationDriver(engine, maxDays = maxDays, reserve = reserve, blessing = blessing)
        return policies.map { p ->
            val runs = IntStream.range(0, accounts).parallel().mapToObj { i -> account(driver, p, baseSeed + i, eras, rule, start) }.collect(Collectors.toList())
            EraPlaySummary.of(p, rule.label, runs)
        }
    }
}
