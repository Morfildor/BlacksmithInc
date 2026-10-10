package com.tinyblacksmith.core

import com.tinyblacksmith.core.TestSupport.engine
import com.tinyblacksmith.core.content.LaunchContent
import com.tinyblacksmith.core.content.MaterialCategory
import com.tinyblacksmith.core.engine.Command
import com.tinyblacksmith.core.engine.acceptedOrThrow
import com.tinyblacksmith.core.engine.stateOrThrow
import com.tinyblacksmith.core.legacy.Legacy
import com.tinyblacksmith.core.legacy.LegacyOutcome
import com.tinyblacksmith.core.model.*
import com.tinyblacksmith.core.shopday.AftermathKind
import com.tinyblacksmith.core.shopday.ShopDay
import com.tinyblacksmith.core.shopday.ShopDayScript
import com.tinyblacksmith.core.sim.Policy
import com.tinyblacksmith.core.sim.SimulationDriver

/**
 * Scenario saves for the debug build's Scenarios menu (plan T2.9): one save per mechanic that natural play shows too
 * rarely to meet on a device. Each is a run the real engine played with a simulator policy, stopped on a pinned
 * morning because the mechanic shows on that day's End Day (or is already in the state); the two cases play cannot
 * reach in reasonable time say what was set by hand in [Scenario.built]. `ScenarioSavesTest` asserts every case;
 * `./gradlew :core:scenarios` writes them to `app/src/debug/assets/scenarios/`.
 *
 * The pinned seeds and days hold for the rules and balance they were found under. After a change that moves them,
 * `./gradlew :core:scenarios -Dscenarios.search=true` prints new candidates ([candidates]).
 */
object ScenarioSaves {
    class Scenario(
        val id: String,
        val title: String,
        /** One line for the menu: where the mechanic shows. */
        val description: String,
        /** How the save was made: the policy, seed and day it was played to, and anything set by hand. */
        val built: String,
        /** True when the mechanic shows on the next End Day; false when it is in the save already. */
        val endDay: Boolean,
        /** The aftermath card End Day shows for it, when it is one. */
        val card: AftermathKind? = null,
        /** The run belongs to an account of its own (upgrades, Legend Board): loading it replaces the player's legacy. */
        val ownLegacy: Boolean = false,
        /** The stored blade the case is about, when it is one: the menu names it and the device script opens it. */
        val blade: (GameState) -> Weapon? = { null },
        build: () -> GameState,
    ) {
        val state: GameState by lazy(build)
    }

    /** A resolved day: the state End Day returned, its record and what the shop-day screen plays back. */
    class Day(val before: GameState, val after: GameState, val resolution: DayResolution, val script: ShopDayScript)

    /** End Day as the game issues it, with nothing done first. */
    fun endDay(state: GameState): Day {
        val out = engine.handle(state, Command.EndDay(TestSupport.endDayId(state))).acceptedOrThrow()
        return Day(state, out.state, out.resolution!!, ShopDay.script(out.resolution!!, out.state, engine.content, engine.config))
    }

    /** The run of [seed] played by [policy] up to the morning of [day], before anything is done on it. */
    fun morning(seed: Long, policy: Policy, day: Int, legacy: LegacyProfile = LegacyProfile()): GameState =
        SimulationDriver(engine, maxDays = day - 1).playRun(legacy, seed, policy).second.also { check(it.day == day && !it.isEnded) { "seed $seed ended on day ${it.day}" } }

    /** Every morning of a run after its first, for the search. */
    private fun mornings(seed: Long, policy: Policy, legacy: LegacyProfile = LegacyProfile(), maxDays: Int = 40): List<GameState> {
        val states = ArrayList<GameState>()
        SimulationDriver(engine, maxDays = maxDays, onDayResolved = { s, _ -> if (!s.isEnded) states += s }).playRun(legacy, seed, policy)
        return states
    }

    const val ACCOUNT_SEED = 2L

    /**
     * The account of the two legacy cases: a first era played to its end ([ACCOUNT_SEED], BALANCED_ACTIVE), claimed, and
     * Known Name bought with the points it earned. Its Legend Board holds that era's famous blades.
     */
    val account: LegacyProfile by lazy {
        val ended = SimulationDriver(engine).playRun(LegacyProfile(), ACCOUNT_SEED, Policy.BALANCED_ACTIVE).second
        val claimed = (engine.claimLegacy(LegacyProfile(), engine.closeRun(ended)) as LegacyOutcome.Updated).legacy
        (engine.purchaseUpgrade(claimed, LaunchContent.UPG_REPUTATION) as LegacyOutcome.Updated).legacy
    }

    /** Day 1 on [account] with three quick iron swords forged and listed, so the regular has a shelf to come to. */
    private fun knownName(seed: Long): GameState {
        var s = engine.newRun(account, seed)
        repeat(3) { s = engine.handle(s, TestSupport.quickSword()).stateOrThrow() }
        for (w in s.storedWeapons()) s = engine.handle(s, Command.ToggleShelf(w.id, listed = true)).stateOrThrow()
        return s
    }

    /** The blade of the storied-blade case: sold, fought three times or more, held by two heroes or more, and back in the shop. */
    fun storied(state: GameState): Weapon? = state.storedWeapons().firstOrNull { w ->
        w.history.any { it.kind == "SOLD" } && w.history.count { it.kind == "VICTORY" || it.kind == "SIEGE" } >= 3 && Legacy.holders(w, state.era).size >= 2
    }

    /** The returned legend of its case: in storage with its affixes asleep. */
    fun dormant(state: GameState): Weapon? = state.storedWeapons().firstOrNull { it.legendKey != null && it.dormantAffixes.isNotEmpty() }

    const val STORAGE_FORGES = 200

    /**
     * Set by hand, since no run forges this much: the materials and the energy for [STORAGE_FORGES] quick forges are
     * added to a played morning, then every blade is forged by a real command (families, cores, augments and risks in
     * rotation) and left in storage. Materials and energy end as the played morning had them.
     */
    private fun longStorage(base: GameState): GameState {
        val c = engine.content
        val cores = c.materials(MaterialCategory.CORE)
        val augments = c.materials(MaterialCategory.AUGMENT)
        val forges = (0 until STORAGE_FORGES).map { i ->
            Command.Forge(ForgeMode.QUICK, c.families[i % c.families.size].id, cores[i % cores.size].id, augments[(i / 2) % augments.size].id, null, Risk.entries[i % Risk.entries.size])
        }
        val needed = forges.flatMap { listOf(it.coreId, it.augmentId) }.groupingBy { it }.eachCount()
        var s = base.copy(
            materials = base.materials + needed.mapValues { (id, n) -> (base.materials[id] ?: 0) + n },
            energy = base.energy + STORAGE_FORGES * engine.config.quickForgeEnergy,
        )
        for (f in forges) s = engine.handle(s, f).stateOrThrow()
        return s
    }

    /** The played cases, then the constructed ones for visitors, relics, siege traits and the pledge chain ([DepthScenarios]). */
    val all: List<Scenario> by lazy { played + DepthScenarios.all }

    val played: List<Scenario> = listOf(
        Scenario(
            "hall_lesson", "A lesson at the guild hall",
            "Press End Day: the aftermath opens with \"At the guild hall\", a hero taught by a guildmate.",
            "Played: BALANCED_FAIR, seed 1, stopped on the morning of day 21.", endDay = true, card = AftermathKind.GUILD_LESSON,
        ) { morning(1, Policy.BALANCED_FAIR, 21) },
        Scenario(
            "inherited_blade", "A fallen hero's blade passes to a guildmate",
            "Press End Day: \"A death\", then \"A blade passed on\" to a living guildmate.",
            "Played: BALANCED_FAIR, seed 12, stopped on the morning of day 11.", endDay = true, card = AftermathKind.INHERITED,
        ) { morning(12, Policy.BALANCED_FAIR, 11) },
        Scenario(
            "merchant_resale", "A merchant sells a fallen hero's blade",
            "A travelling merchant holds a blade and a hero can pay for it. Press End Day: \"Sold on by a merchant\".",
            "Played: BALANCED_FAIR, seed 87, stopped on the morning of day 9.", endDay = true, card = AftermathKind.RESOLD,
        ) { morning(87, Policy.BALANCED_FAIR, 9) },
        Scenario(
            "wall_death", "A champion falls at the wall",
            "A siege day. Press End Day: \"The siege\" is lost as a rout, then \"A death\" at the wall. The forge survives.",
            "Played: BALANCED_FAIR, seed 11, stopped on the morning of day 15 (the siege day).", endDay = true, card = AftermathKind.DEATH,
        ) { morning(11, Policy.BALANCED_FAIR, 15) },
        Scenario(
            "known_name", "Known Name: a regular on day 1",
            "Day 1 of era 2 with Known Name level 1: Town shows a regular already. Press End Day: the regular buys from the shelf.",
            "Played: era 1 on seed $ACCOUNT_SEED (BALANCED_ACTIVE) to its end, claimed, Known Name bought with its points; era 2 on seed 5, three quick iron swords forged and listed on day 1.",
            endDay = true, ownLegacy = true,
        ) { knownName(5) },
        Scenario(
            "returned_legend", "A returned legend, asleep",
            "Open Storage: a blade from the Legend Board has come back with its affixes dormant. Hone it and they wake.",
            "Played: the Known Name account (era 1 on seed $ACCOUNT_SEED); era 2 on seed 33 (BALANCED_ACTIVE), stopped on the morning of day 5, the day after the blade returned.",
            endDay = false, ownLegacy = true, blade = ::dormant,
        ) { morning(33, Policy.BALANCED_ACTIVE, 5, account) },
        Scenario(
            "storied_blade", "One blade through three owners",
            "Open Storage and the blade: its Story has three sales, its fights and a siege.",
            "Played: BALANCED_FAIR, seed 1, stopped on the morning of day 22.", endDay = false, blade = ::storied,
        ) { morning(1, Policy.BALANCED_FAIR, 22) },
        Scenario(
            "long_storage", "A long storage: $STORAGE_FORGES blades",
            "Open Storage: about $STORAGE_FORGES unsold blades to filter, sort, select and salvage.",
            "Constructed: BALANCED_FAIR, seed 1, morning of day 7; the materials and energy for $STORAGE_FORGES quick forges were added by hand, then each blade was forged by a real command.",
            endDay = false,
        ) { longStorage(morning(1, Policy.BALANCED_FAIR, 7)) },
    )

    fun byId(id: String): Scenario = all.first { it.id == id }

    /** The fallen hero's blade passed to a guildmate (a retiring mentor's blade is told with the same card and no fate). */
    fun inheritedOnDeath(day: Day): Boolean = day.script.aftermath.any { it.kind == AftermathKind.INHERITED && it.fate == WeaponFate.INHERITED }

    fun fellAtTheWall(day: Day): Boolean = !day.after.isEnded && day.resolution.field.any { it.outcome == FieldOutcome.FELL_AT_THE_WALL } &&
        day.script.aftermath.any { it.kind == AftermathKind.SIEGE_LOST } && day.script.aftermath.any { it.kind == AftermathKind.DEATH && it.heroId in day.resolution.field.filter { f -> f.outcome == FieldOutcome.FELL_AT_THE_WALL }.map { f -> f.heroId } }

    /**
     * Where the pins came from: the first mornings, over [seeds] and two policies, on which an untouched End Day shows
     * each played case. Slow (every day of every run is resolved twice); run it only to re-pin.
     */
    fun candidates(seeds: LongRange = 1L..400L, perCase: Int = 5): String = buildString {
        val found = linkedMapOf<String, MutableList<String>>()
        fun hit(case: String, policy: Policy, s: GameState, note: String = "") { found.getOrPut(case) { mutableListOf() }.let { if (it.size < perCase) it += "$policy seed ${s.seed} day ${s.day} $note" } }
        for (policy in listOf(Policy.BALANCED_FAIR, Policy.BALANCED_ACTIVE)) for (seed in seeds) for (s in mornings(seed, policy)) {
            val day = endDay(s)
            val cards = day.script.aftermath.map { it.kind }
            if (AftermathKind.GUILD_LESSON in cards) hit("hall_lesson", policy, s, cards.toString())
            if (inheritedOnDeath(day)) hit("inherited_blade", policy, s, cards.toString())
            if (AftermathKind.RESOLD in cards) hit("merchant_resale", policy, s, cards.toString())
            if (fellAtTheWall(day)) hit("wall_death", policy, s, cards.toString())
            if (storied(s) != null) hit("storied_blade", policy, s, storied(s)!!.name)
        }
        for (seed in 1L..40L) {
            val s = knownName(seed)
            val regular = s.aliveHeroes().first { it.loyalty >= engine.config.regularLoyaltyThreshold }
            if (endDay(s).script.featured.any { it.heroId == regular.id && it.purchasedWeaponId != null }) hit("known_name", Policy.PASSIVE, s, regular.fullName)
            for (m in mornings(seed, Policy.BALANCED_ACTIVE, account)) dormant(m)?.let { w ->
                if (m.lastResolution?.events?.any { it.type == EventType.ARTIFACT_RETURNED } == true && (m.materials[w.coreId] ?: 0) > 0) hit("returned_legend", Policy.BALANCED_ACTIVE, m, w.name)
            }
        }
        for ((case, hits) in found) { appendLine(case); hits.forEach { appendLine("  $it") } }
    }
}
