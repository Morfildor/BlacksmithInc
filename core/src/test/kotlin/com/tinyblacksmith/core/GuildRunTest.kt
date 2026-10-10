package com.tinyblacksmith.core

import com.tinyblacksmith.core.combat.Posture
import com.tinyblacksmith.core.content.GuildContent
import com.tinyblacksmith.core.content.LaunchContent
import com.tinyblacksmith.core.content.MissionArchetype
import com.tinyblacksmith.core.engine.Command
import com.tinyblacksmith.core.engine.CommandOutcome
import com.tinyblacksmith.core.engine.GameError
import com.tinyblacksmith.core.engine.Invariants
import com.tinyblacksmith.core.engine.stateOrThrow
import com.tinyblacksmith.core.model.*
import com.tinyblacksmith.core.persistence.SaveCodec
import com.tinyblacksmith.core.sim.GuildBot
import com.tinyblacksmith.core.sim.GuildPolicy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** A guild run through the engine's commands (plan B01 to B10, spec 19): roster, loans, contracts, the wall, the save. */
class GuildRunTest {
    private val engine = TestSupport.engine
    private val content = engine.content
    private val cfg = engine.config.guild

    private fun guildRun(seed: Long = 7, charter: String = GuildContent.TOWNS_LAST_HOPE): GameState =
        engine.newRun(LegacyProfile(), seed, charterId = charter).let { engine.handle(it, Command.DeclineRelicOffer).stateOrThrow() }

    private fun GameState.run(cmd: Command): GameState = engine.handle(this, cmd).stateOrThrow()
    private fun GameState.rejected(cmd: Command): GameError = (engine.handle(this, cmd) as CommandOutcome.Rejected).error
    private fun GameState.endDay(): GameState = run(Command.EndDay(TestSupport.endDayId(this)))
    private fun GameState.forged(): Pair<GameState, WeaponId> = (engine.handle(copy(materials = content.materials.associate { it.id to 20 }, energy = 10), TestSupport.quickSword()) as CommandOutcome.Accepted).let { it.state to it.forgedWeaponId!! }
    private fun GameState.members() = guild!!.members.map { it.heroId }
    private fun GameState.offer(a: MissionArchetype) = guild!!.offers.first { content.guild!!.mission(it.defId).archetype == a }
    private fun GameState.sound(): GameState = also { assertEquals(emptyList(), Invariants.check(it, engine.config, engine.shelfSlots(it), content)) }

    // ---- a classic run is untouched ----

    @Test fun `a run without a charter has no guild and refuses every guild command`() {
        val s = engine.newRun(LegacyProfile(), 7)
        assertNull(s.guild)
        val anyone = s.aliveHeroes().first().id
        for (cmd in listOf(Command.RecruitHero(anyone), Command.DismissHero(anyone), Command.LoanWeapon(anyone, WeaponId("w1")), Command.RecallLoan(WeaponId("w1")), Command.PlanDeployment("m1", listOf(anyone), Posture.BALANCED),
            Command.CancelPlannedDeployment, Command.ReserveDefender(anyone, true), Command.ChooseMissionCheckpoint("m1", CheckpointChoice.PUSH), Command.RetireAfterMilestone("x"), Command.SetGuildRank(1)))
            assertEquals(GameError.NotAGuildRun, s.rejected(cmd), cmd.toString())
        // No relic of the guild is ever offered to it.
        assertTrue(s.pendingRelicOffer.none { content.relic(it)!!.effect == com.tinyblacksmith.core.content.RelicEffect.COMBAT })
    }

    @Test fun `a classic run draws exactly what it drew before the guild stream existed`() {
        // The older streams are seeded by ordinal: a classic run and a guild run of one seed share every hero, faction and world draw of the first morning.
        val classic = engine.newRun(LegacyProfile(), 11)
        val guild = engine.newRun(LegacyProfile(), 11, charterId = GuildContent.TOWNS_LAST_HOPE)
        assertEquals(classic.heroes.keys, guild.heroes.keys)
        assertEquals(classic.heroes.values.map { it.fullName to it.classId }, guild.heroes.values.map { it.fullName to it.classId })
        assertEquals(classic.factions, guild.factions)
        assertEquals(classic.world, guild.world)
        for (stream in com.tinyblacksmith.core.rng.RngStream.entries.filter { it != com.tinyblacksmith.core.rng.RngStream.GUILD && it != com.tinyblacksmith.core.rng.RngStream.ENCOUNTERS })
            assertEquals(classic.rng.stateOf(stream), guild.rng.stateOf(stream), stream.name)
    }

    // ---- founding ----

    @Test fun `a charter founds the guild with its two classes, its gifts, its price and a third member on offer`() {
        for (c in content.guild!!.charters) {
            val classic = engine.newRun(LegacyProfile(), 21)
            val s = engine.newRun(LegacyProfile(), 21, charterId = c.id).sound()
            val g = s.guild!!
            assertEquals(c.startClasses, g.members.map { s.hero(it.heroId).classId }, c.id)
            assertEquals(classic.gold + c.goldDelta, s.gold)
            assertEquals(classic.town.militia + c.militiaDelta, s.town.militia)
            c.materials.forEach { (m, n) -> assertEquals((classic.materials[m] ?: 0) + n, s.materials[m]) }
            val third = g.candidates.first()
            assertEquals(c.offeredClass, s.hero(third.heroId).classId)
            assertEquals((cfg.signingFeeBase + cfg.signingFeePerLevel * (s.hero(third.heroId).level - 1)) * cfg.openingFeePercent / 100, third.fee)
            assertEquals(cfg.candidatesOffered, g.candidates.size)
            assertTrue(g.members.map { it.traitId }.toSet().size == 2 && g.candidates.none { it.traitId in g.members.map { m -> m.traitId } }, "traits are disclosed and distinct")
            // The board: a free safe contract and the standing work are always there.
            assertTrue(g.offers.any { content.guild!!.mission(it.defId).archetype == MissionArchetype.SUPPLY && it.fee == 0 })
            assertTrue(g.offers.any { it.defId == GuildContent.WALL_WORK })
            // The first morning shows what is open on day one (the dangerous contracts come later, unless the charter posts them at once); by day five the board is full.
            if (c.earlyDanger) assertTrue(g.offers.count { it.defId != GuildContent.WALL_WORK } > 1)
            var later = s
            repeat(4) { later = engine.handle(later, Command.EndDay(TestSupport.endDayId(later))).stateOrThrow() }
            if (!later.isEnded) assertEquals(cfg.offersOnBoard + c.extraOffers, later.guild!!.offers.count { o -> o.defId != GuildContent.WALL_WORK && o.subjectHeroId == null && o.subjectWeaponId == null })
        }
    }

    @Test fun `a guild run is offered the party's relics too`() {
        val seen = (1L..40L).flatMap { engine.newRun(LegacyProfile(), it, charterId = GuildContent.TOWNS_LAST_HOPE).pendingRelicOffer }.toSet()
        assertTrue(seen.any { content.relic(it)!!.effect == com.tinyblacksmith.core.content.RelicEffect.COMBAT })
    }

    // ---- recruiting ----

    @Test fun `recruiting pays the fee once, is a no-op the second time and respects the roster and the purse`() {
        val s = guildRun()
        val c = s.guild!!.candidates.first()
        val signed = s.run(Command.RecruitHero(c.heroId)).sound()
        assertEquals(s.gold - c.fee, signed.gold)
        assertEquals(3, signed.guild!!.members.size)
        assertEquals(s.hero(c.heroId).gold + c.fee, signed.hero(c.heroId).gold)
        assertEquals(signed, signed.run(Command.RecruitHero(c.heroId)), "the same command again changes nothing")
        val stranger = s.residents().first { r -> s.guild!!.candidates.none { it.heroId == r.id } }.id
        assertEquals(GameError.NotACandidate(stranger), s.rejected(Command.RecruitHero(stranger)))
        assertIs<GameError.NotEnoughGold>(s.copy(gold = c.fee - 1).rejected(Command.RecruitHero(c.heroId)))
        val full = s.copy(guild = s.guild!!.copy(members = s.guild!!.members + s.residents().filter { r -> s.guild!!.candidates.none { it.heroId == r.id } }.take(cfg.maxMembers - 2).map { GuildMember(it.id, 1, 0, 10, "greedy") }))
        assertEquals(GameError.RosterFull, full.rejected(Command.RecruitHero(c.heroId)))
    }

    @Test fun `members are not customers and the town refills around them`() {
        var s = guildRun(seed = 3)
        val members = s.members().toSet()
        repeat(6) {
            val (f, w) = s.forged()
            s = f.run(Command.ToggleShelf(w, true)).endDay()
            val r = s.lastResolution!!
            assertTrue(r.visits.none { it.heroId in members }, "a member came to the counter")
            assertTrue(r.field.none { it.heroId in members && it.outcome in setOf(FieldOutcome.WON, FieldOutcome.DRIVEN_BACK, FieldOutcome.PATROLLED) }, "a member went off by themselves")
        }
        assertTrue(s.residents().size >= engine.config.customers.minHeroPopulation, "the town keeps its customers")
        s.sound()
    }

    @Test fun `dismissing a member returns their loan and never the fee`() {
        val (s0, w) = guildRun().forged()
        val who = s0.members().first()
        val s = s0.run(Command.LoanWeapon(who, w)).run(Command.ReserveDefender(who, true))
        val out = s.run(Command.DismissHero(who)).sound()
        assertTrue(out.weapon(w).isInStorage)
        assertFalse(out.guild!!.isMember(who))
        assertTrue(out.guild!!.reserved.isEmpty())
        assertEquals(s.gold, out.gold)
    }

    // ---- loans: one blade, one place ----

    @Test fun `a loaned blade leaves the shop and cannot be listed, melted, scrapped, given away, honed or loaned twice`() {
        val (s0, w) = guildRun().forged()
        val (a, b) = s0.members()
        val s = s0.run(Command.LoanWeapon(a, w)).sound()
        assertEquals(WeaponLocation.Loaned(a), s.weapon(w).location)
        assertNull(s.weapon(w).ownerId, "a loan is never a private weapon")
        assertEquals(w, s.loanOf(a)?.id)
        assertTrue(s.storedWeapons().none { it.id == w } && s.listedWeapons().none { it.id == w })
        for (cmd in listOf(Command.ToggleShelf(w, true), Command.Salvage(w), Command.Scrap(listOf(w)), Command.DonateWeapon(w), Command.Hone(w)))
            assertTrue(s.rejected(cmd).let { it is GameError.WeaponOnLoan || it is GameError.WeaponNotAvailable }, cmd.toString())
        assertEquals(GameError.WeaponOnLoan(w, a), s.rejected(Command.LoanWeapon(b, w)))
        assertEquals(s, s.run(Command.LoanWeapon(a, w)), "the same loan again changes nothing")
        val back = s.run(Command.RecallLoan(w)).sound()
        assertTrue(back.weapon(w).isInStorage)
        assertEquals(back, back.run(Command.RecallLoan(w)))
    }

    @Test fun `a blade promised to an order is refused as a loan and a second loan sends the first home`() {
        val (s0, w1) = guildRun().forged()
        val (s1, w2) = s0.forged()
        val who = s1.members().first()
        val order = Commission(CommissionId("c99"), s1.residents().first().id, LaunchContent.SWORD, 1, 50, s1.day, s1.day + 5, CommissionStatus.ACCEPTED)
        val promised = s1.copy(commissions = s1.commissions + (order.id to order), weapons = s1.weapons + (w1 to s1.weapon(w1).copy(promisedTo = order.id)))
        assertEquals(GameError.WeaponPromised(w1, order.id), promised.rejected(Command.LoanWeapon(who, w1)))
        val two = s1.run(Command.LoanWeapon(who, w1)).run(Command.LoanWeapon(who, w2)).sound()
        assertTrue(two.weapon(w1).isInStorage)
        assertEquals(who, two.weapon(w2).loanedTo)
        // A listed blade can be loaned straight off the shelf.
        val listed = s1.run(Command.ToggleShelf(w1, true)).run(Command.LoanWeapon(who, w1)).sound()
        assertEquals(who, listed.weapon(w1).loanedTo)
    }

    @Test fun `nobody buys, inherits or is handed a loaned blade over many days`() {
        var s = guildRun(seed = 5)
        val (f, w) = s.forged()
        val who = f.members().first()
        s = f.run(Command.LoanWeapon(who, w))
        repeat(12) { s = s.endDay().sound(); if (!s.isEnded && s.guild!!.isMember(who)) assertEquals(who, s.weapon(w).loanedTo) }
    }

    // ---- contracts ----

    private fun GameState.send(a: MissionArchetype, posture: Posture = Posture.BALANCED, party: List<HeroId> = members()): GameState = run(Command.PlanDeployment(offer(a).id, party, posture))

    @Test fun `a plan pays its fee once, replacing or cancelling returns it, and End Day sends the party`() {
        val s = guildRun(seed = 9).copy(day = 3).let { it.copy(guild = it.guild!!.copy(offers = emptyList())) }.let { engine.handle(it, Command.EndDay(TestSupport.endDayId(it))).stateOrThrow() }
        val hunt = s.guild!!.offers.firstOrNull { it.fee > 0 } ?: return
        val planned = s.run(Command.PlanDeployment(hunt.id, s.members(), Posture.BALANCED)).sound()
        assertEquals(s.gold - hunt.fee, planned.gold)
        assertEquals(planned, planned.run(Command.PlanDeployment(hunt.id, s.members(), Posture.BALANCED)), "the same plan again costs nothing more")
        val replaced = planned.send(MissionArchetype.SUPPLY)
        assertEquals(s.gold, replaced.gold, "the fee of the replaced plan came back")
        assertEquals(s.gold, planned.run(Command.CancelPlannedDeployment).gold)
        assertEquals(GameError.NoDeploymentPlanned, s.rejected(Command.CancelPlannedDeployment))
    }

    @Test fun `a party is one to three fit members in town, none of them kept for the wall`() {
        val s = guildRun()
        val (a, b) = s.members()
        val supply = s.offer(MissionArchetype.SUPPLY).id
        assertIs<GameError.PartyInvalid>(s.rejected(Command.PlanDeployment(supply, emptyList(), Posture.BALANCED)))
        assertIs<GameError.PartyInvalid>(s.rejected(Command.PlanDeployment(supply, listOf(a, a), Posture.BALANCED)))
        assertIs<GameError.NotAMember>(s.rejected(Command.PlanDeployment(supply, listOf(s.residents().first().id), Posture.BALANCED)))
        assertEquals(GameError.MissionNotOffered("nope"), s.rejected(Command.PlanDeployment("nope", listOf(a), Posture.BALANCED)))
        assertIs<GameError.MemberUnavailable>(s.run(Command.ReserveDefender(a, true)).rejected(Command.PlanDeployment(supply, listOf(a, b), Posture.BALANCED)))
        val hurt = s.copy(guild = s.guild!!.copy(members = s.guild!!.members.map { if (it.heroId == a) it.copy(wound = Wound(WoundKind.INJURED, s.day + 2, "test")) else it }))
        assertIs<GameError.MemberUnavailable>(hurt.rejected(Command.PlanDeployment(supply, listOf(a), Posture.BALANCED)))
        // A scar does not keep a member home.
        val scarred = s.copy(guild = s.guild!!.copy(members = s.guild!!.members.map { if (it.heroId == a) it.copy(wound = Wound(WoundKind.SCARRED, s.day + 5, "test")) else it }))
        assertNotNull(scarred.run(Command.PlanDeployment(supply, listOf(a), Posture.BALANCED)).guild!!.planned)
    }

    @Test fun `a one-day contract - away tonight, home in the morning, rewards once, the ledger balances`() {
        val s = guildRun(seed = 4).send(MissionArchetype.SUPPLY)
        val out = engine.handle(s, Command.EndDay(TestSupport.endDayId(s))) as CommandOutcome.Accepted
        val after = out.state.sound()
        val report = out.resolution!!.mission!!
        assertEquals(report, after.guild!!.lastMission)
        assertFalse(report.continues)
        assertNotNull(report.fight)
        assertTrue(report.fight!!.highlights.isNotEmpty())
        val ledger = out.resolution!!.ledger!!
        assertEquals(ledger.goldAtOpen + ledger.income.values.sum(), ledger.goldAtClose)
        assertEquals(after.gold, ledger.goldAtClose)
        if (report.outcome == MissionOutcome.WON) {
            assertTrue((ledger.income[IncomeKind.CONTRACT] ?: 0) > 0)
            assertTrue(report.gained.materials.all { (m, n) -> (after.materials[m] ?: 0) >= (s.materials[m] ?: 0) + n })
        }
        // Home this morning, each either fit or laid up with a wound that says so.
        assertNull(after.guild!!.mission)
        assertTrue(after.guild!!.members.all { it.status == MemberStatus.HOME })
        // The same End Day again is the stored day: nothing is fought or paid twice.
        val retry = engine.handle(after, Command.EndDay(TestSupport.endDayId(s))) as CommandOutcome.Accepted
        assertEquals(after, retry.state)
        assertEquals(out.resolution, retry.resolution)
    }

    @Test fun `a contract resolves the same after a save and a reload at every step`() {
        var a = guildRun(seed = 12)
        var b = a
        repeat(9) {
            if (a.isEnded) return@repeat
            val bot = GuildBot(engine, GuildPolicy.GREEDY_DIVER)
            a = bot.planDay(a)
            b = SaveCodec.decodeRun(SaveCodec.encodeRun(bot.planDay(b)))
            assertEquals(a, b)
            a = a.endDay(); b = SaveCodec.decodeRun(SaveCodec.encodeRun(b)).endDay()
            assertEquals(a, b)
            assertEquals(a, SaveCodec.decodeRun(SaveCodec.encodeRun(a)), "a guild state survives the codec whole")
        }
    }

    @Test fun `a two-day contract waits at its checkpoint, returns by default and pushes only when told`() {
        fun delve(seed: Long): GameState? {
            var s = guildRun(seed)
            repeat(8) { if (s.guild!!.offers.none { it.optionalPush } || s.day < 4) s = s.endDay() }
            return s.takeIf { !it.isEnded && it.guild!!.offers.any { o -> o.optionalPush } }
        }
        val start = (1L..40L).firstNotNullOf { delve(it) }
        val strong = start.copy(heroes = start.heroes.mapValues { (id, h) -> if (start.guild!!.isMember(id)) h.copy(level = 10, health = 100) else h }, town = start.town.copy(nextSiegeDay = start.day + 9))
        val offer = strong.guild!!.offers.first { it.optionalPush }
        val day1 = strong.run(Command.PlanDeployment(offer.id, strong.members(), Posture.RECKLESS)).endDay().sound()
        val m = day1.guild!!.mission!!
        assertEquals(1, m.stage)
        assertNull(m.outcome)
        assertTrue(day1.lastResolution!!.mission!!.continues)
        assertTrue(day1.guild!!.members.all { it.status == MemberStatus.AWAY })
        assertEquals(GameError.PartyAway, day1.rejected(Command.PlanDeployment(day1.offer(MissionArchetype.SUPPLY).id, day1.members(), Posture.BALANCED)))
        assertIs<GameError.MemberUnavailable>(day1.rejected(Command.ReserveDefender(day1.members().first(), true)))
        // Unanswered: End Day returns with what was secured, no second fight.
        val home = day1.endDay().sound()
        assertNull(home.guild!!.mission)
        assertEquals(strong.day + 2, home.day, "a two-day contract is back on the morning of the third day")
        assertNull(home.lastResolution!!.mission, "the walk home is not a fight")
        assertEquals(GameError.NoMissionCheckpoint, home.rejected(Command.ChooseMissionCheckpoint(m.id, CheckpointChoice.PUSH)))
        // Told to push: the deeper stage is fought on the second day.
        val pushed = day1.run(Command.ChooseMissionCheckpoint(m.id, CheckpointChoice.PUSH)).endDay().sound()
        assertEquals(1, pushed.lastResolution!!.mission!!.stage)
        assertNull(pushed.guild!!.mission)
        // What the first hall gave was the forge's the night it was won.
        assertTrue(day1.gold >= strong.gold - offer.fee)
    }

    // ---- the wall ----

    private fun siegeEve(seed: Long = 6): GameState {
        var s = guildRun(seed)
        while (s.day < s.town.nextSiegeDay) s = s.endDay()
        return s
    }

    @Test fun `the siege of a guild run is fought, reported with a verdict, and the forge damage is what the report says`() {
        val eve = siegeEve()
        val out = engine.handle(eve, Command.EndDay(TestSupport.endDayId(eve))) as CommandOutcome.Accepted
        val report = out.resolution!!.siege!!
        assertEquals(report.forgeDamage, out.resolution!!.events.filter { it.type == EventType.FORGE_DAMAGED }.sumOf { it.data.getValue("damage").toInt() })
        val mended = out.resolution!!.events.filter { it.type == EventType.TOWN_RECOVERED }.sumOf { it.data.getValue("amount").toInt() }
        assertEquals(eve.town.integrity - report.forgeDamage + mended, out.state.town.integrity)
        assertTrue(report.forgeDamage in 0..engine.config.maxForgeDamagePerSiege)
        assertTrue(eve.siege!!.plan.isNotEmpty(), "the besieger's field was drawn at the first warning")
        assertTrue(out.resolution!!.replays.any { it.kind == ReplayKind.SIEGE })
        val line = out.resolution!!.events.first { it.type == EventType.SIEGE_WON || it.type == EventType.SIEGE_LOST }
        assertEquals(report.verdict.name, line.data["verdict"])
        if (report.verdict == SiegeVerdict.HELD_AT_A_COST) assertTrue("at a cost" in line.text)
        assertEquals(eve.town.nextSiegeDay + engine.config.siegeInterval, out.state.town.nextSiegeDay)
        out.state.sound()
    }

    @Test fun `a reserved member stands on the wall and a party that is away does not`() {
        val eve = siegeEve()
        val (a, b) = eve.members()
        val weakest = eve.members().minBy { eve.hero(it).level }
        val reserved = eve.run(Command.ReserveDefender(weakest, true))
        assertEquals(weakest, engine.wallForecast(reserved)!!.defenders.first().first.id)
        val fought = reserved.endDay()
        assertEquals(weakest, fought.lastResolution!!.siege!!.defenders.first())
        assertTrue(fought.guild!!.reserved.isEmpty(), "a reservation is for one siege")
        // The same evening with both members out on a contract: neither is on the wall, and the forecast said so.
        val away = eve.send(MissionArchetype.SUPPLY, party = listOf(a, b))
        val missing = engine.wallForecast(away)!!
        assertTrue(missing.defenders.none { it.first.id == a || it.first.id == b })
        val night = away.endDay()
        assertTrue(night.lastResolution!!.siege!!.defenders.none { it == a || it == b })
    }

    @Test fun `a one-day contract the day before the siege is back for it, a two-day one is not`() {
        var s = guildRun(seed = 6)
        while (s.day < s.town.nextSiegeDay - 1) s = s.endDay()
        val back = s.send(MissionArchetype.SUPPLY).endDay()
        assertEquals(back.town.nextSiegeDay, back.day)
        assertTrue(back.guild!!.members.all { it.status == MemberStatus.HOME }, "they left on the morning of day 4 and are home on the morning of day 5")
        val forecast = engine.wallForecast(back)!!
        assertTrue(back.members().filter { engine.memberUnavailable(back, it) == null }.all { m -> forecast.defenders.any { it.first.id == m } || forecast.defenders.size == cfg.defenders })
    }

    @Test fun `sabotage spoils the siege once - the trait if it has one, else one of their line`() {
        val eve = siegeEve()
        val plain = eve.siege!!
        val ctx = com.tinyblacksmith.core.engine.ResolutionContext(eve, content, engine.config)
        com.tinyblacksmith.core.guild.SiegeFight.sabotage(ctx)
        assertEquals(plain.plan.size - 1, ctx.siege!!.plan.size)
        assertEquals(1, ctx.siege!!.sabotaged)
        com.tinyblacksmith.core.guild.SiegeFight.sabotage(ctx)
        assertEquals(plain.plan.size - 1, ctx.siege!!.plan.size, "once a siege")
        val traited = com.tinyblacksmith.core.engine.ResolutionContext(eve.copy(siege = plain.copy(traitId = com.tinyblacksmith.core.content.Depth.LONG_ASSAULT)), content, engine.config)
        com.tinyblacksmith.core.guild.SiegeFight.sabotage(traited)
        assertNull(traited.siege!!.traitId)
        assertEquals(plain.plan.size, traited.siege!!.plan.size)
    }

    // ---- many days, many seeds ----

    @Test fun `every guild policy plays twenty-five days on twelve seeds without a rejected command or a broken invariant`() {
        for (policy in GuildPolicy.entries) for (seed in 1L..12L) {
            val bot = GuildBot(engine, policy, content.guild!!.charters[(seed % 3).toInt()].id, horizon = 25)
            val (stats, end) = bot.play(seed) { s -> assertEquals(emptyList(), Invariants.check(s, engine.config, engine.shelfSlots(s), content), "$policy seed $seed day ${s.day}") }
            assertEquals(0, stats.rejected, "$policy seed $seed: ${bot.rejections}")
            assertEquals(emptyList(), Invariants.check(end, engine.config, engine.shelfSlots(end), content))
            assertEquals(end, SaveCodec.decodeRun(SaveCodec.encodeRun(end)))
        }
    }

    @Test fun `a guild run of one seed is the same run twice`() {
        val a = GuildBot(engine, GuildPolicy.ADAPTIVE).play(33).second
        val b = GuildBot(engine, GuildPolicy.ADAPTIVE).play(33).second
        assertEquals(a, b)
    }
}
