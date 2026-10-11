package com.example.blacksmithproject

import com.example.blacksmithproject.ui.contractUi
import com.example.blacksmithproject.ui.destName
import com.example.blacksmithproject.ui.guildUi
import com.example.blacksmithproject.ui.memberDetail
import com.example.blacksmithproject.ui.shopUi
import com.example.blacksmithproject.ui.threat
import com.tinyblacksmith.core.combat.Posture
import com.tinyblacksmith.core.content.GuildContent
import com.tinyblacksmith.core.content.LaunchContent
import com.tinyblacksmith.core.engine.Command
import com.tinyblacksmith.core.engine.CommandOutcome
import com.tinyblacksmith.core.engine.GameEngine
import com.tinyblacksmith.core.engine.GameError
import com.tinyblacksmith.core.model.CheckpointChoice
import com.tinyblacksmith.core.model.CommandId
import com.tinyblacksmith.core.model.GameState
import com.tinyblacksmith.core.model.GuildMember
import com.tinyblacksmith.core.model.LegacyProfile
import com.tinyblacksmith.core.model.MissionOffer
import com.tinyblacksmith.core.model.WeaponLocation
import com.tinyblacksmith.core.model.Wound
import com.tinyblacksmith.core.model.WoundKind
import com.tinyblacksmith.core.sim.GuildBot
import com.tinyblacksmith.core.sim.GuildPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The Guild destination's models against real engine states: a guild run under Town's Last Hope, days ended by the engine. */
class GuildUiTest {
    private val engine = GameEngine()
    private val cfg = engine.config.guild

    private fun guildRun(seed: Long = 7): GameState = engine.newRun(LegacyProfile(), seed, charterId = GuildContent.TOWNS_LAST_HOPE)
    private fun GameState.send(cmd: Command): GameState = (engine.handle(this, cmd) as CommandOutcome.Accepted).state
    private fun GameState.endDay(): GameState = send(Command.EndDay(CommandId("${runId.value}:day$day")))
    private fun GameState.siegeOn(day: Int): GameState = copy(town = town.copy(nextSiegeDay = day))

    /** Mornings of a run a bot plays: each as it stands before the bot has planned the day. */
    private fun mornings(policy: GuildPolicy, seed: Long, days: Int = 30): Sequence<GameState> = sequence {
        val bot = GuildBot(engine, policy)
        var s = guildRun(seed)
        while (!s.isEnded && s.day <= days) {
            yield(s)
            s = bot.planDay(s).endDay()
        }
    }

    private fun GameState.offerOf(days: Int): MissionOffer? = guild!!.offers.firstOrNull { it.days == days && it.defId != GuildContent.WALL_WORK }

    @Test
    fun aClassicRunHasNoGuildAndItsThirdDestinationIsStillTown() {
        val classic = engine.newRun(LegacyProfile(), 7)
        assertNull(engine.guildUi(classic))
        assertNull(engine.contractUi(classic, "m1", emptyList(), Posture.BALANCED))
        assertEquals("Town", destName(Dest.TOWN))
        assertEquals("Town", destName(Dest.TOWN, guild = false))
        assertEquals("Guild", destName(Dest.TOWN, guild = true))
        assertEquals(listOf("Shop", "Forge", "Guild", "Records"), Dest.entries.map { destName(it, guild = true) })
    }

    @Test
    fun aOneDayContractIsHomeForTomorrowsSiegeAndATwoDayContractMissesIt() {
        // A morning with a one-day and a two-day contract on the board and nobody out.
        val s = (7L..12L).asSequence().flatMap { mornings(GuildPolicy.NOVICE, it) }.first { it.guild!!.mission == null && it.offerOf(1) != null && it.offerOf(2) != null }
        val one = s.offerOf(1)!!
        val two = s.offerOf(2)!!

        // The siege falls tomorrow night.
        val before = engine.guildUi(s.siegeOn(s.day + 1))!!
        val oneUi = before.offers.first { it.id == one.id }
        assertEquals("1 day: leaves at End Day, home on the morning of day ${s.day + 1}", oneUi.time)
        assertNull("home that morning, so on the wall that night", oneUi.missesSiege)
        val twoUi = before.offers.first { it.id == two.id }
        assertEquals("2 days: leaves at End Day, home on the morning of day ${s.day + 2}", twoUi.time)
        assertEquals("Misses the siege night of day ${s.day + 1}.", twoUi.missesSiege)

        // The siege falls tonight: a party sent today is away tonight, whatever the contract's length.
        val tonight = engine.guildUi(s.siegeOn(s.day))!!
        assertEquals("Misses the siege night of day ${s.day}.", tonight.offers.first { it.id == one.id }.missesSiege)
        assertEquals("Misses the siege night of day ${s.day}.", tonight.offers.first { it.id == two.id }.missesSiege)
        assertEquals("Siege tonight, after today's trading", tonight.wall.whenLine)

        // The siege falls the night after both are home.
        val later = engine.guildUi(s.siegeOn(s.day + 2))!!
        assertNull(later.offers.first { it.id == two.id }.missesSiege)

        // The contract sheet says the same morning, and how many stay.
        val members = s.guild!!.members.map { it.heroId }.filter { engine.memberUnavailable(s, it) == null && it !in s.guild!!.reserved }
        val sheet = engine.contractUi(s.siegeOn(s.day + 1), two.id, members.take(1), Posture.CAUTIOUS)!!
        assertEquals("Home on the morning of day ${s.day + 2}.", sheet.returns)
        val home = s.guild!!.members.count { engine.memberUnavailable(s, it.heroId) == null } - 1
        assertTrue(sheet.atHome, sheet.atHome.endsWith("for the siege night of day ${s.day + 1}."))
        assertTrue(sheet.atHome, sheet.atHome.startsWith(if (home == 1) "1 fit member stays" else "$home fit members stay"))
        assertEquals(listOf(1), sheet.picks.mapNotNull { it.order })
        assertEquals(Posture.CAUTIOUS, sheet.postures.single { it.selected }.posture)
        assertEquals("Leaves when a member is down or the party is under ${cfg.fight.cautiousHealthPercent}% of the health it came with.", sheet.postures.first().line)
    }

    @Test
    fun thePlannedPartyIsToldOnTheWallOnTheBoardAndAtEndDay() {
        val fresh = guildRun().let { if (it.pendingRelicOffer.isNotEmpty()) it.send(Command.DeclineRelicOffer) else it }
        val s = fresh.siegeOn(fresh.day)
        val offer = s.offerOf(1)!!
        val party = s.guild!!.members.map { it.heroId }
        val planned = s.send(Command.PlanDeployment(offer.id, party, Posture.BALANCED))
        val ui = engine.guildUi(planned)!!
        val names = party.map { planned.heroes.getValue(it).name }.joinToString(", ")
        val contract = engine.content.guild!!.mission(offer.defId).name
        assertEquals("Planned for today: $names on $contract, balanced. Leaves at End Day.", ui.plan)
        assertTrue(ui.offers.first { it.id == offer.id }.planned)
        assertTrue(ui.wall.notes.toString(), "The party you planned leaves today and is not on the wall tonight." in ui.wall.notes)
        // Nobody of the party is counted on the wall, and End Day says both things in plain lines.
        assertTrue(ui.wall.defenders.none { it.heroId in party })
        assertEquals("$names leave for $contract, home on the morning of day ${s.day + 1}", ui.endDay[0])
        assertTrue(ui.endDay[1], ui.endDay[1].startsWith("Siege tonight"))
        assertTrue(ui.endDay[1], if (ui.wall.defenders.isEmpty()) ui.endDay[1].endsWith("Nobody stands on the wall tonight") else ui.endDay[1].endsWith("On the wall: " + ui.wall.defenders.joinToString(", ") { it.name }))
        assertTrue(ui.endDay.size <= 3)
        // A planned member cannot be kept for the wall as well, and the toggle says why.
        assertEquals("${planned.heroes.getValue(party[0]).fullName} is in the party you planned for today.", ui.wall.reserve.first { it.heroId == party[0] }.blocked)
        // Without a plan and without a siege tonight End Day has nothing to add.
        assertEquals(emptyList<String>(), engine.guildUi(fresh.siegeOn(fresh.day + 3))!!.endDay)
    }

    @Test
    fun missingDefendersAreListedWithTheEnginesOwnReason() {
        val fresh = guildRun()
        val g = fresh.guild!!
        val hurt = g.members.first()
        val siege = fresh.town.nextSiegeDay
        val s = fresh.copy(guild = g.copy(members = g.members.map { if (it.heroId == hurt.heroId) it.copy(wound = Wound(WoundKind.INJURED, siege + 2, "A test of the wall.")) else it }))
        val ui = engine.guildUi(s)!!
        val name = s.heroes.getValue(hurt.heroId).fullName
        assertEquals(listOf("$name: injured until day ${siege + 2}"), ui.wall.missing)
        assertEquals(engine.wallForecast(s)!!.missing.map { (h, why) -> "${h.fullName}: $why" }, ui.wall.missing)
        assertTrue(ui.wall.defenders.none { it.heroId == hurt.heroId })
        assertEquals("Injured until day ${siege + 2}", ui.roster.first { it.heroId == hurt.heroId }.status)
        // Nobody who cannot stand is offered the Reserve toggle, and the contract sheet says why they cannot go.
        assertTrue(ui.wall.reserve.none { it.heroId == hurt.heroId })
        val pick = engine.contractUi(s, s.offerOf(1)!!.id, listOf(hurt.heroId), Posture.BALANCED)!!
        assertEquals("$name is injured until day ${siege + 2}.", pick.picks.first { it.heroId == hurt.heroId }.blocked)
        assertNull("an unfit member is never part of the party", pick.picks.first { it.heroId == hurt.heroId }.order)
        assertEquals("Pick one to ${cfg.partyMax} members.", pick.sendBlocked)

        // A party on a two-day road over the siege night: each of them is missing, by name, with the morning they return.
        val away = (7L..14L).asSequence().flatMap { mornings(GuildPolicy.GREEDY_DIVER, it) }.firstOrNull { st -> engine.wallForecast(st)!!.missing.any { it.second.startsWith("away until the morning of day") } }
        assertNotNull("no seed sent a party over a siege night", away)
        val awayUi = engine.guildUi(away!!)!!
        val mission = away.guild!!.mission!!
        assertTrue(awayUi.wall.missing.toString(), mission.party.all { id -> "${away.heroes.getValue(id).fullName}: away until the morning of day ${mission.returnDay}" in awayUi.wall.missing })
        assertEquals("Home on the morning of day ${mission.returnDay}.", awayUi.party!!.returns)
        assertEquals("Away until the morning of day ${mission.returnDay}", awayUi.roster.first { it.heroId == mission.party[0] }.status)
        assertNotNull(awayUi.boardNote)
    }

    @Test
    fun aCheckpointShowsWhatIsSecuredWhatDeeperAddsAndWhatSilenceMeans() {
        val s = (7L..30L).asSequence().flatMap { mornings(GuildPolicy.GREEDY_DIVER, it) }
            .firstOrNull { st -> st.guild!!.mission?.let { it.outcome == null && it.stage == 1 && it.offer.optionalPush && it.choice == null } == true }
        assertNotNull("no seed reached a checkpoint", s)
        val m = s!!.guild!!.mission!!
        val ui = engine.guildUi(s)!!
        val cp = ui.party!!.checkpoint!!
        assertEquals(m.id, cp.instanceId)
        assertTrue(cp.secured, cp.secured.startsWith("Secured: "))
        if (m.secured.gold > 0) assertTrue(cp.secured, "${m.secured.gold} contract gold" in cp.secured)
        assertTrue(cp.deeper, cp.deeper.startsWith("Deeper adds, only if won: "))
        assertEquals("Lethal: if the whole party goes down, one dies and one is taken.", cp.danger)
        assertTrue(cp.enemies.isNotEmpty())
        assertNull(cp.chosen)
        assertEquals("If you choose nothing, End Day returns.", cp.note)
        assertEquals("The party at ${engine.content.guild!!.route(m.offer.factionId)!!.name} returns unless you send word", ui.endDay.last())
        // Word sent: the card says which, and End Day no longer warns.
        val pushed = engine.guildUi(s.send(Command.ChooseMissionCheckpoint(m.id, CheckpointChoice.PUSH)))!!
        assertEquals(CheckpointChoice.PUSH, pushed.party!!.checkpoint!!.chosen)
        assertTrue(pushed.endDay.none { "unless you send word" in it })
        // Yesterday's contract is the stage that was won.
        assertEquals("Won. The party is still out", ui.yesterday!!.outcome)
        assertEquals(s.guild!!.lastMission!!.lines, ui.yesterday!!.lines)
    }

    @Test
    fun aCandidateWhoCannotBeSignedSaysWhy() {
        val fresh = guildRun()
        val g = fresh.guild!!
        assertTrue(g.candidates.isNotEmpty())
        val rich = engine.guildUi(fresh.copy(gold = 10_000))!!
        assertTrue(rich.candidates.all { it.blocked == null })
        assertEquals("The list is redrawn on the morning of day ${g.candidatesDay + cfg.candidateRefreshDays}.", rich.candidatesNote)
        val c = g.candidates.first()
        val trait = engine.content.guild!!.trait(c.traitId)!!
        val row = rich.candidates.first { it.heroId == c.heroId }
        assertEquals("${trait.name}: ${trait.description}", row.trait)
        assertEquals("Fee ${c.fee} gold · keeps ${c.sharePercent}% of contract gold", row.terms)
        assertEquals(engine.content.combat!!.kitByClass.getValue(fresh.heroes.getValue(c.heroId).classId).role, row.role)

        // Gold short: the reason names both numbers, and the engine refuses for the same reason.
        val poor = fresh.copy(gold = c.fee - 1)
        assertEquals("Needs ${c.fee} gold. You have ${c.fee - 1}.", engine.guildUi(poor)!!.candidates.first { it.heroId == c.heroId }.blocked)
        assertEquals(GameError.NotEnoughGold(c.fee, c.fee - 1), (engine.handle(poor, Command.RecruitHero(c.heroId)) as CommandOutcome.Rejected).error)

        // Roster full: said before the purse, as the engine checks it.
        val extra = fresh.residents().filter { r -> g.candidates.none { it.heroId == r.id } }.take(cfg.maxMembers - g.members.size).map { GuildMember(it.id, fresh.day, 0, 10, g.members.first().traitId) }
        val full = fresh.copy(gold = 0, guild = g.copy(members = g.members + extra))
        assertEquals(cfg.maxMembers, full.guild!!.members.size)
        assertTrue(engine.guildUi(full)!!.candidates.all { it.blocked == "The roster is full (${cfg.maxMembers} members)." })
        assertEquals(GameError.RosterFull, (engine.handle(full, Command.RecruitHero(c.heroId)) as CommandOutcome.Rejected).error)
    }

    @Test
    fun theContractSheetReadsThePartyFromItsKitsAndBlades() {
        // A morning on which a member at home carries a blade of the forge.
        val s = (7L..14L).asSequence().flatMap { mornings(GuildPolicy.ADAPTIVE, it, 12) }.first { st ->
            st.guild!!.mission == null && st.guild!!.members.all { engine.memberUnavailable(st, it.heroId) == null } &&
                st.guild!!.members.any { m -> st.heroes.getValue(m.heroId).classId != LaunchContent.WARDEN && st.loanOf(m.heroId) != null } &&
                st.guild!!.members.any { st.heroes.getValue(it.heroId).classId == LaunchContent.WARDEN }
        }.let { st -> st.copy(guild = st.guild!!.copy(reserved = emptyList())) }
        val warden = s.guild!!.members.first { s.heroes.getValue(it.heroId).classId == LaunchContent.WARDEN }.heroId
        val bearer = s.guild!!.members.first { s.heroes.getValue(it.heroId).classId != LaunchContent.WARDEN && s.loanOf(it.heroId) != null }.heroId
        val blade = s.loanOf(bearer)!!
        // The same blade with Binding Salt in it (the Capacitor): healing its bearer stores Charge.
        val salted = s.copy(weapons = s.weapons + (blade.id to blade.copy(catalystId = LaunchContent.BINDING_SALT, location = WeaponLocation.Loaned(bearer))))
        val offer = salted.guild!!.offers.first { it.stages.first().enemies.isNotEmpty() }
        val both = engine.contractUi(salted, offer.id, listOf(bearer, warden), Posture.BALANCED)!!
        val w = salted.heroes.getValue(warden).name
        val b = salted.heroes.getValue(bearer).name
        assertTrue(both.together.toString(), "$w's healing charges $b's weapon (Capacitor)." in both.together)
        assertFalse(both.watchOut.toString(), "Nobody in this party heals." in both.watchOut)
        assertEquals(listOf(1, 2), both.picks.filter { it.order != null }.sortedBy { it.order }.map { it.order })
        assertEquals(bearer, both.picks.first { it.order == 1 }.heroId)
        // Alone, the bearer has nobody to heal them: no pairing, and the sheet says what is missing.
        val alone = engine.contractUi(salted, offer.id, listOf(bearer), Posture.BALANCED)!!
        assertTrue(alone.together.toString(), alone.together.none { "healing" in it })
        assertTrue(alone.watchOut.toString(), "Nobody in this party heals." in alone.watchOut)
        // The hero sheet lists the rule by its own name and description.
        val detail = engine.memberDetail(salted, bearer)!!
        val capacitor = com.tinyblacksmith.core.content.CombatContent.CAPACITOR
        assertTrue(detail.rules.toString(), (capacitor.name to capacitor.description) in detail.rules)
        assertEquals(blade.name, detail.loan!!.name)
        assertEquals(listOf("Health", "Strike", "Support"), detail.numbers.map { it.first })
        assertNull(engine.memberDetail(salted, salted.residents().first().id))
    }

    @Test
    fun theSiegeStripOfAGuildRunNamesNoOutlook() {
        for (s in mornings(GuildPolicy.ADAPTIVE, 9, 12)) {
            val classic = engine.shopUi(s, engine.siegeForecast(s)).threat ?: continue
            val ui = engine.guildUi(s)!!
            val strip = ui.threat(classic)!!
            assertEquals("Day ${s.town.nextSiegeDay} · see the wall in Guild", strip.outlook)
            assertEquals(classic.siege, strip.siege)
            if (ui.wall.committed) assertTrue(strip.matchup, strip.matchup!!.startsWith(ui.wall.besieger)) else assertEquals("Besieger not yet known", strip.matchup)
            // The wall never shows a field before the besieger is committed, and always three places at most.
            if (!ui.wall.committed) assertTrue(ui.wall.field.isEmpty())
            assertTrue(ui.wall.defenders.size <= ui.wall.places)
        }
    }
}
