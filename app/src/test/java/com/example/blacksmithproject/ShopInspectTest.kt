package com.example.blacksmithproject

import com.example.blacksmithproject.ui.FactTone
import com.example.blacksmithproject.ui.TipLine
import com.example.blacksmithproject.ui.TipTone
import com.example.blacksmithproject.ui.TipUi
import com.example.blacksmithproject.ui.WEAPON_CARD
import com.example.blacksmithproject.ui.askFacts
import com.example.blacksmithproject.ui.bladeRules
import com.example.blacksmithproject.ui.detail.itemDetail
import com.example.blacksmithproject.ui.detail.rarityColor
import com.example.blacksmithproject.ui.detail.weaponCard
import com.example.blacksmithproject.ui.fans
import com.example.blacksmithproject.ui.keywords
import com.example.blacksmithproject.ui.relicTip
import com.example.blacksmithproject.ui.ruleParts
import com.example.blacksmithproject.ui.shopUi
import com.example.blacksmithproject.ui.toolTip
import com.example.blacksmithproject.ui.withDetails
import com.tinyblacksmith.core.content.AffixKind
import com.tinyblacksmith.core.content.Element
import com.tinyblacksmith.core.content.LaunchContent
import com.tinyblacksmith.core.engine.Command
import com.tinyblacksmith.core.engine.GameEngine
import com.tinyblacksmith.core.engine.Relics
import com.tinyblacksmith.core.engine.stateOrThrow
import com.tinyblacksmith.core.model.Commission
import com.tinyblacksmith.core.model.CommissionId
import com.tinyblacksmith.core.model.CommissionStatus
import com.tinyblacksmith.core.model.ForgeMode
import com.tinyblacksmith.core.model.LegacyProfile
import com.tinyblacksmith.core.model.Risk
import com.tinyblacksmith.core.model.WeaponLocation
import com.tinyblacksmith.core.sim.CombatSandbox
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The inspect cards of the Shop, Storage and Supplies (V3): what a weapon row, a tool, a relic and a commission say when
 * looked at. Pure builders over fixture states: every value is the save's or the catalog's, nothing is drawn.
 */
class ShopInspectTest {
    private val engine = GameEngine()
    private val good = engine.content.affixes.first { it.kind == AffixKind.BENEFICIAL }
    private val flaw = engine.content.affixes.first { it.kind == AffixKind.FLAW }
    private val forged = engine.handle(engine.newRun(LegacyProfile(), 42L), Command.Forge(ForgeMode.QUICK, LaunchContent.SWORD, LaunchContent.IRON, LaunchContent.EMBER_RESIN, null, Risk.BALANCED)).stateOrThrow()
    private val marked = forged.weapons.values.single().copy(affixes = listOf(good.id), flaws = listOf(flaw.id))

    private fun TipUi.lines() = sections.flatMap { it.lines }

    /** No card says nothing: a name and a kind, titled sections and lines with words in them, stats with both halves. */
    private fun assertNothingEmpty(tip: TipUi) {
        assertTrue(tip.name.isNotBlank() && tip.kind.isNotBlank())
        tip.sections.forEach { s -> assertTrue("section \"${s.title}\"", s.title == null || s.title!!.isNotBlank()) }
        tip.lines().forEach { l -> assertTrue("line \"${l.title}\"", (l.title == null || l.title!!.isNotBlank()) && (l.title != null || l.text.isNotBlank())) }
        tip.stats.forEach { assertTrue("stat ${it.label}", it.label.isNotBlank() && it.value.isNotBlank()) }
    }

    @Test
    fun aShelfRowCarriesTheWeaponsCardWithItsNumbersPropertiesAndPrice() {
        val listed = marked.copy(location = WeaponLocation.Shelf(55))
        val state = forged.copy(weapons = mapOf(listed.id to listed))
        val row = engine.shopUi(state).shelf.single()
        val tip = row.tip!!
        assertEquals(listed.name, tip.name)
        assertEquals(rarityColor(listed.rarity), tip.nameColor)
        assertTrue(tip.kind, tip.kind.startsWith(listed.rarity.name.lowercase().replaceFirstChar { it.uppercase() }))
        assertEquals(engine.itemDetail(state, listed.id)!!.sprite, tip.look)
        assertEquals(tip.look!!.base, tip.icon)
        // The numbers are the row's own (a renown of nothing left out), then what the shelf asks.
        val numbers = setOf("Power", "Quality", "Condition", "Fame")
        // Power leads as the card's headline number; the other numbers follow as its lines.
        assertEquals(row.stats.map { it.label }, (listOfNotNull(tip.headline) + tip.stats).map { it.label }.filter { it in numbers })
        assertEquals("${listed.power}", tip.headline!!.value)
        assertEquals("55 gold", tip.stats.single { it.label == "Asking" }.value)
        assertTrue(tip.stats.none { it.label == "Suggested price" })
        // How the besieger takes its element, only when the row says so, with its sign.
        val mark = tip.stats.firstOrNull { it.label == "Against the siege" }
        assertEquals(row.threat?.label, mark?.value)
        row.threat?.let { assertEquals(if (it.counters) FactTone.GOOD else FactTone.WARN, mark!!.tone) }
        // Each buff a gain and each flaw a cost, by name, with what it does in the catalog's words.
        // The properties lead the card untitled: a "+" or "−" says which is which.
        val properties = tip.sections.flatMap { it.lines }.filter { it.tone == TipTone.BONUS || it.tone == TipTone.DRAWBACK }
        assertEquals(TipLine(good.description, TipTone.BONUS, title = good.name), properties.single { it.title == good.name })
        assertEquals(TipLine(flaw.description, TipTone.DRAWBACK, title = flaw.name), properties.single { it.title == flaw.name })
        assertEquals(engine.fans(listed.familyId).joinToString(", "), tip.sections.single { it.title == "Suits" }.lines.single().text)
        assertNull(tip.warning)
        assertEquals(WEAPON_CARD, tip.tag)
        assertNothingEmpty(tip)
        // Built without actions; the row adds its one way on, which only opens the sheet.
        assertTrue(tip.actions.isEmpty())
        var opened = 0
        val shown = tip.withDetails { opened++ }
        assertEquals(listOf("Full details" to "${WEAPON_CARD}_details"), shown.actions.map { it.label to it.tag })
        shown.actions.single().onClick()
        assertEquals(1, opened)
        // Equal saves give equal rows: the card holds no lambda and draws nothing.
        assertEquals(engine.shopUi(state), engine.shopUi(state))
    }

    @Test
    fun aStoredWeaponSaysItsSuggestedPriceAndOneKeptForAnOrderSaysWhatStandsInTheWay() {
        val stored = marked.copy(location = WeaponLocation.Storage)
        val free = engine.shopUi(forged.copy(weapons = mapOf(stored.id to stored))).storage.single().tip!!
        assertEquals("${engine.suggestedPrice(stored)} gold", free.stats.single { it.label == "Suggested price" }.value)
        assertTrue(free.stats.none { it.label == "Asking" })
        assertNull(free.warning)

        // Kept for an order: the reservation is the card's warning, and no price is offered for a weapon that cannot be listed.
        val buyer = forged.aliveHeroes().first()
        val order = Commission(CommissionId("c1"), buyer.id, stored.familyId, 10, 100, forged.day, forged.day + 3, CommissionStatus.ACCEPTED)
        val kept = engine.shopUi(forged.copy(weapons = mapOf(stored.id to stored.copy(promisedTo = order.id)), commissions = mapOf(order.id to order))).storage.single()
        assertEquals(kept.promised, kept.tip!!.warning)
        assertTrue(kept.tip!!.warning!!, buyer.fullName in kept.tip!!.warning!!)
        assertTrue(kept.tip!!.stats.none { it.label == "Suggested price" || it.label == "Asking" })
    }

    @Test
    fun theCardAndTheSheetReadTheSameWeapon() {
        val stored = marked.copy(location = WeaponLocation.Storage)
        val state = forged.copy(weapons = mapOf(stored.id to stored))
        val card = engine.weaponCard(state, stored)
        val sheet = engine.itemDetail(state, stored.id)!!
        assertEquals(sheet.stats.filter { it.max != null || it.value > 0 }, card.stats)
        // Everything else the card has is the sheet's; the records (price, story, history) are the sheet's alone.
        assertEquals(sheet.copy(stats = card.stats, counter = emptyList(), now = emptyList(), recipe = emptyList(), story = emptyList(), history = emptyList(), stock = null, loan = null), card)
    }

    @Test
    fun inAGuildRunTheCardListsEveryFightRuleAsAnAbilityAndExplainsTheStatusesItNames() {
        val guild = engine.guildRun(42)
        val sword = CombatSandbox.capacitor.copy(location = WeaponLocation.Storage)
        val state = guild.copy(weapons = guild.weapons + (sword.id to sword))
        val tip = engine.shopUi(state).storage.single { it.weapon.id == sword.id }.tip!!
        val rules = bladeRules(sword, engine.content)
        // The fight rules are the card's ability rows under "In guild fights" (the siege-only ones under their own title).
        val fight = tip.sections.filter { it.title == "In guild fights" }.flatMap { it.lines }.filter { it.tone == TipTone.ABILITY }
        assertEquals(rules.road.map { it.name }, fight.map { it.title })
        assertTrue(fight.all { it.tone == TipTone.ABILITY })
        // A rule reads as an ability: trigger and limit split from the catalog's own sentence, never reworded.
        fight.forEach { l -> assertEquals(ruleParts(rules.road.single { it.name == l.title }.description).text, l.text) }
        val named = keywords(rules.road.flatMap { it.stats }, engine.config)
        assertEquals(named.map { it.name }, tip.keywords.map { it.name })
        assertTrue("Charge burst spends Charge: ${named.map { it.name }}", named.any { it.name == "Charge" })
        assertNothingEmpty(tip)
        // The sheet explains the same statuses under its open rules; a classic run has neither rules nor statuses.
        assertEquals(tip.keywords, engine.itemDetail(state, sword.id)!!.keywords)
        val classic = forged.copy(weapons = mapOf(sword.id to sword))
        assertTrue(engine.shopUi(classic).storage.single().tip!!.sections.none { it.title == "In guild fights" && it.lines.isNotEmpty() })
        assertTrue(engine.itemDetail(classic, sword.id)!!.keywords.isEmpty())
    }

    @Test
    fun aToolsCardSaysWhatItDoesItsLevelAndWhatTheNextCosts() {
        val t = engine.content.tools.first()
        val tip = toolTip(t, 1, 40)
        assertEquals(listOf("Level" to "1 of ${t.maxLevel}", "Next level" to "40 gold"), tip.stats.map { it.label to it.value })
        assertEquals(t.description, tip.lines().single().text)
        assertEquals("Fully built", toolTip(t, t.maxLevel, null).stats.last().value)
        assertNothingEmpty(tip)
    }

    @Test
    fun aRelicsCardSaysWhatItDoesAndWhereItStandsToday() {
        val view = Relics.View("ashen_bellows", "Ashen Bellows", "Once a day a forge can borrow energy.", "Ready.", true)
        val tip = relicTip(view)
        assertEquals(listOf("What it does" to view.description, "Today" to view.status), tip.sections.map { it.title to it.lines.single().text })
        assertNothingEmpty(tip)
    }

    @Test
    fun aCommissionsAsksAreShortFactsFromItsOwnTerms() {
        val buyer = forged.aliveHeroes().first()
        val order = Commission(CommissionId("c1"), buyer.id, LaunchContent.SWORD, 40, 120, forged.day, forged.day + 3, CommissionStatus.OFFERED, element = Element.FROST)
        val r = engine.shopUi(forged.copy(commissions = mapOf(order.id to order))).requests.single()
        assertEquals(listOf(engine.content.family(LaunchContent.SWORD).name, "Frost", "40 or more"), r.askFacts().map { it.value })
        assertEquals("Quality", r.askFacts().last().label)
        // An offer is not an obligation: the board says "Offer" until it is accepted.
        assertEquals("Offer", r.status)
        assertEquals(120, r.reward)
    }
}
