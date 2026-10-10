package com.tinyblacksmith.core

import com.tinyblacksmith.core.TestSupport.endDay
import com.tinyblacksmith.core.TestSupport.engine
import com.tinyblacksmith.core.TestSupport.forgeAccepted
import com.tinyblacksmith.core.TestSupport.run
import com.tinyblacksmith.core.ScenarioSaves.Scenario
import com.tinyblacksmith.core.content.Depth
import com.tinyblacksmith.core.content.LaunchContent
import com.tinyblacksmith.core.crafting.Forge
import com.tinyblacksmith.core.engine.Command
import com.tinyblacksmith.core.engine.Encounters
import com.tinyblacksmith.core.engine.GameEngine
import com.tinyblacksmith.core.engine.stateOrThrow
import com.tinyblacksmith.core.model.*
import com.tinyblacksmith.core.sim.Policy

/**
 * Scenario saves for morning visitors, workshop relics, siege traits and the wall-pledge chain. Unlike the played cases
 * of [ScenarioSaves] these are constructed: a fresh run is taken to day 2 by real commands, then the one thing the case
 * is about is set by hand (which visitor came, which relic is held, the trait of the coming siege), because which
 * visitor a morning brings is a draw. Every [Scenario.built] says what was set.
 */
object DepthScenarios {
    private val content = engine.content
    private val config = engine.config

    /**
     * Day 2 of a fresh run on [seed]: the opening relic offer declined, six of every material added by hand, four quick
     * blades of four families forged by real commands on day 1, then End Day. 400 gold, no visitor, no relic.
     */
    fun workshop(seed: Long = 3): GameState {
        var s = engine.newRun(LegacyProfile(), seed).run(Command.DeclineRelicOffer)
        s = s.copy(materials = content.materials.associate { it.id to (s.materials[it.id] ?: 0) + 6 })
        for (family in content.families.take(4)) s = s.run(Command.Forge(ForgeMode.QUICK, family.id, LaunchContent.IRON, LaunchContent.EMBER_RESIN, null, Risk.SAFE))
        return s.endDay().copy(gold = 400, encounter = null, pendingRelicOffer = emptyList())
    }

    private fun GameState.visitor(defId: String): GameState = checkNotNull(Encounters.force(this, content, config, defId)) { "$defId is not eligible" }
    private fun GameState.answer(option: String) = run(Command.ResolveEncounter(encounter!!.id, option, CommandId("scenario:$option")))
    private fun GameState.holding(vararg relics: ActiveRelic) = copy(relics = relics.toList())
    private fun GameState.blade(): Weapon = storedWeapons().first()
    private fun GameState.with(w: Weapon) = copy(weapons = weapons + (w.id to w))
    private fun GameState.with(h: Hero) = copy(heroes = heroes + (h.id to h))

    private fun famous(s: GameState) = s.with(s.blade().copy(fame = 3))

    private fun thinPurse(s: GameState): GameState = s.aliveHeroes().let { h -> s.with(h[0].copy(gold = 5)).with(h[1].copy(gold = 900)) }

    private fun fallenComrade(s: GameState): GameState {
        val heroes = s.aliveHeroes()
        val fallen = heroes.last().copy(fate = HeroFate.DEAD, health = 0, diedOnDay = 1, guildId = "g1")
        val blade = s.blade()
        return s.with(fallen).with(heroes[0].copy(guildId = "g1")).copy(town = s.town.copy(championIds = s.town.championIds - fallen.id))
            .with(blade.copy(condition = 40, history = blade.history + HistoryEntry(s.era, 1, "RECOVERED", "Recovered after ${fallen.fullName}'s death and returned to the forge.", listOf(fallen.id.value))))
    }

    /** The pledge taken and its blade forged, with the siege moved to tonight (or two days out) and made weak so the town holds. */
    private fun pledgeDelivered(siegeTonight: Boolean): GameState {
        var s = thinPurse(workshop()).visitor(Depth.BLADE_FOR_THE_WALL).answer("pledge")
        val order = s.commissions.values.single { it.kind == CommissionKind.WALL_PLEDGE }
        val made = s.copy(energy = s.energy + config.quickForgeEnergy).forgeAccepted(Command.Forge(ForgeMode.QUICK, order.familyId, LaunchContent.BRONZE, LaunchContent.FROST_BLOOM, null, Risk.SAFE))
        s = made.state.with(made.state.weapon(made.forgedWeaponId!!).let { it.copy(quality = maxOf(it.quality, 70), power = maxOf(it.power, 45)) })
        val siegeDay = if (siegeTonight) s.day else s.day + 2
        // The night is resolved with a raid a tenth as strong, so the town holds whoever stands on the wall.
        val night = s.copy(town = s.town.copy(nextSiegeDay = siegeDay), siege = SiegeScenario(siegeDay))
        val morning = GameEngine(content, config.copy(siegeModifier = 0.1)).handle(night, Command.EndDay(TestSupport.endDayId(night))).stateOrThrow()
        // The blessing a held siege offers is taken here, so the save opens on the visitor and not on that choice.
        return morning.pendingBlessingOffer.firstOrNull()?.let { engine.handle(morning, Command.ChooseBlessing(it)).stateOrThrow() } ?: morning
    }

    private fun pledgeHeroDead(): GameState {
        val s = pledgeDelivered(siegeTonight = false)
        val due = s.consequences.single { it.kind == ConsequenceKind.WALL_PLEDGE }
        val hero = s.hero(due.heroId!!)
        return s.with(hero.copy(fate = HeroFate.DEAD, health = 0, diedOnDay = s.day)).with(s.weapon(due.weaponId!!).copy(location = WeaponLocation.Lost(s.day, "seized")))
            .copy(day = due.dueDay - 1, encounter = null, town = s.town.copy(championIds = s.town.championIds - hero.id, nextSiegeDay = due.dueDay + 4), siege = SiegeScenario(due.dueDay + 4))
    }

    private fun siege(trait: String): GameState =
        ScenarioSaves.morning(1, Policy.BALANCED_ACTIVE, 8).let { it.copy(siege = SiegeScenario(it.town.nextSiegeDay, trait, it.siege?.factionId), encounter = null) }

    private const val SET_UP = "Constructed: a fresh run on seed 3, four quick blades forged on day 1, End Day; on day 2"

    private fun visitorCase(id: String, title: String, description: String, built: String, build: () -> GameState) =
        Scenario(id, title, "Shop: a visitor is at the forge. $description", "$SET_UP $built", endDay = false, build = build)

    val all: List<Scenario> = listOf(
        visitorCase("visitor_collector", "Visitor: the collector's offer", "Sell the famous blade for good, or keep it for the town.",
            "one stored blade was given 3 fame and the collector was made this morning's visitor.") { famous(workshop()).visitor(Depth.COLLECTORS_OFFER) },
        visitorCase("visitor_crate_no_gold", "Visitor: the last crate, with an empty purse", "Both purchases are closed with the reason shown; only the free answer can be committed.",
            "the forge's gold was set to 0 and the carter was made this morning's visitor (the siege is three days out).") { workshop().copy(gold = 0).visitor(Depth.LAST_CRATE) },
        visitorCase("visitor_pledge", "Visitor: a blade for the wall", "Arm the poor defender on trust (this starts the chain), take the rich patron's order, or neither.",
            "one hero's purse was set to 5 gold, another's to 900, and the two were made this morning's visitors.") { thinPurse(workshop()).visitor(Depth.BLADE_FOR_THE_WALL) },
        visitorCase("visitor_master", "Visitor: the master's afternoon", "Four energy for a journal entry, coin for a recipe clue, or keep working.",
            "the wandering master was made this morning's visitor.") { workshop().visitor(Depth.MASTERS_AFTERNOON) },
        visitorCase("visitor_heirloom", "Visitor: a cracked family blade", "Restore a fallen hero's blade for their guildmate, sell it to a collector, or keep it.",
            "one hero was marked fallen on day 1 with a stored blade recorded as recovered from them, a living hero was put in the same guild, and they were made this morning's visitor.") { fallenComrade(workshop()).visitor(Depth.CRACKED_FAMILY_BLADE) },
        visitorCase("visitor_merchant", "Visitor: the crooked merchant", "Buy unseen, pay to look the blade over (the sheet stays open and shows it), or leave.",
            "the merchant was made this morning's visitor.") { workshop().visitor(Depth.CROOKED_MERCHANT) },
        visitorCase("visitor_wager", "Visitor: the smith's wager", "Stake gold on forging the named blade in three days for a relic, take a plain order, or refuse.",
            "the passing smith was made this morning's visitor.") { workshop().visitor(Depth.SMITHS_WAGER) },
        visitorCase("visitor_festival", "Visitor: the festival contract", "A stall brings the crowd today; arming the watch pays a bounty for two days.",
            "the council was made this morning's visitor.") { workshop().visitor(Depth.FESTIVAL_CONTRACT) },
        visitorCase("visitor_unanswered", "Visitor: left unanswered", "Do not answer. The End Day button says what the visitor will be told; press it and Records shows that he left.",
            "the merchant was made this morning's visitor (seed 4).") { workshop(4).visitor(Depth.CROOKED_MERCHANT) },
        Scenario("relic_offer", "Relics: the opening draft", "Day 1: three relics are offered, one may be taken. Decide later and reopen it from Workshop relics in the Shop.",
            "A fresh run on seed 3, nothing done.", endDay = false) { engine.newRun(LegacyProfile(), 3) },
        Scenario("relic_crucible", "Relic: Salvager's Crucible", "Open Storage and the fine blade: Salvage says it also returns the augment. Do it twice: the second gives the core only.",
            "$SET_UP the crucible was put in the workshop and two stored blades were raised to quality 55.", endDay = false,
        ) { workshop().holding(ActiveRelic(Depth.SALVAGERS_CRUCIBLE)).let { s -> s.storedWeapons().take(2).fold(s) { acc, w -> acc.with(w.copy(quality = 55, rarity = Forge.rarityFor(55, config))) } } },
        Scenario("relic_ledger", "Relic: Tempering Ledger", "Forge: a family outside the streak shows its quality bonus, one inside says it restarts the streak.",
            "$SET_UP the ledger was put in the workshop with a streak of Sword and Axe.", endDay = false,
        ) { workshop().holding(ActiveRelic(Depth.TEMPERING_LEDGER, families = listOf(LaunchContent.SWORD, LaunchContent.AXE))) },
        Scenario("relic_seal", "Relic: Collector's Seal", "Workshop relics shows two seals of three. A shelf sale of ${config.depth.sealMinCash} gold or more in coin earns the third and a rare material.",
            "$SET_UP the seal was put in the workshop with two seals, one stored blade was raised to power 80 and every blade was listed at its going rate.", endDay = false,
        ) {
            var s = workshop().holding(ActiveRelic(Depth.COLLECTORS_SEAL, progress = 2))
            s = s.with(s.blade().copy(power = 80, quality = 70, rarity = Forge.rarityFor(70, config)))
            for (w in s.storedWeapons()) s = s.run(Command.ToggleShelf(w.id, listed = true))
            s
        },
        Scenario("relic_bellows", "Relic: Ashen Bellows", "Forge: switch the bellows on for one blade. It gains a property; tomorrow starts ${config.depth.bellowsDebt} energy short, as End Day warns.",
            "$SET_UP the bellows were put in the workshop.", endDay = false) { workshop().holding(ActiveRelic(Depth.ASHEN_BELLOWS)) },
        Scenario("relic_full_workshop", "Relics: a fourth for a full workshop", "Three relics are held and the fourth is offered: taking it means choosing which one to put away.",
            "$SET_UP three relics were put in the workshop (two seals earned) and the bellows were offered.", endDay = false,
        ) { workshop().holding(ActiveRelic(Depth.SALVAGERS_CRUCIBLE), ActiveRelic(Depth.TEMPERING_LEDGER, families = listOf(LaunchContent.SWORD)), ActiveRelic(Depth.COLLECTORS_SEAL, progress = 2)).copy(pendingRelicOffer = listOf(Depth.ASHEN_BELLOWS)) },
        Scenario("siege_long_assault", "Siege trait: Long Assault", "Town shows the trait two days before the siege: worn blades count for less. Compare the outlook after giving blades to the watch.",
            "Played: BALANCED_ACTIVE, seed 1, stopped on the morning of day 8; the trait of the day 10 siege was set by hand.", endDay = false) { siege(Depth.LONG_ASSAULT) },
        Scenario("siege_many_breaches", "Siege trait: Many Breaches", "Town shows the trait two days before the siege: the watch and militia count for more, the raid is stronger.",
            "Played: BALANCED_ACTIVE, seed 1, stopped on the morning of day 8; the trait of the day 10 siege was set by hand.", endDay = false) { siege(Depth.MANY_BREACHES) },
        Scenario("chain_debt_repaid", "Chain: the debt repaid", "The defender armed on trust came through the siege and is back at the forge: collect, forgive, or let it rest.",
            "$SET_UP a hero's purse was set to 5 gold and their pledge taken by a real answer; the blade was forged (raised by hand to quality 70), the siege moved to that night and its raid cut to a tenth so the town held. This is the next morning.",
            endDay = false) { pledgeDelivered(siegeTonight = true) },
        Scenario("chain_pledge_dead", "Chain: the defender did not live", "Press End Day: no visitor comes tomorrow. Records tells that the hero died and where the blade is.",
            "$SET_UP the pledge was taken and delivered by real commands as in the case above; then the hero was marked dead, the blade seized, and the day moved to the eve of the pledge falling due.",
            endDay = false) { pledgeHeroDead() },
    )
}
