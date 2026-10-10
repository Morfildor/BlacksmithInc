package com.tinyblacksmith.core

import com.tinyblacksmith.core.TestSupport.endDayAccepted
import com.tinyblacksmith.core.TestSupport.endDayId
import com.tinyblacksmith.core.TestSupport.engine
import com.tinyblacksmith.core.TestSupport.forgeAccepted
import com.tinyblacksmith.core.TestSupport.quickSword
import com.tinyblacksmith.core.TestSupport.withMaterials
import com.tinyblacksmith.core.content.LaunchContent
import com.tinyblacksmith.core.engine.Command
import com.tinyblacksmith.core.engine.CommandOutcome
import com.tinyblacksmith.core.engine.GameEngine
import com.tinyblacksmith.core.engine.ResolutionContext
import com.tinyblacksmith.core.engine.WorldEvents
import com.tinyblacksmith.core.gazette.Gazette
import com.tinyblacksmith.core.market.Market
import com.tinyblacksmith.core.model.*
import com.tinyblacksmith.core.persistence.SaveCodec
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** S04, S05, X14: every appearance at the counter is recorded with what was true when it happened, and recording it decides nothing. */
class ShopRecordTest {
    private val config = engine.config
    private val content = engine.content

    private fun GameState.tryRun(command: Command): GameState = (engine.handle(this, command) as? CommandOutcome.Accepted)?.state ?: this
    private fun names(s: GameState) = s.heroes.values.associate { it.id.value to it.fullName }

    /** The golden script's morning: restock, forge up to three quick swords, list every stored blade at the going rate. */
    private fun GameState.morning(): GameState {
        var s = this
        repeat(3) {
            if ((s.materials[LaunchContent.IRON] ?: 0) == 0) s = s.tryRun(Command.BuyMaterial(LaunchContent.IRON))
            if ((s.materials[LaunchContent.EMBER_RESIN] ?: 0) == 0) s = s.tryRun(Command.BuyMaterial(LaunchContent.EMBER_RESIN))
            s = s.tryRun(quickSword())
        }
        for (w in s.storedWeapons()) s = s.tryRun(Command.ToggleShelf(w.id, listed = true))
        return s
    }

    /** A day-1 run whose only weapons are [blades], made from one forged sword. */
    private fun shop(seed: Long = 7, blades: (Weapon) -> List<Weapon>): GameState {
        val out = engine.newRun(LegacyProfile(), seed).withMaterials().forgeAccepted(quickSword(Risk.SAFE))
        val template = out.state.weapon(out.forgedWeaponId!!).copy(affixes = emptyList(), flaws = emptyList(), element = null, fame = 0, condition = 100)
        return out.state.copy(weapons = blades(template).associateBy { it.id })
    }

    private fun Weapon.as_(id: String, power: Int = 30, quality: Int = 50, location: WeaponLocation = WeaponLocation.Storage) =
        copy(id = WeaponId(id), power = power, quality = quality, rarity = Rarity.RARE, location = location)

    private val request = CommissionId("c900")
    private fun GameState.asking(buyer: Hero = aliveHeroes().first()): GameState =
        copy(commissions = mapOf(request to Commission(request, buyer.id, LaunchContent.SWORD, 50, 140, day, day + 3, CommissionStatus.ACCEPTED)))

    /** What must hold of any recorded day against the state End Day started from. */
    private fun consistent(pre: GameState, r: DayResolution, at: String) {
        assertEquals(1, r.recordVersion, at)
        assertEquals(pre.listedWeapons().associate { it.id to it.listedPrice }, r.shelfPrices, "$at: the prices of the opening shelf")
        for (w in r.shopWeapons) assertEquals(WeaponSnapshot.of(pre.weapon(w.weaponId)), w, "$at: a blade as it was when the day opened")
        assertEquals(r.shopWeapons.size, r.shopWeapons.map { it.weaponId }.toSet().size, "$at: each blade once")
        val inShop = r.shopWeapons.map { it.weaponId }.toSet()
        assertTrue(pre.listedWeapons().all { it.id in inShop }, "$at: the whole opening shelf")
        assertEquals(r.visits.indices.toList(), r.visits.map { it.seq }, "$at: visits are numbered in the order they happened")
        val heroesSeen = r.visits.mapNotNull { it.heroId } + r.turnedAway
        assertEquals(heroesSeen.size, heroesSeen.toSet().size, "$at: one appearance per hero per day, served or turned away")
        for (v in r.visits) {
            assertTrue(v.considered.size <= Market.MAX_CONSIDERED && v.considered.all { it.weaponId in inShop }, "$at: ${v.heroName} weighed blades of this shop")
            assertTrue(v.eventIds.all { id -> r.events.any { it.id == id } }, "$at: the visit points at records of the day")
            if (v.purchasedWeaponId != null) assertEquals(v.purchasedWeaponId, v.considered.first().weaponId, "$at: the blade taken comes first")
            assertEquals(v.purchasedWeaponId != null, v.sale != null, "$at: a sale exactly when a blade left")
            if (v.kind == VisitKind.COLLECTOR) { assertNull(v.customer, at); continue }
            val hero = pre.hero(v.heroId!!)
            val c = assertNotNull(v.customer, at)
            assertEquals(listOf(hero.id, hero.fullName, hero.classId, hero.gold, hero.loyalty), listOf(c.heroId, c.name, c.classId, c.gold, c.loyalty), "$at: the customer as they walked in")
            assertEquals(pre.equippedWeapon(hero.id)?.let { WeaponSnapshot.of(it) }, c.equipped, "$at: what ${hero.fullName} carried in")
            if (v.kind != VisitKind.BROWSE) continue
            val credit = Market.tradeInCredit(pre.equippedWeapon(hero.id), config)
            for (k in v.considered) {
                assertEquals(r.shelfPrices[k.weaponId], k.price, "$at: the price on the shelf")
                val affordable = k.price <= hero.gold + credit
                assertEquals(if (affordable) null else k.price - hero.gold - credit, k.shortBy, "$at: gold missing after the trade-in")
                assertEquals(affordable, VisitFactor.CAN_AFFORD in k.factors, at)
                assertEquals(!affordable, VisitFactor.CANNOT_AFFORD in k.factors, at)
                assertEquals(c.equipped == null, VisitFactor.UNARMED in k.factors, at)
            }
        }
    }

    /**
     * The record is written beside the day and read by nothing: End Day from a state whose previous record was blanked
     * gives the same state, RNG streams included. That capture itself draws nothing and moves no outcome is the claim of
     * `GoldenStateTest`, which passes against the file recorded before the visit record existed.
     */
    @Test
    fun captureLeavesTheRngStreamsAndTheGameplayProjectionUnchanged() {
        for (seed in 1L..6L) {
            var s = engine.newRun(LegacyProfile(), seed)
            repeat(8) {
                if (s.isEnded) return@repeat
                s = s.morning()
                val kept = s.endDayAccepted().state
                val blanked = s.copy(lastResolution = null).endDayAccepted().state
                assertEquals(kept.rng, blanked.rng, "seed $seed day ${s.day}: the stored record is no input to the RNG")
                assertEquals(kept, blanked, "seed $seed day ${s.day}: nor to anything else")
                s = kept
            }
        }
    }

    @Test
    fun snapshotsShowTheCounterNotTheEvening() {
        var wornByEvening = 0; var paidByEvening = 0; var tradeIns = 0; var days = 0
        for (seed in 1L..20L) {
            var s = engine.newRun(LegacyProfile(), seed)
            repeat(15) {
                if (s.isEnded) return@repeat
                s = s.morning()
                val out = s.endDayAccepted()
                val r = out.resolution!!
                consistent(s, r, "seed $seed day ${r.day}")
                days++
                for (v in r.browsers.filter { it.purchasedWeaponId != null }) {
                    val counter = r.shopWeapons.first { it.weaponId == v.purchasedWeaponId }
                    val evening = out.state.weapons[v.purchasedWeaponId]
                    if (evening != null && evening.condition < counter.condition) wornByEvening++
                    if (out.state.hero(v.heroId!!).gold != v.customer!!.gold) paidByEvening++
                    val sale = v.sale!!
                    assertEquals(r.shelfPrices[v.purchasedWeaponId], sale.listedPrice)
                    assertEquals(sale.listedPrice!! - sale.tradeInCredit, sale.cashPaid)
                    if (sale.tradeInWeaponId != null) {
                        tradeIns++
                        assertEquals(sale.tradeInWeaponId, v.customer!!.equipped?.weaponId, "the blade they carried in is the one that came back")
                    }
                }
                s = out.state
            }
        }
        assertTrue(days > 100 && wornByEvening > 0 && paidByEvening > 0 && tradeIns > 0, "days=$days worn=$wornByEvening paid=$paidByEvening tradeIns=$tradeIns")
    }

    @Test
    fun aRefusalNamesTheBladeThatWasTooDear() {
        val cheap = WeaponId("cheap"); val dear = WeaponId("dear")
        var refusedDear = 0; var boughtCheapSawDear = 0; var onlyDear = 0
        for (seed in 1L..20L) {
            val s = shop(seed) { listOf(it.as_("cheap", power = 12, location = WeaponLocation.Shelf(1)), it.as_("dear", power = 300, location = WeaponLocation.Shelf(999_999))) }
            val r = s.endDayAccepted().resolution!!
            consistent(s, r, "seed $seed")
            for (v in r.browsers) {
                val k = v.considered.firstOrNull { it.weaponId == dear } ?: continue
                assertTrue(VisitFactor.CANNOT_AFFORD in k.factors && k.shortBy!! > 0, "the dear blade is named with what was missing: $k")
                assertTrue(VisitFactor.ABOVE_THEIR_CEILING in k.factors, "and as priced above what they hold fair: $k")
                if (v.purchasedWeaponId == cheap) boughtCheapSawDear++ else if (v.purchasedWeaponId == null) refusedDear++
            }
            // The dear blade alone: every visitor is turned away by the price, and each visit says by how much.
            val alone = s.copy(weapons = s.weapons - cheap)
            for (v in alone.endDayAccepted().resolution!!.browsers) {
                onlyDear++
                assertEquals(VisitReason.TOO_EXPENSIVE, v.reason)
                val k = v.considered.single()
                assertEquals(listOf(dear, 999_999), listOf(k.weaponId, k.price))
                assertEquals(999_999 - v.customer!!.gold - Market.tradeInCredit(alone.equippedWeapon(v.heroId!!), config), k.shortBy)
            }
        }
        assertTrue(refusedDear > 0 && boughtCheapSawDear > 0 && onlyDear > 0, "refused=$refusedDear boughtCheap=$boughtCheapSawDear onlyDear=$onlyDear")
    }

    @Test
    fun theCommissionPatronIsAVisitOfItsOwnKind() {
        val a = WeaponId("a")
        val s = shop { listOf(it.as_("a")) }.asking()
        val patron = s.aliveHeroes().first()
        val out = s.endDayAccepted()
        val r = out.resolution!!
        consistent(s, r, "commission day")
        val v = r.visits.single { it.kind == VisitKind.COMMISSION }
        assertEquals(0, v.seq, "the patron collects before the browsers")
        assertEquals(listOf(patron.id, a, VisitReason.COMMISSION_DELIVERED), listOf(v.heroId, v.purchasedWeaponId, v.reason))
        assertEquals(Sale(listedPrice = null, cashPaid = 140, commissionId = request), v.sale, "the reward is the coin; a blade from storage had no price")
        assertEquals(patron.gold, v.customer!!.gold, "the purse they walked in with")
        assertEquals(r.events.single { it.type == EventType.COMMISSION_COMPLETED }.id, v.eventIds.first())
        assertTrue(r.shopWeapons.any { it.weaponId == a } && a !in r.shelfPrices, "the stored blade is on the record, not on the shelf")
        assertEquals(140, r.ledger!!.income[IncomeKind.COMMISSION])
        assertTrue(r.browsers.none { it.purchasedWeaponId == a } && v !in r.browsers, "no browser is credited with it")

        // The collector is a visit too, with no hero behind it.
        val listed = shop { listOf(it.as_("a", location = WeaponLocation.Shelf(60)).copy(fame = 4)) }
        val ctx = ResolutionContext(listed, content, config)
        val record = WorldEvents.fire(ctx, WorldEvents.byId("collector"))
        val c = ctx.visits.single()
        assertEquals(listOf(VisitKind.COLLECTOR, null, null, a, VisitReason.COLLECTOR_PURCHASE), listOf(c.kind, c.heroId, c.customer, c.purchasedWeaponId, c.reason))
        assertEquals(Sale(listedPrice = 60, cashPaid = record.data.getValue("price").toInt()), c.sale)
        assertEquals(listOf(record.id), c.eventIds)
        assertEquals(listOf(Considered(a, 60, listOf(VisitFactor.COLLECTOR_PRIZE))), c.considered)
    }

    /**
     * A patron who collects today has had their turn: they are not also seated as a browser, and not turned away either.
     * Their own decision is still drawn, so everyone else is as willing as on the same day without the commission.
     */
    @Test
    fun aPatronAppearsOncePerDay() {
        val crowded = GameEngine(config = config.copy(customers = config.customers.copy(shopCapacity = 20, baseVisitChance = 2.0)))  // everyone at the ceiling, whatever the reputation
        fun GameState.day() = (crowded.handle(this, Command.EndDay(endDayId(this))) as CommandOutcome.Accepted).resolution!!
        var wouldHaveBrowsed = 0
        for (seed in 1L..20L) {
            val plain = shop(seed) { listOf(it.as_("a"), it.as_("b", location = WeaponLocation.Shelf(1_000_000))) }
            val patron = plain.aliveHeroes().first()
            val without = plain.day()
            val with = plain.asking(patron).day()
            consistent(plain.asking(patron), with, "seed $seed")
            assertEquals(listOf(VisitKind.COMMISSION), with.visits.filter { it.heroId == patron.id }.map { it.kind }, "seed $seed: the patron is seen once, collecting")
            assertTrue(patron.id !in with.turnedAway, "seed $seed")
            assertEquals(without.browsers.mapNotNull { it.heroId }.toSet() - patron.id, with.browsers.mapNotNull { it.heroId }.toSet(), "seed $seed: the others decide as they would have")
            if (without.browsers.any { it.heroId == patron.id }) wouldHaveBrowsed++
        }
        assertTrue(wouldHaveBrowsed >= 10, "the patron would have browsed on most of these days: $wouldHaveBrowsed")
    }

    @Test
    fun aShelfBladeTakenByAPatronIsStillInShopWeapons() {
        val a = WeaponId("a")
        var browsers = 0
        for (seed in 1L..12L) {
            val s = shop(seed) { listOf(it.as_("a", location = WeaponLocation.Shelf(77)), it.as_("b", power = 20, quality = 30, location = WeaponLocation.Shelf(30))) }.asking()
            val out = s.endDayAccepted()
            val r = out.resolution!!
            consistent(s, r, "seed $seed")
            assertEquals(a, out.state.commissions.getValue(request).deliveredWeaponId)
            assertEquals(WeaponSnapshot.of(s.weapon(a)), r.shopWeapons.single { it.weaponId == a }, "seed $seed: the blade as it stood on the opening shelf")
            assertEquals(77, r.shelfPrices[a])
            assertEquals(77, r.visits.single { it.kind == VisitKind.COMMISSION }.sale!!.listedPrice, "what it was listed at is kept beside the reward")
            assertTrue(r.browsers.none { v -> v.considered.any { it.weaponId == a } }, "seed $seed: it was gone before any browser looked")
            browsers += r.browsers.size
        }
        assertTrue(browsers > 12, "browsers did come: $browsers")
    }

    /**
     * Real ten-visitor days: a twelve-hero town whose shop seats ten, a full shelf every morning, fifteen days of ten
     * seeds. The largest of them, encoded as the save encodes it (visits, the shelf snapshots and prices, the day's
     * `SHOP_DAY` record), is what one stored day may add to the save.
     */
    @Test
    fun aTenVisitorDayEncodesUnderTwelveKilobytes() {
        val busy = GameEngine(config = config.copy(customers = config.customers.copy(shopCapacity = 10, baseVisitChance = 0.9, startingHeroes = 12)))
        fun payload(r: DayResolution) = SaveCodec.json.encodeToString(ListSerializer(MarketVisit.serializer()), r.visits).toByteArray().size +
            SaveCodec.json.encodeToString(ListSerializer(WeaponSnapshot.serializer()), r.shopWeapons).toByteArray().size +
            SaveCodec.json.encodeToString(MapSerializer(WeaponId.serializer(), Int.serializer()), r.shelfPrices).toByteArray().size +
            SaveCodec.json.encodeToString(EventRecord.serializer(), r.events.single { it.type == EventType.SHOP_DAY }).toByteArray().size
        val tenVisitorDays = mutableListOf<DayResolution>()
        for (seed in 1L..10L) {
            var s = busy.newRun(LegacyProfile(), seed)
            repeat(15) {
                if (s.isEnded) return@repeat
                s = s.morning()
                val out = busy.handle(s, Command.EndDay(endDayId(s))) as CommandOutcome.Accepted
                if (out.resolution!!.browsers.size == 10) tenVisitorDays += out.resolution!!
                s = out.state
            }
        }
        val largest = tenVisitorDays.maxBy { payload(it) }
        val sizes = tenVisitorDays.map { payload(it) }.sorted()
        println("SHOP RECORD ten-visitor days: ${sizes.size}, bytes min=${sizes.first()} median=${sizes[sizes.size / 2]} max=${sizes.last()}; the largest has ${largest.shopWeapons.size} blades, ${largest.visits.count { it.customer?.equipped != null }} armed visitors, ${largest.visits.count { it.sale != null }} sales")
        assertTrue(sizes.size >= 50, "ten-visitor days: ${sizes.size}")
        assertTrue(tenVisitorDays.any { r -> r.shopWeapons.size == config.shelfSlots && r.visits.all { it.customer?.equipped != null && it.considered.size == Market.MAX_CONSIDERED } }, "a full shelf and ten armed visitors who each weighed three blades is among them")
        // 12,191 bytes at balance 9. Rules 4 records nothing more for a visit; the seeds reach other days (12,370 at the largest), so the bound is 12.5 KB.
        assertTrue(sizes.last() < 12_800, "the largest ten-visitor day is ${sizes.last()} bytes")
    }

    @Test
    fun merchantResaleIsNotAShopVisit() {
        val blade = WeaponId("w900")
        var s = engine.newRun(LegacyProfile(), 5)
        repeat(2) { s = s.endDayAccepted().state }
        val fallen = s.aliveHeroes().first()
        val fellOn = s.day - config.weaponFates.merchantDelayDays
        val template = s.withMaterials().forgeAccepted(quickSword(Risk.SAFE)).let { it.state.weapon(it.forgedWeaponId!!) }
        val held = template.copy(
            id = blade, power = 40, location = WeaponLocation.Lost(fellOn, WeaponLocation.Lost.WITH_MERCHANT),
            history = listOf(HistoryEntry(1, fellOn, "SCAVENGED", "Taken from the field where ${fallen.fullName} fell.", listOf(fallen.id.value))),
        )
        val rich = s.copy(
            weapons = s.weapons + (blade to held),
            heroes = s.heroes.mapValues { (id, h) -> if (id == fallen.id) h.copy(fate = HeroFate.DEAD, health = 0, diedOnDay = fellOn) else h.copy(gold = 5000) },
            town = s.town.copy(championIds = s.town.championIds - fallen.id),
        )
        val out = rich.endDayAccepted()
        val r = out.resolution!!
        assertEquals(1, r.events.count { it.type == EventType.WEAPON_RESOLD && blade.value in it.subjectIds }, "the merchant did sell it")
        assertTrue(r.visits.none { v -> v.purchasedWeaponId == blade || v.considered.any { it.weaponId == blade } }, "but not at the smith's counter")
        assertTrue(r.shopWeapons.none { it.weaponId == blade } && blade !in r.shelfPrices)
        assertEquals(0, r.ledger!!.income.values.sum(), "and no gold reached the shop")
    }

    /** The `SHOP_DAY` record carries the tally, so a past day read from the log alone is told as its report was; it is never printed as news. */
    @Test
    fun aPastDaysTallyIsTheOneItsReportShowed() {
        var bought = 0
        for (seed in 1L..10L) {
            var s = engine.newRun(LegacyProfile(), seed)
            repeat(8) {
                if (s.isEnded) return@repeat
                s = s.morning()
                val out = s.endDayAccepted()
                val r = out.resolution!!
                val report = Gazette.edition(Gazette.dayRecords(out.state, r.day), names(out.state), r.visits, r.ledger, r.field)
                val shopDay = r.events.single { it.type == EventType.SHOP_DAY }
                assertEquals(r.browsers.size.toString(), shopDay.data["visitors"])
                assertTrue((report.lede + report.sections.flatMap { it.lines }).none { shopDay.text in it }, "the record is not a line of the paper")
                bought += r.browsers.count { it.purchasedWeaponId != null }
                s = out.state
                if (s.isEnded) return@repeat
                // A day later the report is gone; the log still tells the same tally.
                val later = s.morning().endDayAccepted().state
                assertTrue(later.lastResolution!!.day != r.day)
                assertEquals(report.tally, Gazette.edition(Gazette.dayRecords(later, r.day), names(later)).tally, "seed $seed day ${r.day}")
            }
        }
        assertTrue(bought > 0)
    }

    @Test
    fun theRecordSurvivesTheCodecAndOnlyTheLastDayIsKept() {
        var s = shop { listOf(it.as_("a"), it.as_("b", power = 25, location = WeaponLocation.Shelf(20))) }.asking()
        s = s.endDayAccepted().state
        assertTrue(s.lastResolution!!.visits.any { it.kind == VisitKind.COMMISSION } && s.lastResolution!!.shopWeapons.size == 2)
        assertEquals(s, SaveCodec.decodeRun(SaveCodec.encodeRun(s)))
        val next = s.morning().endDayAccepted().state
        val text = SaveCodec.encodeRun(next)
        // Snapshots live in the one stored report and nowhere else: the log holds one small SHOP_DAY record per day.
        assertEquals(next.lastResolution!!.visits.count { it.customer != null }, Regex("\\\\\"customer\\\\\":\\{").findAll(text).count())
        assertEquals(2, next.events.count { it.type == EventType.SHOP_DAY })
        assertTrue(next.events.filter { it.type == EventType.SHOP_DAY }.all { SaveCodec.json.encodeToString(EventRecord.serializer(), it).length < 300 })
    }
}
