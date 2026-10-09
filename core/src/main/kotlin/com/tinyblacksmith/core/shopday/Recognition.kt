package com.tinyblacksmith.core.shopday

import com.tinyblacksmith.core.engine.ResolutionContext
import com.tinyblacksmith.core.market.Market
import com.tinyblacksmith.core.model.*

/**
 * What the counter knows a customer by (plan 4.5): at most one line per visit, chosen at End Day from facts already in
 * the state and stored on the visit, so the counter, the paper and a relaunch tell the same thing. Narration only: it
 * reads the day and writes the line and the hero's memory of it ([Hero.lastLineDay], [Hero.lastLineCue],
 * [Hero.milestoneLines]); no rule reads those, and the choice among several lines is a hash, never an RNG stream.
 */
object Recognitions {
    // Presentation constants, like ShopDay.FEATURED_MAX: they pace the narration and move no outcome.
    /** The opening days are introductions (class, what they look for): a first visit is no news while everybody is new, and a day tells one line at most. */
    const val INTRO_DAYS = 3
    const val INTRO_DAY_MAX = 1
    /** A recurring line needs this many earlier visits and this many days since the hero's last line. */
    const val RECURRING_MIN_VISITS = 2
    const val COOLDOWN_DAYS = 3

    /** Told once per hero per run, the rarer first when one visit earns several. The last two wait for the next visit when they lose. */
    val MILESTONES = listOf(
        RecognitionCue.OF_THE_LINE, RecognitionCue.BECAME_REGULAR, RecognitionCue.FIRST_BLADE, RecognitionCue.FIRST_VISIT,
        RecognitionCue.HELD_THE_WALL, RecognitionCue.KEPT_THE_VOW,
    )

    /** Fills [MarketVisit.recognition] for the day's browsers. Call once, after the shelf visits and before anybody leaves town. */
    fun apply(ctx: ResolutionContext) {
        val browsers = ctx.visits.withIndex().filter { (_, v) -> v.kind == VisitKind.BROWSE && v.reason != VisitReason.EMPTY_SHELVES && v.heroId != null }
        val first = browsers.firstOrNull()?.value
        val earned = browsers.associate { (i, v) -> i to eligible(ctx, v, v === first) }
        val cap = if (ctx.day <= INTRO_DAYS) INTRO_DAY_MAX else Int.MAX_VALUE
        // Milestones first, the rarer first and then in visit order; each visit tells its best one.
        val milestones = browsers.mapNotNull { (i, v) ->
            val told = ctx.hero(v.heroId!!).milestoneLines
            earned.getValue(i).filter { it.cue in MILESTONES && it.cue !in told }.minByOrNull { MILESTONES.indexOf(it.cue) }?.let { i to it }
        }
        val lines = milestones.sortedBy { (_, line) -> MILESTONES.indexOf(line.cue) }.take(cap).toMap(HashMap())
        for ((i, v) in browsers) {
            if (i in lines || lines.size >= cap) continue
            val hero = ctx.hero(v.heroId!!)
            val before = ctx.base.heroes[hero.id] ?: continue
            if (before.shopVisits < RECURRING_MIN_VISITS || (hero.lastLineDay?.let { ctx.day - it < COOLDOWN_DAYS } == true)) continue
            val open = earned.getValue(i).filter { it.cue !in MILESTONES && it.cue != hero.lastLineCue }
            if (open.isNotEmpty()) lines[i] = open[Math.floorMod(mix(ctx.base.seed, hero.id.value, ctx.day), open.size)]
        }
        for ((i, line) in lines) {
            val v = ctx.visits[i]
            val hero = ctx.hero(v.heroId!!)
            ctx.visits[i] = v.copy(recognition = line)
            ctx.updateHero(hero.copy(lastLineDay = ctx.day, lastLineCue = line.cue, milestoneLines = if (line.cue in MILESTONES) hero.milestoneLines + line.cue else hero.milestoneLines))
        }
    }

    /**
     * Every line the facts of [visit] justify, whatever has been told before. "Before" is the hero as the day opened
     * ([ResolutionContext.base]) and the blade they walked in with; "after" is the hero as served. [seatedFirst] says
     * the visit was the first at the counter today.
     */
    internal fun eligible(ctx: ResolutionContext, visit: MarketVisit, seatedFirst: Boolean): List<Recognition> {
        val id = visit.heroId ?: return emptyList()
        val before = ctx.base.heroes[id] ?: return emptyList()
        val hero = ctx.heroes[id] ?: return emptyList()
        val config = ctx.config
        val bought = visit.purchasedWeaponId != null
        // The blade in hand at the door: still theirs if they bought nothing, traded in if they did. Either way the base state has it.
        val carried = ctx.base.equippedWeapon(id)
        fun entry(kind: String) = carried?.history?.lastOrNull { it.era == ctx.era && it.kind == kind && id.value in it.subjectIds }
        return buildList {
            if (bought && !Market.isRegular(before, config) && Market.isRegular(hero, config)) add(Recognition(RecognitionCue.BECAME_REGULAR, visit.purchasedWeaponId, count = hero.shopPurchases))
            if (bought && before.shopPurchases == 0) add(Recognition(RecognitionCue.FIRST_BLADE, visit.purchasedWeaponId))
            if (before.shopVisits == 0 && hero.lineageId != null) add(Recognition(RecognitionCue.OF_THE_LINE))
            if (before.shopVisits == 0 && ctx.day > INTRO_DAYS) add(Recognition(RecognitionCue.FIRST_VISIT))
            entry("SIEGE")?.let { add(Recognition(RecognitionCue.HELD_THE_WALL, carried?.id, day = it.day)) }
            if (carried != null && before.ambition == Ambition.SLAYER && before.ambitionDone) add(Recognition(RecognitionCue.KEPT_THE_VOW, carried.id, count = config.ambitionSlayerWins))

            before.lastServedDay?.takeIf { Market.isRegular(before, config) }?.let { add(Recognition(RecognitionCue.REGULAR_RETURNS, day = it)) }
            if (carried != null && !bought && carried.victories >= 1 && (entry("SOLD") ?: entry("COMMISSION")) != null) add(Recognition(RecognitionCue.STILL_CARRIES, carried.id, count = carried.victories))
            if (carried != null && carried.condition < config.wornConditionThreshold) add(Recognition(RecognitionCue.BLADE_WORN, carried.id, count = carried.condition))
            if (carried?.title != null && before.elitesSlain >= 1) add(Recognition(RecognitionCue.SLEW_AN_ELITE, carried.id))
            // The retiring mentor's blade: the inheritance names the heir first, then the one it came from.
            if (!bought && before.mentorName != null) entry("INHERITED")?.subjectIds?.getOrNull(1)?.let { HeroId(it) }
                ?.takeIf { ctx.heroes[it]?.fullName == before.mentorName }?.let { add(Recognition(RecognitionCue.MENTORS_BLADE, carried?.id, otherHeroId = it)) }
            if (seatedFirst && before.turnedAwayStreak >= 1) add(Recognition(RecognitionCue.WAITED_YESTERDAY, count = before.turnedAwayStreak))
        }
    }

    /** SplitMix64's finalizer over the run seed, the hero and the day: a fixed arithmetic hash, not a stream. */
    private fun mix(seed: Long, heroId: String, day: Int): Int {
        var z = seed xor (heroId.hashCode().toLong() shl 32) xor day.toLong()
        z += -0x61c8864680b583ebL
        z = (z xor (z ushr 30)) * -0x40a7b892e31b1a47L
        z = (z xor (z ushr 27)) * -0x6b2fb644ecceee15L
        return (z xor (z ushr 31)).toInt()
    }
}
