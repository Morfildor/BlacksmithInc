package com.example.blacksmithproject

import androidx.lifecycle.SavedStateHandle
import com.example.blacksmithproject.ui.*
import com.tinyblacksmith.core.content.Element
import com.tinyblacksmith.core.content.GuildContent
import com.tinyblacksmith.core.engine.Command
import com.tinyblacksmith.core.engine.stateOrThrow
import com.tinyblacksmith.core.model.*
import com.tinyblacksmith.core.persistence.SaveCodec
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ForgePurposeTest : ShopDayTestBase() {
    private val forged = engine.handle(fresh, forgeSword).stateOrThrow()
    private val blade = forged.storedWeapons().single()
    private val hero = forged.aliveHeroes().first()
    private val order = Commission(CommissionId("fit"), hero.id, blade.familyId, 1, 80, forged.day, forged.day + 3, CommissionStatus.ACCEPTED)
    private fun commission(c: Commission = order) = forged.copy(commissions = mapOf(c.id to c))
    private fun assess(s: GameState, p: ForgePurpose) = engine.assessPurpose(s, blade.id, p)!!

    @Test fun commissionFitQualityFamilyAndPropertyUseTheRecordedRequirements() {
        for (c in listOf(order, order.copy(minQuality = blade.quality + 5), order.copy(familyId = engine.content.families.first { it.id != blade.familyId }.id), order.copy(element = Element.entries.first { it != blade.element }))) {
            val s = commission(c)
            val a = assess(s, ForgePurpose(c.id, c))
            assertEquals(c == order, a.fits)
            if (c.minQuality > blade.quality) assertTrue(a.line, a.line.contains("Quality", ignoreCase = true))
            if (c.familyId != blade.familyId) assertTrue(a.line, a.line.contains(engine.content.family(c.familyId).name, ignoreCase = true))
        }
        val older = blade.copy(id = WeaponId("older"), quality = blade.quality - 1, name = "Older sword")
        val s = commission().copy(weapons = forged.weapons + (older.id to older))
        assertTrue(assess(s, ForgePurpose(order.id, order)).details.contains("Older sword will be collected"))
        assertFalse(engine.forgedFor(s, blade.id, order.id)!!.handsOver)
    }

    @Test fun closedMissingAndDeceasedTargetsHaveNonblockingFallbacks() {
        for (status in listOf(CommissionStatus.COMPLETED, CommissionStatus.EXPIRED, CommissionStatus.DECLINED)) {
            val a = assess(commission(order.copy(status = status)), ForgePurpose(order.id, order))
            assertNull(a.fits)
            assertTrue(a.details.contains("craft is complete"))
        }
        assertNull(assess(forged, ForgePurpose(order.id, order)).fits)
        assertNull(assess(commission().copy(heroes = forged.heroes - hero.id), ForgePurpose(order.id, order)).fits)
        val dead = hero.copy(fate = HeroFate.DEAD)
        assertNull(assess(commission().copy(heroes = forged.heroes + (hero.id to dead)), ForgePurpose(order.id, order)).fits)
    }

    @Test fun wantsUseMarketAffordabilityAndSayPurchaseIsUncertainInBothModes() {
        val guildStart = engine.newRun(LegacyProfile(), 7, charterId = GuildContent.TOWNS_LAST_HOPE)
        val guild = engine.handle(guildStart, Command.ChooseOpening(guildStart.guild!!.openingOffer.first().defId)).stateOrThrow()
        for (base in listOf(forged, guild.copy(weapons = guild.weapons + (blade.id to blade)))) {
            val h = base.aliveHeroes().first()
            val want = Want(blade.familyId, 1, 9999, base.day)
            val rich = h.copy(gold = 9999, want = want)
            val s = base.copy(heroes = base.heroes + (h.id to rich), weapons = mapOf(blade.id to blade))
            val p = ForgePurpose(heroId = h.id, want = want)
            val before = SaveCodec.encodeRun(s)
            val a = assess(s, p)
            assertTrue(a.line, a.line.contains("Possible match"))
            assertTrue(a.line.contains("Sale not guaranteed"))
            assertTrue(a.details.contains("Not an order. Nothing reserved"))
            val costly = blade.copy(location = WeaponLocation.Shelf(999999))
            val poor = s.copy(heroes = s.heroes + (h.id to rich.copy(gold = 0)), weapons = s.weapons + (blade.id to costly))
            assertTrue(assess(poor, p).line.contains("Cannot afford"))
            val weak = p.copy(want = want.copy(minPower = blade.power + 5))
            val power = s.copy(heroes = s.heroes + (h.id to rich.copy(want = weak.want)))
            assertTrue(assess(power, weak).line.contains("Power ${blade.power}"))
            assertEquals(before, SaveCodec.encodeRun(s))
            assertNull(s.weapons[blade.id]!!.promisedTo)
        }
    }

    @Test fun aChangedWantIsNeverAssessedAsTheOriginalAndFreeForgingHasNoAssessment() {
        val want = Want(blade.familyId, 1, 80, forged.day)
        val p = ForgePurpose(heroId = hero.id, want = want)
        for (h in listOf(hero.copy(want = null), hero.copy(want = want.copy(budget = 90)), hero.copy(want = want, fate = HeroFate.DEAD))) {
            assertNull(assess(forged.copy(heroes = forged.heroes + (hero.id to h)), p).fits)
        }
        assertNull(assess(forged.copy(heroes = forged.heroes - hero.id), p).fits)
        assertNull(engine.assessPurpose(forged, blade.id, null))
    }

    @Test fun aSaveRetryUsesThePurposeOfThePendingForge() = vmTest {
        val s = fresh.copy(commissions = mapOf(order.id to order))
        val repository = repo(s)
        val vm = open(repository)
        vm.forgeFor(order.id); advanceUntilIdle()
        repository.failNextCommit = java.io.IOException("disk full")
        vm.dispatch(forgeSword); advanceUntilIdle()
        assertNull(vm.playing().revealWeaponId)
        vm.updateDraft { ForgeDraft() }; advanceUntilIdle()
        vm.retry(); advanceUntilIdle()
        val result = vm.playing()
        assertNotNull(result.revealWeaponId)
        assertEquals(order, result.resultPurpose!!.commission)
    }

    @Test fun selectedWantSurvivesRestorationAndDoesNotFollowALaterWantForTheSameCustomer() = vmTest {
        val h = fresh.aliveHeroes().first()
        val want = Want(blade.familyId, 1, 80, fresh.day)
        val s = fresh.copy(heroes = fresh.heroes + (h.id to h.copy(want = want)))
        val handle = SavedStateHandle()
        val vm = open(repo(s), saved = handle)
        vm.forgeWant(h.id); advanceUntilIdle()
        val later = s.copy(heroes = s.heroes + (h.id to h.copy(want = want.copy(minPower = 999))))
        val restored = open(repo(later), saved = SavedStateHandle(handle.keys().associateWith { handle.get<Any?>(it) }))
        restored.dispatch(forgeSword); advanceUntilIdle()
        val result = restored.playing()
        assertEquals(want, result.resultPurpose!!.want)
        val a = engine.assessPurpose(result.state, result.revealWeaponId!!, result.resultPurpose)!!
        assertNull(a.fits)
        assertTrue(a.line.contains("no longer waiting for this want"))
        assertEquals(s.commissions, result.state.commissions)
    }

    @Test fun resultKeepsItsPurposeAcrossDraftChangesNavigationAndSavedState() = vmTest {
        val s = fresh.copy(commissions = mapOf(order.id to order))
        val handle = SavedStateHandle()
        val vm = open(repo(s), saved = handle)
        vm.forgeFor(order.id); advanceUntilIdle()
        vm.dispatch(forgeSword); advanceUntilIdle()
        val result = vm.playing()
        assertEquals(order, result.resultPurpose!!.commission)
        vm.updateDraft { ForgeDraft() }; vm.selectDest(Dest.SHOP); advanceUntilIdle()
        assertEquals(result.resultPurpose, vm.playing().resultPurpose)
        val restored = open(repo(vm.playing().state), saved = SavedStateHandle(handle.keys().associateWith { handle.get<Any?>(it) }))
        assertEquals(result.resultPurpose, restored.playing().resultPurpose)
        assertEquals(result.revealWeaponId, restored.playing().revealWeaponId)
        restored.priceForged(result.revealWeaponId!!); advanceUntilIdle()
        assertNull(restored.playing().resultPurpose)
        assertEquals(Sheet.Item(result.revealWeaponId), restored.playing().sheet)
        vm.storeForged(); advanceUntilIdle()
        vm.dispatch(forgeSword); advanceUntilIdle()
        assertNull(vm.playing().resultPurpose)
    }
}
