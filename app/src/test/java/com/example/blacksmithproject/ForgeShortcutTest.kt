package com.example.blacksmithproject

import androidx.lifecycle.SavedStateHandle
import com.example.blacksmithproject.ui.supplyNotes
import com.example.blacksmithproject.ui.townTies
import com.tinyblacksmith.core.content.LaunchContent
import com.tinyblacksmith.core.content.MaterialCategory
import com.tinyblacksmith.core.engine.WorldEvents
import com.tinyblacksmith.core.model.CommissionStatus
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** "Forge this" on a request, and the lines the Supplies sheet and the Town rows add from the save. */
@OptIn(ExperimentalCoroutinesApi::class)
class ForgeShortcutTest : ShopDayTestBase() {
    private fun mornings() = (42L..53L).asSequence().flatMap { ShopDayFixtures.run(it, 40) }.map { it.state }.filter { !it.isEnded }

    @Test
    fun forgeThisPrefillsTheDraftFromARequest() = vmTest {
        // A morning with an open request that names an element, played by the real engine.
        val state = mornings().firstOrNull { s -> s.commissions.values.any { it.element != null && (it.status == CommissionStatus.OFFERED || it.status == CommissionStatus.ACCEPTED) && s.heroes[it.buyerId]?.isAlive == true } }
        assertNotNull("no morning with a request for an element", state)
        val asked = state!!.commissions.values.first { it.element != null && (it.status == CommissionStatus.OFFERED || it.status == CommissionStatus.ACCEPTED) }
        val handle = SavedStateHandle()
        val vm = open(planning(state), saved = handle)
        val before = vm.playing().draft
        vm.forgeFor(asked.id); advanceUntilIdle()
        val p = vm.playing()
        assertEquals(Dest.FORGE, p.dest)
        assertEquals(asked.familyId, p.draft.familyId)
        assertEquals("an augment of the element asked for", asked.element, engine.content.material(p.draft.augmentId!!).element)
        assertEquals(MaterialCategory.AUGMENT, engine.content.material(p.draft.augmentId!!).category)
        assertEquals(asked.id, p.draft.commissionId)
        assertEquals("the rest of the draft is the player's", before.copy(familyId = p.draft.familyId, augmentId = p.draft.augmentId, commissionId = asked.id), p.draft)
        assertEquals("nothing was issued: the save is the same", state, p.state)
        // The link survives the process, and the player can drop it.
        val reopened = open(planning(state), saved = SavedStateHandle(handle.keys().associateWith { handle.get<Any?>(it) }))
        assertEquals(p.draft, reopened.playing().draft)
        vm.updateDraft { it.copy(commissionId = null) }; advanceUntilIdle()
        assertNull(vm.playing().draft.commissionId)
        assertEquals(asked.familyId, vm.playing().draft.familyId)
    }

    @Test
    fun aRequestWithNoElementSetsOnlyTheFamily() = vmTest {
        val state = mornings().firstOrNull { s -> s.commissions.values.any { it.element == null && (it.status == CommissionStatus.OFFERED || it.status == CommissionStatus.ACCEPTED) } }
        assertNotNull("no morning with a plain request", state)
        val asked = state!!.commissions.values.first { it.element == null && (it.status == CommissionStatus.OFFERED || it.status == CommissionStatus.ACCEPTED) }
        val vm = open(planning(state))
        vm.updateDraft { it.copy(augmentId = LaunchContent.EMBER_RESIN) }
        vm.forgeFor(asked.id); advanceUntilIdle()
        assertEquals(asked.familyId to LaunchContent.EMBER_RESIN, vm.playing().draft.let { it.familyId to it.augmentId })
    }

    @Test
    fun suppliesMarkCaravanTiesTheOreMerchantAndALateCaravan() {
        val rare = engine.content.materials.first { it.dailySupplierStock != null }
        val common = engine.content.materials.first { it.dailySupplierStock == null }
        assertTrue(engine.supplyNotes(fresh, rare).isEmpty())
        val tied = fresh.copy(legacy = fresh.legacy.copy(upgrades = mapOf(LaunchContent.UPG_CATALOG to 2)))
        assertEquals(listOf("Caravan Ties: ${2 * engine.config.legacyTracks.catalogStockPerLevel} more each day"), engine.supplyNotes(tied, rare))
        val merchant = fresh.copy(worldFlags = mapOf(WorldEvents.FLAG_ORE_MERCHANT + rare.id.value to fresh.day))
        assertEquals(listOf("Ore merchant in town: ${engine.config.worldEvents.oreMerchantStock} more today"), engine.supplyNotes(merchant, rare))
        assertTrue("yesterday's merchant is gone", engine.supplyNotes(fresh.copy(worldFlags = mapOf(WorldEvents.FLAG_ORE_MERCHANT + rare.id.value to fresh.day - 1)), rare).isEmpty())
        assertEquals(1, engine.supplyNotes(fresh.copy(worldFlags = mapOf(WorldEvents.FLAG_CARAVAN_DELAYED to fresh.day)), rare).size)
        assertTrue("a metal that is always in stock has no note", engine.supplyNotes(tied.copy(worldFlags = merchant.worldFlags), common).isEmpty())
    }

    @Test
    fun townRowsNameGuildMentorAndRegulars() {
        val h = fresh.aliveHeroes().first()
        assertNull(townTies(h, fresh, engine.config))
        val regular = h.copy(loyalty = engine.config.regularLoyaltyThreshold, mentorName = "Old Bram")
        assertEquals("mentor Old Bram · a regular of your shop", townTies(regular, fresh, engine.config))
        assertEquals("mentor Old Bram", townTies(regular.copy(fate = com.tinyblacksmith.core.model.HeroFate.DEAD), fresh, engine.config))
    }
}
