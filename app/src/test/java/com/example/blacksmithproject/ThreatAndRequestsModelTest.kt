package com.example.blacksmithproject

import com.example.blacksmithproject.ui.shopUi
import com.example.blacksmithproject.ui.threatUi
import com.tinyblacksmith.core.content.Element
import com.tinyblacksmith.core.model.Commission
import com.tinyblacksmith.core.model.CommissionId
import com.tinyblacksmith.core.model.CommissionKind
import com.tinyblacksmith.core.model.CommissionStatus
import com.tinyblacksmith.core.shopday.Lines
import com.tinyblacksmith.core.shopday.ThreatMark
import com.tinyblacksmith.core.shopday.Threats
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The besieger on the Shop and the Forge, and the reason on a request card: `Threats`, `Lines.threat`, `Lines.threatMark`, `Lines.commissionWhy`. */
class ThreatAndRequestsModelTest : ShopDayTestBase() {
    private fun mornings() = (42L..47L).asSequence().flatMap { ShopDayFixtures.run(it, 40) }.map { it.state }.filter { !it.isEnded }

    @Test
    fun theThreatIsCoresOwnOnEveryMorning() {
        var warned = 0
        var marked = 0
        for (state in mornings().take(120)) {
            val t = Threats.of(state, engine.content, engine.config)!!
            val ui = engine.threatUi(state)!!
            assertEquals(Lines.threat(t, engine.content), ui.line)
            assertEquals(t.warned, ui.warned)
            assertEquals(Element.entries.filter { it == t.weakTo || it == t.resists }.toSet(), ui.marks.keys)
            for ((element, mark) in ui.marks) {
                val m = Threats.mark(element, t)!!
                assertEquals(Lines.threatMark(m, t, engine.content), mark.label)
                assertEquals(m == ThreatMark.COUNTERS, mark.counters)
            }
            val days = state.town.nextSiegeDay - state.day
            assertEquals(when { days <= 0 -> "Siege today"; days == 1 -> "Siege tomorrow"; else -> "Siege in $days days" }, ui.siege)
            assertTrue(ui.plate.startsWith(ui.siege))
            assertEquals("the note is said only on a warned day", t.warned && ui.marks.isNotEmpty(), ui.note != null)
            // The Shop carries the same object, and each stock row the mark of its own element.
            val shop = engine.shopUi(state)
            assertEquals(ui, shop.threat)
            for (row in shop.shelf + shop.storage) {
                assertEquals(row.weapon.name, row.weapon.element?.let { ui.marks[it] }, row.threat)
                if (row.threat != null) marked++
            }
            if (t.warned) warned++
        }
        assertTrue("the fixtures reach a warned day", warned > 0)
        assertTrue("the fixtures reach a blade the besieger cares about", marked > 0)
    }

    @Test
    fun aRequestCardSaysWhyItWasMade() {
        val buyer = fresh.aliveHeroes()[0]
        val newcomer = fresh.aliveHeroes()[1]
        val family = engine.content.families.first().id
        fun request(n: Int, kind: CommissionKind, status: CommissionStatus = CommissionStatus.OFFERED) =
            Commission(CommissionId("c$n"), if (n == 1) buyer.id else newcomer.id, family, 40, 80, fresh.day, fresh.day + 3 + n, status, kind = kind, recipientId = newcomer.id.takeIf { kind == CommissionKind.FIRST_BLADE })
        // Two open at once, as `maxOpenCommissions` allows: both are on the Shop, in deadline order, each with its own reason.
        val two = listOf(request(1, CommissionKind.FIRST_BLADE), request(2, CommissionKind.SIEGE_PREP, CommissionStatus.ACCEPTED))
        val state = fresh.copy(commissions = two.associateBy { it.id })
        val shop = engine.shopUi(state)
        assertEquals(2, engine.config.customers.maxOpenCommissions)
        assertEquals(engine.config.customers.maxOpenCommissions, shop.requestSlots)
        assertEquals(two.map { it.id }, shop.requests.map { it.id })
        assertEquals(two.map { Lines.commissionWhy(it, state) }, shop.requests.map { it.why })
        assertTrue(shop.requests[0].why!!.contains(newcomer.fullName) && shop.requests[0].why!!.contains(buyer.fullName))
        assertNotNull(shop.requests[1].why)
        assertFalse(shop.requests[1].offered)
        // An ordinary request has no reason to say.
        val plain = fresh.copy(commissions = mapOf(CommissionId("c1") to request(1, CommissionKind.ORDINARY)))
        assertNull(engine.shopUi(plain).requests.single().why)
    }
}
