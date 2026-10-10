package com.tinyblacksmith.core

import com.tinyblacksmith.core.TestSupport.endDay
import com.tinyblacksmith.core.TestSupport.endDayAccepted
import com.tinyblacksmith.core.TestSupport.engine
import com.tinyblacksmith.core.TestSupport.forgeAccepted
import com.tinyblacksmith.core.TestSupport.quickSword
import com.tinyblacksmith.core.TestSupport.run
import com.tinyblacksmith.core.TestSupport.withMaterials
import com.tinyblacksmith.core.battle.Battle
import com.tinyblacksmith.core.content.Depth
import com.tinyblacksmith.core.content.LaunchContent
import com.tinyblacksmith.core.engine.Command
import com.tinyblacksmith.core.engine.CommandOutcome
import com.tinyblacksmith.core.engine.GameEngine
import com.tinyblacksmith.core.engine.GameError
import com.tinyblacksmith.core.engine.ResolutionContext
import com.tinyblacksmith.core.model.*
import com.tinyblacksmith.core.rng.RngStream
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Workshop relics (offer, slots, replacement, each relic's limit) and siege traits (announced early, the forecast is the fight). */
class RelicsAndSiegeTraitsTest {
    private val config = engine.config
    private val content = engine.content

    private fun GameState.rejected(cmd: Command): GameError = assertIs<CommandOutcome.Rejected>(engine.handle(this, cmd)).error
    private fun fresh(seed: Long = 7) = engine.newRun(LegacyProfile(), seed).withMaterials().copy(gold = 1000)
    private fun GameState.holding(vararg ids: String) = copy(relics = ids.map { ActiveRelic(it) }, pendingRelicOffer = emptyList())
    private fun spear(bellows: Boolean = false) = Command.Forge(ForgeMode.QUICK, LaunchContent.SPEAR, LaunchContent.IRON, LaunchContent.EMBER_RESIN, null, Risk.SAFE, bellows = bellows)

    // ---- offers and slots -----------------------------------------------------------------------------------

    @Test
    fun aRunOpensWithADraftOfThreeAndChoosingIsOnce() {
        val s = fresh()
        assertEquals(4, content.relics.count { it.effect != com.tinyblacksmith.core.content.RelicEffect.COMBAT })   // the workshop's four; a guild run adds the party's
        assertEquals(3, s.pendingRelicOffer.toSet().size)
        assertTrue(s.relics.isEmpty() && s.relicOffersMade == setOf("start"))
        assertEquals(engine.newRun(LegacyProfile(), 7).pendingRelicOffer, s.pendingRelicOffer)
        val notOffered = content.relics.filter { it.effect != com.tinyblacksmith.core.content.RelicEffect.COMBAT }.map { it.id }.single { it !in s.pendingRelicOffer }
        assertIs<GameError.RelicNotOffered>(s.rejected(Command.ChooseRelic(notOffered)))
        val chosen = s.run(Command.ChooseRelic(s.pendingRelicOffer.first()))
        assertEquals(listOf(ActiveRelic(s.pendingRelicOffer.first())), chosen.relics)
        assertIs<GameError.NoRelicOffer>(chosen.rejected(Command.ChooseRelic(s.pendingRelicOffer.first())))
        assertTrue(s.run(Command.DeclineRelicOffer).let { it.pendingRelicOffer.isEmpty() && it.relics.isEmpty() })
        // The draft draws on its own stream: every other stream is where a run without relics has it.
        val plain = GameEngine(content.copy(relics = emptyList())).newRun(LegacyProfile(), 7)
        for (stream in RngStream.entries - RngStream.ENCOUNTERS) assertEquals(plain.rng.stateOf(stream), engine.newRun(LegacyProfile(), 7).rng.stateOf(stream), stream.name)
    }

    @Test
    fun aFullWorkshopMustNameTheRelicItPutsAwayAndNeverHoldsOneTwice() {
        val full = fresh().holding(Depth.SALVAGERS_CRUCIBLE, Depth.TEMPERING_LEDGER, Depth.COLLECTORS_SEAL).copy(pendingRelicOffer = listOf(Depth.ASHEN_BELLOWS))
        assertIs<GameError.RelicSlotsFull>(full.rejected(Command.ChooseRelic(Depth.ASHEN_BELLOWS)))
        assertIs<GameError.RelicNotOwned>(full.rejected(Command.ChooseRelic(Depth.ASHEN_BELLOWS, replaceId = Depth.ASHEN_BELLOWS)))
        val swapped = full.run(Command.ChooseRelic(Depth.ASHEN_BELLOWS, replaceId = Depth.TEMPERING_LEDGER))
        assertEquals(setOf(Depth.SALVAGERS_CRUCIBLE, Depth.COLLECTORS_SEAL, Depth.ASHEN_BELLOWS), swapped.relics.map { it.id }.toSet())
        // An offer is only ever of relics the workshop lacks.
        val ctx = ResolutionContext(swapped, content, config)
        assertTrue(com.tinyblacksmith.core.engine.Relics.offer(ctx, "test"))
        assertEquals(listOf(Depth.TEMPERING_LEDGER), ctx.pendingRelicOffer)
    }

    @Test
    fun aRelicIsOfferedAfterTheSecondAndFourthSiegeWonOrLostAndNeverForARunThatEnded() {
        val s = fresh().holding().copy(relicOffersMade = setOf("start"))
        fun morningAfter(survived: Int, lost: Int) = s.copy(town = s.town.copy(siegesSurvived = survived, siegesLost = lost)).endDay()
        assertTrue(morningAfter(1, 0).pendingRelicOffer.isEmpty())
        assertTrue(morningAfter(0, 2).pendingRelicOffer.isNotEmpty(), "two lost sieges still bring the second relic")
        assertEquals(setOf("start", "siege2"), morningAfter(1, 1).relicOffersMade)
        // One offer at a time: the fourth siege's relic waits until the second one's is answered.
        val both = morningAfter(4, 0)
        assertEquals(setOf("start", "siege2"), both.relicOffersMade)
        assertEquals(setOf("start", "siege2", "siege4"), both.run(Command.ChooseRelic(both.pendingRelicOffer.first())).endDay().relicOffersMade)
    }

    // ---- Ashen Bellows --------------------------------------------------------------------------------------

    @Test
    fun theBellowsTakeTomorrowsEnergyOnceADayThroughTheSameOverworkAsLateHours() {
        val s = fresh().holding(Depth.ASHEN_BELLOWS)
        assertIs<GameError.RelicNotOwned>(fresh().holding().rejected(spear(bellows = true)))
        val blown = s.forgeAccepted(spear(bellows = true))
        val plain = s.forgeAccepted(spear())
        assertEquals(config.depth.bellowsDebt, blown.state.overworkToday)
        assertEquals(plain.state.energy, blown.state.energy, "the bellows cost nothing today")
        assertEquals(plain.state.weapon(plain.forgedWeaponId!!).affixes.size + config.depth.bellowsExtraSlots, blown.state.weapon(blown.forgedWeaponId!!).affixes.size)
        assertIs<GameError.RelicSpent>(blown.state.rejected(spear(bellows = true)))
        // Tomorrow pays, and the bellows are ready again.
        val next = blown.state.endDay()
        assertEquals(plain.state.endDay().energy - config.depth.bellowsDebt, next.energy)
        assertIs<CommandOutcome.Accepted>(engine.handle(next.withMaterials(), spear(bellows = true)))
        // The debt competes with ordinary overwork for the same four points: with three already taken there is no room for two more.
        val tired = s.copy(energy = 0, overworkToday = 0)
        assertIs<GameError.NotEnoughEnergy>(tired.copy(overworkToday = config.maxOverworkPerDay - 1).rejected(spear(bellows = true)))
        assertEquals(config.quickForgeEnergy + config.depth.bellowsDebt, tired.forgeAccepted(spear(bellows = true)).state.overworkToday)
    }

    @Test
    fun aRelicPutAwayKeepsItsDebtAndDoesNotGiveItsChargeBackTheSameDay() {
        val blown = fresh().holding(Depth.ASHEN_BELLOWS).forgeAccepted(spear(bellows = true)).state
        val swapped = blown.copy(relics = listOf(ActiveRelic(Depth.TEMPERING_LEDGER)), pendingRelicOffer = listOf(Depth.ASHEN_BELLOWS))
        assertEquals(config.depth.bellowsDebt, swapped.overworkToday)
        val back = swapped.run(Command.ChooseRelic(Depth.ASHEN_BELLOWS))
        assertIs<GameError.RelicSpent>(back.rejected(spear(bellows = true)))
    }

    // ---- Tempering Ledger -----------------------------------------------------------------------------------

    @Test
    fun theLedgerRewardsANewFamilyUpToItsCapAndARepeatStartsOver() {
        fun forge(s: GameState, family: WeaponFamilyId) = s.forgeAccepted(Command.Forge(ForgeMode.QUICK, family, LaunchContent.IRON, LaunchContent.EMBER_RESIN, null, Risk.SAFE))
        fun bonus(a: CommandOutcome.Accepted) = a.events.first { it.type == EventType.WEAPON_FORGED }.data["ledger"]?.toInt() ?: 0
        var s = fresh().holding(Depth.TEMPERING_LEDGER).copy(energy = 100)
        val families = content.families.map { it.id }
        val bonuses = families.map { f -> forge(s, f).also { s = it.state }.let(::bonus) }
        assertEquals(listOf(0, 2, 4, 6, 6, 6), bonuses)
        assertEquals(families, s.relics.single().families)
        // The same seed without the ledger forges the same blade two quality lower: the bonus is the only difference.
        val start = fresh().copy(energy = 100)
        val with = forge(forge(start.holding(Depth.TEMPERING_LEDGER), families[0]).state, families[1])
        val without = forge(forge(start.holding(), families[0]).state, families[1])
        assertEquals(without.state.weapon(without.forgedWeaponId!!).quality + 2, with.state.weapon(with.forgedWeaponId!!).quality)
        // Repeating is allowed and costs the streak.
        val repeat = forge(s, families[2])
        assertEquals(0, bonus(repeat))
        assertEquals(listOf(families[2]), repeat.state.relics.single().families)
    }

    // ---- Salvager's Crucible --------------------------------------------------------------------------------

    @Test
    fun theCrucibleReturnsAFineBladesAugmentOnceADayAndNeverMakesGoldOrFreeMaterial() {
        val s = fresh().holding(Depth.SALVAGERS_CRUCIBLE).copy(energy = 100)
        val a = s.forgeAccepted(quickSword(Risk.SAFE))
        fun fine(state: GameState, id: WeaponId, quality: Int) = state.copy(weapons = state.weapons + (id to state.weapon(id).copy(quality = quality)))
        val resin = LaunchContent.EMBER_RESIN
        // A crude blade melts as it always did.
        val crude = fine(a.state, a.forgedWeaponId!!, config.rareMin - 1)
        assertTrue(!engine.salvageKeepsAugment(crude, crude.weapon(a.forgedWeaponId!!)))
        assertEquals(crude.materials[resin], crude.run(Command.Salvage(a.forgedWeaponId!!)).materials[resin])
        // A fine one gives the augment back, once.
        val good = fine(a.state, a.forgedWeaponId!!, config.rareMin)
        val melted = good.run(Command.Salvage(a.forgedWeaponId!!))
        assertEquals(good.materials.getValue(resin) + 1, melted.materials[resin])
        assertEquals(good.gold, melted.gold)
        val b = melted.forgeAccepted(quickSword(Risk.SAFE))
        val second = fine(b.state, b.forgedWeaponId!!, config.rareMin)
        assertEquals(second.materials[resin], second.run(Command.Salvage(b.forgedWeaponId!!)).materials[resin], "the crucible works once a day")
        // The whole loop (forge, melt in the crucible) ends with exactly the materials it began with and less energy: nothing is created.
        assertEquals(s.materials, melted.materials)
        assertTrue(melted.energy < s.energy)
        // Bulk scrap is not a salvage: no augment, charge untouched.
        val scrapped = good.run(Command.Scrap(listOf(a.forgedWeaponId!!)))
        assertEquals(good.materials[resin], scrapped.materials[resin])
        assertTrue(scrapped.relicUses.isEmpty())
    }

    // ---- Collector's Seal -----------------------------------------------------------------------------------

    @Test
    fun theSealCountsOnlyCoinAHeroPaidForTheFirstCostlySaleOfADay() {
        val s = fresh().holding(Depth.COLLECTORS_SEAL)
        val blade = s.forgeAccepted(quickSword(Risk.SAFE)).let { it.state.weapon(it.forgedWeaponId!!) }
        fun sale(price: Int, cash: Int, progress: Int = 0, usedToday: Boolean = false): ResolutionContext {
            val ctx = ResolutionContext(s.copy(relics = listOf(ActiveRelic(Depth.COLLECTORS_SEAL, progress)), relicUses = if (usedToday) mapOf(Depth.COLLECTORS_SEAL to s.day) else emptyMap()), content, config)
            com.tinyblacksmith.core.engine.Relics.onShelfSale(ctx, blade, price, cash)
            return ctx
        }
        val asking = engine.suggestedPrice(blade)
        val min = config.depth.sealMinCash
        assertEquals(1, sale(maxOf(asking, min), min).relics.single().progress)
        assertEquals(0, sale(maxOf(asking, min), min - 1).relics.single().progress, "a trade-in or a stipend is not coin")
        assertEquals(0, sale(asking - 1, min).relics.single().progress, "a blade sold under the going rate earns nothing")
        assertEquals(0, sale(maxOf(asking, min), min, usedToday = true).relics.single().progress, "one seal a day")
        // The third seal is one limited material of tier 3 or more, and the count starts again.
        val third = sale(maxOf(asking, min), min, progress = config.depth.sealsPerReward - 1)
        assertEquals(0, third.relics.single().progress)
        val gained = third.materials.filter { (id, n) -> n != s.materials[id] }
        assertEquals(1, gained.size)
        val material = content.material(gained.keys.single())
        assertTrue(material.dailySupplierStock != null && material.tier >= config.depth.sealMaterialTier && gained.values.single() == s.materials.getValue(material.id) + 1)
    }

    // ---- siege traits ---------------------------------------------------------------------------------------

    private fun siegeEve(trait: String?, seed: Long = 7): GameState {
        val s = fresh(seed).holding()
        val heroes = s.aliveHeroes()
        // Three armed champions with worn blades, a militia and an armory: every term a trait can move is non-zero.
        var armed = s.copy(energy = 100)
        for (h in heroes.take(3)) {
            val made = armed.forgeAccepted(quickSword(Risk.SAFE))
            armed = made.state.copy(weapons = made.state.weapons + (made.forgedWeaponId!! to made.state.weapon(made.forgedWeaponId!!).copy(location = WeaponLocation.Owned(h.id, true), condition = 40)))
        }
        return armed.copy(day = armed.town.nextSiegeDay, town = armed.town.copy(armory = 12, militia = 6), siege = SiegeScenario(armed.town.nextSiegeDay, trait))
    }

    @Test
    fun aTraitChangesTheForecastAndTheFightIsTheForecast() {
        val plain = engine.siegeForecast(siegeEve(null))!!
        val long = engine.siegeForecast(siegeEve(Depth.LONG_ASSAULT))!!
        val breaches = engine.siegeForecast(siegeEve(Depth.MANY_BREACHES))!!
        assertNull(plain.trait)
        assertTrue(long.championPowers.sum() < plain.championPowers.sum(), "worn blades count for less in a long assault")
        assertEquals(plain.militia + plain.armory, long.militia + long.armory, "the watch does not tire")
        assertEquals(plain.raidPower, long.raidPower)
        val many = content.siegeTrait(Depth.MANY_BREACHES)!!
        assertTrue(many.watchMultiplier > 1.0 && many.raidMultiplier > 1.0)
        assertEquals((plain.militia + plain.armory) * many.watchMultiplier, breaches.militia + breaches.armory, 1e-9)
        assertEquals(plain.raidPower * many.raidMultiplier, breaches.raidPower, 1e-9)
        assertEquals(plain.championPowers, breaches.championPowers)
        for (trait in listOf(null, Depth.LONG_ASSAULT, Depth.MANY_BREACHES)) {
            val eve = siegeEve(trait)
            // The fight is `Battle.outlook` taken that evening (the day's patrols, wounds and pressure come first), so the
            // morning's forecast and the record differ only by what the day did: the trait is in both, counted the same way.
            val day = eve.endDayAccepted()
            val fought = day.resolution!!.events.single { it.type == EventType.SIEGE_WON || it.type == EventType.SIEGE_LOST }
            assertEquals(trait, fought.data["trait"])
            assertEquals(fought.data.getValue("townDefense").toInt() >= fought.data.getValue("raidPower").toInt(), fought.type == EventType.SIEGE_WON)
            val replay = day.resolution!!.replays.first { it.kind == ReplayKind.SIEGE }
            val watch = content.siegeTrait(trait)?.watchMultiplier ?: 1.0
            assertEquals((eve.town.armory * watch).roundToInt(), replay.rounds.single { it.attacker == "Town watch" }.damage, "trait $trait: the watch fights at what the forecast counted it for")
        }
    }

    @Test
    fun aLongAssaultWearsBladesTwiceAsFastAndHasTwoAnswers() {
        // Per blade, the siege's own wear doubles (a champion may also have worn the blade on an expedition that day; that part is the same in both).
        fun wornBy(trait: String?) = siegeEve(trait).let { eve -> eve.endDay().weapons.values.filter { it.isEquipped }.sortedBy { it.id.value }.map { 40 - it.condition } }
        assertEquals(wornBy(null).map { it + config.wearPerSiege }, wornBy(Depth.LONG_ASSAULT))
        // Answer one: fresh blades in the champions' hands. Answer two: the watch, whose arms are untouched by the trait.
        val eve = siegeEve(Depth.LONG_ASSAULT)
        val base = engine.siegeForecast(eve)!!.townDefense
        val fresh = eve.copy(weapons = eve.weapons.mapValues { (_, w) -> if (w.isEquipped) w.copy(condition = 100) else w })
        val watch = eve.copy(town = eve.town.copy(armory = eve.town.armory + 10))
        assertTrue(engine.siegeForecast(fresh)!!.townDefense > base)
        assertEquals(base + 10, engine.siegeForecast(watch)!!.townDefense, 1e-9)
    }

    @Test
    fun theNextSiegesTraitIsKnownFromTheEveningTheLastOneEnds() {
        val eve = siegeEve(null)
        val after = eve.endDayAccepted()
        if (after.state.isEnded) return
        val scenario = assertNotNull(after.state.siege)
        assertEquals(after.state.town.nextSiegeDay, scenario.siegeDay)
        assertNull(scenario.factionId, "the besieger is not committed before the first warning")
        // Siege 2 may carry a trait (the first never does); when it does the day's Gazette says so, five days ahead.
        assertNull(fresh().siege!!.traitId)
        assertEquals(scenario.traitId, after.resolution!!.events.singleOrNull { it.type == EventType.SIEGE_TRAIT }?.data?.get("trait"))
        assertEquals(scenario.traitId, engine.siegeForecast(after.state)!!.trait?.id)
        // Over many seeds both traits and the plain siege all occur.
        val drawn = (1L..40L).mapNotNull { seed -> siegeEve(null, seed).endDay().takeIf { !it.isEnded }?.siege?.traitId ?: "plain" }.toSet()
        assertEquals(setOf("plain", Depth.LONG_ASSAULT, Depth.MANY_BREACHES), drawn)
    }

    @Test
    fun theBesiegerIsFixedAtTheFirstWarningWhateverPressureDoesAfter() {
        var s = fresh().holding()
        while (s.town.nextSiegeDay - s.day > config.combat.siegeWarningDays) s = s.endDay()
        assertNull(s.siege!!.factionId)
        val warned = s.endDay()
        val committed = assertNotNull(warned.siege!!.factionId)
        assertEquals(Battle.leadingFaction(ResolutionContext(s.copy(factions = warned.factions), content, config))!!.id, committed)
        // Another faction surges past it the next day: the forecast, the warning and the siege still name the committed one.
        val other = warned.factions.keys.first { it != committed }
        val surged = warned.copy(factions = warned.factions + (other to warned.factions.getValue(other).copy(pressure = 100)))
        assertEquals(committed, engine.siegeForecast(surged)!!.faction.id)
        assertEquals(other, engine.siegeForecast(surged.copy(siege = SiegeScenario(surged.town.nextSiegeDay)))!!.faction.id)
        // Rules 3 behaviour is one switch away, for the comparison runs.
        val live = GameEngine(content, config.copy(depth = config.depth.copy(commitBesieger = false)))
        assertNull(live.handle(s, Command.EndDay(TestSupport.endDayId(s))).let { (it as CommandOutcome.Accepted).state.siege!!.factionId })
    }
}
