package com.example.blacksmithproject

import com.example.blacksmithproject.ui.RecipeSlot
import com.example.blacksmithproject.ui.forgeOptions
import com.example.blacksmithproject.ui.forgeWorkbench
import com.example.blacksmithproject.ui.inMode
import com.tinyblacksmith.core.crafting.ClueRung
import com.tinyblacksmith.core.crafting.SignatureCatalog
import com.tinyblacksmith.core.crafting.Journal as JournalRules
import com.example.blacksmithproject.ui.shopUi
import com.tinyblacksmith.core.content.MaterialCategory
import com.tinyblacksmith.core.engine.Command
import com.tinyblacksmith.core.engine.Technique
import com.tinyblacksmith.core.model.Commission
import com.tinyblacksmith.core.model.CommissionId
import com.tinyblacksmith.core.model.CommissionStatus
import com.tinyblacksmith.core.model.ForgeMode
import com.tinyblacksmith.core.model.Journal
import com.tinyblacksmith.core.model.KnowledgeState
import com.tinyblacksmith.core.model.HeroFate
import com.tinyblacksmith.core.model.Want
import com.tinyblacksmith.core.engine.CommandOutcome
import com.tinyblacksmith.core.engine.ResolutionContext
import com.tinyblacksmith.core.market.Market
import com.tinyblacksmith.core.shopday.Lines
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The Forge workbench as a pure reading of the save and the draft: slots, the one action, field notes and the commission brief. */
class ForgeWorkbenchModelTest : ShopDayTestBase() {
    private val content get() = engine.content
    private val family get() = content.families.first()
    private val core get() = content.materials(MaterialCategory.CORE).first { (fresh.materials[it.id] ?: 0) > 0 }
    private val augment get() = content.materials(MaterialCategory.AUGMENT).first { (fresh.materials[it.id] ?: 0) > 0 }
    private val ready get() = ForgeDraft(familyId = family.id, coreId = core.id, augmentId = augment.id)

    private fun bench(draft: ForgeDraft, state: com.tinyblacksmith.core.model.GameState = fresh) = engine.forgeWorkbench(state, draft, engine.shopUi(state).requests)

    @Test
    fun anEmptyDraftAsksForTheWeaponFirstAndThenEachMissingIngredient() {
        val empty = bench(ForgeDraft())
        assertEquals(RecipeSlot.WEAPON, empty.next)
        assertEquals("Choose a weapon type", empty.action.note)
        assertFalse(empty.action.enabled)
        assertNull(empty.command)
        assertEquals(listOf(RecipeSlot.WEAPON, RecipeSlot.METAL, RecipeSlot.AUGMENT), empty.slots.map { it.slot })

        val one = bench(ForgeDraft(familyId = family.id))
        assertEquals(RecipeSlot.METAL, one.next)
        assertEquals("Choose a metal", one.action.note)
        val two = bench(ForgeDraft(familyId = family.id, coreId = core.id))
        assertEquals(RecipeSlot.AUGMENT, two.next)
        assertEquals("Choose an augment", two.action.note)
        assertTrue(two.notes.isEmpty())
    }

    @Test
    fun aReadyRecipeIsOneActionThatSaysItsCostAndCarriesTheDraftUnchanged() {
        val b = bench(ready)
        assertNull(b.next)
        assertEquals("Forge · ${engine.config.quickForgeEnergy} energy", b.action.label)
        assertTrue(b.action.enabled)
        assertNull(b.action.note)
        assertEquals(Command.Forge(ForgeMode.QUICK, family.id, core.id, augment.id, null, ready.risk, null), b.command)
        assertEquals("${core.name} ${family.name.lowercase()}", b.title)
        assertEquals(listOf(null, fresh.materials[core.id], fresh.materials[augment.id]), b.slots.map { it.stock })
    }

    @Test
    fun advancedAddsCatalystAndTechniqueAndQuickNeverSendsThem() {
        val catalyst = content.materials(MaterialCategory.CATALYST).first()
        val advanced = bench(ready.copy(mode = ForgeMode.ADVANCED, catalystId = catalyst.id, technique = Technique.QUENCH))
        assertEquals(listOf(RecipeSlot.WEAPON, RecipeSlot.METAL, RecipeSlot.AUGMENT, RecipeSlot.CATALYST, RecipeSlot.TECHNIQUE), advanced.slots.map { it.slot })
        assertEquals("Forge · ${engine.config.advancedForgeEnergy} energy", advanced.action.label)
        // A draft that still holds them in Quick (it should not) cannot leak them into the command.
        val quick = bench(ready.copy(catalystId = catalyst.id, technique = Technique.QUENCH))
        assertNull(quick.command!!.catalystId)
        assertNull(quick.command!!.technique)
    }

    @Test
    fun aMissingMaterialIsNamedWithItsRestock() {
        val out = fresh.copy(materials = fresh.materials + (core.id to 0))
        val b = bench(ready, out)
        assertFalse(b.action.enabled)
        assertEquals("No ${core.name} left", b.action.note)
        assertEquals(core.id, b.action.restock)
        // Still shown in the picker, apart from what can be used.
        val options = engine.forgeOptions(out, ready, RecipeSlot.METAL, null)
        assertEquals(0, options.first { it.id == core.id.value }.stock)
        assertTrue(options.indexOfFirst { it.stock == 0 } > options.indexOfLast { (it.stock ?: 1) > 0 })
    }

    @Test
    fun overworkSaysItsPriceAndNoEnergyOffersEndDay() {
        val cost = engine.config.quickForgeEnergy
        val tired = bench(ready, fresh.copy(energy = cost - 1))
        assertTrue(tired.action.enabled)
        assertEquals("You'll have 1 less energy tomorrow", tired.action.note)
        assertFalse(tired.action.endDay)
        val spent = bench(ready, fresh.copy(energy = 0, overworkToday = engine.config.maxOverworkPerDay))
        assertFalse(spent.action.enabled)
        assertTrue(spent.action.endDay)
    }

    @Test
    fun fieldNotesKeepTheTwoRelationshipsApartAndSayNothingOfAnUntriedOne() {
        val ca = Journal.coreAugmentKey(core.id, augment.id)
        val af = Journal.augmentFamilyKey(augment.id, family.id)
        val untried = bench(ready).notes
        assertEquals(listOf("Metal + augment", "Augment + weapon"), untried.map { it.label })
        assertEquals(listOf("Untried", "Untried"), untried.map { it.stage })
        assertTrue(untried.all { it.hint == "Forge to learn" })

        val knowing = fresh.copy(legacy = fresh.legacy.copy(journal = Journal(interactions = mapOf(ca to KnowledgeState.OBSERVED, af to KnowledgeState.UNDERSTOOD))))
        val notes = bench(ready, knowing).notes
        assertEquals(listOf("Studying", "Learned"), notes.map { it.stage })
        assertTrue("an observed pairing stays tentative", notes[0].hint.startsWith("Seems"))
        assertEquals(com.tinyblacksmith.core.crafting.Journal.hint(knowing.legacy.journal, content, af), notes[1].hint)
    }

    @Test
    fun aSignaturesCluesAreReadAgainstTheDraftOnlyAsFarAsTheyGo() {
        val forgeSword = Command.Forge(ForgeMode.QUICK, com.tinyblacksmith.core.content.LaunchContent.SWORD, com.tinyblacksmith.core.content.LaunchContent.IRON, com.tinyblacksmith.core.content.LaunchContent.EMBER_RESIN, null, com.tinyblacksmith.core.model.Risk.BALANCED)
        val dawn = SignatureCatalog.forRecipe(forgeSword)!!
        val quick = ForgeDraft(familyId = forgeSword.familyId, coreId = forgeSword.coreId, augmentId = forgeSword.augmentId)
        fun with(vararg r: ClueRung, state: KnowledgeState = KnowledgeState.OBSERVED) =
            fresh.copy(legacy = fresh.legacy.copy(journal = Journal(interactions = mapOf(dawn.journalKey to state), signatureClues = mapOf(dawn.journalKey to r.sumOf { it.bit }))))
        fun note(state: com.tinyblacksmith.core.model.GameState, d: ForgeDraft = quick) = bench(d, state).notes.firstOrNull { it.label == "Signature" }
        assertNull("no clue, nothing told of a hidden recipe", note(fresh))
        note(with(ClueRung.RECIPE))!!.let {
            assertEquals("Incomplete clues", it.stage)
            assertEquals("Your draft matches the clues you have. The rest are still unknown.", it.hint)
        }
        assertEquals("Your draft misses a clue: ${JournalRules.clue(dawn, ClueRung.CATALYST, engine.config)}.", note(with(ClueRung.RECIPE, ClueRung.CATALYST))!!.hint)
        val exact = quick.copy(mode = ForgeMode.ADVANCED, catalystId = dawn.catalystId)
        note(with(*ClueRung.entries.toTypedArray()), exact)!!.let {
            assertEquals("All clues found", it.stage)
            assertEquals("Your draft matches every clue. Quality is decided by the roll, and a signature is never certain.", it.hint)
        }
        note(with(*ClueRung.entries.toTypedArray(), state = KnowledgeState.SIGNATURE_DISCOVERED), exact)!!.let {
            assertEquals(dawn.name, it.stage)
            assertEquals("Your draft is the recipe. Quality is decided by the roll.", it.hint)
        }
        // Before it is found, the signature's name is never on the bench.
        val hidden = listOf(note(with(ClueRung.RECIPE))!!, note(with(*ClueRung.entries.toTypedArray()), exact)!!)
        assertTrue(hidden.none { dawn.name in it.hint || dawn.name in it.stage || '%' in it.hint })
    }

    @Test
    fun switchingToQuickDropsWhatOnlyAdvancedTakesAndKeepsTheRest() {
        val catalyst = content.materials(MaterialCategory.CATALYST).first()
        val adv = ready.copy(mode = ForgeMode.ADVANCED, catalystId = catalyst.id, technique = Technique.TEMPER, heartfire = true, risk = com.tinyblacksmith.core.model.Risk.SAFE, commissionId = CommissionId("c1"))
        val quick = adv.inMode(ForgeMode.QUICK)
        assertEquals(adv.copy(mode = ForgeMode.QUICK, catalystId = null, technique = null, heartfire = false), quick)
        assertEquals("back to Advanced brings nothing dropped back", quick.copy(mode = ForgeMode.ADVANCED), quick.inMode(ForgeMode.ADVANCED))
        assertEquals(adv, adv.inMode(ForgeMode.ADVANCED))
        assertEquals(3, bench(quick).slots.size)
        assertEquals(5, bench(quick.inMode(ForgeMode.ADVANCED)).slots.size)
        assertEquals("Forge · ${engine.config.quickForgeEnergy} energy", bench(quick).action.label)
    }

    @Test
    fun onlyTheChosenOpenCommissionIsBriefedAndItIsNeverCalledAccepted() {
        val buyer = fresh.aliveHeroes().first()
        val offer = Commission(CommissionId("c1"), buyer.id, family.id, 35, 165, fresh.day, fresh.day + 3, CommissionStatus.OFFERED, element = augment.element)
        val other = offer.copy(id = CommissionId("c2"), deadlineDay = fresh.day + 5)
        val state = fresh.copy(commissions = mapOf(offer.id to offer, other.id to other))
        assertNull(bench(ready, state).brief)
        val brief = bench(ready.copy(commissionId = offer.id), state).brief!!
        assertEquals("${buyer.name}'s commission · 165 gold · Due in 3 days", brief.title)
        assertFalse(brief.accepted)
        assertEquals(listOf(true, true, null), brief.asks.map { it.matches })
        assertEquals("Quality 35+", brief.asks.last().text)
        assertEquals("Not accepted yet", brief.status)
        // A commission that has closed is said to be gone, in place: it asks nothing, lists nothing, and the forge stays open.
        for ((status, word) in listOf(CommissionStatus.EXPIRED to "expired", CommissionStatus.COMPLETED to "completed", CommissionStatus.DECLINED to "declined")) {
            val closed = state.copy(commissions = state.commissions + (offer.id to offer.copy(status = status)))
            val gone = bench(ready.copy(commissionId = offer.id), closed)
            assertTrue(gone.brief!!.gone!!, gone.brief!!.gone!!.contains(word))
            assertTrue(gone.brief!!.asks.isEmpty() && gone.brief!!.stock.isEmpty())
            assertEquals("forging never depends on the purpose", bench(ready, closed).action, gone.action)
            assertEquals(bench(ready, closed).command, gone.command)
        }
        val vanished = bench(ready.copy(commissionId = CommissionId("nope")), state).brief!!
        assertEquals("Commission", vanished.title)
        assertTrue(vanished.gone!!.startsWith("It is no longer open."))
    }

    @Test
    fun theBriefWeighsTheShopsOwnStockBeforeAnotherForge() {
        val forged = engine.handle(fresh, forgeSword) as CommandOutcome.Accepted
        val blade = forged.state.weapons.getValue(forged.forgedWeaponId!!)
        val buyer = fresh.aliveHeroes().first()
        val asked = Commission(CommissionId("c1"), buyer.id, blade.familyId, blade.quality, 120, fresh.day, fresh.day + 3, CommissionStatus.ACCEPTED, element = blade.element)
        val withBlade = forged.state.copy(commissions = mapOf(asked.id to asked))
        val draft = ForgeDraft(familyId = blade.familyId, commissionId = asked.id)
        val b = bench(draft, withBlade).brief!!
        assertEquals(listOf(blade.id), b.stock.map { it.weaponId })
        assertTrue(b.stock.single().fits)
        assertEquals("In storage · fits · End Day would hand this over", b.stock.single().line)
        assertNull(b.stockNote)
        assertEquals("Accepted", b.status)
        assertNull("showing a blade reserves nothing", withBlade.weapons.getValue(blade.id).promisedTo)
        // Asked for more than the blade has: it is listed with what it lacks, and nothing fits yet.
        val harder = withBlade.copy(commissions = mapOf(asked.id to asked.copy(minQuality = blade.quality + 1)))
        val short = bench(draft, harder).brief!!
        assertFalse(short.stock.single().fits)
        assertEquals("In storage · quality ${blade.quality}, needs ${blade.quality + 1}", short.stock.single().line)
        assertEquals("Nothing in stock fits yet.", short.stockNote)
        // No blade of the type at all.
        val empty = fresh.copy(weapons = emptyMap(), commissions = mapOf(asked.id to asked))
        assertEquals("No ${content.family(blade.familyId).name.lowercase()} in stock.", bench(draft, empty).brief!!.stockNote)
    }

    @Test
    fun aWantIsBriefedAsNoOrderReadByTheCountersRuleAndGoneWhenTheHeroNoLongerWaits() {
        val hero = stocked.aliveHeroes().first()
        val sword = stocked.listedWeapons().first().familyId
        val want = Want(sword, minPower = 1, budget = 999, sinceDay = stocked.day)
        val state = stocked.copy(heroes = stocked.heroes + (hero.id to hero.copy(want = want)))
        val b = bench(ForgeDraft(familyId = sword, wantHeroId = hero.id), state).brief!!
        assertEquals(Lines.want(state.hero(hero.id), content), b.title)
        assertEquals("Not an order", b.status)
        assertFalse(b.accepted)
        assertNull("a want never names a commission", b.id)
        assertEquals(listOf(true, null), b.asks.map { it.matches })
        assertEquals("Power 1+", b.asks.last().text)
        // Every listed blade is read by the counter's own rule; a stored one is never said to suit them.
        val ctx = ResolutionContext(state, content, engine.config)
        val listed = state.listedWeapons().filter { it.familyId == sword }
        assertTrue(listed.isNotEmpty())
        b.stock.forEach { s ->
            val w = state.weapons.getValue(s.weaponId)
            assertEquals(w.isListed && Market.answersWant(ctx, state.hero(hero.id), w), s.fits)
        }
        // The want gone: lapsed or met, the hero dead.
        val met = state.copy(heroes = state.heroes + (hero.id to hero.copy(want = null)))
        assertEquals("${hero.fullName} is no longer waiting for a weapon. You can keep forging freely.", bench(ForgeDraft(wantHeroId = hero.id), met).brief!!.gone)
        val dead = state.copy(heroes = state.heroes + (hero.id to hero.copy(want = want, fate = HeroFate.DEAD)))
        assertTrue(bench(ForgeDraft(wantHeroId = hero.id), dead).brief!!.gone!!.startsWith("${hero.fullName} has died."))
    }
}
