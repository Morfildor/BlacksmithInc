package com.tinyblacksmith.core

import com.tinyblacksmith.core.battle.Power
import com.tinyblacksmith.core.config.BalanceConfig
import com.tinyblacksmith.core.content.AffixKind
import com.tinyblacksmith.core.content.LaunchContent
import com.tinyblacksmith.core.crafting.Forge
import com.tinyblacksmith.core.crafting.SignatureCatalog
import com.tinyblacksmith.core.engine.Command
import com.tinyblacksmith.core.engine.CommandOutcome
import com.tinyblacksmith.core.engine.GameEngine
import com.tinyblacksmith.core.engine.Invariants
import com.tinyblacksmith.core.engine.ResolutionContext
import com.tinyblacksmith.core.engine.Technique
import com.tinyblacksmith.core.engine.WorldEvents
import com.tinyblacksmith.core.legacy.Legacy
import com.tinyblacksmith.core.legacy.LegacyOutcome
import com.tinyblacksmith.core.model.*
import com.tinyblacksmith.core.persistence.SaveCodec
import com.tinyblacksmith.core.shopday.Lines
import com.tinyblacksmith.core.sim.BuyRule
import com.tinyblacksmith.core.sim.Policy
import com.tinyblacksmith.core.sim.SimulationDriver
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** G09, X06, E5: a blade's story survives eras; what returns is what it was, dormant until honed; one blade is one board entry. */
class ArtifactFidelityTest {
    private val engine = TestSupport.engine
    private val content = engine.content
    private val config = engine.config
    /** The signature roll always takes: a forge that qualifies is the signature. */
    private val sure = GameEngine(content, BalanceConfig.DEFAULT.copy(signatureBaseChance = 1.0, signatureMaxChance = 1.0))
    private val dawnbrand = SignatureCatalog.byId.getValue("dawnbrand")
    private val affixNames = content.affixes.map { it.name }
    private val signatureNames = SignatureCatalog.all.map { it.name }.toSet()

    private fun GameState.accepted(cmd: Command, e: GameEngine = engine) = e.handle(this, cmd) as CommandOutcome.Accepted
    private fun GameState.fallen(e: GameEngine = engine): GameState {
        var s = copy(town = town.copy(integrity = 1))
        while (!s.isEnded) s = s.accepted(Command.EndDay(TestSupport.endDayId(s)), e).state
        return s
    }
    private fun claim(legacy: LegacyProfile, ended: GameState) = (engine.claimLegacy(legacy, engine.closeRun(ended)) as LegacyOutcome.Updated).legacy

    /** The blade [entry] comes back as, in a new run on [legacy]. */
    private fun returned(legacy: LegacyProfile, seed: Long = 3): Pair<GameState, Weapon> {
        val ctx = ResolutionContext(engine.newRun(legacy, seed), content, config)
        WorldEvents.fire(ctx, WorldEvents.byId("famous_blade"))
        val s = ctx.toState()
        assertEquals(emptyList(), Invariants.check(s, config))
        return s to s.weapons.values.single { it.legendKey != null }
    }

    /** A Dawnbrand forged in era 1 with a flaw and fame, remembered on the board. */
    private val remembered: LegacyProfile by lazy {
        var s = sure.newRun(LegacyProfile(), 11).copy(materials = content.materials.associate { it.id to 50 }, energy = 1000)
        var blade: Weapon? = null
        while (blade == null) {
            val out = s.accepted(SignatureCatalog.recipe(dawnbrand), sure)
            s = out.state
            blade = s.weapon(out.forgedWeaponId!!).takeIf { it.signatureId == dawnbrand.id }
        }
        val flaw = content.affixes.first { it.kind == AffixKind.FLAW }.id
        s = s.copy(weapons = mapOf(blade.id to blade.copy(flaws = listOf(flaw), fame = 9, kills = 12, title = "Bane of the Ashclaw Raiders")))
        claim(LegacyProfile(), s.fallen(sure))
    }

    private fun promisesOnlyWhatItHas(w: Weapon) {
        val has = (w.affixes + w.dormantAffixes + w.flaws).map { content.affix(it).name }
        for (n in affixNames) if (Regex("\\b${Regex.escape(n)}\\b").containsMatchIn(w.name)) assertTrue(n in has, "${w.name} promises $n; it has $has")
        if (w.name in signatureNames) assertEquals(w.name, w.signatureId?.let { SignatureCatalog.byId.getValue(it).name }, "${w.name} is a signature's name")
    }

    @Test
    fun aReturnedLegendKeepsAffixesFlawsCatalystAndSignature() {
        val entry = remembered.legendBoard.single()
        assertEquals(dawnbrand.id, entry.signatureId)
        assertTrue(entry.affixes.containsAll(dawnbrand.grantedAffixes) && entry.flaws.size == 1 && entry.catalystId == dawnbrand.catalystId, "$entry")
        assertTrue(entry.weaponKey.startsWith("era1-w"), entry.weaponKey)
        assertEquals(entry, SaveCodec.decodeLegacy(SaveCodec.encodeLegacy(remembered)).legendBoard.single(), "and the save keeps it")

        val (_, blade) = returned(remembered)
        assertEquals(listOf(dawnbrand.name, dawnbrand.id, entry.flaws, entry.catalystId, entry.title, entry.fame, entry.kills),
            listOf(blade.name, blade.signatureId, blade.flaws, blade.catalystId, blade.title, blade.fame, blade.kills))
        assertEquals(entry.affixes, blade.dormantAffixes, "its beneficial affixes are all there, asleep")
        assertEquals(emptyList(), blade.affixes)
        assertEquals(listOf(entry.familyId, entry.coreId, entry.augmentId, entry.element), listOf(blade.familyId, blade.coreId, blade.augmentId, blade.element))
        assertEquals(entry.key, blade.legendKey)
        assertTrue(blade.power < entry.power && blade.quality < entry.quality, "never whole (GDD 7)")
    }

    @Test
    fun theOwnerLineIncludesHeirsAndCommissionPatrons() {
        val forged = engine.newRun(LegacyProfile(), 5).copy(materials = content.materials.associate { it.id to 50 }).accepted(TestSupport.quickSword())
        val (buyer, heir, patron, last) = forged.state.aliveHeroes()
        fun entry(day: Int, kind: String, text: String, vararg heroes: Hero) = HistoryEntry(1, day, kind, text, heroes.map { it.id.value })
        val w = forged.state.weapon(forged.forgedWeaponId!!)
        val storied = w.copy(
            fame = 6, location = WeaponLocation.Owned(last.id, true),
            history = w.history + listOf(
                entry(1, "SOLD", "Sold to ${buyer.fullName} for 60 gold.", buyer), entry(1, "EQUIPPED", "Wielded by ${buyer.fullName}.", buyer),
                entry(2, "VICTORY", "${buyer.fullName} routed scouts.", buyer), entry(3, "VICTORY", "${buyer.fullName} routed a warband.", buyer),
                entry(4, "INHERITED", "Passed to ${heir.fullName} by the guild.", heir, buyer),
                entry(5, "TRADED_IN", "Traded in by ${heir.fullName}.", heir),
                entry(6, "COMMISSION", "Delivered to ${patron.fullName} on commission.", patron),
                entry(7, "SIEGE", "${patron.fullName} held the wall.", patron),
                entry(8, "RESOLD", "Sold to ${last.fullName} by a travelling merchant for 90 gold.", last),
            ),
        )
        val ended = forged.state.copy(weapons = mapOf(w.id to storied)).fallen()
        val legend = engine.closeRun(ended).legends.single()
        assertEquals(listOf(buyer, heir, patron, last).map { it.fullName }, legend.owners, "buyer, heir, commission patron and the merchant's customer, in order")
        // The story is the stored entries, minus routine fights after the first; nothing is composed and no hero ID crosses eras.
        assertEquals(listOf("FORGED", "SOLD", "VICTORY", "INHERITED", "TRADED_IN", "COMMISSION", "SIEGE", "RESOLD"), legend.ownerLine.map { it.kind })
        assertTrue(legend.ownerLine.all { line -> line.subjectIds.isEmpty() && ended.weapon(w.id).history.any { it.text == line.text && it.day == line.day } })
        assertEquals(Legacy.story(ended.weapon(w.id)).map { it.text }, legend.ownerLine.map { it.text })
        val told = Lines.story(ended.weapon(w.id), 1)
        assertEquals("Day 4: Passed to ${heir.fullName} by the guild.", told[3])
        assertTrue(Lines.legend(legend, content, 2).let { l -> "Carried by" in l[2] && last.fullName in l[2] && l.last() == "Era 1, day 8: Sold to ${last.fullName} by a travelling merchant for 90 gold." })
    }

    @Test
    fun aReturnedNameNeverPromisesAnAffixTheBladeLacks() {
        val flaming = LaunchContent.FLAMING
        val keen = LaunchContent.KEEN
        fun old(name: String) = LegendEntry(1, name, "Bane of the Ashclaw Raiders", 12, 9, listOf("Mira Vance"), LaunchContent.SWORD, LaunchContent.IRON, LaunchContent.EMBER_RESIN, 80, 30)
        val cases = listOf(
            // Recorded with its make: the name is the one it had, and what the name says is in it, asleep.
            old("Flaming Iron Sword").copy(affixes = listOf(flaming, keen), weaponKey = "era1-w7") to "Flaming Iron Sword",
            // From before the make was recorded: called by what is known of it.
            old("Flaming Keen Iron Sword") to "Iron Sword",
            old("Dawnbrand") to "Iron Sword",
            // A signature id that does not fit the recorded recipe is not kept, and neither is its name.
            old("Winterwake").copy(signatureId = "winterwake", weaponKey = "era1-w8") to "Iron Sword",
        )
        for ((entry, name) in cases) {
            val (_, blade) = returned(LegacyProfile(legendBoard = listOf(entry)))
            assertEquals(name, blade.name, "$entry")
            promisesOnlyWhatItHas(blade)
        }
        promisesOnlyWhatItHas(returned(remembered).second)
        // And the check itself has teeth.
        val lying = returned(LegacyProfile(legendBoard = listOf(old("Iron Sword")))).second.copy(name = "Keen Iron Sword")
        assertTrue(runCatching { promisesOnlyWhatItHas(lying) }.isFailure)
    }

    @Test
    fun aLegendCannotReEnterTheBoardOwnerless() {
        val entry = remembered.legendBoard.single()
        val other = entry.copy(weaponName = "Old Ember", weaponKey = "era1-w99", signatureId = null, affixes = emptyList())
        val legacy = remembered.copy(legendBoard = listOf(entry, other))
        val event = WorldEvents.byId("famous_blade")

        // Returned and never carried: the run ends, nothing of it goes onto the board, and the old entry stands alone.
        val ctx = ResolutionContext(engine.newRun(legacy, 3), content, config)
        assertTrue(WorldEvents.canFire(ctx, event))
        WorldEvents.fire(ctx, event)
        val unowned = ctx.toState()
        val blade = unowned.weapons.values.single()
        val picked = legacy.legendBoard.single { it.key == blade.legendKey }
        assertTrue(blade.fame >= config.legendFameThreshold, "it would qualify by fame alone")
        val quiet = unowned.fallen()
        assertTrue(engine.closeRun(quiet).legends.none { it.key == picked.key })
        assertEquals(legacy.legendBoard, claim(legacy, quiet).legendBoard.filter { it.key in setOf(entry.key, other.key) })
        // A blade already back in this era is not picked again: only the other entry can still return.
        val two = ResolutionContext(unowned.copy(eventCounters = emptyMap()), content, config)
        WorldEvents.fire(two, event)
        assertEquals(setOf(entry.key, other.key), two.weapons.values.mapNotNull { it.legendKey }.toSet())
        assertFalse(WorldEvents.canFire(ResolutionContext(two.toState().copy(eventCounters = emptyMap()), content, config), event), "both are in town; none is left to return")

        // Carried again: it goes back on the board as the same entry, with the new hand added to the old ones.
        val buyer = unowned.aliveHeroes().first()
        val carried = unowned.copy(weapons = mapOf(blade.id to blade.copy(
            location = WeaponLocation.Owned(buyer.id, true),
            history = blade.history + HistoryEntry(unowned.era, 2, "SOLD", "Sold to ${buyer.fullName} for 90 gold.", listOf(buyer.id.value)),
        ))).fallen()
        val again = engine.closeRun(carried).legends.single { it.key == picked.key }
        assertEquals(picked.owners + buyer.fullName, again.owners)
        assertEquals(unowned.era, again.era)
        assertTrue(again.ownerLine.map { it.kind }.containsAll(listOf("RETURNED", "SOLD")) && again.ownerLine.size > picked.ownerLine.size)
        val board = claim(legacy, carried).legendBoard
        assertEquals(1, board.count { it.key == picked.key }, "one blade, one entry")
        assertEquals(again, board.single { it.key == picked.key })
        assertEquals(board.size, board.map { it.key }.toSet().size)
    }

    @Test
    fun threeNaturalErasKeepOneBladesStoryTruthful() {
        // Three eras of an active smith on one account; at the start of eras 2 and 3 a blade of the board returns (the event itself, fired on day 1).
        val driver = SimulationDriver(engine)
        var returns = 0
        var reEntered = 0
        var woken = 0
        for (seed in 1L..12L) {
            var legacy = LegacyProfile()
            for (era in 1..3) {
                var start: GameState? = null
                var key: String? = null
                if (legacy.legendBoard.isNotEmpty()) {
                    val ctx = ResolutionContext(engine.newRun(legacy, seed + era * 1000), content, config)
                    WorldEvents.fire(ctx, WorldEvents.byId("famous_blade"))
                    start = ctx.toState()
                    val blade = start.weapons.values.single()
                    val entry = legacy.legendBoard.single { it.key == blade.legendKey }
                    key = entry.key
                    returns++
                    promisesOnlyWhatItHas(blade)
                    assertEquals(entry.affixes.filter { content.affix(it).kind == AffixKind.BENEFICIAL }, blade.dormantAffixes)
                    assertEquals(entry.flaws to entry.signatureId, blade.flaws to blade.signatureId)
                    assertEquals(entry.ownerLine + blade.history.last(), blade.history, "the story so far, then the return")
                    assertEquals(blade.history.filter { it.kind != "EQUIPPED" }.size, Lines.story(blade, start.era).size)
                }
                val (_, ended) = driver.playRun(legacy, seed + era * 1000, Policy.BALANCED_ACTIVE, from = start)
                assertTrue(ended.isEnded)
                if (key != null) {
                    val blade = ended.weapons.values.firstOrNull { it.legendKey == key }
                    if (blade != null) {
                        promisesOnlyWhatItHas(blade)
                        if (blade.history.any { it.kind == "AWAKENED" }) { woken++; assertTrue(blade.dormantAffixes.isEmpty()) }
                    }
                }
                val closed = engine.closeRun(ended)
                // Every line of every remembered story is an entry of that blade's history.
                for (l in closed.legends) {
                    val w = ended.weapons.values.single { (it.legendKey ?: "era${it.forgedEra}-${it.id.value}") == l.weaponKey }
                    assertTrue(l.ownerLine.all { line -> w.history.any { it.era == line.era && it.day == line.day && it.kind == line.kind && it.text == line.text } }, "seed $seed era $era ${l.weaponName}")
                    assertEquals(l.ownerLine.sortedBy { it.era }, l.ownerLine, "told in order")
                    if (l.key == key) { reEntered++; assertTrue(l.owners.isNotEmpty() && l.ownerLine.any { it.kind == "RETURNED" }) }
                    if (w.signatureId == null) assertFalse(l.weaponName in signatureNames)
                }
                legacy = BuyRule.CHEAPEST.spend(engine, (engine.claimLegacy(legacy, closed) as LegacyOutcome.Updated).legacy)
                assertEquals(legacy.legendBoard.size, legacy.legendBoard.map { it.key }.toSet().size, "seed $seed era $era: one entry per blade")
                assertTrue(legacy.legendBoard.all { it.weaponKey.isNotEmpty() && !it.lostToTime })
            }
        }
        assertTrue(returns >= 20, "blades returned ($returns)")
        assertTrue(woken > 0, "an active smith hones a returned legend awake ($woken)")
        assertTrue(reEntered > 0, "and some were carried again and remembered as the same blade ($reEntered)")
    }

    @Test
    fun entriesFromOlderProfilesRenderAsLostToTime() {
        // A legacy document as 0.6.0 wrote it: an entry has no affixes, flaws, catalyst, signature, story or key.
        val text = """{"schemaVersion":1,"payload":"{\"legendBoard\":[{\"era\":1,\"weaponName\":\"Flaming Keen Iron Sword\",\"title\":\"Bane of the Ashclaw Raiders\",\"kills\":12,\"fame\":9,\"owners\":[\"Mira Vance\"],\"familyId\":\"sword\",\"coreId\":\"iron\",\"augmentId\":\"ember_resin\",\"quality\":80,\"power\":30,\"element\":\"FIRE\"}]}"}"""
        val legacy = SaveCodec.decodeLegacy(text)
        val entry = legacy.legendBoard.single()
        assertTrue(entry.lostToTime)
        assertEquals("era1:Flaming Keen Iron Sword:Bane of the Ashclaw Raiders", entry.key)
        val lines = Lines.legend(entry, content, 2)
        assertEquals(listOf("Flaming Keen Iron Sword, Bane of the Ashclaw Raiders. Era 1; 12 victories; fame 9.", "Its properties are lost to time.", "Carried by Mira Vance."), lines)
        // What returns is honest about it: no affixes awake or asleep, no flaws, a plain name; its fame and title are all that is known.
        val (_, blade) = returned(legacy)
        assertEquals(listOf("Iron Sword", emptyList<AffixId>(), emptyList<AffixId>(), emptyList<AffixId>(), null, 9, "Bane of the Ashclaw Raiders"),
            listOf(blade.name, blade.affixes, blade.dormantAffixes, blade.flaws, blade.signatureId, blade.fame, blade.title))
        assertNull(Lines.dormant(blade.dormantAffixes, content))
        // An entry of today says what the blade was instead.
        val now = Lines.legend(remembered.legendBoard.single(), content, 2)
        assertTrue("lost to time" !in now.joinToString(" ") && "A signature blade: Dawnbrand" in now[1] && "flawed:" in now[1] && "forged with Binding Salt" in now[1], now[1])
        assertTrue(now.any { it.startsWith("Era 1, day 1: Forged from Iron and Ember Resin") }, "$now")
    }

    @Test
    fun aReturnedLegendsAffixesAreDormantUntilHoned() {
        val entry = remembered.legendBoard.single()
        val (s0, asleep) = returned(remembered)
        val hero = s0.aliveHeroes().first()
        assertEquals(1.0, Power.affixAttackMultiplier(asleep.copy(flaws = emptyList()), content), 1e-12, "asleep, they add nothing to a strike")
        assertEquals("Dormant: ${entry.affixes.joinToString(", ") { content.affix(it).name }}. Hone it once to wake them.", Lines.dormant(asleep.dormantAffixes, content))
        assertEquals(asleep.dormantAffixes, WeaponSnapshot.of(asleep).dormantAffixes, "the counter's snapshot carries the marker")
        assertTrue(Power.attackPower(hero, asleep, content.factions.first(), content, config) > 0)

        val s = s0.copy(materials = s0.materials + (asleep.coreId to 1))
        val out = s.accepted(Command.Hone(asleep.id))
        val awake = out.state.weapon(asleep.id)
        assertEquals(entry.affixes, awake.affixes)
        assertEquals(emptyList(), awake.dormantAffixes)
        val quality = minOf(100, asleep.quality + config.honeQualityBonus)
        assertEquals(asleep.power + entry.affixes.sumOf { content.affix(it).power } + quality / config.powerPerQualityDivisor - asleep.quality / config.powerPerQualityDivisor, awake.power, "the power they carry wakes with them")
        assertTrue(Power.affixAttackMultiplier(awake.copy(flaws = emptyList()), content) >= 1.0)
        assertEquals("AWAKENED", awake.history.last().kind)
        val honed = out.events.single { it.type == EventType.WEAPON_HONED }
        assertEquals(entry.affixes.joinToString(",") { it.value }, honed.data["woke"])
        assertTrue("woke" in honed.text)
        assertNull(Lines.dormant(awake.dormantAffixes, content))
        assertTrue(Lines.story(awake, out.state.era).last().endsWith("Woke under the hone: ${entry.affixes.joinToString(", ") { content.affix(it).name }}."))
        // Waking happens once: a later hone of the worn blade restores its edge and nothing else.
        val worn = out.state.copy(weapons = mapOf(awake.id to awake.copy(condition = 40)), materials = out.state.materials + (awake.coreId to 1), energy = 10)
        val again = worn.accepted(Command.Hone(awake.id))
        assertEquals(awake.affixes to awake.power, again.state.weapon(awake.id).let { it.affixes to it.power })
        assertTrue(again.events.single { it.type == EventType.WEAPON_HONED }.data["woke"] == null)
        // A blade forged in the run has nothing dormant, ever.
        assertTrue(engine.newRun(LegacyProfile(), 1).copy(materials = content.materials.associate { it.id to 9 }).accepted(TestSupport.quickSword()).let { it.state.weapon(it.forgedWeaponId!!).dormantAffixes.isEmpty() })
    }

    @Test
    fun aNameCarriesAtMostOnePrefix() {
        var many = 0
        for (seed in 1L..40L) {
            var s = engine.newRun(LegacyProfile(), seed).copy(materials = content.materials.associate { it.id to 50 }, energy = 1000)
            for ((family, core, augment) in listOf(Triple(LaunchContent.SWORD, LaunchContent.STARSTEEL, LaunchContent.EMBER_RESIN), Triple(LaunchContent.AXE, LaunchContent.MOONSTEEL, LaunchContent.FROST_BLOOM), Triple(LaunchContent.STAFF, LaunchContent.IRON, LaunchContent.VERDANT_SAP))) {
                val out = s.accepted(Command.Forge(ForgeMode.ADVANCED, family, core, augment, null, Risk.RECKLESS, Technique.ETCH))
                s = out.state
                val w = s.weapon(out.forgedWeaponId!!)
                if (w.signatureId != null) { assertEquals(SignatureCatalog.byId.getValue(w.signatureId!!).name, w.name); continue }
                if (w.affixes.size >= 2) many++
                val base = "${content.material(core).name} ${content.family(family).name}"
                assertEquals(listOfNotNull(w.affixes.firstOrNull()?.let { content.affix(it).name }, base).joinToString(" "), w.name)
                assertTrue(affixNames.count { Regex("\\b${Regex.escape(it)}\\b").containsMatchIn(w.name) } <= 1, w.name)
                assertEquals(w.name, Forge.weaponName(content, family, core, w.affixes))
                // An earned title takes the prefix's place and is written into the blade's story; only the first title sticks.
                val ctx = ResolutionContext(s, content, config)
                Forge.entitle(ctx, w.id, "Slayer of the Cinder Matriarch")
                Forge.entitle(ctx, w.id, "Bane of the Ashclaw Raiders")
                val titled = ctx.weapon(w.id)
                assertEquals(base to "Slayer of the Cinder Matriarch", titled.name to titled.title)
                assertEquals(1, titled.history.count { it.kind == "TITLED" })
                assertEquals(w.affixes, titled.affixes, "the affixes stay; only the name changes")
            }
        }
        assertTrue(many > 20, "blades with several affixes were forged ($many)")
        assertEquals("Dawnbrand", Forge.weaponName(content, LaunchContent.SWORD, LaunchContent.IRON, listOf(LaunchContent.FLAMING, LaunchContent.KEEN), "dawnbrand"))
    }
}
