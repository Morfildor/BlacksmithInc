package com.example.blacksmithproject

import com.example.blacksmithproject.ui.AssignAction
import com.example.blacksmithproject.ui.ChipTone
import com.example.blacksmithproject.ui.StepState
import com.example.blacksmithproject.ui.TODAY_ROWS
import com.example.blacksmithproject.ui.TodayTarget
import com.example.blacksmithproject.ui.guildTabFor
import com.example.blacksmithproject.ui.GuildUi
import com.example.blacksmithproject.ui.guildUi
import com.tinyblacksmith.core.combat.Posture
import com.tinyblacksmith.core.content.GuildContent
import com.tinyblacksmith.core.engine.Command
import com.tinyblacksmith.core.engine.CommandOutcome
import com.tinyblacksmith.core.engine.GameEngine
import com.tinyblacksmith.core.model.CommandId
import com.tinyblacksmith.core.model.GameState
import com.tinyblacksmith.core.model.RunMilestoneClaim
import com.tinyblacksmith.core.model.Wound
import com.tinyblacksmith.core.model.WoundKind
import com.tinyblacksmith.core.sim.GuildBot
import com.tinyblacksmith.core.sim.GuildPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** What the Guild puts first and what it says is to come: Today, the sub-part on a visit, the contracts still to come, the assign sheet, the charter's steps. */
class GuildPlanUiTest {
    private val engine = GameEngine()

    private fun GameState.send(cmd: Command): GameState = (engine.handle(this, cmd) as CommandOutcome.Accepted).state
    private fun GameState.endDay(): GameState = send(Command.EndDay(CommandId("${runId.value}:day$day")))

    private fun mornings(policy: GuildPolicy, seed: Long, days: Int = 30): Sequence<GameState> = sequence {
        val bot = GuildBot(engine, policy)
        var s = engine.guildRun(seed)
        while (!s.isEnded && s.day <= days) {
            yield(s)
            yield(bot.planDay(s))
            s = bot.planDay(s).endDay()
        }
    }

    @Test
    fun thePartShownIsTodaysChoiceElseTheWallOnTheEveElseContracts() {
        assertEquals(GuildTab.CONTRACTS, guildTabFor(null, day = 3, daysToSiege = 2))
        assertEquals(GuildTab.WALL, guildTabFor(null, day = 4, daysToSiege = 1))
        assertEquals(GuildTab.WALL, guildTabFor(null, day = 5, daysToSiege = 0))
        assertEquals("today's choice holds", GuildTab.COMPANY, guildTabFor(GuildTabMemory(GuildTab.COMPANY, 5), day = 5, daysToSiege = 0))
        assertEquals("yesterday's choice does not", GuildTab.CONTRACTS, guildTabFor(GuildTabMemory(GuildTab.COMPANY, 2), day = 3, daysToSiege = 2))
    }

    @Test
    fun todayIsAtMostThreeRowsMostPressingFirstAndNeverTheCountdown() {
        val order = listOf("checkpoint", "wall", "rescue", "plan", "branch", "charter")
        var seen = 0
        for (policy in listOf(GuildPolicy.ADAPTIVE, GuildPolicy.GREEDY_DIVER, GuildPolicy.NOVICE)) for (s in mornings(policy, 9)) {
            val ui = engine.guildUi(s)!!
            val g = s.guild!!
            assertTrue(ui.today.size <= TODAY_ROWS)
            assertEquals("rows in priority order", ui.today.map { order.indexOf(it.key.substringBefore('_')) }.sorted(), ui.today.map { order.indexOf(it.key.substringBefore('_')) })
            assertTrue(ui.today.toString(), ui.today.none { it.text.startsWith("Siege") })
            // An empty place is a row exactly on the eve and the day, and the row goes to the assign sheet.
            val empty = ui.wall.daysLeft <= 1 && ui.wall.defenders.size < ui.wall.places
            assertEquals(empty, ui.today.any { it.target == TodayTarget.AssignWall })
            // A checkpoint without word is always first.
            val waiting = g.mission?.let { it.outcome == null && it.stage == 1 && it.offer.optionalPush && it.choice == null } == true
            if (waiting) { seen++; assertEquals("checkpoint", ui.today.first().key) }
            // "No contract planned" only when nothing is out or planned, and always when there is room for it.
            val unplanned = g.mission == null && g.planned == null && g.offers.isNotEmpty()
            if (ui.today.any { it.key == "plan" }) assertTrue(unplanned)
            if (unplanned && ui.today.size < TODAY_ROWS) assertTrue(ui.today.toString(), ui.today.any { it.key == "plan" })
        }
        assertTrue("the bots reach a checkpoint", seen > 0)
    }

    @Test
    fun theContractsToComeAreTheBoardsOwnRules() {
        val s = engine.guildRun(7)
        val ui = engine.guildUi(s)!!
        val cat = engine.content.guild!!
        val board = s.guild!!.offers.map { it.defId }.toSet()
        val later = cat.missions.filter { it.weight > 0.0 && s.day < it.minDay && it.id !in board }
        assertTrue("a first morning has contracts to come", later.isNotEmpty())
        for (def in later) assertTrue(ui.upcoming.toString(), ui.upcoming.any { it.name == def.name && it.condition == "May be posted from day ${def.minDay}" })
        assertTrue(ui.upcoming.any { it.name == cat.mission(GuildContent.RESCUE).name })
        // Under a charter that posts dangerous contracts from the first day, none waits on a day.
        val early = engine.guildUi(engine.guildRun(7, GuildContent.LICENSED_BAD_IDEAS))!!
        assertTrue(early.upcoming.toString(), early.upcoming.none { it.condition.startsWith("May be posted from day") })
    }

    @Test
    fun theAssignSheetHasTheReserveRulesOwnReasons() {
        val fresh = engine.guildRun(7)
        val g = fresh.guild!!
        val (a, b) = g.members.take(2)
        assertTrue(engine.guildUi(fresh)!!.assign.all { it.action == AssignAction.RESERVE && it.reason == null })
        // Kept, then laid up, then in today's party: release, a reason without a button, keep the party home.
        val kept = fresh.send(Command.ReserveDefender(a.heroId, true))
        assertEquals(AssignAction.RELEASE, engine.guildUi(kept)!!.assign.first { it.heroId == a.heroId }.action)
        val hurt = fresh.copy(guild = g.copy(members = g.members.map { if (it.heroId == a.heroId) it.copy(wound = Wound(WoundKind.INJURED, fresh.day + 3, "test")) else it }))
        val hurtRow = engine.guildUi(hurt)!!.assign.first { it.heroId == a.heroId }
        assertEquals(AssignAction.NONE, hurtRow.action)
        assertEquals("Injured until day ${fresh.day + 3}", hurtRow.reason)
        assertEquals(ChipTone.HURT, engine.guildUi(hurt)!!.roster.first { it.heroId == a.heroId }.chipTone)
        val offer = g.offers.first { it.defId != GuildContent.WALL_WORK && it.fee == 0 }
        val planned = fresh.send(Command.PlanDeployment(offer.id, listOf(b.heroId), Posture.BALANCED))
        val row = engine.guildUi(planned)!!.assign.first { it.heroId == b.heroId }
        assertEquals(AssignAction.KEEP_HOME, row.action)
        assertEquals("In today's planned party", row.reason)
    }

    @Test
    fun theCharterOpensRetiringAndTheRanksAndARankIsKept() {
        val fresh = engine.guildRun(7)
        fun GuildUi.charter() = steps.filter { it.key != "road" }
        val before = engine.guildUi(fresh)!!.charter()
        assertEquals("charter", before.first().key)
        assertTrue(before.all { it.state == StepState.LOCKED })
        val g = fresh.guild!!
        val secured = fresh.copy(guild = g.copy(milestones = g.milestones + RunMilestoneClaim(GuildContent.CHARTER_SECURED, fresh.day)))
        val after = engine.guildUi(secured)!!
        assertEquals(StepState.DONE, after.charter().first().state)
        assertTrue(after.charter().drop(1).all { it.state == StepState.OPEN })
        assertTrue("a charter secured today is a row of Today", after.today.any { it.key == "charter" })
        val ranked = secured.send(Command.SetGuildRank(1))
        val steps = engine.guildUi(ranked)!!.charter()
        assertEquals(StepState.DONE, steps[1].state)
        assertFalse(steps.drop(2).any { it.state == StepState.LOCKED })
    }

    @Test
    fun theDeepRoadIsSaidAsTheRulesOpenIt() {
        // Shut on the first morning: a step still to come, and a contract the board will post after the first siege.
        val fresh = engine.guildRun(7)
        val road = engine.content.guild!!.mission(GuildContent.DEEP_ROAD)
        engine.guildUi(fresh)!!.run {
            assertEquals(StepState.LOCKED, steps.first { it.key == "road" }.state)
            assertTrue(upcoming.toString(), upcoming.any { it.name == road.name && it.condition == "Posted after the first siege while the road is shut" })
        }
        // Its contract on the board: the step is open, the card says what it opens, and it is no longer "to come".
        val posted = listOf(GuildPolicy.ADAPTIVE, GuildPolicy.NOVICE).asSequence().flatMap { p -> (7L..12L).asSequence().flatMap { mornings(p, it, 14) } }
            .first { s -> s.guild!!.offers.any { it.defId == GuildContent.DEEP_ROAD } }
        engine.guildUi(posted)!!.run {
            assertEquals(StepState.OPEN, steps.first { it.key == "road" }.state)
            assertTrue(upcoming.none { it.name == road.name })
            assertTrue(offers.first { o -> posted.guild!!.offers.first { it.id == o.id }.defId == GuildContent.DEEP_ROAD }.specials.contains("Opens the deep road"))
        }
        // Open: done, with its day, and nothing left to post.
        val open = fresh.copy(deepRoadOpenDay = fresh.day)
        engine.guildUi(open)!!.run {
            assertEquals(StepState.DONE to "Opened on day ${fresh.day}.", steps.first { it.key == "road" }.let { it.state to it.condition })
            assertTrue(upcoming.none { it.name == road.name })
        }
    }
}
