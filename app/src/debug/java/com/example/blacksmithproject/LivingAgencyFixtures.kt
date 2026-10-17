package com.example.blacksmithproject

import com.tinyblacksmith.core.content.GuildContent
import com.tinyblacksmith.core.content.LaunchContent
import com.tinyblacksmith.core.engine.Command
import com.tinyblacksmith.core.engine.CommandOutcome
import com.tinyblacksmith.core.engine.GameEngine
import com.tinyblacksmith.core.model.Concern
import com.tinyblacksmith.core.model.ConcernKind
import com.tinyblacksmith.core.model.Departure
import com.tinyblacksmith.core.model.Bond
import com.tinyblacksmith.core.model.EncounterInstance
import com.tinyblacksmith.core.model.EnemySpec
import com.tinyblacksmith.core.model.Wound
import com.tinyblacksmith.core.model.WoundKind
import com.tinyblacksmith.core.model.ForgeMode
import com.tinyblacksmith.core.model.GameState
import com.tinyblacksmith.core.model.HeroId
import com.tinyblacksmith.core.model.Hook
import com.tinyblacksmith.core.model.HookKind
import com.tinyblacksmith.core.model.LegacyProfile
import com.tinyblacksmith.core.model.LoanReturnIntent
import com.tinyblacksmith.core.model.ReturnReason
import com.tinyblacksmith.core.model.Risk
import com.tinyblacksmith.core.model.SiegeScenario
import com.tinyblacksmith.core.model.SpecialityLean
import com.tinyblacksmith.core.model.VisitKind

/**
 * Debug builds only: a guild era in which the members decide for themselves (living heroes final design 8 to 10), for the
 * roster, the member sheet, the board, the shop day and the Wall. The days are played by the real engine (a smith who
 * forges and lists every morning) until a member has bought a weapon at the counter with their own gold; then, by hand: a
 * Speciality lean, a loan the member means to return, a departure notice, a personal offer on the board and the army
 * "Powder at the Gate" (with its Goblin Sapper) committed for the coming siege. A constructed case, not natural play.
 */
object LivingAgencyFixtures {
    val engine = GameEngine()

    class Fixture(val state: GameState, val buyer: HeroId?, val leaning: HeroId, val departing: HeroId)

    private fun GameState.tryRun(command: Command): GameState = (engine.handle(this, command) as? CommandOutcome.Accepted)?.state ?: this
    private fun GameState.must(command: Command): GameState = engine.handle(this, command).let { (it as? CommandOutcome.Accepted)?.state ?: error("$command: $it") }


    /** A plain morning: take any offer, forge three quick iron spears and put everything stored on the shelf. */
    private fun GameState.morning(): GameState {
        var s = this
        s.pendingBlessingOffer.firstOrNull()?.let { s = s.tryRun(Command.ChooseBlessing(it)) }
        repeat(3) {
            if ((s.materials[LaunchContent.IRON] ?: 0) == 0) s = s.tryRun(Command.BuyMaterial(LaunchContent.IRON))
            if ((s.materials[LaunchContent.FROST_BLOOM] ?: 0) == 0) s = s.tryRun(Command.BuyMaterial(LaunchContent.FROST_BLOOM))
            s = s.tryRun(Command.Forge(ForgeMode.QUICK, LaunchContent.SPEAR, LaunchContent.IRON, LaunchContent.FROST_BLOOM, null, Risk.BALANCED))
        }
        for (w in s.storedWeapons()) s = s.tryRun(Command.ToggleShelf(w.id, listed = true))
        return s
    }

    /** [visitor]: one of the living visitors (`Hooks.CREDITOR`, `LESSON`, `WOUNDED_FRIEND`) waiting this morning, or none. */
    fun build(seed: Long = 9, visitor: String? = null): Fixture {
        val fresh = engine.newRun(LegacyProfile(), seed, charterId = GuildContent.TOWNS_LAST_HOPE)
        var s = (engine.handle(fresh, Command.ChooseOpening(fresh.guild!!.openingOffer.first().defId)) as CommandOutcome.Accepted).state
        // Play mornings and End Days, before the first siege, until a member has bought at the counter.
        while (s.day < s.town.nextSiegeDay - 1 && s.lastResolution?.visits?.none { it.kind == VisitKind.MEMBER } != false) {
            val next = s.morning()
            s = next.tryRun(Command.EndDay(com.tinyblacksmith.core.model.CommandId("fixture-day-${next.day}")))
            if (s.isEnded) break
        }
        val buyer = s.lastResolution?.visits?.firstOrNull { it.kind == VisitKind.MEMBER }?.heroId
        val g0 = s.guild!!
        val members = g0.members.map { it.heroId }
        val leaning = members.first { it != buyer }
        val departing = buyer ?: members.last()
        // A loan for the member who owns a weapon now: the smith's stored weapon, through the real command.
        if (buyer != null && s.storedWeapons().isEmpty() && s.weapons.values.none { it.isListed }) {
            s = s.tryRun(Command.BuyMaterial(LaunchContent.IRON)).tryRun(Command.BuyMaterial(LaunchContent.FROST_BLOOM))
            s = s.must(Command.Forge(ForgeMode.QUICK, LaunchContent.SWORD, LaunchContent.IRON, LaunchContent.FROST_BLOOM, null, Risk.BALANCED))
        }
        val stored = s.storedWeapons().firstOrNull { !it.isListed } ?: s.weapons.values.firstOrNull { it.isListed }
        if (buyer != null && stored != null) {
            if (stored.isListed) s = s.must(Command.ToggleShelf(stored.id, listed = false))
            s = s.must(Command.LoanWeapon(buyer, stored.id))
        }
        val cat = engine.content.guild!!
        val veteran = cat.specialities.filter { it.deed == "veteran" }
        val army = cat.armies.first { it.id == "army_ib_03" }
        val owned = buyer?.let { b -> s.weapons.values.firstOrNull { it.ownerId == b } }
        val loan = buyer?.let { b -> s.loanOf(b) }
        val g = s.guild!!
        val departure = Departure(s.day, s.town.nextSiegeDay + 2, "pay the 40 gold owed to Celine Sallow", emptyList())
        val newMembers = g.members.map { m ->
            when (m.heroId) {
                leaning -> m.copy(missionWins = 3, lean = SpecialityLean("veteran", veteran.map { it.id }, veteran.first().id, "has won 3 quests", s.day, s.day + 1))
                buyer -> m.copy(
                    loanReturn = if (owned != null && loan != null) LoanReturnIntent(loan.id, owned.id, ReturnReason.STRONGER, s.day, s.day + 1) else null,
                    concern = Concern(ConcernKind.DEBT, s.day - 1, departure.remedy, departure = departure),
                )
                else -> m
            }
        }
        val heroes = s.heroes.toMutableMap()
        heroes[departing]?.let { heroes[departing] = it.copy(hook = Hook(HookKind.DEBT, 1, creditor = "Celine Sallow", amount = 40, dueDay = s.day)) }
        // A personal offer: a quest on the board that serves the leaning member's grudge.
        val personal = g.offers.firstOrNull()?.let { o ->
            o.copy(id = "${o.id}-personal", sponsorHeroId = leaning, opportunityId = "grudge", reason = "${s.heroes.getValue(leaning).name} asks for this one: the raiders who burned a home village are camped on this road.")
        }
        val drawn = s.siege?.takeIf { it.siegeDay == s.town.nextSiegeDay } ?: SiegeScenario(s.town.nextSiegeDay)
        // A preparation drawn for another besieger does not go with this army.
        val scenario = drawn.copy(factionId = army.factionId, plan = army.members.map { EnemySpec(it, 100) }, armyId = army.id, prepId = drawn.prepId.takeIf { drawn.factionId == army.factionId })
        s = s.copy(
            heroes = heroes,
            guild = g.copy(members = newMembers, offers = listOfNotNull(personal) + g.offers, armyLog = (g.armyLog + army.id).takeLast(6)),
            siege = scenario,
        )
        if (visitor != null) {
            val g2 = s.guild!!
            val other = g2.members.first { it.heroId != departing }.heroId
            s = when (visitor) {
                com.tinyblacksmith.core.guild.Hooks.CREDITOR -> s.copy(encounter = EncounterInstance("fixture-visitor", visitor, s.day, heroId = departing, amounts = mapOf("amount" to 40)))
                com.tinyblacksmith.core.guild.Hooks.LESSON -> s.copy(
                    heroes = s.heroes + (other to s.heroes.getValue(other).copy(level = 4)),
                    encounter = EncounterInstance("fixture-visitor", visitor, s.day, heroId = other, otherHeroId = departing, amounts = mapOf("xp" to engine.config.living.lessonXp)),
                )
                else -> s.copy(
                    guild = g2.copy(members = g2.members.map { m ->
                        when (m.heroId) {
                            departing -> m.copy(wound = Wound(WoundKind.INJURED, s.day + 3, "Hurt on the Ashroad."))
                            other -> m.copy(bonds = m.bonds + Bond(departing, "comrades", s.day - 1, "Came home together."))
                            else -> m
                        }
                    }),
                    encounter = EncounterInstance("fixture-visitor", visitor, s.day, heroId = other, otherHeroId = departing, amounts = mapOf("fee" to engine.config.living.healerFee)),
                )
            }
        }
        return Fixture(s, buyer, leaning, departing)
    }
}
