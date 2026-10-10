package com.tinyblacksmith.core

import com.tinyblacksmith.core.TestSupport.endDay
import com.tinyblacksmith.core.TestSupport.endDayAccepted
import com.tinyblacksmith.core.TestSupport.endDayId
import com.tinyblacksmith.core.TestSupport.engine
import com.tinyblacksmith.core.TestSupport.forgeAccepted
import com.tinyblacksmith.core.TestSupport.quickSword
import com.tinyblacksmith.core.TestSupport.run
import com.tinyblacksmith.core.TestSupport.withMaterials
import com.tinyblacksmith.core.battle.Battle
import com.tinyblacksmith.core.content.LaunchContent
import com.tinyblacksmith.core.content.SliceContent
import com.tinyblacksmith.core.crafting.Journal as JournalRules
import com.tinyblacksmith.core.crafting.SignatureCatalog
import com.tinyblacksmith.core.engine.Command
import com.tinyblacksmith.core.engine.Encounters
import com.tinyblacksmith.core.engine.GameEngine
import com.tinyblacksmith.core.engine.Invariants
import com.tinyblacksmith.core.engine.ResolutionContext
import com.tinyblacksmith.core.engine.WorldEvents
import com.tinyblacksmith.core.engine.acceptedOrThrow
import com.tinyblacksmith.core.model.*
import com.tinyblacksmith.core.sim.Policy
import com.tinyblacksmith.core.sim.SimulationDriver
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class WorldEventsAndGenerationsTest {
    /** A catalogue without morning visitors: the three events that come as visitors in the launch catalogue (collector, wandering master, merchant festival) still fire by themselves here, as in rules 3. */
    private fun ctx(state: GameState) = ResolutionContext(state, engine.content.copy(encounters = emptyList()), engine.config)
    private fun eligible(state: GameState, id: String) = WorldEvents.canFire(ctx(state), WorldEvents.byId(id))

    /** Applies one event directly (bypassing the daily roll) and returns the new state. */
    private fun fire(state: GameState, id: String): GameState {
        val c = ctx(state)
        WorldEvents.fire(c, WorldEvents.byId(id))
        val s = c.toState()
        assertEquals(emptyList(), Invariants.check(s, engine.config), "after $id")
        assertEquals(1, s.events.count { it.day == state.day && it.type == EventType.WORLD_EVENT && it.data["event"] == id })
        return s
    }

    private val legend = LegendEntry(
        era = 1, weaponName = "Old Ember", title = "Bane of the Ashclaw Raiders", kills = 6, fame = 9, owners = listOf("Mira Vance"),
        familyId = SliceContent.SWORD, coreId = SliceContent.IRON, augmentId = SliceContent.EMBER_RESIN, quality = 80, power = 30,
    )
    private val lineageA = LineageAnchor(1, "Mira Vance", "Vance", engine.content.classes.first().id, 5, "died on day 9", id = "era1-h3")
    private val lineageB = LineageAnchor(1, "Bram Ferris", "Ferris", engine.content.classes.first().id, 4, "survived the fall of the forge", id = "era2-h5")
    private val storiedLegacy = LegacyProfile(legendBoard = listOf(legend), lineages = listOf(lineageA, lineageB), eras = listOf(EraSummary(1, 12, 8, "The forge fell.")))

    @Test
    fun legacyEventsAreIneligibleOnFreshLegacyAndFireWithHistory() {
        val fresh = engine.newRun(LegacyProfile(), 1)
        for (id in listOf("famous_blade", "descendant", "guild_banner")) assertFalse(eligible(fresh, id), id)

        val s0 = engine.newRun(storiedLegacy, 1)
        for (id in listOf("famous_blade", "descendant", "guild_banner")) assertTrue(eligible(s0, id), id)

        val s1 = fire(s0, "famous_blade")
        val blade = s1.weapons.values.single()
        assertEquals("Old Ember", blade.name)
        assertEquals(legend.title, blade.title)
        assertTrue(blade.quality < legend.quality && blade.power < legend.power, "returned blade is dormant/damaged")
        assertTrue(blade.isInStorage)
        assertEquals(1, blade.history.count { it.kind == "RETURNED" })
        assertTrue(s1.events.any { it.type == EventType.ARTIFACT_RETURNED && blade.id.value in it.subjectIds })
        assertEquals(1, s1.eventCounters["famous_blade"])
        assertFalse(eligible(s1, "famous_blade"), "maxPerRun 1")

        // newRun already seeded a descendant of the last lineage; the event brings the other one.
        val s2 = fire(s1, "descendant")
        assertNotNull(s2.heroes.values.firstOrNull { it.lineageId == lineageA.id })
        assertFalse(eligible(s2, "descendant"), "no unclaimed lineage remains")
    }

    /** X18: the extra stock used to be added at night and overwritten by the morning restock, so it was never on sale. */
    @Test
    fun theOreMerchantsStockIsOnSaleTheNextMorning() {
        var checked = 0
        for (seed in 1L..8L) {
            val s = engine.newRun(LegacyProfile(), seed)
            val merchant = fire(s, "ore_merchant")
            val material = MaterialId(merchant.events.last { it.data["event"] == "ore_merchant" }.data.getValue("materialId"))
            assertEquals(mapOf(WorldEvents.FLAG_ORE_MERCHANT + material.value to s.day + 1), merchant.worldFlags)
            val morning = merchant.endDay()
            if (morning.worldFlags[WorldEvents.FLAG_CARAVAN_DELAYED] == morning.day) continue  // no restock to add to that morning
            val daily = engine.content.material(material).dailySupplierStock!!
            val stock = daily + engine.config.worldEvents.oreMerchantStock
            assertEquals(stock, morning.supplierStock[material], "seed $seed")
            assertEquals(s.supplierStock - material, morning.supplierStock - material, "only the merchant's material has more")
            val rich = morning.copy(gold = 100_000)
            assertEquals(0, rich.run(Command.BuyMaterial(material, stock)).supplierStock[material], "every unit can be bought")
            assertTrue(engine.handle(rich, Command.BuyMaterial(material, stock + 1)) is com.tinyblacksmith.core.engine.CommandOutcome.Rejected)
            val after = morning.endDay()
            if (after.worldFlags.keys.none { it.startsWith(WorldEvents.FLAG_ORE_MERCHANT) } && after.worldFlags[WorldEvents.FLAG_CARAVAN_DELAYED] != after.day)
                assertEquals(daily, after.supplierStock[material], "one morning only")
            assertTrue(after.worldFlags.none { it.key.startsWith(WorldEvents.FLAG_ORE_MERCHANT) && it.value < after.day }, "the spent flag is dropped")
            checked++
        }
        assertTrue(checked >= 5, "mornings checked: $checked")
    }

    @Test
    fun supplyAndTownEventsApplyTheirEffects() {
        val s = engine.newRun(LegacyProfile(), 3)
        for (id in listOf("ore_merchant", "caravan_delayed", "merchant_festival", "abandoned_mine", "noble_commission", "new_adventurers",
            "veteran_returns", "raider_encampment", "restless_graves", "volcanic_tremors", "successful_patrol", "border_ambush", "ancient_notes", "mysterious_alloy", "forgotten_shrine", "weapon_fragment")) {
            assertTrue(eligible(s, id), "$id eligible on a fresh run")
        }
        for (id in listOf("heroic_inheritance", "wandering_master", "collector", "ballad")) {
            assertFalse(eligible(s, id), "$id ineligible on a fresh run")
        }

        val merchant = fire(s, "ore_merchant")
        assertEquals(1, merchant.materials.values.sum() - s.materials.values.sum())
        assertEquals(s.supplierStock, merchant.supplierStock, "the extra stock is tomorrow morning's")

        val mine = fire(s, "abandoned_mine")
        assertEquals(engine.config.abandonedMineMaterials, mine.materials.values.sum() - s.materials.values.sum())

        val shrine = fire(s, "forgotten_shrine")
        val catalysts = engine.content.materials(com.tinyblacksmith.core.content.MaterialCategory.CATALYST).map { it.id }
        assertEquals(2, catalysts.sumOf { (shrine.materials[it] ?: 0) - (s.materials[it] ?: 0) })

        val camp = fire(s, "raider_encampment")
        assertEquals(engine.config.encampmentPressure, camp.factions.getValue(SliceContent.ASHCLAW).pressure - s.factions.getValue(SliceContent.ASHCLAW).pressure)

        val patrol = fire(s, "successful_patrol")
        val pressing = s.factions.values.maxWith(compareBy<FactionState> { it.pressure }.thenBy { it.id.value }).id
        assertEquals(-engine.config.successfulPatrolPressureDrop, patrol.factions.getValue(pressing).pressure - s.factions.getValue(pressing).pressure)
        assertEquals(engine.config.successfulPatrolMilitia, patrol.town.militia - s.town.militia)

        val ambush = fire(s, "border_ambush")
        assertEquals(1, ambush.heroes.values.count { it.health == 100 - engine.config.ambushDamage })

        val notes = fire(s, "ancient_notes")
        assertEquals(1, notes.legacy.journal.interactions.values.count { it == KnowledgeState.OBSERVED })
        assertTrue(eligible(notes, "wandering_master"), "an observed interaction can now be explained")
        val master = fire(notes, "wandering_master")
        assertEquals(1, master.legacy.journal.interactions.values.count { it == KnowledgeState.UNDERSTOOD })
        assertEquals(s.discoveriesThisRun + 1, master.discoveriesThisRun)

        val alloy = fire(s, "mysterious_alloy")
        val key = alloy.events.last { it.type == EventType.WORLD_EVENT }.data.getValue("key")
        assertTrue(key.startsWith("ca:"))
        assertEquals(KnowledgeState.OBSERVED, alloy.legacy.journal.state(key))
        assertEquals(2, alloy.materials.values.sum() - s.materials.values.sum())

        val noble = fire(s, "noble_commission")
        val c = noble.commissions.values.single()
        assertEquals(CommissionStatus.OFFERED, c.status)
        assertTrue(c.minQuality == engine.config.epicMin && c.reward > engine.config.commissionRewardBase * 2, "a noble asks for the superb floor")
        assertFalse(eligible(noble, "noble_commission"), "no second commission while one is open")

        val arrivals = fire(s, "new_adventurers")
        assertEquals(engine.config.newAdventurerCount, arrivals.aliveHeroes().size - s.aliveHeroes().size)
        val veteran = fire(s, "veteran_returns")
        val vet = veteran.heroes.values.single { it.id !in s.heroes }
        assertEquals(engine.config.veteranLevel, vet.level)

        val ballad = fire(s.copy(milestones = setOf("FIRST_SALE")), "ballad")
        assertEquals(engine.config.balladReputation, ballad.reputation - s.reputation)
    }

    @Test
    fun strangeWeaponFragmentRevealsASignatureRecipeDeterministically() {
        val s = engine.newRun(LegacyProfile(), 5)
        val after = fire(s, "weapon_fragment")
        val record = after.events.last { it.type == EventType.WORLD_EVENT }
        val key = record.data.getValue("key")
        val def = SignatureCatalog.byId.getValue(key.removePrefix("sig:"))
        assertTrue(def.familyId in engine.content.familyById, "only signatures the active catalog can forge")
        assertEquals(KnowledgeState.OBSERVED, after.legacy.journal.state(key))
        assertEquals(1, after.legacy.journal.interactions.size, "one clue per fragment")
        assertEquals(1, after.materials.getValue(def.coreId) - (s.materials[def.coreId] ?: 0))
        assertEquals(1, after.materials.getValue(def.augmentId) - (s.materials[def.augmentId] ?: 0))
        assertEquals(s.discoveriesThisRun, after.discoveriesThisRun, "a clue is not a discovery")
        val subject = JournalRules.subjectName(engine.content, key)
        assertTrue(record.text.contains(subject), record.text)
        val clue = after.events.single { it.type == EventType.DISCOVERY && it.data["key"] == key }
        assertTrue(clue.text.contains("something more") && !clue.text.contains('%'), clue.text)
        val hint = JournalRules.hint(after.legacy.journal, engine.content, key)
        assertTrue(hint != "Unknown" && !hint.contains('%') && !hint.contains(def.name), hint)

        // Same state, same pick.
        val again = fire(s, "weapon_fragment")
        assertEquals(after.events, again.events)
        assertEquals(after.legacy, again.legacy)

        // Nothing left to reveal: ineligible.
        val allKnown = LegacyProfile(journal = Journal(interactions = SignatureCatalog.all.associate { it.journalKey to KnowledgeState.OBSERVED }))
        assertFalse(eligible(engine.newRun(allKnown, 5), "weapon_fragment"))

        // Under launch content the pool covers all six families.
        val launch = GameEngine(content = LaunchContent.catalog, config = engine.config)
        val keys = mutableSetOf<String>()
        for (seed in 1L..40L) {
            val c = ResolutionContext(launch.newRun(LegacyProfile(), seed), launch.content, launch.config)
            keys += WorldEvents.fire(c, WorldEvents.byId("weapon_fragment")).data.getValue("key")
            assertEquals(emptyList(), Invariants.check(c.toState(), launch.config))
        }
        assertTrue(keys.map { SignatureCatalog.byId.getValue(it.removePrefix("sig:")).familyId }.toSet().size >= 4, "spread across families: $keys")
    }

    @Test
    fun caravanDelayEmptiesRareSupplyNextMorningAndFestivalBringsExtraCustomers() {
        val s = engine.newRun(LegacyProfile(), 9)
        val delayed = fire(s, "caravan_delayed")
        assertEquals(s.day + 1, delayed.worldFlags[WorldEvents.FLAG_CARAVAN_DELAYED])
        val morning = delayed.endDay()
        assertEquals(0, morning.supplierStock.getValue(SliceContent.SILVER))
        assertEquals(0, morning.supplierStock.getValue(SliceContent.STORMGLASS))
        assertTrue(morning.worldFlags.isEmpty() || morning.worldFlags.values.all { it >= morning.day }, "stale flags are dropped")
        assertEquals(1, morning.endDay().supplierStock.getValue(SliceContent.SILVER), "normal restock resumes")

        // The shop seats its capacity and no more; whoever else was willing is recorded as turned away. A festival adds seats.
        val seats = engine.config.customers.shopCapacity
        var maxNormal = 0
        var maxFestival = 0
        var turnedAway = 0
        for (seed in 1L..30L) {
            val base = engine.newRun(LegacyProfile(), seed).copy(reputation = 200)
            val normal = base.endDayAccepted().resolution!!
            maxNormal = maxOf(maxNormal, normal.browsers.size)
            assertTrue(normal.turnedAway.isEmpty() || normal.browsers.size == seats, "seed $seed: nobody is turned away from a shop with a free seat")
            turnedAway += normal.turnedAway.size
            val festival = base.copy(worldFlags = mapOf(WorldEvents.FLAG_FESTIVAL to base.day)).endDayAccepted().resolution!!
            maxFestival = maxOf(maxFestival, festival.browsers.size)
            assertTrue((festival.browsers.mapNotNull { it.heroId } + festival.turnedAway).containsAll(normal.browsers.mapNotNull { it.heroId } + normal.turnedAway), "seed $seed: a festival only adds to the willing")
        }
        assertEquals(seats, maxNormal)
        assertEquals(seats + engine.config.customers.festivalExtraSeats, maxFestival)
        assertTrue(turnedAway > 0, "on a busy day someone finds the shop full")
    }

    @Test
    fun collectorBuysAFamousListedWeapon() {
        var s = engine.newRun(LegacyProfile(), 4).withMaterials()
        val out = s.forgeAccepted(quickSword())
        val id = out.forgedWeaponId!!
        s = out.state.run(Command.ToggleShelf(id, true, 100))
        assertFalse(eligible(s, "collector"))
        s = s.copy(weapons = s.weapons + (id to s.weapon(id).copy(fame = 4)))
        assertTrue(eligible(s, "collector"))
        val after = fire(s, "collector")
        assertEquals(s.gold + 150, after.gold)
        assertTrue(after.weapon(id).location is WeaponLocation.Lost)
    }

    @Test
    fun seizedWeaponsCanReturnThroughHeroicInheritance() {
        var s = engine.newRun(LegacyProfile(), 6).withMaterials()
        val out = s.forgeAccepted(quickSword())
        val id = out.forgedWeaponId!!
        val victim = out.state.aliveHeroes().first()
        s = out.state.copy(weapons = out.state.weapons + (id to out.state.weapon(id).copy(location = WeaponLocation.Owned(victim.id, true))))
        assertFalse(eligible(s, "heroic_inheritance"))

        val c = ctx(s)
        Battle.kill(c, c.hero(victim.id), "fell to a test", weaponRecovered = false, weaponSeized = true)
        val dead = c.toState()
        assertEquals(emptyList(), Invariants.check(dead, engine.config))
        assertEquals(WeaponLocation.Lost(dead.day, "seized"), dead.weapon(id).location)
        assertTrue(dead.events.any { it.type == EventType.WEAPON_STOLEN && id.value in it.subjectIds })
        assertEquals(HeroFate.DEAD, dead.hero(victim.id).fate)
        assertTrue(eligible(dead, "heroic_inheritance"))

        val returned = fire(dead, "heroic_inheritance")
        val w = returned.weapon(id)
        val heir = returned.hero(w.ownerId!!)
        assertTrue(heir.isAlive && w.isEquipped)
        assertTrue(returned.events.any { it.type == EventType.WEAPON_INHERITED && id.value in it.subjectIds && heir.id.value in it.subjectIds })
        assertEquals(1, returned.weapons.values.count { it.ownerId == heir.id && it.isEquipped })
    }

    @Test
    fun atMostOneEventPerDayAndRepetitionLimitsHoldOver200Seeds() {
        val driver = SimulationDriver()
        val seen = mutableSetOf<String>()
        for (seed in 1L..200L) {
            val (_, state) = driver.playRun(LegacyProfile(), seed, Policy.BALANCED_FAIR)
            val worldEvents = state.events.filter { it.type == EventType.WORLD_EVENT }
            worldEvents.groupBy { it.day }.forEach { (day, es) -> assertEquals(1, es.size, "seed $seed day $day fired ${es.map { it.data["event"] }}") }
            val counts = mutableMapOf<String, Int>()
            worldEvents.groupBy { it.data.getValue("event") }.forEach { (id, es) ->
                val def = WorldEvents.byId(id)
                counts[id] = es.size
                assertTrue(es.size <= def.maxPerRun, "seed $seed: $id fired ${es.size} > ${def.maxPerRun}")
                es.map { it.day }.zipWithNext().forEach { (a, b) -> assertTrue(b - a > def.cooldownDays, "seed $seed: $id fired on days $a and $b (cooldown ${def.cooldownDays})") }
                assertFalse(id in setOf("famous_blade", "descendant"), "seed $seed: $id needs legacy history")
                if (id == "guild_banner") assertTrue(state.town.guilds.isNotEmpty(), "seed $seed: banner without eras needs an in-run guild")
            }
            // Morning visitors count under their own keys, or under the event they took over; neither leaves a WORLD_EVENT record.
            val visitors = state.eventCounters.keys.filter { it.startsWith(Encounters.COUNTER_PREFIX) || Encounters.replaces(engine.content, it) }
            val counters = state.eventCounters - WorldEvents.RUMOUR - visitors.toSet()
            // A run that outlives the record window (routine records are kept `routineEventRetentionDays`) has counted events whose records are gone.
            if (state.day <= engine.config.saveGrowth.routineEventRetentionDays) assertEquals(counts, counters, "seed $seed counters mismatch")
            else counts.forEach { (id, n) -> assertTrue(n <= (counters[id] ?: 0), "seed $seed: $id has $n records for a count of ${counters[id]}") }
            seen += counts.keys
        }
        assertTrue(seen.size >= 12, "variety across 200 runs: $seen")
        assertTrue("weapon_fragment" in seen, "the fragment turns up in seeded runs: $seen")
    }

    @Test
    fun famousBladeReturnsAtMostOncePerRunWhenLegendsExist() {
        val driver = SimulationDriver()
        var returned = 0
        for (seed in 1L..60L) {
            val (_, state) = driver.playRun(storiedLegacy, seed, Policy.BALANCED_FAIR)
            val blades = state.events.filter { it.type == EventType.WORLD_EVENT && it.data["event"] == "famous_blade" }
            assertTrue(blades.size <= 1)
            if (blades.isNotEmpty()) {
                returned++
                assertTrue(state.weapons.values.any { it.title == legend.title })
            }
        }
        assertTrue(returned > 0, "a legend should return in some of 60 runs")
    }

    @Test
    fun retirementFoundsGuildMentorsNewcomerAndPassesTheWeaponOn() {
        var s = engine.newRun(LegacyProfile(), 11).withMaterials()
        val out = s.forgeAccepted(quickSword())
        val wid = out.forgedWeaponId!!
        val vet = out.state.aliveHeroes().first()
        s = out.state.copy(
            heroes = out.state.heroes + (vet.id to vet.copy(level = engine.config.retirementLevel, fame = engine.config.guildFameThreshold, health = 100)),
            weapons = out.state.weapons + (wid to out.state.weapon(wid).copy(location = WeaponLocation.Owned(vet.id, true))),
        )
        val acc = s.endDayAccepted()
        val after = acc.state
        val retired = after.hero(vet.id)
        assertEquals(HeroFate.RETIRED, retired.fate)
        assertEquals(s.day, retired.retiredOnDay)
        assertFalse(vet.id in after.town.championIds)
        val guild = after.town.guilds.single()
        assertEquals(vet.id, guild.founderId)
        assertEquals(guild.id, retired.guildId)
        val mentee = after.heroes.values.single { it.mentorName == vet.fullName }
        assertTrue(mentee.isAlive)
        assertEquals(2, mentee.level)
        assertEquals(vet.elementTaste, mentee.elementTaste)
        assertEquals(guild.id, mentee.guildId)
        assertEquals(WeaponLocation.Owned(mentee.id, equipped = true), after.weapon(wid).location)
        assertTrue(after.weapons.values.none { it.ownerId == vet.id })
        for (t in listOf(EventType.HERO_RETIRED, EventType.GUILD_FOUNDED, EventType.HERO_MENTORED, EventType.WEAPON_INHERITED)) {
            assertTrue(acc.resolution!!.events.any { it.type == t && vet.id.value in it.subjectIds }, t.name)
        }
        assertEquals(emptyList(), Invariants.check(after, engine.config))
        // The retiree is not a lineage casualty: the deed records the retirement.
        val ended = after.copy(phase = Phase.ENDED, endCause = "test", town = after.town.copy(integrity = 0), heroes = after.heroes.mapValues { (id, h) -> if (id == vet.id) h.copy(fame = 50) else h })
        assertEquals("retired on day ${s.day}", engine.closeRun(ended).lineage!!.deed)
    }

    @Test
    fun retiredHeroesNeverShopFightOrDefend() {
        val eager = GameEngine(config = engine.config.copy(retirementLevel = 3, retirementVictories = 2, retirementChance = 0.5))
        val acting = setOf(
            EventType.WEAPON_SOLD, EventType.WEAPON_EQUIPPED, EventType.EXPEDITION_WON, EventType.EXPEDITION_LOST, EventType.HERO_PATROLLED,
            EventType.HERO_RESTED, EventType.HERO_WOUNDED, EventType.HERO_DIED, EventType.SIEGE_WON, EventType.SIEGE_LOST, EventType.COMMISSION_COMPLETED,
        )
        var retirements = 0
        for (seed in 1L..60L) {
            var s = eager.newRun(LegacyProfile(), seed).withMaterials().copy(energy = 100)
            repeat(4) { s = eager.handle(s, quickSword()).acceptedOrThrow().state }
            s.storedWeapons().forEach { s = eager.handle(s, Command.ToggleShelf(it.id, true, 1)).acceptedOrThrow().state }
            while (!s.isEnded && s.day < 40) {
                val retiredBefore = s.retiredHeroes().map { it.id }.toSet()
                val acc = eager.handle(s, Command.EndDay(endDayId(s))).acceptedOrThrow()
                for (e in acc.resolution!!.events) {
                    if (e.type in acting) assertTrue(e.subjectIds.none { HeroId(it) in retiredBefore }, "seed $seed: retired hero acted: ${e.text}")
                }
                assertTrue(acc.resolution.visits.none { it.heroId in retiredBefore })
                s = acc.state
                assertTrue(s.town.championIds.none { it in s.retiredHeroes().map { h -> h.id } })
                assertTrue(s.weapons.values.none { w -> w.ownerId != null && s.hero(w.ownerId!!).fate == HeroFate.RETIRED })
                assertEquals(emptyList(), Invariants.check(s, eager.config))
            }
            retirements += s.retiredHeroes().size
        }
        assertTrue(retirements > 0, "eager retirement config should retire somebody")
    }
}
