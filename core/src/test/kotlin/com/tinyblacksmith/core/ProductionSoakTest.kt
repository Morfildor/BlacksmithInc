package com.tinyblacksmith.core

import com.tinyblacksmith.core.config.SaveGrowthConfig
import com.tinyblacksmith.core.engine.GameEngine
import com.tinyblacksmith.core.model.CommissionStatus
import com.tinyblacksmith.core.model.DayResolution
import com.tinyblacksmith.core.model.GameState
import com.tinyblacksmith.core.model.HeroFate
import com.tinyblacksmith.core.model.LegacyProfile
import com.tinyblacksmith.core.model.WeaponLocation
import com.tinyblacksmith.core.persistence.EventCompaction
import com.tinyblacksmith.core.persistence.SaveCodec
import com.tinyblacksmith.core.persistence.WeaponPruning
import com.tinyblacksmith.core.sim.Policy
import com.tinyblacksmith.core.sim.SimulationDriver
import com.tinyblacksmith.core.sim.Simulator
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Plan 9.4, F10: a production-shaped long save, measured. Not in the default suite: `./gradlew :core:soak`.
 *
 * 2,000 days of forced survival under the engine's own rules only (no simulator trimming, no forge cap) for two smiths:
 * one who spends all ten energy forging every day and never salvages (BALANCED_FAIR), and an active one who hones, arms
 * the watch and salvages (BALANCED_ACTIVE). Every 100 days the state is counted, encoded, decoded and timed; the saves at
 * day 1,000 and 2,000 are written to `core/build/soak/` for the device half of the task.
 *
 * Hard budget (GDD 15.3): End Day p95 under 200 ms, compute only, over the first 1,000 days. Everything else is reported.
 * This is the JVM of the machine that runs it, not a phone.
 */
class ProductionSoakTest {
    private companion object {
        const val DAYS = 2000
        const val STEP = 100
        const val SEED = 4242L
        const val BUDGET_DAYS = 1000
        const val END_DAY_P95_BUDGET_MS = 200.0
        val out = File("build/soak")
    }

    private class Row(
        val day: Int, val storage: Int, val shelf: Int, val owned: Int, val returnable: Int, val gone: Int,
        val alive: Int, val dead: Int, val retired: Int, val open: Int, val closed: Int, val ids: Int,
        val events: Int, val eventsOld: Int, val historyLongest: Int, val historyMean: Double,
        val resolutionBytes: Int, val saveBytes: Int, val envelopeBytes: Int, val notStockBytes: Int,
        val encodeMs: Double, val decodeMs: Double, val p50: Double, val p95: Double, val max: Double,
    )

    private fun bytes(text: String) = text.toByteArray(Charsets.UTF_8).size
    private fun payload(s: GameState) = SaveCodec.json.encodeToString(GameState.serializer(), s)
    private fun percentile(sorted: List<Long>, p: Double) = if (sorted.isEmpty()) 0.0 else sorted[((sorted.size - 1) * p).toInt()] / 1_000_000.0

    /** The median of three, in milliseconds. */
    private fun <T> timed(block: () -> T): Pair<T, Double> {
        var result: T? = null
        val times = (1..3).map { val t = System.nanoTime(); result = block(); (System.nanoTime() - t) / 1_000_000.0 }.sorted()
        @Suppress("UNCHECKED_CAST")
        return (result as T) to times[1]
    }

    private fun measure(s: GameState, day: Int, window: List<Long>): Row {
        val weapons = s.weapons.values
        val lost = weapons.mapNotNull { it.location as? WeaponLocation.Lost }
        val retention = Simulator.forcedSurvival().eventRetentionDays
        val (text, encodeMs) = timed { SaveCodec.encodeRun(s) }
        val (decoded, decodeMs) = timed { SaveCodec.decodeRun(text) }
        assertEquals(s, decoded, "day $day: the save round-trips")
        val inner = bytes(payload(s))
        val sorted = window.sorted()
        return Row(
            day, weapons.count { it.isInStorage }, weapons.count { it.isListed }, weapons.count { it.location is WeaponLocation.Owned },
            lost.count { it.reason !in WeaponPruning.terminalReasons }, lost.count { it.reason in WeaponPruning.terminalReasons } + weapons.count { it.location is WeaponLocation.Destroyed },
            s.heroes.values.count { it.fate == HeroFate.ALIVE }, s.heroes.values.count { it.fate == HeroFate.DEAD }, s.heroes.values.count { it.fate == HeroFate.RETIRED },
            s.commissions.values.count { it.status == CommissionStatus.OFFERED || it.status == CommissionStatus.ACCEPTED },
            s.commissions.values.count { it.status != CommissionStatus.OFFERED && it.status != CommissionStatus.ACCEPTED },
            s.processedEndDayIds.size, s.events.size, s.events.count { it.day <= day - retention },
            weapons.maxOfOrNull { it.history.size } ?: 0, if (weapons.isEmpty()) 0.0 else weapons.sumOf { it.history.size }.toDouble() / weapons.size,
            s.lastResolution?.let { bytes(SaveCodec.json.encodeToString(DayResolution.serializer(), it)) } ?: 0,
            bytes(text), bytes(text) - inner, bytes(payload(s.copy(weapons = s.weapons.filterValues { !it.isInStorage && !it.isListed }))),
            encodeMs, decodeMs, percentile(sorted, 0.5), percentile(sorted, 0.95), percentile(sorted, 1.0),
        )
    }

    /** What each part of the save weighs: the payload with the part against the payload without it. Parts in the order they are reported. */
    private fun parts(s: GameState, day: Int): LinkedHashMap<String, Int> {
        val whole = bytes(payload(s))
        fun without(other: GameState) = whole - bytes(payload(other))
        fun weapons(keep: (com.tinyblacksmith.core.model.Weapon) -> Boolean) = without(s.copy(weapons = s.weapons.filterValues { !keep(it) }))
        fun returnable(w: com.tinyblacksmith.core.model.Weapon) = (w.location as? WeaponLocation.Lost)?.let { it.reason !in WeaponPruning.terminalReasons } == true
        val retention = Simulator.forcedSurvival().eventRetentionDays
        val result = linkedMapOf(
            "stock (storage and shelf)" to weapons { it.isInStorage || it.isListed },
            "blades that can come home (seized, lost with a hero, with a merchant)" to weapons { returnable(it) },
            "blades in heroes' hands" to weapons { it.location is WeaponLocation.Owned },
            "blades gone for good, not yet pruned or kept as legends" to weapons { !it.isInStorage && !it.isListed && it.location !is WeaponLocation.Owned && !returnable(it) },
            "dead and retired heroes" to without(s.copy(heroes = s.heroes.filterValues { it.fate == HeroFate.ALIVE })),
            "living heroes" to without(s.copy(heroes = s.heroes.filterValues { it.fate != HeroFate.ALIVE })),
            "records older than 30 days (history kept for the run)" to without(s.copy(events = s.events.filter { it.day > day - retention })),
            "records of the last 30 days" to without(s.copy(events = s.events.filter { it.day <= day - retention })),
            "commissions" to without(s.copy(commissions = emptyMap())),
            "the last day's report" to without(s.copy(lastResolution = null)),
        )
        result["everything else (journal, counters, flags, materials, IDs)"] = whole - result.values.sum()
        return result
    }

    private fun kb(bytes: Int) = "%,d".format((bytes + 512) / 1024)
    private fun ms(v: Double) = "%.1f".format(v)

    private fun report(label: String, policy: Policy, rows: List<Row>, p95First: Double, p95Second: Double, slopeAll: Double, slopeNotStock: Double, pair: String, parts: Map<Int, Map<String, Int>>, oldRecords: String): String = buildString {
        appendLine("## $label ($policy, seed $SEED, forced survival, $DAYS days, engine rules only)")
        appendLine()
        appendLine("| Day | Storage | Shelf | In hands | Returnable | Gone, kept | Alive | Dead | Retired | Open req. | Closed req. | IDs | Events | of them older than 30 days | Longest history | Mean history |")
        appendLine("|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|")
        for (r in rows) appendLine("| ${r.day} | ${r.storage} | ${r.shelf} | ${r.owned} | ${r.returnable} | ${r.gone} | ${r.alive} | ${r.dead} | ${r.retired} | ${r.open} | ${r.closed} | ${r.ids} | ${r.events} | ${r.eventsOld} | ${r.historyLongest} | ${"%.1f".format(r.historyMean)} |")
        appendLine()
        appendLine("| Day | Save KB | Envelope overhead KB | Save without stock KB | Last day's report KB | Encode ms | Decode ms | End Day p50 ms | p95 ms | max ms (last 100 days) |")
        appendLine("|---|---|---|---|---|---|---|---|---|---|")
        for (r in rows) appendLine("| ${r.day} | ${kb(r.saveBytes)} | ${kb(r.envelopeBytes)} | ${kb(r.notStockBytes)} | ${kb(r.resolutionBytes)} | ${ms(r.encodeMs)} | ${ms(r.decodeMs)} | ${ms(r.p50)} | ${ms(r.p95)} | ${ms(r.max)} |")
        appendLine()
        appendLine("End Day p95, compute only: days 1-$BUDGET_DAYS ${ms(p95First)} ms (hard budget ${END_DAY_P95_BUDGET_MS.toInt()} ms), days ${BUDGET_DAYS + 1}-$DAYS ${ms(p95Second)} ms.")
        appendLine("Growth after day 200: ${"%,.0f".format(slopeAll)} bytes a day in all, ${"%,.0f".format(slopeNotStock)} bytes a day without storage and shelf stock.")
        appendLine(pair)
        appendLine()
        val days = parts.keys.sorted()
        appendLine("| Part of the save (payload, KB) | " + days.joinToString(" | ") { "Day $it" } + " | Bytes a day, day ${days.first()} to ${days.last()} |")
        appendLine("|---|" + days.joinToString("") { "---|" } + "---|")
        for (name in parts.getValue(days.first()).keys) {
            val first = parts.getValue(days.first()).getValue(name)
            val last = parts.getValue(days.last()).getValue(name)
            appendLine("| $name | " + days.joinToString(" | ") { kb(parts.getValue(it).getValue(name)) } + " | ${"%,.0f".format((last - first).toDouble() / (days.last() - days.first()))} |")
        }
        appendLine()
        appendLine("Records older than 30 days at day $DAYS, by type: $oldRecords.")
    }

    private fun soak(label: String, policy: Policy) {
        val config = Simulator.forcedSurvival()
        val nanos = ArrayList<Long>(DAYS)
        val rows = ArrayList<Row>()
        val parts = sortedMapOf<Int, Map<String, Int>>()
        var atBudget: GameState? = null
        out.mkdirs()
        val driver = SimulationDriver(GameEngine(config = config), maxDays = DAYS, eventRetentionDays = 0, maxForgesPerDay = null) { s, n ->
            nanos += n
            val day = s.day - 1
            if (day % STEP == 0) rows += measure(s, day, nanos.takeLast(STEP))
            if (day == 200 || day == BUDGET_DAYS || day == DAYS) parts[day] = parts(s, day)
            if (day == BUDGET_DAYS) atBudget = s
            if (day == BUDGET_DAYS || day == DAYS) File(out, "save_${label}_day$day.json").writeText(SaveCodec.encodeRun(s))
        }
        val (stats, end) = driver.playRun(LegacyProfile(), SEED, policy)
        assertEquals(DAYS, stats.daysSurvived, "forced survival must not end: ${end.endCause}")

        // The same seed with the four T6.3a rules off, to the budget day: the outcome must be the same state.
        val unbounded = SimulationDriver(GameEngine(config = config.copy(saveGrowth = SaveGrowthConfig(0, 0, 0, 0))), maxDays = BUDGET_DAYS, eventRetentionDays = 0, maxForgesPerDay = null)
            .playRun(LegacyProfile(), SEED, policy).second
        fun GameState.outcome() = copy(weapons = weapons.mapValues { (_, w) -> w.copy(history = emptyList(), ownerIds = emptyList()) }, commissions = emptyMap(), processedEndDayIds = emptySet(), events = emptyList())
        val bounded = atBudget!!
        assertEquals(unbounded.outcome(), bounded.outcome(), "$label: the growth rules changed an outcome within $BUDGET_DAYS days")
        val pair = "Same seed with the four T6.3a rules off, day $BUDGET_DAYS: outcome identical; save ${kb(bytes(SaveCodec.encodeRun(bounded)))} KB with the rules, ${kb(bytes(SaveCodec.encodeRun(unbounded)))} KB without " +
            "(commissions ${bounded.commissions.size} / ${unbounded.commissions.size}, events ${bounded.events.size} / ${unbounded.events.size}, processed IDs ${bounded.processedEndDayIds.size} / ${unbounded.processedEndDayIds.size}, " +
            "history lines ${bounded.weapons.values.sumOf { it.history.size }} / ${unbounded.weapons.values.sumOf { it.history.size }})."

        val p95First = percentile(nanos.take(BUDGET_DAYS).sorted(), 0.95)
        val p95Second = percentile(nanos.drop(BUDGET_DAYS).sorted(), 0.95)
        val from = rows.first { it.day == 200 }
        val to = rows.last()
        val text = report(label, policy, rows, p95First, p95Second, (to.saveBytes - from.saveBytes).toDouble() / (to.day - from.day), (to.notStockBytes - from.notStockBytes).toDouble() / (to.day - from.day), pair, parts,
            end.events.filter { it.day <= DAYS - config.eventRetentionDays }.groupingBy { it.type }.eachCount().entries.sortedByDescending { it.value }.joinToString(", ") { "${it.key} ${it.value}" })
        File(out, "soak_$label.md").writeText(text)
        println(text)
        assertTrue(p95First < END_DAY_P95_BUDGET_MS, "$label: End Day p95 over the first $BUDGET_DAYS days is ${ms(p95First)} ms, budget ${END_DAY_P95_BUDGET_MS.toInt()} ms")
    }

    @Test
    fun aSmithWhoForgesAllDayAndNeverSalvages() = soak("hoarder", Policy.BALANCED_FAIR)

    @Test
    fun anActiveSmith() = soak("active", Policy.BALANCED_ACTIVE)
}
