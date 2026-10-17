package com.tinyblacksmith.core

import com.tinyblacksmith.core.TestSupport.admitted
import com.tinyblacksmith.core.TestSupport.guildRun
import com.tinyblacksmith.core.TestSupport.withMaterials
import com.tinyblacksmith.core.combat.Stat
import com.tinyblacksmith.core.content.GuildContent
import com.tinyblacksmith.core.content.LaunchContent
import com.tinyblacksmith.core.content.MissionArchetype
import com.tinyblacksmith.core.crafting.Forge
import com.tinyblacksmith.core.crafting.Legends
import com.tinyblacksmith.core.crafting.SignatureCatalog
import com.tinyblacksmith.core.engine.Command
import com.tinyblacksmith.core.engine.CommandOutcome
import com.tinyblacksmith.core.engine.GameEngine
import com.tinyblacksmith.core.engine.GameError
import com.tinyblacksmith.core.engine.Invariants
import com.tinyblacksmith.core.engine.ResolutionContext
import com.tinyblacksmith.core.engine.WorldEvents
import com.tinyblacksmith.core.engine.stateOrThrow
import com.tinyblacksmith.core.guild.GuildOps
import com.tinyblacksmith.core.guild.Missions
import com.tinyblacksmith.core.guild.SiegeFight
import com.tinyblacksmith.core.model.*
import com.tinyblacksmith.core.persistence.SaveCodec
import com.tinyblacksmith.core.rng.RngStream
import com.tinyblacksmith.core.sim.CombatSandbox
import com.tinyblacksmith.core.sim.Simulator
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Rules 6 (docs/RUN_VARIETY_LEDGER.md): Legendary by Heartfire or by deeds, the opening packages, a siege's
 * preparation and its jobs, the supplier's mood, supply thinning, rank scaling, and what a save from 0.8.0 keeps.
 */
class RunVarietyTest {
    private val engine = TestSupport.engine
    private val content = engine.content
    private val config = engine.config
    private val cat = content.guild!!
    private val legend = config.depth.legend

    private fun GameState.send(cmd: Command, e: GameEngine = engine): GameState = e.handle(this, cmd).stateOrThrow()
    private fun GameState.rejected(cmd: Command): GameError = (engine.handle(this, cmd) as CommandOutcome.Rejected).error
    private fun GameState.endDay(e: GameEngine = engine): GameState = send(Command.EndDay(TestSupport.endDayId(this)), e)
    private fun GameState.sound(): GameState = also { assertEquals(emptyList(), Invariants.check(it, config, engine.shelfSlots(it), content)) }
    private fun GameState.edit(f: (ResolutionContext) -> Unit): GameState = ResolutionContext(this, content, config).also(f).toState()
    private fun classic(seed: Long = 1) = engine.newRun(LegacyProfile(), seed).withMaterials()
    private fun guild(seed: Long = 7, charter: String = GuildContent.TOWNS_LAST_HOPE) = engine.guildRun(LegacyProfile(), seed, charter)
    private fun best(risk: Risk = Risk.BALANCED, heartfire: Boolean = false) =
        Command.Forge(if (heartfire) ForgeMode.ADVANCED else ForgeMode.QUICK, LaunchContent.STAFF, LaunchContent.STARSTEEL, LaunchContent.SUN_ASH, null, risk, heartfire = heartfire)

    // ---- the forge ----

    @Test fun `no forge without Heartfire makes a Legendary weapon, on a new account or a maxed one`() {
        for (legacy in listOf(LegacyProfile(), Simulator.maxedLegacy(engine))) for (seed in 1L..150L) {
            var s = engine.newRun(legacy, seed).withMaterials()
            for (risk in Risk.entries) {
                val out = engine.handle(s, best(risk)) as CommandOutcome.Accepted
                val w = out.state.weapon(out.forgedWeaponId!!)
                assertTrue(w.rarity <= Rarity.EPIC, "seed $seed $risk made ${w.rarity} at quality ${w.quality}")
                assertNull(w.legend)
                s = out.state
            }
        }
    }

    @Test fun `a Heartfire forging needs Advanced and one Heartfire, spends it once with the energy shown, and sometimes breaks through`() {
        val s = classic()
        assertEquals(GameError.NoHeartfire, s.rejected(best(heartfire = true)))
        assertEquals(GameError.HeartfireRequiresAdvanced, s.copy(heartfire = 1).rejected(best(heartfire = true).copy(mode = ForgeMode.QUICK)))
        var legends = 0
        var epics = 0
        for (seed in 1L..200L) {
            val before = classic(seed).copy(heartfire = 2)
            val outlook = engine.heartfireOutlook(before, best(heartfire = true))
            assertEquals(config.advancedForgeEnergy + legend.heartfireEnergy, outlook.energy)
            val out = engine.handle(before, best(heartfire = true)) as CommandOutcome.Accepted
            assertEquals(1, out.state.heartfire, "one Heartfire, whatever came of it")
            assertEquals(before.energy - outlook.energy, out.state.energy)
            val w = out.state.weapon(out.forgedWeaponId!!)
            assertTrue(w.quality in 1..100 && w.power >= 1, "every valid forge yields a usable weapon")
            if (w.rarity == Rarity.LEGENDARY) { legends++; assertEquals(LegendOrigin.HEARTFIRE, w.legend); assertTrue(w.quality >= config.epicMin) } else { epics++; assertNull(w.legend) }
        }
        assertTrue(legends in 40..150, "$legends of 200 broke through: bounded, never certain")
        assertTrue(epics > 0)
    }

    @Test fun `under the Epic floor Heartfire cannot break through, and the workbench says so before it is spent`() {
        val weak = Command.Forge(ForgeMode.ADVANCED, LaunchContent.SWORD, LaunchContent.IRON, LaunchContent.EMBER_RESIN, null, Risk.SAFE, heartfire = true)
        val outlook = engine.heartfireOutlook(classic().copy(heartfire = 1), weak)
        assertTrue(outlook.qualityHigh < outlook.floor)
        assertEquals(0.0, outlook.chanceHigh)
        for (seed in 1L..60L) {
            val out = engine.handle(classic(seed).copy(heartfire = 1), weak) as CommandOutcome.Accepted
            assertTrue(out.state.weapon(out.forgedWeaponId!!).rarity < Rarity.LEGENDARY)
        }
    }

    @Test fun `the temper changes what comes out - safe is narrow, reckless has one more property and far more flaws`() {
        val qualities = Risk.entries.associateWith { ArrayList<Int>() }
        val slots = Risk.entries.associateWith { ArrayList<Int>() }
        val flaws = Risk.entries.associateWith { intArrayOf(0) }
        for (seed in 1L..300L) for (risk in Risk.entries) {
            val out = engine.handle(classic(seed), Command.Forge(ForgeMode.QUICK, LaunchContent.SWORD, LaunchContent.SILVER, LaunchContent.STORMGLASS, null, risk)) as CommandOutcome.Accepted
            val w = out.state.weapon(out.forgedWeaponId!!)
            val e = out.events.first { it.type == EventType.WEAPON_FORGED }
            if (e.data["exceptional"] == "false" && e.data["defect"] == "false") qualities.getValue(risk) += w.quality
            slots.getValue(risk) += w.affixes.size
            if (w.flaws.isNotEmpty()) flaws.getValue(risk)[0]++
        }
        fun spread(r: Risk) = qualities.getValue(r).let { it.max() - it.min() }
        assertTrue(spread(Risk.SAFE) <= 2 * config.risk.getValue(Risk.SAFE).rollSpread!!, "safe ${spread(Risk.SAFE)}")
        assertTrue(spread(Risk.BALANCED) > spread(Risk.SAFE))
        assertTrue(slots.getValue(Risk.RECKLESS).average() > slots.getValue(Risk.BALANCED).average() + 0.5)
        assertTrue(flaws.getValue(Risk.RECKLESS)[0] > 3 * flaws.getValue(Risk.BALANCED)[0].coerceAtLeast(1) / 2)
    }

    @Test fun `a hone never makes a Legendary weapon and never unmakes one`() {
        val epic = CombatSandbox.blade("w900", LaunchContent.SWORD, LaunchContent.MOONSTEEL, LaunchContent.FROST_BLOOM, quality = 83).copy(location = WeaponLocation.Storage)
        assertEquals(Rarity.EPIC, epic.copy(rarity = Forge.forgedRarity(83, config)).rarity)
        val s = classic().let { it.copy(weapons = it.weapons + (epic.id to epic.copy(rarity = Rarity.EPIC))) }
        val honed = s.send(Command.Hone(epic.id)).weapon(epic.id)
        assertTrue(honed.quality >= config.legendaryMin, "the hone takes its quality past the old threshold")
        assertEquals(Rarity.EPIC, honed.rarity)
        // A Legendary from 0.8.0 with a quality the hone leaves under the old threshold stays what it is.
        val old = epic.copy(id = WeaponId("w901"), quality = 60, rarity = Rarity.LEGENDARY)
        assertEquals(Rarity.LEGENDARY, s.copy(weapons = s.weapons + (old.id to old)).send(Command.Hone(old.id)).weapon(old.id).rarity)
    }

    @Test fun `deeds make a weapon Legendary once, and more deeds add only to its story`() {
        val blade = CombatSandbox.blade("w900", LaunchContent.SWORD, LaunchContent.IRON, LaunchContent.EMBER_RESIN, quality = 40).copy(location = WeaponLocation.Storage, rarity = Rarity.UNCOMMON)
        val s = classic().let { it.copy(weapons = it.weapons + (blade.id to blade)) }
        val once = s.edit { Legends.deed(it, blade.id, legend.renownDangerous, "A first deed.") }
        assertEquals(Rarity.UNCOMMON, once.weapon(blade.id).rarity)
        assertEquals(legend.renownDangerous, once.weapon(blade.id).renown)
        val twice = once.edit { Legends.deed(it, blade.id, legend.renownDangerous, "A second deed.") }
        val w = twice.weapon(blade.id)
        assertEquals(Rarity.LEGENDARY, w.rarity)
        assertEquals(LegendOrigin.DEEDS, w.legend)
        assertEquals(blade.power + legend.deedPower, w.power)
        assertEquals(blade.quality, w.quality, "its craftsmanship is what it was")
        assertEquals(blade.affixes, w.affixes)
        assertEquals(1, w.history.count { it.kind == Legends.HISTORY_LEGEND })
        assertEquals(1, twice.events.count { it.type == EventType.LEGEND_EARNED })
        val thrice = twice.edit { Legends.deed(it, blade.id, legend.renownCharter, "A third deed.") }
        assertEquals(w.power, thrice.weapon(blade.id).power, "promoted once")
        assertEquals(1, thrice.events.count { it.type == EventType.LEGEND_EARNED })
        assertEquals(3, thrice.weapon(blade.id).history.count { it.kind == Legends.HISTORY_DEED })
    }

    @Test fun `a signature the journal knows is forged most of the time, and an unknown one stays a discovery`() {
        val def = SignatureCatalog.byId.getValue("thornwall")
        val recipe = SignatureCatalog.recipe(def)
        val knows = LegacyProfile(journal = Journal(interactions = mapOf(def.journalKey to KnowledgeState.SIGNATURE_DISCOVERED)))
        fun rate(legacy: LegacyProfile) = (1L..300L).count { seed ->
            val out = engine.handle(engine.newRun(legacy, seed).withMaterials(), recipe) as CommandOutcome.Accepted
            out.state.weapon(out.forgedWeaponId!!).signatureId == def.id
        } / 300.0
        val known = rate(knows)
        val unknown = rate(LegacyProfile())
        assertTrue(known > unknown + 0.2, "known $known unknown $unknown")
        assertTrue(known < 0.9, "knowledge is control, not a certainty: $known")
    }

    @Test fun `overwork is paid the next morning and only then`() {
        var s = classic().copy(energy = 2)
        s = s.send(Command.Forge(ForgeMode.ADVANCED, LaunchContent.SWORD, LaunchContent.IRON, LaunchContent.EMBER_RESIN, null, Risk.SAFE))
        assertEquals(config.advancedForgeEnergy - 2, s.overworkToday)
        val debt = s.overworkToday
        val next = s.endDay()
        val full = next.endDay().energy
        assertEquals(full - debt, next.energy, "the debt survives End Day")
        assertEquals(0, next.overworkToday)
        assertTrue(debt <= config.maxOverworkPerDay && full > 0, "bounded: one day's debt, then a full day again")
    }

    // ---- the opening ----

    @Test fun `the opening is drawn once - the same for a seed, the same after a reload, three packages of the charter`() {
        for (charter in cat.charters) for (seed in 1L..20L) {
            val s = engine.newRun(LegacyProfile(), seed, charterId = charter.id).sound()
            val offer = s.guild!!.openingOffer
            assertEquals(config.guild.openingsOffered, offer.map { it.defId }.toSet().size)
            assertTrue(offer.all { charter.id in cat.opening(it.defId)!!.charters })
            assertEquals(offer, engine.newRun(LegacyProfile(), seed, charterId = charter.id).guild!!.openingOffer)
            val reloaded = SaveCodec.decodeRun(SaveCodec.encodeRun(s))
            assertEquals(offer, reloaded.guild!!.openingOffer)
            assertEquals(s.send(Command.ChooseOpening(offer[1].defId)), reloaded.send(Command.ChooseOpening(offer[1].defId)), "choosing after a reload is the same choice")
            assertTrue(s.guild!!.members.isEmpty() && s.pendingRelicOffer.isEmpty())
        }
        assertTrue((1L..40L).map { engine.newRun(LegacyProfile(), it, charterId = GuildContent.TOWNS_LAST_HOPE).guild!!.openingOffer.map { o -> o.defId }.toSet() }.toSet().size > 3, "seeds differ")
    }

    @Test fun `End Day waits for the opening, every package can be taken and leaves a start that can be played, and it is taken once`() {
        for (charter in cat.charters) for (def in cat.openings.filter { charter.id in it.charters }) {
            val seed = (1L..200L).first { s -> engine.newRun(LegacyProfile(), s, charterId = charter.id).guild!!.openingOffer.any { it.defId == def.id } }
            val fresh = engine.newRun(LegacyProfile(), seed, charterId = charter.id, livingRevision = com.tinyblacksmith.core.model.LivingRevision.LEGACY)
            assertEquals(GameError.OpeningPending, fresh.rejected(Command.EndDay(TestSupport.endDayId(fresh))))
            val s = fresh.send(Command.ChooseOpening(def.id)).sound()
            val g = s.guild!!
            assertEquals(def.id, g.openingId)
            assertTrue(g.openingOffer.isEmpty())
            assertEquals(charter.startClasses, g.members.map { s.hero(it.heroId).classId }, "${def.id}: the charter's two sign")
            def.traits.forEach { (cls, trait) -> g.members.firstOrNull { s.hero(it.heroId).classId == cls }?.let { assertEquals(trait, it.traitId, def.id) } }
            assertEquals(listOf(def.relicId), s.relics.map { it.id })
            def.materials.forEach { (m, n) -> assertTrue((s.materials[m] ?: 0) >= n, "${def.id} brings ${m.value}") }
            def.weapon?.let { w -> assertEquals(listOf(w.flaw), s.storedWeapons().single().flaws) }
            // A first day can be played: a weapon can be forged and loaned, a party can be sent on the free work, and the day ends.
            assertTrue(s.gold >= 0 && s.town.integrity > 0 && s.energy >= config.quickForgeEnergy)
            val forged = engine.handle(s, TestSupport.quickSword()) as CommandOutcome.Accepted
            var day = forged.state.send(Command.LoanWeapon(g.members.first().heroId, forged.forgedWeaponId!!))
            val safe = day.guild!!.offers.first { cat.mission(it.defId).archetype == MissionArchetype.SUPPLY }
            day = day.send(Command.PlanDeployment(safe.id, g.members.map { it.heroId }, com.tinyblacksmith.core.combat.Posture.CAUTIOUS)).endDay().sound()
            assertEquals(2, day.day)
            // Once: the same answer again changes nothing, another is refused.
            assertEquals(s, s.send(Command.ChooseOpening(def.id)))
            assertEquals(GameError.NoOpeningOffer, s.rejected(Command.ChooseOpening(cat.openings.first { it.id != def.id }.id)))
        }
        val fresh = engine.newRun(LegacyProfile(), 7, charterId = GuildContent.TOWNS_LAST_HOPE)
        val notOffered = cat.openings.first { o -> fresh.guild!!.openingOffer.none { it.defId == o.id } }
        assertEquals(GameError.OpeningNotOffered(notOffered.id), fresh.rejected(Command.ChooseOpening(notOffered.id)))
        assertEquals(GameError.NotAGuildRun, classic().rejected(Command.ChooseOpening("overflow")))
    }

    // ---- the siege: preparation, jobs, the nemesis ----

    /** The run as it stands on the morning the siege's jobs are posted: the besieger is committed and its preparation known. */
    private fun committed(seed: Long = 7): GameState {
        var s = guild(seed)
        while (s.siege?.factionId == null) s = s.endDay()
        return s
    }

    @Test fun `the besieger, its line and its preparation are fixed four days ahead, announced, and unchanged by looking or reloading`() {
        val s = committed().sound()
        val scenario = s.siege!!
        assertEquals(config.guild.siegeCommitDays - 1, scenario.siegeDay - s.day, "known from the morning after the commitment")
        val prep = assertNotNull(cat.preparation(scenario.prepId))
        assertEquals(scenario.factionId, prep.factionId)
        assertTrue(s.lastResolution!!.events.any { it.type == EventType.SIEGE_PREPARATION && it.data["prep"] == prep.id && prep.counters.all { c -> c in it.text } }, "announced with its answers")
        val forecast = engine.wallForecast(s)!!
        assertEquals(prep, forecast.prep)
        assertEquals(forecast.plan, engine.wallForecast(SaveCodec.decodeRun(SaveCodec.encodeRun(s)))!!.plan)
        assertEquals(scenario, s.endDay().siege, "another day draws nothing new for this siege")
        assertTrue(cat.preparations.groupBy { it.factionId }.all { it.value.size >= 2 } && cat.preparations.size >= 6)
    }

    @Test fun `each preparation changes the field the way it says, and a spoiled one changes nothing`() {
        val s = committed()
        for (prep in cat.preparations) {
            val staged = s.copy(siege = s.siege!!.copy(factionId = prep.factionId, prepId = prep.id, plan = s.siege!!.plan.map { it.copy(unitId = cat.siegeGrunts.getValue(prep.factionId).first()) }))
            val ctx = ResolutionContext(staged, content, config)
            val plain = staged.siege!!.plan
            val line = SiegeFight.fieldPlan(ctx, staged.siege!!)
            prep.addUnit?.let { u -> assertEquals(1, line.count { it.unitId == u && it.tag != null }, "${prep.id} brings ${u}") }
            prep.gruntUnit?.let { u -> assertTrue(line.drop(1).all { it.unitId == u }, "${prep.id}: the rank and file are ${u}") }
            assertTrue(line.size <= config.guild.fight.maxActorsPerSide && line.first() == plain.first(), "the lead stays, the line fits")
            if (prep.addUnit == null && prep.gruntUnit == null) assertTrue(prep.enemyStats.isNotEmpty() || prep.partyStats.isNotEmpty())
            // Spoiled: the stored plan is what comes.
            SiegeFight.sabotage(ctx)
            assertNull(ctx.siege!!.prepId)
            assertEquals(prep.id, ctx.siege!!.prepSpoiled)
            assertEquals(plain, SiegeFight.fieldPlan(ctx, ctx.siege!!))
            assertEquals(1, ctx.newEvents.count { it.type == EventType.SABOTAGE_DONE })
        }
        // What starts a fight differently does so in the fight itself: grave mist on the wall is Bleed on every defender from the first round.
        val mist = cat.preparation("grave_mist")!!
        assertEquals(2, mist.partyStats[Stat.BLEED])
    }

    @Test fun `an unspoiled preparation costs the wall more than a spoiled one on the same night`() {
        var worse = 0
        var better = 0
        for (seed in 1L..40L) {
            var s = guild(seed)
            while (s.day < s.town.nextSiegeDay) s = s.endDay()
            if (s.siege?.prepId == null) continue
            val spoiled = s.edit { SiegeFight.sabotage(it) }
            val a = s.endDay().lastResolution!!.siege!!
            val b = spoiled.endDay().lastResolution!!.siege!!
            if (a.forgeDamage > b.forgeDamage || a.verdict > b.verdict) worse++
            if (a.forgeDamage < b.forgeDamage || a.verdict < b.verdict) better++
            assertTrue(a.notes.any { it.startsWith("They came with") }, "the report names what came")
            assertTrue(b.notes.any { it.startsWith("Your party had spoiled") })
        }
        assertTrue(worse > 2 * better && worse >= 8, "ignored $worse times worse, $better times better")
    }

    @Test fun `the sabotage and the siege's opportunity are posted together, leave together, and one party cannot do both`() {
        val s = committed()
        val g = s.guild!!
        val sabotage = g.offers.single { cat.mission(it.defId).archetype == MissionArchetype.SABOTAGE }
        val chance = g.offers.single { cat.mission(it.defId).archetype == MissionArchetype.OPPORTUNITY }
        assertEquals(sabotage.expiresDay, chance.expiresDay)
        assertEquals(2, sabotage.days); assertEquals(2, chance.days)
        assertTrue(sabotage.expiresDay + sabotage.days <= s.siege!!.siegeDay, "whoever leaves on the last morning is home for the siege")
        assertEquals(cat.preparation(s.siege!!.prepId)!!.sabotage.objective, sabotage.stages.last().objective.kind, "the second day is the preparation's own")
        assertTrue(sabotage.reason!!.startsWith(cat.preparation(s.siege!!.prepId)!!.sabotageBrief))
        // Take one: by the morning the party is back, the other has left the board.
        var day = s.send(Command.PlanDeployment(sabotage.id, g.members.map { it.heroId }, com.tinyblacksmith.core.combat.Posture.BALANCED))
        while (day.guild!!.mission != null || day.day == s.day) day = day.endDay()
        assertTrue(day.guild!!.offers.none { it.id == chance.id }, "the opportunity is gone when the party is home")
        assertTrue(day.day <= s.siege!!.siegeDay)
        assertTrue(day.guild!!.offers.none { cat.mission(it.defId).archetype == MissionArchetype.SABOTAGE }, "posted once a siege")
    }

    @Test fun `the supplier answers what the guild did - thanks or fright for a few days, visible, then over`() {
        // No siege near: the board posts no supplier's road of its own while this one mood is followed.
        val s = guild().let { it.copy(town = it.town.copy(nextSiegeDay = it.day + 30), siege = SiegeScenario(it.day + 30)) }
        val limited = content.materials.filter { it.dailySupplierStock != null }
        fun stock(st: GameState) = limited.sumOf { st.supplierStock[it.id] ?: 0 }
        val grateful = s.edit { Missions.supplier(it, true, "The supplier got through.") }
        assertTrue(grateful.events.any { it.type == EventType.SUPPLIER_NEWS && "day ${s.day + config.guild.supplierDays}" in it.text })
        // The same run with and without the supplier's thanks, morning by morning (a delayed caravan empties both).
        var plain = s
        var day = grateful
        var richer = 0
        val trace = ArrayList<String>()
        repeat(config.guild.supplierDays) {
            plain = plain.endDay(); day = day.endDay()
            // Thanks deepen what the supplier carries. What the deep road does not bring yet stays at none (rules 7).
            val carried = limited.filter { engine.onTheRoad(day, it.id) }
            val delayed = plain.worldFlags[WorldEvents.FLAG_CARAVAN_DELAYED] == plain.day
            assertEquals(if (delayed) 0 else stock(plain) + carried.size * config.guild.supplierGratefulStock, stock(day), "day ${day.day}")
            assertTrue((limited - carried.toSet()).all { day.supplierStock[it.id] == 0 }, "day ${day.day}: not carried yet")
            if (stock(day) > stock(plain)) richer++
            trace += "day ${day.day}: ${stock(plain)} and ${stock(day)}, mood ${day.consequences.map { it.kind to it.dueDay }}"
        }
        assertTrue(richer >= config.guild.supplierDays - 1, trace.toString())
        plain = plain.endDay(); day = day.endDay()
        assertEquals(stock(plain), stock(day), "bounded")
        assertTrue(day.consequences.none { it.kind == ConsequenceKind.SUPPLIER_GRATEFUL })
        val shaken = s.edit { Missions.supplier(it, false, "The supplier's cart was lost.") }.endDay()
        assertTrue(stock(shaken) < stock(s.endDay()))
        // Nobody went: the card leaves the board unanswered and that costs nothing. Only a lost cart frightens the supplier.
        val posted = s.edit { c -> c.guild = c.guild!!.copy(offers = c.guild!!.offers + Missions.generate(c, cat.mission(GuildContent.SUPPLIER_ROAD), c.factions.keys.first(), c.rng(RngStream.GUILD), "t1", expires = c.day, lapse = GuildContent.LAPSE_SUPPLIER)) }
        assertTrue(posted.guild!!.offers.any { it.id == "t1" && it.lapse == GuildContent.LAPSE_SUPPLIER })
        val left = posted.endDay()
        assertTrue(left.guild!!.offers.none { it.id == "t1" }, "the card is gone")
        assertTrue(left.consequences.none { it.kind == ConsequenceKind.SUPPLIER_SHAKEN || it.kind == ConsequenceKind.SUPPLIER_GRATEFUL })
        assertTrue(left.events.none { it.type == EventType.SUPPLIER_NEWS })
        assertEquals(s.endDay().supplierStock, left.supplierStock, "the supplier brings what it would have")
    }

    @Test fun `a supply road worked often carries less, says so, and recovers`() {
        val s = guild()
        fun gold(st: GameState, worked: List<Int>): Pair<Int, String?> = st.copy(guild = st.guild!!.copy(supplyDays = worked)).let { g ->
            val ctx = ResolutionContext(g, content, config)
            Missions.generate(ctx, cat.mission(GuildContent.SUPPLY_RUN), ctx.factions.keys.first(), com.tinyblacksmith.core.rng.Rng(99), "t").let { it.stages.first().reward.gold to it.reason }
        }
        val (fresh, noReason) = gold(s, emptyList())
        val (once, said) = gold(s, listOf(s.day))
        val (thrice, _) = gold(s, listOf(s.day, s.day, s.day))
        assertNull(noReason)
        assertTrue(said!!.contains("pays ${config.guild.supplyThinPercent[1]}% of the usual gold"), said)
        assertTrue(fresh > once && once > thrice && thrice > 0, "$fresh $once $thrice")
        assertEquals(fresh, gold(s.copy(day = s.day + config.guild.supplyMemoryDays), listOf(s.day)).first, "the road recovers")
        // It does not grow with the day either: a supply run on day 20 pays what one on day 1 pays.
        assertEquals(fresh, gold(s.copy(day = 20), emptyList()).first)
    }

    @Test fun `a rank makes every enemy stronger by the share its card names`() {
        val s = guild()
        val ctx0 = ResolutionContext(s, content, config)
        for (rank in cat.ranks) {
            val ranked = ResolutionContext(s.copy(guild = s.guild!!.copy(rank = rank.rank)), content, config)
            for (base in listOf(165, 200, 260)) assertEquals(com.tinyblacksmith.core.combat.Fight.pct(base, 100 + rank.enemyPercent), GuildOps.ranked(ranked, base))
            assertEquals(200, GuildOps.ranked(ctx0, 200))
            val plain = Missions.generate(ctx0, cat.mission(GuildContent.HUNT), ctx0.factions.keys.first(), com.tinyblacksmith.core.rng.Rng(5), "a").stages.first().enemies.first().percent
            val harder = Missions.generate(ranked, cat.mission(GuildContent.HUNT), ranked.factions.keys.first(), com.tinyblacksmith.core.rng.Rng(5), "a").stages.first().enemies.first().percent
            assertTrue(Math.abs(harder * 100.0 / plain - (100 + rank.enemyPercent)) < 1.0, "rank ${rank.rank}: ${harder * 100.0 / plain}")
        }
    }

    @Test fun `a taken weapon comes back to the wall in its bearer's hands, and is home again when the bearer falls on a wall that holds`() {
        val easy = GameEngine(config = config.copy(guild = config.guild.copy(siegePercentBase = 20, siegePercentPerDay = 0.0, siegePercentPerPressure = 0.0)))
        var s = guild()
        while (s.day < s.town.nextSiegeDay) s = s.endDay()
        val faction = s.siege!!.factionId!!
        val taken = CombatSandbox.blade("w900", LaunchContent.SWORD, LaunchContent.BRONZE, LaunchContent.FROST_BLOOM, quality = 55).copy(location = WeaponLocation.Lost(s.day - 1, GuildOps.LOST_TAKEN))
        s = s.copy(weapons = s.weapons + (taken.id to taken), heroes = s.heroes.mapValues { (id, h) -> if (s.guild!!.isMember(id)) h.copy(health = 100, level = config.heroMaxLevel) else h },
            guild = s.guild!!.copy(nemesis = Nemesis("Grask", faction, taken.id, s.day - 1, cat.siegeElites.getValue(faction))))
        val forecast = engine.wallForecast(s)!!
        assertEquals("Grask", forecast.nemesis?.name)
        assertEquals(1, forecast.plan.count { it.tag == "nemesis" })
        val after = s.endDay(easy)
        val report = after.lastResolution!!.siege!!
        assertTrue(report.verdict != SiegeVerdict.BREACHED)
        assertEquals(WeaponLocation.Storage, after.weapon(taken.id).location)
        assertNull(after.guild!!.nemesis)
        assertTrue(report.notes.any { "Grask" in it && taken.name in it })
        assertEquals(1, after.weapon(taken.id).history.count { it.kind == "RECOVERED" })
        assertEquals(emptyList(), Invariants.check(after, easy.config, easy.shelfSlots(after), content))
    }

    // ---- a save from 0.8.0 ----

    private fun v6(name: String) = javaClass.classLoader.getResource("saves/v6_$name.json")!!.readText()

    @Test fun `a 0_8_0 save keeps every weapon as it was, is admitted, and plays on without an opening`() {
        for (name in listOf("guild_siege_eve", "guild_captive", "guild_charter_eve", "guild_checkpoint", "guild_stormwell", "guild_day_one")) {
            val loaded = SaveCodec.decodeRun(v6(name))
            assertEquals(5, loaded.rulesVersion, name)
            val legends = loaded.weapons.values.filter { it.rarity == Rarity.LEGENDARY }
            assertTrue(legends.all { it.legend == null && it.renown == 0 })
            assertEquals(0, loaded.heartfire)
            var s = loaded.admitted()
            assertEquals(loaded.weapons, s.weapons, "$name: rarity, stats, signatures, titles and histories are untouched")
            assertEquals(loaded.siege, s.siege, "$name: a stored siege is not redrawn")
            assertEquals(loaded.guild!!.offers, s.guild!!.offers, "$name: posted contracts are as posted")
            assertTrue(s.guild!!.openingOffer.isEmpty() && s.guild!!.openingId == null, "$name: no opening is asked of a run that began without one")
            val before = s.weapons.values.filter { it.rarity == Rarity.LEGENDARY }.map { it.id }
            repeat(3) { if (!s.isEnded) { s.guild?.mission?.let { m -> if (Missions.atCheckpoint(m) && m.choice == null) s = s.send(Command.ChooseMissionCheckpoint(m.id, CheckpointChoice.RETURN)) }; s = s.endDay() } }
            assertEquals(emptyList(), Invariants.check(s, config, engine.shelfSlots(s), content), name)
            assertTrue(before.all { s.weapons[it]?.rarity != null && s.weapons.getValue(it).rarity == Rarity.LEGENDARY }, "$name: a Legendary weapon stays one")
        }
        assertTrue(SaveCodec.decodeRun(v6("guild_captive")).weapons.values.any { it.rarity == Rarity.LEGENDARY }, "the fixture holds a Legendary weapon from the old rules")
    }

    @Test fun `a siege stored before preparations existed is fought as it was stored`() {
        val eve = SaveCodec.decodeRun(v6("guild_siege_eve")).admitted()
        val stored = eve.siege!!
        assertNull(stored.prepId)
        assertTrue(stored.plan.isNotEmpty())
        assertEquals(stored.plan, engine.wallForecast(eve)!!.plan)
        val night = eve.endDay().lastResolution!!.siege!!
        assertTrue(night.notes.none { it.startsWith("They came with") })
        assertNotEquals(0, night.notes.size)
    }
}
