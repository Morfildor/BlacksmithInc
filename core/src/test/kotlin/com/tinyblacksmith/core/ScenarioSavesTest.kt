package com.tinyblacksmith.core

import com.tinyblacksmith.core.ScenarioSaves.byId
import com.tinyblacksmith.core.ScenarioSaves.endDay
import com.tinyblacksmith.core.TestSupport.engine
import com.tinyblacksmith.core.content.LaunchContent
import com.tinyblacksmith.core.engine.Command
import com.tinyblacksmith.core.engine.Compatibility
import com.tinyblacksmith.core.engine.Invariants
import com.tinyblacksmith.core.engine.acceptedOrThrow
import com.tinyblacksmith.core.legacy.Legacy
import com.tinyblacksmith.core.model.*
import com.tinyblacksmith.core.persistence.SaveCodec
import com.tinyblacksmith.core.shopday.AftermathKind
import com.tinyblacksmith.core.sim.Policy
import com.tinyblacksmith.core.sim.Simulator
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Every scenario save is a sound save, and playing it shows the mechanic it is named for. */
class ScenarioSavesTest {
    private fun cards(day: ScenarioSaves.Day) = day.script.aftermath.map { it.kind }

    @Test
    fun everySaveIsSoundAdmittedAndSurvivesTheCodec() {
        assertEquals(ScenarioSaves.all.size, ScenarioSaves.all.map { it.id }.toSet().size, "IDs are file names")
        for (sc in ScenarioSaves.all) {
            val s = sc.state
            assertEquals(Phase.PLANNING, s.phase, sc.id)
            assertEquals(emptyList(), Invariants.check(s, engine.config, engine.shelfSlots(s), engine.content), sc.id)
            val decoded = SaveCodec.decodeRun(SaveCodec.encodeRun(s))
            assertEquals(s, decoded, sc.id)
            assertIs<Compatibility.Result.Admitted>(Compatibility.admit(decoded, engine.content, engine.config), sc.id)
            assertEquals(sc.ownLegacy, s.legacy.eras.isNotEmpty(), "${sc.id}: only the legacy cases were played on an account")
        }
    }

    /** The app gives a save without an account the player's own legacy; the day must show the same card under any. */
    @Test
    fun theCardShowsUnderAnotherLegacyToo() {
        val maxed = Simulator.maxedLegacy(engine)
        for (sc in ScenarioSaves.all.filter { it.endDay && !it.ownLegacy }) {
            assertTrue(sc.card in cards(endDay(sc.state)), "${sc.id}: ${cards(endDay(sc.state))}")
            assertTrue(sc.card in cards(endDay(sc.state.copy(legacy = maxed))), "${sc.id} with every upgrade: ${cards(endDay(sc.state.copy(legacy = maxed)))}")
        }
    }

    @Test
    fun aHeroIsTaughtAtTheGuildHall() {
        val day = endDay(byId("hall_lesson").state)
        val lesson = day.resolution.field.first { it.outcome == FieldOutcome.GUILD_LESSON }
        val mentor = day.after.hero(lesson.withHeroId!!)
        val pupil = day.after.hero(lesson.heroId)
        assertEquals(mentor.guildId, pupil.guildId)
        assertTrue(day.before.hero(mentor.id).level > day.before.hero(pupil.id).level)
        assertTrue(day.resolution.events.any { it.type == EventType.GUILD_MENTORED && it.subjectIds == listOf(pupil.id.value, mentor.id.value) })
        assertEquals(AftermathKind.GUILD_LESSON, cards(day).first(), "the lesson is the first card after the counter")
    }

    @Test
    fun aFallenHerosBladePassesToAGuildmate() {
        val day = endDay(byId("inherited_blade").state)
        assertTrue(ScenarioSaves.inheritedOnDeath(day), cards(day).toString())
        val passed = day.resolution.events.first { it.type == EventType.WEAPON_INHERITED && it.data[WeaponFate.KEY] == WeaponFate.INHERITED.name }
        val (blade, heir, fallen) = passed.subjectIds
        assertEquals(HeroFate.DEAD, day.after.hero(HeroId(fallen)).fate)
        assertEquals(day.before.day, day.after.hero(HeroId(fallen)).diedOnDay)
        assertEquals(HeroId(fallen), day.before.weapon(WeaponId(blade)).ownerId)
        assertEquals(HeroId(heir), day.after.weapon(WeaponId(blade)).ownerId)
        assertTrue(day.after.hero(HeroId(heir)).isAlive)
        assertNotNull(day.after.hero(HeroId(heir)).guildId)
        assertEquals(day.after.hero(HeroId(fallen)).guildId, day.after.hero(HeroId(heir)).guildId)
    }

    @Test
    fun aMerchantSellsTheBladeHeHolds() {
        val day = endDay(byId("merchant_resale").state)
        val held = day.before.weapons.values.single { it.isWithMerchant }
        val sold = day.resolution.events.single { it.type == EventType.WEAPON_RESOLD }
        assertEquals(held.id.value, sold.subjectIds[1])
        assertEquals(WeaponFate.RESOLD.name, sold.data[WeaponFate.KEY])
        assertEquals(HeroId(sold.subjectIds[0]), day.after.weapon(held.id).ownerId)
        assertTrue(day.resolution.visits.none { it.purchasedWeaponId == held.id }, "not a sale of the shop")
    }

    @Test
    fun aChampionFallsAtTheWallAndTheForgeStands() {
        val s = byId("wall_death").state
        assertEquals(s.town.nextSiegeDay, s.day, "saved on the siege day")
        val day = endDay(s)
        assertTrue(ScenarioSaves.fellAtTheWall(day), cards(day).toString())
        assertEquals(AftermathKind.SIEGE_LOST, cards(day).first(), "the siege aftermath card comes first")
        val fell = day.resolution.field.first { it.outcome == FieldOutcome.FELL_AT_THE_WALL }
        assertEquals(HeroFate.DEAD, day.after.hero(fell.heroId).fate)
        assertTrue(day.resolution.events.any { it.type == EventType.SIEGE_LOST && it.data["rout"] == "true" && fell.heroId.value in it.subjectIds })
        assertTrue(day.resolution.events.any { it.type == EventType.HERO_DIED && "died defending the walls" in it.text })
        assertTrue(!day.after.isEnded && day.after.town.integrity > 0)
    }

    @Test
    fun knownNameGivesARegularOnDayOneWhoBuys() {
        val s = byId("known_name").state
        assertEquals(1, s.day)
        assertEquals(2, s.era)
        assertEquals(1, s.legacy.upgradeLevel(LaunchContent.UPG_REPUTATION))
        val regular = s.aliveHeroes().single { it.loyalty >= engine.config.regularLoyaltyThreshold }
        assertTrue(s.events.any { it.type == EventType.RUN_STARTED && "already a regular" in it.text && regular.id.value in it.subjectIds })
        val visit = endDay(s).script.featured.first { it.heroId == regular.id }
        assertTrue(visit.customer!!.regular)
        assertNotNull(visit.purchasedWeaponId)
    }

    @Test
    fun theReturnedLegendSleepsUntilItIsHoned() {
        val s = byId("returned_legend").state
        val blade = assertNotNull(ScenarioSaves.dormant(s))
        assertTrue(s.legacy.legendBoard.any { it.key == blade.legendKey }, "it is a blade of the account's Legend Board")
        assertTrue(s.lastResolution!!.events.any { it.type == EventType.ARTIFACT_RETURNED && it.subjectIds == listOf(blade.id.value) }, "it returned yesterday")
        assertTrue(blade.affixes.isEmpty())
        val out = engine.handle(s, Command.Hone(blade.id)).acceptedOrThrow()
        val woken = out.state.weapon(blade.id)
        assertEquals(blade.dormantAffixes, woken.affixes)
        assertTrue(woken.dormantAffixes.isEmpty())
        assertTrue(woken.power > blade.power)
        assertTrue(out.events.any { it.type == EventType.WEAPON_HONED && "woke" in it.data })
    }

    @Test
    fun theStoriedBladeWasSoldFoughtAndChangedHands() {
        val s = byId("storied_blade").state
        val blade = assertNotNull(ScenarioSaves.storied(s))
        assertTrue(blade.history.count { it.kind == "SOLD" } >= 2, "sold again after it came back")
        assertTrue(blade.history.count { it.kind == "VICTORY" || it.kind == "SIEGE" } >= 3)
        assertTrue(Legacy.holders(blade, s.era).size >= 2)
        val story = Legacy.story(blade).map { it.kind }
        assertTrue("SOLD" in story && "TRADED_IN" in story && "VICTORY" in story, story.toString())
    }

    @Test
    fun theLongStorageAddsTwoHundredBladesToAPlayedMorning() {
        val s = byId("long_storage").state
        val base = ScenarioSaves.morning(1, Policy.BALANCED_FAIR, 7)
        assertEquals(base.storedWeapons().size + ScenarioSaves.STORAGE_FORGES, s.storedWeapons().size)
        assertEquals(base.energy, s.energy)
        assertEquals(base.materials.filterValues { it > 0 }, s.materials.filterValues { it > 0 })
        assertEquals(engine.content.families.size, s.storedWeapons().map { it.familyId }.toSet().size, "every family is there to filter by")
    }
}
