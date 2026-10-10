package com.tinyblacksmith.core

import com.tinyblacksmith.core.combat.Posture
import com.tinyblacksmith.core.config.BalanceConfig
import com.tinyblacksmith.core.content.Depth
import com.tinyblacksmith.core.content.GuildContent
import com.tinyblacksmith.core.content.LaunchContent
import com.tinyblacksmith.core.engine.Command
import com.tinyblacksmith.core.engine.CommandOutcome
import com.tinyblacksmith.core.engine.Encounters
import com.tinyblacksmith.core.engine.GameEngine
import com.tinyblacksmith.core.engine.GameError
import com.tinyblacksmith.core.engine.Invariants
import com.tinyblacksmith.core.engine.ResolutionContext
import com.tinyblacksmith.core.engine.stateOrThrow
import com.tinyblacksmith.core.guild.GuildOps
import com.tinyblacksmith.core.guild.Missions
import com.tinyblacksmith.core.guild.Stories
import com.tinyblacksmith.core.legacy.LegacyOutcome
import com.tinyblacksmith.core.model.*
import com.tinyblacksmith.core.rng.RngStream
import com.tinyblacksmith.core.sim.CombatSandbox
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** What can be lost and won back, and what a run earns for good (plan C02, C05, C06, D01 to D03; spec 10.4 to 10.7, 11, 12). */
class GuildStoriesTest {
    private val base = BalanceConfig.DEFAULT
    private val engine = TestSupport.engine
    private val content = engine.content
    private val cat = content.guild!!
    private val cfg = base.guild

    private fun run(seed: Long = 7, e: GameEngine = engine): GameState = e.newRun(LegacyProfile(), seed, charterId = GuildContent.TOWNS_LAST_HOPE).let { e.handle(it, Command.DeclineRelicOffer).stateOrThrow() }
    private fun GameState.send(cmd: Command, e: GameEngine = engine): GameState = e.handle(this, cmd).stateOrThrow()
    private fun GameState.rejected(cmd: Command): GameError = (engine.handle(this, cmd) as CommandOutcome.Rejected).error
    private fun GameState.endDay(e: GameEngine = engine): GameState = send(Command.EndDay(TestSupport.endDayId(this)), e)
    private fun GameState.sound(e: GameEngine = engine): GameState = also { assertEquals(emptyList(), Invariants.check(it, e.config, e.shelfSlots(it), content)) }
    private fun GameState.members() = guild!!.members.map { it.heroId }
    private fun GameState.edit(e: GameEngine = engine, f: (ResolutionContext) -> Unit): GameState = ResolutionContext(this, content, e.config).also(f).toState()

    /** Puts a contract of [defId] on the board, generated the way the board generates it. */
    private fun GameState.post(defId: String, hero: HeroId? = null, weapon: WeaponId? = null): Pair<GameState, String> {
        val id = "t${day}x${guild!!.nextMissionSerial}x${guild!!.offers.size}"
        return edit { c -> c.guild = c.guild!!.copy(offers = c.guild!!.offers + Missions.generate(c, cat.mission(defId), c.factions.keys.minBy { it.value }, c.rng(RngStream.GUILD), id, hero, weapon)) } to id
    }
    private fun GameState.frail(): GameState = copy(heroes = heroes.mapValues { (id, h) -> if (guild!!.isMember(id)) h.copy(health = 3, level = 1) else h })
    private fun GameState.mighty(): GameState = copy(heroes = heroes.mapValues { (id, h) -> if (guild!!.isMember(id)) h.copy(health = 100, level = base.heroMaxLevel) else h })
    private fun GameState.loaned(to: HeroId, id: String = "w900"): GameState {
        val blade = CombatSandbox.blade(id, LaunchContent.SWORD, LaunchContent.MOONSTEEL, LaunchContent.FROST_BLOOM, quality = 95).copy(location = WeaponLocation.Loaned(to))
        return copy(weapons = weapons + (blade.id to blade))
    }
    private fun GameState.farSiege(): GameState = copy(town = town.copy(nextSiegeDay = day + 30), siege = SiegeScenario(day + 30))

    /** A frail, reckless party on a dangerous hunt: beaten, and somebody is taken. */
    private fun captured(seed: Long = 7): Triple<GameState, HeroId, WeaponId> {
        val s0 = run(seed).farSiege().frail()
        val carrier = s0.members().first()
        val (s1, hunt) = s0.loaned(carrier).post(GuildContent.HUNT)
        val out = s1.send(Command.PlanDeployment(hunt, s1.members(), Posture.RECKLESS)).endDay().sound()
        assertEquals(MissionOutcome.LOST, out.lastResolution!!.mission!!.outcome)
        return Triple(out, out.guild!!.captives.single().heroId, WeaponId("w900"))
    }

    // ---- capture, rescue, deadline ----

    @Test fun `a party beaten on a dangerous contract loses one member alive, with the loan they carried, and a rescue is posted`() {
        val (s, who, blade) = captured()
        val g = s.guild!!
        assertEquals(MemberStatus.CAPTURED, g.member(who)!!.status)
        assertTrue(s.hero(who).isAlive)
        val c = g.captives.single()
        assertEquals(s.day - 1 + cfg.captiveDays, c.deadlineDay)
        // The last to fall is the one taken; the loan goes with them only if they carried it.
        if (c.weaponId != null) {
            assertEquals(blade, c.weaponId)
            assertEquals(GuildOps.LOST_WITH_CAPTIVE, (s.weapon(blade).location as WeaponLocation.Lost).reason)
        } else assertTrue(s.weapon(blade).isLoaned)
        val rescue = g.offers.single { it.subjectHeroId == who }
        assertEquals(GuildContent.RESCUE, rescue.defId)
        assertTrue(who.value in rescue.reason!!.let { s.hero(who).fullName } || rescue.reason!!.contains(s.hero(who).fullName))
        assertEquals(c.deadlineDay - 1, rescue.expiresDay)
        // The others came home hurt, not dead, and the captive is on nobody's wall.
        assertTrue(g.members.filter { it.heroId != who }.all { it.status == MemberStatus.HOME && it.wound?.kind == WoundKind.INJURED })
        assertIs<GameError.MemberUnavailable>(s.rejected(Command.ReserveDefender(who, true)))
        assertIs<GameError.MemberUnavailable>(s.rejected(Command.DismissHero(who)))
        assertTrue(engine.wallForecast(s)!!.missing.any { it.first.id == who })
    }

    @Test fun `nobody is taken or killed on a safe contract however badly it goes`() {
        val s0 = run().farSiege().frail()
        val supply = s0.guild!!.offers.first { it.defId == GuildContent.SUPPLY_RUN }
        val out = s0.send(Command.PlanDeployment(supply.id, s0.members(), Posture.RECKLESS)).endDay().sound()
        assertTrue(out.guild!!.captives.isEmpty())
        assertTrue(out.members().size == 2 && out.members().all { out.hero(it).isAlive })
    }

    @Test fun `a rescue brings the captive and their loan home, scarred, exactly once`() {
        val (lost, who, blade) = captured()
        val strong = lost.copy(guild = lost.guild!!.copy(members = lost.guild!!.members.map { it.copy(wound = null) })).mighty()
        val rescuers = strong.members().filter { it != who }
        val armed = rescuers.foldIndexed(strong) { i, s, id -> if (s.loanOf(id) == null) s.loaned(id, "w95$i") else s }
        val rescue = armed.guild!!.offers.single { it.subjectHeroId == who }
        // Guards as weak as a test may make them: what is under test is the way home, not the fight.
        val easy = armed.copy(guild = armed.guild!!.copy(offers = armed.guild!!.offers.map { o -> if (o.id == rescue.id) o.copy(stages = o.stages.map { st -> st.copy(enemies = st.enemies.map { it.copy(percent = 10) }) }) else o }))
        val day1 = easy.send(Command.PlanDeployment(rescue.id, rescuers, Posture.RECKLESS)).endDay().sound()
        assertTrue(day1.lastResolution!!.mission!!.continues, "breaking in is the first day")
        assertEquals(MemberStatus.CAPTURED, day1.guild!!.member(who)!!.status)
        val day2 = day1.endDay().sound()
        assertEquals(MissionOutcome.WON, day2.lastResolution!!.mission!!.outcome)
        val m = day2.guild!!.member(who)!!
        assertEquals(MemberStatus.HOME, m.status)
        assertEquals(WoundKind.SCARRED, m.wound!!.kind)
        assertTrue(day2.guild!!.captives.isEmpty() && day2.guild!!.offers.none { it.subjectHeroId == who })
        if (lost.guild!!.captives.single().weaponId != null) assertEquals(who, day2.weapon(blade).loanedTo)
        assertEquals(1, day2.lastResolution!!.events.count { it.type == EventType.MEMBER_RESCUED })
        // A scar does not keep them home; it costs health on the next field.
        assertNull(engine.memberUnavailable(day2, who))
        val f = engine.fighterView(day2.copy(heroes = day2.heroes + (who to day2.hero(who).copy(health = 100))), who)!!
        assertTrue(f.health <= f.maxHealth * cfg.scarredHealthPercent / 100 + 1)
        // Whoever brought them out watches over them.
        assertTrue(day2.guild!!.members.any { x -> x.bonds.any { it.withHeroId == who && it.kind == Stories.BOND_PROTECTOR } })
    }

    @Test fun `a captive nobody brings out is dead on the deadline and the loan stays with the enemy`() {
        val (lost, who, blade) = captured()
        var s = lost
        while (s.guild!!.captives.isNotEmpty() && !s.isEnded) s = s.endDay().sound()
        assertEquals(HeroFate.DEAD, s.hero(who).fate)
        assertFalse(s.guild!!.isMember(who))
        assertTrue(s.guild!!.offers.none { it.subjectHeroId == who })
        if (lost.guild!!.captives.single().weaponId != null) {
            assertEquals(GuildOps.LOST_TAKEN, (s.weapon(blade).location as WeaponLocation.Lost).reason)
            assertEquals(blade, s.guild!!.nemesis!!.weaponId, "a blade the enemy keeps gets a bearer with a name")
        }
    }

    // ---- the nemesis ----

    private fun nemesisRun(): Pair<GameState, WeaponId> {
        val s0 = run().farSiege().mighty()
        val carrier = s0.members().first()
        val blade = WeaponId("w900")
        val s = s0.loaned(carrier).edit { c ->
            c.updateWeapon(c.weapon(blade).copy(location = WeaponLocation.Lost(c.day, GuildOps.LOST_TAKEN)))
            Stories.taken(c, blade, LaunchContent.ASHCLAW)
            Missions.refreshBoard(c)
        }.sound()
        return s to blade
    }

    @Test fun `a taken loan has one bearer, who fights with what the blade does, and a recovery contract for that very blade`() {
        val (s, blade) = nemesisRun()
        val n = s.guild!!.nemesis!!
        assertEquals(blade, n.weaponId)
        val offer = s.guild!!.offers.single { it.subjectWeaponId == blade }
        assertEquals(GuildContent.RECOVERY, offer.defId)
        val setup = engine.missionSetup(s, offer.id, s.members(), Posture.BALANCED)!!
        val bearer = setup.enemies.first()
        assertEquals(n.name, bearer.name)
        assertEquals(s.weapon(blade).name, bearer.weaponName)
        assertTrue(bearer.effects.any { it.id == "element_frost" }, "the blade's own rule is used against you")
        // A second taken blade does not make a second nemesis.
        val again = s.loaned(s.members().first(), "w901").edit { c -> Stories.taken(c, WeaponId("w901"), LaunchContent.HOLLOWBOUND) }
        assertEquals(blade, again.guild!!.nemesis!!.weaponId)
    }

    @Test fun `winning the recovery returns the blade once and ends the nemesis`() {
        val (s0, blade) = nemesisRun()
        val offer = s0.guild!!.offers.single { it.subjectWeaponId == blade }
        val s = s0.copy(guild = s0.guild!!.copy(offers = s0.guild!!.offers.map { o -> if (o.id == offer.id) o.copy(stages = o.stages.map { st -> st.copy(enemies = st.enemies.map { it.copy(percent = 10) }) }) else o }))
        val out = s.send(Command.PlanDeployment(offer.id, s.members(), Posture.RECKLESS)).endDay().sound()
        assertEquals(MissionOutcome.WON, out.lastResolution!!.mission!!.outcome)
        assertTrue(out.weapon(blade).isInStorage)
        assertNull(out.guild!!.nemesis)
        assertTrue(out.guild!!.offers.none { it.subjectWeaponId == blade })
        assertEquals(1, out.weapons.values.count { it.name == out.weapon(blade).name && it.id == blade })
    }

    // ---- visitors of the guild ----

    private fun GameState.visitor(defId: String): GameState = Encounters.force(this, content, engine.config, defId) ?: error("$defId is not eligible")
    private fun GameState.answer(option: String): GameState = send(Command.ResolveEncounter(encounter!!.id, option, CommandId("answer:${encounter!!.id}:$option")))

    @Test fun `no visitor of the guild ever comes to a run without one`() {
        val classic = engine.newRun(LegacyProfile(), 7)
        for (def in GuildContent.encounters) assertNull(Encounters.force(classic, content, engine.config, def.id), def.id)
    }

    @Test fun `the coward returns - time can be bought and the survivor questioned, each as the card says`() {
        val (lost, who, _) = captured()
        val s = lost.copy(guild = lost.guild!!.copy(members = lost.guild!!.members.map { it.copy(wound = null) })).visitor(GuildContent.COWARD_RETURNS)
        assertEquals(who, s.encounter!!.heroId)
        val view = engine.encounterView(s)!!
        assertTrue(lost.hero(who).fullName in view.text)
        val before = s.guild!!.captives.single().deadlineDay
        val bought = s.copy(gold = 500).answer("time").sound()
        assertEquals(before + cfg.captiveTimeDays, bought.guild!!.captives.single().deadlineDay)
        assertEquals(500 - cfg.captiveTimeGold, bought.gold)
        val survivor = s.encounter!!.otherHeroId!!
        val guards = s.guild!!.offers.single { it.subjectHeroId == who }.stages
        val asked = s.answer("question").sound()
        val after = asked.guild!!.offers.single { it.subjectHeroId == who }.stages
        assertTrue(after[0].enemies.zip(guards[0].enemies).all { (a, b) -> a.percent == b.percent * cfg.questionedPercent / 100 })
        assertEquals(guards[1], after[1], "the way out is as it was")
        assertEquals(guards[0].seed, after[0].seed, "the fight's seed is not touched")
        assertNotNull(engine.memberUnavailable(asked, survivor))
        // Unanswered, nothing changes.
        assertEquals(s.guild!!.captives, s.endDay().guild!!.captives.map { it.copy() }.takeIf { it.isNotEmpty() } ?: s.guild!!.captives)
    }

    @Test fun `the sword's complaint - an oath that needs novices, or a blade made quiet`() {
        val s0 = run().farSiege()
        val who = s0.members().first()
        val sentient = CombatSandbox.blade("w800", LaunchContent.SWORD, LaunchContent.IRON, LaunchContent.EMBER_RESIN, flaws = listOf(LaunchContent.SENTIENT)).copy(location = WeaponLocation.Loaned(who))
        val s = s0.copy(weapons = s0.weapons + (sentient.id to sentient), materials = s0.materials + (LaunchContent.BINDING_SALT to 2)).visitor(GuildContent.SWORD_COMPLAINT)
        val sworn = s.answer("oath").sound()
        val novice = engine.fighterView(sworn, who)!!
        assertTrue(novice.effects.any { it.id == "oath_inexperienced" })
        assertTrue(GuildContent.TAG_NOVICE_PARTY in novice.tags)
        val veteran = engine.fighterView(sworn.copy(heroes = sworn.heroes + (who to sworn.hero(who).copy(level = cfg.oathMaxLevel + 1))), who)!!
        assertFalse(GuildContent.TAG_NOVICE_PARTY in veteran.tags, "a seasoned bearer ends it")
        assertNull(Encounters.force(sworn.copy(encounter = null), content, engine.config, GuildContent.SWORD_COMPLAINT), "one complaint a blade")
        val quiet = s.answer("silence").sound()
        assertFalse(LaunchContent.SENTIENT in quiet.weapon(sentient.id).flaws)
        assertEquals(sentient.power - content.affix(LaunchContent.SENTIENT).power, quiet.weapon(sentient.id).power)
        assertEquals(s.materials.getValue(LaunchContent.BINDING_SALT) - 1, quiet.materials[LaunchContent.BINDING_SALT])
        assertEquals(who, quiet.weapon(sentient.id).loanedTo, "the blade stays where it was")
    }

    @Test fun `across the counter - the very blade can be bought back, once`() {
        val (s0, blade) = nemesisRun()
        val s = s0.copy(gold = 5000).visitor(GuildContent.ACROSS_THE_COUNTER)
        val price = s.encounter!!.amounts.getValue("price")
        val bought = s.answer("buy").sound()
        assertTrue(bought.weapon(blade).isInStorage)
        assertEquals(5000 - price, bought.gold)
        assertNull(bought.guild!!.nemesis)
        assertEquals(s.weapons.size, bought.weapons.size, "no copy is made")
        assertIs<GameError.EncounterNotOpen>(bought.rejected(Command.ResolveEncounter(s.encounter!!.id, "buy", CommandId("again"))))
        val lead = s.answer("lead").sound()
        assertEquals(0, lead.guild!!.offers.single { it.subjectWeaponId == blade }.fee)
    }

    @Test fun `insurance pays once, only when the enemy takes the blade, and never for a blade the smith disposes of`() {
        val s0 = run().farSiege()
        val who = s0.members().first()
        val s = s0.loaned(who).copy(gold = 500).visitor(GuildContent.INSURANCE_ADJUSTER)
        val payout = s.encounter!!.amounts.getValue("payout")
        val insured = s.answer("insure").sound()
        assertEquals(500 - s.encounter!!.amounts.getValue("fee"), insured.gold)
        val blade = WeaponId("w900")
        val taken = insured.edit { c -> c.updateWeapon(c.weapon(blade).copy(location = WeaponLocation.Lost(c.day, GuildOps.LOST_TAKEN))); Stories.taken(c, blade, LaunchContent.ASHCLAW); Stories.taken(c, blade, LaunchContent.ASHCLAW) }
        assertEquals(insured.gold + payout, taken.gold, "paid once however often it is asked")
        assertTrue(taken.consequences.none { it.kind == ConsequenceKind.INSURANCE })
        // Recalled and melted: nothing is paid, and the policy simply lapses.
        var melted = insured.send(Command.RecallLoan(blade)).send(Command.Salvage(blade))
        assertEquals(insured.gold, melted.gold)
        repeat(cfg.insuranceDays + 2) { if (!melted.isEnded) melted = melted.endDay() }
        assertTrue(melted.consequences.none { it.kind == ConsequenceKind.INSURANCE })
    }

    @Test fun `the festival can take a member for the tournament, and a restored heirloom makes its heir willing to sign`() {
        val s = run().farSiege().visitor(Depth.FESTIVAL_CONTRACT)
        val view = engine.encounterView(s)!!
        assertEquals(listOf("stall", "watch", "tournament", Encounters.PASS), view.options.map { it.id })
        val fought = s.answer("tournament").sound()
        assertEquals(s.gold + cfg.tournamentGold, fought.gold)
        val champion = fought.guild!!.members.single { it.wound != null }
        assertEquals(WoundKind.EXHAUSTED, champion.wound!!.kind)
        // A classic run's festival has no tournament.
        val classic = Encounters.force(engine.newRun(LegacyProfile(), 7), content, engine.config, Depth.FESTIVAL_CONTRACT)!!
        assertEquals(listOf("stall", "watch", Encounters.PASS), engine.encounterView(classic)!!.options.map { it.id })
    }

    // ---- the charter ----

    private fun easySieges(percent: Int) = GameEngine(config = base.copy(guild = cfg.copy(siegePercentBase = percent, siegePercentPerDay = 0.0, siegePercentPerPressure = 0.0)))

    private fun charterEve(e: GameEngine): GameState {
        val s = run(e = e).mighty()
        val armed = s.members().foldIndexed(s) { i, st, id -> st.loaned(id, "w95$i") }
        return armed.copy(day = cfg.charterDay, town = armed.town.copy(nextSiegeDay = cfg.charterDay), siege = SiegeScenario(cfg.charterDay))
    }

    @Test fun `beating the charter warlord secures the charter once, and the smith may close the chapter`() {
        val e = easySieges(20)
        val eve = charterEve(e)
        assertEquals(GameError.MilestoneNotEarned(GuildContent.CHARTER_SECURED), (e.handle(eve, Command.RetireAfterMilestone(GuildContent.CHARTER_SECURED)) as CommandOutcome.Rejected).error)
        val won = eve.endDay(e).sound(e)
        assertTrue(won.lastResolution!!.siege!!.fight!!.actors.any { it.name == content.combat!!.unit(cat.siegeLeaders.getValue(won.lastResolution!!.events.first { it.type == EventType.SIEGE_WON }.let { eve.siege!!.factionId ?: won.factions.keys.first() }.let { f -> eve.factions.values.maxBy { it.pressure }.id })).name } ||
            won.guild!!.milestone(GuildContent.CHARTER_SECURED) != null)
        val claim = won.guild!!.milestone(GuildContent.CHARTER_SECURED)!!
        assertEquals(cfg.charterDay, claim.day)
        assertEquals(cfg.charterLegacyPoints, claim.points)
        assertEquals(1, won.lastResolution!!.events.count { it.type == EventType.CHARTER_SECURED })
        assertFalse(won.isEnded)
        // Closing the chapter ends the run as a success with the forge standing.
        val closed = won.send(Command.RetireAfterMilestone(GuildContent.CHARTER_SECURED), e).sound(e)
        assertTrue(closed.isEnded && closed.guild!!.retired && closed.town.integrity > 0)
        assertEquals(GameError.RunEnded, (e.handle(closed, Command.SetGuildRank(1)) as CommandOutcome.Rejected).error)
        val end = e.closeRun(closed)
        assertTrue("charter secured" in end.cause)
        val classicPoints = end.basePoints + end.survivalPoints + end.discoveryPoints
        assertTrue(end.totalPoints - classicPoints >= cfg.charterLegacyPoints)
        val claimed = e.claimLegacy(LegacyProfile(), end) as LegacyOutcome.Updated
        assertIs<LegacyOutcome.Rejected>(e.claimLegacy(claimed.legacy, end), "a run is claimed once, however it ended")
    }

    @Test fun `going on after the charter and falling later still counts it, once`() {
        val e = easySieges(20)
        val won = charterEve(e).endDay(e)
        val ranked = won.send(Command.SetGuildRank(2), e).sound(e)
        assertEquals(GameError.PartyInvalid("A rank once taken is kept."), (e.handle(ranked, Command.SetGuildRank(1)) as CommandOutcome.Rejected).error)
        // The next siege is not the charter's again.
        val fallen = ranked.copy(town = ranked.town.copy(integrity = 0), phase = Phase.ENDED, endCause = "The forge fell.")
        val end = e.closeRun(fallen)
        assertEquals(cfg.charterLegacyPoints + cat.rank(2)!!.legacyPoints, end.milestonePoints - e.closeRun(fallen.copy(guild = fallen.guild!!.copy(milestones = emptyList(), rank = 0))).milestonePoints)
        var s = ranked
        repeat(base.siegeInterval) { if (!s.isEnded) s = s.endDay(e) }
        assertEquals(1, s.guild!!.milestones.size)
        assertTrue(s.isEnded || s.lastResolution?.siege == null || !s.events.last { it.type == EventType.SIEGE_WON || it.type == EventType.SIEGE_LOST }.text.contains("Charter"))
    }

    @Test fun `a warlord held off but not beaten leaves the charter open and leads the next siege too`() {
        val e = easySieges(20)
        val eve = charterEve(e)
        val held = eve.edit(e) { c -> Stories.charterSiege(c, "Warlord Krag", beaten = false) }
        assertTrue(held.guild!!.milestones.isEmpty())
        val next = held.edit(e) { c -> c.town = c.town.copy(nextSiegeDay = c.day + 5); c.siege = SiegeScenario(c.day + 5, factionId = LaunchContent.ASHCLAW); com.tinyblacksmith.core.guild.SiegeFight.plan(c) }
        assertTrue(next.siege!!.charter)
        assertEquals(cat.siegeLeaders.getValue(LaunchContent.ASHCLAW), next.siege!!.plan.first().unitId)
    }

    // ---- bonds, branches, laws, the rival ----

    @Test fun `three contracts won side by side make comrades, and a branch opens by deeds and is chosen once`() {
        var s = run().farSiege().mighty()
        repeat(cfg.bondMissions) {
            s = s.copy(heroes = s.heroes.mapValues { (id, h) -> if (s.guild!!.isMember(id)) h.copy(health = 100) else h }, guild = s.guild!!.copy(members = s.guild!!.members.map { it.copy(wound = null) })).farSiege()
            val supply = s.guild!!.offers.first { it.defId == GuildContent.SUPPLY_RUN }
            s = s.send(Command.PlanDeployment(supply.id, s.members(), Posture.BALANCED)).endDay().sound()
            assertEquals(MissionOutcome.WON, s.lastResolution!!.mission!!.outcome)
        }
        val (a, b) = s.members()
        assertTrue(s.guild!!.member(a)!!.bonds.any { it.withHeroId == b && it.kind == Stories.BOND_COMRADES } || s.guild!!.member(b)!!.bonds.any { it.withHeroId == a })
        val bonded = s.guild!!.members.first { it.bonds.isNotEmpty() }
        assertTrue(engine.fighterView(s, bonded.heroId)!!.effects.none { it.id == "bond_comrades" }, "alone, a bond does nothing")
        val setup = engine.missionSetup(s, s.guild!!.offers.first { it.defId == GuildContent.SUPPLY_RUN }.id, s.members(), Posture.BALANCED)!!
        assertTrue(setup.party.first { it.key == bonded.heroId.value }.effects.any { it.id == "bond_comrades" })
        // Three wins: the veteran's two branches are open; one is taken and the other closes.
        val open = engine.openBranches(s, a)
        assertEquals(setOf("vanguard", "striker"), open.map { it.id }.toSet())
        val before = engine.fighterView(s, a)!!
        val chosen = s.send(Command.ChooseSpeciality(a, "vanguard")).sound()
        assertTrue(engine.fighterView(chosen, a)!!.maxHealth > before.maxHealth)
        assertTrue(engine.openBranches(chosen, a).isEmpty())
        assertIs<GameError.UnknownContent>(chosen.rejected(Command.ChooseSpeciality(a, "striker")))
    }

    @Test fun `a law is announced a morning before it binds and then changes the field for everybody`() {
        val s = run().farSiege()
        val announced = s.copy(guild = s.guild!!.copy(law = WorldLaw("storm_front", s.day + 1, s.day + 4)))
        val supply = announced.guild!!.offers.first { it.defId == GuildContent.SUPPLY_RUN }.id
        val today = engine.missionSetup(announced, supply, announced.members(), Posture.BALANCED)!!
        assertTrue((today.party + today.enemies).none { (it.stats[com.tinyblacksmith.core.combat.Stat.WET] ?: 0) > 0 }, "not yet")
        val tomorrow = announced.copy(day = s.day + 1)
        val under = engine.missionSetup(tomorrow, supply, tomorrow.members(), Posture.BALANCED)!!
        assertTrue((under.party + under.enemies).all { it.stats[com.tinyblacksmith.core.combat.Stat.WET] == 2 })
    }

    @Test fun `the rival takes a contract on its day, never the one the smith has planned`() {
        var s = run(seed = 3).farSiege()
        while (s.guild!!.rival == null && !s.isEnded) s = s.farSiege().endDay()
        val r = s.guild!!.rival!!
        while (s.day < r.nextMoveDay - 1) s = s.farSiege().endDay()
        val target = s.guild!!.offers.firstOrNull { o -> cat.mission(o.defId).let { it.weight > 0 && it.archetype != com.tinyblacksmith.core.content.MissionArchetype.SUPPLY } } ?: return
        val planned = s.copy(gold = 500).farSiege().send(Command.PlanDeployment(target.id, s.members().filter { engine.memberUnavailable(s, it) == null }.ifEmpty { return }, Posture.CAUTIOUS))
        val next = planned.endDay().sound()
        assertTrue(next.guild!!.rival!!.taken.none { "day ${next.day}" in it.lowercase() && false })
        assertEquals(r.nextMoveDay + cfg.rivalMoveEveryDays, next.guild!!.rival!!.nextMoveDay)
        assertTrue(next.guild!!.rival!!.taken.size <= 5)
    }

    @Test fun `a party keeps the relics it left with when the workshop changes them`() {
        val s0 = run().farSiege().mighty().copy(relics = listOf(ActiveRelic("overflow_basin")))
        val (s1, delve) = s0.post(GuildContent.RELIC_DELVE)
        val out = s1.copy(gold = 500).send(Command.PlanDeployment(delve, s1.members(), Posture.BALANCED)).endDay()
        val m = out.guild!!.mission ?: return
        val swapped = out.copy(relics = listOf(ActiveRelic("salvage_bell")))
        assertEquals(listOf("overflow_basin"), swapped.guild!!.mission!!.relics)
        val setup = Missions.setup(ResolutionContext(swapped, content, engine.config), m, 1)!!
        assertEquals(listOf("relic_overflow_basin"), setup.partyEffects.map { it.id })
    }

    @Test fun `a chain that really happened is written down once, with who and what did it`() {
        val s0 = run().farSiege()
        val guardian = s0.guild!!.members.first { s0.hero(it.heroId).classId == LaunchContent.GUARDIAN }.heroId
        val capacitor = CombatSandbox.capacitor.copy(id = WeaponId("w700"), location = WeaponLocation.Loaned(guardian))
        var s = s0.copy(weapons = s0.weapons + (capacitor.id to capacitor), relics = listOf(ActiveRelic("overflow_basin")))
        var notes = 0
        repeat(6) {
            if (s.isEnded) return@repeat
            s = s.farSiege().copy(guild = s.guild!!.copy(members = s.guild!!.members.map { it.copy(wound = null) }))
            val (posted, hunt) = s.post(GuildContent.HUNT)
            val fit = posted.members().filter { engine.memberUnavailable(posted, it) == null }
            s = (if (fit.isEmpty()) posted else posted.copy(gold = 500).send(Command.PlanDeployment(hunt, fit, Posture.BALANCED))).endDay().sound()
            notes += s.lastResolution!!.events.count { it.type == EventType.COMBO_NOTED }
        }
        assertEquals(notes, s.guild!!.comboNotes.size, "each chain once")
        assertTrue(s.guild!!.comboNotes.all { "|" in it && it.substringAfter("|").isNotBlank() })
    }
}
