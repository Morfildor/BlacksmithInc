package com.tinyblacksmith.core

import com.tinyblacksmith.core.TestSupport.guildRun
import com.tinyblacksmith.core.TestSupport.withMaterials
import com.tinyblacksmith.core.combat.ObjectiveKind
import com.tinyblacksmith.core.combat.Posture
import com.tinyblacksmith.core.combat.Side
import com.tinyblacksmith.core.content.GuildContent
import com.tinyblacksmith.core.content.LaunchContent
import com.tinyblacksmith.core.content.MaterialCategory
import com.tinyblacksmith.core.content.MissionArchetype
import com.tinyblacksmith.core.crafting.Forge
import com.tinyblacksmith.core.engine.Command
import com.tinyblacksmith.core.engine.CommandOutcome
import com.tinyblacksmith.core.engine.GameEngine
import com.tinyblacksmith.core.engine.GameError
import com.tinyblacksmith.core.engine.ResolutionContext
import com.tinyblacksmith.core.engine.stateOrThrow
import com.tinyblacksmith.core.guild.Missions
import com.tinyblacksmith.core.model.*
import com.tinyblacksmith.core.rng.RngStream
import com.tinyblacksmith.core.sim.GuildBot
import com.tinyblacksmith.core.sim.GuildPolicy
import com.tinyblacksmith.core.sim.Policy
import com.tinyblacksmith.core.sim.SimulationDriver
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The second pass of rules 6 (docs/RUN_VARIETY_LEDGER.md, rulings R1 to R3 and gates G7, G10, G11, G13): Epic needs a
 * fine metal and the supplier brings those later, a rescue is never walked into alone and takes nobody else, contract
 * modifiers that change the decision, and what a classic run does not have.
 */
class RunVarietySecondPassTest {
    private val engine = TestSupport.engine
    private val content = engine.content
    private val config = engine.config
    private val cat = content.guild!!
    private val legend = config.depth.legend

    private fun GameState.send(cmd: Command): GameState = engine.handle(this, cmd).stateOrThrow()
    private fun GameState.rejected(cmd: Command): GameError = (engine.handle(this, cmd) as CommandOutcome.Rejected).error
    private fun GameState.endDay(): GameState = send(Command.EndDay(TestSupport.endDayId(this)))
    private fun GameState.edit(f: (ResolutionContext) -> Unit): GameState = ResolutionContext(this, content, config).also(f).toState()
    private fun classic(seed: Long = 1) = engine.newRun(LegacyProfile(), seed)
    private fun guild(seed: Long = 7, charter: String = GuildContent.TOWNS_LAST_HOPE) = engine.guildRun(LegacyProfile(), seed, charter)
    private fun forge(core: MaterialId, heartfire: Boolean = false) =
        Command.Forge(if (heartfire) ForgeMode.ADVANCED else ForgeMode.QUICK, LaunchContent.STAFF, core, LaunchContent.SUN_ASH, null, Risk.RECKLESS, heartfire = heartfire)

    // ---- Epic needs a fine metal ----

    @Test fun `only a fine metal holds Epic work, whatever the quality`() {
        var epicInStarsteel = 0
        for (seed in 1L..120L) {
            var s = classic(seed).withMaterials()
            for (core in content.materials(MaterialCategory.CORE)) {
                val before = s.weapons.keys
                s = s.copy(energy = 10).send(forge(core.id))
                val w = s.weapons.values.single { it.id !in before }
                if (core.tier < legend.fineCoreTier) assertTrue(w.rarity <= Rarity.RARE, "${core.name} at quality ${w.quality} is ${w.rarity}")
                else if (w.quality >= config.epicMin) { assertEquals(Rarity.EPIC, w.rarity); epicInStarsteel++ }
            }
        }
        assertTrue(epicInStarsteel > 0)
        // The rule itself, at the edges: a perfect piece of Obsidian is Rare, the same quality in Starsteel is Epic.
        assertEquals(Rarity.RARE, Forge.forgedRarity(100, config, content.material(LaunchContent.OBSIDIAN).tier))
        assertEquals(Rarity.EPIC, Forge.forgedRarity(100, config, content.material(LaunchContent.STARSTEEL).tier))
        assertEquals(Rarity.RARE, Forge.forgedRarity(config.epicMin - 1, config, content.material(LaunchContent.MOONSTEEL).tier))
    }

    @Test fun `the hone never promotes a lesser metal to Epic and never takes a rarity away`() {
        for (seed in 1L..60L) {
            var s = classic(seed).withMaterials().copy(energy = 10).send(forge(LaunchContent.SILVER))
            val w = s.weapons.values.single()
            s = s.send(Command.Hone(w.id))
            assertTrue(s.weapons.getValue(w.id).rarity <= Rarity.RARE, "silver honed to ${s.weapons.getValue(w.id).quality}")
        }
        // A weapon from an older save: Epic in silver. It stays what it was when it is honed.
        var s = classic().withMaterials().copy(energy = 10).send(forge(LaunchContent.SILVER))
        val w = s.weapons.values.single()
        s = s.copy(weapons = mapOf(w.id to w.copy(quality = 72, rarity = Rarity.EPIC))).send(Command.Hone(w.id))
        assertEquals(Rarity.EPIC, s.weapons.getValue(w.id).rarity)
    }

    @Test fun `Heartfire does not take in a lesser metal, and the workbench says so before it is spent`() {
        val s0 = classic().withMaterials().copy(heartfire = 3)
        val silver = engine.heartfireOutlook(s0, forge(LaunchContent.SILVER, heartfire = true))
        assertFalse(silver.fineMetal)
        assertEquals(0.0, silver.chanceHigh)
        assertTrue(engine.heartfireOutlook(s0, forge(LaunchContent.STARSTEEL, heartfire = true)).let { it.fineMetal && it.chanceHigh > 0.0 })
        for (seed in 1L..80L) {
            val s = classic(seed).withMaterials().copy(heartfire = 1, energy = 10).send(forge(LaunchContent.OBSIDIAN, heartfire = true))
            assertTrue(s.weapons.values.single().let { it.rarity <= Rarity.RARE && it.legend == null })
        }
    }

    @Test fun `the supplier carries Starsteel from day 6 and Moonsteel from day 11, in a classic run and a guild run`() {
        val star = legend.coreTierFromDay.getValue(content.material(LaunchContent.STARSTEEL).tier)
        val moon = legend.coreTierFromDay.getValue(content.material(LaunchContent.MOONSTEEL).tier)
        assertEquals(star, engine.stockedFromDay(LaunchContent.STARSTEEL))
        assertEquals(1, engine.stockedFromDay(LaunchContent.OBSIDIAN))
        assertEquals(1, engine.stockedFromDay(LaunchContent.SUN_ASH))
        for (start in listOf(classic(3), guild(3).let { g -> g.guild!!.openingOffer.firstOrNull()?.let { g.send(Command.ChooseOpening(it.defId)) } ?: g })) {
            var s = start.copy(gold = 5000)
            assertEquals(GameError.SupplierOutOfStock(LaunchContent.STARSTEEL), s.rejected(Command.BuyMaterial(LaunchContent.STARSTEEL)))
            while (!s.isEnded && s.day < moon) {
                val delayed = s.worldFlags[com.tinyblacksmith.core.engine.WorldEvents.FLAG_CARAVAN_DELAYED] == s.day
                if (s.day < star) assertEquals(0, s.supplierStock[LaunchContent.STARSTEEL], "day ${s.day}")
                else if (!delayed) assertTrue((s.supplierStock[LaunchContent.STARSTEEL] ?: 0) >= 1, "day ${s.day}")
                // An ore merchant may bring a metal the supplier already carries, never one it does not.
                assertEquals(0, s.supplierStock[LaunchContent.MOONSTEEL], "day ${s.day}")
                assertTrue((s.supplierStock[LaunchContent.OBSIDIAN] ?: 0) >= 1 || delayed, "obsidian on day ${s.day}")
                s = s.endDay()
            }
        }
    }

    // ---- rescue ----

    private fun withRescue(seed: Long): Triple<GameState, MissionOffer, List<HeroId>> {
        var s = guild(seed)
        s.guild!!.openingOffer.firstOrNull()?.let { s = s.send(Command.ChooseOpening(it.defId)) }
        val party = s.guild!!.members.map { it.heroId }
        val held = party.last()
        s = s.edit { c ->
            val g = c.guild!!
            val faction = c.factions.keys.first()
            c.guild = g.copy(captives = g.captives + Captive(held, faction, c.day, c.day + config.guild.captiveDays, null, GuildContent.HUNT),
                members = g.members.map { if (it.heroId == held) it.copy(status = MemberStatus.CAPTURED) else it })
            c.guild = c.guild!!.copy(offers = c.guild!!.offers + Missions.generate(c, cat.mission(GuildContent.RESCUE), faction, c.rng(RngStream.GUILD), "t-rescue", hero = held))
        }
        return Triple(s, s.guild!!.offers.first { it.id == "t-rescue" }, party - held)
    }

    @Test fun `a rescue is never walked into alone - the watch fills every empty place, and no other contract gets it`() {
        val (s, offer, free) = withRescue(7)
        val alone = assertNotNull(engine.missionSetup(s, offer.id, free.take(1), Posture.BALANCED))
        val fighters = alone.party.filter { it.side == Side.PARTY }
        assertEquals(config.guild.partyMax, fighters.size)
        assertEquals(config.guild.partyMax - 1, fighters.count { it.key.startsWith("watch") })
        assertTrue(fighters.first().key.startsWith("watch"), "the watch stands in front")
        // The way out: the same, and the captive behind them.
        val out = assertNotNull(engine.missionSetup(s, offer.id, free.take(1), Posture.BALANCED, stage = 1))
        assertEquals(config.guild.partyMax + 1, out.party.size)
        // A hunt is the party's own business.
        val hunt = s.edit { c -> c.guild = c.guild!!.copy(offers = c.guild!!.offers + Missions.generate(c, cat.mission(GuildContent.HUNT), c.factions.keys.first(), c.rng(RngStream.GUILD), "t-hunt")) }
        assertEquals(1, assertNotNull(engine.missionSetup(hunt, "t-hunt", free.take(1), Posture.BALANCED)).party.size)
    }

    @Test fun `a lost rescue takes nobody else, and one member with the watch usually wins it`() {
        assertTrue(cat.mission(GuildContent.RESCUE).stages.all { it.danger == Danger.SAFE })
        var won = 0; var tried = 0
        for (seed in 1L..40L) {
            val (s0, offer, free) = withRescue(seed)
            var s = s0.send(Command.PlanDeployment(offer.id, free.take(1), Posture.BALANCED))
            repeat(2) { s = s.endDay() }
            tried++
            // Whatever happened, the guild holds one captive at most: the one it went for, or nobody.
            assertTrue(s.guild!!.captives.size <= 1, "seed $seed")
            assertTrue(s.guild!!.members.count { it.status == MemberStatus.CAPTURED } <= 1, "seed $seed")
            if (s.guild!!.captives.isEmpty()) won++
        }
        assertTrue(won * 10 >= tried * 8, "one member and the watch won $won of $tried")
    }

    // ---- contract modifiers that change the decision ----

    private fun posted(s: GameState, defId: String, modifierId: String, id: String = "t-mod"): GameState =
        s.edit { c -> c.guild = c.guild!!.copy(offers = c.guild!!.offers + Missions.generate(c, cat.mission(defId), c.factions.keys.first(), c.rng(RngStream.GUILD), id, modifier = cat.modifier(modifierId))) }

    @Test fun `four modifiers change what winning is, who may go or what is paid`() {
        var s = guild(11)
        s.guild!!.openingOffer.firstOrNull()?.let { s = s.send(Command.ChooseOpening(it.defId)) }
        val plain = posted(s, GuildContent.HUNT, "guarded", "t-plain").guild!!.offers.first { it.id == "t-plain" }
        assertEquals(ObjectiveKind.DEFEAT, plain.stages.single().objective.kind)

        val ambush = posted(s, GuildContent.HUNT, "ambush").guild!!.offers.first { it.id == "t-mod" }.stages.single()
        assertEquals(ObjectiveKind.HOLD, ambush.objective.kind)
        assertEquals(cat.modifier("ambush")!!.hold, ambush.objective.rounds)

        val hostage = posted(s, GuildContent.HUNT, "hostage").guild!!.offers.first { it.id == "t-mod" }.stages.single()
        assertEquals("captive", hostage.protectUnitId)
        assertEquals("protected", hostage.objective.protect)
        assertEquals(cat.modifier("hostage")!!.militia, hostage.reward.militia)

        val kind = posted(s, GuildContent.HUNT, "in_kind").guild!!.offers.first { it.id == "t-mod" }.stages.single().reward
        assertEquals(0, kind.gold)
        assertEquals(listOf(5), kind.materials.keys.map { content.material(it).tier })

        val narrow = posted(s.copy(gold = 500), GuildContent.HUNT, "narrow")
        assertEquals(2, engine.partyMax(narrow, "t-mod"))
        assertEquals(config.guild.partyMax, engine.partyMax(narrow, narrow.guild!!.offers.first { it.modifierId == null }.id))
        val three = narrow.guild!!.candidates.fold(narrow) { acc, c -> if (acc.guild!!.members.size < 3) acc.send(Command.RecruitHero(c.heroId)) else acc }.guild!!.members.map { it.heroId }
        val ready = narrow.guild!!.candidates.fold(narrow) { acc, c -> if (acc.guild!!.members.size < 3) acc.send(Command.RecruitHero(c.heroId)) else acc }
        assertEquals(3, three.size)
        assertTrue(ready.rejected(Command.PlanDeployment("t-mod", three, Posture.BALANCED)) is GameError.PartyInvalid)
        assertEquals(2, ready.send(Command.PlanDeployment("t-mod", three.take(2), Posture.BALANCED)).guild!!.planned!!.heroIds.size)

        // The same smith, the same morning, the same contract: the card alone changes who is sent.
        fun sent(modifierId: String): Int {
            val board = posted(ready, GuildContent.HUNT, modifierId, "t-bot").edit { c -> c.guild = c.guild!!.copy(offers = c.guild!!.offers.filter { it.id == "t-bot" || cat.mission(it.defId).archetype == MissionArchetype.EMERGENCY }) }
            return GuildBot(engine, GuildPolicy.SYNERGY).planDay(board).guild!!.planned!!.let { p -> assertEquals("t-bot", p.offerId); p.heroIds.size }
        }
        assertEquals(3, sent("guarded"))
        assertEquals(2, sent("narrow"))
    }

    @Test fun `a board only draws a modifier that fits the contract it is on`() {
        val seen = HashSet<String>()
        for (seed in 1L..25L) {
            var s = guild(seed, GuildContent.LICENSED_BAD_IDEAS)
            s.guild!!.openingOffer.firstOrNull()?.let { s = s.send(Command.ChooseOpening(it.defId)) }
            repeat(12) {
                if (s.isEnded) return@repeat
                for (o in s.guild!!.offers) cat.modifier(o.modifierId)?.let { m ->
                    seen += m.id
                    assertTrue(m.archetypes.isEmpty() || cat.mission(o.defId).archetype in m.archetypes, "${m.id} on ${o.defId}")
                }
                s = s.endDay()
            }
        }
        assertEquals(cat.modifiers.map { it.id }.toSet(), seen, "every modifier is reachable")
        assertTrue(cat.modifiers.count { it.hold > 0 || it.protectUnitId != null || it.partyMax != null || it.payment != null } >= 3)
    }

    // ---- charters and packages ----

    @Test fun `Licensed Bad Ideas can sign a Guardian and a Warden on the first morning, and no opening's drawback is militia`() {
        for (seed in 1L..20L) {
            val s = guild(seed, GuildContent.LICENSED_BAD_IDEAS)
            val classes = s.guild!!.candidates.map { s.heroes.getValue(it.heroId).classId }
            assertTrue(LaunchContent.GUARDIAN in classes && LaunchContent.WARDEN in classes, "seed $seed: $classes")
        }
        assertTrue(cat.openings.all { it.militiaDelta == 0 })
        assertTrue(cat.openings.all { it.goldDelta < 0 || it.integrityDelta < 0 || it.weapon != null })
    }

    // ---- what a classic run does not have ----

    @Test fun `a classic run has no opening, no preparation and no siege job, and its smith can spend Heartfire`() {
        var s = classic(5)
        repeat(12) {
            assertNull(s.guild)
            assertNull(s.siege?.prepId)
            assertTrue(s.events.none { it.type == EventType.OPENING_CHOSEN || it.type == EventType.SIEGE_PREPARATION || it.type == EventType.SUPPLIER_NEWS })
            assertTrue(engine.handle(s, Command.ChooseOpening("overflow")) is CommandOutcome.Rejected)
            if (!s.isEnded) s = s.endDay()
        }
        var forgings = 0
        for (seed in 1L..12L) {
            val driver = SimulationDriver(GameEngine(), maxDays = 40, spendHeartfire = true)
            val end = driver.playRun(LegacyProfile(), seed, Policy.EXPERT).second
            forgings += driver.heartfireForges
            assertTrue(driver.heartfireBreakthroughs <= driver.heartfireForges)
            assertEquals(driver.heartfireBreakthroughs, end.weapons.values.count { it.legend == LegendOrigin.HEARTFIRE }, "seed $seed")
            assertTrue(end.weapons.values.none { it.rarity == Rarity.LEGENDARY && it.legend == null && it.legendKey == null }, "seed $seed")
            assertTrue(end.weapons.values.filter { it.legend == LegendOrigin.HEARTFIRE }.all { content.material(it.coreId).tier >= legend.fineCoreTier })
        }
        assertTrue(forgings > 0, "no classic smith spent Heartfire in twelve runs")
    }

    // ---- the scenario saves the device pass loads ----

    @Test fun `the two Heartfire saves do what their cards say when the forging is made`() {
        fun made(id: String): Pair<GameState, Weapon> {
            val s = ScenarioSaves.all.single { it.id == id }.state
            val after = s.send(RunVarietyScenarios.FORGING)
            assertEquals(s.heartfire - 1, after.heartfire)
            return after to after.weapons.values.single { it.id !in s.weapons }
        }
        val (_, legendary) = made("heartfire_breakthrough")
        assertEquals(Rarity.LEGENDARY, legendary.rarity)
        assertEquals(LegendOrigin.HEARTFIRE, legendary.legend)
        val (_, missed) = made("heartfire_miss")
        assertEquals(Rarity.EPIC, missed.rarity)
        assertNull(missed.legend)
        assertTrue(missed.quality >= config.epicMin)

        val siege = ScenarioSaves.all.single { it.id == "siege_preparation" }.state
        assertEquals(siege.day, siege.town.nextSiegeDay)
        assertNotNull(siege.siege?.prepId, "the preparation stands")
        assertTrue(siege.endDay().guild!!.lastSiege!!.notes.isNotEmpty(), "the siege card says what decided the night")

        val stock = { s: GameState -> s.supplierStock.getValue(LaunchContent.SILVER) }
        val grateful = ScenarioSaves.all.single { it.id == "supplier_grateful" }.state.endDay()
        val shaken = ScenarioSaves.all.single { it.id == "supplier_shaken" }.state.endDay()
        assertEquals(content.material(LaunchContent.SILVER).dailySupplierStock!! + config.guild.supplierGratefulStock, stock(grateful))
        assertEquals(content.material(LaunchContent.SILVER).dailySupplierStock!! - config.guild.supplierShakenStock, stock(shaken))

        val legends = ScenarioSaves.all.single { it.id == "legend_lines" }.state.storedWeapons()
        assertEquals(1, legends.count { it.legend == LegendOrigin.HEARTFIRE })
        assertEquals(1, legends.count { it.legend == LegendOrigin.DEEDS })
        assertEquals(1, legends.count { it.legend == null && it.renown in 1 until legend.renownForLegend })
    }
}
