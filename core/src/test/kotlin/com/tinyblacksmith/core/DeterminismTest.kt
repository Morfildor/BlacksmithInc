package com.tinyblacksmith.core

import com.tinyblacksmith.core.TestSupport.engine
import com.tinyblacksmith.core.battle.Battle
import com.tinyblacksmith.core.content.LaunchContent
import com.tinyblacksmith.core.engine.ResolutionContext
import com.tinyblacksmith.core.model.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Outcomes must not depend on the order a map happens to list its entries in (X11). */
class DeterminismTest {
    private fun ctx(state: GameState) = ResolutionContext(state, engine.content, engine.config)

    @Test
    fun factionTieIsBrokenById() {
        val s = engine.newRun(LegacyProfile(), 7).let { it.copy(town = it.town.copy(nextSiegeDay = it.day + 1)) }
        val ids = s.factions.keys.toList()
        assertEquals(listOf(LaunchContent.ASHCLAW, LaunchContent.HOLLOWBOUND, LaunchContent.EMBERMAW), ids, "catalog order is not ID order, so the two rules can be told apart")
        // Each pair tied at the top, and all three; every listing order of the same factions.
        val ties = listOf(listOf(ids[1], ids[2]), listOf(ids[0], ids[2]), listOf(ids[0], ids[1]), ids)
        for (tied in ties) {
            val pressures = s.factions.mapValues { (id, f) -> f.copy(pressure = if (id in tied) 60 else 10) }
            val expected = tied.minBy { it.value }
            for (order in listOf(ids, ids.reversed(), listOf(ids[2], ids[0], ids[1]))) {
                val state = s.copy(factions = order.associateWith { pressures.getValue(it) })
                assertEquals(order, state.factions.keys.toList())
                assertEquals(expected, Battle.leadingFaction(ctx(state))!!.id, "tie $tied listed as $order")
                assertEquals(expected, engine.siegeForecast(state)!!.factionState.id)
                val c = ctx(state)
                Battle.warnOfSiege(c)
                val warning = c.newEvents.single { it.type == EventType.SIEGE_WARNING }
                assertTrue(warning.text.startsWith(engine.content.faction(expected).name), warning.text)
            }
        }
        // No tie: the strongest leads whatever its ID.
        val clear = s.copy(factions = s.factions.mapValues { (id, f) -> f.copy(pressure = if (id == LaunchContent.HOLLOWBOUND) 61 else 60) })
        assertEquals(LaunchContent.HOLLOWBOUND, engine.siegeForecast(clear)!!.factionState.id)
    }
}
