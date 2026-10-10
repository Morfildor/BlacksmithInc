package com.tinyblacksmith.core

import com.tinyblacksmith.core.TestSupport.endDay
import com.tinyblacksmith.core.TestSupport.endDayAccepted
import com.tinyblacksmith.core.TestSupport.engine
import com.tinyblacksmith.core.TestSupport.forgeAccepted
import com.tinyblacksmith.core.TestSupport.run
import com.tinyblacksmith.core.TestSupport.withMaterials
import com.tinyblacksmith.core.content.Depth
import com.tinyblacksmith.core.content.LaunchContent
import com.tinyblacksmith.core.engine.Command
import com.tinyblacksmith.core.engine.CommandOutcome
import com.tinyblacksmith.core.engine.Encounters
import com.tinyblacksmith.core.engine.GameEngine
import com.tinyblacksmith.core.engine.GameError
import com.tinyblacksmith.core.model.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The follow-up chain: a blade made on trust, and what the simulation, not the choice, makes of it after the siege. */
class WallPledgeChainTest {
    private val config = engine.config
    private val content = engine.content

    /** The same rules with a raid a tenth as strong: the town holds, so the test is about the morning after and not about the fight. */
    private val easySiege = GameEngine(content, config.copy(siegeModifier = 0.1))

    private fun GameState.answer(option: String, id: String = "a1") = Command.ResolveEncounter(encounter!!.id, option, CommandId(id))

    /** Day 1, the siege moved to tonight and made safe to win; a poor unarmed hero has asked and been answered "pledge". */
    private fun pledged(): Pair<GameState, HeroId> {
        val base = engine.newRun(LegacyProfile(), 7).withMaterials().copy(gold = 1000, energy = 100, pendingRelicOffer = emptyList())
        val heroes = base.aliveHeroes()
        val poor = heroes[0].copy(gold = 5)
        val s = base.copy(
            heroes = base.heroes + (poor.id to poor) + (heroes[1].id to heroes[1].copy(gold = 900)),
            town = base.town.copy(nextSiegeDay = base.day), siege = SiegeScenario(base.day),
        )
        val offered = assertNotNull(Encounters.force(s, content, config, Depth.BLADE_FOR_THE_WALL))
        assertEquals(poor.id, offered.encounter!!.heroId)
        return offered.run(offered.answer("pledge")) to poor.id
    }

    /** A blade that closes the pledge, in storage. */
    private fun GameState.withPledgedBlade(): GameState {
        val order = commissions.values.single { it.kind == CommissionKind.WALL_PLEDGE }
        val made = forgeAccepted(Command.Forge(ForgeMode.QUICK, order.familyId, LaunchContent.IRON, LaunchContent.EMBER_RESIN, null, Risk.SAFE))
        return made.state.copy(weapons = made.state.weapons + (made.forgedWeaponId!! to made.state.weapon(made.forgedWeaponId!!).copy(quality = 80, power = 60)))
    }

    @Test
    fun thePledgeIsAnAcceptedOrderAtHalfRewardThatNamesTheHero() {
        val (s, hero) = pledged()
        val order = s.commissions.values.single()
        val offer = s.encounter!!.amounts
        assertEquals(CommissionStatus.ACCEPTED, order.status)
        assertEquals(hero, order.buyerId)
        assertEquals(offer.getValue("pledgeReward"), order.reward)
        assertEquals(config.commissionRewardBase + order.minQuality * config.commissionRewardPerQuality, order.reward + offer.getValue("owed"))
        assertEquals(ConsequenceKind.WALL_PLEDGE, s.consequences.single().kind)
        // The other answer took the same slot for the richer patron and starts nothing.
        val (again, _) = pledged()
        assertTrue(again.consequences.size == 1)
    }

    @Test
    fun aHeroWhoCameThroughTheSiegeWithTheBladeReturnsTheNextMorning() {
        val (s, hero) = pledged()
        val night = s.withPledgedBlade().let { easySiege.handle(it, Command.EndDay(TestSupport.endDayId(it))) as CommandOutcome.Accepted }
        val next = night.state
        val siege = night.resolution!!.events.singleOrNull { it.type == EventType.SIEGE_WON } ?: error(night.resolution!!.events.filter { it.priority >= 6 }.joinToString(" | ") { "${it.type} ${it.text}" })
        val visitor = assertNotNull(next.encounter)
        assertEquals(Depth.DEBT_REPAID, visitor.defId)
        assertEquals(hero, visitor.heroId)
        assertEquals(next.equippedWeapon(hero)!!.id, visitor.weaponId)
        assertEquals(if (hero.value in siege.subjectIds) 1 else 0, visitor.amounts["stood"], "whether they stood on the wall is read from the siege record")
        assertEquals(1, visitor.amounts["held"])
        assertTrue(next.consequences.isEmpty())
        val view = engine.encounterView(next)!!
        assertTrue(next.hero(hero).fullName in view.text && next.weapon(visitor.weaponId!!).name in view.text)
        assertEquals(visitor.amounts["stood"] == 1, view.options.any { it.id == "speak" }, "only a hero who held the wall can speak for the forge")

        // Collecting takes real coin from a real purse, never more than is there.
        val purse = next.hero(hero).gold
        val owed = visitor.amounts.getValue("owed")
        val paid = next.run(next.answer("collect"))
        assertEquals(minOf(owed, purse), paid.gold - next.gold)
        assertEquals(purse - minOf(owed, purse), paid.hero(hero).gold)
        // Forgiving makes a regular and a drilled militia instead.
        val forgiven = next.run(next.answer("forgive"))
        assertTrue(forgiven.hero(hero).loyalty >= config.regularLoyaltyThreshold && forgiven.town.militia == next.town.militia + config.depth.pledgeMilitia && forgiven.gold == next.gold)
        // Answered once.
        assertIs<GameError.EncounterNotOpen>((engine.handle(paid, next.answer("forgive", "again")) as CommandOutcome.Rejected).error)
    }

    @Test
    fun aHeroWhoDiedIsReportedHonestlyAndBringsNoVisitorAndNoReward() {
        val (s, hero) = pledged()
        // Delivered tonight; the siege is put off so the hero can be lost before the pledge falls due.
        val delivered = s.withPledgedBlade().let { it.copy(town = it.town.copy(nextSiegeDay = it.day + 2), siege = SiegeScenario(it.day + 2)) }.endDay()
        val due = delivered.consequences.single()
        assertEquals(delivered.town.nextSiegeDay + 1, due.dueDay)
        val blade = due.weaponId!!
        val dead = delivered.copy(
            day = due.dueDay - 1,
            heroes = delivered.heroes + (hero to delivered.hero(hero).copy(fate = HeroFate.DEAD, health = 0, diedOnDay = delivered.day)),
            weapons = delivered.weapons + (blade to delivered.weapon(blade).copy(location = WeaponLocation.Lost(delivered.day, "seized"))),
            town = delivered.town.copy(championIds = delivered.town.championIds - hero, nextSiegeDay = due.dueDay + 5, militia = 5000), siege = SiegeScenario(due.dueDay + 5),
        )
        val morning = dead.endDay()
        assertTrue(morning.encounter?.defId != Depth.DEBT_REPAID)
        val told = morning.events.single { it.type == EventType.PLEDGE_RESOLVED }
        assertEquals("dead", told.data["outcome"])
        assertTrue(dead.hero(hero).fullName in told.text && "seized" in told.text, told.text)
        assertTrue(morning.consequences.isEmpty() && morning.gold == dead.gold + (morning.gold - dead.gold).coerceAtLeast(0))
    }

    @Test
    fun aHeroWhoNoLongerCarriesTheBladeEndsTheChainWithoutAVisit() {
        val (s, hero) = pledged()
        val delivered = s.withPledgedBlade().let { it.copy(town = it.town.copy(nextSiegeDay = it.day + 2), siege = SiegeScenario(it.day + 2)) }.endDay()
        val due = delivered.consequences.single()
        val parted = delivered.copy(
            day = due.dueDay - 1,
            weapons = delivered.weapons + (due.weaponId!! to delivered.weapon(due.weaponId!!).copy(location = WeaponLocation.Storage)),
            town = delivered.town.copy(nextSiegeDay = due.dueDay + 5, militia = 5000), siege = SiegeScenario(due.dueDay + 5),
        )
        val morning = parted.endDay()
        assertEquals("parted", morning.events.single { it.type == EventType.PLEDGE_RESOLVED }.data["outcome"])
        assertTrue(morning.encounter?.defId != Depth.DEBT_REPAID && morning.consequences.isEmpty())
        assertTrue(morning.hero(hero).isAlive)
    }

    @Test
    fun aPledgeNeverMadeGoodLapsesWithItsOrder() {
        val (s, _) = pledged()
        var next = s.copy(town = s.town.copy(nextSiegeDay = s.day + 20), siege = SiegeScenario(s.day + 20))
        val deadline = next.commissions.values.single().deadlineDay
        while (next.day <= deadline) next = next.endDay()
        assertEquals(CommissionStatus.EXPIRED, next.commissions.values.first { it.kind == CommissionKind.WALL_PLEDGE }.status)
        assertTrue(next.consequences.none { it.kind == ConsequenceKind.WALL_PLEDGE })
        assertEquals("lapsed", next.events.single { it.type == EventType.PLEDGE_RESOLVED }.data["outcome"])
        assertNull(next.encounterLog.firstOrNull { it.defId == Depth.DEBT_REPAID })
    }

    @Test
    fun aRestoredHeirloomIsKeptForItsHeirAndNothingElseCanTakeIt() {
        val base = engine.newRun(LegacyProfile(), 7).withMaterials().copy(gold = 1000, energy = 100, pendingRelicOffer = emptyList())
        val heroes = base.aliveHeroes()
        val fallen = heroes[0].copy(fate = HeroFate.DEAD, health = 0, diedOnDay = 1, guildId = "g1")
        val made = base.forgeAccepted(Command.Forge(ForgeMode.QUICK, LaunchContent.SWORD, LaunchContent.IRON, LaunchContent.EMBER_RESIN, null, Risk.SAFE))
        val id = made.forgedWeaponId!!
        val s = made.state.copy(
            heroes = made.state.heroes + (fallen.id to fallen) + (heroes[1].id to heroes[1].copy(guildId = "g1")),
            town = made.state.town.copy(championIds = made.state.town.championIds - fallen.id),
            weapons = made.state.weapons + (id to made.state.weapon(id).copy(condition = 40, history = made.state.weapon(id).history + HistoryEntry(made.state.era, 1, "RECOVERED", "Recovered.", listOf(fallen.id.value)))),
        ).let { assertNotNull(Encounters.force(it, content, config, Depth.CRACKED_FAMILY_BLADE)) }
        assertEquals(heroes[1].id, s.encounter!!.heirId())
        val restored = s.run(s.answer("restore"))
        val order = restored.commissions.values.single()
        assertEquals(order.id, restored.weapon(id).promisedTo)
        assertEquals(100, restored.weapon(id).condition)
        assertEquals(s.materials.getValue(LaunchContent.IRON) - 1, restored.materials[LaunchContent.IRON])
        // One blade, one place: promised, it cannot be listed, melted, scrapped or given away, and a second sword order cannot have it.
        for (cmd in listOf(Command.ToggleShelf(id, listed = true), Command.Salvage(id), Command.Scrap(listOf(id)), Command.DonateWeapon(id)))
            assertIs<GameError.WeaponPromised>((engine.handle(restored, cmd) as CommandOutcome.Rejected).error, cmd.toString())
        val rival = Commission(CommissionId("c99"), heroes[2].id, LaunchContent.SWORD, 1, 10, restored.day, restored.day + 3, CommissionStatus.ACCEPTED)
        val night = restored.copy(commissions = restored.commissions + (rival.id to rival)).endDay()
        assertEquals(WeaponLocation.Owned(heroes[1].id, true).heroId, night.weapon(id).ownerId)
        assertNull(night.weapon(id).promisedTo)
        assertEquals(CommissionStatus.ACCEPTED, night.commissions.getValue(rival.id).status)
        assertEquals(1, night.weapons.values.count { it.name == night.weapon(id).name && it.id == id })
        // An heir who dies first frees the blade again.
        val orphaned = restored.copy(heroes = restored.heroes + (heroes[1].id to restored.hero(heroes[1].id).copy(fate = HeroFate.DEAD, health = 0, diedOnDay = restored.day)),
            town = restored.town.copy(championIds = restored.town.championIds - heroes[1].id)).endDay()
        assertTrue(orphaned.weapon(id).isInStorage && orphaned.weapon(id).promisedTo == null)
    }

    private fun EncounterInstance.heirId(): HeroId? = heroId
}
