package com.tinyblacksmith.core

import com.tinyblacksmith.core.TestSupport.endDayAccepted
import com.tinyblacksmith.core.TestSupport.engine
import com.tinyblacksmith.core.TestSupport.quickSword
import com.tinyblacksmith.core.content.LaunchContent
import com.tinyblacksmith.core.engine.Command
import com.tinyblacksmith.core.engine.CommandOutcome
import com.tinyblacksmith.core.engine.ResolutionContext
import com.tinyblacksmith.core.market.Market
import com.tinyblacksmith.core.model.*
import com.tinyblacksmith.core.shopday.Lines
import com.tinyblacksmith.core.shopday.Recognitions
import com.tinyblacksmith.core.shopday.ShopDay
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** N05, plan 4.5: at most one recognition line per visit, each justified by stored facts, paced, and chosen without a draw. */
class RecognitionTest {
    private val config = engine.config
    private val content = engine.content
    private val lineage = LineageAnchor(1, "Mira Vance", "Vance", content.classes.first().id, 5, "held the walls of Emberfall", id = "era1-h3")

    private fun GameState.tryRun(command: Command): GameState = (engine.handle(this, command) as? CommandOutcome.Accepted)?.state ?: this

    /** The golden script's morning: restock, forge up to three quick swords, list every stored blade at the going rate. */
    private fun GameState.morning(): GameState {
        var s = this
        repeat(3) {
            if ((s.materials[LaunchContent.IRON] ?: 0) == 0) s = s.tryRun(Command.BuyMaterial(LaunchContent.IRON))
            if ((s.materials[LaunchContent.EMBER_RESIN] ?: 0) == 0) s = s.tryRun(Command.BuyMaterial(LaunchContent.EMBER_RESIN))
            s = s.tryRun(quickSword())
        }
        for (w in s.storedWeapons()) s = s.tryRun(Command.ToggleShelf(w.id, listed = true))
        return s
    }

    private class Day(val seed: Long, val pre: GameState, val post: GameState, val res: DayResolution)

    /** Thirty towns, one of them a descendant's, played until they fall or day 30. */
    private val days: List<Day> by lazy {
        (1L..30L).flatMap { seed ->
            val out = mutableListOf<Day>()
            var s = engine.newRun(LegacyProfile(lineages = listOf(lineage)), seed)
            while (!s.isEnded && s.day <= 30) {
                val pre = s.morning()
                val done = pre.endDayAccepted()
                out += Day(seed, pre, done.state, done.resolution!!)
                s = done.state
            }
            out
        }
    }

    private fun lines() = days.flatMap { d -> d.res.visits.filter { it.recognition != null }.map { d to it } }

    @Test
    fun everyCueIsJustifiedByItsFields() {
        val seen = mutableSetOf<RecognitionCue>()
        for ((d, v) in lines()) {
            val r = v.recognition!!
            val at = "seed ${d.seed} day ${d.res.day} ${v.heroName} ${r.cue}"
            seen += r.cue
            assertTrue(v.kind == VisitKind.BROWSE && v.reason != VisitReason.EMPTY_SHELVES, at)
            val before = d.pre.hero(v.heroId!!)
            val carried = d.pre.equippedWeapon(before.id)
            val bought = v.purchasedWeaponId != null
            fun history(kind: String) = carried?.history.orEmpty().filter { it.kind == kind && before.id.value in it.subjectIds }
            when (r.cue) {
                RecognitionCue.FIRST_VISIT -> assertTrue(before.shopVisits == 0 && d.res.day > Recognitions.INTRO_DAYS, at)
                RecognitionCue.FIRST_BLADE -> assertTrue(bought && before.shopPurchases == 0 && r.weaponId == v.purchasedWeaponId, at)
                RecognitionCue.BECAME_REGULAR -> assertTrue(bought && !Market.isRegular(before, config) && Market.isRegular(d.post.hero(before.id), config), at)
                RecognitionCue.REGULAR_RETURNS -> assertTrue(Market.isRegular(before, config) && r.day == before.lastServedDay && r.day != null, at)
                RecognitionCue.STILL_CARRIES -> assertTrue(!bought && carried != null && r.weaponId == carried.id && r.count == carried.victories && carried.victories >= 1 && (history("SOLD") + history("COMMISSION")).isNotEmpty(), at)
                RecognitionCue.BLADE_WORN -> assertTrue(carried != null && r.weaponId == carried.id && carried.condition < config.wornConditionThreshold, at)
                RecognitionCue.HELD_THE_WALL -> assertTrue(r.weaponId == carried?.id && history("SIEGE").any { it.day == r.day }, at)
                RecognitionCue.SLEW_AN_ELITE -> assertTrue(before.elitesSlain >= 1 && carried?.title != null && r.weaponId == carried.id, at)
                RecognitionCue.KEPT_THE_VOW -> assertTrue(before.ambition == Ambition.SLAYER && before.ambitionDone && carried != null && r.count == config.ambitionSlayerWins, at)
                RecognitionCue.MENTORS_BLADE -> assertTrue(!bought && before.mentorName != null && d.pre.hero(r.otherHeroId!!).fullName == before.mentorName && history("INHERITED").any { it.subjectIds.getOrNull(1) == r.otherHeroId.value }, at)
                RecognitionCue.OF_THE_LINE -> assertTrue(before.shopVisits == 0 && before.lineageId == lineage.id, at)
                RecognitionCue.WAITED_YESTERDAY -> assertTrue(before.turnedAwayStreak >= 1 && d.res.browsers.first() === v, at)
                RecognitionCue.WANT_ANSWERED -> assertTrue(bought && r.weaponId == v.purchasedWeaponId && before.want != null && r.day == before.want.sinceDay && d.post.weapon(v.purchasedWeaponId!!).familyId == before.want.familyId, at)
            }
            // The sentence is the cue's own and names the customer; no clause without its field.
            val text = assertNotNull(Lines.recognition(v, ShopDay.script(d.res, d.post, content, config), d.post), at)
            assertTrue(v.heroName in text && "null" !in text, "$at: $text")
        }
        // Mentors' blades need a retirement and are met in longer runs; every other cue turns up in these thirty towns.
        assertEquals(RecognitionCue.entries.toSet() - RecognitionCue.MENTORS_BLADE, seen - RecognitionCue.MENTORS_BLADE)
        assertTrue(lines().any { (_, v) -> v.recognition!!.cue == RecognitionCue.OF_THE_LINE && "of the line of Mira Vance, who held the walls of Emberfall" in Lines.recognition(v, ShopDay.script(days.first().res, days.first().post, content, config), days.first().post).orEmpty() })
    }

    /** The retired mentor's blade, which thirty short towns may never show: built by hand. */
    @Test
    fun theMentorsBladeIsToldOnlyWhileItIsCarried() {
        val s = engine.newRun(LegacyProfile(), 3).morning()
        val (mentor, pupil) = s.aliveHeroes().let { it[0] to it[1] }
        val blade = s.listedWeapons().first().copy(
            id = WeaponId("w900"), location = WeaponLocation.Owned(pupil.id, true),
            history = listOf(HistoryEntry(s.era, 1, "INHERITED", "Inherited.", listOf(pupil.id.value, mentor.id.value))),
        )
        val town = s.copy(weapons = s.weapons + (blade.id to blade), heroes = s.heroes + (pupil.id to pupil.copy(mentorName = mentor.fullName, shopVisits = 2)))
        val ctx = ResolutionContext(town, content, config)
        fun visit(bought: WeaponId?) = MarketVisit(pupil.id, pupil.fullName, bought, if (bought == null) VisitReason.NOT_BETTER else VisitReason.GOOD_ENOUGH)
        assertEquals(Recognition(RecognitionCue.MENTORS_BLADE, blade.id, otherHeroId = mentor.id), Recognitions.eligible(ctx, visit(null), seatedFirst = false).single { it.cue == RecognitionCue.MENTORS_BLADE })
        assertTrue(Recognitions.eligible(ctx, visit(s.listedWeapons().first().id), seatedFirst = false).none { it.cue == RecognitionCue.MENTORS_BLADE }, "traded in: no longer carried")
        val guildmate = ResolutionContext(town.copy(heroes = town.heroes + (pupil.id to town.hero(pupil.id).copy(mentorName = "Somebody Else"))), content, config)
        assertTrue(Recognitions.eligible(guildmate, visit(null), seatedFirst = false).none { it.cue == RecognitionCue.MENTORS_BLADE }, "inherited from a guildmate, not the mentor")
    }

    @Test
    fun milestonesShowOncePerHeroPerRun() {
        val told = lines().filter { (_, v) -> v.recognition!!.cue in Recognitions.MILESTONES }.groupingBy { (d, v) -> Triple(d.seed, v.heroId, v.recognition!!.cue) }.eachCount()
        assertTrue(told.isNotEmpty())
        assertEquals(emptyMap(), told.filterValues { it > 1 })
        for ((d, v) in lines()) assertEquals(v.recognition!!.cue in Recognitions.MILESTONES, v.recognition!!.cue in d.post.hero(v.heroId!!).milestoneLines, "seed ${d.seed} day ${d.res.day}: remembered on the hero")
    }

    @Test
    fun recurringLinesRespectTheCooldown() {
        var recurring = 0
        for ((d, v) in lines()) {
            val cue = v.recognition!!.cue
            val before = d.pre.hero(v.heroId!!)
            val after = d.post.heroes[before.id]
            if (after != null && after.isAlive) assertEquals(d.res.day to cue, after.lastLineDay to after.lastLineCue)
            if (cue in Recognitions.MILESTONES) continue
            recurring++
            val at = "seed ${d.seed} day ${d.res.day} ${v.heroName} $cue"
            assertTrue(before.shopVisits >= Recognitions.RECURRING_MIN_VISITS, at)
            assertTrue(before.lastLineDay?.let { d.res.day - it >= Recognitions.COOLDOWN_DAYS } ?: true, "$at: last line on day ${before.lastLineDay}")
            assertTrue(cue != before.lastLineCue, "$at: the same line twice running")
        }
        assertTrue(recurring > 100, "recurring lines seen: $recurring")
    }

    @Test
    fun atMostThreeAmongTheFeatured() {
        var withLine = 0
        for (d in days) {
            val script = ShopDay.script(d.res, d.post, content, config)
            assertTrue(script.featured.count { it.recognition != null } <= 3, "seed ${d.seed} day ${d.res.day}")
            if (script.featured.any { it.recognition != null }) withLine++
            // The opening days are introductions: one line a day at most, and a first visit is not one.
            if (d.res.day <= Recognitions.INTRO_DAYS) assertTrue(d.res.visits.count { it.recognition != null } <= Recognitions.INTRO_DAY_MAX, "seed ${d.seed} day ${d.res.day}")
        }
        assertTrue(withLine > days.size / 4, "days whose featured visits carry a line: $withLine of ${days.size}")
        // From day 6 a line on roughly four visits in ten (plan target 35-50 %; this script is a plain smith).
        val late = days.filter { it.res.day >= 6 }.flatMap { it.res.browsers }
        val share = late.count { it.recognition != null }.toDouble() / late.size
        assertTrue(share in 0.30..0.55, "share of visits with a line from day 6: $share")
    }

    @Test
    fun choiceLeavesEveryRngStreamUnchanged() {
        var lines = 0
        for (d in days.filter { it.seed <= 5 }) {
            val ctx = ResolutionContext(d.pre, content, config)
            Market.resolveShelfVisits(ctx)
            val served = ctx.toState()
            Recognitions.apply(ctx)
            val told = ctx.toState()
            lines += ctx.visits.count { it.recognition != null }
            assertEquals(served.rng, told.rng, "seed ${d.seed} day ${d.res.day}")
            // Only the heroes' memory of the lines differs.
            assertEquals(served, told.copy(heroes = told.heroes.mapValues { (id, h) -> h.copy(lastLineDay = served.hero(id).lastLineDay, lastLineCue = served.hero(id).lastLineCue, milestoneLines = served.hero(id).milestoneLines) }))
            assertEquals(ctx.visits.filter { it.kind == VisitKind.BROWSE }.map { it.recognition }, d.res.browsers.map { it.recognition }, "the day told the same lines")
        }
        assertTrue(lines > 20)
    }
}
