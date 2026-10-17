package com.tinyblacksmith.core

import com.tinyblacksmith.core.TestSupport.endDay
import com.tinyblacksmith.core.TestSupport.endDayAccepted
import com.tinyblacksmith.core.TestSupport.engine
import com.tinyblacksmith.core.TestSupport.guildRun
import com.tinyblacksmith.core.TestSupport.run
import com.tinyblacksmith.core.combat.Posture
import com.tinyblacksmith.core.content.GuildContent
import com.tinyblacksmith.core.content.LaunchContent
import com.tinyblacksmith.core.crafting.Forge
import com.tinyblacksmith.core.crafting.WeaponRules
import com.tinyblacksmith.core.engine.Command
import com.tinyblacksmith.core.engine.CommandOutcome
import com.tinyblacksmith.core.engine.GameError
import com.tinyblacksmith.core.engine.ResolutionContext
import com.tinyblacksmith.core.guild.Hooks
import com.tinyblacksmith.core.model.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Final design 18.2, fixtures 1 to 12: the members' own decisions, their timing, ownership and the smith's influence. */
class LivingAgencyTest {
    private val content = engine.content

    /** A living guild era on its first planning morning, with every Hook cleared so a fixture adds only the one it tests. */
    private fun base(seed: Long = 11): GameState = engine.guildRun(LegacyProfile(), seed, GuildContent.TOWNS_LAST_HOPE).let { s ->
        s.copy(gold = 5_000, heroes = s.heroes.mapValues { (_, h) -> h.copy(hook = null, want = null, gold = if (s.guild!!.isMember(h.id)) h.gold else 0) }, pendingRelicOffer = emptyList(), encounter = null)
    }
    private fun GameState.member(i: Int = 0): Hero = hero(guild!!.members[i].heroId)
    private fun GameState.withHero(h: Hero) = copy(heroes = heroes + (h.id to h))
    private fun GameState.withMember(id: HeroId, f: (GuildMember) -> GuildMember) = copy(guild = guild!!.copy(members = guild!!.members.map { if (it.heroId == id) f(it) else it }))
    private fun GameState.add(w: Weapon) = copy(weapons = weapons + (w.id to w), nextWeaponSerial = nextWeaponSerial + 1)
    /** No siege on the calendar this week and only the standing work on the board: a free slot for a personal offer. */
    private fun GameState.calm() = copy(town = town.copy(nextSiegeDay = 40), siege = SiegeScenario(40), guild = guild!!.copy(offers = guild!!.offers.filter { it.defId == GuildContent.WALL_WORK }))
    private fun GameState.rejected(cmd: Command): GameError = (engine.handle(this, cmd) as CommandOutcome.Rejected).error

    private fun blade(s: GameState, family: WeaponFamilyId, quality: Int, location: WeaponLocation, core: MaterialId = LaunchContent.IRON, augment: MaterialId = LaunchContent.EMBER_RESIN): Weapon {
        val id = WeaponId("w${s.nextWeaponSerial}")
        return Weapon(id, "Test ${family.value} ${id.value}", family, core, augment, null, ForgeMode.QUICK, Risk.BALANCED, quality, Forge.forgedRarity(quality, engine.config, content.material(core).tier),
            Forge.powerOf(content, engine.config, family, core, quality, emptyList()), content.material(augment).element, emptyList(), emptyList(), location, s.era, s.day, craft = WeaponRules.CRAFT)
    }
    private fun family(h: Hero) = content.heroClass(h.classId).preferredFamilies.first()

    @Test fun `1 2 a member buys at the counter with their own gold, stays a member, and a loan is never traded in`() {
        var s = base()
        val m = s.member()
        s = s.withHero(m.copy(gold = 400, want = Want(family(m), 1, 400, s.day)))
        val loan = blade(s, family(m), 20, WeaponLocation.Loaned(m.id)); s = s.add(loan)
        val sale = blade(s, family(m), 60, WeaponLocation.Shelf(60)); s = s.add(sale)
        val day = s.endDayAccepted()
        val visit = day.resolution!!.visits.single { it.kind == VisitKind.MEMBER }
        assertEquals(m.id, visit.heroId)
        assertEquals(sale.id, visit.purchasedWeaponId)
        assertEquals(0, visit.sale!!.tradeInCredit, "the loan is the smith's: never credited")
        val after = day.state
        assertEquals(WeaponLocation.Loaned(m.id), after.weapon(loan.id).location)
        assertEquals(m.id, after.weapon(sale.id).ownerId)
        assertEquals(400 - 60, after.hero(m.id).gold)
        assertTrue(after.guild!!.isMember(m.id) && after.residents().none { it.id == m.id })
    }

    @Test fun `3 4 a better owned weapon replaces the loan after one whole planning day, and waits for a quest to come home`() {
        var s = base()
        val m = s.member()
        val loan = blade(s, family(m), 8, WeaponLocation.Loaned(m.id)); s = s.add(loan)
        val own = blade(s, family(m), 70, WeaponLocation.Owned(m.id, equipped = true)); s = s.add(own)
        s = s.endDay()
        val intent = assertNotNull(s.guild!!.member(m.id)!!.loanReturn)
        assertEquals(s.day, intent.noticeDay, "shown this morning")
        assertEquals(WeaponLocation.Loaned(m.id), s.weapon(loan.id).location, "nothing changed on the morning it is announced")
        // The member leaves on a quest that morning: the loadout is frozen and the return waits until the commitment resolves.
        val supply = s.guild!!.offers.first { it.defId == GuildContent.SUPPLY_RUN }
        s = s.run(Command.PlanDeployment(supply.id, listOf(m.id), Posture.CAUTIOUS)).endDay()
        assertEquals(WeaponLocation.Loaned(m.id), s.weapon(loan.id).location, "away on the quest: the loan stays in hand")
        while (s.guild!!.member(m.id)!!.status != MemberStatus.HOME) s = s.endDay()
        s = s.endDay()
        assertEquals(WeaponLocation.Storage, s.weapon(loan.id).location)
        assertEquals(WeaponLocation.Owned(m.id, equipped = true), s.weapon(own.id).location)
        assertNull(s.guild!!.member(m.id)!!.loanReturn)
        assertTrue(s.events.any { it.type == EventType.LOAN_RETURNED && m.id.value in it.subjectIds })
    }

    @Test fun `5 a better loan answers the return at once`() {
        var s = base()
        val m = s.member()
        val loan = blade(s, family(m), 8, WeaponLocation.Loaned(m.id)); s = s.add(loan)
        val own = blade(s, family(m), 50, WeaponLocation.Owned(m.id, equipped = true)); s = s.add(own)
        s = s.endDay()
        assertNotNull(s.guild!!.member(m.id)!!.loanReturn)
        val better = blade(s, family(m), 95, WeaponLocation.Storage, core = LaunchContent.STARSTEEL); s = s.add(better)
        s = s.run(Command.LoanWeapon(m.id, better.id))
        assertNull(s.guild!!.member(m.id)!!.loanReturn)
    }

    @Test fun `6 7 8 a Speciality lean is announced, chosen once by the member, and loan toggles earn no evidence`() {
        var s = base()
        val m = s.member()
        s = s.withMember(m.id) { it.copy(missionWins = 3) }
        val ev0 = s.guild!!.member(m.id)!!.evidence
        val a = blade(s, family(m), 40, WeaponLocation.Storage); s = s.add(a)
        repeat(4) { s = s.run(Command.LoanWeapon(m.id, a.id)).run(Command.RecallLoan(a.id)) }
        assertEquals(ev0, s.guild!!.member(m.id)!!.evidence, "commands and previews are not deeds")
        assertIs<GameError.SpecialityChosenByMember>(s.rejected(Command.ChooseSpeciality(m.id, "vanguard")))
        s = s.endDay()
        val lean = assertNotNull(s.guild!!.member(m.id)!!.lean)
        assertEquals(setOf("vanguard", "striker"), lean.options.toSet())
        assertEquals(s.day, lean.resolveDay)
        s = s.endDay()
        val chosen = assertNotNull(s.guild!!.member(m.id)!!.specialityId)
        assertTrue(chosen in lean.options)
        assertNull(s.guild!!.member(m.id)!!.lean)
        assertEquals(chosen, s.endDay().guild!!.member(m.id)!!.specialityId, "chosen once, for good")
        // A legacy era keeps the smith's choice, and never leans.
        var old = engine.guildRun(LegacyProfile(), 11, GuildContent.TOWNS_LAST_HOPE, livingRevision = LivingRevision.LEGACY)
        val om = old.member()
        old = old.withMember(om.id) { it.copy(missionWins = 3) }
        assertNull(old.endDay().guild!!.member(om.id)!!.lean)
        assertEquals("vanguard", old.run(Command.ChooseSpeciality(om.id, "vanguard")).guild!!.member(om.id)!!.specialityId)
    }

    @Test fun `9 a grudge, a lost family weapon and a debt point at real factions, weapons and members`() {
        var s = base()
        val g = s.member(0)
        s = s.withHero(g.copy(hook = Hook(HookKind.GRUDGE, 1, factionId = LaunchContent.ASHCLAW)))
        s = s.calm()
        s = s.endDay()
        val hunt = s.guild!!.offers.single { it.sponsorHeroId == g.id }
        assertEquals(LaunchContent.ASHCLAW, hunt.factionId)
        assertEquals(GuildContent.HUNT, hunt.defId)
        // With every ordinary slot taken, the offer waits in the queue (never a missed chance) and is posted when a slot frees.
        val full = base().let { f -> f.withHero(f.member(0).copy(hook = Hook(HookKind.GRUDGE, 1, factionId = LaunchContent.ASHCLAW))) }.endDay()
        if (full.guild!!.offers.none { it.sponsorHeroId == g.id }) assertEquals("grudge|${g.id.value}", full.guild!!.queued)
        // A lost family weapon: its recovery names the very record, which is Lost.
        var t = base(12)
        val h = t.member(1)
        val heir = blade(t, family(h), 40, WeaponLocation.Lost(1, "lost family weapon of ${h.fullName}")); t = t.add(heir)
        t = t.withHero(h.copy(hook = Hook(HookKind.LOST_WEAPON, 1, weaponId = heir.id)))
        t = t.calm()
        t = t.endDay()
        val recovery = t.guild!!.offers.single { it.sponsorHeroId == h.id }
        assertEquals(heir.id, recovery.subjectWeaponId)
        assertIs<WeaponLocation.Lost>(t.weapon(heir.id).location)
        // A due debt the member cannot pay brings the creditor to the forge the next morning, about that member.
        var d = base(13).let { it.copy(town = it.town.copy(siegesSurvived = 1)) }
        val debtor = d.member(0)
        d = d.withHero(debtor.copy(gold = 0, hook = Hook(HookKind.DEBT, 1, creditor = "a moneylender in Emberfall", amount = 40, dueDay = d.day)))
        d = d.endDay()
        assertEquals(ConcernKind.DEBT, d.guild!!.member(debtor.id)!!.concern?.kind)
        assertEquals(Hooks.CREDITOR, d.encounter?.defId)
        assertEquals(debtor.id, d.encounter?.heroId)
    }

    @Test fun `10 an offer that lapsed while the member was away or the till was empty is never a missed chance`() {
        val s = base()
        val m = s.member()
        val withHook = s.withHero(m.copy(hook = Hook(HookKind.GRUDGE, 1, factionId = LaunchContent.ASHCLAW)))
        val offer = withHook.guild!!.offers.first().copy(id = "mx", sponsorHeroId = m.id, opportunityId = "hook_grudge", fee = 15)
        fun lapse(state: GameState): Concern? = ResolutionContext(state, content, engine.config).also { Hooks.lapsed(it, offer) }.toState().guild!!.member(m.id)!!.concern
        assertNull(lapse(withHook.withMember(m.id) { it.copy(status = MemberStatus.AWAY) }), "away")
        assertNull(lapse(withHook.copy(gold = 0)), "the smith could not pay the fee")
        assertEquals(listOf("mx"), lapse(withHook)?.missed, "a fair chance, missed once")
    }

    @Test fun `11 a departure is announced three mornings ahead, a remedy cancels it, and leaving returns every loan once`() {
        var s = base().let { it.copy(town = it.town.copy(siegesSurvived = 1)) }
        val m = s.member()
        val loan = blade(s, family(m), 30, WeaponLocation.Loaned(m.id)); s = s.add(loan)
        val own = blade(s, family(m), 30, WeaponLocation.Owned(m.id, equipped = true)); s = s.add(own)
        s = s.withHero(m.copy(hook = Hook(HookKind.GRUDGE, 1, factionId = LaunchContent.ASHCLAW)))
        s = s.withMember(m.id) { it.copy(concern = Concern(ConcernKind.GRUDGE, 1, "send a party against the Ashclaw Raiders", missed = listOf("a", "b", "c"))) }
        s = s.endDay()
        val dep = assertNotNull(s.guild!!.member(m.id)!!.concern?.departure)
        assertEquals(s.day, dep.noticeDay)
        assertEquals(s.day + 3, dep.day, "three whole planning days: this one and the next two")
        // The remedy: the grievance answered (here resolved directly) cancels the notice.
        val saved = ResolutionContext(s, content, engine.config).also { c -> c.updateHero(c.hero(m.id).copy(hook = null)); com.tinyblacksmith.core.guild.GuildOps.updateMember(c, m.id) { it.copy(concern = null) } }.toState()
        assertTrue(saved.endDay().endDay().endDay().guild!!.isMember(m.id))
        // Unanswered, the member leaves before the morning of the date, alive, with their own weapon; the loan is home, once.
        while (s.guild!!.isMember(m.id)) s = s.endDay()
        assertEquals(dep.day, s.day)
        val gone = s.hero(m.id)
        assertEquals(HeroFate.DEPARTED, gone.fate)
        assertEquals(WeaponLocation.Storage, s.weapon(loan.id).location)
        assertIs<WeaponLocation.Lost>(s.weapon(own.id).location)
        assertEquals(1, s.events.count { it.type == EventType.MEMBER_DEPARTED })
    }

    @Test fun `12 a relative's death is mourned and never starts a departure`() {
        var s = base().let { it.copy(town = it.town.copy(siegesSurvived = 1)) }
        val m = s.member()
        val kin = s.residents().first()
        s = s.withHero(m.copy(hook = Hook(HookKind.KIN, 1, kinHeroId = kin.id))).withHero(kin.copy(fate = HeroFate.DEAD, diedOnDay = 1))
        repeat(3) { s = s.endDay() }
        assertNull(s.guild!!.member(m.id)!!.concern)
        assertTrue(s.hero(m.id).reasons.any { it.kind == "kin_lost" })
        assertTrue(s.guild!!.isMember(m.id))
    }
}
