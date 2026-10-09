package com.tinyblacksmith.core

import com.tinyblacksmith.core.TestSupport.endDayAccepted
import com.tinyblacksmith.core.TestSupport.engine
import com.tinyblacksmith.core.battle.Battle
import com.tinyblacksmith.core.config.WeaponFatesConfig
import com.tinyblacksmith.core.config.BalanceConfig
import com.tinyblacksmith.core.content.LaunchContent
import com.tinyblacksmith.core.engine.Command
import com.tinyblacksmith.core.engine.CommandOutcome
import com.tinyblacksmith.core.engine.GameEngine
import com.tinyblacksmith.core.engine.Invariants
import com.tinyblacksmith.core.engine.ResolutionContext
import com.tinyblacksmith.core.engine.WorldEvents
import com.tinyblacksmith.core.gazette.Gazette
import com.tinyblacksmith.core.market.Market
import com.tinyblacksmith.core.model.*
import com.tinyblacksmith.core.persistence.SaveCodec
import com.tinyblacksmith.core.rng.Rng
import com.tinyblacksmith.core.rng.RngStream
import com.tinyblacksmith.core.sim.Policy
import com.tinyblacksmith.core.sim.PolicySummary
import com.tinyblacksmith.core.sim.RunStats
import com.tinyblacksmith.core.sim.SimulationDriver
import com.tinyblacksmith.core.sim.Simulator
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Balance v5: replays for significant expeditions (GDD 6, 11) and the fates of a fallen hero's blade (GDD 7:
 * comrades, guild inheritance, merchant resale, seizure, loss).
 */
class ReplaysAndWeaponFatesTest {
    private val base = BalanceConfig.DEFAULT
    private val bladeId = WeaponId("w900")

    private fun weaponFates(change: WeaponFatesConfig.() -> WeaponFatesConfig) = base.copy(weaponFates = base.weaponFates.change())

    private fun endDay(eng: GameEngine, s: GameState): CommandOutcome.Accepted =
        eng.handle(s, Command.EndDay(CommandId("${s.runId.value}:day${s.day}"))) as CommandOutcome.Accepted

    private fun fates(events: List<EventRecord>): List<WeaponFate> = events.mapNotNull { it.data[WeaponFate.KEY] }.map { WeaponFate.valueOf(it) }

    private fun blade(id: WeaponId, location: WeaponLocation, power: Int = 30, fame: Int = 0, history: List<HistoryEntry> = emptyList()) = Weapon(
        id = id, name = "Test Blade ${id.value}", familyId = LaunchContent.SWORD, coreId = LaunchContent.IRON, augmentId = LaunchContent.EMBER_RESIN,
        mode = ForgeMode.QUICK, risk = Risk.BALANCED, quality = 50, rarity = Rarity.RARE, power = power, element = null,
        affixes = emptyList(), flaws = emptyList(), location = location, forgedEra = 1, forgedDay = 1, fame = fame, history = history,
    )

    /** A fresh run whose first hero carries [bladeId]. */
    private fun armedVictim(seed: Long, fame: Int = 0): Pair<GameState, HeroId> {
        val s = engine.newRun(LegacyProfile(), seed)
        val victim = s.aliveHeroes().first().id
        return s.copy(weapons = s.weapons + (bladeId to blade(bladeId, WeaponLocation.Owned(victim, true), fame = fame))) to victim
    }

    // ---- Feature A: replays ------------------------------------------------------------------------------------

    /**
     * Plays End Days and checks each day's replays against that day's event records: every significant expedition
     * (an elite fight, or one the hero died on) is replayed up to the cap, most significant first, after the siege,
     * and each replay's outcome and numbers are the linked record's. Returns how many replays illustrated each event type.
     */
    private fun playAndCheckReplays(eng: GameEngine, seeds: LongRange, days: Int): Map<EventType, Int> {
        val seen = mutableMapOf<EventType, Int>()
        val eliteNames = eng.content.factions.flatMap { it.eliteNames }
        val cap = eng.config.weaponFates.maxExpeditionReplaysPerDay
        for (seed in seeds) {
            var s = eng.newRun(LegacyProfile(), seed)
            while (!s.isEnded && s.day <= days) {
                val acc = endDay(eng, s)
                val res = acc.resolution!!
                val where = "seed $seed day ${res.day}"
                val byId = res.events.associateBy { it.id }
                val significant = res.events.filter { e ->
                    e.type == EventType.ELITE_SLAIN || (e.type == EventType.HERO_DIED && "fell to" in e.text) ||
                        (e.type == EventType.EXPEDITION_LOST && eliteNames.any { it in e.text })
                }
                val fights = res.replays.filter { it.kind == ReplayKind.EXPEDITION }
                val sieges = res.replays.filter { it.kind == ReplayKind.SIEGE }
                assertEquals(minOf(cap, significant.size), fights.size, where)
                assertEquals(sieges + fights, res.replays, "$where: the siege comes first")
                assertEquals(if (res.events.any { it.type == EventType.SIEGE_WON || it.type == EventType.SIEGE_LOST }) 1 else 0, sieges.size, where)
                val priorities = fights.map { byId.getValue(it.eventId!!).priority }
                assertEquals(priorities.sortedDescending(), priorities, "$where: most significant first")
                val dropped = significant.filter { e -> fights.none { it.eventId == e.id } }
                if (dropped.isNotEmpty() && priorities.isNotEmpty()) assertTrue(dropped.maxOf { it.priority } <= priorities.min(), "$where: the cap drops the least significant")
                assertEquals(fights.size, fights.map { it.title }.distinct().size, "$where: one replay per hero")
                for (r in fights) {
                    val e = byId.getValue(r.eventId!!)
                    assertTrue(e in significant, where)
                    val hero = acc.state.hero(HeroId(e.subjectIds.first()))
                    assertEquals(res.day, r.day)
                    assertTrue(r.title.startsWith("${hero.fullName} vs "), r.title)
                    assertEquals(3, r.rounds.size)
                    assertEquals(hero.fullName, r.rounds[0].attacker)
                    assertEquals(hero.fullName, r.rounds[1].defender)
                    val last = r.rounds[2]
                    when (e.type) {
                        EventType.ELITE_SLAIN -> {
                            assertTrue(r.outcome.startsWith("${hero.fullName} slew "), r.outcome)
                            assertEquals(hero.fullName, last.attacker)
                            assertTrue("with ${last.damage} gold in spoils" in e.text, "$where: the spoils shown are the record's: ${e.text} vs ${last.damage}")
                        }
                        EventType.EXPEDITION_LOST -> {
                            assertEquals("${hero.fullName} was driven back", r.outcome)
                            assertEquals(hero.fullName, last.defender)
                        }
                        else -> {
                            assertEquals(EventType.HERO_DIED, e.type)
                            assertEquals("${hero.fullName} fell", r.outcome)
                            assertEquals(hero.fullName, last.defender)
                            assertEquals(HeroFate.DEAD, hero.fate)
                        }
                    }
                    seen.merge(e.type, 1, Int::plus)
                }
                s = acc.state
            }
        }
        return seen
    }

    @Test
    fun everySignificantExpeditionGetsAReplayThatMatchesItsEventRecord() {
        // Every expedition meets an elite: a win and a rout both earn a replay.
        val elites = playAndCheckReplays(GameEngine(config = base.copy(eliteBaseChance = 1.0)), 1L..8L, 9)
        assertTrue((elites[EventType.ELITE_SLAIN] ?: 0) > 0 && (elites[EventType.EXPEDITION_LOST] ?: 0) > 0, elites.toString())
        // Every rout is fatal: a death earns a replay whatever the foe.
        val deaths = playAndCheckReplays(GameEngine(config = base.copy(expeditionDamageMin = 150, expeditionDamageMax = 150)), 1L..8L, 9)
        assertTrue((deaths[EventType.HERO_DIED] ?: 0) > 0, deaths.toString())
        // The game as shipped.
        val shipped = playAndCheckReplays(engine, 1L..40L, 30)
        assertTrue(shipped.values.sum() > 0, shipped.toString())
    }

    @Test
    fun aDayKeepsAtMostTheConfiguredFightsAndTheSaveOnlyTheLastDay() {
        for (cap in listOf(0, 1, 2)) {
            val eng = GameEngine(config = base.copy(eliteBaseChance = 1.0, weaponFates = base.weaponFates.copy(maxExpeditionReplaysPerDay = cap)))
            val seen = playAndCheckReplays(eng, 1L..4L, 8)  // asserts min(cap, significant) fights a day, the siege first
            assertEquals(cap == 0, seen.isEmpty(), "cap $cap: $seen")
        }
        val eng = GameEngine(config = base.copy(eliteBaseChance = 1.0))
        var s = eng.newRun(LegacyProfile(), 2)
        var busiestDay = 0
        repeat(12) {
            if (!s.isEnded) {
                s = endDay(eng, s).state
                busiestDay = maxOf(busiestDay, s.lastResolution!!.replays.size)
            }
        }
        val last = s.lastResolution!!
        assertTrue(busiestDay in 2..1 + base.weaponFates.maxExpeditionReplaysPerDay, "busiest day held $busiestDay replays")
        assertTrue(last.replays.all { it.day == last.day })
        // CombatReplay is the only saved type with an "outcome" field: the save holds the last day's replays and no others.
        assertEquals(last.replays.size, Regex("""\\"outcome\\":""").findAll(SaveCodec.encodeRun(s)).count())
        // A replay saved before v5 has no kind: it was a siege.
        val old = SaveCodec.json.decodeFromString(CombatReplay.serializer(), """{"title":"Siege of Emberfall, day 5","day":5,"rounds":[],"outcome":"Town held"}""")
        assertEquals(ReplayKind.SIEGE, old.kind)
        assertNull(old.eventId)
    }

    /**
     * The replay is built from numbers already rolled. An elite fight draws exactly what the resolver drew before
     * replays existed: variance, elite check, foe, outcome, then gold and the elite's material for a win (6 draws) or
     * the wound for a rout (5). A draw added to the replay would shift every later roll of the run and fail here.
     */
    @Test
    fun buildingAReplayDrawsNoRng() {
        val (s, victim) = armedVictim(4)
        val f = s.factions.values.sortedBy { it.id.value }.maxByOrNull { it.pressure }!!
        fun fight(config: BalanceConfig): Pair<ResolutionContext, Int> {
            val c = ResolutionContext(s, engine.content, config)
            Battle.resolveExpedition(c, c.hero(victim), c.equippedWeapon(victim), f.id, engine.content.faction(f.id))
            return c to combatDraws(s, c.toState())
        }
        val (won, winDraws) = fight(base.copy(eliteBaseChance = 1.0, winProbabilityFloor = 1.0, winProbabilityCeiling = 1.0))
        assertEquals(listOf(EventType.ELITE_SLAIN), won.newEvents.filter { it.type == EventType.ELITE_SLAIN || it.type == EventType.EXPEDITION_LOST }.map { it.type })
        assertEquals(1, won.replays.size)
        assertEquals(6, winDraws)
        val (routed, routDraws) = fight(base.copy(eliteBaseChance = 1.0, winProbabilityFloor = 0.0, winProbabilityCeiling = 0.0, expeditionDamageMin = 1, expeditionDamageMax = 1))
        assertEquals(listOf(EventType.EXPEDITION_LOST), routed.newEvents.filter { it.type == EventType.ELITE_SLAIN || it.type == EventType.EXPEDITION_LOST }.map { it.type })
        assertEquals(1, routed.replays.size)
        assertEquals(5, routDraws)
        // An ordinary rout earns no replay and draws the same five.
        val (plain, plainDraws) = fight(base.copy(eliteBaseChance = 0.0, eliteChancePerPressure = 0.0, winProbabilityFloor = 0.0, winProbabilityCeiling = 0.0, expeditionDamageMin = 1, expeditionDamageMax = 1))
        assertEquals(0, plain.replays.size)
        assertEquals(5, plainDraws)
    }

    @Test
    fun keepingOrDroppingReplaysNeverChangesTheRun() {
        fun play(cap: Int) = SimulationDriver(GameEngine(config = weaponFates { copy(maxExpeditionReplaysPerDay = cap) })).playRun(LegacyProfile(), 11, Policy.BALANCED_ACTIVE)
        val (statsA, a) = play(0)
        val (statsB, b) = play(3)
        assertEquals(statsA, statsB)
        assertEquals(a.rng, b.rng)
        assertEquals(a.copy(lastResolution = null), b.copy(lastResolution = null))
        assertEquals(a.lastResolution!!.copy(replays = emptyList()), b.lastResolution!!.copy(replays = emptyList()))
    }

    // ---- Feature B: weapon fates -------------------------------------------------------------------------------

    /** Kills [victim] with the caller's two rolls as given; returns the state after and the COMBAT draws the fate itself consumed. */
    private fun kill(state: GameState, victim: HeroId, recovered: Boolean, seized: Boolean, config: BalanceConfig = base): Pair<GameState, Int> {
        val c = ResolutionContext(state, engine.content, config)
        Battle.kill(c, c.hero(victim), "fell to a test", recovered, seized)
        val after = c.toState()
        assertEquals(emptyList(), Invariants.check(after, config))
        assertEquals(HeroFate.DEAD, after.hero(victim).fate)
        assertTrue(after.weapons.values.none { it.ownerId == victim }, "dead heroes own nothing")
        return after to combatDraws(state, after)
    }

    /** How many values the COMBAT stream gave out between two states; no other stream may have moved. */
    private fun combatDraws(before: GameState, after: GameState): Int {
        for (stream in RngStream.entries) if (stream != RngStream.COMBAT) assertEquals(before.rng.stateOf(stream), after.rng.stateOf(stream), "$stream untouched")
        val probe = Rng(before.rng.stateOf(RngStream.COMBAT))
        var draws = 0
        while (probe.state != after.rng.stateOf(RngStream.COMBAT)) { probe.nextLong(); draws++; check(draws < 20) { "more than twenty draws" } }
        return draws
    }

    @Test
    fun aFallenHerosBladeMeetsOneFateWithARecordAndABoundedNumberOfDraws() {
        val (s, victim) = armedVictim(6)
        val name = s.hero(victim).fullName
        val sure = weaponFates { copy(merchantBaseChance = 1.0, merchantMaxChance = 1.0) }
        val unlikely = weaponFates { copy(merchantBaseChance = 1e-12, merchantChancePerFame = 0.0) }
        val never = weaponFates { copy(merchantBaseChance = 0.0, merchantChancePerFame = 0.0) }
        data class Case(val recovered: Boolean, val seized: Boolean, val config: BalanceConfig, val fate: WeaponFate, val draws: Int, val kind: String, val type: EventType, val location: (Int) -> WeaponLocation)
        val cases = listOf(
            Case(true, false, base, WeaponFate.RECOVERED, 0, "RECOVERED", EventType.WEAPON_RECOVERED) { WeaponLocation.Storage },
            Case(true, true, base, WeaponFate.RECOVERED, 0, "RECOVERED", EventType.WEAPON_RECOVERED) { WeaponLocation.Storage },
            Case(false, true, base, WeaponFate.SEIZED, 0, "SEIZED", EventType.WEAPON_STOLEN) { WeaponLocation.Lost(it, "seized") },
            Case(false, false, sure, WeaponFate.MERCHANT, 1, "SCAVENGED", EventType.WEAPON_LOST) { WeaponLocation.Lost(it, WeaponLocation.Lost.WITH_MERCHANT) },
            Case(false, false, unlikely, WeaponFate.LOST, 1, "LOST", EventType.WEAPON_LOST) { WeaponLocation.Lost(it, "lost with $name") },
            Case(false, false, never, WeaponFate.LOST, 0, "LOST", EventType.WEAPON_LOST) { WeaponLocation.Lost(it, "lost with $name") },
        )
        for (case in cases) {
            val (after, draws) = kill(s, victim, case.recovered, case.seized, case.config)
            val what = "${case.fate} (recovered=${case.recovered}, seized=${case.seized})"
            val w = after.weapon(bladeId)
            assertEquals(case.location(after.day), w.location, what)
            assertEquals(case.fate == WeaponFate.MERCHANT, w.isWithMerchant, what)
            assertEquals(case.draws, draws, "$what: draws")
            val told = after.events.filter { WeaponFate.KEY in it.data }
            assertEquals(listOf(case.fate), fates(told), what)
            assertEquals(case.type, told.single().type, what)
            assertEquals(listOf(bladeId.value, victim.value), told.single().subjectIds, "$what: real subject IDs")
            assertEquals(case.kind, w.history.last().kind, what)
            assertEquals(listOf(victim.value), w.history.last().subjectIds, what)
        }
        // A spare blade is lost with the hero, quietly, and a bare-handed hero's death draws nothing.
        val spareId = WeaponId("w901")
        val (withSpare, _) = kill(s.copy(weapons = s.weapons + (spareId to blade(spareId, WeaponLocation.Owned(victim, false)))), victim, recovered = true, seized = false)
        assertEquals(WeaponLocation.Lost(withSpare.day, "lost with $name"), withSpare.weapon(spareId).location)
        assertEquals(1, withSpare.events.count { WeaponFate.KEY in it.data })
        val (bare, draws) = kill(s.copy(weapons = emptyMap()), victim, recovered = false, seized = false)
        assertEquals(0, draws)
        assertTrue(bare.events.none { WeaponFate.KEY in it.data })
    }

    @Test
    fun aGuildBladePassesOnlyToALivingGuildmate() {
        val (s0, victim) = armedVictim(6)
        val others = s0.aliveHeroes().filter { it.id != victim }
        val mate = others[0].id
        val second = others[1].id
        fun inGuild(state: GameState, ids: List<HeroId>, guild: String = "g1") = state.copy(heroes = state.heroes + ids.map { it to state.hero(it).copy(guildId = guild) })
        val s = inGuild(s0.copy(town = s0.town.copy(guilds = listOf(Guild("g1", "the Test Company", victim, 1)))), listOf(victim, mate))
        val name = s.hero(victim).fullName
        val mateName = s.hero(mate).fullName
        val sure = weaponFates { copy(guildInheritanceChance = 1.0) }

        // Comrades brought it home, or it would have been lost: either way the guild keeps it, on one draw.
        for (recovered in listOf(true, false)) {
            val (after, draws) = kill(s, victim, recovered, seized = false, config = sure)
            val w = after.weapon(bladeId)
            assertEquals(WeaponLocation.Owned(mate, equipped = true), w.location, "an unarmed heir wields it")
            assertEquals(1, draws)
            val told = after.events.single { WeaponFate.KEY in it.data }
            assertEquals(EventType.WEAPON_INHERITED, told.type)
            assertEquals(WeaponFate.INHERITED.name, told.data[WeaponFate.KEY])
            assertEquals(listOf(bladeId.value, mate.value, victim.value), told.subjectIds)
            assertEquals("${w.name} passed from the fallen $name to guildmate $mateName.", told.text)
            assertEquals(listOf("INHERITED", "EQUIPPED"), w.history.takeLast(2).map { it.kind })
        }
        // What the enemy seized, the guild cannot claim.
        val (seized, seizedDraws) = kill(s, victim, recovered = false, seized = true, config = sure)
        assertEquals(WeaponLocation.Lost(seized.day, "seized"), seized.weapon(bladeId).location)
        assertEquals(0, seizedDraws)
        // No living guildmate, no claim: a dead or retired member, or a hero of another guild, inherits nothing.
        val ineligible = listOf(
            s.copy(heroes = s.heroes + (mate to s.hero(mate).copy(fate = HeroFate.DEAD, health = 0, diedOnDay = 1)), town = s.town.copy(championIds = s.town.championIds - mate)),
            s.copy(heroes = s.heroes + (mate to s.hero(mate).copy(fate = HeroFate.RETIRED, retiredOnDay = 1)), town = s.town.copy(championIds = s.town.championIds - mate)),
            inGuild(s, listOf(mate), guild = "g2"),
            s.copy(heroes = s.heroes + (victim to s.hero(victim).copy(guildId = null))),
        )
        for (state in ineligible) {
            val (after, draws) = kill(state, victim, recovered = true, seized = false, config = sure)
            assertTrue(after.weapon(bladeId).isInStorage)
            assertEquals(0, draws)
        }
        // A chance of zero draws nothing; the default is a chance, not a rule.
        assertEquals(0, kill(s, victim, recovered = true, seized = false, config = weaponFates { copy(guildInheritanceChance = 0.0) }).second)
        val inherited = (1L..200L).count { seed ->
            val reseeded = s.copy(rng = engine.newRun(LegacyProfile(), seed).rng)
            kill(reseeded, victim, recovered = true, seized = false).first.weapon(bladeId).ownerId == mate
        }
        assertTrue(inherited in 90..150, "about ${base.weaponFates.guildInheritanceChance} of 200, was $inherited")
        // The mentee rule of Heroes.retire: an heir who carries better keeps it in hand and the blade as a spare.
        val betterId = WeaponId("w902")
        val wellArmed = s.copy(weapons = s.weapons + (betterId to blade(betterId, WeaponLocation.Owned(mate, true), power = 300)))
        val (spare, _) = kill(wellArmed, victim, recovered = true, seized = false, config = sure)
        assertEquals(WeaponLocation.Owned(mate, equipped = false), spare.weapon(bladeId).location)
        assertTrue(spare.weapon(betterId).isEquipped)
        // Of two living guildmates the blade goes to the one it serves best: the unarmed one, though the other comes first by ID.
        val (two, _) = kill(inGuild(wellArmed, listOf(second)), victim, recovered = true, seized = false, config = sure)
        assertEquals(WeaponLocation.Owned(second, equipped = true), two.weapon(bladeId).location)
    }

    /** One fatal expedition per seed for a hero carrying [bladeId]; tallies the blade's fate. */
    private fun expeditionFates(elite: Boolean, seeds: LongRange, fame: Int = 0): Map<WeaponFate, Int> {
        val fatal = base.copy(
            winProbabilityFloor = 0.0, winProbabilityCeiling = 0.0, expeditionDamageMin = 500, expeditionDamageMax = 500,
            eliteBaseChance = if (elite) 1.0 else 0.0, eliteChancePerPressure = 0.0,
        )
        val tally = mutableMapOf<WeaponFate, Int>()
        for (seed in seeds) {
            val (s, victim) = armedVictim(seed, fame)
            val c = ResolutionContext(s, engine.content, fatal)
            val f = s.factions.values.sortedBy { it.id.value }.maxByOrNull { it.pressure }!!
            Battle.resolveExpedition(c, c.hero(victim), c.equippedWeapon(victim), f.id, engine.content.faction(f.id))
            val after = c.toState()
            assertEquals(emptyList(), Invariants.check(after, fatal))
            assertEquals(HeroFate.DEAD, after.hero(victim).fate)
            val eliteNames = engine.content.faction(f.id).eliteNames
            assertEquals(if (elite) 1 else 0, c.newEvents.count { e -> e.type == EventType.HERO_DIED && eliteNames.any { it in e.text } }, "seed $seed: the foe")
            tally.merge(fates(c.newEvents).single(), 1, Int::plus)
        }
        return tally
    }

    /** One lost siege per seed in which every champion falls on the walls; tallies their blades' fates. */
    private fun wallsFates(seeds: LongRange): Map<WeaponFate, Int> {
        val overrun = base.copy(siegeModifier = 50.0, championSiegeDamageOnLoss = 500)
        val tally = mutableMapOf<WeaponFate, Int>()
        for (seed in seeds) {
            val s0 = engine.newRun(LegacyProfile(), seed)
            val blades = s0.aliveHeroes().mapIndexed { i, h -> WeaponId("w9$i").let { it to blade(it, WeaponLocation.Owned(h.id, true)) } }
            val c = ResolutionContext(s0.copy(weapons = s0.weapons + blades, town = s0.town.copy(nextSiegeDay = s0.day)), engine.content, overrun)
            Battle.resolveSiegeIfDue(c)
            assertEquals(emptyList(), Invariants.check(c.toState(), overrun))
            assertEquals(base.championCount, c.newEvents.count { it.type == EventType.HERO_DIED && "died defending the walls" in it.text })
            fates(c.newEvents).forEach { tally.merge(it, 1, Int::plus) }
        }
        return tally
    }

    private fun Map<WeaponFate, Int>.share(fate: WeaponFate): Double = (this[fate] ?: 0).toDouble() / values.sum()

    @Test
    fun theOddsDependOnWhereAndHowTheHeroFellAndFameNeverGuaranteesAReturn() {
        val road = expeditionFates(elite = false, seeds = 1L..600L)
        val elite = expeditionFates(elite = true, seeds = 1L..600L)
        val walls = wallsFates(1L..200L)
        val context = "road=$road elite=$elite walls=$walls"
        // Every fate that needs no guild is reachable from a death on the road.
        for (fate in listOf(WeaponFate.RECOVERED, WeaponFate.SEIZED, WeaponFate.MERCHANT, WeaponFate.LOST)) assertTrue((road[fate] ?: 0) > 0, "$fate: $context")
        assertTrue(road.share(WeaponFate.RECOVERED) in 0.42..0.58, context)
        assertTrue(walls.share(WeaponFate.RECOVERED) > road.share(WeaponFate.RECOVERED) + 0.1, "comrades are close on the walls: $context")
        assertTrue(elite.share(WeaponFate.RECOVERED) < road.share(WeaponFate.RECOVERED) - 0.05, "an elite gives little back: $context")
        assertTrue(elite.share(WeaponFate.SEIZED) > road.share(WeaponFate.SEIZED) + 0.1, "an elite keeps its trophy: $context")

        // Fame raises the chance that a lost blade surfaces, up to a ceiling below certainty.
        val chances = (0..50).map { Battle.merchantChance(blade(bladeId, WeaponLocation.Storage, fame = it), base) }
        assertEquals(base.weaponFates.merchantBaseChance, chances.first(), 1e-9)
        assertEquals(base.weaponFates.merchantBaseChance, Battle.merchantChance(blade(bladeId, WeaponLocation.Storage, fame = -5), base), 1e-9)
        assertEquals(chances.sorted(), chances, "more fame never lowers the chance")
        assertTrue(chances[5] > chances[0])
        assertEquals(base.weaponFates.merchantMaxChance, chances.last(), 1e-9)
        assertTrue(chances.all { it <= base.weaponFates.merchantMaxChance } && base.weaponFates.merchantMaxChance < 1.0, "bounded, never a guarantee")
        val famed = expeditionFates(elite = false, seeds = 1L..600L, fame = 1000)
        fun surfaced(t: Map<WeaponFate, Int>) = (t[WeaponFate.MERCHANT] ?: 0).toDouble() / ((t[WeaponFate.MERCHANT] ?: 0) + (t[WeaponFate.LOST] ?: 0))
        assertTrue(surfaced(famed) > surfaced(road) + 0.15, "famed=$famed road=$road")
        assertTrue((famed[WeaponFate.LOST] ?: 0) > 0, "even a legend can be lost: $famed")
    }

    /** One End Day's merchant step on [day]; asserts it draws no RNG and leaves the invariants standing. */
    private fun merchantDay(state: GameState, day: Int, config: BalanceConfig = base): Pair<GameState, List<EventRecord>> {
        val c = ResolutionContext(state.copy(day = day), engine.content, config)
        Market.resolveMerchant(c)
        val after = c.toState()
        assertEquals(emptyList(), Invariants.check(after, config), "day $day")
        assertEquals(state.rng, after.rng, "the merchant draws no RNG")
        return after to c.newEvents.toList()
    }

    /** A run in which [bladeId] fell with the first hero on day 3 and a merchant has it; every other hero is broke and bare-handed. */
    private fun merchantState(): Pair<GameState, List<Hero>> {
        val s0 = engine.newRun(LegacyProfile(), 9)
        val heroes = s0.aliveHeroes()
        val fallen = heroes.first()
        val w = blade(bladeId, WeaponLocation.Lost(3, WeaponLocation.Lost.WITH_MERCHANT), history = listOf(HistoryEntry(1, 3, "SCAVENGED", "Taken from the field where ${fallen.fullName} fell.", listOf(fallen.id.value))))
        val s = s0.copy(
            weapons = s0.weapons + (bladeId to w),
            heroes = s0.heroes.mapValues { (id, h) -> if (id == fallen.id) h.copy(fate = HeroFate.DEAD, health = 0, diedOnDay = 3) else h.copy(gold = 0) },
            town = s0.town.copy(championIds = s0.town.championIds - fallen.id),
        )
        return s to heroes
    }

    @Test
    fun theMerchantArrivesAfterTheDelayAndSellsToTheHeroWhoValuesTheBladeMostAndCanPay() {
        val (s0, heroes) = merchantState()
        val fallen = heroes[0]
        val price = Market.askingPrice(s0.weapon(bladeId), base)
        assertEquals(120, price)
        // Four heroes eye the blade: one who would prize it but is a coin short, one rich but better armed, and two
        // who can pay. Of those two the Guardian (swords suit the class) values it more than the Ranger, who comes first by ID.
        val poor = heroes[1].id
        val armed = heroes[2].id
        val ranger = heroes[3].id
        val guardian = heroes[4].id
        val ownId = WeaponId("w901")
        val s = s0.copy(
            heroes = s0.heroes +
                (poor to s0.hero(poor).copy(classId = LaunchContent.GUARDIAN, gold = price - 1)) +
                (armed to s0.hero(armed).copy(classId = LaunchContent.GUARDIAN, gold = 10_000)) +
                (ranger to s0.hero(ranger).copy(classId = LaunchContent.RANGER, gold = price + 10)) +
                (guardian to s0.hero(guardian).copy(classId = LaunchContent.GUARDIAN, gold = price + 10)),
            weapons = s0.weapons + (ownId to blade(ownId, WeaponLocation.Owned(armed, true), power = 300)),
        )
        val delay = base.weaponFates.merchantDelayDays
        // Before the merchant arrives nothing happens.
        for (day in 3 until 3 + delay) {
            val (waiting, events) = merchantDay(s, day)
            assertEquals(emptyList(), events)
            assertTrue(waiting.weapon(bladeId).isWithMerchant)
        }
        val (sold, events) = merchantDay(s, 3 + delay)
        assertEquals(listOf(EventType.WEAPON_SURFACED, EventType.WEAPON_EQUIPPED, EventType.WEAPON_RESOLD), events.map { it.type })
        assertEquals(listOf(bladeId.value, fallen.id.value), events[0].subjectIds)
        assertTrue(fallen.fullName in events[0].text && s.weapon(bladeId).name in events[0].text)
        val resold = events[2]
        assertEquals(listOf(guardian.value, bladeId.value), resold.subjectIds)
        assertEquals(mapOf("price" to "$price", WeaponFate.KEY to WeaponFate.RESOLD.name), resold.data)
        assertTrue(resold.text.startsWith("${s.hero(guardian).fullName} bought "), resold.text)
        assertEquals(WeaponLocation.Owned(guardian, equipped = true), sold.weapon(bladeId).location)
        assertEquals(listOf("RESOLD", "EQUIPPED"), sold.weapon(bladeId).history.takeLast(2).map { it.kind })
        // The hero pays the merchant: the gold leaves the economy and the smith sees none of it.
        assertEquals(s.hero(guardian).gold - price, sold.hero(guardian).gold)
        assertEquals(s.heroes.values.sumOf { it.gold } - price, sold.heroes.values.sumOf { it.gold })
        assertEquals(s.gold, sold.gold)
        assertEquals(s.reputation, sold.reputation)
        // Without the Guardian the Ranger is the best who can pay; the coin-short and the better-armed never buy.
        val (second, _) = merchantDay(s.copy(heroes = s.heroes + (guardian to s.hero(guardian).copy(gold = 0))), 3 + delay)
        assertEquals(ranger, second.weapon(bladeId).ownerId)
        val (unsold, onlyArrival) = merchantDay(s.copy(heroes = s.heroes + (guardian to s.hero(guardian).copy(gold = 0)) + (ranger to s.hero(ranger).copy(gold = 0))), 3 + delay)
        assertEquals(listOf(EventType.WEAPON_SURFACED), onlyArrival.map { it.type })
        assertTrue(unsold.weapon(bladeId).isWithMerchant)
    }

    @Test
    fun anUnsoldBladeLeavesWithTheMerchantForGood() {
        val (s, heroes) = merchantState()
        val fallen = heroes[0]
        val arrives = 3 + base.weaponFates.merchantDelayDays
        val leaves = arrives + base.weaponFates.merchantStayDays - 1
        var state = s
        for (day in 3..leaves) {
            val (next, events) = merchantDay(state, day)
            val expected = when (day) {
                arrives -> listOf(EventType.WEAPON_SURFACED)
                leaves -> listOf(EventType.WEAPON_LOST)
                else -> emptyList()
            }
            assertEquals(expected, events.map { it.type }, "day $day")
            assertEquals(day < leaves, next.weapon(bladeId).isWithMerchant, "day $day")
            assertFalse(WorldEvents.canFire(ResolutionContext(next, engine.content, base), WorldEvents.byId("heroic_inheritance")), "day $day: not a blade comrades can carry home")
            if (day == leaves) {
                assertEquals(WeaponLocation.Lost(leaves, "carried off by a travelling merchant"), next.weapon(bladeId).location)
                assertEquals(mapOf(WeaponFate.KEY to WeaponFate.LOST.name), events.single().data)
                assertEquals(listOf(bladeId.value, fallen.id.value), events.single().subjectIds)
                assertEquals("LOST", next.weapon(bladeId).history.last().kind)
            }
            state = next
        }
        // The longest stay is delay + stay - 1 days after the death; the invariant allows one day of slack (a delay of 0 is legal) and names a blade held past that.
        assertEquals(4, leaves - 3)
        assertEquals(emptyList(), Invariants.check(s.copy(day = leaves + 1), base))
        assertTrue(Invariants.check(s.copy(day = leaves + 2), base).any { "held by a merchant" in it })
        // A stay of one day, or none, still offers the blade once and then lets it go.
        for (stay in listOf(1, 0)) {
            val short = weaponFates { copy(merchantStayDays = stay) }
            val (gone, events) = merchantDay(s, arrives, short)
            assertEquals(listOf(EventType.WEAPON_SURFACED, EventType.WEAPON_LOST), events.map { it.type })
            assertFalse(gone.weapon(bladeId).isWithMerchant)
        }
    }

    @Test
    fun endDayRunsTheMerchantWithoutAPlayerCommand() {
        var s = engine.newRun(LegacyProfile(), 5)
        repeat(2) { s = s.endDayAccepted().state }
        assertEquals(3, s.day)
        val fallen = s.aliveHeroes().first()
        val fellOn = s.day - base.weaponFates.merchantDelayDays
        fun withBlade(power: Int, heroGold: Int?) = s.copy(
            weapons = s.weapons + (bladeId to blade(bladeId, WeaponLocation.Lost(fellOn, WeaponLocation.Lost.WITH_MERCHANT), power = power,
                history = listOf(HistoryEntry(1, fellOn, "SCAVENGED", "Taken from the field where ${fallen.fullName} fell.", listOf(fallen.id.value))))),
            heroes = s.heroes.mapValues { (id, h) -> if (id == fallen.id) h.copy(fate = HeroFate.DEAD, health = 0, diedOnDay = fellOn) else if (heroGold != null) h.copy(gold = heroGold) else h },
            town = s.town.copy(championIds = s.town.championIds - fallen.id),
        )
        // Priced out of every purse: it arrives today, waits, and is gone by the last day of the stay.
        var day = withBlade(power = 5000, heroGold = null).endDayAccepted()
        assertEquals(1, day.resolution!!.events.count { it.type == EventType.WEAPON_SURFACED && bladeId.value in it.subjectIds })
        assertTrue(day.state.weapon(bladeId).isWithMerchant)
        repeat(base.weaponFates.merchantStayDays - 1) {
            assertTrue(day.state.weapon(bladeId).isWithMerchant)
            day = day.state.endDayAccepted()
            assertEquals(0, day.resolution!!.events.count { it.type == EventType.WEAPON_SURFACED })
        }
        assertFalse(day.state.weapon(bladeId).isWithMerchant)
        assertEquals(listOf(WeaponFate.LOST), fates(day.resolution!!.events.filter { bladeId.value in it.subjectIds }))
        // Within reach of a full purse: a living hero buys it the day it arrives, and the smith's gold does not move (the shelves are bare).
        val rich = withBlade(power = 40, heroGold = 5000)
        val sold = rich.endDayAccepted()
        val resold = sold.resolution!!.events.single { it.type == EventType.WEAPON_RESOLD }
        val buyer = HeroId(resold.subjectIds.first())
        assertTrue(rich.hero(buyer).isAlive && buyer != fallen.id)
        assertEquals(bladeId.value, resold.subjectIds[1])
        assertTrue(sold.state.weapon(bladeId).history.any { it.kind == "RESOLD" && it.subjectIds == listOf(buyer.value) })
        assertEquals(rich.gold, sold.state.gold)
        val edition = Gazette.edition(sold.resolution!!.events, sold.state.heroes.values.associate { it.id.value to it.fullName }, sold.resolution!!.visits)
        assertTrue(edition.sections.first { it.title == Gazette.HEROES }.lines.any { "A travelling merchant reached Emberfall" in it })
    }

    @Test
    fun theGazetteTellsEveryFate() {
        val names = mapOf("h1" to "Mira Ashwood", "h2" to "Bram Holt")
        var n = 0
        fun ev(type: EventType, priority: Int, text: String, subjects: List<String>, data: Map<String, String> = emptyMap()) = EventRecord("e${n++}", 1, 7, type, priority, text, subjects, data)
        val surfaced = ev(EventType.WEAPON_SURFACED, 4, "A travelling merchant reached Emberfall offering Stormwhisper, the blade Mira Ashwood fell with.", listOf("w3", "h1"))
        val equipped = ev(EventType.WEAPON_EQUIPPED, 2, "Bram Holt now wields Stormwhisper.", listOf("h2", "w3"))
        val resold = ev(EventType.WEAPON_RESOLD, 5, "Bram Holt bought Stormwhisper, the blade Mira Ashwood fell with, from a travelling merchant for 120 gold.", listOf("h2", "w3"), mapOf("price" to "120", WeaponFate.KEY to "RESOLD"))
        val won = ev(EventType.EXPEDITION_WON, 5, "Bram Holt routed Ashclaw scouts using Stormwhisper.", listOf("h2", "w3"), mapOf("winProbability" to "0.60"))
        val e = Gazette.edition(listOf(surfaced, equipped, resold, won), names)
        // A blade's story sits with the heroes: the arrival under the fallen hero, the sale folded into the buyer's day.
        assertEquals(
            listOf(
                "Bram Holt bought Stormwhisper, the blade Mira Ashwood fell with, from a travelling merchant for 120 gold; routed Ashclaw scouts using Stormwhisper.",
                surfaced.text,
            ),
            e.sections.single().lines,
        )
        assertEquals(Gazette.HEROES, e.sections.single().title)
        assertEquals(listOf("Expeditions: 1 won, 0 lost"), e.tally, "the merchant's sale is no gold for the shop")

        // Over a long simulated run every record of a fate is in that day's paper.
        var checked = 0
        SimulationDriver(GameEngine(config = Simulator.forcedSurvival()), maxDays = 200, maxForgesPerDay = 1) { s, _ ->
            val res = s.lastResolution!!
            val told = res.events.filter { WeaponFate.KEY in it.data || it.type == EventType.WEAPON_SURFACED }
            if (told.isNotEmpty()) {
                val heroNames = s.heroes.values.associate { it.id.value to it.fullName }
                val lines = Gazette.edition(res.events, heroNames, res.visits).let { it.lede + it.sections.flatMap { section -> section.lines } }
                for (record in told) {
                    val hero = record.subjectIds.firstOrNull { it.startsWith("h") }?.let { heroNames[it] }
                    val needle = (if (hero != null) record.text.removePrefix("$hero ") else record.text).trimEnd('.')
                    assertTrue(lines.any { needle in it }, "day ${res.day}: record not in the paper: ${record.type} ${record.text}")
                    checked++
                }
            }
        }.playRun(LegacyProfile(), 3, Policy.BALANCED_FAIR)
        assertTrue(checked > 5, "only $checked fate records in 200 days")
    }

    @Test
    fun invariantsHoldAndEveryMerchantBladeSettlesOverManySeeds() {
        val totals = WeaponFate.entries.associateWith { 0 }.toMutableMap()
        fun check(what: String, stats: RunStats, state: GameState) {
            // Every record of a fate is of a type the event log keeps for the whole run, so the log and the simulator's counters agree.
            val told = state.events.filter { WeaponFate.KEY in it.data }
            for (fate in WeaponFate.entries) assertEquals(told.count { it.data[WeaponFate.KEY] == fate.name }, stats.weaponFates.getValue(fate), "$what: $fate")
            val taken = told.count { it.data[WeaponFate.KEY] == WeaponFate.MERCHANT.name }
            val resold = told.count { it.data[WeaponFate.KEY] == WeaponFate.RESOLD.name }
            val carriedOff = told.count { it.data[WeaponFate.KEY] == WeaponFate.LOST.name && "travelling merchant" in it.text }
            val held = state.weapons.values.count { it.isWithMerchant }
            assertEquals(taken, resold + carriedOff + held, "$what: every blade a merchant took is sold, carried off or still on offer")
            assertTrue(state.weapons.values.all { w -> w.ownerId?.let { state.hero(it).isAlive } ?: true }, "$what: dead and retired heroes own nothing")
            assertEquals(emptyList(), Invariants.check(state, base, engine.shelfSlots(state)), what)
            stats.weaponFates.forEach { (fate, count) -> totals[fate] = totals.getValue(fate) + count }
        }
        // The engine asserts the invariants after every command, so a violation on any day of any run fails the run.
        val driver = SimulationDriver()
        for (seed in 1L..120L) driver.playRun(LegacyProfile(), seed, Policy.BALANCED_ACTIVE).let { (stats, state) -> check("seed $seed", stats, state) }
        val long = SimulationDriver(GameEngine(config = Simulator.forcedSurvival()), maxDays = 200, maxForgesPerDay = 1)
        for (seed in 1L..12L) long.playRun(LegacyProfile(), seed, Policy.BALANCED_FAIR).let { (stats, state) -> check("forced survival seed $seed", stats, state) }
        // Guild inheritance needs a second living member of the fallen hero's guild; it is covered by aGuildBladePassesOnlyToALivingGuildmate.
        for (fate in listOf(WeaponFate.RECOVERED, WeaponFate.SEIZED, WeaponFate.LOST, WeaponFate.MERCHANT, WeaponFate.RESOLD)) assertTrue(totals.getValue(fate) > 0, "$fate never occurred: $totals")
    }

    @Test
    fun fatesAndReplaysReplayExactlyFromTheSeedAndSurviveAReload() {
        val eng = GameEngine(config = Simulator.forcedSurvival())
        fun play() = SimulationDriver(eng, maxDays = 150, maxForgesPerDay = 1).playRun(LegacyProfile(), 7, Policy.BALANCED_FAIR)
        val (a, sa) = play()
        val (b, sb) = play()
        assertEquals(a, b)
        assertEquals(SaveCodec.encodeRun(sa), SaveCodec.encodeRun(sb))
        assertTrue(a.heroDeaths > 0 && a.weaponFates.values.sum() > 0, "the run must exercise the fates: $a")
        // A save taken while a merchant holds a blade resumes to the same outcome, and the blade settles within the stay.
        val held = (1L..10L).firstNotNullOf { seed ->
            var found: GameState? = null
            SimulationDriver(eng, maxDays = 300, maxForgesPerDay = 1) { s, _ -> if (found == null && s.weapons.values.any { it.isWithMerchant }) found = s }.playRun(LegacyProfile(), seed, Policy.BALANCED_FAIR)
            found
        }
        val before = held.weapons.values.filter { it.isWithMerchant }.associate { it.id to it.location }
        var live = held
        var reloaded = SaveCodec.decodeRun(SaveCodec.encodeRun(held))
        assertEquals(live, reloaded)
        repeat(base.weaponFates.merchantDelayDays + base.weaponFates.merchantStayDays) {
            live = endDay(eng, live).state
            reloaded = endDay(eng, reloaded).state
        }
        assertEquals(live, reloaded)
        assertEquals(SaveCodec.encodeRun(live), SaveCodec.encodeRun(reloaded))
        before.forEach { (id, location) -> assertNotEquals(location, live.weapon(id).location, "${id.value} settled") }
    }

    @Test
    fun theSimulatorReportsFatesAndArtifactRecovery() {
        val report = Simulator.run(runs = 24, baseSeed = 1, policies = listOf(Policy.BALANCED_FAIR), config = Simulator.forcedSurvival(), maxDays = 120).single()
        val s = report.summary()
        assertEquals(WeaponFate.entries.toSet(), s.weaponFatesPerRun.keys)
        fun total(fate: WeaponFate) = report.runs.sumOf { it.weaponFates.getValue(fate) }
        val returned = total(WeaponFate.RECOVERED) + total(WeaponFate.INHERITED) + total(WeaponFate.RESOLD)
        val settled = returned + total(WeaponFate.SEIZED) + total(WeaponFate.LOST)
        assertTrue(settled > 0)
        assertEquals(returned.toDouble() / settled, s.artifactRecoveryRate, 1e-9)
        assertTrue(s.artifactRecoveryRate > 0.0 && s.artifactRecoveryRate < 1.0)
        assertEquals(total(WeaponFate.SEIZED).toDouble() / 24, s.weaponFatesPerRun.getValue(WeaponFate.SEIZED), 1e-9)
        val text = report.render()
        assertTrue("weapon fates on death/run: recovered=" in text && "artifact recovery: " in text, text)
        val json = Json.encodeToString(PolicySummary.serializer(), s)
        assertTrue("\"artifactRecoveryRate\"" in json && "\"weaponFatesPerRun\"" in json)
        assertNotNull(Json.decodeFromString(PolicySummary.serializer(), json).weaponFatesPerRun[WeaponFate.RESOLD])
    }
}
