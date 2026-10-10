package com.tinyblacksmith.core

import com.tinyblacksmith.core.TestSupport.endDayAccepted
import com.tinyblacksmith.core.TestSupport.forgeAccepted
import com.tinyblacksmith.core.TestSupport.quickSword
import com.tinyblacksmith.core.TestSupport.run
import com.tinyblacksmith.core.TestSupport.withMaterials
import com.tinyblacksmith.core.content.LaunchContent
import com.tinyblacksmith.core.crafting.SignatureCatalog
import com.tinyblacksmith.core.engine.Command
import com.tinyblacksmith.core.engine.CommandOutcome
import com.tinyblacksmith.core.engine.GameEngine
import com.tinyblacksmith.core.gazette.Gazette
import com.tinyblacksmith.core.model.EventType
import com.tinyblacksmith.core.model.ForgeMode
import com.tinyblacksmith.core.model.GameState
import com.tinyblacksmith.core.model.LegacyProfile
import com.tinyblacksmith.core.model.Risk
import com.tinyblacksmith.core.persistence.SaveCodec
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** One edition per day: what the report shows is what the archive keeps (review F03). */
class DayEditionTest {
    private val engine = TestSupport.engine

    private fun names(s: GameState) = s.heroes.values.associate { it.id.value to it.fullName }

    @Test
    fun theDayReportCarriesPreparationRecordsExactlyOnce() {
        val start = engine.newRun(LegacyProfile(), 7L).withMaterials()
        val forged = start.forgeAccepted(quickSword())
        val listed = forged.state.run(Command.ToggleShelf(forged.forgedWeaponId!!, listed = true))
        val day = listed.day
        val out = listed.endDayAccepted()
        val report = out.resolution!!.events
        assertEquals(out.state.eventsForDay(day).map { it.id }, report.map { it.id }, "report and archive hold the same records")
        assertEquals(1, report.count { it.type == EventType.WEAPON_FORGED }, "the blade forged today is in today's report")
        assertEquals(report.size, report.map { it.id }.toSet().size, "no record appears twice")
        assertTrue(out.events.none { it.type == EventType.WEAPON_FORGED }, "Accepted.events still means emitted by this command")
    }

    @Test
    fun reportAndArchiveAreTheSameEdition() {
        val start = engine.newRun(LegacyProfile(), 11L).withMaterials().copy(gold = 5_000)
        val forged = start.forgeAccepted(quickSword())
        val id = forged.forgedWeaponId!!
        val prepared = forged.state.run(Command.ToggleShelf(id, listed = true)).run(Command.Hone(id)).run(Command.BuyTool("whetstone"))
        val out = prepared.endDayAccepted()
        val res = out.resolution!!
        val fromReport = Gazette.edition(res.events, names(out.state), res.visits, res.ledger, res.field)
        val fromArchive = Gazette.edition(out.state.eventsForDay(res.day), names(out.state), res.visits)
        assertEquals(fromArchive, fromReport)
        assertEquals(fromReport, Gazette.edition(Gazette.dayRecords(out.state, res.day), names(out.state), res.visits))
        val forgeLine = fromReport.sections.first { it.title == Gazette.FORGE }.lines.first()
        assertTrue("Forged 1 weapon" in forgeLine && "Listed 1" in forgeLine && "Honed 1" in forgeLine && "Bought" in forgeLine, forgeLine)
        assertEquals(res.events.count { it.type == EventType.WEAPON_SOLD }, res.events.filter { it.type == EventType.WEAPON_SOLD }.map { it.id }.toSet().size, "sales are counted once")
    }

    @Test
    fun aSignatureForgedWhilePlanningLeadsThePaper() {
        val def = SignatureCatalog.all.first()
        val launch = GameEngine(content = LaunchContent.catalog, config = engine.config.copy(signatureBaseChance = 1.0, signatureMaxChance = 1.0))
        val cmd = Command.Forge(ForgeMode.ADVANCED, def.familyId, def.coreId, def.augmentId, def.catalystId, def.risk ?: Risk.BALANCED)
        var checked = 0
        for (seed in 1L..40L) {
            val start = launch.newRun(LegacyProfile(), seed).copy(materials = LaunchContent.catalog.materials.associate { it.id to 50 }, energy = 100)
            val forged = launch.handle(start, cmd) as CommandOutcome.Accepted
            val signature = forged.events.firstOrNull { it.type == EventType.SIGNATURE_DISCOVERED } ?: continue
            val out = launch.handle(forged.state, Command.EndDay(TestSupport.endDayId(forged.state))) as CommandOutcome.Accepted
            val res = out.resolution!!
            val edition = Gazette.edition(res.events, names(out.state), res.visits)
            // Only a bigger story (priority 7+) outranks it; otherwise it is the lede. Either way the paper tells it.
            val outranked = res.events.any { it.priority > signature.priority }
            if (!outranked) {
                assertEquals(signature.text, edition.lede.first(), "seed $seed: the signature leads the paper")
                assertTrue(Gazette.headlines(res.events).contains(signature.text), "seed $seed: and it is a headline")
            }
            assertTrue(signature.text in edition.lede + edition.sections.flatMap { it.lines }, "seed $seed: the paper tells it")
            checked++
        }
        assertTrue(checked > 0, "no seed discovered the signature")
    }

    @Test
    fun anOldSavesLastReportIsBuiltFromTheArchive() {
        val state = SaveCodec.decodeRun(DayEditionTest::class.java.getResource("/saves/v1_forced_seed4242_day61.json")!!.readText())
        val stored = state.lastResolution!!
        assertEquals(60, stored.day)
        assertEquals(15, stored.events.size, "a v1 report holds the End Day records only")
        assertEquals(20, state.eventsForDay(60).size, "the archive also holds the preparation records")
        assertEquals(state.eventsForDay(60), Gazette.dayRecords(state, 60))
        assertEquals(state.eventsForDay(59), Gazette.dayRecords(state, 59), "any other day is the archive")
    }
}
