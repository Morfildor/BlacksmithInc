package com.example.blacksmithproject

import com.tinyblacksmith.core.engine.Compatibility
import com.tinyblacksmith.core.engine.Invariants
import com.tinyblacksmith.core.model.VisitKind
import com.tinyblacksmith.core.persistence.SaveCodec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The debug scenario of members deciding for themselves is a sound save with every state the agency screens show. */
class LivingAgencyFixturesTest {
    @Test
    fun theScenarioIsAdmittedAndCarriesEveryAgencyState() {
        val f = LivingAgencyFixtures.build()
        val e = LivingAgencyFixtures.engine
        val s = f.state
        assertEquals(emptyList<String>(), Invariants.check(s, e.config, content = e.content))
        assertTrue(Compatibility.admit(SaveCodec.decodeRun(SaveCodec.encodeRun(s)), e.content, e.config) is Compatibility.Result.Admitted)
        val g = s.guild!!
        assertNotNull("a member bought at the counter", s.lastResolution?.visits?.firstOrNull { it.kind == VisitKind.MEMBER })
        assertNotNull(g.member(f.leaning)!!.lean)
        assertNotNull(g.member(f.departing)!!.concern?.departure)
        assertNotNull(g.members.firstOrNull { it.loanReturn != null })
        assertTrue(g.offers.any { it.sponsorHeroId != null })
        assertTrue("ib_goblin_sapper" in s.siege!!.plan.map { it.unitId })
    }

    @Test
    fun eachLivingVisitorScenarioIsSound() {
        val e = LivingAgencyFixtures.engine
        for (v in listOf("creditor_calls", "a_lesson", "wounded_friend")) {
            val s = LivingAgencyFixtures.build(visitor = v).state
            assertEquals(v, emptyList<String>(), Invariants.check(s, e.config, content = e.content))
            assertEquals(v, s.encounter?.defId)
            assertTrue(v, Compatibility.admit(SaveCodec.decodeRun(SaveCodec.encodeRun(s)), e.content, e.config) is Compatibility.Result.Admitted)
        }
    }
}
