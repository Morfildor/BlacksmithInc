package com.tinyblacksmith.core

import com.tinyblacksmith.core.ScenarioSaves.Scenario
import com.tinyblacksmith.core.TestSupport.engine
import com.tinyblacksmith.core.TestSupport.run
import com.tinyblacksmith.core.content.CombatContent
import com.tinyblacksmith.core.content.GuildContent
import com.tinyblacksmith.core.content.LaunchContent
import com.tinyblacksmith.core.content.MissionArchetype
import com.tinyblacksmith.core.crafting.Forge
import com.tinyblacksmith.core.engine.Command
import com.tinyblacksmith.core.engine.ResolutionContext
import com.tinyblacksmith.core.engine.stateOrThrow
import com.tinyblacksmith.core.guild.Missions
import com.tinyblacksmith.core.model.*
import com.tinyblacksmith.core.rng.RngStream
import com.tinyblacksmith.core.sim.GuildBot
import com.tinyblacksmith.core.sim.GuildPolicy

/**
 * Scenario saves of a guild run (docs/GUILD_EVOLUTION_PLAN.md), all under Town's Last Hope. Three are played: a
 * simulator guild bot ([GuildBot.planDay], then End Day) takes a seed to a pinned morning. Three are constructed on
 * top of real commands, and [Scenario.built] says what was set by hand. None shows a card of the classic aftermath, so
 * each is listed as "in the save already" and its description says what to press.
 *
 * The pins hold for the rules and balance they were found under; [candidates] prints new ones.
 */
object GuildScenarios {
    private val content = engine.content
    private val config = engine.config
    private val guildContent = content.guild!!
    const val CHARTER = GuildContent.TOWNS_LAST_HOPE

    fun fresh(seed: Long): GameState = engine.newRun(LegacyProfile(), seed, charterId = CHARTER)

    /** The guild run of [seed] as [policy] plays it, up to the morning of [day] with nothing done on it yet. */
    fun morning(seed: Long, policy: GuildPolicy, day: Int): GameState {
        val bot = GuildBot(engine, policy)
        var s = fresh(seed)
        while (!s.isEnded && s.day < day) s = bot.planDay(s).run(Command.EndDay(TestSupport.endDayId(s)))
        check(s.day == day && !s.isEnded) { "$policy seed $seed ended on day ${s.day}" }
        return s
    }

    /** A blessing on offer is taken, so the save opens on its own situation and not on that choice. */
    private fun GameState.settled(): GameState = pendingBlessingOffer.firstOrNull()?.let { run(Command.ChooseBlessing(it)) } ?: this

    private fun GameState.with(w: Weapon) = copy(weapons = weapons + (w.id to w))
    private fun GameState.with(h: Hero) = copy(heroes = heroes + (h.id to h))
    private fun GameState.member(cls: HeroClassId): Hero = guild!!.members.map { hero(it.heroId) }.first { it.classId == cls }
    private fun archetype(o: MissionOffer): MissionArchetype = guildContent.mission(o.defId).archetype

    // ---- what each case is about, for the builders, the tests and the search ----

    /** The party is out after the first stage of a relic delve and the smith has not said which way. */
    fun atDelveCheckpoint(s: GameState): Boolean = s.guild?.mission?.let { it.offer.defId == GuildContent.RELIC_DELVE && Missions.atCheckpoint(it) && it.choice == null } == true

    /** A member is held and the contract that brings them back is on the board. */
    fun captiveWithRescue(s: GameState): Captive? = s.guild?.captives?.firstOrNull { c -> s.guild!!.offers.any { it.defId == GuildContent.RESCUE && it.subjectHeroId == c.heroId } }

    /** A contract that would take [party] away tonight: something to fight, nothing in the way of planning it. */
    fun awayContract(s: GameState): MissionOffer? = s.guild!!.offers.firstOrNull { it.stages.first().enemies.isNotEmpty() && it.subjectHeroId == null && it.fee <= s.gold }

    // ---- the cases ----

    const val DAY_ONE_SEED = 3L

    /** Day 1: the opening relics declined, one quick iron sword forged by a real command and left in storage. */
    private fun dayOne(): GameState = fresh(DAY_ONE_SEED).run(Command.DeclineRelicOffer).run(TestSupport.quickSword(Risk.SAFE))

    const val CHECKPOINT_SEED = 1L
    const val CHECKPOINT_DAY = 10

    const val SIEGE_EVE_SEED = 1L

    /** The morning of the first siege, with the Guardian reserved for the wall by a real command. */
    private fun siegeEve(): GameState {
        val s = morning(SIEGE_EVE_SEED, GuildPolicy.SYNERGY, config.siegeInterval).settled()
        return s.run(Command.ReserveDefender(s.member(LaunchContent.GUARDIAN).id, true))
    }

    const val CAPTIVE_SEED = 1L
    const val CAPTIVE_DAY = 18

    const val CHARTER_SEED = 4L
    const val CHARTER_LEVEL = 8
    const val CHARTER_QUALITY = 90

    /** The morning of the charter day as the bot reached it: played on from here, the wall is breached. */
    fun charterEveAsPlayed(): GameState = morning(CHARTER_SEED, GuildPolicy.SYNERGY, config.guild.charterDay).settled()

    /**
     * That morning made strong by hand: every member at home is level [CHARTER_LEVEL] in full health with no wound and
     * every blade on loan to them is quality [CHARTER_QUALITY] in full repair. Then the Guardian, the Warden and the
     * Battlemage are reserved for the wall by real commands, so it is they who meet the warlord.
     */
    private fun charterEve(): GameState {
        var s = charterEveAsPlayed()
        val home = s.guild!!.members.filter { it.status == MemberStatus.HOME }.map { it.heroId }.toSet()
        for (id in home) s = s.with(s.hero(id).copy(level = maxOf(s.hero(id).level, CHARTER_LEVEL), health = 100))
        s = s.copy(guild = s.guild!!.copy(members = s.guild!!.members.map { if (it.heroId in home) it.copy(wound = null) else it }))
        for (w in s.weapons.values.filter { it.loanedTo in home }) s = s.with(strong(w))
        for (cls in listOf(LaunchContent.GUARDIAN, LaunchContent.WARDEN, LaunchContent.BATTLEMAGE)) s = s.run(Command.ReserveDefender(s.member(cls).id, true))
        return s
    }

    private fun strong(w: Weapon): Weapon = maxOf(w.quality, CHARTER_QUALITY).let { q ->
        w.copy(quality = q, rarity = Forge.rarityFor(q, config), power = maxOf(w.power, Forge.powerOf(content, config, w.familyId, w.coreId, q, w.affixes + w.flaws)), condition = 100)
    }

    const val STORMWELL_SEED = 3L

    /**
     * Stormwell (spec 8.1) put together on day 1: the Battlemage the charter offers is signed and both blades are forged
     * and loaned by real commands; the gold, the materials and the energy for that, the relic and the Hunt are set by hand.
     */
    private fun stormwell(): GameState {
        var s = fresh(STORMWELL_SEED).run(Command.DeclineRelicOffer)
        val mage = s.guild!!.candidates.first { s.hero(it.heroId).classId == LaunchContent.BATTLEMAGE }
        s = s.copy(
            gold = s.gold + 300, energy = s.energy + 2 * config.advancedForgeEnergy,
            materials = s.materials + listOf(LaunchContent.IRON, LaunchContent.STORMGLASS, LaunchContent.VERDANT_SAP, LaunchContent.BINDING_SALT).associateWith { (s.materials[it] ?: 0) + 2 },
            relics = listOf(ActiveRelic(CombatContent.OVERFLOW_BASIN)),
        ).run(Command.RecruitHero(mage.heroId))
        val sword = engine.handle(s, Command.Forge(ForgeMode.ADVANCED, LaunchContent.SWORD, LaunchContent.IRON, LaunchContent.STORMGLASS, LaunchContent.BINDING_SALT, Risk.SAFE)).let { s = it.stateOrThrow(); (it as com.tinyblacksmith.core.engine.CommandOutcome.Accepted).forgedWeaponId!! }
        val staff = engine.handle(s, Command.Forge(ForgeMode.QUICK, LaunchContent.STAFF, LaunchContent.IRON, LaunchContent.VERDANT_SAP, null, Risk.SAFE)).let { s = it.stateOrThrow(); (it as com.tinyblacksmith.core.engine.CommandOutcome.Accepted).forgedWeaponId!! }
        s = s.run(Command.LoanWeapon(s.member(LaunchContent.GUARDIAN).id, sword)).run(Command.LoanWeapon(s.member(LaunchContent.WARDEN).id, staff))
        // The Hunt is posted from day 2 on: this one is drawn for today as the board would draw it, against the faction that presses hardest.
        // The context is thrown away after the draw, so the run's own GUILD stream stands where it stood.
        val ctx = ResolutionContext(s, content, config)
        val g = s.guild!!
        val faction = s.factions.values.maxWith(compareBy<FactionState> { it.pressure }.thenBy { it.id.value }).id
        val hunt = Missions.generate(ctx, guildContent.mission(GuildContent.HUNT), faction, ctx.rng(RngStream.GUILD), "m${g.nextMissionSerial}")
        return s.copy(guild = g.copy(offers = g.offers.filter { it.defId != GuildContent.HUNT } + hunt, nextMissionSerial = g.nextMissionSerial + 1))
    }

    private const val TLH = "a guild run under Town's Last Hope"

    val all: List<Scenario> = listOf(
        Scenario("guild_day_one", "Guild: day one, the first loan",
            "Day 1 with a Guardian and a Warden signed and one sword in storage. Open the Guardian and loan them the sword: it leaves storage and shows in their hand; recall it and it is back.",
            "Constructed: $TLH on seed $DAY_ONE_SEED; the opening relic offer was declined and one quick iron sword forged, both by real commands.", endDay = false,
            blade = { it.storedWeapons().singleOrNull() }) { dayOne() },
        Scenario("guild_checkpoint", "Guild: a party at a relic delve's checkpoint",
            "The party won the first stage of a relic delve and waits. The Guild screen asks: push on (the deeper stage, at its own risk) or return with what is secured. Choose, then press End Day.",
            "Played: $TLH, GREEDY_DIVER, seed $CHECKPOINT_SEED, stopped on the morning of day $CHECKPOINT_DAY; a blessing on offer was taken.", endDay = false) { morning(CHECKPOINT_SEED, GuildPolicy.GREEDY_DIVER, CHECKPOINT_DAY).settled() },
        Scenario("guild_siege_eve", "Guild: the morning of a siege, one reserved",
            "A siege comes tonight and the Guardian is kept for the wall. A contract on the board would take the others away: plan it and the wall's forecast names who is left; press End Day and the siege card says who stood.",
            "Played: $TLH, SYNERGY, seed $SIEGE_EVE_SEED, stopped on the morning of day ${config.siegeInterval} (the siege day); a blessing on offer was taken and the Guardian reserved by a real command.", endDay = false) { siegeEve() },
        Scenario("guild_captive", "Guild: a member held, a rescue posted",
            "A member is in enemy hands with a deadline, and the Rescue contract for them is on the board. Send a party: a won rescue brings them home, a missed deadline does not.",
            "Played: $TLH, GREEDY_DIVER, seed $CAPTIVE_SEED, stopped on the morning of day $CAPTIVE_DAY; a blessing on offer was taken.", endDay = false) { morning(CAPTIVE_SEED, GuildPolicy.GREEDY_DIVER, CAPTIVE_DAY).settled() },
        Scenario("guild_charter_eve", "Guild: the charter siege, a strong roster",
            "The morning of day ${config.guild.charterDay}: the warlord leads tonight's siege. Press End Day: the wall holds, the charter is secured, and the run can go on or be closed with it.",
            "Constructed: $TLH, SYNERGY, seed $CHARTER_SEED, played to the morning of day ${config.guild.charterDay} (left as played, the wall is breached that night). Set by hand: every member at home raised to level $CHARTER_LEVEL in full health with wounds cleared, every blade on loan to them raised to quality $CHARTER_QUALITY and full repair. By real commands: a blessing on offer taken; the Guardian, the Warden and the Battlemage reserved for the wall.",
            endDay = false) { charterEve() },
        Scenario("guild_stormwell", "Guild: Stormwell",
            "The Guardian carries a Stormglass sword forged with Binding Salt, the Warden a staff, a Battlemage has signed, the Overflow Basin is held and a Hunt is on the board. Send the three on the Hunt and press End Day: the party's card tells the chain.",
            "Constructed: $TLH on seed $STORMWELL_SEED, day 1. By real commands: the relic offer declined, the offered Battlemage signed, the sword (Advanced, Iron, Stormglass, Binding Salt) and the staff (Iron, Verdant Sap) forged and loaned. Set by hand: 300 gold, the materials and energy for the two forges, the Overflow Basin in the workshop, and a Hunt generated onto the board a day early (drawn from a copy of the run's guild stream: the saved stream is not advanced).",
            endDay = false, blade = { s -> s.loanOf(s.member(LaunchContent.GUARDIAN).id) }) { stormwell() },
    )

    /** The first mornings, over [seeds] and the two policies that go deep, on which the played cases are there. Run it to re-pin. */
    fun candidates(seeds: LongRange = 1L..60L, perCase: Int = 5, days: Int = 24): String = buildString {
        val found = linkedMapOf<String, MutableList<String>>()
        fun hit(case: String, policy: GuildPolicy, s: GameState, note: String = "") { found.getOrPut(case) { mutableListOf() }.let { if (it.size < perCase) it += "$policy seed ${s.seed} day ${s.day} $note" } }
        for (policy in listOf(GuildPolicy.GREEDY_DIVER, GuildPolicy.HIGHEST_STAT, GuildPolicy.SYNERGY)) for (seed in seeds) {
            val bot = GuildBot(engine, policy)
            var s = fresh(seed)
            while (!s.isEnded && s.day <= days) {
                if (atDelveCheckpoint(s)) hit("guild_checkpoint", policy, s)
                captiveWithRescue(s)?.let { hit("guild_captive", policy, s, "${s.hero(it.heroId).fullName} until day ${it.deadlineDay}, ${s.guild!!.members.count { m -> m.status == MemberStatus.HOME }} at home") }
                if (s.day == config.siegeInterval && s.town.nextSiegeDay == s.day && s.guild!!.mission == null && awayContract(s) != null) hit("guild_siege_eve", policy, s, "${s.guild!!.members.size} members")
                if (s.day == config.guild.charterDay && s.guild!!.mission == null) hit("guild_charter_eve", policy, s, "${s.guild!!.members.count { m -> m.status == MemberStatus.HOME }} at home, integrity ${s.town.integrity}")
                s = bot.planDay(s).run(Command.EndDay(TestSupport.endDayId(s)))
            }
        }
        for ((case, hits) in found) { appendLine(case); hits.forEach { appendLine("  $it") } }
    }
}
