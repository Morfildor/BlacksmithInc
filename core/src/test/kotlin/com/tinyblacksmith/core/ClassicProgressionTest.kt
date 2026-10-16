package com.tinyblacksmith.core

import com.tinyblacksmith.core.content.Depth
import com.tinyblacksmith.core.content.LaunchContent
import com.tinyblacksmith.core.engine.*
import com.tinyblacksmith.core.model.*
import com.tinyblacksmith.core.persistence.SaveCodec
import kotlin.test.*

/** Real End Day/visitor/restock/save pipeline; only the forge's health and purse are funded to isolate access. */
class ClassicProgressionTest {
    private val engine = GameEngine(config = TestSupport.engine.config.copy(startingForgeIntegrity = 1000))
    private fun send(s: GameState, c: Command) = engine.handle(s, c).stateOrThrow()
    private fun next(s: GameState) = send(s, Command.EndDay(CommandId("progress:${s.day}")))
    private fun restart(s: GameState): GameState {
        val decoded = SaveCodec.decodeRun(SaveCodec.encodeRun(s))
        assertEquals(s, decoded)
        return assertIs<Compatibility.Result.Admitted>(Compatibility.admit(decoded, engine.content, engine.config)).state.also { assertEquals(s, it) }
    }

    @Test fun paidDeclinedAndIgnoredVisitsKeepTheirScheduleThroughDay15AndRestart() {
        for (seed in listOf(11L, 31L, 4242L)) for (answer in listOf("crate", Encounters.PASS, "ignore")) {
            var s = engine.newRun(LegacyProfile(), seed).copy(gold = 10000)
            var opening: Int? = null
            var visitorDay: Int? = null
            for (day in 1..15) {
                assertEquals(day, s.day)
                s = restart(s)
                s.encounter?.takeIf { it.defId == Depth.DEEP_ROAD && it.isOpen }?.let { visit ->
                    assertNull(visitorDay, "caravan appears once")
                    visitorDay = day
                    assertEquals(1, DeepRoad.fought(s.town))
                    opening = day + engine.config.depth.legend.deepRoadWaitDays
                    assertEquals(opening, s.deepRoadOpenDay)
                    // A restart while the offer is open must preserve the paid option and its dates.
                    assertEquals(Encounters.view(s, engine.content, engine.config), Encounters.view(restart(s), engine.content, engine.config))
                    if (answer != "ignore") {
                        val gold = s.gold
                        s = send(s, Command.ResolveEncounter(visit.id, answer, CommandId("progress:answer")))
                        assertEquals(gold - if (answer == "crate") engine.config.depth.legend.deepRoadCrateGold else 0, s.gold)
                        if (answer == "crate") opening = day
                        s = restart(s)
                    }
                }
                val expectedStage = when { day >= 11 -> 2; opening != null && day >= opening!! -> 1; else -> 0 }
                assertEquals(expectedStage, DeepRoad.stage(s), "$seed/$answer/day$day")
                val delayed = s.worldFlags[WorldEvents.FLAG_CARAVAN_DELAYED] == day
                for (m in engine.content.materials) {
                    val needed = engine.roadNeeds(m.id)
                    assertEquals(needed <= expectedStage, engine.onTheRoad(s, m.id), "${m.name}/day$day")
                    if (m.dailySupplierStock == null) continue
                    val stock = s.supplierStock.getValue(m.id)
                    if (needed > expectedStage) assertEquals(0, stock, "locked ${m.name}/day$day")
                    else if (!delayed || answer == "crate" && day == visitorDay && needed == 1) assertTrue(stock > 0, "unlocked ${m.name}/day$day")
                }
                if (day < 15) {
                    val uninterrupted = next(s)
                    assertEquals(uninterrupted, next(restart(s)), "restart must preserve End Day and RNG")
                    s = uninterrupted
                }
            }
            assertNotNull(visitorDay)
            assertTrue(visitorDay!! >= 6)
            assertEquals(1, s.eventCounters[Encounters.COUNTER_PREFIX + Depth.DEEP_ROAD])
            assertEquals(1, s.eventCounters[DeepRoad.OPENED])
            assertEquals(1, s.eventCounters[DeepRoad.SECOND])
        }
    }

    @Test fun lockedOutOfStockUnaffordableAndAvailableAreDifferentStates() {
        var s = engine.newRun(LegacyProfile(), 11).copy(gold = 10000)
        val id = LaunchContent.STARSTEEL
        assertFalse(engine.onTheRoad(s, id))
        assertIs<GameError.SupplierOutOfStock>(assertIs<CommandOutcome.Rejected>(engine.handle(s, Command.BuyMaterial(id))).error)
        while (s.day < 6) s = next(s)
        val visit = s.encounter!!
        assertEquals(Depth.DEEP_ROAD, visit.defId)
        s = send(s, Command.ResolveEncounter(visit.id, "crate", CommandId("buy:road")))
        assertTrue(engine.onTheRoad(s, id))
        assertIs<GameError.NotEnoughGold>(assertIs<CommandOutcome.Rejected>(engine.handle(s.copy(gold = 0), Command.BuyMaterial(id))).error)
        val owned = s.materials[id] ?: 0
        s = send(s, Command.BuyMaterial(id))
        assertEquals(owned + 1, s.materials[id])
        assertEquals(0, s.supplierStock[id])
        assertTrue(engine.onTheRoad(s, id), "sold out is still unlocked")
        assertIs<GameError.SupplierOutOfStock>(assertIs<CommandOutcome.Rejected>(engine.handle(s, Command.BuyMaterial(id))).error)
        // Owned material remains forgeable when the supplier has sold out.
        s = send(restart(s), Command.Forge(ForgeMode.QUICK, LaunchContent.SWORD, id, LaunchContent.EMBER_RESIN, null, Risk.BALANCED))
        assertEquals(owned, s.materials[id])
        do { s = next(s) } while (s.worldFlags[WorldEvents.FLAG_CARAVAN_DELAYED] == s.day)
        assertTrue(s.supplierStock.getValue(id) > 0)
    }

    @Test fun secondSiegeOpensEverythingEvenWhenTheCaravanStoryIsAbsent() {
        val content = engine.content.copy(encounters = engine.content.encounters.filter { it.id != Depth.DEEP_ROAD })
        val e = GameEngine(content, engine.config)
        var s = e.newRun(LegacyProfile(), 11)
        while (s.day < 15) {
            assertEquals(if (s.day >= 11) 2 else 0, DeepRoad.stage(s))
            s = e.handle(restart(s), Command.EndDay(CommandId("fallback:${s.day}"))).stateOrThrow()
        }
        assertNull(s.deepRoadOpenDay)
        assertEquals(2, DeepRoad.stage(s))
        assertTrue(content.materials.all { e.onTheRoad(s, it.id) })
    }

    @Test fun aDelayedCaravanDoesNotRelockTheRoadAndTheNextDeliveryRestocksIt() {
        var s = engine.newRun(LegacyProfile(), 11).copy(gold = 10000)
        while (s.day < 6) s = next(s)
        s = send(s, Command.ResolveEncounter(s.encounter!!.id, Encounters.PASS, CommandId("delay:wait")))
        s = next(s) // day 7; train due on day 8
        s = restart(s.copy(worldFlags = s.worldFlags + (WorldEvents.FLAG_CARAVAN_DELAYED to 8)))
        s = next(s)
        assertEquals(8, s.day)
        assertEquals(1, DeepRoad.stage(s))
        assertTrue(engine.onTheRoad(s, LaunchContent.STARSTEEL))
        assertEquals(0, s.supplierStock[LaunchContent.STARSTEEL], "open, but the delivery is delayed")
        assertIs<GameError.SupplierOutOfStock>(assertIs<CommandOutcome.Rejected>(engine.handle(s, Command.BuyMaterial(LaunchContent.STARSTEEL))).error)
        do { s = next(restart(s)) } while (s.worldFlags[WorldEvents.FLAG_CARAVAN_DELAYED] == s.day)
        assertTrue(s.supplierStock.getValue(LaunchContent.STARSTEEL) > 0)
    }

    @Test fun aCatalogWithoutVisitorsOpensAfterTheFirstSiegeWithoutPayment() {
        val e = GameEngine(engine.content.copy(encounters = emptyList()), engine.config)
        var s = e.newRun(LegacyProfile(), 11)
        while (s.day < 6) s = e.handle(s, Command.EndDay(CommandId("noVisitors:${s.day}"))).stateOrThrow()
        assertEquals(6, s.deepRoadOpenDay)
        assertEquals(1, DeepRoad.stage(s))
        assertNull(s.encounter)
        assertEquals(0, s.supplierStock[LaunchContent.MOONSTEEL])
    }
}
