import com.tinyblacksmith.core.content.MaterialCategory
import com.tinyblacksmith.core.content.ToolEffect
import com.tinyblacksmith.core.engine.Command
import com.tinyblacksmith.core.engine.CommandOutcome
import com.tinyblacksmith.core.engine.GameEngine
import com.tinyblacksmith.core.model.*
import java.util.Locale

/** Scratch baseline (not project code): how the capped shelf-visit scan treats heroes by ID order, and identity variety. */
fun f2(x: Double) = "%.2f".format(Locale.ROOT, x)
fun pct(a: Long, b: Long) = if (b == 0L) "-" else "%.1f%%".format(Locale.ROOT, 100.0 * a / b)

fun main(args: Array<String>) {
    val runs = args.getOrNull(0)?.toInt() ?: 500
    val buySignboard = args.getOrNull(1) == "signboard"
    fun arg(k: String) = args.firstOrNull { it.startsWith("$k=") }?.substringAfter("=")
    var cfg = com.tinyblacksmith.core.config.BalanceConfig.DEFAULT
    arg("pop")?.let { cfg = cfg.copy(startingHeroCount = it.toInt()) }
    arg("min")?.let { cfg = cfg.copy(minHeroPopulation = it.toInt()) }
    arg("max")?.let { cfg = cfg.copy(maxHeroPopulation = it.toInt()) }
    arg("cap")?.let { cfg = cfg.copy(maxCustomersPerDay = it.toInt()) }
    arg("visit")?.let { cfg = cfg.copy(baseVisitChance = it.toDouble()) }
    arg("raidPerDay")?.let { cfg = cfg.copy(raidPerDay = it.toDouble()) }
    arg("expSupp")?.let { cfg = cfg.copy(expeditionSuppression = it.toInt()) }
    val engine = GameEngine(config = cfg)
    println("config: pop=${cfg.startingHeroCount} min=${cfg.minHeroPopulation} max=${cfg.maxHeroPopulation} cap=${cfg.maxCustomersPerDay} visit=${cfg.baseVisitChance} raidPerDay=${cfg.raidPerDay} expSupp=${cfg.expeditionSuppression}")
    val content = engine.content
    val cores = content.materials.filter { it.category == MaterialCategory.CORE }.sortedBy { it.tier }
    val augments = content.materials.filter { it.category == MaterialCategory.AUGMENT }.sortedBy { it.price }
    val rankVisits = LongArray(40); val rankDays = LongArray(40); val rankBuys = LongArray(40)
    val visitsPerDay = HashMap<Int, Int>()
    var days = 0L; var capDays = 0L
    var uniqueVisitorsSum = 0L; var heroesEverSum = 0L; var neverVisited = 0L
    val firstVisitWait = ArrayList<Int>()
    var classesServedSum = 0L; var classesAliveSum = 0L
    var aliveSum = 0L
    var firstNameClashDays = 0L; var fullNameClashRuns = 0L; var surnameShareDays = 0L
    var portraitDupDays = 0L; var maxSamePortrait = 0; var samePortraitPairsSum = 0L
    var returnVisits = 0L; var totalVisits = 0L; var totalBuys = 0L
    val daysSurvived = ArrayList<Int>()
    for (r in 0 until runs) {
        var s = engine.newRun(LegacyProfile(), 1000L + r)
        val arrival = HashMap<String, Int>(); val firstVisit = HashMap<String, Int>()
        var fam = 0
        var fullClash = false
        while (!s.isEnded && s.day <= 400) {
            for (h in s.aliveHeroes()) arrival.putIfAbsent(h.id.value, s.day)
            if (buySignboard) {
                val o = engine.handle(s, Command.BuyTool("signboard")); if (o is CommandOutcome.Accepted) s = o.state
            }
            // A plain fair smith: restock the cheapest core and augment when out, forge Quick/Balanced until energy runs out, list at the suggested price.
            var guard = 0
            while (guard++ < 12) {
                val core = cores.firstOrNull { (s.materials[it.id] ?: 0) > 0 }
                val aug = augments.firstOrNull { (s.materials[it.id] ?: 0) > 0 }
                if (core == null) { val o = engine.handle(s, Command.BuyMaterial(cores.first().id, 1)); if (o is CommandOutcome.Accepted) { s = o.state; continue } else break }
                if (aug == null) { val o = engine.handle(s, Command.BuyMaterial(augments.first().id, 1)); if (o is CommandOutcome.Accepted) { s = o.state; continue } else break }
                if (s.energy < engine.config.quickForgeEnergy) break
                val f = content.families[fam++ % content.families.size]
                val o = engine.handle(s, Command.Forge(ForgeMode.QUICK, f.id, core.id, aug.id, null, Risk.BALANCED))
                if (o is CommandOutcome.Accepted) s = o.state else break
            }
            for (w in s.storedWeapons().sortedByDescending { it.power }) {
                if (s.listedWeapons().size >= engine.shelfSlots(s)) break
                val o = engine.handle(s, Command.ToggleShelf(w.id, true, null)); if (o is CommandOutcome.Accepted) s = o.state
            }
            val alive = s.aliveHeroes()
            val rankOf = alive.withIndex().associate { it.value.id.value to it.index }
            aliveSum += alive.size
            for (i in alive.indices) rankDays[i]++
            if (alive.groupBy { it.name }.any { it.value.size > 1 }) firstNameClashDays++
            if (alive.groupBy { it.surname }.any { it.value.size > 1 }) surnameShareDays++
            if (s.heroes.values.groupBy { it.fullName }.any { it.value.size > 1 }) fullClash = true
            val looks = alive.groupBy { it.classId.value + "/" + Math.floorMod(it.id.value.hashCode(), 5) }
            val worst = looks.values.maxOfOrNull { it.size } ?: 0
            if (worst > 1) portraitDupDays++
            if (worst > maxSamePortrait) maxSamePortrait = worst
            samePortraitPairsSum += looks.values.sumOf { it.size * (it.size - 1) / 2 }
            val day = s.day
            val cap = engine.config.maxCustomersPerDay + engine.toolTotal(s, ToolEffect.EXTRA_CUSTOMERS)
            val out = engine.handle(s, Command.EndDay(CommandId("${s.runId.value}:day${s.day}"))) as CommandOutcome.Accepted
            val visits = out.resolution!!.visits
            days++
            visitsPerDay.merge(visits.size, 1, Int::plus)
            if (visits.size >= cap) capDays++
            for (v in visits) {
                totalVisits++
                if (v.purchasedWeaponId != null) totalBuys++
                if (firstVisit.containsKey(v.heroId.value)) returnVisits++
                val rk = rankOf[v.heroId.value] ?: continue
                rankVisits[rk]++; if (v.purchasedWeaponId != null) rankBuys[rk]++
                firstVisit.putIfAbsent(v.heroId.value, day)
            }
            classesServedSum += visits.mapNotNull { v -> alive.firstOrNull { it.id == v.heroId }?.classId?.value }.toSet().size
            classesAliveSum += alive.map { it.classId.value }.toSet().size
            s = out.state
        }
        daysSurvived += s.day
        heroesEverSum += arrival.size; uniqueVisitorsSum += firstVisit.size; neverVisited += arrival.size - firstVisit.size
        for ((id, d) in firstVisit) firstVisitWait += d - (arrival[id] ?: d)
        if (fullClash) fullNameClashRuns++
    }
    daysSurvived.sort(); firstVisitWait.sort()
    println("survival p10=${daysSurvived[daysSurvived.size / 10]} median=${daysSurvived[daysSurvived.size / 2]} mean=${f2(daysSurvived.average())} p90=${daysSurvived[daysSurvived.size * 9 / 10]} max=${daysSurvived.last()}")
    println("runs=$runs signboard=$buySignboard days=$days median survival=${daysSurvived[daysSurvived.size / 2]} mean alive heroes/day=${f2(aliveSum.toDouble() / days)}")
    println("visits/day distribution: " + visitsPerDay.toSortedMap().entries.joinToString("  ") { "${it.key}:${pct(it.value.toLong(), days)}" } + "  mean=" + f2(visitsPerDay.entries.sumOf { it.key * it.value }.toDouble() / days))
    println("days at the customer cap: ${pct(capDays, days)}; buys/day=${f2(totalBuys.toDouble() / days)}; buy share of visits=${pct(totalBuys, totalVisits)}; return-visit share=${pct(returnVisits, totalVisits)}")
    println("visit rate by position in the ID-sorted scan (visits / days a hero stood at that position), buy rate in brackets:")
    for (i in 0 until 14) if (rankDays[i] > 0) println("  pos ${i + 1}: ${pct(rankVisits[i], rankDays[i])} [buy ${pct(rankBuys[i], rankDays[i])}] over ${rankDays[i]} hero-days")
    println("unique visitors/run=${f2(uniqueVisitorsSum.toDouble() / runs)} of heroes ever in town/run=${f2(heroesEverSum.toDouble() / runs)}; heroes who never visited: ${pct(neverVisited, heroesEverSum)}")
    println("wait from arrival to first visit (days): median=${firstVisitWait[firstVisitWait.size / 2]} p90=${firstVisitWait[firstVisitWait.size * 9 / 10]} max=${firstVisitWait.last()}")
    println("classes served/day=${f2(classesServedSum.toDouble() / days)} of classes alive/day=${f2(classesAliveSum.toDouble() / days)}")
    println("names: days with two living heroes sharing a first name=${pct(firstNameClashDays, days)}; sharing a surname=${pct(surnameShareDays, days)}; runs with a repeated full name=${pct(fullNameClashRuns, runs.toLong())}")
    println("portraits (class + id-hash mod 5): days with two living heroes on one face=${pct(portraitDupDays, days)}; same-face pairs/day=${f2(samePortraitPairsSum.toDouble() / days)}; worst=$maxSamePortrait heroes on one face")
}
