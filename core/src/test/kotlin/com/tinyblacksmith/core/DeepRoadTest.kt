package com.tinyblacksmith.core

import com.tinyblacksmith.core.TestSupport.admitted
import com.tinyblacksmith.core.TestSupport.endDay
import com.tinyblacksmith.core.TestSupport.guildRun
import com.tinyblacksmith.core.TestSupport.run
import com.tinyblacksmith.core.TestSupport.withMaterials
import com.tinyblacksmith.core.content.Depth
import com.tinyblacksmith.core.content.GuildContent
import com.tinyblacksmith.core.content.LaunchContent
import com.tinyblacksmith.core.crafting.Forge
import com.tinyblacksmith.core.engine.Command
import com.tinyblacksmith.core.engine.CommandOutcome
import com.tinyblacksmith.core.engine.DeepRoad
import com.tinyblacksmith.core.engine.GameEngine
import com.tinyblacksmith.core.engine.GameError
import com.tinyblacksmith.core.engine.ResolutionContext
import com.tinyblacksmith.core.engine.WorldEvents
import com.tinyblacksmith.core.engine.stateOrThrow
import com.tinyblacksmith.core.guild.Missions
import com.tinyblacksmith.core.model.*
import com.tinyblacksmith.core.rng.RngStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Owner playtest 2026-10-14 (balance 16, rules 7): the deep road, the quality each metal holds, and what a contract pays. */
class DeepRoadTest {
    private val engine = TestSupport.engine
    private val config = engine.config
    private val legend = config.depth.legend

    /** The last evening before the first morning after the first siege: the siege is fought, the next one is days away. */
    private fun afterFirstSiege(start: GameState) = start.copy(day = config.siegeInterval, town = start.town.copy(siegesSurvived = 1, nextSiegeDay = 2 * config.siegeInterval))

    private fun delayed(s: GameState) = s.worldFlags[WorldEvents.FLAG_CARAVAN_DELAYED] == s.day

    @Test
    fun theCaravanMasterOpensTheRoadTodayForTheCrateOrOnHerTrainsDay() {
        val morning = afterFirstSiege(engine.newRun(LegacyProfile(), 11)).endDay()
        val visit = morning.encounter!!
        assertEquals(Depth.DEEP_ROAD, visit.defId)
        val arrives = morning.day + legend.deepRoadWaitDays
        assertEquals(arrives, morning.deepRoadOpenDay, "the train comes whatever the answer")
        assertEquals(0, DeepRoad.stage(morning))
        assertEquals(0, morning.supplierStock[LaunchContent.STARSTEEL])

        // Paid: on sale this morning, and the Gazette says so once.
        val paid = morning.run(Command.ResolveEncounter(visit.id, "crate", CommandId("t:crate")))
        assertEquals(morning.gold - legend.deepRoadCrateGold, paid.gold)
        assertEquals(1, DeepRoad.stage(paid))
        assertTrue((paid.supplierStock[LaunchContent.STARSTEEL] ?: 0) >= 1)
        assertEquals(morning.materials[LaunchContent.STARSTEEL] ?: 0, (paid.copy(gold = 10_000).run(Command.BuyMaterial(LaunchContent.STARSTEEL)).materials[LaunchContent.STARSTEEL] ?: 0) - 1)
        assertEquals(1, paid.events.count { it.type == EventType.SUPPLIER_NEWS && it.data["road"] == DeepRoad.OPENED })
        assertEquals(1, paid.endDay().endDay().events.count { it.type == EventType.SUPPLIER_NEWS && it.data["road"] == DeepRoad.OPENED }, "said once")

        // Unanswered: shut until her train's day, then carried.
        var waited = morning
        while (waited.day < arrives) {
            assertEquals(0, DeepRoad.stage(waited), "day ${waited.day}")
            waited = waited.endDay()
        }
        assertEquals(1, DeepRoad.stage(waited))
        if (!delayed(waited)) assertTrue((waited.supplierStock[LaunchContent.OBSIDIAN] ?: 0) >= 1)
        assertEquals(0, waited.supplierStock[LaunchContent.MOONSTEEL], "Moonsteel waits for the second siege")
        assertNull(waited.endDay().encounter?.takeIf { it.defId == Depth.DEEP_ROAD }, "she comes once")
    }

    @Test
    fun aGuildRunHasTheContractUntilTheSecondSiegeAndWinningItOpensTheRoadTomorrow() {
        val morning = afterFirstSiege(engine.guildRun(LegacyProfile(), 11, GuildContent.TOWNS_LAST_HOPE)).endDay()
        assertNull(morning.encounter?.takeIf { it.defId == Depth.DEEP_ROAD }, "a guild run has the contract, not the visit")
        val offer = morning.guild!!.offers.single { it.defId == GuildContent.DEEP_ROAD }
        assertEquals(morning.town.nextSiegeDay - offer.days, offer.expiresDay, "back before the siege")
        assertTrue(offer.stages.last().reward.road && offer.stages.dropLast(1).none { it.reward.road }, "the road is the last stage's prize")
        assertNull(morning.deepRoadOpenDay)

        // What winning does, on the context the grant writes to.
        val ctx = ResolutionContext(morning, engine.content, config)
        assertTrue("opens tomorrow" in DeepRoad.openTomorrow(ctx, "Through."))
        val won = ctx.toState()
        assertEquals(0, DeepRoad.stage(won))
        assertEquals(1, DeepRoad.stage(won.endDay()))
        assertTrue(won.endDay().guild!!.offers.none { it.defId == GuildContent.DEEP_ROAD }, "no longer posted once open")

        // Never won: the second siege opens everything.
        val fallback = morning.copy(town = morning.town.copy(siegesLost = 1))
        assertEquals(2, DeepRoad.stage(fallback))
        assertTrue(fallback.endDay().guild!!.offers.none { it.defId == GuildContent.DEEP_ROAD })
    }

    @Test
    fun aRunFromBeforeTheRoadKeepsWhatWasOnSale() {
        val fresh = engine.newRun(LegacyProfile(), 5)
        val fought = fresh.copy(rulesVersion = 6, day = 7, town = fresh.town.copy(siegesSurvived = 1, nextSiegeDay = 10))
        assertEquals(7, fought.admitted().deepRoadOpenDay)
        assertEquals(1, DeepRoad.stage(fought.admitted()))
        assertNull(fresh.copy(rulesVersion = 6).admitted().deepRoadOpenDay, "before its first siege the road is still to open")
        assertNull(fought.copy(rulesVersion = GameEngine.RULES_VERSION).admitted().deepRoadOpenDay, "a rules-7 run is never opened by loading it")
    }

    @Test
    fun eachMetalHoldsOnlySoMuchQuality() {
        assertEquals(listOf(55, 65, 75, 84, 100, 100), (1..6).map { Forge.qualityCap(it, config) })
        for ((core, cap) in listOf(LaunchContent.IRON to 55, LaunchContent.BRONZE to 65, LaunchContent.SILVER to 75, LaunchContent.OBSIDIAN to 84)) {
            var best = 0
            for (seed in 1L..60L) {
                val s = engine.newRun(LegacyProfile(), seed).withMaterials()
                val out = engine.handle(s, Command.Forge(ForgeMode.ADVANCED, LaunchContent.BOW, core, LaunchContent.STORMGLASS, LaunchContent.BINDING_SALT, Risk.RECKLESS))
                val w = assertIs<CommandOutcome.Accepted>(out).state.weapons.values.single()
                assertTrue(w.quality <= cap, "${core.value}: ${w.quality} over $cap")
                best = maxOf(best, w.quality)
                // A hone stops at what the metal holds.
                assertTrue(assertIs<CommandOutcome.Accepted>(engine.handle(assertIs<CommandOutcome.Accepted>(out).state.copy(energy = 10), Command.Hone(w.id))).state.weapons.getValue(w.id).quality <= maxOf(cap, w.quality))
            }
            assertTrue(best >= cap - 10, "${core.value} reaches near its cap: $best")
        }
    }

    @Test
    fun aHoneAtTheCapSaysItOnlyRepaired() {
        val forged = engine.newRun(LegacyProfile(), 2).withMaterials().run(TestSupport.quickSword())
        val blade = forged.weapons.values.single().copy(quality = 55, condition = 60)
        val honed = engine.handle(forged.copy(weapons = mapOf(blade.id to blade)), Command.Hone(blade.id)) as CommandOutcome.Accepted
        assertEquals(55, honed.state.weapons.getValue(blade.id).quality)
        assertEquals(100, honed.state.weapons.getValue(blade.id).condition)
        assertTrue(honed.events.single { it.type == EventType.WEAPON_HONED }.text.endsWith("Quality 55 is the most Iron holds."))
    }

    @Test
    fun contractsPayThreeTimesTheirCard() {
        val s = engine.guildRun(LegacyProfile(), 21, GuildContent.TOWNS_LAST_HOPE)
        fun gold(percent: Int): Int {
            val e = GameEngine(engine.content, config.copy(guild = config.guild.copy(contractGoldPercent = percent)))
            val ctx = ResolutionContext(s, e.content, e.config)
            return Missions.generate(ctx, e.content.guild!!.mission(GuildContent.HUNT), s.factions.keys.first(), ctx.rng(RngStream.GUILD), "t").stages.sumOf { it.reward.gold }
        }
        assertEquals(300, config.guild.contractGoldPercent)
        assertEquals(gold(100) * 3, gold(300))
        assertTrue(gold(300) >= 3 * 55, "a hunt pays like a good sale: ${gold(300)}")
    }

    @Test
    fun theDearMaterialsCostWhatTheyShould() {
        val prices = engine.content.materials.associate { it.id to it.price }
        assertTrue(prices.getValue(LaunchContent.SILVER) + prices.getValue(LaunchContent.STORMGLASS) + prices.getValue(LaunchContent.DRAGON_OIL) > config.startingGold, "day 1 cannot buy the best of tier 3")
        val e = assertIs<CommandOutcome.Rejected>(engine.handle(engine.newRun(LegacyProfile(), 1), Command.BuyMaterial(LaunchContent.OBSIDIAN))).error
        assertIs<GameError.SupplierOutOfStock>(e)
    }
}
