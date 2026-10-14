package com.example.blacksmithproject

import com.example.blacksmithproject.ui.RecipeSlot
import com.example.blacksmithproject.ui.contractUi
import com.example.blacksmithproject.ui.supplyNotes
import com.example.blacksmithproject.ui.detail.itemDetail
import com.example.blacksmithproject.ui.forgeWorkbench
import com.example.blacksmithproject.ui.guildUi
import com.example.blacksmithproject.ui.openingChooser
import com.example.blacksmithproject.ui.chanceWord
import com.example.blacksmithproject.ui.frequencyWord
import com.example.blacksmithproject.ui.riskExplanation
import com.example.blacksmithproject.ui.shopUi
import com.example.blacksmithproject.ui.supplierMood
import com.tinyblacksmith.core.combat.Posture
import com.tinyblacksmith.core.content.GuildContent
import com.tinyblacksmith.core.content.LaunchContent
import com.tinyblacksmith.core.content.MaterialCategory
import com.tinyblacksmith.core.content.MissionArchetype
import com.tinyblacksmith.core.engine.Command
import com.tinyblacksmith.core.engine.CommandOutcome
import com.tinyblacksmith.core.engine.GameEngine
import com.tinyblacksmith.core.engine.GameError
import com.tinyblacksmith.core.model.CommandId
import com.tinyblacksmith.core.model.ConsequenceKind
import com.tinyblacksmith.core.model.ForgeMode
import com.tinyblacksmith.core.model.GameState
import com.tinyblacksmith.core.model.LegacyProfile
import com.tinyblacksmith.core.model.LegendOrigin
import com.tinyblacksmith.core.model.Rarity
import com.tinyblacksmith.core.model.Risk
import com.tinyblacksmith.core.model.ScheduledConsequence
import com.tinyblacksmith.core.sim.GuildBot
import com.tinyblacksmith.core.sim.GuildPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The screen models of rules 6 against real engine states: the opening chooser, the Heartfire panel of the workbench, the
 * siege preparation on the wall, what is new on a contract card, the supplier's mood and why a weapon is Legendary.
 */
class RunVarietyUiTest {
    private val engine = GameEngine()
    private val content = engine.content
    private val cat = content.guild!!
    private val legend = engine.config.depth.legend

    private fun firstMorning(seed: Long): GameState = engine.newRun(LegacyProfile(), seed, charterId = GuildContent.TOWNS_LAST_HOPE)
    private fun GameState.endDay() = engine.handle(this, Command.EndDay(CommandId("${runId.value}:day$day")))

    /** Mornings of guild runs the simulator's bots play, each as it stands before the bot has planned the day. */
    private fun mornings(policy: GuildPolicy, seeds: LongRange, days: Int = 24): Sequence<GameState> = seeds.asSequence().flatMap { seed ->
        sequence {
            val bot = GuildBot(engine, policy)
            var s = engine.guildRun(seed)
            while (!s.isEnded && s.day <= days) {
                yield(s)
                s = (bot.planDay(s).endDay() as CommandOutcome.Accepted).state
            }
        }
    }

    // ---- the opening chooser ----

    @Test
    fun theChooserShowsEveryOfferedPackageWholeAndOnlyWhileTheOfferStands() {
        val s = firstMorning(7)
        val offers = s.guild!!.openingOffer
        val ui = engine.openingChooser(s)!!
        assertEquals(engine.config.guild.openingsOffered, ui.openings.size)
        assertEquals(offers.map { it.defId }, ui.openings.map { it.id })
        assertEquals(cat.charter(GuildContent.TOWNS_LAST_HOPE)!!.name, ui.charter)
        for ((offer, card) in offers.zip(ui.openings)) {
            val def = cat.opening(offer.defId)!!
            assertEquals(listOf(def.name, def.build, def.pitch, def.plan, def.drawback), listOf(card.name, card.build, card.pitch, card.plan, card.drawback))
            // Who signs, each with the trait this package gives them, in the catalog's own words.
            assertEquals(offer.heroIds, card.signers.map { it.heroId })
            assertEquals(offer.heroIds.map { s.heroes.getValue(it).fullName }, card.signers.map { it.name })
            assertEquals(offer.traitIds.map { id -> cat.trait(id)!!.let { "${it.name}: ${it.description}" } }, card.signers.map { it.trait })
            val relic = content.relic(def.relicId)!!
            assertEquals(relic.name, card.relic)
            assertEquals(relic.description, card.relicDescription)
            def.materials.forEach { (id, n) -> assertTrue(card.brings.toString(), card.brings.any { "$n ${content.material(id).name}" in it }) }
            assertEquals(listOfNotNull(def.materials.takeIf { it.isNotEmpty() }, def.weapon).size, card.brings.size)
        }
        // End Day waits for the choice, with the engine's own error, and the workshop has nothing to choose once it is made.
        assertEquals(GameError.OpeningPending, (s.endDay() as CommandOutcome.Rejected).error)
        // The workshop's own models still build on that morning (the ViewModel builds them under the chooser), with nobody signed yet.
        assertTrue(engine.guildUi(s)!!.roster.isEmpty())
        assertNotNull(engine.shopUi(s))
        val chosen = (engine.handle(s, Command.ChooseOpening(ui.openings[1].id)) as CommandOutcome.Accepted).state
        assertNull(engine.openingChooser(chosen))
        assertEquals(ui.openings[1].id, chosen.guild!!.openingId)
        assertEquals(ui.openings[1].signers.map { it.heroId }, chosen.guild!!.members.map { it.heroId })
        assertTrue(chosen.endDay() is CommandOutcome.Accepted)
        // A classic run never has one.
        assertNull(engine.openingChooser(engine.newRun(LegacyProfile(), 7)))
    }

    @Test
    fun aPackageThatBringsAWeaponSaysWhatItIsAndItsFlaw() {
        val (s, offer) = (1L..60L).asSequence().map(::firstMorning).firstNotNullOf { st -> st.guild!!.openingOffer.firstOrNull { cat.opening(it.defId)!!.weapon != null }?.let { st to it } }
        val def = cat.opening(offer.defId)!!
        val w = def.weapon!!
        val line = engine.openingChooser(s)!!.openings.first { it.id == def.id }.brings.last()
        val flaw = content.affixById.getValue(w.flaw)
        assertEquals("${content.material(w.coreId).name} ${content.family(w.familyId).name.lowercase()} with ${content.material(w.augmentId).name}, quality ${w.quality}, ${flaw.name}", line)
        // The card's costs say the flaw's price in words and the rest from the package's own numbers.
        val card = engine.openingChooser(s)!!.openings.first { it.id == def.id }
        assertEquals(listOfNotNull(def.costNote, def.goldDelta.takeIf { it < 0 }?.let { "Start with ${-it} less gold" }, def.integrityDelta.takeIf { it < 0 }?.let { "The forge starts ${-it} health down" }), card.costs)
    }

    // ---- the Heartfire panel ----

    private val classic = engine.newRun(LegacyProfile(), 42)
    private val family = content.families.first()
    private val core = content.materials(MaterialCategory.CORE).first { (classic.materials[it.id] ?: 0) > 0 }
    private val augment = content.materials(MaterialCategory.AUGMENT).first { (classic.materials[it.id] ?: 0) > 0 }
    private val ready = ForgeDraft(mode = ForgeMode.ADVANCED, familyId = family.id, coreId = core.id, augmentId = augment.id)
    private fun bench(state: GameState, draft: ForgeDraft) = engine.forgeWorkbench(state, draft, engine.shopUi(state).requests)

    @Test
    fun heartfireIsShownHeldAndCanOnlyBeAskedForInAdvancedWithSomeHeld() {
        // None held: the switch is not offered, and a draft that still asks forges without it.
        val none = bench(classic, ready.copy(heartfire = true))
        val h0 = none.heartfire!!
        assertEquals("Heartfire 0 of ${legend.heartfireMax}", h0.held)
        assertEquals("None held.", h0.blocked)
        assertFalse(h0.on)
        assertFalse(none.command!!.heartfire)
        assertEquals("Forge · ${engine.config.advancedForgeEnergy} energy", none.action.label)
        assertTrue(h0.outlook.isEmpty())
        assertTrue(h0.rule, h0.rule.startsWith("Quality alone stops at Epic, and only Starsteel and Moonsteel hold Epic work. In any other metal it stops at Rare."))
        assertTrue(h0.source, listOf("a deep hall cleared", "a taken weapon won back", "a starfall site", "a warlord broken on the wall").all { it in h0.source })

        // One held, but Quick: the reason is the engine's own rule.
        val held = classic.copy(heartfire = 1)
        val quick = bench(held, ready.copy(mode = ForgeMode.QUICK, heartfire = true))
        assertEquals("Forging with Heartfire needs the Advanced forge.", quick.heartfire!!.blocked)
        assertFalse(quick.command!!.heartfire)
        assertEquals(GameError.HeartfireRequiresAdvanced, (engine.handle(held, quick.command!!.copy(heartfire = true)) as CommandOutcome.Rejected).error)

        // Advanced and not asked: offered, off, and nothing is said of an outlook yet.
        val off = bench(held, ready).heartfire!!
        assertNull(off.blocked)
        assertFalse(off.on)
        assertTrue(off.outlook.isEmpty())
    }

    @Test
    fun theHeartfireOutlookSaysWhatIsSpentTheQualityRangeTheChanceAndWhatAMissGives() {
        val held = classic.copy(heartfire = 1)
        val b = bench(held, ready.copy(heartfire = true))
        val h = b.heartfire!!
        val o = engine.heartfireOutlook(held, b.command!!)
        assertTrue(h.on)
        assertTrue(b.command!!.heartfire)
        assertEquals(engine.config.advancedForgeEnergy + legend.heartfireEnergy, o.energy)
        assertEquals("Forge with Heartfire · ${o.energy} energy", b.action.label)
        assertEquals(4, h.outlook.size)
        assertEquals("Uses 1 Heartfire and ${legend.heartfireEnergy} more energy, ${o.energy} in all. Both are spent whether or not it breaks through.", h.outlook[0])
        assertEquals("Quality lands between ${o.qualityLow} and ${o.qualityHigh}, before an exceptional result or a flaw. It can break through only at ${o.floor} or more.", h.outlook[1])
        assertEquals("If it does not break through, the weapon still gets one more property.", h.outlook[3])
        // A cheap first-day recipe is in a metal that does not hold Epic work: the panel says so instead of promising a chance.
        assertTrue("the first metals should not reach Epic", o.qualityHigh < o.floor)
        assertFalse(o.fineMetal)
        assertEquals("Chance to break through: none in this metal.", h.outlook[2])
        assertEquals("Heartfire does not take in this metal. Only Starsteel and Moonsteel hold Epic work.", h.warning)
        // The forge is the one the panel described: the Heartfire and the energy are gone.
        val after = (engine.handle(held, b.command!!) as CommandOutcome.Accepted).state
        assertEquals(0, after.heartfire)
        assertEquals(held.energy - o.energy, after.energy)

        // The best recipe of the catalog reaches the floor: the chance is the engine's, at both ends of the roll.
        val best = content.families.flatMap { f -> content.materials(MaterialCategory.CORE).flatMap { c -> content.materials(MaterialCategory.AUGMENT).map { a -> ready.copy(familyId = f.id, coreId = c.id, augmentId = a.id, heartfire = true) } } }
            .maxBy { engine.heartfireOutlook(held, bench(held, it).command!!).chanceLow }
        val top = bench(held, best)
        val t = engine.heartfireOutlook(held, top.command!!)
        assertTrue("some recipe reaches Epic before luck", t.chanceLow > 0.0)
        assertNull(top.heartfire!!.warning)
        val chance = top.heartfire!!.outlook[2]
        // Owner decision A: the hidden chance is a band word, never a number.
        val low = chanceWord(t.chanceLow); val high = chanceWord(t.chanceHigh)
        assertEquals(if (low == high) "Chance to break through: $low." else "Chance to break through: $low at quality ${t.qualityLow}, $high at quality ${t.qualityHigh}.", chance)
        assertFalse(chance, chance.contains('%'))
        // The confirmation before it is sent: what it spends and what is left, the chance in words, what a miss gives.
        val confirm = top.heartfire!!.confirm
        assertEquals("Spends 1 Heartfire and ${t.energy} energy, whether or not it breaks through. 0 of ${legend.heartfireMax} Heartfire left after.", confirm[0])
        assertEquals(listOf(chance, top.heartfire!!.outlook[3]), confirm.drop(1))
        assertTrue("nothing to confirm before the recipe is whole", bench(held, ForgeDraft(mode = ForgeMode.ADVANCED, heartfire = true)).heartfire!!.confirm.isEmpty())
        // A recipe that is not whole has no outlook to show yet.
        assertEquals(listOf("Choose a weapon, a metal and an augment to see what it would do."), bench(held, ForgeDraft(mode = ForgeMode.ADVANCED, heartfire = true)).heartfire!!.outlook)
        assertEquals(RecipeSlot.WEAPON, bench(held, ForgeDraft(mode = ForgeMode.ADVANCED, heartfire = true)).next)
    }

    @Test
    fun theTempersSayTheNumbersOfTheRules() {
        val cfg = engine.config
        val safe = riskExplanation(Risk.SAFE, cfg)
        assertTrue(safe, safe.startsWith("Quality stays within ${cfg.risk.getValue(Risk.SAFE).rollSpread} of what the recipe gives."))
        assertTrue(riskExplanation(Risk.BALANCED, cfg).startsWith("Quality moves up to ${cfg.qualityRollSpread} either way."))
        val reckless = riskExplanation(Risk.RECKLESS, cfg)
        assertTrue(reckless, reckless.startsWith("Adds one more property. Flaws are ${frequencyWord(cfg.risk.getValue(Risk.RECKLESS).defectChance)}, and a flawed weapon is still usable."))
        assertFalse("hidden odds are words, never a percentage", reckless.contains('%'))
    }

    // ---- the wall, the board, the supplier, the weapon sheet ----

    @Test
    fun theWallSaysWhatTheyPrepareEveryAnswerAndWhatWasSpoiled() {
        val s = mornings(GuildPolicy.PASSIVE, 1L..8L).firstOrNull { engine.wallForecast(it)!!.prep != null }
        assertNotNull("no seed committed a besieger with a preparation", s)
        val def = engine.wallForecast(s!!)!!.prep!!
        val wall = engine.guildUi(s)!!.wall
        val prep = wall.prep!!
        assertEquals(listOf(def.name, def.description, def.sabotageBrief), listOf(prep.name, prep.description, prep.sabotage))
        assertEquals(def.counters, prep.answers)
        assertTrue(prep.answers.size >= 2)
        assertNull(wall.spoiled)

        // The same siege once a party has spoiled it: the preparation is gone from the wall and named as spoiled, once.
        val spoiled = engine.guildUi(s.copy(siege = s.siege!!.copy(prepId = null, prepSpoiled = def.id, sabotaged = 1)))!!.wall
        assertNull(spoiled.prep)
        assertEquals("Your party spoiled their preparation: ${def.name}. It will not come.", spoiled.spoiled)
        assertFalse("Their preparations are spoiled." in spoiled.notes)
        // Before the besieger is committed there is nothing to say of either.
        val early = engine.guildUi(engine.guildRun(7))!!.wall
        assertFalse(early.committed)
        assertNull(early.prep)
        assertNull(early.nemesis)
    }

    @Test
    fun aContractCardSaysItsModifierItsHeartfireAndTheSuppliersStake() {
        val all = mornings(GuildPolicy.NOVICE, 1L..10L).toList()
        fun card(s: GameState, id: String) = engine.guildUi(s)!!.offers.first { it.id == id }

        val modified = all.firstNotNullOfOrNull { s -> s.guild!!.offers.firstOrNull { it.modifierId != null }?.let { s to it } }
        assertNotNull("no contract was posted with a modifier", modified)
        val mod = cat.modifier(modified!!.second.modifierId)!!
        assertEquals("${mod.name}: ${mod.description}", card(modified.first, modified.second.id).modifier)

        val chance = all.firstNotNullOfOrNull { s -> s.guild!!.offers.firstOrNull { cat.mission(it.defId).archetype == MissionArchetype.OPPORTUNITY && it.lapse == null }?.let { s to it } }
        assertNotNull("no opportunity without a stake was posted", chance)
        val starfall = card(chance!!.first, chance.second.id)
        assertEquals("Opportunity", starfall.tag)
        assertNull(starfall.stake)
        if (chance.second.stages.any { it.reward.heartfire > 0 }) assertTrue(starfall.stages.toString(), starfall.stages.any { "Heartfire for the forge" in it.reward })

        val road = all.firstNotNullOfOrNull { s -> s.guild!!.offers.firstOrNull { it.lapse == GuildContent.LAPSE_SUPPLIER }?.let { s to it } }
        assertNotNull("the supplier's road was never posted", road)
        val g = engine.config.guild
        val stake = card(road!!.first, road.second.id)
        assertEquals("Opportunity", stake.tag)
        assertEquals("If the cart is lost, the supplier brings ${g.supplierShakenStock} fewer of every limited material each morning for ${g.supplierDays} days. Leaving the job on the board costs nothing.", stake.stake)
        assertTrue(stake.stages.toString(), stake.stages.any { "the supplier's thanks" in it.reward })
        // An ordinary contract carries none of the three.
        val plain = all.first().let { s -> card(s, s.guild!!.offers.first { it.modifierId == null && it.lapse == null && cat.mission(it.defId).archetype != MissionArchetype.OPPORTUNITY }.id) }
        assertEquals(listOf(null, null, null), listOf(plain.tag, plain.modifier, plain.stake))
    }

    @Test
    fun theSuppliersMoodIsSaidWithItsLastDayAndNotAfterIt() {
        val s = engine.guildRun(7)
        val g = engine.config.guild
        assertNull(engine.supplierMood(s))
        val until = s.day + 3
        fun with(kind: ConsequenceKind, due: Int) = s.copy(consequences = s.consequences + ScheduledConsequence("supplier-test", kind, due))
        assertEquals("The supplier is grateful. ${g.supplierGratefulStock} more of every limited material each morning, until day $until.", engine.supplierMood(with(ConsequenceKind.SUPPLIER_GRATEFUL, until)))
        assertEquals("The supplier is shaken. ${g.supplierShakenStock} fewer of every limited material each morning, until day $until.", engine.supplierMood(with(ConsequenceKind.SUPPLIER_SHAKEN, until)))
        assertNull("a mood that has run out says nothing", engine.supplierMood(with(ConsequenceKind.SUPPLIER_SHAKEN, s.day - 1)))
    }

    @Test
    fun aWeaponSaysWhyItIsLegendaryAndWhatItsDeedsHaveEarned() {
        val forged = engine.handle(classic, Command.Forge(ForgeMode.QUICK, family.id, core.id, augment.id, null, Risk.BALANCED)) as CommandOutcome.Accepted
        val w = forged.state.weapons.getValue(forged.forgedWeaponId!!)
        fun detail(change: (com.tinyblacksmith.core.model.Weapon) -> com.tinyblacksmith.core.model.Weapon) = engine.itemDetail(forged.state.copy(weapons = forged.state.weapons + (w.id to change(w))), w.id)!!
        val plain = detail { it }
        assertNull(plain.legend)
        assertNull(plain.deeds)
        assertEquals("Forged with Heartfire", detail { it.copy(rarity = Rarity.LEGENDARY, legend = LegendOrigin.HEARTFIRE) }.legend)
        val earning = detail { it.copy(renown = 2) }
        assertNull(earning.legend)
        assertEquals("2 of ${legend.renownForLegend}", earning.deeds)
        val earned = detail { it.copy(rarity = Rarity.LEGENDARY, legend = LegendOrigin.DEEDS, renown = legend.renownForLegend) }
        assertEquals("Legendary by its deeds", earned.legend)
        assertEquals("${legend.renownForLegend} of ${legend.renownForLegend}", earned.deeds)
    }

    @Test
    fun theSuppliesSayWhenAFineMetalArrivesAndACardSaysWhoMayGoAndWhatWinningIs() {
        // Day 1: star metal is not on the roads yet, and the row says from which day and where it comes from before that.
        val first = engine.newRun(LegacyProfile(), 3)
        val star = engine.content.material(LaunchContent.STARSTEEL)
        val from = engine.stockedFromDay(star.id)
        assertEquals(listOf("The supplier carries it from day $from. Before that it comes only from dangerous work."), engine.supplyNotes(first, star))
        assertEquals(emptyList<String>(), engine.supplyNotes(first.copy(day = from), star))
        assertEquals(emptyList<String>(), engine.supplyNotes(first, engine.content.material(LaunchContent.OBSIDIAN)))

        // Contracts whose card changes the decision: the party size and the objective are the card's, not the guild's.
        val all = mornings(GuildPolicy.SYNERGY, 1L..12L).toList()
        fun find(id: String) = all.firstNotNullOfOrNull { s -> s.guild!!.offers.firstOrNull { it.modifierId == id }?.let { s to it } }
        val narrow = find("narrow")
        assertNotNull("no narrow way was posted", narrow)
        val sheet = engine.contractUi(narrow!!.first, narrow.second.id, emptyList(), Posture.BALANCED)!!
        assertEquals(2, sheet.partyMax)
        if (narrow.first.guild!!.mission == null) assertEquals("Pick one to 2 members.", sheet.sendBlocked)
        fun card(pair: Pair<GameState, com.tinyblacksmith.core.model.MissionOffer>) = engine.guildUi(pair.first)!!.offers.first { it.id == pair.second.id }
        val ambush = find("ambush")
        assertNotNull("no ford to hold was posted", ambush)
        assertEquals("Last ${cat.modifier("ambush")!!.hold} rounds", card(ambush!!).stages.single().objective)
        val hostage = find("hostage")
        assertNotNull("no held guard was posted", hostage)
        assertEquals("Defeat them all and keep the captive standing", card(hostage!!).stages.single().objective)
        assertTrue(card(hostage).stages.single().reward, "strengthens the militia" in card(hostage).stages.single().reward)
        val kind = find("in_kind")
        assertNotNull("no contract paid in metal was posted", kind)
        assertFalse(card(kind!!).stages.last().reward, "contract gold" in card(kind).stages.last().reward)
    }
}
