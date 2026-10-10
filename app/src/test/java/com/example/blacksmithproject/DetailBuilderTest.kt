package com.example.blacksmithproject

import com.example.blacksmithproject.ui.Labels
import com.example.blacksmithproject.ui.detail.StockAction
import com.example.blacksmithproject.ui.detail.customerSnapshot
import com.example.blacksmithproject.ui.detail.heroDetail
import com.example.blacksmithproject.ui.detail.itemDetail
import com.example.blacksmithproject.ui.detail.toCommand
import com.example.blacksmithproject.ui.detail.weaponSnapshot
import com.tinyblacksmith.core.content.AffixKind
import com.tinyblacksmith.core.content.Element
import com.tinyblacksmith.core.content.LaunchContent
import com.tinyblacksmith.core.engine.Command
import com.tinyblacksmith.core.engine.GameEngine
import com.tinyblacksmith.core.engine.stateOrThrow
import com.tinyblacksmith.core.model.CommandId
import com.tinyblacksmith.core.model.Considered
import com.tinyblacksmith.core.model.CustomerSnapshot
import com.tinyblacksmith.core.model.DayResolution
import com.tinyblacksmith.core.model.EventRecord
import com.tinyblacksmith.core.model.EventType
import com.tinyblacksmith.core.model.ForgeMode
import com.tinyblacksmith.core.model.GameState
import com.tinyblacksmith.core.model.Guild
import com.tinyblacksmith.core.model.Hero
import com.tinyblacksmith.core.model.HeroFate
import com.tinyblacksmith.core.model.HeroId
import com.tinyblacksmith.core.model.HistoryEntry
import com.tinyblacksmith.core.model.LegacyProfile
import com.tinyblacksmith.core.model.MarketVisit
import com.tinyblacksmith.core.model.Risk
import com.tinyblacksmith.core.model.VisitReason
import com.tinyblacksmith.core.model.Weapon
import com.tinyblacksmith.core.model.WeaponId
import com.tinyblacksmith.core.model.WeaponLocation
import com.tinyblacksmith.core.model.WeaponSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The two sheet builders: what they say from the save, from a visit snapshot, and from a snapshot alone. */
class DetailBuilderTest {
    private val engine = GameEngine()
    private val good = engine.content.affixes.first { it.kind == AffixKind.BENEFICIAL }
    private val flaw = engine.content.affixes.first { it.kind == AffixKind.FLAW }

    private class Fixture(val state: GameState, val hero: Hero, val mentor: Hero, val blade: Weapon)

    /** A hero with a taste, a guild and a mentor, who is a regular and carries a blade of ours that has a story. */
    private fun fixture(): Fixture {
        val forged = engine.handle(engine.newRun(LegacyProfile(), 42L), Command.Forge(ForgeMode.QUICK, LaunchContent.SWORD, LaunchContent.IRON, LaunchContent.EMBER_RESIN, null, Risk.BALANCED)).stateOrThrow()
        val mentor = forged.heroes.values.first()
        val hero = forged.heroes.values.elementAt(1).copy(elementTaste = Element.FROST, gold = 77, guildId = "guild_1", mentorName = mentor.fullName, loyalty = engine.config.regularLoyaltyThreshold)
        val made = forged.weapons.values.single()
        val blade = made.copy(
            affixes = listOf(good.id), flaws = listOf(flaw.id), location = WeaponLocation.Owned(hero.id, equipped = true),
            history = made.history + HistoryEntry(1, 2, "SOLD", "Sold to ${hero.fullName} for 40 gold.") + HistoryEntry(1, 3, "VICTORY", "Routed a raider on the north road."),
        )
        val state = forged.copy(
            heroes = forged.heroes + (hero.id to hero), weapons = mapOf(blade.id to blade),
            town = forged.town.copy(guilds = listOf(Guild("guild_1", "The Ember Hall", mentor.id, 1))),
        )
        return Fixture(state, hero, mentor, blade)
    }

    private fun Fixture.atTheCounter() = CustomerSnapshot(
        heroId = hero.id, name = hero.fullName, classId = hero.classId, level = hero.level, appearance = "portrait_${hero.classId.value}_0",
        traits = hero.traits, elementTaste = hero.elementTaste, ambition = hero.ambition, gold = 120, guildId = hero.guildId, mentorName = hero.mentorName,
    )

    @Test
    fun theHeroOfTodayIsToldWholeWithLinksToTheMentorAndTheBlade() {
        val f = fixture()
        val d = engine.heroDetail(f.state, f.hero.id)!!
        assertEquals(f.hero.fullName, d.name)
        assertTrue("no snapshot, no counter block", d.counter.isEmpty())
        val now = d.now.associateBy { it.label }
        assertEquals(listOf("Health", "Class", "Element taste", "Traits", "Purse", "Standing", "Guild", "Mentor", "Carries"), d.now.map { it.label }.filter { it != "Ambition" })
        assertEquals("Favours frost blades", now.getValue("Element taste").value)
        assertEquals("77 gold", now.getValue("Purse").value)
        assertEquals("A regular of your shop", now.getValue("Standing").value)
        assertEquals("The Ember Hall", now.getValue("Guild").value)
        assertEquals(f.mentor.fullName to f.mentor.id, now.getValue("Mentor").let { it.value to it.heroId })
        assertEquals(f.blade.name to f.blade.id, now.getValue("Carries").let { it.value to it.weaponId })
        assertEquals("Hale", now.getValue("Health").value)
    }

    @Test
    fun aSnapshotThatDiffersComesFirstAndNowSaysOnlyWhatChanged() {
        val f = fixture()
        val d = engine.heroDetail(f.state, f.hero.id, f.atTheCounter())!!
        val counter = d.counter.associate { it.label to it.value }
        assertEquals("120 gold", counter["Purse"])
        assertEquals("Unarmed", counter["Carries"])
        assertEquals("Not a regular yet", counter["Standing"])
        // The purse, the blade and the standing changed; taste, traits, guild and mentor did not and are not repeated.
        assertEquals(listOf("Health", "Purse", "Standing", "Carries"), d.now.map { it.label })
        assertEquals("77 gold", d.now.first { it.label == "Purse" }.value)

        // A snapshot that says what the save says is not a second block.
        val same = engine.heroDetail(f.state.copy(heroes = f.state.heroes + (f.hero.id to f.hero.copy(gold = 120, loyalty = 0)), weapons = emptyMap()), f.hero.id, f.atTheCounter())!!
        assertTrue(same.counter.isEmpty())
        assertTrue(same.now.any { it.label == "Traits" })
    }

    @Test
    fun aHeroWhoFellOrLeftTheSaveStillOpens() {
        val f = fixture()
        val fallen = f.hero.copy(fate = HeroFate.DEAD, diedOnDay = 4, gold = 0)
        val dead = engine.heroDetail(f.state.copy(heroes = f.state.heroes + (fallen.id to fallen), weapons = emptyMap()), f.hero.id, f.atTheCounter())!!
        assertEquals("120 gold", dead.counter.first { it.label == "Purse" }.value)
        assertEquals(listOf("Health"), dead.now.map { it.label })
        assertEquals("Fallen on day 4", dead.now.single().value)
        assertNotNull("the fallen marker", dead.marker)

        val gone = engine.heroDetail(f.state.copy(heroes = f.state.heroes - f.hero.id), f.hero.id, f.atTheCounter())!!
        assertEquals(f.hero.fullName, gone.name)
        assertTrue(gone.counter.isNotEmpty())
        assertEquals("No longer in the town's records", gone.now.single().value)
        assertNull(gone.deeds)

        assertNull("no hero and no snapshot: nothing to open", engine.heroDetail(f.state, HeroId("hero_nobody")))
    }

    @Test
    fun withYourShopAndRecentEventsAreNewestFirst() {
        val f = fixture()
        val id = f.hero.id.value
        fun record(n: Int, day: Int, type: EventType, text: String) = EventRecord("e$n", 1, day, type, 3, text, listOf(id))
        val shelf = WeaponSnapshot.of(f.blade).copy(weaponId = WeaponId("w_shelf"), name = "Bronze Axe")
        val refusal = MarketVisit(f.hero.id, f.hero.fullName, null, VisitReason.TOO_EXPENSIVE, customer = f.atTheCounter(), considered = listOf(Considered(shelf.weaponId, 90, shortBy = 13)))
        val state = f.state.copy(
            events = listOf(
                record(1, 2, EventType.WEAPON_SOLD, "bought a sword"), record(2, 3, EventType.EXPEDITION_WON, "won on the road"),
                record(3, 4, EventType.COMMISSION_OFFERED, "asked for a spear"), record(4, 5, EventType.HERO_RESTED, "rested"),
                EventRecord("e5", 1, 5, EventType.HERO_RESTED, 1, "someone else rested", listOf("hero_other")),
            ),
            lastResolution = DayResolution(CommandId("c"), 5, emptyList(), emptyList(), listOf(refusal), emptyList(), defeated = false, shopWeapons = listOf(shelf)),
        )
        val d = engine.heroDetail(state, f.hero.id)!!
        assertEquals(
            listOf("Day 5: Left without buying: could afford nothing on the shelf; Bronze Axe was 13 gold out of reach.", "Day 4: asked for a spear", "Day 2: bought a sword"),
            d.shop,
        )
        assertEquals(listOf("Day 5: rested", "Day 3: won on the road"), d.events)
        assertEquals(f.atTheCounter(), state.lastResolution!!.customerSnapshot(f.hero.id))
        assertEquals(shelf, state.lastResolution!!.weaponSnapshot(shelf.weaponId))
    }

    @Test
    fun theBladeIsToldWithItsPropertiesRecipeHolderAndHistory() {
        val f = fixture()
        val d = engine.itemDetail(f.state, f.blade.id)!!
        assertEquals(f.blade.name, d.name)
        assertTrue(d.counter.isEmpty())
        assertEquals(listOf(good.name to good.description), d.affixes.map { it.name to it.description })
        assertEquals(listOf(flaw.name to flaw.description), d.flaws.map { it.name to it.description })
        assertTrue("every affix and flaw has a description", (d.affixes + d.flaws).all { it.description.isNotBlank() })
        assertEquals(listOf("Day 3: Routed a raider on the north road.", "Day 2: Sold to ${f.hero.fullName} for 40 gold."), d.history.take(2))
        assertTrue(d.history.last().startsWith("Day 1: Forged from Iron"))
        val holder = d.now.first()
        assertEquals(Triple("Carried by", f.hero.fullName, f.hero.id), Triple(holder.label, holder.value, holder.heroId))
        assertTrue("the rarity is a word", d.now.first { it.label == "Rarity" }.value.startsWith(f.blade.rarity.name.lowercase().replaceFirstChar { it.uppercase() }))
        assertEquals(listOf("Sword", "Iron", "Ember Resin"), d.recipe.take(3).map { it.value })
        assertNull("a carried blade is not the shop's to price", d.stock)
    }

    @Test
    fun stockIsOfferedOnlyForABladeInTheShop() {
        val f = fixture()
        val stored = f.blade.copy(location = WeaponLocation.Storage)
        val inStorage = engine.itemDetail(f.state.copy(weapons = mapOf(stored.id to stored)), stored.id)!!.stock!!
        assertNull(inStorage.listedPrice)
        assertEquals(engine.suggestedPrice(stored), inStorage.suggestedPrice)
        val listed = stored.copy(location = WeaponLocation.Shelf(55))
        assertEquals(55, engine.itemDetail(f.state.copy(weapons = mapOf(listed.id to listed)), listed.id)!!.stock!!.listedPrice)
        val melted = stored.copy(location = WeaponLocation.Destroyed(3))
        val gone = engine.itemDetail(f.state.copy(weapons = mapOf(melted.id to melted)), melted.id)!!
        assertNull(gone.stock)
        assertEquals("Destroyed on day 3", gone.now.first().value)
    }

    @Test
    fun everyStockActionIsTheCommandItNames() {
        val id = WeaponId("w1")
        assertEquals(Command.ToggleShelf(id, true, 55), StockAction.ListAt(55).toCommand(id))
        assertEquals(Command.SetPrice(id, 70), StockAction.SetPrice(70).toCommand(id))
        assertEquals(Command.ToggleShelf(id, false), StockAction.Unlist.toCommand(id))
        assertEquals(Command.Salvage(id), StockAction.Salvage.toCommand(id))
        assertEquals(Command.Hone(id), StockAction.Hone.toCommand(id))
        assertEquals(Command.DonateWeapon(id), StockAction.Donate.toCommand(id))
        // A blade listed from the sheet at a typed price is on the shelf at that price.
        val f = fixture()
        val stored = f.blade.copy(location = WeaponLocation.Storage)
        val listed = engine.handle(f.state.copy(weapons = mapOf(stored.id to stored)), StockAction.ListAt(55).toCommand(stored.id)).stateOrThrow()
        assertEquals(55, engine.itemDetail(listed, stored.id)!!.stock!!.listedPrice)
    }

    /** The condition word follows the engine's own line (`wornConditionThreshold`, which the market and the requests use), not a number of the screen's. */
    @Test
    fun conditionWordsFollowTheEnginesWornLine() {
        val line = engine.config.wornConditionThreshold
        assertEquals(listOf(null, "worn", "worn", "battered"), listOf(line, line - 1, line / 2, line / 2 - 1).map { Labels.condition(it) })
        val f = fixture()
        fun word(condition: Int) = f.blade.copy(condition = condition).let { w -> engine.itemDetail(f.state.copy(weapons = mapOf(w.id to w)), w.id)!!.stats.first { it.label == "Condition" }.word }
        assertEquals(listOf("Sound", "Worn"), listOf(word(line), word(line - 1)))
    }

    @Test
    fun aBladeThatChangedOrLeftTheSaveOpensFromItsSnapshot() {
        val f = fixture()
        val morning = WeaponSnapshot.of(f.blade.copy(condition = 100, fame = 0))
        val worn = f.blade.copy(condition = 20, fame = 4)
        val d = engine.itemDetail(f.state.copy(weapons = mapOf(worn.id to worn)), worn.id, morning)!!
        assertEquals("Sound", d.counter.first { it.label == "Condition" }.value)
        assertEquals(listOf("Carried by", "Condition", "Renown"), d.now.map { it.label })
        assertEquals("Battered", d.now.first { it.label == "Condition" }.value)

        assertTrue("an unchanged blade is told once", engine.itemDetail(f.state, f.blade.id, WeaponSnapshot.of(f.blade))!!.counter.isEmpty())

        val pruned = engine.itemDetail(f.state.copy(weapons = emptyMap()), f.blade.id, morning)!!
        assertEquals(f.blade.name, pruned.name)
        assertTrue(pruned.counter.isNotEmpty())
        assertEquals("No longer in the forge's records", pruned.now.single().value)
        assertEquals(listOf(good.description), pruned.affixes.map { it.description })
        assertEquals(listOf("Sword", "Iron", "Ember Resin"), pruned.recipe.map { it.value })
        assertNull(pruned.stock)
        assertFalse(pruned.signature)

        assertNull("no blade and no snapshot: nothing to open", engine.itemDetail(f.state, WeaponId("w_nothing")))
    }
}
