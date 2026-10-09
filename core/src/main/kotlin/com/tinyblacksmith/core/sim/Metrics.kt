package com.tinyblacksmith.core.sim

import com.tinyblacksmith.core.content.MaterialCategory
import com.tinyblacksmith.core.content.ToolEffect
import com.tinyblacksmith.core.engine.GameEngine
import com.tinyblacksmith.core.engine.ResolutionContext
import com.tinyblacksmith.core.engine.WorldEvents
import com.tinyblacksmith.core.market.Commissions
import com.tinyblacksmith.core.market.Market
import com.tinyblacksmith.core.model.*
import kotlinx.serialization.Serializable
import java.util.TreeMap

/*
 * Customer and identity metrics for the simulator (`--customers`, plan 9.3). They are read from the state before each
 * End Day and from the day's resolution, never from inside the engine: no gameplay RNG is drawn and no state is
 * touched, so a run with metrics is the same run. Metrics that need mechanics which do not exist yet are left out until
 * the task that adds the mechanic: wants and sidegrades by
 * reason (T4.1, T4.2), clue rungs, signature firsts and days to the first signature (T4.x signatures), commissions by
 * kind (T4.6), patronage share and stipend gold (T3.6), counter or resisted elements held at a siege (T4.2), and the
 * appearance model's face collisions (T1.x; until then a face is class plus `Math.floorMod(id.hashCode(), 5)`, as in
 * the planning harness).
 */

/** One siege as the player saw it that morning (`forecast*`) and as it resolved (`defense`, `raid`). */
@Serializable
data class SiegeSnap(
    val siege: Int, val held: Boolean, val pressure: Int, val militia: Int, val armory: Int, val championPower: Double,
    val forecastDefense: Double, val forecastRaid: Double, val defense: Int, val raid: Int,
)

/** Raw counters of one run; [CustomerSummary] aggregates them. Lists indexed by [CustomerCollector.BANDS], [CustomerCollector.CHECKPOINTS] or scan position. */
@Serializable
data class RunCustomers(
    val days: Int, val heroDays: Int,
    /** Index = visitors served that day. */
    val servedByCount: List<Int>, val capDays: Int, val lowDays: Int,
    /** Willing heroes who found every seat taken, and the days on which there was one (`DayResolution.turnedAway`). */
    val turnedAway: Int = 0, val turnedAwayDays: Int = 0,
    /** Hero-days, visits and purchases by position (0-based) among the living in numeric ID order (arrival order; before fair selection this was the order they were served in). */
    val posHeroDays: List<Int>, val posVisits: List<Int>, val posBuys: List<Int>,
    val visits: Int, val buys: Int, val bandVisits: List<Int>, val bandBuys: List<Int>,
    val returnVisits: Int, val gapDaysSum: Int, val gapCount: Int,
    val heroesEver: Int, val servedEver: Int, val servedByDay5: Int, val servedByDay10: Int,
    /** Days from a hero's first appearance in the scan to the first visit; heroes who never visit are left out. */
    val waits: List<Int>, val newcomerWaits: List<Int>,
    val classesServedSum: Int, val classesAliveSum: Int, val classesServedDay5: Int, val classesServedDay10: Int, val classesBoughtDay10: Int,
    val startersHeroDays: Int, val startersVisits: Int, val laterHeroDays: Int, val laterVisits: Int,
    /** Highest and lowest visits per living day among heroes who lived 10 days or more; null with fewer than two such heroes. */
    val shareMax: Double?, val shareMin: Double?,
    val goldHeroes: List<Int>, val goldSum: List<Int>, val shelfHeroes: List<Int>, val cannotAfford: List<Int>,
    val firstNameDays: Int, val surnameDays: Int, val faceDays: Int, val facePairs: Int, val worstFace: Int, val fullNameRepeat: Boolean,
    val expWon: Int, val expLost: Int, val expFatal: Int,
    /** [droughtDays] here is the empty-shelf count (listed blades at End Day = 0); the G10 drought is [RunRecovery.droughtDays]. */
    val sellOutDays: Int, val droughtDays: Int,
    val sieges: List<SiegeSnap>,
    val recovery: RunRecovery,
)

/** Observes one run through [beforeEndDay] and [afterEndDay]; read-only on every state it is handed. */
class CustomerCollector(private val engine: GameEngine) {
    companion object {
        val BANDS = listOf("d1-5", "d6-10", "d11-20", "d21+")
        val CHECKPOINTS = listOf(1, 5, 10, 15)
        const val POSITIONS = 24
        private fun band(day: Int) = when { day <= 5 -> 0; day <= 10 -> 1; day <= 20 -> 2; else -> 3 }
    }

    private class Track(val classId: String, val arrival: Int) { var first = -1; var last = -1; var visits = 0; var days = 0 }

    private val cfg = engine.config
    private val tracks = HashMap<String, Track>()
    private var alive: List<Hero> = emptyList()
    private var cap = 0
    private var listedBefore = 0
    private var forecast: com.tinyblacksmith.core.battle.Battle.SiegeOutlook? = null

    private var days = 0; private var heroDays = 0; private var capDays = 0; private var lowDays = 0
    private val servedByCount = ArrayList<Int>()
    private var turnedAway = 0; private var turnedAwayDays = 0
    private val posHeroDays = IntArray(POSITIONS); private val posVisits = IntArray(POSITIONS); private val posBuys = IntArray(POSITIONS)
    private var visits = 0; private var buys = 0
    private val bandVisits = IntArray(BANDS.size); private val bandBuys = IntArray(BANDS.size)
    private var returnVisits = 0; private var gapDaysSum = 0; private var gapCount = 0
    private var classesServedSum = 0; private var classesAliveSum = 0
    private val classesDay5 = HashSet<String>(); private val classesDay10 = HashSet<String>(); private val boughtDay10 = HashSet<String>()
    private val goldHeroes = IntArray(CHECKPOINTS.size); private val goldSum = IntArray(CHECKPOINTS.size)
    private val shelfHeroes = IntArray(CHECKPOINTS.size); private val cannotAfford = IntArray(CHECKPOINTS.size)
    private var firstNameDays = 0; private var surnameDays = 0; private var faceDays = 0; private var facePairs = 0; private var worstFace = 0
    private var expWon = 0; private var expLost = 0; private var expFatal = 0
    private var sellOutDays = 0; private var droughtDays = 0
    private val sieges = ArrayList<SiegeSnap>()
    private val recovery = RecoveryProbe(engine)

    /** Call with the state the policy sees at the start of the day, before it acts (recovery states, [RecoveryProbe]). */
    fun morning(s: GameState) = recovery.morning(s)

    /** Call with the state the End Day command is about to resolve (after the day's shop actions). */
    fun beforeEndDay(s: GameState) {
        alive = s.aliveHeroes()
        val festival = s.worldFlags[WorldEvents.FLAG_FESTIVAL] == s.day
        cap = cfg.customers.shopCapacity + engine.toolTotal(s, ToolEffect.EXTRA_CUSTOMERS) + (if (festival) cfg.customers.festivalExtraSeats else 0)
        val listed = s.listedWeapons()
        listedBefore = listed.size
        for ((i, h) in alive.withIndex()) {
            tracks.getOrPut(h.id.value) { Track(h.classId.value, s.day) }.days++
            posHeroDays[minOf(i, POSITIONS - 1)]++
        }
        CHECKPOINTS.indexOf(s.day).takeIf { it >= 0 }?.let { c ->
            goldHeroes[c] += alive.size; goldSum[c] += alive.sumOf { it.gold }
            val cheapest = listed.minOfOrNull { it.listedPrice ?: Int.MAX_VALUE }
            if (cheapest != null) {
                shelfHeroes[c] += alive.size
                cannotAfford[c] += alive.count { it.gold + Market.tradeInCredit(s.equippedWeapon(it.id), cfg) < cheapest }
            }
        }
        forecast = if (s.day == s.town.nextSiegeDay) engine.siegeForecast(s) else null
    }

    /** Call with the same state, the state End Day returned and the resolution it carries. */
    fun afterEndDay(pre: GameState, out: GameState, res: DayResolution) {
        val day = pre.day
        val served = res.browsers.filter { it.heroId != null }
        days++; heroDays += alive.size
        while (servedByCount.size <= served.size) servedByCount += 0
        servedByCount[served.size]++
        if (served.size >= cap) capDays++
        if (served.size <= 2) lowDays++
        turnedAway += res.turnedAway.size
        if (res.turnedAway.isNotEmpty()) turnedAwayDays++
        if (listedBefore == 0) droughtDays++
        if (listedBefore > 0 && out.listedWeapons().isEmpty()) sellOutDays++
        val rank = alive.withIndex().associate { it.value.id.value to it.index }
        val b = band(day)
        for (v in served) {
            val t = tracks.getValue(v.heroId!!.value)
            val bought = v.purchasedWeaponId != null
            if (t.first < 0) t.first = day else { returnVisits++; gapDaysSum += day - t.last; gapCount++ }
            t.last = day; t.visits++
            visits++; bandVisits[b]++
            if (bought) { buys++; bandBuys[b]++ }
            val p = minOf(rank.getValue(v.heroId!!.value), POSITIONS - 1)
            posVisits[p]++
            if (bought) posBuys[p]++
            if (day <= 5) classesDay5 += t.classId
            if (day <= 10) { classesDay10 += t.classId; if (bought) boughtDay10 += t.classId }
        }
        val servedClasses = served.map { tracks.getValue(it.heroId!!.value).classId }.toSet()
        classesServedSum += servedClasses.size
        classesAliveSum += alive.map { it.classId.value }.toSet().size
        if (alive.groupBy { it.name }.any { it.value.size > 1 }) firstNameDays++
        if (alive.groupBy { it.surname }.any { it.value.size > 1 }) surnameDays++
        val faces = alive.groupBy { it.classId.value + "/" + Math.floorMod(it.id.value.hashCode(), 5) }.values
        val worst = faces.maxOfOrNull { it.size } ?: 0
        if (worst > 1) faceDays++
        worstFace = maxOf(worstFace, worst)
        facePairs += faces.sumOf { it.size * (it.size - 1) / 2 }
        expWon += res.events.count { it.type == EventType.ELITE_SLAIN || (it.type == EventType.EXPEDITION_WON && "material" !in it.data) }
        expLost += res.events.count { it.type == EventType.EXPEDITION_LOST }
        expFatal += res.events.count { it.type == EventType.HERO_DIED }
        res.events.firstOrNull { it.type == EventType.SIEGE_WON || it.type == EventType.SIEGE_LOST }?.let { e ->
            val f = forecast
            sieges += SiegeSnap(
                siege = pre.town.siegesSurvived + pre.town.siegesLost + 1, held = e.type == EventType.SIEGE_WON,
                pressure = f?.factionState?.pressure ?: 0, militia = pre.town.militia, armory = pre.town.armory, championPower = f?.championPowers?.sum() ?: 0.0,
                forecastDefense = f?.townDefense ?: 0.0, forecastRaid = f?.raidPower ?: 0.0,
                defense = e.data["townDefense"]?.toInt() ?: 0, raid = e.data["raidPower"]?.toInt() ?: 0,
            )
        }
    }

    /** Call once with the run's final state. */
    fun finish(last: GameState): RunCustomers {
        val all = tracks.values
        val servedTracks = all.filter { it.first >= 0 }
        val lived = all.filter { it.days >= 10 }.map { it.visits.toDouble() / it.days }
        val starters = all.filter { it.arrival == 1 }
        val later = all.filter { it.arrival > 1 }
        return RunCustomers(
            days = days, heroDays = heroDays, servedByCount = servedByCount, capDays = capDays, lowDays = lowDays,
            turnedAway = turnedAway, turnedAwayDays = turnedAwayDays,
            posHeroDays = posHeroDays.toList(), posVisits = posVisits.toList(), posBuys = posBuys.toList(),
            visits = visits, buys = buys, bandVisits = bandVisits.toList(), bandBuys = bandBuys.toList(),
            returnVisits = returnVisits, gapDaysSum = gapDaysSum, gapCount = gapCount,
            heroesEver = all.size, servedEver = servedTracks.size, servedByDay5 = servedTracks.count { it.first <= 5 }, servedByDay10 = servedTracks.count { it.first <= 10 },
            waits = servedTracks.map { it.first - it.arrival }, newcomerWaits = servedTracks.filter { it.arrival > 1 }.map { it.first - it.arrival },
            classesServedSum = classesServedSum, classesAliveSum = classesAliveSum,
            classesServedDay5 = classesDay5.size, classesServedDay10 = classesDay10.size, classesBoughtDay10 = boughtDay10.size,
            startersHeroDays = starters.sumOf { it.days }, startersVisits = starters.sumOf { it.visits }, laterHeroDays = later.sumOf { it.days }, laterVisits = later.sumOf { it.visits },
            shareMax = if (lived.size >= 2) lived.max() else null, shareMin = if (lived.size >= 2) lived.min() else null,
            goldHeroes = goldHeroes.toList(), goldSum = goldSum.toList(), shelfHeroes = shelfHeroes.toList(), cannotAfford = cannotAfford.toList(),
            firstNameDays = firstNameDays, surnameDays = surnameDays, faceDays = faceDays, facePairs = facePairs, worstFace = worstFace,
            fullNameRepeat = last.heroes.values.groupBy { it.fullName }.any { it.value.size > 1 },
            expWon = expWon, expLost = expLost, expFatal = expFatal, sellOutDays = sellOutDays, droughtDays = droughtDays, sieges = sieges, recovery = recovery.finish(),
        )
    }
}

/** One row per siege number (1 = the first): runs that fought it, share held, and the means of the snapshot. */
@Serializable
data class SiegeRow(
    val siege: Int, val runs: Int, val held: Double, val pressure: Double, val militia: Double, val armory: Double, val championPower: Double,
    val forecastDefense: Double, val forecastRaid: Double, val defense: Double, val raid: Double,
)

/** Customer and identity numbers for one policy row. Shares are 0-1. Per-day numbers divide by all resolved days of all runs. */
@Serializable
data class CustomerSummary(
    val livingHeroesPerDay: Double,
    val servedPerDay: Double, val servedDistribution: Map<Int, Double>, val daysAtCap: Double, val daysTwoOrFewer: Double,
    val turnedAwayPerDay: Double = 0.0, val daysWithTurnedAway: Double = 0.0,
    val purchasesPerDay: Double, val salesPerDay: Double, val conversion: Double, val conversionByBand: Map<String, Double>, val refusalMix: Map<String, Double>,
    /** Served visits per hero-day at each scan position (1-based key). */
    val visitRateByPosition: Map<Int, Double>,
    val heroesEverPerRun: Double, val servedEverPerRun: Double, val neverServedShare: Double,
    val servedByDay5: Double, val servedByDay10: Double, val runsWithNineServedByDay5: Double, val runsWithThirteenServed: Double,
    val waitMedian: Int, val waitP90: Int, val waitMax: Int, val newcomerWaitMedian: Int, val newcomerWaitP90: Int,
    val classesServedPerDay: Double, val classesAlivePerDay: Double, val classesServedByDay5: Double, val classesServedByDay10: Double,
    val classesBoughtByDay10: Double, val runsWithFourClassesBoughtByDay10: Double,
    val returnVisitShare: Double, val daysBetweenVisits: Double,
    val startersVisitRate: Double, val laterArrivalsVisitRate: Double,
    val servedShareMaxMean: Double, val servedShareMinMean: Double, val servedShareRatioMedian: Double, val servedShareRatioP90: Double, val runsWithAHeroNeverServed: Double,
    /** Mean gold of a living hero, and the share of living heroes who could not pay (gold plus trade-in credit) for the cheapest listed blade, at the start of days 1, 5, 10, 15. */
    val heroGold: Map<Int, Double>, val cannotAffordCheapest: Map<Int, Double>,
    val firstNameClashDays: Double, val surnameClashDays: Double, val faceClashDays: Double, val sameFacePairsPerDay: Double, val worstSameFace: Int, val fullNameRepeatRuns: Double,
    val expeditionsWonPerRun: Double, val expeditionsLostPerRun: Double, val expeditionsFatalPerRun: Double, val deathsPerHeroDay: Double,
    val sellOutDays: Double, val droughtDays: Double,
    val sieges: List<SiegeRow>,
    val recovery: RecoverySummary,
) {
    companion object {
        private fun ratio(a: Number, b: Number) = if (b.toDouble() == 0.0) 0.0 else a.toDouble() / b.toDouble()
        private fun pctl(v: List<Int>, p: Double) = if (v.isEmpty()) 0 else v.sorted()[((v.size - 1) * p).toInt()]
        private fun pctlD(v: List<Double>, p: Double) = if (v.isEmpty()) 0.0 else v.sorted()[((v.size - 1) * p).toInt()]

        /** Null when the runs carry no metrics. */
        fun of(runs: List<RunStats>): CustomerSummary? {
            val rs = runs.mapNotNull { it.customers }
            if (rs.isEmpty()) return null
            val days = rs.sumOf { it.days }
            val heroDays = rs.sumOf { it.heroDays }
            val visits = rs.sumOf { it.visits }
            val buys = rs.sumOf { it.buys }
            val histogram = TreeMap<Int, Int>()
            rs.forEach { r -> r.servedByCount.forEachIndexed { n, c -> if (c > 0) histogram.merge(n, c, Int::plus) } }
            val reasonTotals = runs.flatMap { it.visitReasons.keys }.toSortedSet().associateWith { k -> runs.sumOf { it.visitReasons[k] ?: 0 } }
            val reached10 = rs.filter { it.days >= 10 }
            val reached5 = rs.filter { it.days >= 5 }
            val ratios = rs.filter { it.shareMax != null && it.shareMin != null && it.shareMin > 0.0 }.map { it.shareMax!! / it.shareMin!! }
            val both = rs.filter { it.shareMax != null }
            fun checkpoint(f: (Int) -> Double) = CustomerCollector.CHECKPOINTS.withIndex().filter { (i, _) -> rs.sumOf { it.goldHeroes[i] } > 0 }.associate { (i, d) -> d to f(i) }
            return CustomerSummary(
                livingHeroesPerDay = ratio(heroDays, days),
                servedPerDay = ratio(visits, days), servedDistribution = histogram.mapValuesTo(TreeMap()) { ratio(it.value, days) },
                daysAtCap = ratio(rs.sumOf { it.capDays }, days), daysTwoOrFewer = ratio(rs.sumOf { it.lowDays }, days),
                turnedAwayPerDay = ratio(rs.sumOf { it.turnedAway }, days), daysWithTurnedAway = ratio(rs.sumOf { it.turnedAwayDays }, days),
                purchasesPerDay = ratio(buys, days), salesPerDay = ratio(runs.sumOf { it.weaponsSold }, days), conversion = ratio(buys, visits),
                conversionByBand = CustomerCollector.BANDS.withIndex().associate { (i, name) -> name to ratio(rs.sumOf { it.bandBuys[i] }, rs.sumOf { it.bandVisits[i] }) },
                refusalMix = reasonTotals.mapValues { ratio(it.value, visits) },
                visitRateByPosition = (0 until CustomerCollector.POSITIONS).filter { p -> rs.sumOf { it.posHeroDays[p] } > 0 }
                    .associateTo(TreeMap()) { p -> p + 1 to ratio(rs.sumOf { it.posVisits[p] }, rs.sumOf { it.posHeroDays[p] }) },
                heroesEverPerRun = rs.map { it.heroesEver }.average(), servedEverPerRun = rs.map { it.servedEver }.average(),
                neverServedShare = ratio(rs.sumOf { it.heroesEver - it.servedEver }, rs.sumOf { it.heroesEver }),
                servedByDay5 = reached5.map { it.servedByDay5 }.averageOrZero(), servedByDay10 = reached10.map { it.servedByDay10 }.averageOrZero(),
                runsWithNineServedByDay5 = ratio(reached5.count { it.servedByDay5 >= 9 }, reached5.size), runsWithThirteenServed = ratio(rs.count { it.servedEver >= 13 }, rs.size),
                waitMedian = pctl(rs.flatMap { it.waits }, 0.5), waitP90 = pctl(rs.flatMap { it.waits }, 0.9), waitMax = rs.flatMap { it.waits }.maxOrNull() ?: 0,
                newcomerWaitMedian = pctl(rs.flatMap { it.newcomerWaits }, 0.5), newcomerWaitP90 = pctl(rs.flatMap { it.newcomerWaits }, 0.9),
                classesServedPerDay = ratio(rs.sumOf { it.classesServedSum }, days), classesAlivePerDay = ratio(rs.sumOf { it.classesAliveSum }, days),
                classesServedByDay5 = reached5.map { it.classesServedDay5 }.averageOrZero(), classesServedByDay10 = reached10.map { it.classesServedDay10 }.averageOrZero(),
                classesBoughtByDay10 = reached10.map { it.classesBoughtDay10 }.averageOrZero(), runsWithFourClassesBoughtByDay10 = ratio(reached10.count { it.classesBoughtDay10 >= 4 }, reached10.size),
                returnVisitShare = ratio(rs.sumOf { it.returnVisits }, visits), daysBetweenVisits = ratio(rs.sumOf { it.gapDaysSum }, rs.sumOf { it.gapCount }),
                startersVisitRate = ratio(rs.sumOf { it.startersVisits }, rs.sumOf { it.startersHeroDays }), laterArrivalsVisitRate = ratio(rs.sumOf { it.laterVisits }, rs.sumOf { it.laterHeroDays }),
                servedShareMaxMean = both.map { it.shareMax!! }.averageOrZero(), servedShareMinMean = both.map { it.shareMin!! }.averageOrZero(),
                servedShareRatioMedian = pctlD(ratios, 0.5), servedShareRatioP90 = pctlD(ratios, 0.9),
                runsWithAHeroNeverServed = ratio(both.count { it.shareMin == 0.0 }, both.size),
                heroGold = checkpoint { i -> ratio(rs.sumOf { it.goldSum[i] }, rs.sumOf { it.goldHeroes[i] }) },
                cannotAffordCheapest = checkpoint { i -> ratio(rs.sumOf { it.cannotAfford[i] }, rs.sumOf { it.shelfHeroes[i] }) },
                firstNameClashDays = ratio(rs.sumOf { it.firstNameDays }, days), surnameClashDays = ratio(rs.sumOf { it.surnameDays }, days), faceClashDays = ratio(rs.sumOf { it.faceDays }, days),
                sameFacePairsPerDay = ratio(rs.sumOf { it.facePairs }, days), worstSameFace = rs.maxOf { it.worstFace }, fullNameRepeatRuns = ratio(rs.count { it.fullNameRepeat }, rs.size),
                expeditionsWonPerRun = rs.map { it.expWon }.average(), expeditionsLostPerRun = rs.map { it.expLost }.average(), expeditionsFatalPerRun = rs.map { it.expFatal }.average(),
                deathsPerHeroDay = ratio(runs.sumOf { it.heroDeaths }, heroDays),
                sellOutDays = ratio(rs.sumOf { it.sellOutDays }, days), droughtDays = ratio(rs.sumOf { it.droughtDays }, days),
                recovery = RecoverySummary.of(rs.map { it.recovery }),
                sieges = rs.flatMap { it.sieges }.groupBy { it.siege }.toSortedMap().map { (n, s) ->
                    SiegeRow(n, s.size, s.count { it.held }.toDouble() / s.size, s.map { it.pressure }.average(), s.map { it.militia }.average(), s.map { it.armory }.average(),
                        s.map { it.championPower }.average(), s.map { it.forecastDefense }.average(), s.map { it.forecastRaid }.average(), s.map { it.defense }.average(), s.map { it.raid }.average())
                },
            )
        }

        private fun List<Int>.averageOrZero() = if (isEmpty()) 0.0 else average()
        @JvmName("averageOrZeroD") private fun List<Double>.averageOrZero() = if (isEmpty()) 0.0 else average()
    }

    fun render(): String {
        fun pct(v: Double) = "%.1f%%".format(100.0 * v)
        fun f2(v: Double) = "%.2f".format(v)
        return buildString {
            appendLine("  customers: living heroes/day=${f2(livingHeroesPerDay)}  served/day=${f2(servedPerDay)}  at cap=${pct(daysAtCap)} of days  two or fewer=${pct(daysTwoOrFewer)}  purchases/day=${f2(purchasesPerDay)}  sales/day=${f2(salesPerDay)}  conversion=${pct(conversion)}")
            appendLine("    served per day: " + servedDistribution.entries.joinToString("  ") { "${it.key}:${pct(it.value)}" })
            appendLine("    turned away (willing, shop full): ${f2(turnedAwayPerDay)} a day, someone on ${pct(daysWithTurnedAway)} of days")
            appendLine("    conversion by day band: " + conversionByBand.entries.joinToString("  ") { "${it.key}=${pct(it.value)}" } + "   refusal mix (of visits): " + refusalMix.entries.joinToString("  ") { "${it.key}=${pct(it.value)}" })
            appendLine("    visit rate by scan position: " + visitRateByPosition.entries.joinToString("  ") { "${it.key}:${pct(it.value)}" })
            appendLine("    heroes ever/run=${f2(heroesEverPerRun)}  served/run=${f2(servedEverPerRun)}  never served=${pct(neverServedShare)}  served by day 5=${f2(servedByDay5)}  by day 10=${f2(servedByDay10)}  runs with 9 by day 5=${pct(runsWithNineServedByDay5)}  with 13 in a run=${pct(runsWithThirteenServed)}")
            appendLine("    wait to first visit (days): median=$waitMedian p90=$waitP90 max=$waitMax  newcomers only: median=$newcomerWaitMedian p90=$newcomerWaitP90")
            appendLine("    classes: served/day=${f2(classesServedPerDay)} of alive/day=${f2(classesAlivePerDay)}  served by day 5=${f2(classesServedByDay5)}, by day 10=${f2(classesServedByDay10)}  bought by day 10=${f2(classesBoughtByDay10)}  runs with 4+ classes bought by day 10=${pct(runsWithFourClassesBoughtByDay10)}")
            appendLine("    return-visit share=${pct(returnVisitShare)}  days between visits=${f2(daysBetweenVisits)}  visits per hero-day: starting cast=${f2(startersVisitRate)} later arrivals=${f2(laterArrivalsVisitRate)}")
            appendLine("    served share among heroes alive 10+ days: max mean=${f2(servedShareMaxMean)} min mean=${f2(servedShareMinMean)} max/min median=${f2(servedShareRatioMedian)} p90=${f2(servedShareRatioP90)}  runs with a hero never served=${pct(runsWithAHeroNeverServed)}")
            appendLine("    hero gold at the start of day: " + heroGold.entries.joinToString("  ") { "${it.key}=${"%.0f".format(it.value)}" } + "   cannot pay for the cheapest listed blade: " + cannotAffordCheapest.entries.joinToString("  ") { "${it.key}=${pct(it.value)}" })
            appendLine("    names: first name shared=${pct(firstNameClashDays)} of days  surname shared=${pct(surnameClashDays)}  face shared=${pct(faceClashDays)} (pairs/day=${f2(sameFacePairsPerDay)}, worst=$worstSameFace)  full name repeats in ${pct(fullNameRepeatRuns)} of runs")
            appendLine("    expeditions/run: won=${f2(expeditionsWonPerRun)} lost=${f2(expeditionsLostPerRun)} fatal=${f2(expeditionsFatalPerRun)}  deaths per hero-day=${"%.4f".format(deathsPerHeroDay)}  sell-out days=${pct(sellOutDays)}  empty-shelf days=${pct(droughtDays)}")
            append(recovery.render())
            if (sieges.isNotEmpty()) appendLine("    sieges (n, held, pressure, militia, armory, champions, forecast def/raid, def/raid): " + sieges.joinToString("; ") {
                "#${it.siege} n=${it.runs} ${pct(it.held)} p=${"%.0f".format(it.pressure)} m=${"%.0f".format(it.militia)} a=${"%.0f".format(it.armory)} c=${"%.0f".format(it.championPower)} f=${"%.0f".format(it.forecastDefense)}/${"%.0f".format(it.forecastRaid)} r=${"%.0f".format(it.defense)}/${"%.0f".format(it.raid)}"
            })
        }
    }
}

/*
 * Recovery (plan 4.7, G10): three states told apart, read from the morning state before the policy acts, so a number
 * describes the position the player is in and not what the bot then did.
 *   resting - could act and chose not to (not measured here: it depends on the policy, not on the position);
 *   drought - no possible sale today, but a legal forge exists;
 *   stuck   - no possible sale today and no legal forge.
 * A possible sale is a blade in the shop (shelf or storage) that some living hero could afford at the going rate
 * (`suggestedPrice`) and would be stronger with (`Market.evaluate`: affordable and improvement > 0, neutral noise), or a
 * blade that closes an offered or accepted commission. A legal forge is a Quick forge: energy (with the day's overwork
 * allowance) for it, and a core and an augment each on hand or buyable in stock with the gold on hand. Salvage, hone and
 * donating are not counted as ways out. The driver's `hardLocks` is a different, older count (see [RunStats.hardLockDays]).
 */

/** One run's recovery counters; [days] is the number of mornings observed. */
@Serializable
data class RunRecovery(val days: Int, val stuckDays: Int, val droughtDays: Int, val longestStuckStreak: Int)

/** Classifies each morning as sale-possible, drought or stuck. Read-only. */
class RecoveryProbe(private val engine: GameEngine) {
    private var days = 0; private var stuckDays = 0; private var droughtDays = 0; private var streak = 0; private var longest = 0

    /** Call with the day's opening state. A drought day ends a stuck streak, like a day with a possible sale. */
    fun morning(s: GameState) {
        days++
        if (canSell(s)) { streak = 0; return }
        if (canForge(s)) { droughtDays++; streak = 0; return }
        stuckDays++; streak++; longest = maxOf(longest, streak)
    }

    fun finish() = RunRecovery(days, stuckDays, droughtDays, longest)

    internal fun canForge(s: GameState): Boolean {
        val cfg = engine.config
        if (s.energy + (cfg.maxOverworkPerDay - s.overworkToday) < cfg.quickForgeEnergy) return false
        fun cheapest(category: MaterialCategory): Int? = engine.content.materials(category).mapNotNull { m ->
            when {
                (s.materials[m.id] ?: 0) > 0 -> 0
                (s.supplierStock[m.id] ?: Int.MAX_VALUE) > 0 -> engine.materialPrice(s, m.id)
                else -> null
            }
        }.minOrNull()
        val core = cheapest(MaterialCategory.CORE) ?: return false
        val augment = cheapest(MaterialCategory.AUGMENT) ?: return false
        return core + augment <= s.gold
    }

    internal fun canSell(s: GameState): Boolean {
        val cfg = engine.config
        val stock = s.weapons.values.filter { it.isListed || it.isInStorage }
        if (stock.isEmpty()) return false
        val open = s.commissions.values.filter { it.status == CommissionStatus.OFFERED || it.status == CommissionStatus.ACCEPTED }
        if (open.any { c -> s.heroes[c.buyerId]?.isAlive == true && Commissions.pick(stock, c, cfg) != null }) return true
        val ctx = ResolutionContext(s, engine.content, cfg)
        val alive = s.aliveHeroes()
        return stock.any { w ->
            val offer = w.copy(location = WeaponLocation.Shelf(Market.askingPrice(w, cfg)))
            alive.any { h -> Market.evaluate(ctx, h, ctx.equippedWeapon(h.id), offer, 0.5).let { it.affordable && it.improvement > 0 } }
        }
    }
}

/** Recovery numbers for one policy row; shares are 0-1 of runs or of observed mornings. */
@Serializable
data class RecoverySummary(
    val stuckDayShare: Double, val droughtDayShare: Double, val stuckDaysPerRun: Double, val droughtDaysPerRun: Double,
    val runsWithStuckDay: Double, val runsWithStreak2: Double, val runsWithStreak3: Double, val runsWithStreak5: Double, val longestStuckStreak: Int,
) {
    companion object {
        private fun share(rs: List<RunRecovery>, f: (RunRecovery) -> Boolean) = if (rs.isEmpty()) 0.0 else rs.count(f).toDouble() / rs.size

        fun of(rs: List<RunRecovery>): RecoverySummary {
            val days = rs.sumOf { it.days }.coerceAtLeast(1)
            return RecoverySummary(
                stuckDayShare = rs.sumOf { it.stuckDays }.toDouble() / days, droughtDayShare = rs.sumOf { it.droughtDays }.toDouble() / days,
                stuckDaysPerRun = rs.map { it.stuckDays }.average(), droughtDaysPerRun = rs.map { it.droughtDays }.average(),
                runsWithStuckDay = share(rs) { it.stuckDays > 0 }, runsWithStreak2 = share(rs) { it.longestStuckStreak >= 2 },
                runsWithStreak3 = share(rs) { it.longestStuckStreak >= 3 }, runsWithStreak5 = share(rs) { it.longestStuckStreak >= 5 },
                longestStuckStreak = rs.maxOfOrNull { it.longestStuckStreak } ?: 0,
            )
        }
    }

    fun render(): String {
        fun pct(v: Double) = "%.2f%%".format(100.0 * v)
        return "    recovery (morning state): stuck days=${pct(stuckDayShare)} of days (${"%.3f".format(stuckDaysPerRun)}/run)  drought days=${pct(droughtDayShare)} (${"%.3f".format(droughtDaysPerRun)}/run)" +
            "  runs with a stuck day=${pct(runsWithStuckDay)}  stuck streak 2+ days=${pct(runsWithStreak2)}  3+ days=${pct(runsWithStreak3)}  5+ days=${pct(runsWithStreak5)}  longest=$longestStuckStreak\n"
    }
}
