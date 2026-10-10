package com.tinyblacksmith.core

import com.tinyblacksmith.core.ScenarioSaves.byId
import com.tinyblacksmith.core.ScenarioSaves.endDay
import com.tinyblacksmith.core.TestSupport.engine
import com.tinyblacksmith.core.TestSupport.run
import com.tinyblacksmith.core.combat.Posture
import com.tinyblacksmith.core.combat.Side
import com.tinyblacksmith.core.content.CombatContent
import com.tinyblacksmith.core.content.GuildContent
import com.tinyblacksmith.core.content.LaunchContent
import com.tinyblacksmith.core.engine.Command
import com.tinyblacksmith.core.engine.CommandOutcome
import com.tinyblacksmith.core.engine.Invariants
import com.tinyblacksmith.core.guild.Loadout
import com.tinyblacksmith.core.guild.Missions
import com.tinyblacksmith.core.model.*
import com.tinyblacksmith.core.persistence.SaveCodec
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The guild scenario saves hold what their titles say, as the app will load them: decoded from the stored text. */
class GuildScenariosTest {
    private val config = engine.config
    private fun loaded(id: String): GameState = SaveCodec.decodeRun(SaveCodec.encodeRun(byId(id).state))
    private fun GameState.classOf(id: HeroId) = hero(id).classId
    private fun GameState.memberOf(cls: HeroClassId) = guild!!.members.first { classOf(it.heroId) == cls }.heroId

    @Test
    fun everyGuildSaveIsASoundGuildRunOnAPlanningMorning() {
        assertEquals(listOf("guild_day_one", "guild_checkpoint", "guild_siege_eve", "guild_captive", "guild_charter_eve", "guild_stormwell"), GuildScenarios.all.map { it.id })
        for (sc in GuildScenarios.all) {
            val s = sc.state
            assertEquals(GuildContent.TOWNS_LAST_HOPE, s.guild?.charterId, sc.id)
            assertEquals(Phase.PLANNING, s.phase, sc.id)
            assertEquals(emptyList(), Invariants.check(s, config, engine.shelfSlots(s), engine.content), sc.id)
            assertEquals(s, loaded(sc.id), sc.id)
            assertTrue(s.pendingBlessingOffer.isEmpty(), "${sc.id} opens on its own situation")
            assertTrue(sc.built.startsWith("Played") || sc.built.startsWith("Constructed"), sc.id)
            // Whatever is done next, the day can be ended and leaves a sound run.
            val day = endDay(s)
            assertEquals(emptyList(), Invariants.check(day.after, config, engine.shelfSlots(day.after), engine.content), sc.id)
        }
    }

    @Test
    fun dayOneHasOneSwordToLoanAndTakeBack() {
        val s = loaded("guild_day_one")
        assertTrue(s.day == 1 && s.pendingRelicOffer.isEmpty() && s.relics.isEmpty())
        assertEquals(listOf(LaunchContent.GUARDIAN, LaunchContent.WARDEN), s.guild!!.members.map { s.classOf(it.heroId) })
        val sword = s.storedWeapons().single()
        assertEquals(LaunchContent.SWORD, sword.familyId)
        assertEquals(sword, byId("guild_day_one").blade(s))
        assertTrue(s.weapons.values.none { it.isLoaned } && s.guild!!.members.all { s.loanOf(it.heroId) == null })
        val guardian = s.memberOf(LaunchContent.GUARDIAN)
        val loaned = s.run(Command.LoanWeapon(guardian, sword.id))
        assertEquals(sword.id, loaned.loanOf(guardian)?.id)
        assertTrue(loaned.storedWeapons().isEmpty())
        assertEquals(listOf(sword.id), loaned.run(Command.RecallLoan(sword.id)).storedWeapons().map { it.id })
    }

    @Test
    fun thePartyWaitsAtTheDelvesCheckpointAndBothAnswersAreOpen() {
        val s = loaded("guild_checkpoint")
        assertTrue(GuildScenarios.atDelveCheckpoint(s))
        val m = s.guild!!.mission!!
        assertTrue(m.stage == 1 && m.outcome == null && m.offer.stages.size == 2 && !m.secured.isEmpty)
        assertTrue(m.party.all { s.guild!!.member(it)?.status == MemberStatus.AWAY })
        // Yesterday's report says so: won, and still out.
        assertTrue(s.lastResolution!!.mission!!.let { it.missionId == m.id && it.outcome == MissionOutcome.WON && it.continues })
        val home = endDay(s.run(Command.ChooseMissionCheckpoint(m.id, CheckpointChoice.RETURN)))
        assertTrue(home.after.guild!!.mission.let { it == null || it.outcome == MissionOutcome.WON })
        assertNull(home.resolution.mission, "walking home is not a fight")
        val deeper = endDay(s.run(Command.ChooseMissionCheckpoint(m.id, CheckpointChoice.PUSH)))
        assertTrue(deeper.resolution.mission!!.let { it.missionId == m.id && it.stage == 1 && !it.continues })
    }

    @Test
    fun onTheSiegeMorningOneIsReservedAndAContractWouldTakeTheOthers() {
        val s = loaded("guild_siege_eve")
        assertEquals(s.day, s.town.nextSiegeDay)
        val g = s.guild!!
        val kept = g.reserved.single()
        assertEquals(LaunchContent.GUARDIAN, s.classOf(kept))
        assertTrue(g.mission == null && g.planned == null)
        val offer = assertNotNull(GuildScenarios.awayContract(s))
        val others = g.members.map { it.heroId }.filter { it != kept && engine.memberUnavailable(s, it) == null }.take(config.guild.partyMax)
        assertTrue(others.isNotEmpty())
        assertIs<CommandOutcome.Rejected>(engine.handle(s, Command.PlanDeployment(offer.id, listOf(kept), Posture.BALANCED)), "the reserved member cannot go")
        val day = endDay(s.run(Command.PlanDeployment(offer.id, others, Posture.BALANCED)))
        val wall = assertNotNull(day.resolution.siege)
        assertTrue(kept in wall.defenders && others.none { it in wall.defenders }, "the party was away: ${wall.defenders}")
        assertEquals(others, assertNotNull(day.resolution.mission).party)
    }

    @Test
    fun aMemberIsHeldAndTheirRescueIsOnTheBoard() {
        val s = loaded("guild_captive")
        val captive = assertNotNull(GuildScenarios.captiveWithRescue(s))
        assertTrue(captive.deadlineDay > s.day)
        assertEquals(MemberStatus.CAPTURED, s.guild!!.member(captive.heroId)!!.status)
        assertNotNull(engine.memberUnavailable(s, captive.heroId))
        val rescue = s.guild!!.offers.single { it.subjectHeroId == captive.heroId }
        assertTrue(s.hero(captive.heroId).fullName in rescue.reason!!)
        assertEquals(captive.heroId, rescue.stages.last().reward.captiveId)
        val fit = s.guild!!.members.map { it.heroId }.filter { engine.memberUnavailable(s, it) == null }
        assertTrue(fit.isNotEmpty() && s.guild!!.mission == null, "somebody can be sent")
        assertIs<CommandOutcome.Accepted>(engine.handle(s, Command.PlanDeployment(rescue.id, fit.take(config.guild.partyMax), Posture.BALANCED)))
    }

    @Test
    fun theStrongRosterBeatsTheCharterWarlord() {
        val s = loaded("guild_charter_eve")
        assertTrue(s.day == config.guild.charterDay && s.town.nextSiegeDay == s.day)
        assertNull(s.guild!!.milestone(GuildContent.CHARTER_SECURED))
        val forecast = engine.wallForecast(s)!!
        assertTrue(forecast.charter, "the forecast names the charter siege")
        assertEquals(s.guild!!.reserved, forecast.defenders.map { it.first.id })
        assertEquals(listOf(LaunchContent.GUARDIAN, LaunchContent.WARDEN, LaunchContent.BATTLEMAGE), forecast.defenders.map { it.first.classId })
        assertTrue(forecast.defenders.all { (h, w) -> h.level >= GuildScenarios.CHARTER_LEVEL && h.health == 100 && w != null && w.isLoaned && w.quality >= GuildScenarios.CHARTER_QUALITY && w.condition == 100 })
        // What `built` says of the morning as it was played: without the hand, the warlord breaks through.
        assertEquals(SiegeVerdict.BREACHED, endDay(GuildScenarios.charterEveAsPlayed()).resolution.siege!!.verdict)
        val day = endDay(s)
        val wall = day.resolution.siege!!
        val warlord = engine.content.combat!!.unit(engine.content.guild!!.siegeLeaders.getValue(forecast.faction!!.id)).name
        assertTrue(wall.fight!!.actors.any { it.side == Side.ENEMY && it.name == warlord }, "$warlord leads")
        assertTrue(wall.verdict != SiegeVerdict.BREACHED && wall.fight!!.won, "${wall.verdict}")
        assertNotNull(day.after.guild!!.milestone(GuildContent.CHARTER_SECURED))
        assertIs<CommandOutcome.Accepted>(engine.handle(day.after.let { a -> a.pendingBlessingOffer.firstOrNull()?.let { a.run(Command.ChooseBlessing(it)) } ?: a }, Command.RetireAfterMilestone(GuildContent.CHARTER_SECURED)))
    }

    @Test
    fun stormwellIsAssembledAndTheHuntCanBeTaken() {
        val s = loaded("guild_stormwell")
        val g = s.guild!!
        val guardian = s.memberOf(LaunchContent.GUARDIAN); val warden = s.memberOf(LaunchContent.WARDEN); val mage = s.memberOf(LaunchContent.BATTLEMAGE)
        val sword = s.loanOf(guardian)!!
        assertTrue(sword.familyId == LaunchContent.SWORD && sword.augmentId == LaunchContent.STORMGLASS && sword.catalystId == LaunchContent.BINDING_SALT)
        assertEquals(sword, byId("guild_stormwell").blade(s))
        assertEquals(LaunchContent.STAFF, s.loanOf(warden)!!.familyId)
        assertEquals(listOf(CombatContent.OVERFLOW_BASIN), s.relics.map { it.id })
        // The blade's rules on the road are the capacitor's: Charge from healing, a burst at three.
        val rules = Loadout.weaponEffects(sword, engine.content.combat!!, Loadout.Field.ROAD).map { it.id }
        assertTrue(CombatContent.CHARGE_BURST.id in rules, "$rules")
        val hunt = g.offers.single { it.defId == GuildContent.HUNT }
        assertTrue(hunt.stages.first().enemies.isNotEmpty() && hunt.fee <= s.gold)
        val setup = assertNotNull(engine.missionSetup(s, hunt.id, listOf(guardian, warden, mage), Posture.BALANCED))
        assertEquals(listOf("Overflow Basin"), setup.partyEffects.map { it.name })
        val day = endDay(s.run(Command.PlanDeployment(hunt.id, listOf(guardian, warden, mage), Posture.BALANCED)))
        val report = assertNotNull(day.resolution.mission)
        assertEquals(listOf(guardian, warden, mage), report.party)
        assertNotNull(report.fight)
        assertTrue(Missions.compact(report.fight!!) == report.fight)
    }
}
