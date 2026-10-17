package com.example.blacksmithproject

import com.example.blacksmithproject.ui.replay.reportLead
import com.example.blacksmithproject.ui.shopday.Beat
import com.example.blacksmithproject.ui.shopday.SiegeOutcomeUi
import com.example.blacksmithproject.ui.shopday.classicContribution
import com.example.blacksmithproject.ui.shopday.toUi
import com.tinyblacksmith.core.combat.FightAccounting
import com.tinyblacksmith.core.combat.FightReplay
import com.tinyblacksmith.core.combat.Side
import com.tinyblacksmith.core.engine.Command
import com.tinyblacksmith.core.engine.CommandOutcome
import com.tinyblacksmith.core.model.CombatReplay
import com.tinyblacksmith.core.model.CombatRound
import com.tinyblacksmith.core.model.CommandId
import com.tinyblacksmith.core.model.DayResolution
import com.tinyblacksmith.core.model.EventType
import com.tinyblacksmith.core.model.FieldOutcome
import com.tinyblacksmith.core.model.GameState
import com.tinyblacksmith.core.model.SiegeVerdict
import com.tinyblacksmith.core.persistence.SaveCodec
import com.tinyblacksmith.core.shopday.AftermathKind
import com.tinyblacksmith.core.shopday.ShopDay
import com.tinyblacksmith.core.shopday.ShopDayScript
import com.tinyblacksmith.core.sim.GuildBot
import com.tinyblacksmith.core.sim.GuildPolicy
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * T3: the one recorded contribution beside a siege, and the costs said once, from typed fields. Days are real (the guild
 * bots and the classic fixtures play the engine); the old and the cut records are those same days with fields taken away.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SiegeContributionUiTest : ShopDayTestBase() {
    private class SiegeDay(val seed: Long, val policy: GuildPolicy, val state: GameState, val resolution: DayResolution, val script: ShopDayScript) {
        override fun toString() = "$policy seed $seed day ${resolution.day}"
    }

    private val days: List<SiegeDay> by lazy {
        listOf(GuildPolicy.GREEDY_DIVER, GuildPolicy.SYNERGY, GuildPolicy.DEFENCE_FIRST, GuildPolicy.NOVICE, GuildPolicy.PASSIVE).flatMap { p ->
            (1L..6L).flatMap { seed ->
                val bot = GuildBot(engine, p)
                var s = engine.guildRun(seed)
                val out = ArrayList<SiegeDay>()
                while (!s.isEnded && s.day <= 24) {
                    val done = engine.handle(bot.planDay(s), Command.EndDay(CommandId("${s.runId.value}:day${s.day}"))) as CommandOutcome.Accepted
                    val r = done.resolution!!
                    if (r.siege != null) out += SiegeDay(seed, p, done.state, r, ShopDay.script(r, done.state, engine.content, engine.config))
                    s = done.state
                }
                out
            }
        }
    }

    private fun SiegeDay.siege(state: GameState = this.state, script: ShopDayScript = this.script): SiegeOutcomeUi =
        script.toUi(state, engine.content, engine.config).beats.filterIsInstance<Beat.Aftermath>().firstNotNullOf { it.card.siege }

    /** The same night as a save that holds less: [change] is applied to the day's own siege report. */
    private fun SiegeDay.with(change: (com.tinyblacksmith.core.model.SiegeReport) -> com.tinyblacksmith.core.model.SiegeReport): GameState =
        state.copy(lastResolution = resolution.copy(siege = change(resolution.siege!!)))

    @Test
    fun aGuildSiegeNamesOneContributionFromTheFightsOwnAccountOrNone() {
        assertTrue("sieges: ${days.size}", days.size > 10)
        var named = 0
        var omitted = 0
        for (d in days) {
            val siege = d.siege()
            val fight = d.resolution.siege!!.fight
            val expected = fight?.let { FightAccounting.of(it) }?.let { FightAccounting.standout(it) }?.text
            assertEquals("$d", expected, siege.contribution)
            if (expected != null) named++ else omitted++
            // A contribution is what was counted: no sentence of it claims the night or the fight was won by it.
            siege.contribution?.let { text -> listOf("won", "saved", "decided", "victory").forEach { assertFalse("$d: $text", it in text.lowercase()) } }
            // The report leads with the same line the card names, or with none, and with the siege's own notes.
            val lead = reportLead(siege)
            assertEquals("$d", siege.contribution, lead.contribution)
            assertEquals("$d", siege.notes, lead.notes)
            assertTrue("$d: ${lead.eyebrow}", lead.eyebrow.startsWith("SIEGE OF EMBERFALL · DAY ${siege.day}"))
            (listOfNotNull(lead.contribution) + lead.notes).forEach { line -> listOf("mvp", "best defender", "hero of the night").forEach { assertFalse("$d: $line", it in line.lowercase()) } }
        }
        assertTrue("some sieges have a contribution to name", named > 0)
        println("guild sieges: $named with a contribution, $omitted without")
    }

    @Test
    fun theCostsAreEachSaidOnceAndFromTheirOwnFields() {
        for (d in days) {
            val siege = d.siege()
            val report = d.resolution.siege!!
            val rows = siege.consequences
            assertEquals("$d: one row per fact", rows.map { it.label }.toSet().size, rows.size)
            assertEquals("$d", report.forgeDamage.takeIf { it > 0 }?.let { "−$it" } ?: "None", rows.first { it.label == "Forge damage" }.value)
            assertEquals("$d", "${d.state.town.integrity}", rows.first { it.label == "Forge health now" }.value)
            // Who fell is the day's own FELL_AT_THE_WALL record, by the name it kept; who went down and lived is the fight's.
            val fell = d.resolution.field.filter { it.outcome == FieldOutcome.FELL_AT_THE_WALL }
            // By the name the fight recorded, so the two casualty rows read alike.
            val names = fell.map { f -> report.fight!!.actors.first { it.key == f.heroId.value }.name }
            assertEquals("$d", names, siege.fellNames)
            assertEquals("$d", names.joinToString(", ").ifEmpty { "Nobody" }, rows.first { it.label == "Fell" }.value)
            val downed = report.fight!!.actors.filter { it.side == Side.PARTY && !it.summoned && it.downed }
            val lived = downed.filter { a -> fell.none { it.heroId.value == a.key } }.map { it.name }
            assertEquals("$d", lived, siege.wentDown)
            assertTrue("$d: a hero who fell is not also one who went down and lived", siege.wentDown!!.none { it in siege.fellNames })
            // A verdict of "held" with a defender down is the costly hold, and never the clean one.
            if (downed.isNotEmpty() && report.verdict != SiegeVerdict.BREACHED) assertEquals("$d", SiegeVerdict.HELD_AT_A_COST, report.verdict)
        }
    }

    @Test
    fun reopeningTheSameResultGivesTheSameContentAndTouchesNothing() {
        for (d in days.take(12)) {
            val before = SaveCodec.encodeRun(d.state)
            val first = d.siege()
            val fight = d.resolution.siege!!.fight!!
            val account = FightAccounting.of(fight)
            val replay = FightReplay.of(fight)
            // Opened again, and the report and the replay built again: the same values, and the run's save (RNG streams included) is the same bytes.
            repeat(3) {
                assertEquals("$d", first, d.siege())
                assertEquals("$d", account, FightAccounting.of(fight))
                assertEquals("$d", replay, FightReplay.of(fight))
            }
            assertEquals("$d", before, SaveCodec.encodeRun(d.state))
        }
    }

    @Test
    fun aRecordBeforeReplaysSaysNothingItCannotBackUp() {
        for (d in days.filter { it.resolution.siege!!.fight != null }.take(10)) {
            val old = d.siege(d.with { r -> r.copy(fight = r.fight!!.copy(record = 0)) })
            assertNull("$d: an older record cannot be accounted, so no contribution is invented", old.contribution)
            assertNull("$d: and the report has no lead line for it", reportLead(old).contribution)
            // The costs the record does say are still said, unchanged.
            assertEquals("$d", d.siege().consequences, old.consequences)
            assertEquals("$d", d.siege().outcome, old.outcome)
        }
    }

    @Test
    fun aWallWithNoFightRecordSaysWhoWasThereAndNothingElse() {
        val d = days.first()
        val siege = d.siege(d.with { it.copy(fight = null) })
        assertNull(siege.contribution)
        assertNull(reportLead(siege).contribution)
        assertNull("no fight: who went down is not known, and is not said", siege.wentDown)
        assertFalse(siege.consequences.any { it.label.startsWith("Went down") })
        assertTrue(siege.consequences.any { it.label == "Forge damage" })
    }

    @Test
    fun aHeroOrBladeThatIsGoneFromTheRunIsStillNamedAsTheNightRecordedThem() {
        val d = days.first { day -> day.resolution.field.any { it.outcome == FieldOutcome.FELL_AT_THE_WALL } || day.resolution.siege!!.fight != null }
        val gone = d.state.copy(heroes = d.state.heroes - d.resolution.siege!!.defenders.toSet(), weapons = emptyMap())
        val siege = d.siege(gone)
        val whole = d.siege()
        assertEquals("the card's words come from the night's records, not the roster", whole.contribution, siege.contribution)
        assertEquals(whole.fellNames, siege.fellNames)
        assertEquals(whole.wentDown, siege.wentDown)
    }

    @Test
    fun aClassicSiegeUsesTheDefenseItsChampionsCountedAndNoFightAccounting() {
        var checked = 0
        for (d in (1L..8L).asSequence().flatMap { ShopDayFixtures.run(it, 25) }) {
            val beat = d.script.toUi(d.state, d.engine.content, d.engine.config).beats.filterIsInstance<Beat.Aftermath>().firstOrNull { it.card.siege != null } ?: continue
            val siege = beat.card.siege!!
            assertNull(siege.fight); assertNull(siege.wentDown)
            assertFalse("a classic night has no 'went down' row", siege.consequences.any { it.label.startsWith("Went down") })
            // Champions on the wall are a typed record of who stood there: a night without deaths says so, held or lost.
            if (beat.card.champions.isNotEmpty()) assertEquals("Nobody".takeIf { siege.fellNames.isEmpty() } ?: siege.fellNames.joinToString(", "), siege.consequences.first { it.label == "Fell" }.value)
            val champions = beat.card.replay!!.rounds.filter { it.attackerId != null && it.damage > 0 }
            val text = siege.contribution
            if (champions.isEmpty()) assertNull(text) else {
                val best = champions.maxByOrNull { it.damage }!!
                assertEquals("${best.attacker} ${best.note}: ${best.damage}", text!!.substringBefore(" of the town's").removeSuffix(" defense.").removeSuffix("."))
                assertTrue(text, text.endsWith("defense."))
            }
            checked++
        }
        assertTrue("classic sieges reached: $checked", checked > 0)
    }

    @Test
    fun theClassicDefenseLineReadsOnlyWhatTheReplayRecorded() {
        fun round(who: String?, note: String, damage: Int, id: String? = who) = CombatRound(who ?: "Town militia", "the raiders", damage, note, id)
        val replay = CombatReplay("Siege of Emberfall, day 7", 7, listOf(round("Wren", "strikes with Ashen Blade", 24), round("Brann", "fights without a weapon", 24), round(null, "holds the gate", 30, null), CombatRound("raiders", "the forge", 12, "breaks through")), "Defenses broken")
        assertEquals("Wren strikes with Ashen Blade: 24 of the town's 78 defense.", classicContribution(replay, "78"))
        assertEquals("the first champion listed when equal, and no total when none was recorded", "Wren strikes with Ashen Blade: 24 defense.", classicContribution(replay, null))
        assertEquals("a total smaller than the part is not a total", "Wren strikes with Ashen Blade: 24 defense.", classicContribution(replay, "10"))
        assertNull("the militia is not a champion", classicContribution(replay.copy(rounds = replay.rounds.filter { it.attackerId == null }), "78"))
        assertNull(classicContribution(null, "78"))
    }

    @Test
    fun watchingTheDayAndOpeningSheetsLeavesTheRunAsItWasUntilItIsAcknowledged() = vmTest {
        // The evening before the first siege of a smith who never forges: the day has a siege card, then the fall or the long road on.
        val run = generateSequence(fresh) { if (it.isEnded) null else it.nextDay() }.take(30).firstOrNull { s ->
            !s.isEnded && s.nextDay().lastResolution!!.events.any { it.type == EventType.SIEGE_WON || it.type == EventType.SIEGE_LOST }
        }
        assertNotNull("an evening before a siege", run)
        val repo = planning(run!!)
        val vm = open(repo)
        vm.endDay(); advanceUntilIdle()
        val day = vm.day()
        val card = day.script.aftermath.first { it.kind == AftermathKind.SIEGE_HELD || it.kind == AftermathKind.SIEGE_LOST }
        val before = SaveCodec.encodeRun(day.state)
        // The card's links open read-only sheets over the day; none of them leaves it.
        card.championIds.firstOrNull()?.let { vm.openSheet(Sheet.Hero(it)); advanceUntilIdle(); vm.closeSheet(); advanceUntilIdle() }
        vm.openGazette(); advanceUntilIdle(); vm.back(); advanceUntilIdle()
        assertTrue("still the day", vm.ui.value is UiState.ShopDay)
        assertEquals("the run is the same bytes, RNG included", before, SaveCodec.encodeRun(vm.day().state))
        val done = com.tinyblacksmith.core.persistence.DayCursor(day.state.lastResolution!!.commandId.value, com.tinyblacksmith.core.persistence.DayCursor.Stage.DONE)
        assertTrue("this day is not acknowledged", repo.storedCursor() != done)
        // Planning stays locked until the day's own last button.
        val gold = vm.day().state.gold
        vm.dispatch(Command.BuyMaterial(com.tinyblacksmith.core.content.LaunchContent.IRON)); advanceUntilIdle()
        assertEquals(gold, vm.day().state.gold)
        vm.skipDay(); advanceUntilIdle(); vm.acknowledge(); advanceUntilIdle()
        assertTrue("acknowledged: now the live shop", vm.ui.value is UiState.Playing)
        assertEquals("only its own last button acknowledges the day", done, repo.storedCursor())
    }
}
