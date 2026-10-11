package com.tinyblacksmith.core.sim

import com.tinyblacksmith.core.combat.Side
import com.tinyblacksmith.core.config.BalanceConfig
import com.tinyblacksmith.core.content.AffixKind
import com.tinyblacksmith.core.content.ContentCatalog
import com.tinyblacksmith.core.content.LaunchContent
import com.tinyblacksmith.core.content.MaterialCategory
import com.tinyblacksmith.core.crafting.Bench
import com.tinyblacksmith.core.crafting.Capabilities
import com.tinyblacksmith.core.crafting.SignatureCatalog
import com.tinyblacksmith.core.crafting.WeaponRules
import com.tinyblacksmith.core.model.HeroClassId
import com.tinyblacksmith.core.model.Weapon

/**
 * A bounded sweep of legal recipes through the bench's fights (`./gradlew :core:combat --args="sweep"`): every family,
 * metal, augment and catalyst at one quality, in the hands of each class the family suits, with each company against
 * each line. It answers four questions with fights, not with power scores: does every rule ever fire, is one metal or
 * recipe best everywhere, does a catalyst ever fail to earn its place, and does the evaluator's "ready" match what
 * fired. Rolled properties get a second, smaller pass: each buff, flaw and signature on the recipes that can carry it.
 */
object ForgeSweep {
    private val content: ContentCatalog = LaunchContent.catalog
    private val config: BalanceConfig = BalanceConfig.DEFAULT
    private val combat = content.combat!!

    class Tally { var carried = 0; var fired = 0; var readyFired = 0; var readySilent = 0; var openFired = 0; var openSilent = 0; val readySilentWhy = HashMap<String, Int>(); val openFiredAt = ArrayList<String>() }

    data class Fought(val weapon: Weapon, val wielder: HeroClassId, val companions: String, val target: String, val score: Int, val won: Boolean)

    class Result(val rules: Map<String, Tally>, val fights: List<Fought>, val maxEventsInARound: Int, val extraFights: Int)

    /** 0..200: the share of the enemy line taken down, and for a win the share of the party's health still standing. */
    private fun score(report: Bench.Report): Int {
        val foes = report.result.actors.filter { it.side == Side.ENEMY && !it.summoned }
        val party = report.result.actors.filter { it.side == Side.PARTY && !it.summoned }
        val taken = 100 - foes.sumOf { it.health } * 100 / maxOf(1, foes.sumOf { it.maxHealth })
        return taken + if (report.result.won) party.sumOf { it.health } * 100 / maxOf(1, party.sumOf { it.maxHealth }) else 0
    }

    fun run(): Result {
        val rules = HashMap<String, Tally>()
        val fights = ArrayList<Fought>()
        var maxEvents = 0
        var extra = 0
        fun fight(w: Weapon, keep: Boolean) {
            val composed = WeaponRules.of(w, content)
            for (cls in content.classes.filter { (content.family(w.familyId).classFit[it.id] ?: 0.0) >= 1.0 }) for (c in Bench.companions) for (t in Bench.targets) {
                val report = Bench.run(w, true, cls.id, c.id, t.id, content, config)
                maxEvents = maxOf(maxEvents, report.result.events.groupingBy { it.round }.eachCount().values.max())
                val open = Capabilities.missing(composed, Bench.company(cls.id, c.id, t.id, content, config))
                for (r in report.fired) rules.getOrPut(r.rule.id) { Tally() }.let {
                    it.carried++; it.fired++
                    if (r.rule.id in open) { it.openFired++; if (it.openFiredAt.size < 2) it.openFiredAt += "${w.name}, ${cls.name}, ${c.id}, ${t.id}: ${r.chain.joinToString(" > ")}" } else it.readyFired++
                }
                for (r in report.silent) rules.getOrPut(r.rule.id) { Tally() }.let {
                    it.carried++
                    if (r.rule.id in open) it.openSilent++ else { it.readySilent++; val why = r.why.replace(report.setup.party.first { p -> p.key == report.wielderKey }.name, "The wielder"); it.readySilentWhy[why] = (it.readySilentWhy[why] ?: 0) + 1 }
                }
                if (keep) fights += Fought(w, cls.id, c.id, t.id, score(report), report.result.won) else extra++
            }
        }
        val cores = content.materials(MaterialCategory.CORE)
        val augments = content.materials(MaterialCategory.AUGMENT)
        val catalysts = listOf(null) + content.materials(MaterialCategory.CATALYST).map { it.id }
        // Each recipe at the quality its materials centre on for a new account: a dearer metal is a finer weapon, as at the anvil.
        fun centre(f: com.tinyblacksmith.core.model.WeaponFamilyId, core: com.tinyblacksmith.core.content.MaterialDef, aug: com.tinyblacksmith.core.content.MaterialDef, cat: com.tinyblacksmith.core.model.MaterialId?) =
            config.qualityBase + config.qualityPerCoreTier * core.tier + config.qualityPerAugmentTier * aug.tier + (content.coreAugmentAffinity[core.id to aug.id] ?: 0) + (content.augmentFamilyAffinity[aug.id to f] ?: 0) +
                (if (cat != null) config.catalystQualityBonus else 0)
        for (f in content.families) for (core in cores) for (aug in augments) for (cat in catalysts) fight(Bench.demonstration(f.id, core.id, aug.id, cat, content, config, centre(f.id, core, aug, cat)), keep = true)
        // What a roll adds: each buff and flaw on a plain bronze recipe of every family, each signature on its own recipe.
        for (f in content.families) for (aug in augments) {
            val base = Bench.demonstration(f.id, LaunchContent.BRONZE, aug.id, null, content, config)
            for (a in content.affixes) {
                if (a.kind == AffixKind.BENEFICIAL && a.element != null && a.element != base.element) continue
                fight(if (a.kind == AffixKind.FLAW) base.copy(flaws = listOf(a.id)) else base.copy(affixes = listOf(a.id)), keep = false)
            }
        }
        for (sig in SignatureCatalog.all.filter { it.id in combat.signatureEffects }) {
            fight(Bench.demonstration(sig.familyId, sig.coreId, sig.augmentId, sig.catalystId, content, config).copy(signatureId = sig.id, affixes = sig.grantedAffixes, name = sig.name), keep = false)
        }
        return Result(rules, fights, maxEvents, extra)
    }

    fun print(out: Appendable = System.out) {
        val r = run()
        fun name(id: com.tinyblacksmith.core.model.MaterialId?) = id?.let { content.material(it).name } ?: "No catalyst"
        out.appendLine("FORGE SWEEP: ${r.fights.size} recipe fights (each recipe at the quality its materials centre on) and ${r.extraFights} fights for rolled properties (quality ${Bench.DEMO_QUALITY}). Bench level ${Bench.LEVEL}, seed ${Bench.SEED}.")
        out.appendLine("Most events in one round: ${r.maxEventsInARound} of a budget of ${config.guild.fight.eventBudgetPerRound}. No fight threw.")
        out.appendLine()
        out.appendLine("A. RULES: carried / fired, and the evaluator against the fight (ready = nothing missing in that company)")
        out.appendLine("%-28s %8s %8s %6s | %11s %12s %10s %11s".format("rule", "carried", "fired", "rate", "ready+fired", "ready+silent", "open+fired", "open+silent"))
        val known = combat.allEffects.filter { !it.party }.map { it.id }.distinct()
        for (id in known) {
            val t = r.rules[id]
            if (t == null) out.appendLine("%-28s %8s".format(id, "never carried")) else
                out.appendLine("%-28s %8d %8d %5d%% | %11d %12d %10d %11d".format(id, t.carried, t.fired, t.fired * 100 / t.carried, t.readyFired, t.readySilent, t.openFired, t.openSilent))
        }
        out.appendLine()
        // One group is everything but the thing compared, so a metal is measured against the other metals in the same weapon, hands, company and line.
        fun <K> table(title: String, label: (Fought) -> String, group: (Fought) -> K, order: List<String>) {
            out.appendLine(title)
            out.appendLine("%-16s %6s %6s %11s %9s %12s %7s".format("", "mean", "wins", "alone best", "best in", "within 5 of", "worst"))
            val groups = r.fights.groupBy(group).values
            for (l in order) {
                val mine = r.fights.filter { label(it) == l }
                val best = groups.count { g -> g.maxOf { it.score } == g.first { label(it) == l }.score }
                val sole = groups.count { g -> g.first { label(it) == l }.score.let { mine -> g.count { it.score >= mine } == 1 } }
                val near = groups.count { g -> g.maxOf { it.score } - g.first { label(it) == l }.score <= 5 }
                val worst = groups.count { g -> g.minOf { it.score } == g.first { label(it) == l }.score }
                out.appendLine("%-16s %6d %5d%% %10d%% %8d%% %11d%% %6d%%".format(l, mine.sumOf { it.score } / mine.size, mine.count { it.won } * 100 / mine.size, sole * 100 / groups.size, best * 100 / groups.size, near * 100 / groups.size, worst * 100 / groups.size))
            }
            out.appendLine()
        }
        table("B. METALS (same family, augment, catalyst, wielder, company and line)", { name(it.weapon.coreId) }, { listOf(it.weapon.familyId, it.weapon.augmentId, it.weapon.catalystId, it.wielder, it.companions, it.target) }, content.materials(MaterialCategory.CORE).map { it.name })
        table("C. CATALYSTS (same family, metal, augment, wielder, company and line)", { name(it.weapon.catalystId) }, { listOf(it.weapon.familyId, it.weapon.coreId, it.weapon.augmentId, it.wielder, it.companions, it.target) }, listOf("No catalyst") + content.materials(MaterialCategory.CATALYST).map { it.name })
        table("D. AUGMENTS (same family, metal, catalyst, wielder, company and line)", { name(it.weapon.augmentId) }, { listOf(it.weapon.familyId, it.weapon.coreId, it.weapon.catalystId, it.wielder, it.companions, it.target) }, content.materials(MaterialCategory.AUGMENT).map { it.name })

        out.appendLine("E. WHERE EACH METAL IS ALONE THE BEST CHOICE (share of groups, by company and by line)")
        val coreGroups = r.fights.groupBy { listOf(it.weapon.familyId, it.weapon.augmentId, it.weapon.catalystId, it.wielder, it.companions, it.target) }.values
        for (axis in listOf<Pair<String, (Fought) -> String>>("company" to { it.companions }, "line" to { it.target })) {
            val keys = r.fights.map(axis.second).distinct()
            out.appendLine("%-12s".format(axis.first) + keys.joinToString("") { "%10s".format(it) })
            for (core in content.materials(MaterialCategory.CORE)) {
                out.appendLine("%-12s".format(core.name) + keys.joinToString("") { k ->
                    val gs = coreGroups.filter { axis.second(it.first()) == k }
                    "%9d%%".format(gs.count { g -> g.first { it.weapon.coreId == core.id }.score.let { mine -> g.count { it.score >= mine } == 1 } } * 100 / gs.size)
                })
            }
            out.appendLine()
        }

        out.appendLine("G. WHERE THE EVALUATOR AND THE FIGHT DISAGREE")
        for (id in known) {
            val t = r.rules[id] ?: continue
            if (t.readySilent == 0 && t.openFired == 0) continue
            out.appendLine(id)
            t.readySilentWhy.entries.sortedByDescending { it.value }.take(3).forEach { out.appendLine("  ready but silent, %5d: %s".format(it.value, it.key)) }
            t.openFiredAt.forEach { out.appendLine("  missing but fired: $it") }
        }
        out.appendLine()
        out.appendLine("F. ONE RECIPE EVERYWHERE? Recipes in the top tenth of their scenario (wielder, company, line), by how many of the scenarios they reach it in")
        val scenarios = r.fights.groupBy { Triple(it.wielder, it.companions, it.target) }
        val top = HashMap<String, Int>()
        val seenIn = HashMap<String, Int>()
        for ((_, fs) in scenarios) {
            val cut = fs.map { it.score }.sortedDescending()[fs.size / 10]
            for (f in fs) {
                val key = "${content.family(f.weapon.familyId).name} / ${name(f.weapon.coreId)} / ${name(f.weapon.augmentId)} / ${name(f.weapon.catalystId)}"
                seenIn[key] = (seenIn[key] ?: 0) + 1
                if (f.score >= cut) top[key] = (top[key] ?: 0) + 1
            }
        }
        val everywhere = top.filter { (k, n) -> n == seenIn[k] }
        out.appendLine("Recipes in the top tenth of every scenario they were fought in: ${everywhere.size} of ${seenIn.size}")
        top.entries.sortedByDescending { it.value * 100 / seenIn.getValue(it.key) }.take(12).forEach { out.appendLine("  %3d%% of %2d  %s".format(it.value * 100 / seenIn.getValue(it.key), seenIn.getValue(it.key), it.key)) }
    }
}
