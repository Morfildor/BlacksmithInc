package com.tinyblacksmith.core.sim

import com.tinyblacksmith.core.combat.EventKind
import com.tinyblacksmith.core.config.BalanceConfig
import com.tinyblacksmith.core.content.CombatContent
import com.tinyblacksmith.core.content.ContentCatalog
import com.tinyblacksmith.core.content.LaunchContent
import com.tinyblacksmith.core.content.MaterialCategory
import com.tinyblacksmith.core.content.MaterialDef
import com.tinyblacksmith.core.crafting.Bench
import com.tinyblacksmith.core.crafting.Capabilities
import com.tinyblacksmith.core.crafting.WeaponRules
import com.tinyblacksmith.core.model.HeroClassId
import com.tinyblacksmith.core.model.MaterialId
import com.tinyblacksmith.core.model.WeaponFamilyId

/**
 * Ingredient questions answered with fights (`./gradlew :core:combat --args="lab seeds=11..13"`). Where the sweep asks
 * "does it work", this asks "is it worth choosing":
 *  1. What a metal's rule is worth by itself: the same weapon at the same quality, forged before the metals had rules
 *     and since, with and without what the rule waits for.
 *  2. The choice at the anvil: each metal at the quality it centres on, against the cheaper ones.
 *  3. Whether one augment and catalyst pair is the answer everywhere.
 *  4. Where each augment is a good pick.
 * Bench fights: made-up fighters, one companion at most, fixed lines. They rank ingredients against each other. They do
 * not say how a run goes: the guild simulator does that. Other numbers for the metal rules can be tried without a
 * change to the catalog: `iron=4 bronze=7 silver=2 obsidian=115 starsteel=4 moonsteel=4`, and `oil=chill` (Dragon Oil as it was), `chill=25`
 * (the share Chill takes off a strike), `ashclaw=GRAVE` (a what-if on what the Ashclaw are weak to), `crafts=2` (skip table 1).
 */
object ForgeLab {
    class Stage(val id: String, val about: String, val qualityShift: Int, val setting: (Long) -> Bench.Setting)

    val stages = listOf(
        Stage("early", "a level 2 wielder, the line as written, quality where the materials centre", 0) { Bench.Setting(it, 2, 100) },
        Stage("late", "a level 5 wielder, the line at 140%, quality 15 higher", 15) { Bench.Setting(it, 5, 140) },
        Stage("hold", "as early, but the party only has to be standing after 4 rounds", 0) { Bench.Setting(it, 2, 100, 4) },
    )

    /** [prepared]: the evaluator finds nothing missing for the metal's rule in this company. [fired] and [cracked] are read off the fight. */
    data class Fought(
        val family: WeaponFamilyId, val core: MaterialId, val augment: MaterialId, val catalyst: MaterialId?, val wielder: HeroClassId, val company: String, val target: String,
        val stage: String, val seed: Long, val craft: Int, val score: Int, val won: Boolean, val fired: Boolean, val prepared: Boolean, val cracked: Boolean,
    )

    /** [oilAnswersChill]: Dragon Oil as it was, answering Chill with Burn as well. [ashclawWeak]: what the Ashclaw are weak to, in place of frost (a what-if for the owner, never the catalog). */
    fun content(numbers: CombatContent.CoreNumbers, oilAnswersChill: Boolean = false, ashclawWeak: com.tinyblacksmith.core.content.Element? = null): ContentCatalog = LaunchContent.catalog.let { c ->
        val combat = c.combat!!
        c.copy(combat = combat.copy(
            cores = CombatContent.cores(numbers),
            revisions = if (oilAnswersChill) combat.revisions - CombatContent.SPREADING.id else combat.revisions,
            units = if (ashclawWeak == null) combat.units else combat.units.map { if (it.id.startsWith("ashclaw") || it.id == "warlord_krag") it.copy(weakTo = ashclawWeak) else it },
        ))
    }

    fun run(content: ContentCatalog, config: BalanceConfig, seeds: List<Long>, crafts: List<Int> = listOf(1, 2)): List<Fought> {
        val combat = content.combat!!
        val cores = content.materials(MaterialCategory.CORE)
        val augments = content.materials(MaterialCategory.AUGMENT)
        val catalysts = listOf<MaterialId?>(null) + content.materials(MaterialCategory.CATALYST).map { it.id }
        fun centre(f: WeaponFamilyId, core: MaterialDef, aug: MaterialDef, cat: MaterialId?) =
            config.qualityBase + config.qualityPerCoreTier * core.tier + config.qualityPerAugmentTier * aug.tier + (content.coreAugmentAffinity[core.id to aug.id] ?: 0) + (content.augmentFamilyAffinity[aug.id to f] ?: 0) +
                (if (cat != null) config.catalystQualityBonus else 0)
        val recipes = content.families.flatMap { f -> cores.flatMap { core -> augments.flatMap { aug -> catalysts.map { cat -> listOf(f.id, core, aug, cat) } } } }
        return recipes.parallelStream().flatMap { r ->
            val family = r[0] as WeaponFamilyId; val core = r[1] as MaterialDef; val aug = r[2] as MaterialDef; val cat = r[3] as MaterialId?
            val ruleId = combat.coreById[core.id]?.effects?.firstOrNull()?.id
            val out = ArrayList<Fought>()
            for (stage in stages) for (craft in crafts) {
                val weapon = Bench.demonstration(family, core.id, aug.id, cat, content, config, centre(family, core, aug, cat) + stage.qualityShift).copy(craft = craft)
                val today = WeaponRules.of(weapon.copy(craft = WeaponRules.CRAFT), content)
                for (cls in content.classes.filter { (content.family(family).classFit[it.id] ?: 0.0) >= 1.0 }) for (c in Bench.companions) for (t in Bench.targets) {
                    // Judged on the weapon as forged today, whichever of the pair is fighting. Silver's rule waits for nothing, but its Charge is worth something only where a rule spends it.
                    val company = Bench.company(cls.id, c.id, t.id, content, config)
                    val prepared = if (core.id == LaunchContent.SILVER) Capabilities.evaluate(com.tinyblacksmith.core.crafting.Query.Spends(com.tinyblacksmith.core.crafting.Res.CHARGE), today, company) is com.tinyblacksmith.core.crafting.Verdict.Available
                        else ruleId != null && ruleId !in Capabilities.missing(today, company)
                    for (seed in seeds) {
                        val report = Bench.run(weapon, true, cls.id, c.id, t.id, content, config, setting = stage.setting(seed))
                        out += Fought(family, core.id, aug.id, cat, cls.id, c.id, t.id, stage.id, seed, craft, ForgeSweep.score(report), report.result.won,
                            report.fired.any { it.rule.id == ruleId }, prepared, report.result.events.any { it.kind == EventKind.FRACTURED && it.source == report.wielderKey })
                    }
                }
            }
            out.stream()
        }.toList()
    }

    private fun List<Int>.mean(): Double = if (isEmpty()) 0.0 else sum().toDouble() / size
    private fun pct(n: Int, of: Int): String = if (of == 0) "   -" else "%3d%%".format(n * 100 / of)

    fun print(args: Set<String>, out: Appendable = System.out) {
        val a = args.filter { "=" in it }.associate { it.substringBefore("=") to it.substringAfter("=") }
        val seeds = (a["seeds"] ?: "11..13").split("..").let { (it[0].toLong()..it.getOrElse(1) { _ -> it[0] }.toLong()).toList() }
        val d = CombatContent.CoreNumbers()
        val numbers = CombatContent.CoreNumbers(a["iron"]?.toInt() ?: d.ironGuard, a["bronze"]?.toInt() ?: d.bronzeAnswer, a["silver"]?.toInt() ?: d.silverCharge, a["obsidian"]?.toInt() ?: d.obsidianStrike, a["starsteel"]?.toInt() ?: d.starsteelBonus, a["moonsteel"]?.toInt() ?: d.moonsteelBonus)
        val content = content(numbers, a["oil"] == "chill", a["ashclaw"]?.let { com.tinyblacksmith.core.content.Element.valueOf(it) })
        val config = BalanceConfig.DEFAULT.let { c -> a["chill"]?.toInt()?.let { n -> c.copy(guild = c.guild.copy(fight = c.guild.fight.copy(chillPercent = n))) } ?: c }
        val fights = run(content, config, seeds, if (a["crafts"] == "2") listOf(2) else listOf(1, 2))
        val cores = content.materials(MaterialCategory.CORE)
        val augments = content.materials(MaterialCategory.AUGMENT)
        fun name(id: MaterialId?) = id?.let { content.material(it).name } ?: "No catalyst"
        out.appendLine("FORGE LAB: ${fights.size} bench fights, seeds ${seeds.first()} to ${seeds.last()}, metal numbers $numbers${a.filterKeys { it in setOf("oil", "ashclaw", "chill") }.entries.joinToString("") { ", ${it.key}=${it.value}" }}")
        stages.forEach { out.appendLine("  ${it.id}: ${it.about}") }
        out.appendLine("Score: share of the enemy line's health removed (0 to 100), plus for a win the share of the party's health left (0 to 100).")
        out.appendLine()

        out.appendLine("1. THE METAL'S RULE BY ITSELF: the same weapon at the same quality, forged since (with the rule) minus forged before (without)")
        out.appendLine("   prepared = the evaluator finds everything the rule waits for in that company. Score change is a mean. Wins: fights won with the rule that were lost without, minus the reverse, per 100 fights.")
        out.appendLine("%-10s %-6s %5s %9s | %8s %8s %7s | %8s %8s %7s".format("metal", "stage", "price", "prepared", "fires", "score", "wins", "fires", "score", "wins"))
        out.appendLine("%-10s %-6s %5s %9s | %25s | %25s".format("", "", "", "", "when prepared", "when not prepared"))
        // Only recipes whose other rules are the same before and since: a catalyst the catalog has rewritten would be measured along with the metal.
        val rewritten = content.combat!!.catalystEffects.filterValues { es -> es.any { it.id in content.combat.revisions } }.keys
        out.appendLine("   Recipes with ${rewritten.joinToString(" or ") { name(it) }} are left out here: their own rules differ between the two.")
        val pairs = fights.filter { it.catalyst !in rewritten }.groupBy { it.copy(craft = 0, score = 0, won = false, fired = false, cracked = false) }.values.filter { it.size == 2 }.map { p -> p.first { it.craft == 1 } to p.first { it.craft == 2 } }
        for (core in cores) for (stage in stages) {
            val mine = pairs.filter { it.second.core == core.id && it.second.stage == stage.id }
            fun cell(ps: List<Pair<Fought, Fought>>) = if (ps.isEmpty()) "%8s %8s %7s".format("-", "-", "-") else
                "%8s %+8.1f %+7.1f".format(pct(ps.count { it.second.fired }, ps.size), ps.map { it.second.score - it.first.score }.mean(), (ps.count { it.second.won && !it.first.won } - ps.count { !it.second.won && it.first.won }) * 100.0 / ps.size)
            val (ready, not) = mine.partition { it.second.prepared }
            out.appendLine("%-10s %-6s %5d %9s | %s | %s".format(core.name, stage.id, core.price, pct(ready.size, mine.size), cell(ready), cell(not)))
        }
        out.appendLine()
        out.appendLine("   Obsidian is a strike bonus that cracks: how often it cracked, and the score change where it did and did not")
        for (stage in stages) {
            val mine = pairs.filter { it.second.core == LaunchContent.OBSIDIAN && it.second.stage == stage.id }
            val (cr, ok) = mine.partition { it.second.cracked }
            out.appendLine("   %-6s cracked %s | cracked %+5.1f | whole %+5.1f | by wielder: %s".format(stage.id, pct(cr.size, mine.size), cr.map { it.second.score - it.first.score }.mean(), ok.map { it.second.score - it.first.score }.mean(),
                mine.groupBy { it.second.wielder }.entries.joinToString(", ") { (k, v) -> "${k.value} ${pct(v.count { it.second.cracked }, v.size).trim()} ${"%+.1f".format(v.map { it.second.score - it.first.score }.mean())}" }))
        }
        out.appendLine()
        out.appendLine("   Where a rule is prepared (share of that metal's fights, early stage), by company, wielder and catalyst: what the preparation costs")
        for (core in cores) {
            val mine = fights.filter { it.core == core.id && it.craft == 2 && it.stage == "early" }
            fun by(label: String, key: (Fought) -> String) = "$label " + mine.groupBy(key).entries.joinToString(" ") { (k, v) -> "$k ${pct(v.count { it.prepared }, v.size).trim()}" }
            out.appendLine("   %-10s %s".format(core.name, by("company:", { it.company })))
            out.appendLine("   %-10s %s | %s".format("", by("wielder:", { it.wielder.value }), by("catalyst:", { name(it.catalyst) })))
        }
        out.appendLine()

        val now = fights.filter { it.craft == 2 }
        out.appendLine("2. THE CHOICE AT THE ANVIL: each metal at the quality it centres on, same family, augment, catalyst, wielder, company, line, stage and seed")
        out.appendLine("   competitive = within 5 of the best metal. beats cheaper = scores more than every cheaper metal. a cheaper one is as good = some cheaper metal scores at least as much.")
        out.appendLine("%-10s %-6s %5s %6s %6s %12s %13s %22s %22s".format("metal", "stage", "price", "mean", "wins", "competitive", "beats cheaper", "a cheaper is as good", "competitive, prepared"))
        val groups = now.groupBy { listOf(it.family, it.augment, it.catalyst, it.wielder, it.company, it.target, it.stage, it.seed) }.values
        for (core in cores) for (stage in stages) {
            val gs = groups.filter { it.first().stage == stage.id }
            val mine = gs.map { g -> g.first { it.core == core.id } to g }
            val cheaper = cores.filter { it.price < core.price }.map { it.id }.toSet()
            val ready = mine.filter { it.first.prepared }
            out.appendLine("%-10s %-6s %5d %6.0f %6s %12s %13s %22s %22s".format(core.name, stage.id, core.price, mine.map { it.first.score }.mean(), pct(mine.count { it.first.won }, mine.size),
                pct(mine.count { (m, g) -> g.maxOf { it.score } - m.score <= 5 }, mine.size),
                if (cheaper.isEmpty()) "   -" else pct(mine.count { (m, g) -> g.filter { it.core in cheaper }.all { it.score < m.score } }, mine.size),
                if (cheaper.isEmpty()) "   -" else pct(mine.count { (m, g) -> g.any { it.core in cheaper && it.score >= m.score } }, mine.size),
                pct(ready.count { (m, g) -> g.maxOf { it.score } - m.score <= 5 }, ready.size)))
        }
        out.appendLine()

        out.appendLine("3. ONE PAIR EVERYWHERE? Share of scenarios (wielder, company, line, stage, seed) in which a recipe with this augment and catalyst is in the top tenth of all recipes fought there, averaged over its families and metals")
        val scenarios = now.groupBy { listOf(it.wielder, it.company, it.target, it.stage, it.seed) }.values
        val topKeys = HashSet<Fought>()
        for (fs in scenarios) { val cut = fs.map { it.score }.sortedDescending()[fs.size / 10]; fs.filterTo(topKeys) { it.score >= cut } }
        val catalysts = listOf<MaterialId?>(null) + content.materials(MaterialCategory.CATALYST).map { it.id }
        fun share(fs: List<Fought>) = pct(fs.count { it in topKeys }, fs.size)
        out.appendLine("%-13s".format("") + catalysts.joinToString("") { "%16s".format(name(it)) } + "   recipe price, iron")
        for (aug in augments) out.appendLine("%-13s".format(aug.name) + catalysts.joinToString("") { cat -> "%16s".format(share(now.filter { it.augment == aug.id && it.catalyst == cat })) } +
            "   ${content.material(LaunchContent.IRON).price + aug.price} to ${content.material(LaunchContent.IRON).price + aug.price + content.materials(MaterialCategory.CATALYST).maxOf { it.price }}")
        out.appendLine()
        val pair = now.filter { it.augment == LaunchContent.FROST_BLOOM && it.catalyst == LaunchContent.DRAGON_OIL }
        out.appendLine("   Frost Bloom with Dragon Oil (${content.material(LaunchContent.FROST_BLOOM).price} + ${content.material(LaunchContent.DRAGON_OIL).price} gold over the metal, an Advanced forge), top-tenth share by:")
        for ((label, key) in listOf<Pair<String, (Fought) -> String>>("stage" to { it.stage }, "company" to { it.company }, "line" to { it.target }, "family" to { it.family.value }, "metal" to { name(it.core) }, "wielder" to { it.wielder.value }))
            out.appendLine("   %-8s %s".format(label, pair.groupBy(key).entries.joinToString("  ") { (k, v) -> "$k ${share(v).trim()}" }))
        // Against what else the same gold and the same wielder could carry: the best cheaper recipe in the same scenario.
        val byScenario = now.groupBy { listOf(it.wielder, it.company, it.target, it.stage, it.seed) }
        fun price(f: Fought) = content.material(f.core).price + content.material(f.augment).price + (f.catalyst?.let { content.material(it).price } ?: 0)
        var matched = 0; var beaten = 0
        for (f in pair) { val others = byScenario.getValue(listOf(f.wielder, f.company, f.target, f.stage, f.seed)).filter { it.family == f.family && price(it) < price(f) && !(it.augment == f.augment && it.catalyst == f.catalyst) }; if (others.any { it.score >= f.score - 5 }) matched++; if (others.any { it.score > f.score }) beaten++ }
        out.appendLine("   A cheaper recipe of the same family, same wielder and scenario, is within 5 of it in ${pct(matched, pair.size).trim()} of its fights and beats it in ${pct(beaten, pair.size).trim()}.")
        out.appendLine("   Mean score: Frost Bloom + Dragon Oil ${"%.0f".format(pair.map { it.score }.mean())}, Frost Bloom alone ${"%.0f".format(now.filter { it.augment == LaunchContent.FROST_BLOOM && it.catalyst == null }.map { it.score }.mean())}, all recipes ${"%.0f".format(now.map { it.score }.mean())}.")
        out.appendLine()

        out.appendLine("4. AUGMENTS: share of groups (same family, metal, catalyst, wielder, stage, seed) in which the augment is within 5 of the best augment, by line and by company")
        val augGroups = now.groupBy { listOf(it.family, it.core, it.catalyst, it.wielder, it.company, it.target, it.stage, it.seed) }.values
        for ((label, key) in listOf<Pair<String, (Fought) -> String>>("line" to { it.target }, "company" to { it.company }, "stage" to { it.stage })) {
            val keys = now.map(key).distinct()
            out.appendLine("%-13s".format(label) + keys.joinToString("") { "%10s".format(it) } + "%10s".format("all"))
            for (aug in augments) {
                fun near(gs: Collection<List<Fought>>) = pct(gs.count { g -> g.maxOf { it.score } - g.first { it.augment == aug.id }.score <= 5 }, gs.size)
                out.appendLine("%-13s".format(aug.name) + keys.joinToString("") { k -> "%10s".format(near(augGroups.filter { key(it.first()) == k })) } + "%10s".format(near(augGroups)))
            }
            out.appendLine()
        }
        out.appendLine("   Alone the best augment (no other within 0), by line")
        val lines = now.map { it.target }.distinct()
        for (aug in augments) out.appendLine("   %-13s".format(aug.name) + lines.joinToString("") { l -> val gs = augGroups.filter { it.first().target == l }; "%10s".format(pct(gs.count { g -> g.first { it.augment == aug.id }.score.let { m -> g.count { it.score >= m } == 1 } }, gs.size)) })
    }
}
