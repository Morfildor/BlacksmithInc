package com.tinyblacksmith.core

import com.tinyblacksmith.core.TestSupport.endDayId
import com.tinyblacksmith.core.TestSupport.engine
import com.tinyblacksmith.core.TestSupport.forgeAccepted
import com.tinyblacksmith.core.TestSupport.quickSword
import com.tinyblacksmith.core.TestSupport.withMaterials
import com.tinyblacksmith.core.config.BalanceConfig
import com.tinyblacksmith.core.content.LaunchContent
import com.tinyblacksmith.core.content.SliceContent
import com.tinyblacksmith.core.engine.Command
import com.tinyblacksmith.core.engine.GameEngine
import com.tinyblacksmith.core.engine.Invariants
import com.tinyblacksmith.core.engine.ResolutionContext
import com.tinyblacksmith.core.engine.acceptedOrThrow
import com.tinyblacksmith.core.heroes.Heroes
import com.tinyblacksmith.core.model.*
import com.tinyblacksmith.core.persistence.SaveCodec
import com.tinyblacksmith.core.sim.Policy
import com.tinyblacksmith.core.sim.SimulationDriver
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Balance v5: the guild hall and personal ambitions as scored daily activities, and the money and prior-history inputs (GDD 6). */
class HeroDailyLifeTest {
    private val config = engine.config
    private val content = engine.content
    private val life = config.heroLife

    /** The hall, or the ambition, outweighs everything else: every hero who may choose it does. */
    private val hallDay = config.copy(heroLife = life.copy(guildBaseWeight = 1e9))
    private val ambitionDay = config.copy(ambitionActivityWeight = 1e9)

    private val hallAndAmbitionTypes = setOf(EventType.GUILD_TRAINED, EventType.GUILD_JOINED, EventType.GUILD_MENTORED, EventType.AMBITION_PURSUED)
    private val eliteNames = content.factions.flatMap { it.eliteNames }

    private fun fresh(seed: Long = 7) = engine.newRun(LegacyProfile(), seed).withMaterials()

    private fun GameState.withHeroes(change: (Hero) -> Hero) = copy(heroes = heroes.mapValues { change(it.value) })

    private fun GameState.withGuild(id: String, founder: Hero) =
        copy(town = town.copy(guilds = town.guilds + Guild(id, "the ${founder.surname} Company", founder.id, day)))

    /** Step 4 of End Day on its own (GDD 3.2): every living hero chooses and acts once. */
    private fun morning(state: GameState, cfg: BalanceConfig = config): ResolutionContext =
        ResolutionContext(state, content, cfg).also { Heroes.resolveActivities(it) }

    /** Records of [type] whose first subject is [hero] (all heroes when null). */
    private fun ResolutionContext.count(type: EventType, hero: Hero? = null) =
        newEvents.count { it.type == type && (hero == null || it.subjectIds.firstOrNull() == hero.id.value) }

    private fun weights(state: GameState, hero: Hero, weapon: Weapon? = null): Map<HeroActivity, Double> =
        Heroes.activityWeights(ResolutionContext(state, content, config), hero, weapon, state.factions.values.first()).toMap()

    /** Share of hero-days spent on [types] over one morning of 40 seeds, with every hero prepared by [prepare]. */
    private fun share(types: Set<EventType>, guild: Boolean = false, day: Int = 1, prepare: (Hero) -> Hero): Double {
        var days = 0
        var chosen = 0
        for (seed in 1L..40L) {
            val s0 = fresh(seed).copy(day = day)
            val s = (if (guild) s0.withGuild("g1", s0.aliveHeroes().first()) else s0).withHeroes(prepare)
            days += s.aliveHeroes().size
            chosen += morning(s).newEvents.count { it.type in types }
        }
        return chosen.toDouble() / days
    }

    // --- Guild / mentor ---

    @Test
    fun aDayAtTheHallGivesBoundedXpAndASmallHealAndNothingElse() {
        val s0 = fresh()
        val s = s0.withGuild("g1", s0.aliveHeroes().first()).withHeroes { it.copy(guildId = "g1", health = 80, level = 3, xp = 0) }
        val ctx = morning(s, hallDay)
        for (h in s.aliveHeroes()) {
            val after = ctx.hero(h.id)
            assertEquals(HeroActivity.GUILD, after.lastActivity)
            assertEquals(life.guildXp, after.xp, "equal levels: nobody is mentored")
            assertEquals(3, after.level)
            assertEquals(80 + life.guildHeal, after.health)
            assertEquals(h.gold, after.gold, "the hall pays nothing")
            assertEquals(1, ctx.count(EventType.GUILD_TRAINED, h))
        }
        assertEquals(s.town.militia, ctx.town.militia)
        assertTrue(ctx.factions.values.all { it.suppressionToday == 0 }, "the hall suppresses no faction")
        assertEquals(0, ctx.patrolsToday + ctx.expeditionWinsToday)
        assertEquals(s.aliveHeroes().size, ctx.newEvents.size, "one quiet record per hero and nothing else")
        assertTrue(life.guildXp + life.mentorXp < config.expeditionXp, "even a mentored day at the hall teaches less than a won expedition")
        assertTrue(life.guildHeal < config.heroRestHeal, "the hall mends less than a day of rest")
        // The heal stops at full health.
        assertTrue(morning(s.withHeroes { it.copy(health = 97) }, hallDay).heroes.values.all { it.health == 100 })
    }

    @Test
    fun onlyAHigherLevelGuildmateAtTheHallMentorsAndOnlyOnceADay() {
        val s0 = fresh()
        val h = s0.aliveHeroes()
        // Guild g1: levels 5, 4 and 2, and two level-9 members who are wounded today. Guild g2: levels 6, 6 and 1.
        val levels = listOf(5, 4, 2, 6, 6, 1, 9, 9)
        val guildOf = listOf("g1", "g1", "g1", "g2", "g2", "g2", "g1", "g1")
        val s = s0.withGuild("g1", h[0]).withGuild("g2", h[3]).withHeroes { hero ->
            val i = h.indexOfFirst { it.id == hero.id }
            hero.copy(
                guildId = guildOf[i], level = levels[i], xp = 0, health = if (i >= 6) config.heroWoundedThreshold - 1 else 100,
                mentorName = if (i == 2) "Old Master" else null,
            )
        }
        val ctx = morning(s, hallDay)
        val lessons = ctx.newEvents.filter { it.type == EventType.GUILD_MENTORED }
        fun mentorsOf(i: Int) = lessons.filter { it.subjectIds.firstOrNull() == h[i].id.value }.map { it.subjectIds[1] }
        assertEquals(emptyList(), mentorsOf(0), "the highest level at the hall has nobody to learn from")
        assertEquals(listOf(h[0].id.value), mentorsOf(1))
        assertEquals(listOf(h[0].id.value), mentorsOf(2), "one lesson a day, from the highest-level guildmate, not one from each")
        assertEquals(emptyList(), mentorsOf(3) + mentorsOf(4), "equals do not mentor each other")
        assertEquals(listOf(h[3].id.value), mentorsOf(5), "a tie between mentors goes to the lower ID")
        assertEquals(3, lessons.size, "the level-6 heroes of the other guild and the wounded level-9 members taught nobody")
        assertEquals(listOf(0, 1, 1, 0, 0, 1).map { life.guildXp + it * life.mentorXp }, (0..5).map { ctx.hero(h[it].id).xp })
        assertEquals(h[0].fullName, ctx.hero(h[1].id).mentorName)
        assertEquals("Old Master", ctx.hero(h[2].id).mentorName, "the first mentor stays on record")
        assertEquals(h[3].fullName, ctx.hero(h[5].id).mentorName)
        assertNull(ctx.hero(h[0].id).mentorName)
        for (i in 6..7) assertEquals(0, ctx.count(EventType.GUILD_TRAINED, h[i]), "a wounded hero rests")

        // The mentor has to be at the hall: with the level-5 hero wounded, the level-4 one teaches and learns from nobody.
        val absent = morning(s.withHeroes { if (it.id == h[0].id) it.copy(health = config.heroWoundedThreshold - 1) else it }, hallDay)
        val absentLessons = absent.newEvents.filter { it.type == EventType.GUILD_MENTORED && it.subjectIds.firstOrNull() in setOf(h[1].id.value, h[2].id.value) }
        assertEquals(listOf(listOf(h[2].id.value, h[1].id.value)), absentLessons.map { it.subjectIds })
        assertEquals(life.guildXp, absent.hero(h[1].id).xp)
    }

    @Test
    fun aFamousHeroFoundsTheFirstGuildAndTheOthersJoinIt() {
        val s0 = fresh().withHeroes { it.copy(health = 100, fame = 0) }
        val order = s0.aliveHeroes()
        // No guild in town and nobody with a name: the hall is on nobody's list, however much they want it.
        val quiet = morning(s0, hallDay)
        assertTrue(quiet.town.guilds.isEmpty())
        assertTrue(quiet.newEvents.none { it.type in hallAndAmbitionTypes - EventType.AMBITION_PURSUED || it.type == EventType.GUILD_FOUNDED })
        assertTrue(order.all { HeroActivity.GUILD !in weights(s0, it) })
        assertTrue(HeroActivity.GUILD !in weights(s0, order[0].copy(fame = config.guildFameThreshold - 1)))
        assertTrue(HeroActivity.GUILD in weights(s0, order[0].copy(fame = config.guildFameThreshold)))

        // The third hero of the day has the fame for it: the two before had no hall to go to, the founder opens one, the rest join it.
        val founder = order[2]
        val day = morning(s0.withHeroes { if (it.id == founder.id) it.copy(fame = config.guildFameThreshold) else it }, hallDay)
        val guild = day.town.guilds.single()
        assertEquals(founder.id, guild.founderId)
        assertEquals(1, day.count(EventType.GUILD_FOUNDED, founder))
        for ((i, h) in order.withIndex()) {
            assertEquals(if (i < 2) null else guild.id, day.hero(h.id).guildId)
            assertEquals(if (i < 2) 0 else 1, day.count(EventType.GUILD_TRAINED, h))
            assertEquals(if (i <= 2) 0 else 1, day.count(EventType.GUILD_JOINED, h))
        }

        // Next morning the first two walk in and join. Nobody founds a second guild while one stands.
        val next = morning(day.toState().withHeroes { it.copy(health = 100, fame = config.guildFameThreshold) }, hallDay)
        assertEquals(listOf(guild), next.town.guilds)
        assertTrue(next.aliveHeroes().all { it.guildId == guild.id })
        assertEquals(2, next.count(EventType.GUILD_JOINED))
        assertEquals(0, next.count(EventType.GUILD_FOUNDED))
        assertEquals(emptyList(), Invariants.check(next.toState(), config))
    }

    @Test
    fun traitsWeighOnTheHall() {
        val s0 = fresh()
        val s = s0.withGuild("g1", s0.aliveHeroes().first())
        val hero = s.aliveHeroes().first()
        fun hall(traits: List<TraitId>) = weights(s, hero.copy(traits = traits)).getValue(HeroActivity.GUILD)
        assertEquals(life.guildBaseWeight, hall(emptyList()), 1e-9)
        val weighted = content.traits.filter { it.guildWeight != 0.0 }
        assertTrue(weighted.any { it.guildWeight > 0 } && weighted.any { it.guildWeight < 0 })
        for (t in weighted) assertEquals(maxOf(0.02, life.guildBaseWeight + t.guildWeight), hall(listOf(t.id)), 1e-9, t.name)
        assertEquals(0.02, hall(listOf(LaunchContent.GREEDY, LaunchContent.RESTLESS)), 1e-9, "the weight has a floor")
        assertTrue(SliceContent.catalog.traits.any { it.guildWeight > 0 } && SliceContent.catalog.traits.any { it.guildWeight < 0 })

        // Over many mornings patient members are at the hall far more often than greedy ones.
        val patient = share(setOf(EventType.GUILD_TRAINED), guild = true) { it.copy(guildId = "g1", traits = listOf(LaunchContent.PATIENT)) }
        val greedy = share(setOf(EventType.GUILD_TRAINED), guild = true) { it.copy(guildId = "g1", traits = listOf(LaunchContent.GREEDY)) }
        assertTrue(patient > greedy + 0.1, "patient $patient, greedy $greedy")
    }

    // --- Personal ambition ---

    @Test
    fun aSlayerHuntsAnEliteOnPurpose() {
        fun hunts(cfg: BalanceConfig): Pair<Int, Int> {  // fights, fights against an elite
            var fights = 0
            var elites = 0
            for (seed in 1L..30L) {
                val s = fresh(seed).withHeroes { it.copy(ambition = Ambition.SLAYER, ambitionDone = false) }
                val ctx = morning(s, cfg)
                for (h in s.aliveHeroes()) {
                    assertEquals(1, ctx.newEvents.count { it.type == EventType.AMBITION_PURSUED && it.subjectIds.firstOrNull() == h.id.value && it.data["ambition"] == "SLAYER" })
                    val fight = ctx.newEvents.filter {
                        it.subjectIds.firstOrNull() == h.id.value &&
                            (it.type == EventType.ELITE_SLAIN || it.type == EventType.EXPEDITION_LOST || it.type == EventType.HERO_DIED || (it.type == EventType.EXPEDITION_WON && "material" !in it.data))
                    }
                    assertEquals(1, fight.size, "a hunt is one expedition")
                    fights++
                    if (eliteNames.any { it in fight.single().text }) elites++
                    val after = ctx.hero(h.id)
                    if (after.isAlive) assertEquals(HeroActivity.AMBITION, after.lastActivity)
                }
            }
            return fights to elites
        }
        val (fights, elites) = hunts(ambitionDay)
        val (plainFights, plainElites) = hunts(ambitionDay.copy(heroLife = life.copy(slayerHuntEliteChance = 0.0)))
        assertEquals(fights, plainFights)
        assertTrue(elites > plainElites + fights * 0.15, "elites met on $fights hunts: $elites, without the hunt bonus $plainElites")
        val (sureFights, sureElites) = hunts(ambitionDay.copy(heroLife = life.copy(slayerHuntEliteChance = 1.0)))
        assertEquals(sureFights, sureElites, "a certain elite roll makes every hunt an elite fight")
    }

    @Test
    fun aDefenderDrillsTheWatchAndCollectorsAndFortuneSeekersTakePaidWork() {
        val s0 = fresh().withHeroes { it.copy(health = 100, ambitionDone = false) }.let { it.copy(town = it.town.copy(militia = 0)) }
        val n = s0.aliveHeroes().size

        // DEFENDER: militia only. No suppression, no pay, no XP, and it is not a patrol (no forge recovery).
        val drill = morning(s0.withHeroes { it.copy(ambition = Ambition.DEFENDER) }, ambitionDay.copy(militiaMax = 1000))
        assertEquals(n * life.defenderDrillMilitia, drill.town.militia)
        assertTrue(life.defenderDrillMilitia > config.patrolMilitiaGain)
        assertTrue(drill.factions.values.all { it.suppressionToday == 0 })
        assertEquals(0, drill.patrolsToday)
        assertEquals(n, drill.newEvents.count { it.type == EventType.AMBITION_PURSUED && it.data["ambition"] == "DEFENDER" })
        assertEquals(n, drill.newEvents.size)
        for (h in s0.aliveHeroes()) assertEquals(h.copy(ambition = Ambition.DEFENDER, lastActivity = HeroActivity.AMBITION), drill.hero(h.id))
        // The militia cap still holds.
        assertEquals(config.militiaMax, morning(s0.withHeroes { it.copy(ambition = Ambition.DEFENDER) }, ambitionDay).town.militia)

        // COLLECTOR and FORTUNE: gold only.
        for (ambition in listOf(Ambition.COLLECTOR, Ambition.FORTUNE)) {
            val work = morning(s0.withHeroes { it.copy(ambition = ambition) }, ambitionDay)
            assertEquals(0, work.town.militia)
            assertTrue(work.factions.values.all { it.suppressionToday == 0 })
            assertEquals(n, work.newEvents.count { it.type == EventType.AMBITION_PURSUED && it.data["ambition"] == ambition.name && it.data["gold"] == life.ambitionWorkGold.toString() })
            assertEquals(n, work.newEvents.size)
            for (h in s0.aliveHeroes()) {
                assertEquals(h.copy(ambition = ambition, gold = h.gold + life.ambitionWorkGold, lastActivity = HeroActivity.AMBITION), work.hero(h.id))
            }
        }
        assertTrue(life.ambitionWorkGold > config.patrolGold)
    }

    @Test
    fun anAmbitionIsPursuedOnlyWhileUnfulfilled() {
        val s0 = fresh().withHeroes { it.copy(health = 100) }
        val hero = s0.aliveHeroes().first()
        for (ambition in Ambition.entries) {
            assertEquals(config.ambitionActivityWeight, weights(s0, hero.copy(ambition = ambition, ambitionDone = false)).getValue(HeroActivity.AMBITION), 1e-9)
            assertTrue(HeroActivity.AMBITION !in weights(s0, hero.copy(ambition = ambition, ambitionDone = true)))
        }
        assertTrue(HeroActivity.AMBITION !in weights(s0, hero.copy(ambition = null)), "a hero from an older save has no ambition to pursue")
        assertEquals(0, morning(s0.withHeroes { it.copy(ambitionDone = true) }, ambitionDay).count(EventType.AMBITION_PURSUED))
        assertEquals(0, morning(s0.withHeroes { it.copy(ambition = null) }, ambitionDay).count(EventType.AMBITION_PURSUED))

        // Through a whole End Day: the last day of paid work completes a fortune by the v3 rule, and the hero stops pursuing it.
        val eager = GameEngine(config = ambitionDay)
        val nearly = s0.withHeroes { if (it.id == hero.id) it.copy(ambition = Ambition.FORTUNE, ambitionDone = false, gold = config.ambitionFortuneGold - 1) else it }
        val first = eager.handle(nearly, Command.EndDay(endDayId(nearly))).acceptedOrThrow()
        fun mine(events: List<EventRecord>, type: EventType) = events.count { it.type == type && it.subjectIds.firstOrNull() == hero.id.value }
        assertEquals(1, mine(first.resolution!!.events, EventType.AMBITION_PURSUED))
        assertEquals(1, mine(first.resolution!!.events, EventType.AMBITION_FULFILLED))
        assertTrue(first.state.hero(hero.id).ambitionDone)
        val second = eager.handle(first.state, Command.EndDay(endDayId(first.state))).acceptedOrThrow()
        assertEquals(0, mine(second.resolution!!.events, EventType.AMBITION_PURSUED))
    }

    // --- Money and prior history ---

    @Test
    fun anUnarmedHeroShortOfGoldLeansTowardPatrolPay() {
        val s = fresh()
        val hero = s.aliveHeroes().first()
        val sword = s.forgeAccepted(quickSword()).let { it.state.weapon(it.forgedWeaponId!!) }
        fun patrol(gold: Int, weapon: Weapon? = null) = weights(s, hero.copy(gold = gold), weapon).getValue(HeroActivity.PATROL)
        assertEquals(patrol(life.poorHeroGold) + life.poorPatrolWeight, patrol(life.poorHeroGold - 1), 1e-9)
        assertEquals(patrol(life.poorHeroGold), patrol(100_000), 1e-9)
        assertEquals(patrol(life.poorHeroGold), patrol(0, sword), 1e-9, "an armed hero is not looking for patrol pay")

        // Fresh heroes are unarmed: over many mornings the broke ones walk the walls more than the rich ones.
        val broke = share(setOf(EventType.HERO_PATROLLED)) { it.copy(gold = 0, ambition = null) }
        val rich = share(setOf(EventType.HERO_PATROLLED)) { it.copy(gold = 500, ambition = null) }
        assertTrue(broke > rich + 0.05, "patrol share broke $broke, rich $rich")
    }

    @Test
    fun aHeroDrivenBackYesterdayLiesLowOrGoesToTheHall() {
        // The rout is written on the hero the day it happens; a win or a quiet day writes nothing.
        var routs = 0
        for (seed in 1L..20L) {
            val s = fresh(seed)
            val ctx = morning(s)
            for (h in s.aliveHeroes()) {
                val lost = ctx.count(EventType.EXPEDITION_LOST, h) == 1
                assertEquals(if (lost) s.day else null, ctx.hero(h.id).drivenBackOnDay)
                if (lost) routs++
            }
        }
        assertTrue(routs >= 10, "routs in 20 mornings: $routs")

        val s0 = fresh().copy(day = 3)
        val s = s0.withGuild("g1", s0.aliveHeroes().first())
        val member = s.aliveHeroes().first().copy(guildId = "g1", health = 100)
        val calm = weights(s, member)
        val shaken = weights(s, member.copy(drivenBackOnDay = 2))
        assertEquals(calm.getValue(HeroActivity.REST) + life.setbackRestWeight, shaken.getValue(HeroActivity.REST), 1e-9)
        assertEquals(calm.getValue(HeroActivity.GUILD) + life.setbackGuildWeight, shaken.getValue(HeroActivity.GUILD), 1e-9)
        assertEquals(calm, weights(s, member.copy(drivenBackOnDay = 1)), "only yesterday counts")
        assertEquals(calm - HeroActivity.REST - HeroActivity.GUILD, shaken - HeroActivity.REST - HeroActivity.GUILD)

        // Healthy guild members, so wounds play no part: the ones driven back yesterday rest or train more often.
        val quietTypes = setOf(EventType.HERO_RESTED, EventType.GUILD_TRAINED)
        val shakenShare = share(quietTypes, guild = true, day = 3) { it.copy(guildId = "g1", health = 100, drivenBackOnDay = 2) }
        val calmShare = share(quietTypes, guild = true, day = 3) { it.copy(guildId = "g1", health = 100, drivenBackOnDay = null) }
        val oldShare = share(quietTypes, guild = true, day = 3) { it.copy(guildId = "g1", health = 100, drivenBackOnDay = 1) }
        assertTrue(shakenShare > calmShare + 0.05, "rest or hall share after a rout $shakenShare, otherwise $calmShare")
        assertEquals(calmShare, oldShare)
    }

    // --- Constraints, determinism, bookkeeping ---

    @Test
    fun woundedDeadAndRetiredHeroesNeverTrainOrPursue() {
        val keen = hallDay.copy(ambitionActivityWeight = 1e9)
        val s0 = fresh()
        val h = s0.aliveHeroes()
        val s = s0.withGuild("g1", h[0]).withHeroes { hero ->
            val member = hero.copy(guildId = "g1", ambition = Ambition.DEFENDER, ambitionDone = false, health = 100)
            when (hero.id) {
                h[0].id -> member.copy(health = config.heroWoundedThreshold - 1)
                h[1].id -> member.copy(fate = HeroFate.DEAD, health = 0, diedOnDay = 1)
                h[2].id -> member.copy(fate = HeroFate.RETIRED, retiredOnDay = 1)
                else -> member
            }
        }
        val ctx = morning(s, keen)
        for (i in 0..2) assertTrue(ctx.newEvents.none { it.type in hallAndAmbitionTypes && h[i].id.value in it.subjectIds }, "hero $i acted")
        assertEquals(HeroActivity.REST, ctx.hero(h[0].id).lastActivity)
        assertEquals(1, ctx.count(EventType.HERO_RESTED, h[0]))
        for (i in 1..2) assertEquals(s.hero(h[i].id), ctx.hero(h[i].id), "the dead and the retired are untouched")
        for (i in 3 until h.size) {
            assertEquals(1, ctx.newEvents.count { (it.type == EventType.GUILD_TRAINED || it.type == EventType.AMBITION_PURSUED) && it.subjectIds.firstOrNull() == h[i].id.value })
        }

        // Whole runs with quick retirements and busy halls: nobody who was dead, retired or wounded that morning shows up in a hall or ambition record.
        val eager = GameEngine(config = config.copy(retirementLevel = 3, retirementVictories = 2, retirementChance = 0.5, ambitionActivityWeight = 1.5, heroLife = life.copy(guildBaseWeight = 1.5)))
        val seen = mutableMapOf<EventType, Int>()
        var gone = 0
        for (seed in 1L..60L) {
            var state = eager.newRun(LegacyProfile(), seed)
            while (!state.isEnded && state.day < 30) {
                val unable = state.heroes.values.filter { !it.isAlive || it.health < config.heroWoundedThreshold }.map { it.id.value }.toSet()
                val acc = eager.handle(state, Command.EndDay(endDayId(state))).acceptedOrThrow()
                for (e in acc.resolution!!.events.filter { it.type in hallAndAmbitionTypes }) {
                    assertTrue(e.subjectIds.none { it in unable }, "seed $seed day ${state.day}: ${e.text}")
                    seen[e.type] = (seen[e.type] ?: 0) + 1
                }
                state = acc.state
            }
            gone += state.heroes.values.count { !it.isAlive }
        }
        assertTrue(gone >= 10, "dead or retired heroes over the runs: $gone")
        assertEquals(hallAndAmbitionTypes, seen.keys)
    }

    @Test
    fun hallAndAmbitionDaysAreDeterministicSurviveASaveAndAreCountedBySimulator() {
        val seen = mutableSetOf<EventType>()
        val activities = mutableSetOf<String>()
        for (seed in 1L..8L) {
            val (statsA, a) = SimulationDriver(maxDays = 15).playRun(LegacyProfile(), seed, Policy.BALANCED_ACTIVE)
            val (statsB, b) = SimulationDriver(maxDays = 15).playRun(LegacyProfile(), seed, Policy.BALANCED_ACTIVE)
            assertEquals(a, b, "seed $seed: same seed and commands, same state")
            assertEquals(statsA, statsB)
            assertEquals(a, SaveCodec.decodeRun(SaveCodec.encodeRun(a)), "seed $seed: the new hero fields survive a save")
            seen += a.events.map { it.type }
            activities += statsA.activityDays.keys
            // Every hero alive at End Day did exactly one thing the simulator can name.
            assertTrue("NONE" !in statsA.activityDays, "seed $seed: ${statsA.activityDays}")
            assertEquals(statsA.mentorings, a.events.count { it.type == EventType.GUILD_MENTORED })
        }
        assertTrue(seen.containsAll(hallAndAmbitionTypes + EventType.GUILD_FOUNDED), "seen: ${seen.intersect(hallAndAmbitionTypes)}")
        assertTrue(activities.containsAll(listOf("EXPEDITION", "PATROL", "REST", "REST_WOUNDED", "GUILD") + Ambition.entries.map { "AMBITION_${it.name}" }), "activities: $activities")
    }
}
