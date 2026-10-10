package com.example.blacksmithproject

import com.example.blacksmithproject.ui.fightOutcomeLine
import com.example.blacksmithproject.ui.fightSide
import com.example.blacksmithproject.ui.fightTimeline
import com.example.blacksmithproject.ui.shopday.Beat
import com.example.blacksmithproject.ui.shopday.ShopDayUiModel
import com.example.blacksmithproject.ui.shopday.toUi
import com.tinyblacksmith.core.combat.EventKind
import com.tinyblacksmith.core.combat.Side
import com.tinyblacksmith.core.content.GuildContent
import com.tinyblacksmith.core.engine.Command
import com.tinyblacksmith.core.engine.CommandOutcome
import com.tinyblacksmith.core.engine.GameEngine
import com.tinyblacksmith.core.model.CommandId
import com.tinyblacksmith.core.model.DayResolution
import com.tinyblacksmith.core.model.GameState
import com.tinyblacksmith.core.model.IncomeKind
import com.tinyblacksmith.core.model.LegacyProfile
import com.tinyblacksmith.core.model.MissionOutcome
import com.tinyblacksmith.core.model.SiegeVerdict
import com.tinyblacksmith.core.persistence.DayCursor
import com.tinyblacksmith.core.shopday.AftermathKind
import com.tinyblacksmith.core.shopday.ShopDay
import com.tinyblacksmith.core.shopday.ShopDayScript
import com.tinyblacksmith.core.sim.GuildBot
import com.tinyblacksmith.core.sim.GuildPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import com.example.blacksmithproject.Beat as Card

/**
 * The evening of a guild run: the party's card leads the day, the siege card says the wall's own verdict, and both are
 * the day's records rearranged. Days are real: played by the simulator's guild bots under Town's Last Hope.
 */
class GuildEveningTest {
    private class Day(val seed: Long, val policy: GuildPolicy, val state: GameState, val resolution: DayResolution, val script: ShopDayScript) {
        val contract get() = resolution.mission != null
        fun ui(): ShopDayUiModel = script.toUi(state, engine.content, engine.config, contract)
        fun cards(): List<Card> = ShopDayPosition.beats(script, contract)
        override fun toString() = "$policy seed $seed day ${resolution.day}"
    }

    private companion object {
        val engine = GameEngine()

        fun run(seed: Long, policy: GuildPolicy, days: Int = 24): List<Day> {
            val bot = GuildBot(engine, policy)
            var s = engine.newRun(LegacyProfile(), seed, charterId = GuildContent.TOWNS_LAST_HOPE)
            val out = ArrayList<Day>()
            while (!s.isEnded && s.day <= days) {
                val planned = bot.planDay(s)
                val done = engine.handle(planned, Command.EndDay(CommandId("${s.runId.value}:day${s.day}"))) as CommandOutcome.Accepted
                val r = done.resolution!!
                out += Day(seed, policy, done.state, r, ShopDay.script(r, done.state, engine.content, engine.config))
                s = done.state
            }
            return out
        }

        /** Every day of a few runs per policy: the divers reach checkpoints, the passive smith a breached wall. */
        val days: List<Day> by lazy {
            listOf(GuildPolicy.GREEDY_DIVER, GuildPolicy.SYNERGY, GuildPolicy.DEFENCE_FIRST, GuildPolicy.NOVICE, GuildPolicy.PASSIVE).flatMap { p -> (1L..6L).flatMap { run(it, p) } }
        }
    }

    private fun contractDays() = days.filter { it.contract }
    private fun siegeDays() = days.filter { it.resolution.siege != null }

    @Test
    fun thePartysCardLeadsTheDayAndTheSavedPositionCountsIt() {
        assertTrue("days with a party report: ${contractDays().size}", contractDays().size > 20)
        for (d in contractDays()) {
            val ui = d.ui()
            val cards = d.cards()
            assertEquals("$d", cards.size, ui.beats.size)
            assertEquals("$d", ShopDayPosition(cards, 0).ending, ui.endingIndex)
            assertEquals("$d", Card.Contract, cards[0])
            assertTrue("$d", ui.beats[0] is Beat.Contract)
            assertEquals("$d: one card for the party", 1, ui.beats.count { it is Beat.Contract })
            // Before the routine sales: the shop opens (or stays quiet) on the card after it.
            assertTrue("$d: ${ui.beats[1]}", ui.beats[1] is Beat.Open || ui.beats[1] is Beat.Quiet)
            assertEquals("$d: it waits for the player", 0, ui.beats[0].millis)
            // Every other card is the one the day has without the party's.
            assertEquals("$d", d.script.toUi(d.state, engine.content, engine.config).beats, ui.beats.drop(1))
            assertEquals("$d", ShopDayPosition.beats(d.script), cards.drop(1))
        }
    }

    @Test
    fun theCardSaysWhoWentHowItEndedAndWhatItBrought() {
        var finished = 0; var checkpoints = 0; var pulledBack = 0; var beaten = 0; var fights = 0
        for (d in contractDays()) {
            val m = d.resolution.mission!!
            val c = (d.ui().beats[0] as Beat.Contract).contract
            assertEquals(m.title, c.title)
            assertEquals("$d", m.party.map { d.state.heroes.getValue(it).fullName }, c.party.map { it.name })
            assertEquals(m.lines, c.lines)
            assertEquals(m.fight?.highlights.orEmpty().take(3).map { it.text }, c.highlights)
            assertTrue(c.highlights.size <= 3)
            assertEquals(m.fight, c.fight)
            val still = d.state.guild!!.mission?.takeIf { it.id == m.missionId }
            val expected = when {
                m.outcome == MissionOutcome.RETREATED -> "Pulled back".also { pulledBack++ }
                m.outcome == MissionOutcome.LOST -> "Beaten".also { beaten++ }
                !m.continues -> "Finished".also { finished++ }
                still!!.offer.optionalPush -> "Won the first stage; the smith decides in the morning".also { checkpoints++ }
                else -> "Won the first day; the party presses on tomorrow"
            }
            assertEquals("$d", expected, c.outcome)
            if (m.fight != null) fights++
        }
        assertTrue("a contract finished in a day: $finished", finished > 0)
        assertTrue("a party waiting at a checkpoint: $checkpoints", checkpoints > 0)
        assertTrue("a party that pulled back or was beaten: $pulledBack, $beaten", pulledBack + beaten > 0)
        assertTrue("reports with a fight: $fights", fights > 0)
    }

    /** (a) of the task: a one-day contract won, with gold in the till under its own label and a tally that still adds up. */
    @Test
    fun aWonContractsGoldIsARowOfTheTillAndTheTillBalances() {
        val d = contractDays().first { it.resolution.mission!!.let { m -> m.outcome == MissionOutcome.WON && !m.continues && m.gained.gold > 0 } && it.script.ledger != null && it.script.quiet == null }
        val close = d.ui().beats.filterIsInstance<Beat.Close>().single()
        val ledger = d.script.ledger!!
        val contract = close.rows.single { it.label == "Guild contracts" }
        assertEquals("${ledger.income.getValue(IncomeKind.CONTRACT)} gold", contract.value)
        val total = close.rows.single { it.total }
        assertEquals("Earned today", total.label)
        assertEquals("$d: the rows add up to the total", total.value, "${close.rows.filter { !it.total }.sumOf { it.value.removeSuffix(" gold").toInt() }} gold")
        assertEquals(ledger.goldAtClose, close.purse)
    }

    @Test
    fun withoutTheFlagTheDayIsLaidOutAsBeforeOnBothSides() {
        for (d in contractDays().take(10)) {
            val ui = d.script.toUi(d.state, engine.content, engine.config)
            val cards = ShopDayPosition.beats(d.script)
            assertEquals(cards.size, ui.beats.size)
            assertTrue(ui.beats.none { it is Beat.Contract } && Card.Contract !in cards)
        }
        // A day without a party report has no such card whatever is passed to the screen's side.
        val none = days.first { !it.contract }
        assertEquals(none.script.toUi(none.state, engine.content, engine.config).beats, none.script.toUi(none.state, engine.content, engine.config, contract = true).beats)
    }

    @Test
    fun aStoredCursorStillNamesItsCard() {
        val d = contractDays().first { it.script.aftermath.isNotEmpty() && it.script.quiet == null }
        val cards = d.cards()
        val id = d.resolution.commandId.value
        assertEquals(0, ShopDayPosition.index(cards, null))
        assertEquals(Card.Contract, cards[ShopDayPosition.index(cards, DayCursor(id, DayCursor.Stage.COUNTER, 0))])
        assertEquals(Card.Aftermath(0), cards[ShopDayPosition.index(cards, DayCursor(id, DayCursor.Stage.AFTERMATH, 0))])
        assertEquals(cards.lastIndex, ShopDayPosition.index(cards, DayCursor(id, DayCursor.Stage.TOMORROW, 5)))
        // Every card's own cursor comes back to it, through the stored text.
        for (at in cards.indices) assertEquals(at, ShopDayPosition.index(cards, DayCursor.decode(ShopDayPosition(cards, at).cursor(id).encode())))
        // A day laid out without the card (every classic day, every day stored before the guild): the old cursors, the old cards.
        val old = ShopDayPosition.beats(d.script)
        for (at in old.indices) {
            val cursor = ShopDayPosition(old, at).cursor(id)
            assertEquals(at, ShopDayPosition.index(old, cursor))
            assertEquals("the same card with the party's in front", old[at], cards[ShopDayPosition.index(cards, cursor.copy(index = cursor.index + if (cursor.stage == DayCursor.Stage.COUNTER) 1 else 0))])
        }
    }

    @Test
    fun theSiegeCardSaysTheWallsVerdictWhoStoodAndWhatItCost() {
        val seen = HashSet<SiegeVerdict>()
        assertTrue("sieges: ${siegeDays().size}", siegeDays().size > 10)
        for (d in siegeDays()) {
            val report = d.resolution.siege!!
            val beat = d.ui().beats.filterIsInstance<Beat.Aftermath>().single { it.card.kind == AftermathKind.SIEGE_HELD || it.card.kind == AftermathKind.SIEGE_LOST }
            val siege = assertNotNull(beat.card.siege).let { beat.card.siege!! }
            assertEquals("$d", report.verdict, siege.verdict)
            assertEquals("$d", when (report.verdict) { SiegeVerdict.HELD -> "The town held"; SiegeVerdict.HELD_AT_A_COST -> "The town held, at a cost"; SiegeVerdict.BREACHED -> "The wall was breached" }, siege.outcome)
            assertEquals("$d", report.verdict != SiegeVerdict.BREACHED, siege.held)
            assertEquals("$d", report.outerLine, siege.outerLine)
            assertEquals("$d", report.defenders.map { id -> d.state.heroes.getValue(id).fullName }, beat.card.champions.map { it.name })
            // The damage is the day's own record, and the report's number is the same one.
            assertEquals("$d", report.forgeDamage.takeIf { it > 0 }, siege.forgeDamage)
            assertEquals(report.fight?.highlights.orEmpty().take(3).map { it.text }, siege.highlights)
            assertEquals(report.fight, siege.fight)
            assertEquals("a siege waits for the player", 0, beat.millis)
            // A skipped day still says it: the evening card opens with the same words.
            val recap = when (val ending = d.ui().let { it.beats[it.endingIndex] }) { is Beat.Blessing -> ending.siege; is Beat.Tomorrow -> ending.recap; is Beat.Fallen -> ending.recap; else -> null }
            assertEquals("$d", siege.recap, recap)
            assertTrue(siege.recap, siege.recap.startsWith(siege.outcome))
            if (report.verdict == SiegeVerdict.HELD_AT_A_COST) assertTrue(siege.recap, "at a cost" in siege.recap)
            // The stored replay of the wall is still a siege replay the Gazette's stage can read: its last round is the forge's.
            val replay = assertNotNull(beat.card.replay).let { beat.card.replay!! }
            assertEquals("the forge", replay.rounds.last().defender)
            for (round in replay.rounds) round.attackerId?.let { id -> assertTrue("$d: $id struck and stood on the wall", report.defenders.any { it.value == id }) }
            seen += report.verdict
        }
        assertEquals("every verdict is reached", SiegeVerdict.entries.toSet(), seen)
    }

    @Test
    fun aClassicSiegeKeepsItsOwnWords() {
        val d = ShopDayFixtures.find("siege")!!
        val siege = d.script.toUi(d.state, d.engine.content, d.engine.config, contract = true).beats.filterIsInstance<Beat.Aftermath>().firstNotNullOf { it.card.siege }
        assertNull(siege.verdict); assertNull(siege.outerLine); assertNull(siege.fight)
        assertTrue(siege.highlights.isEmpty())
        assertEquals(if (siege.held) "The town held" else "The defenses broke", siege.outcome)
    }

    @Test
    fun theFightReportIsTheRecordInOrder() {
        val fights = days.mapNotNull { it.resolution.mission?.fight } + days.mapNotNull { it.resolution.siege?.fight }
        assertTrue(fights.size > 20)
        var chained = 0
        for (f in fights) {
            val lines = fightTimeline(f)
            assertEquals(f.events.map { it.text }, lines.map { it.text })
            assertEquals(f.events.count { it.kind == EventKind.ROUND_START }, lines.count { it.header })
            assertTrue(lines.filter { it.header }.all { it.depth == 0 && it.text.startsWith("Round ") })
            f.events.zip(lines).forEach { (e, line) ->
                if (line.header) return@forEach
                val parent = e.parentId?.let { id -> f.events.first { it.id == id } }
                val parentLine = parent?.let { lines[f.events.indexOf(it)] }
                assertEquals(e.text, if (parentLine == null || parentLine.header) 0 else parentLine.depth + 1, line.depth)
                if (line.depth > 1) chained++
            }
            val party = fightSide(f, Side.PARTY)
            assertEquals(f.actors.count { it.side == Side.PARTY }, party.size)
            f.actors.filter { it.side == Side.PARTY }.zip(party).forEach { (a, text) ->
                assertTrue(text, text.startsWith(a.name + " " + if (a.downed) "down" else "${a.health}/${a.maxHealth}"))
                assertEquals(text, a.fractured, "blade cracked" in text)
            }
            assertTrue(fightOutcomeLine(f), fightOutcomeLine(f).endsWith("${f.rounds} round" + if (f.rounds == 1) "" else "s"))
        }
        assertTrue("chains deeper than one step: $chained", chained > 0)
        assertFalse(fights.all { f -> f.actors.none { it.downed } })
    }
}
