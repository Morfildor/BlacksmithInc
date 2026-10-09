package com.tinyblacksmith.core

import com.tinyblacksmith.core.TestSupport.admitted
import com.tinyblacksmith.core.TestSupport.endDayId
import com.tinyblacksmith.core.TestSupport.engine
import com.tinyblacksmith.core.engine.Command
import com.tinyblacksmith.core.engine.CommandOutcome
import com.tinyblacksmith.core.engine.GameEngine
import com.tinyblacksmith.core.engine.Invariants
import com.tinyblacksmith.core.heroes.Appearance
import com.tinyblacksmith.core.model.CommissionStatus
import com.tinyblacksmith.core.model.EventType
import com.tinyblacksmith.core.model.GameState
import com.tinyblacksmith.core.model.Phase
import com.tinyblacksmith.core.model.VisitReason
import com.tinyblacksmith.core.persistence.SaveCodec
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Regression anchor for the save schema: a real mid-run v1 save captured with the codec as it stood on 2026-10-08
 * (forced survival, seed 4242, BALANCED_FAIR, 60 End Days). Every later schema change must still decode it,
 * migrate it and leave it playable. Byte-equality against the fixture is deliberately NOT asserted: fields added
 * with defaults change the encoded text (`encodeDefaults = true`) without changing the decoded state.
 */
class SaveFixtureTest {
    private companion object {
        const val FIXTURE = "/saves/v1_forced_seed4242_day61.json"
        val combatKinds = setOf("VICTORY", "SIEGE")

        val fixtureText: String by lazy { SaveFixtureTest::class.java.getResource(FIXTURE)?.readText() ?: error("missing $FIXTURE") }
        val decoded: GameState by lazy { SaveCodec.decodeRun(fixtureText) }
        /** What the app hands the engine: the decoded save after `Compatibility.admit`. */
        val admitted: GameState by lazy { decoded.admitted() }

        const val FIXTURE_V2 = "/saves/v2_forced_seed4242_day61.json"
        val fixtureTextV2: String by lazy { SaveFixtureTest::class.java.getResource(FIXTURE_V2)?.readText() ?: error("missing $FIXTURE_V2") }
        val decodedV2: GameState by lazy { SaveCodec.decodeRun(fixtureTextV2) }

        const val FIXTURE_V3 = "/saves/v3_forced_seed4242_day61.json"
        val fixtureTextV3: String by lazy { SaveFixtureTest::class.java.getResource(FIXTURE_V3)?.readText() ?: error("missing $FIXTURE_V3") }
        val decodedV3: GameState by lazy { SaveCodec.decodeRun(fixtureTextV3) }

        fun fixture(name: String): String = SaveFixtureTest::class.java.getResource("/saves/$name")?.readText() ?: error("missing $name")
    }

    @Test
    fun fixtureIsAVersionOneEnvelope() {
        assertTrue(fixtureText.startsWith("""{"schemaVersion":1,"payload":"""), fixtureText.take(40))
    }

    @Test
    fun decodesToTheCapturedMidRunState() {
        val s = decoded
        assertEquals(4242L, s.seed)
        assertEquals("era1-seed4242", s.runId.value)
        assertEquals(1, s.rulesVersion)
        assertEquals(61, s.day)
        assertEquals(Phase.PLANNING, s.phase)
        assertFalse(s.isEnded)
        assertEquals(235, s.weapons.size)
        assertEquals(615, s.events.size)
        assertTrue(s.heroes.isNotEmpty() && s.aliveHeroes().isNotEmpty())
        assertEquals(60, s.processedEndDayIds.size, "one processed End Day per played day")
        assertTrue(Invariants.check(s, engine.config).isEmpty(), Invariants.check(s, engine.config).toString())
        // The fixture exercises long weapon histories: one weapon carries 15 combat entries (VICTORY/SIEGE).
        assertEquals(15, s.weapons.values.maxOf { w -> w.history.count { it.kind in combatKinds } })
    }

    @Test
    fun decodedStateIsStableUnderTodaysCodec() {
        val again = SaveCodec.decodeRun(SaveCodec.encodeRun(decoded))
        assertEquals(decoded, again)
        assertEquals(decoded.rng, again.rng, "RNG state survives untouched")
        assertEquals(decoded.events, again.events, "event history survives untouched")
    }

    @Test
    fun decodedStateAcceptsAnEndDay() {
        val outcome = engine.handle(admitted, Command.EndDay(endDayId(admitted)))
        val accepted = assertIs<CommandOutcome.Accepted>(outcome)
        val next = accepted.state
        assertTrue(Invariants.check(next, engine.config).isEmpty())
        assertEquals(61, accepted.resolution?.day)
        if (!next.isEnded) assertEquals(62, next.day)
        assertTrue(next.events.any { it.day == 61 }, "the End Day emitted events")
    }

    /** An uncompacted v1 history (15 combat entries) is bounded on the first End Day; ownership entries survive verbatim. */
    @Test
    fun oldLongHistoriesAreCompactedOnTheFirstEndDay() {
        val cap = engine.config.weaponHistoryCap
        assertTrue(cap in 1..14, "the fixture's 15-entry weapon must exceed the cap; cap=$cap")
        val longest = decoded.weapons.values.maxBy { w -> w.history.count { it.kind in combatKinds } }
        val outcome = engine.handle(admitted, Command.EndDay(endDayId(admitted)))
        val next = assertIs<CommandOutcome.Accepted>(outcome).state
        assertTrue(next.weapons.values.all { w -> w.history.count { it.kind in combatKinds } <= cap })
        val after = next.weapon(longest.id)
        assertEquals(cap, after.history.count { it.kind in combatKinds })
        assertEquals(longest.history.filter { it.kind !in combatKinds }, after.history.filter { it.kind !in combatKinds && it.day < 61 })
        assertTrue(after.kills >= longest.kills && after.siegesDefended >= longest.siegesDefended && after.fame >= longest.fame, "counters are never reduced")
    }

    /**
     * The schema-2 anchor: a run written by the schema-2 codec (forced survival, seed 4242, BALANCED_FAIR, 60 End Days,
     * rules 2, balance 5). It is admitted to the installed rules and plays on.
     */
    @Test
    fun theV2FixtureIsAdmittedAndPlays() {
        assertTrue(fixtureTextV2.startsWith("""{"schemaVersion":2,"payload":"""), fixtureTextV2.take(40))
        val s = decodedV2
        assertEquals(4242L, s.seed)
        assertEquals(listOf(2, 5, 61), listOf(s.rulesVersion, s.balanceVersion, s.day))
        assertEquals(2, s.contentVersion)
        assertTrue(Invariants.check(s, engine.config, content = engine.content).isEmpty())
        assertEquals(s, SaveCodec.decodeRun(SaveCodec.encodeRun(s)))
        val next = assertIs<CommandOutcome.Accepted>(s.admitted().let { engine.handle(it, Command.EndDay(endDayId(it))) }).state
        assertTrue(next.isEnded || next.day == 62)
    }

    /**
     * The schema-3 anchor: the same recipe (forced survival, seed 4242, BALANCED_FAIR, 60 End Days) written with the
     * visit record. Its stored day carries the counter snapshots; its log carries one `SHOP_DAY` record per kept day.
     * Written under rules 2 and balance 6, before fair selection: its heroes read as newcomers nobody has kept waiting,
     * and after admission its next End Day seats them by the rules-3 draw.
     */
    @Test
    fun theV3FixtureCarriesTheVisitRecordIsAdmittedAndPlays() {
        assertTrue(fixtureTextV3.startsWith("""{"schemaVersion":3,"payload":"""), fixtureTextV3.take(40))
        val s = decodedV3
        assertEquals(listOf(2, 6, 61), listOf(s.rulesVersion, s.balanceVersion, s.day))
        assertEquals(2, s.contentVersion)
        assertTrue(Invariants.check(s, engine.config, content = engine.content).isEmpty())
        assertEquals(s, SaveCodec.decodeRun(SaveCodec.encodeRun(s)))
        val last = s.lastResolution!!
        assertEquals(listOf(1, 60), listOf(last.recordVersion, last.day))
        assertTrue(last.visits.isNotEmpty() && last.visits.all { it.customer != null } && last.shopWeapons.isNotEmpty() && last.ledger != null)
        assertTrue(last.visits.flatMap { it.considered }.all { c -> last.shopWeapons.any { it.weaponId == c.weaponId } })
        assertEquals((31..60).toList(), s.events.filter { it.type == EventType.SHOP_DAY }.map { it.day }, "one small record per day of the kept log")
        assertTrue(s.heroes.values.all { it.shopVisits == 0 && it.turnedAwayStreak == 0 && it.lastServedDay == null })
        assertIs<CommandOutcome.Rejected>(engine.handle(s, Command.EndDay(endDayId(s))), "a rules-2 run is not played before it is admitted")
        val out = assertIs<CommandOutcome.Accepted>(s.admitted().let { engine.handle(it, Command.EndDay(endDayId(it))) })
        val next = out.state
        assertTrue(next.isEnded || next.day == 62)
        assertEquals(emptyList(), Invariants.check(next, engine.config, engine.shelfSlots(next), engine.content))
        assertEquals(listOf(engine.config.version, GameEngine.RULES_VERSION), listOf(next.balanceVersion, next.rulesVersion))
        // The day it resolves is a rules-3 day: every browser served at the stocked shelf is remembered, everyone turned away has a streak.
        val r = out.resolution!!
        for (v in r.browsers.filter { it.reason != VisitReason.EMPTY_SHELVES }) assertEquals(listOf(1, 61), next.hero(v.heroId!!).let { listOf(it.shopVisits, it.lastServedDay) })
        for (id in r.turnedAway) assertEquals(1, next.hero(id).turnedAwayStreak)
        assertTrue(r.browsers.isNotEmpty(), "the fixture day has customers")
    }

    /**
     * Content 3 changed the name pools, not the heroes: every fixture of schema 1, 2 and 3 is admitted, accepts an End
     * Day, and each of its heroes still carries the name it was saved with, the names that left the pool included.
     */
    @Test
    fun oldSavesKeepTheirHeroesNames() {
        val retired = setOf("Ashwood", "Brackenridge", "Holloway", "Mossgrave", "Rooksbane")
        var carried = 0
        for (name in listOf("v1_forced_seed4242_day61.json", "v1_release060_active_seed4242_day61.json", "v2_forced_seed4242_day61.json", "v2_balance6_expert_seed4242_day33.json", "v3_forced_seed4242_day61.json")) {
            val s = SaveCodec.decodeRun(fixture(name))
            assertEquals(2, s.contentVersion, name)
            assertTrue(s.heroes.values.all { it.lineageId == null } && s.legacy.lineages.isEmpty(), "$name: a first-era run has no lineage to link")
            val admitted = s.admitted()
            assertEquals(engine.content.version, admitted.contentVersion)
            assertEquals(s.heroes, admitted.heroes, name)
            val next = assertIs<CommandOutcome.Accepted>(engine.handle(admitted, Command.EndDay(endDayId(admitted)))).state
            assertEquals(emptyList(), Invariants.check(next, engine.config, engine.shelfSlots(next), engine.content), name)
            for (h in s.heroes.values) assertEquals(h.fullName, next.hero(h.id).fullName, "$name ${h.id.value}")
            assertTrue(s.aliveHeroes().isNotEmpty() && next.aliveHeroes().any { it.id in s.heroes }, name)
            carried += s.heroes.values.count { it.surname in retired || it.name == "Nessa" }
            // A hero who arrives now is named from the new pools and shares no name with the living.
            for (h in next.heroes.values.filter { it.id !in s.heroes }) {
                assertTrue(h.name in engine.content.firstNames && h.surname in engine.content.surnames, "$name: ${h.fullName}")
                assertTrue(next.aliveHeroes().none { it.id != h.id && (it.name == h.name || it.surname == h.surname) }, "$name: ${h.fullName}")
            }
        }
        assertTrue(carried > 0, "the fixtures hold heroes whose names have left the pool")
    }

    /**
     * Faces (T3.3): a save of schema 1, 2 or 3 gets, stored on every hero, the face that hero has always shown; a schema-4
     * save written before the field existed shows the same face without it. Both are admitted, accept an End Day, keep
     * every face through it, and a hero who arrives that day takes a face of their class that fewest of the living wear.
     */
    @Test
    fun oldSavesKeepTheirHeroesFaces() {
        for (name in listOf("v1_forced_seed4242_day61.json", "v1_release060_active_seed4242_day61.json", "v2_forced_seed4242_day61.json", "v2_balance6_expert_seed4242_day33.json", "v3_forced_seed4242_day61.json")) {
            val migrated = SaveCodec.decodeRun(fixture(name))
            assertTrue(migrated.heroes.values.any { !it.isAlive }, name)
            for (h in migrated.heroes.values) assertEquals(Appearance.legacyKey(h), h.appearance, "$name ${h.id.value}")
            // The same run as a development build of schema 4 wrote it: no stored face. The step does not run; the face is the same.
            val unstamped = SaveCodec.decodeRun(SaveCodec.encodeRun(migrated.copy(heroes = migrated.heroes.mapValues { it.value.copy(appearance = null) })))
            assertTrue(unstamped.heroes.values.all { it.appearance == null }, name)
            for (s in listOf(migrated, unstamped)) {
                val admitted = s.admitted()
                assertEquals(s.heroes, admitted.heroes, name)
                val next = assertIs<CommandOutcome.Accepted>(engine.handle(admitted, Command.EndDay(endDayId(admitted)))).state
                assertEquals(emptyList(), Invariants.check(next, engine.config, engine.shelfSlots(next), engine.content), name)
                for (h in s.heroes.values) assertEquals(Appearance.keyOf(h), Appearance.keyOf(next.hero(h.id)), "$name ${h.id.value}")
                for (h in next.heroes.values.filter { it.id !in s.heroes }) assertTrue(h.appearance in engine.content.heroClass(h.classId).appearances, "$name ${h.id.value}")
                for (v in next.lastResolution!!.visits) v.customer?.let { c -> assertEquals(Appearance.keyOf(next.hero(c.heroId)), c.appearance, "$name ${c.heroId.value}") }
            }
        }
    }

    /**
     * What release 0.6.0 itself wrote (the codec of tag v0.6.0: schema 1, rules 1, balance not yet recorded): forced
     * survival, seed 4242, BALANCED_ACTIVE, 60 End Days, so it owns tools and has delivered commissions. It is migrated
     * on decode, admitted, and plays; the day it stored reads as a day without snapshots.
     */
    @Test
    fun aSaveWrittenByRelease060MigratesIsAdmittedAndPlays() {
        val text = fixture("v1_release060_active_seed4242_day61.json")
        assertTrue(text.startsWith("""{"schemaVersion":1,"payload":"""), text.take(40))
        val s = SaveCodec.decodeRun(text)
        assertEquals(listOf(1, 5, 61), listOf(s.rulesVersion, s.balanceVersion, s.day))
        assertTrue(s.tools.isNotEmpty() && s.commissions.values.any { it.status == CommissionStatus.COMPLETED })
        assertEquals(s, SaveCodec.decodeRun(SaveCodec.encodeRun(s)))
        val stored = s.lastResolution!!
        assertEquals(0, stored.recordVersion)
        assertTrue(stored.visits.isNotEmpty() && stored.visits.all { it.customer == null } && stored.ledger == null && stored.shopWeapons.isEmpty())
        val out = assertIs<CommandOutcome.Accepted>(s.admitted().let { engine.handle(it, Command.EndDay(endDayId(it))) })
        assertEquals(emptyList(), Invariants.check(out.state, engine.config, engine.shelfSlots(out.state), engine.content))
        assertEquals(listOf(61, 1), listOf(out.resolution!!.day, out.resolution!!.recordVersion))
    }

    /**
     * The stored shape of the M1 gate (schema 2, rules 2, balance 6; commit 0f529c5): forced survival, seed 4242,
     * EXPERT_ACTIVE, 32 End Days. The day was chosen because the save then holds everything T1.6-T1.9 added: a till
     * with a trade-in, field results, material and tool purchases with their cost, an ore merchant's flag and an open
     * commission.
     */
    @Test
    fun theSchemaTwoSaveOfTheM1GateMigratesAndPlays() {
        val text = fixture("v2_balance6_expert_seed4242_day33.json")
        assertTrue(text.startsWith("""{"schemaVersion":2,"payload":"""), text.take(40))
        val s = SaveCodec.decodeRun(text)
        assertEquals(listOf(2, 6, 33), listOf(s.rulesVersion, s.balanceVersion, s.day))
        assertEquals(emptyList(), Invariants.check(s, engine.config, engine.shelfSlots(s), engine.content))
        assertEquals(s, SaveCodec.decodeRun(SaveCodec.encodeRun(s)))
        val stored = s.lastResolution!!
        assertEquals(0, stored.recordVersion)
        assertTrue(stored.ledger!!.tradeInCredit > 0 && stored.field.isNotEmpty() && stored.visits.isNotEmpty() && stored.shopWeapons.isEmpty())
        for (type in listOf(EventType.MATERIAL_BOUGHT, EventType.TOOL_BOUGHT)) assertTrue(s.events.any { it.type == type && it.data.getValue("cost").toInt() > 0 }, "$type")
        assertTrue(s.worldFlags.keys.any { it.startsWith("ore_merchant:") })
        assertTrue(s.commissions.values.any { it.status == CommissionStatus.ACCEPTED })
        val out = assertIs<CommandOutcome.Accepted>(s.admitted().let { engine.handle(it, Command.EndDay(endDayId(it))) })
        assertEquals(emptyList(), Invariants.check(out.state, engine.config, engine.shelfSlots(out.state), engine.content))
        assertEquals(listOf(33, 1), listOf(out.resolution!!.day, out.resolution!!.recordVersion))
        assertEquals(1, out.state.events.count { it.type == EventType.SHOP_DAY })
    }
}
