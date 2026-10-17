package com.example.blacksmithproject

import com.example.blacksmithproject.ui.Fact
import com.example.blacksmithproject.ui.FactTone
import com.example.blacksmithproject.ui.RunEndKind
import com.example.blacksmithproject.ui.runEndKind
import com.example.blacksmithproject.ui.stockFact
import com.tinyblacksmith.core.content.GuildContent
import com.tinyblacksmith.core.engine.GameEngine
import com.tinyblacksmith.core.model.LegacyProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The pure parts of the sheets and the run end (visual pass 2026-10-17): the supplies stock line and how an era ended. */
class SheetModelsTest {
    private val engine = GameEngine()

    @Test
    fun aMaterialAlwaysInStockOrNotOnTheRoadYetHasNoStockLine() {
        assertNull(stockFact(null, onTheRoad = true))
        assertNull(stockFact(null, onTheRoad = false))
        // The deep road does not carry it yet: its note says when, a stock count would only repeat "none".
        assertNull(stockFact(0, onTheRoad = false))
        assertNull(stockFact(3, onTheRoad = false))
    }

    @Test
    fun soldOutIsSaidInWordsAsAWarningAndOtherwiseTheCountIsPlain() {
        assertEquals(Fact("Sold out today", tone = FactTone.WARN), stockFact(0, onTheRoad = true))
        assertEquals(Fact("2 left today"), stockFact(2, onTheRoad = true))
        assertEquals(FactTone.PLAIN, stockFact(1, onTheRoad = true)!!.tone)
    }

    @Test
    fun aRetiredGuildIsAnEndingChosenAndEverythingElseIsAFall() {
        val classic = engine.newRun(LegacyProfile(), 7)
        assertEquals(RunEndKind.FALLEN, runEndKind(classic))
        val guild = engine.newRun(LegacyProfile(), 7, charterId = GuildContent.TOWNS_LAST_HOPE)
        assertEquals(RunEndKind.FALLEN, runEndKind(guild))
        assertEquals(RunEndKind.RETIRED, runEndKind(guild.copy(guild = guild.guild!!.copy(retired = true))))
    }
}
