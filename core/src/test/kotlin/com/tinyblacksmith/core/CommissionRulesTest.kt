package com.tinyblacksmith.core

import com.tinyblacksmith.core.TestSupport.endDay
import com.tinyblacksmith.core.TestSupport.endDayAccepted
import com.tinyblacksmith.core.TestSupport.engine
import com.tinyblacksmith.core.TestSupport.forgeAccepted
import com.tinyblacksmith.core.TestSupport.quickSword
import com.tinyblacksmith.core.TestSupport.withMaterials
import com.tinyblacksmith.core.content.Element
import com.tinyblacksmith.core.content.LaunchContent
import com.tinyblacksmith.core.crafting.Forge
import com.tinyblacksmith.core.market.Commissions
import com.tinyblacksmith.core.market.Commissions.Fit
import com.tinyblacksmith.core.market.Market
import com.tinyblacksmith.core.market.QualityBand
import com.tinyblacksmith.core.model.*
import com.tinyblacksmith.core.persistence.SaveCodec
import kotlinx.serialization.builtins.serializer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** F04, F06: a commission asks for a band floor, one rule decides what closes it, and the patron collects before the browsers. */
class CommissionRulesTest {
    private val config = engine.config
    private val content = engine.content
    private val fine = QualityBand.FINE.floor(config)

    /** A day-1 run whose only weapons are [blades], made from one forged sword. */
    private fun shop(seed: Long = 7, blades: (Weapon) -> List<Weapon>): GameState {
        val out = engine.newRun(LegacyProfile(), seed).withMaterials().forgeAccepted(quickSword(Risk.SAFE))
        val template = out.state.weapon(out.forgedWeaponId!!).copy(affixes = emptyList(), flaws = emptyList(), element = null, fame = 0, condition = 100)
        return out.state.copy(weapons = blades(template).associateBy { it.id })
    }

    private fun Weapon.as_(id: String, quality: Int, element: Element? = null, family: WeaponFamilyId = LaunchContent.SWORD, power: Int = 30, location: WeaponLocation = WeaponLocation.Storage) =
        copy(id = WeaponId(id), quality = quality, rarity = Forge.rarityFor(quality, config), element = element, familyId = family, power = power, location = location)

    private fun GameState.asking(minQuality: Int, element: Element? = null, deadlineIn: Int = 3, status: CommissionStatus = CommissionStatus.ACCEPTED): GameState {
        val c = Commission(CommissionId("c900"), aliveHeroes().first().id, LaunchContent.SWORD, minQuality, 140, day, day + deadlineIn, status, element = element)
        return copy(commissions = mapOf(c.id to c))
    }

    private val GameState.request get() = commissions.getValue(CommissionId("c900"))

    @Test
    fun aBladeOneQualityShortIsRefusedAndTheMissingCriterionIsNamed() {
        val short = shop { listOf(it.as_("a", fine - 1)) }.asking(fine)
        assertEquals(Fit.QUALITY, Commissions.fit(short.weapon(WeaponId("a")), short.request))
        assertNull(Commissions.pick(short.weapons.values, short.request, config))
        assertEquals(CommissionStatus.ACCEPTED, short.endDay().request.status, "one point short closes nothing")

        val enough = shop { listOf(it.as_("a", fine)) }.asking(fine)
        assertEquals(Fit.OK, Commissions.fit(enough.weapon(WeaponId("a")), enough.request))
        val done = enough.endDay().request
        assertEquals(CommissionStatus.COMPLETED to WeaponId("a"), done.status to done.deliveredWeaponId)
    }

    @Test
    fun everyVisibleBandBoundaryMatchesTheRule() {
        val s = shop { listOf(it.as_("a", 1)) }
        val blade = s.weapon(WeaponId("a"))
        assertEquals(listOf(0, config.uncommonMin, config.rareMin, config.epicMin, config.legendaryMin), QualityBand.entries.map { it.floor(config) })
        for (band in QualityBand.entries.drop(1)) {
            val floor = band.floor(config)
            assertEquals(band, QualityBand.of(floor, config))
            assertEquals(QualityBand.entries[band.ordinal - 1], QualityBand.of(floor - 1, config))
            // The word and the rarity change at the same quality, and that quality is what the request checks.
            assertEquals(band.ordinal, Forge.rarityFor(floor, config).ordinal)
            assertEquals(band.ordinal - 1, Forge.rarityFor(floor - 1, config).ordinal)
            val asked = s.asking(floor).request
            assertEquals("${band.word} Sword (quality $floor+)", Commissions.describe(asked, content, config))
            assertEquals(Fit.OK, Commissions.fit(blade.copy(quality = floor), asked))
            assertEquals(Fit.QUALITY, Commissions.fit(blade.copy(quality = floor - 1), asked))
        }
        assertEquals("fine frost Sword (quality $fine+)", Commissions.describe(s.asking(fine, Element.FROST).request, content, config))
        assertEquals(fine, config.ambitionCollectorQuality, "a collector wants a fine blade")

        // Every commission the game offers asks for a band floor, and its Gazette line says so.
        val standard = setOf(QualityBand.DECENT.floor(config), fine)
        val seen = mutableSetOf<Int>()
        for (seed in 1L..30L) {
            var run = engine.newRun(LegacyProfile(), seed)
            repeat(12) {
                if (!run.isEnded) run = run.endDay()
                for (c in run.commissions.values) {
                    assertTrue(c.minQuality in standard || c.minQuality == QualityBand.SUPERB.floor(config), "seed $seed: ${c.minQuality}")
                    seen += c.minQuality
                }
            }
            run.events.filter { it.type == EventType.COMMISSION_OFFERED }.forEach { e ->
                val c = run.commissions.getValue(CommissionId(e.subjectIds.last()))
                assertTrue(Commissions.describe(c, content, config) in e.text, e.text)
            }
        }
        assertEquals(standard + QualityBand.SUPERB.floor(config), seen, "decent, fine and (noble) superb requests all occur")
    }

    @Test
    fun deliveryOnTheDeadlineDayCounts() {
        val due = shop { listOf(it.as_("a", fine)) }.asking(fine, deadlineIn = 0)
        assertEquals(CommissionStatus.COMPLETED, due.endDay().request.status)
        val empty = shop { listOf(it.as_("a", fine - 1)) }.asking(fine, deadlineIn = 0)
        assertEquals(CommissionStatus.EXPIRED, empty.endDay().request.status)
    }

    @Test
    fun aSoleCandidateIsDeliveredBeforeBrowsersArrive() {
        var browsers = 0
        for (seed in 1L..12L) {
            // The only fitting blade is on the shelf for nothing: any browser who came first would take it.
            val s = shop(seed) { listOf(it.as_("a", fine, power = 60, location = WeaponLocation.Shelf(0))) }.asking(fine)
            val out = s.endDayAccepted()
            val done = out.state.request
            assertEquals(CommissionStatus.COMPLETED to WeaponId("a"), done.status to done.deliveredWeaponId, "seed $seed")
            assertTrue(out.resolution!!.browsers.none { it.purchasedWeaponId == WeaponId("a") })
            assertEquals(s.request.buyerId, out.state.weapon(WeaponId("a")).ownerId)
            browsers += out.resolution!!.browsers.size
        }
        assertTrue(browsers > 12, "browsers did come: $browsers")
    }

    @Test
    fun theLeastSufficientBladeIsHandedOver() {
        val s = shop { listOf(it.as_("best", 80), it.as_("mid", 62), it.as_("least", fine + 5), it.as_("short", fine - 1)) }.asking(fine)
        assertEquals(WeaponId("least"), Commissions.pick(s.weapons.values, s.request, config)!!.id)
        val after = s.endDay()
        assertEquals(WeaponId("least"), after.request.deliveredWeaponId)
        assertTrue(after.weapon(WeaponId("best")).isInStorage && after.weapon(WeaponId("mid")).isInStorage, "the better work stays with the smith")

        // Equal quality: the cheaper blade by the going rate; equal in that too: the lower ID.
        val tied = shop { listOf(it.as_("b", fine, power = 40), it.as_("c", fine, power = 20), it.as_("a", fine, power = 40)) }.asking(fine)
        assertTrue(Market.askingPrice(tied.weapon(WeaponId("c")), config) < Market.askingPrice(tied.weapon(WeaponId("a")), config))
        assertEquals(WeaponId("c"), Commissions.pick(tied.weapons.values, tied.request, config)!!.id)
        assertEquals(WeaponId("a"), Commissions.pick(tied.weapons.values.filter { it.id.value != "c" }, tied.request, config)!!.id)
    }

    @Test
    fun storageIsPreferredOverTheShelf() {
        val s = shop { listOf(it.as_("shelf", fine, location = WeaponLocation.Shelf(999_999)), it.as_("stored", 90)) }.asking(fine)
        assertEquals(WeaponId("stored"), Commissions.pick(s.weapons.values, s.request, config)!!.id, "a priced blade is taken only when storage has nothing that fits")
        val after = s.endDay()
        assertEquals(WeaponId("stored"), after.request.deliveredWeaponId)
        assertTrue(after.weapon(WeaponId("shelf")).isListed)

        val shelfOnly = shop { listOf(it.as_("shelf", fine, location = WeaponLocation.Shelf(999_999)), it.as_("stored", fine - 1)) }.asking(fine)
        assertEquals(WeaponId("shelf"), shelfOnly.endDay().request.deliveredWeaponId)
        // A blade already in a hero's hands is never a candidate.
        val owned = shop { listOf(it.as_("owned", 90)) }.asking(fine).let { st -> st.copy(weapons = st.weapons.mapValues { (_, w) -> w.copy(location = WeaponLocation.Owned(st.aliveHeroes().last().id, equipped = true)) }) }
        assertNull(Commissions.pick(owned.weapons.values, owned.request, config))
    }

    @Test
    fun familyAndElementMismatchesAreReportedSeparately() {
        val s = shop {
            listOf(it.as_("axe", 90, Element.FROST, family = LaunchContent.AXE), it.as_("plain", 90), it.as_("fire", 90, Element.FIRE), it.as_("dull", fine - 1, Element.FROST), it.as_("fits", fine, Element.FROST))
        }.asking(fine, Element.FROST)
        fun fit(id: String) = Commissions.fit(s.weapon(WeaponId(id)), s.request)
        assertEquals(listOf(Fit.FAMILY, Fit.ELEMENT, Fit.ELEMENT, Fit.QUALITY, Fit.OK), listOf("axe", "plain", "fire", "dull", "fits").map(::fit))
        // One criterion is named, in the order family, element, quality.
        assertEquals(Fit.FAMILY, Commissions.fit(s.weapon(WeaponId("axe")).copy(element = null, quality = 1), s.request))
        assertEquals(Fit.ELEMENT, Commissions.fit(s.weapon(WeaponId("plain")).copy(quality = 1), s.request))
        assertEquals(Fit.OK, Commissions.fit(s.weapon(WeaponId("fire")), s.request.copy(element = null)), "a request without an element takes any")
        assertEquals(WeaponId("fits"), s.endDay().request.deliveredWeaponId)
    }

    @Test
    fun anOpenOffBandCommissionIsLoweredOnLoad() {
        val s = engine.newRun(LegacyProfile(), 7)
        val buyer = s.aliveHeroes().first().id
        fun c(n: Int, quality: Int, status: CommissionStatus) = Commission(CommissionId("c$n"), buyer, LaunchContent.SWORD, quality, 100 + n, 1, 5, status)
        val saved = s.copy(commissions = listOf(
            c(1, 47, CommissionStatus.ACCEPTED), c(2, 59, CommissionStatus.OFFERED), c(3, 62, CommissionStatus.OFFERED), c(4, 74, CommissionStatus.ACCEPTED),
            c(5, 50, CommissionStatus.OFFERED), c(6, 47, CommissionStatus.EXPIRED), c(7, 62, CommissionStatus.COMPLETED), c(8, 47, CommissionStatus.DECLINED),
        ).associateBy { it.id })
        val payload = SaveCodec.json.encodeToString(GameState.serializer(), saved)
        fun envelope(schema: Int) = """{"schemaVersion":$schema,"payload":${SaveCodec.json.encodeToString(String.serializer(), payload)}}"""

        val loaded = SaveCodec.decodeRun(envelope(1))
        assertEquals(listOf(35, 50, 50, 70, 50, 47, 62, 47), loaded.commissions.values.sortedBy { it.id.value }.map { it.minQuality }, "open ones fall to their band floor; closed ones are history")
        assertEquals(saved.commissions.mapValues { it.value.copy(minQuality = 0) }, loaded.commissions.mapValues { it.value.copy(minQuality = 0) }, "nothing but the quality changes")
        assertEquals(saved.copy(commissions = emptyMap()), loaded.copy(commissions = emptyMap()))
        for (c in loaded.commissions.values.filter { it.status == CommissionStatus.OFFERED || it.status == CommissionStatus.ACCEPTED })
            assertEquals(c.minQuality, QualityBand.of(c.minQuality, config).floor(config))
        assertEquals(saved, SaveCodec.decodeRun(envelope(SaveCodec.SCHEMA_VERSION)), "a save of the current schema is not converted")
    }
}
