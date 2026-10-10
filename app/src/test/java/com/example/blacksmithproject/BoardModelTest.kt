package com.example.blacksmithproject

import com.example.blacksmithproject.ui.board
import com.example.blacksmithproject.ui.shopUi
import com.tinyblacksmith.core.model.Commission
import com.tinyblacksmith.core.model.CommissionId
import com.tinyblacksmith.core.model.CommissionStatus
import com.tinyblacksmith.core.model.Want
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The commission board: formal commissions and customer wants as two kinds of thing, every record of the Shop's model kept. */
class BoardModelTest : ShopDayTestBase() {
    private fun mornings() = (42L..53L).asSequence().flatMap { ShopDayFixtures.run(it, 40) }.map { it.state }.filter { !it.isEnded }

    @Test
    fun everyWantIsInExactlyOneGroupOfItsOwnFamily() {
        var grouped = 0
        for (state in mornings().filter { s -> s.aliveHeroes().any { it.want != null } }.take(60)) {
            val shop = engine.shopUi(state)
            val board = shop.board()
            assertEquals(shop.wants.map { it.heroId.value }.sorted(), board.groups.flatMap { g -> g.wants.map { it.heroId.value } }.sorted())
            for (g in board.groups) {
                assertTrue(g.wants.all { it.familyId == g.familyId })
                assertEquals("${engine.content.family(g.familyId).name} · ${g.wants.size} ${if (g.wants.size == 1) "customer" else "customers"}", g.title)
                // The facts of each want are the hero's own, never a sum or a promised price.
                for (w in g.wants) assertEquals(state.hero(w.heroId).want!!.minPower, w.minPower)
            }
            assertEquals(board.groups.map { it.familyId }.distinct(), board.groups.map { it.familyId })
            grouped += board.groups.count { it.wants.size > 1 }
        }
        assertTrue("the fixtures reach a family two heroes want", grouped > 0)
    }

    @Test
    fun acceptedCommissionsDueSoonComeFirstThenOffersAndTenRowsStayWhole() {
        val heroes = fresh.aliveHeroes()
        val family = engine.content.families.first().id
        // Ten is a layout stress case: the rules never open more than `maxOpenCommissions`.
        val ten = (0 until 10).map { i ->
            Commission(CommissionId("c$i"), heroes[i % heroes.size].id, family, 30, 100 + i, fresh.day, fresh.day + 1 + (i * 3) % 7, if (i % 2 == 0) CommissionStatus.OFFERED else CommissionStatus.ACCEPTED)
        }
        val board = engine.shopUi(fresh.copy(commissions = ten.associateBy { it.id })).board()
        assertEquals(10, board.commissions.size)
        val accepted = board.commissions.takeWhile { !it.offered }
        assertEquals(5, accepted.size)
        assertTrue(board.commissions.drop(5).all { it.offered })
        assertEquals(accepted.map { it.daysLeft }.sorted(), accepted.map { it.daysLeft })
        assertEquals("10 commissions · no customer wants", board.summary)
        assertEquals("5 await your answer", board.pending)
        assertEquals(setOf("Offer", "Accepted", "Due today").intersect(board.commissions.map { it.status }.toSet()), board.commissions.map { it.status }.toSet())
    }

    @Test
    fun theSummaryCountsBothKindsAndAnEmptyBoardSaysSo() {
        assertEquals("No commissions · no customer wants", engine.shopUi(fresh).board().summary)
        val hero = fresh.aliveHeroes().first()
        val family = engine.content.families.first().id
        val wanting = fresh.copy(heroes = fresh.heroes + (hero.id to hero.copy(want = Want(family, minPower = 10, budget = 95, sinceDay = fresh.day))))
        val board = engine.shopUi(wanting).board()
        assertEquals("No commissions · 1 customer want", board.summary)
        assertFalse(board.groups.single().wants.single().answered)
        assertEquals(null, board.pending)
    }
}
