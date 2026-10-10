package com.example.blacksmithproject

import com.example.blacksmithproject.ui.shopday.Beat
import com.example.blacksmithproject.ui.shopday.toUi
import com.tinyblacksmith.core.model.EventType
import com.tinyblacksmith.core.shopday.AftermathKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** A siege in the shop day: its own card that waits for the player, the recorded damage carried through, and a recap on the evening card. */
class SiegeOutcomeTest {
    private fun days() = (1L..8L).asSequence().flatMap { ShopDayFixtures.run(it, 25) } + (1L..3L).asSequence().flatMap { ShopDayFixtures.run(it, 60, forge = false) }
    private fun Beat.Aftermath.isSiege() = card.kind == AftermathKind.SIEGE_HELD || card.kind == AftermathKind.SIEGE_LOST

    @Test
    fun aSiegeCardWaitsCarriesTheRecordedDamageAndIsNeverDoubled() {
        var held = 0
        var lost = 0
        var damaged = 0
        for (d in days()) {
            val ui = d.script.toUi(d.state, d.engine.content, d.engine.config)
            val sieges = ui.beats.filterIsInstance<Beat.Aftermath>().filter { it.isSiege() }
            val recorded = d.resolution.events.count { it.type == EventType.SIEGE_WON || it.type == EventType.SIEGE_LOST }
            assertEquals("seed ${d.seed} day ${d.script.day}: one card per recorded siege", recorded, sieges.size)
            for (other in ui.beats.filterIsInstance<Beat.Aftermath>().filter { !it.isSiege() }) {
                assertTrue(other.millis > 0)
                assertNull(other.card.siege)
            }
            val beat = sieges.singleOrNull() ?: continue
            val siege = assertNotNull(beat.card.siege).let { beat.card.siege!! }
            assertEquals("a siege waits for the player at any speed", 0, beat.millis)
            assertEquals(d.script.day, siege.day)
            assertEquals(beat.card.kind == AftermathKind.SIEGE_HELD, siege.held)
            assertEquals(if (siege.held) "The town held" else "The defenses broke", siege.outcome)
            // The number is the day's own FORGE_DAMAGED record, never worked back from the forge as it stands now.
            val record = d.resolution.events.firstOrNull { it.type == EventType.FORGE_DAMAGED }?.data?.get("damage")?.toIntOrNull()
            assertEquals(record, siege.forgeDamage)
            assertEquals(d.state.town.integrity, siege.forgeHealthNow)
            if (siege.held) held++ else lost++
            if ((record ?: 0) > 0) damaged++
            // Skipping to the evening lands on a card that still says what the siege did.
            val recap = when (val ending = ui.beats[ui.endingIndex]) {
                is Beat.Blessing -> ending.siege
                is Beat.Tomorrow -> ending.recap
                is Beat.Fallen -> ending.recap   // the forge fell: its card still says what the siege did
                else -> null
            }
            assertEquals(siege.recap, recap)
            assertTrue(siege.recap.startsWith(siege.outcome))
            if (record != null) assertTrue(siege.recap, siege.recap.contains("forge damage $record"))
        }
        assertTrue("the fixtures reach a held siege", held > 0)
        assertTrue("the fixtures reach a lost siege", lost > 0)
        assertTrue("the fixtures reach a siege that damaged the forge", damaged > 0)
    }

    @Test
    fun aDayWithoutASiegeHasNoRecap() {
        val quiet = days().first { d -> d.resolution.events.none { it.type == EventType.SIEGE_WON || it.type == EventType.SIEGE_LOST } && !d.state.isEnded }
        val ui = quiet.script.toUi(quiet.state, quiet.engine.content, quiet.engine.config)
        assertNull((ui.beats.last() as Beat.Tomorrow).recap)
    }
}
