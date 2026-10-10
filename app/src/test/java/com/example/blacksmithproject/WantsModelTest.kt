package com.example.blacksmithproject

import com.example.blacksmithproject.ui.detail.heroDetail
import com.example.blacksmithproject.ui.leadActionLabel
import com.example.blacksmithproject.ui.shopUi
import com.tinyblacksmith.core.content.LaunchContent
import com.tinyblacksmith.core.model.CommissionId
import com.tinyblacksmith.core.model.Want
import com.tinyblacksmith.core.shopday.Advice
import com.tinyblacksmith.core.shopday.Demand
import com.tinyblacksmith.core.shopday.LeadKind
import com.tinyblacksmith.core.shopday.Lines
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Standing wants on the Shop, the lead, the hero sheet and "Forge this": every line is core's, every mark is `Demand.summary`'s. */
@OptIn(ExperimentalCoroutinesApi::class)
class WantsModelTest : ShopDayTestBase() {
    private fun mornings() = (42L..53L).asSequence().flatMap { ShopDayFixtures.run(it, 40) }.map { it.state }.filter { !it.isEnded }

    @Test
    fun whoIsBuyingSaysEveryStandingWantAndMarksTheAnsweredOnes() {
        // Mornings the real engine reached: at least one with a want the shelf answers and one with a want it does not.
        val withWants = mornings().filter { s -> s.aliveHeroes().any { it.want != null } }.take(60).toList()
        assertTrue("no morning with a standing want", withWants.isNotEmpty())
        var answered = 0
        var open = 0
        for (state in withWants) {
            val d = Demand.summary(state, engine.content, engine.config)
            val shop = engine.shopUi(state)
            assertEquals(d.wants, shop.wants.map { it.heroId })
            assertEquals(d.wants.map { Lines.want(state.hero(it), engine.content) }, shop.wants.map { it.line })
            assertEquals(d.wantsAnswered, shop.wants.filter { it.answered }.map { it.heroId })
            assertEquals(d.wants.map { state.hero(it).want!!.familyId }, shop.wants.map { it.familyId })
            answered += d.wantsAnswered.size
            open += d.wants.size - d.wantsAnswered.size
            // The hero sheet says the same line; a hero without a want says nothing.
            for (w in shop.wants) assertEquals(w.line, engine.heroDetail(state, w.heroId)!!.want)
            state.aliveHeroes().firstOrNull { it.want == null }?.let { assertNull(engine.heroDetail(state, it.id)!!.want) }
        }
        assertTrue("the fixtures reach an unanswered want", open > 0)
        assertTrue("the fixtures reach an answered want", answered > 0)
    }

    @Test
    fun aWantNothingAnswersLeadsTheShopAndForgeThisSetsTheFamily() = vmTest {
        // Day 1 with swords on the shelf, and one hero who left without a blade of a family nobody has listed.
        val hero = stocked.aliveHeroes().first()
        val family = engine.content.families.first { f -> stocked.listedWeapons().none { it.familyId == f.id } }.id
        val state = stocked.copy(heroes = stocked.heroes + (hero.id to hero.copy(want = Want(family, minPower = 10, budget = 95, sinceDay = stocked.day))))
        val vm = open(planning(state))
        val shop = vm.playing().shop
        val lead = Advice.lead(state, engine.content, engine.config)
        assertEquals(LeadKind.ANSWER_WANT, lead.kind)
        val line = Lines.lead(lead, state, engine.content, engine.config)
        assertEquals(line.action to line.reason, shop.lead.action to shop.lead.reason)
        assertNotNull(shop.lead.reason)
        assertEquals(family, shop.lead.familyId)
        assertEquals("Forge this", leadActionLabel(shop.lead.kind))
        assertEquals(listOf(hero.id), shop.wants.map { it.heroId })
        assertFalse(shop.wants.single().answered)

        vm.updateDraft { it.copy(coreId = LaunchContent.IRON, commissionId = CommissionId("gone")) }
        vm.forgeFamily(family); advanceUntilIdle()
        val p = vm.playing()
        assertEquals(Dest.FORGE, p.dest)
        assertEquals("the family asked for, the rest of the draft kept, no request linked", ForgeDraft(familyId = family, coreId = LaunchContent.IRON), p.draft)
        assertEquals("nothing was issued: the save is the same", state, p.state)
    }
}
