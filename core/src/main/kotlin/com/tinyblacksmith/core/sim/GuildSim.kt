package com.tinyblacksmith.core.sim

import com.tinyblacksmith.core.combat.EventKind
import com.tinyblacksmith.core.combat.Posture
import com.tinyblacksmith.core.config.BalanceConfig
import com.tinyblacksmith.core.content.Element
import com.tinyblacksmith.core.content.GuildContent
import com.tinyblacksmith.core.content.MaterialCategory
import com.tinyblacksmith.core.content.MissionArchetype
import com.tinyblacksmith.core.engine.Command
import com.tinyblacksmith.core.engine.CommandOutcome
import com.tinyblacksmith.core.engine.GameEngine
import com.tinyblacksmith.core.guild.Loadout
import com.tinyblacksmith.core.model.*
import java.util.stream.Collectors
import java.util.stream.IntStream

/**
 * Headless players of a guild run (spec 17.3). Each acts only on what a player can see: the board, the roster, the
 * forecast of the wall, the forge. Every action is an engine [Command]; a rejected one is counted and the tests hold
 * the count at zero.
 */
enum class GuildPolicy(val about: String) {
    PASSIVE("ends every day and does nothing else"),
    NOVICE("takes the suggested party on the safe contract, forges what it can afford, never risks anybody"),
    SHOP_FOCUSED("keeps its best blades on the shelf, loans the rest, hires nobody"),
    HIGHEST_STAT("loans the blade with the largest number to everybody and takes the richest contract"),
    SYNERGY("forges and loans for a chain: a capacitor for the front, healing behind it, a relic that feeds it"),
    DEFENCE_FIRST("reserves the wall, takes the sabotage when it is posted, never sends the party out on a siege day"),
    GREEDY_DIVER("takes the deepest contract, reckless, and always pushes on"),
    RECOVERY_AWARE("cautious; rescues first, mends the wall when it is low, hones what cracked"),
    ADAPTIVE("changes between the others by what the board, the roster and the wall show today"),
    // Rules 6: the players the run-variety gates are measured with (docs/RUN_VARIETY_LEDGER.md).
    REPEAT_RECIPE("forges one recipe for everybody and everything, an iron sword with ember resin, and otherwise plays like the adaptive smith"),
    IGNORE_COUNTER("plays like the adaptive smith but never reads the scouts: no sabotage, no weapon made against the besieger, no wall arranged for what comes"),
    RECOMMENDED("takes the first of everything it is offered: opening, relic, contract, the suggested party, a balanced temper"),
    LEGEND_SEEKER("plays like the adaptive smith and goes after Heartfire: the starfall site, the deep hall, a taken weapon, and a Heartfire forging as soon as one can break through"),
    SUPPLY_FARMER("takes only supply runs until day 10 and sells what it forges, then plays like the adaptive smith with the gold it saved"),
    // Review 2026-10-14: is spoiling the preparation the only answer? This smith answers it every other way.
    NO_SABOTAGE("plays like the adaptive smith and reads the scouts, weapons and wall against what comes, but never takes the sabotage"),
}

data class GuildRunStats(
    val seed: Long, val policy: GuildPolicy, val days: Int, val charterDay: Int?, val defeated: Boolean, val retired: Boolean,
    val contracts: Map<String, Int>, val captured: Int, val rescued: Int, val membersDead: Int, val sieges: Map<SiegeVerdict, Int>, val combos: Int, val firstComboDay: Int?,
    val goldAtEnd: Int, val members: Int, val rejected: Int, val firstSiegeIntegrity: Int?, val loansMade: Int,
    /** Forged this run by rarity name, Legendary weapons by where they came from ("forged", "heartfire", "deed"), and the till by source. */
    val forged: Map<String, Int> = emptyMap(), val legends: Map<String, Int> = emptyMap(), val firstLegendDay: Int? = null,
    val income: Map<IncomeKind, Int> = emptyMap(), val spent: Int = 0, val overworkDays: Int = 0, val recipes: Int = 0,
    /** Each siege in order: its verdict and the forge damage it did. Then Heartfire earned and spent, deeds, the opening taken, and days with nobody fit to send. */
    val siegeLog: List<Pair<SiegeVerdict, Int>> = emptyList(), val heartfireGained: Int = 0, val heartfireForges: Int = 0, val deeds: Int = 0, val opening: String? = null, val stalledDays: Int = 0,
    val sabotaged: Int = 0, val firstLoans: Map<String, Int> = emptyMap(),
    /** The best rarity in a member's hands on the mornings of day 5 and day 20 (null: the run did not get there), the day of the run's first Epic and how it came ("forge", "exceptional", "hone"). */
    val bestDay5: Rarity? = null, val bestDay20: Rarity? = null, val firstEpicDay: Int? = null, val firstEpicBy: String? = null,
    /** Every finished contract: archetype, party size, the party's mean health when it left, won. Then what the forge holds at the end at the going rate, weapons and stock. */
    val attempts: List<Attempt> = emptyList(), val stockValue: Int = 0,
    /** Contracts taken by the modifier on their card. */
    val modifiers: Map<String, Int> = emptyMap(),
    /** Living heroes, counted from each day's own record: member purchases, announced and completed loan returns, Specialities chosen (by ID), personal offers posted (by opportunity), Hooks answered or changed, concerns, departure notices and departures. */
    val living: Map<String, Int> = emptyMap(),
)

data class Attempt(val archetype: String, val party: Int, val health: Int, val won: Boolean)

private val LaunchStaff = com.tinyblacksmith.core.content.LaunchContent.STAFF

class GuildBot(val engine: GameEngine, val policy: GuildPolicy, val charterId: String = GuildContent.TOWNS_LAST_HOPE, val horizon: Int = 30, val opening: String? = null,
    /** False plays on after the charter is secured, to the horizon or to defeat: continued play is measured apart from retirement. */
    val retire: Boolean = true,
    /** False ignores what the opening package is for: the same smith on the same seeds, as the measure of what following a plan is worth. */
    val followPlan: Boolean = true) {
    private val content = engine.content
    private val cfg = engine.config
    private val guildCat = content.guild!!
    var rejected = 0; private set
    private val contracts = HashMap<String, Int>()
    private var captured = 0; private var rescued = 0; private var dead = 0; private var loans = 0
    private val sieges = HashMap<SiegeVerdict, Int>()
    private var firstCombo: Int? = null
    private var firstSiegeIntegrity: Int? = null
    val rejections = ArrayList<String>()
    private val forged = HashMap<String, Int>()
    private val legends = HashMap<String, Int>()
    private var firstLegend: Int? = null
    private val income = HashMap<IncomeKind, Int>()
    private val living = HashMap<String, Int>()
    private var spent = 0
    private var overworkDays = 0
    private val recipes = HashSet<String>()
    private val siegeLog = ArrayList<Pair<SiegeVerdict, Int>>()
    private var heartfireGained = 0
    private var heartfireForges = 0
    private var deeds = 0
    private var stalled = 0
    private var sabotaged = 0
    private val firstLoans = HashMap<String, Int>()
    private var bestDay5: Rarity? = null; private var bestDay20: Rarity? = null
    private var firstEpicDay: Int? = null; private var firstEpicBy: String? = null
    private val attempts = ArrayList<Attempt>()
    private var leaving: Pair<Int, Int>? = null
    private val modifiers = HashMap<String, Int>()
    /** The smiths that play the whole board. */
    private val adaptive = policy in setOf(GuildPolicy.ADAPTIVE, GuildPolicy.NO_SABOTAGE, GuildPolicy.REPEAT_RECIPE, GuildPolicy.IGNORE_COUNTER, GuildPolicy.LEGEND_SEEKER, GuildPolicy.SUPPLY_FARMER)
    private val chains = policy == GuildPolicy.SYNERGY || policy == GuildPolicy.ADAPTIVE || policy == GuildPolicy.NO_SABOTAGE || policy == GuildPolicy.LEGEND_SEEKER || policy == GuildPolicy.SUPPLY_FARMER
    /** Day the supply farmer stops farming. */
    private val farmUntil = 10
    private fun farming(s: GameState) = policy == GuildPolicy.SUPPLY_FARMER && s.day <= farmUntil
    /** The opening's relic, for a smith who follows its plan. */
    private fun plan(s: GameState, relicId: String) = chains && followPlan && s.relics.any { it.id == relicId }
    private fun traitOf(s: GameState, heroId: HeroId?) = heroId?.let { s.guild?.member(it)?.traitId }
    private fun classRank(s: GameState, id: HeroId) = when (s.heroes[id]?.classId?.value) { "guardian" -> 0; "duelist" -> 1; "ranger" -> 2; "battlemage" -> 3; else -> 4 }
    /** Whoever stands first in a party: the one a plan means by "the front". */
    private fun front(s: GameState): HeroId? = s.guild!!.members.map { it.heroId }.minByOrNull { classRank(s, it) }
    private val readsScouts = policy != GuildPolicy.IGNORE_COUNTER && policy != GuildPolicy.RECOMMENDED && policy != GuildPolicy.NOVICE && policy != GuildPolicy.PASSIVE && policy != GuildPolicy.REPEAT_RECIPE

    private fun GameState.send(cmd: Command): GameState = when (val o = engine.handle(this, cmd)) {
        is CommandOutcome.Accepted -> o.state
        is CommandOutcome.Rejected -> { rejected++; if (rejections.size < 20) rejections += "day $day ${cmd::class.simpleName}: ${o.error}"; this }
    }

    /** The era feature revision the bot's runs start under. */
    var livingRevision: Int = com.tinyblacksmith.core.model.LivingRevision.CURRENT
    /** Living heroes: "answer" takes a member's personal offer first (after a rescue), "ignore" never takes one; null plays the policy as it is. */
    var personal: String? = null

    fun play(seed: Long, legacy: LegacyProfile = LegacyProfile(), onDay: (GameState) -> Unit = {}): Pair<GuildRunStats, GameState> {
        var s = engine.newRun(legacy, seed, charterId = charterId, livingRevision = livingRevision)
        s.guild?.openingOffer?.let { offer -> if (offer.isNotEmpty()) s = s.send(Command.ChooseOpening((opening?.let { want -> offer.firstOrNull { it.defId == want } } ?: offer.first()).defId)) }
        while (!s.isEnded && s.day <= horizon) {
            s = planDay(s)
            onDay(s)
            val before = s
            s = s.send(Command.EndDay(CommandId("${s.runId.value}:day${s.day}")))
            record(before, s)
            // A bot that has secured the charter closes the chapter: a completed success, not a missing defeat.
            if (retire && !s.isEnded && s.guild?.milestone(GuildContent.CHARTER_SECURED) != null && s.guild?.mission == null) s = s.send(Command.RetireAfterMilestone(GuildContent.CHARTER_SECURED))
        }
        val g = s.guild!!
        return GuildRunStats(seed, policy, s.day, g.milestone(GuildContent.CHARTER_SECURED)?.day, s.isEnded && !g.retired, g.retired, contracts.toMap(), captured, rescued, dead, sieges.toMap(), g.comboNotes.size, firstCombo,
            s.gold, g.members.size, rejected, firstSiegeIntegrity, loans, forged.toMap(), legends.toMap(), firstLegend, income.toMap(), spent, overworkDays, recipes.size,
            siegeLog.toList(), heartfireGained, heartfireForges, deeds, g.openingId, stalled, sabotaged, firstLoans.toMap(), bestDay5, bestDay20, firstEpicDay, firstEpicBy, attempts.toList(),
            s.weapons.values.filter { it.location is WeaponLocation.Storage || it.location is WeaponLocation.Shelf || it.isLoaned }.sumOf { engine.suggestedPrice(it) } + s.materials.entries.sumOf { (m, n) -> content.material(m).price * n }, modifiers.toMap(), living.toMap()) to s
    }

    private fun record(before: GameState, after: GameState) {
        val r = after.lastResolution ?: return
        if (before.overworkToday > 0) overworkDays++
        for (e in r.events.filter { it.type == EventType.WEAPON_FORGED }) {
            val rarity = e.data["rarity"] ?: continue
            forged.merge(rarity, 1, Int::plus)
            after.weapons[WeaponId(e.subjectIds.firstOrNull() ?: "")]?.let { w -> recipes += "${w.familyId.value}|${w.coreId.value}|${w.augmentId.value}" }
            if (rarity == Rarity.LEGENDARY.name) { legends.merge(if (e.data["heartfire"] == "true") "heartfire" else "forged", 1, Int::plus); if (firstLegend == null) firstLegend = r.day }
        }
        for (e in r.events.filter { it.type.name == "LEGEND_EARNED" }) { legends.merge("deed", 1, Int::plus); if (firstLegend == null) firstLegend = r.day }
        r.ledger?.let { l -> l.income.forEach { (k, v) -> income.merge(k, v, Int::plus) }; spent += l.spentPreparing }
        r.mission?.let { m -> if (!m.continues) contracts.merge("${guildCat.mission(m.defId).archetype.name}:${m.outcome.name}", 1, Int::plus) }
        r.siege?.let { sieges.merge(it.verdict, 1, Int::plus); siegeLog += it.verdict to it.forgeDamage; if (firstSiegeIntegrity == null) firstSiegeIntegrity = after.town.integrity }
        heartfireGained += r.events.filter { it.type == EventType.HEARTFIRE_GAINED }.sumOf { it.data["gained"]?.toIntOrNull() ?: 0 }
        heartfireForges += r.events.count { it.type == EventType.WEAPON_FORGED && it.data["heartfire"] == "true" }
        deeds += r.events.count { it.type == EventType.WEAPON_DEED || it.type == EventType.LEGEND_EARNED }
        sabotaged += r.events.count { it.type == EventType.SABOTAGE_DONE }
        val g = before.guild!!
        // The first Epic of the run, and how it came: off the anvil, by an exceptional result, or by the hone.
        if (firstEpicDay == null) {
            r.events.firstOrNull { it.type == EventType.WEAPON_FORGED && (it.data["rarity"] == Rarity.EPIC.name || it.data["rarity"] == Rarity.LEGENDARY.name) }?.let { firstEpicDay = r.day; firstEpicBy = if (it.data["exceptional"] == "true") "exceptional" else "forge" }
                ?: r.events.firstOrNull { e -> e.type == EventType.WEAPON_HONED && e.subjectIds.firstOrNull()?.let { id -> after.weapons[WeaponId(id)] }?.let { w -> w.rarity >= Rarity.EPIC && w.forgedDay > 0 && w.quality - cfg.honeQualityBonus < cfg.epicMin } == true }?.let { firstEpicDay = r.day; firstEpicBy = "hone" }
        }
        fun best(s: GameState) = s.guild!!.members.mapNotNull { m -> s.loanOf(m.heroId)?.rarity }.maxOrNull() ?: Rarity.COMMON
        if (before.day == 5) bestDay5 = best(before)
        if (before.day == 20) bestDay20 = best(before)
        // A party that leaves tonight: how many, and how well.
        g.planned?.let { p -> leaving = p.heroIds.size to p.heroIds.sumOf { before.heroes[it]?.health ?: 0 } / p.heroIds.size.coerceAtLeast(1); g.offers.firstOrNull { it.id == p.offerId }?.modifierId?.let { modifiers.merge(it, 1, Int::plus) } }
        r.mission?.let { m -> if (!m.continues) { leaving?.let { (n, h) -> attempts += Attempt(guildCat.mission(m.defId).archetype.name, n, h, m.outcome == MissionOutcome.WON) }; leaving = null } }
        if (g.mission == null && g.members.none { engine.memberUnavailable(before, it.heroId) == null }) stalled++
        // What the members carried on the fifth morning: the opening recipes a run settled on, for the variety table.
        if (before.day == 5) for (m in g.members) before.loanOf(m.heroId)?.let { w -> firstLoans.merge("${w.familyId.value}/${w.element?.name?.lowercase() ?: "plain"}", 1, Int::plus) }
        for (e in r.events) when (e.type) {
            EventType.MEMBER_PURCHASE -> living.merge("purchase", 1, Int::plus)
            EventType.LOAN_RETURN_ANNOUNCED -> living.merge(if (e.data["cancelled"] == "true") "return_cancelled" else "return_announced", 1, Int::plus)
            EventType.LOAN_RETURNED -> living.merge("returned", 1, Int::plus)
            EventType.SPECIALITY_CHOSEN -> living.merge("speciality:${e.data["speciality"]}", 1, Int::plus)
            EventType.OPPORTUNITY_POSTED -> living.merge("offer:${e.data["opportunity"]}", 1, Int::plus)
            EventType.HOOK_CHANGED -> living.merge("hook:${e.data["status"] ?: "CHANGED"}", 1, Int::plus)
            EventType.CONCERN_RAISED -> living.merge("concern", 1, Int::plus)
            EventType.DEPARTURE_ANNOUNCED -> living.merge(if (e.data["cancelled"] == "true") "departure_cancelled" else "departure_notice", 1, Int::plus)
            EventType.MEMBER_DEPARTED -> living.merge("departed", 1, Int::plus)
            EventType.ENCOUNTER_OFFERED -> e.data["encounter"]?.takeIf { it in setOf("creditor_calls", "a_lesson", "wounded_friend") }?.let { living.merge("visitor:$it", 1, Int::plus) }
            else -> {}
        }

        r.mission?.takeIf { !it.continues }?.let { m -> (before.guild?.mission?.takeIf { it.id == m.missionId }?.offer ?: before.guild?.offers?.firstOrNull { it.id == m.missionId })?.opportunityId?.let { living.merge("taken:$it:${m.outcome.name}", 1, Int::plus) } }
        captured += r.events.count { it.type == EventType.MEMBER_CAPTURED }
        rescued += r.events.count { it.type == EventType.MEMBER_RESCUED }
        dead += before.guild!!.members.count { m -> after.heroes[m.heroId]?.let { it.fate == HeroFate.DEAD } ?: true }
        if (firstCombo == null && r.events.any { it.type == EventType.COMBO_NOTED }) firstCombo = r.day
    }

    // ---- one planning day ----

    fun planDay(start: GameState): GameState {
        var s = start
        if (s.pendingBlessingOffer.isNotEmpty()) s = s.send(Command.ChooseBlessing(s.pendingBlessingOffer.first()))
        if (s.pendingRelicOffer.isNotEmpty()) s = relic(s)
        if (policy == GuildPolicy.PASSIVE) return s
        s.guild?.mission?.let { m -> if (m.outcome == null && m.stage == 1 && m.offer.optionalPush && m.choice == null) s = s.send(Command.ChooseMissionCheckpoint(m.id, if (pushes(s)) CheckpointChoice.PUSH else CheckpointChoice.RETURN)) }
        s = recruit(s)
        s = forge(s)
        s = loans(s)
        s = shelf(s)
        s = wall(s)
        s = deploy(s)
        return s
    }

    private fun pushes(s: GameState): Boolean = when (policy) {
        GuildPolicy.GREEDY_DIVER, GuildPolicy.HIGHEST_STAT -> true
        GuildPolicy.LEGEND_SEEKER -> s.guild!!.mission!!.party.all { (s.heroes[it]?.health ?: 0) >= 50 }
        GuildPolicy.SYNERGY, GuildPolicy.ADAPTIVE, GuildPolicy.NO_SABOTAGE, GuildPolicy.REPEAT_RECIPE, GuildPolicy.IGNORE_COUNTER, GuildPolicy.SUPPLY_FARMER -> s.guild!!.mission!!.party.all { (s.heroes[it]?.health ?: 0) >= 70 }
        else -> false
    }

    private fun relic(s: GameState): GameState {
        val offer = s.pendingRelicOffer
        val wanted = when (policy) {
            GuildPolicy.SYNERGY, GuildPolicy.ADAPTIVE, GuildPolicy.NO_SABOTAGE, GuildPolicy.LEGEND_SEEKER, GuildPolicy.REPEAT_RECIPE, GuildPolicy.IGNORE_COUNTER, GuildPolicy.SUPPLY_FARMER -> listOf("overflow_basin", "salvage_bell", "storm_ledger", "blood_receipt", "furnace_lung", "bone_music_box")
            GuildPolicy.RECOVERY_AWARE -> listOf("cowards_medal", "overflow_basin")
            GuildPolicy.SHOP_FOCUSED -> listOf("collectors_seal", "tempering_ledger", "ashen_bellows")
            else -> emptyList()
        }
        val pick = wanted.firstOrNull { it in offer } ?: offer.first()
        val full = s.relics.size >= cfg.depth.relicSlots
        return if (full) s.send(Command.DeclineRelicOffer) else s.send(Command.ChooseRelic(pick))
    }

    private fun recruit(s0: GameState): GameState {
        var s = s0
        val target = when (policy) { GuildPolicy.SHOP_FOCUSED -> 2; GuildPolicy.NOVICE -> 3; GuildPolicy.SUPPLY_FARMER -> if (farming(s0)) 3 else 4; else -> 4 }
        for (c in s.guild!!.candidates.sortedBy { it.fee }) {
            if (s.guild!!.members.size >= target || s.gold < c.fee + 60) break
            s = s.send(Command.RecruitHero(c.heroId))
        }
        return s
    }

    /** What a member would do with [w]: the strike it gives them, plus what a chain-minded smith sees in its rules. */
    private fun worth(s: GameState, heroId: HeroId, w: Weapon): Int {
        val f = engine.fighterView(s, heroId, w) ?: return 0
        val base = f.strikeMin * 3 + f.support
        if (!chains) return base
        val cls = s.heroes[heroId]?.classId?.value
        val rules = f.effects.map { it.id }.toSet()
        var bonus = 0
        if ("catalyst_capacitor" in rules && cls == "guardian") bonus += 12
        if (w.element != null) bonus += 3
        // The opening's plan, as its card states it. One branch a package, keyed on the relic it came with.
        val trait = traitOf(s, heroId)
        val isFront = heroId == front(s)
        if (plan(s, "salvage_bell")) { if ("affix_brittle" in rules && isFront) bonus += 6; if ("catalyst_runed" in rules) bonus += 6 }
        if (plan(s, "blunt_oath")) bonus += if (w.rarity <= Rarity.UNCOMMON) 8 else -6
        if (plan(s, "blood_receipt") && "affix_bloodbound" in rules && cls != "warden") bonus += 10
        if (plan(s, "overflow_basin")) { if (cls == "warden" && w.familyId.value == "staff") bonus += 8; if (isFront && w.element == Element.VERDANT) bonus += 6 }
        if (plan(s, "storm_ledger") && trait == "storm_touched") { if (w.element == Element.STORM) bonus += 4; if ("catalyst_capacitor" in rules) bonus += 6 }
        if (plan(s, "furnace_lung")) { if (w.element == Element.FIRE) bonus += 5; if ("catalyst_converting" in rules) bonus += 12 }
        if (plan(s, "bone_music_box")) { if (w.element == Element.SUN) bonus += if (trait == "methodical") 10 else 4 }
        return base + bonus
    }

    /**
     * A Heartfire forging, by a smith who holds Heartfire and can pay for a recipe whose plain roll cannot miss the floor:
     * the best core it has or can buy, with the augment that suits it, for whoever in the guild fits the weapon.
     */
    private fun heartfireForge(s0: GameState): GameState {
        var s = s0
        if (s.heartfire < 1 || policy == GuildPolicy.NOVICE || policy == GuildPolicy.PASSIVE || policy == GuildPolicy.REPEAT_RECIPE) return s
        fun have(m: MaterialId) = (s.materials[m] ?: 0) > 0
        fun price(m: MaterialId) = if (have(m)) 0 else if ((s.supplierStock[m] ?: 1) > 0) engine.materialPrice(s, m) else Int.MAX_VALUE / 4
        val family = s.guild!!.members.firstNotNullOfOrNull { s.heroes[it.heroId] }?.let { content.heroClass(it.classId).preferredFamilies.first() } ?: content.families.first().id
        // Every temper is weighed by what the workbench shows: the chance at both ends of the roll. A quarter on average is worth the Heartfire.
        val best = Risk.entries.flatMap { risk -> content.materials(MaterialCategory.CORE).flatMap { c -> content.materials(MaterialCategory.AUGMENT).map { a -> Command.Forge(ForgeMode.ADVANCED, family, c.id, a.id, null, risk, heartfire = true) } } }
            .map { it to engine.heartfireOutlook(s, it) }
            .filter { (c, o) -> o.chanceLow + o.chanceHigh >= 0.5 && price(c.coreId) + price(c.augmentId) + 60 <= s.gold && s.energy + cfg.maxOverworkPerDay - s.overworkToday >= o.energy }
            .maxByOrNull { it.second.chanceLow + it.second.chanceHigh }?.first ?: return s
        for (m in listOf(best.coreId, best.augmentId)) if (!have(m)) s = s.send(Command.BuyMaterial(m))
        if (!have(best.coreId) || !have(best.augmentId)) return s
        return s.send(best)
    }

    /** The family that answers what the scouts say is coming, for a member who can use it, or null. */
    private fun answerFamily(s: GameState, hero: Hero?): WeaponFamilyId? {
        if (!readsScouts || hero == null) return null
        val prep = engine.wallForecast(s)?.prep?.id ?: return null
        val wanted = when (prep) { "shield_wall" -> "spear"; "gravecaller", "cutpurses" -> "bow"; else -> return null }
        return content.heroClass(hero.classId).preferredFamilies.firstOrNull { it.value == wanted }
    }

    private fun forge(s0: GameState): GameState {
        var s = heartfireForge(s0)
        var guard = 0
        while (guard++ < 6 && s.energy >= cfg.quickForgeEnergy) {
            val members = s.guild!!.members.mapNotNull { s.heroes[it.heroId] }
            // For whom: the member with nothing in hand, else the shelf.
            val bare = members.firstOrNull { s.loanOf(it.id) == null && s.equippedWeapon(it.id) == null && s.storedWeapons().none { w -> w.promisedTo == null } }
            val familyId = if (policy == GuildPolicy.REPEAT_RECIPE) com.tinyblacksmith.core.content.LaunchContent.SWORD
                else answerFamily(s, bare) ?: LaunchStaff.takeIf { bare?.classId?.value == "warden" && plan(s, "overflow_basin") } ?: bare?.let { content.heroClass(it.classId).preferredFamilies.first() } ?: content.families[(s.day + guard) % content.families.size].id
            val cores = content.materials(MaterialCategory.CORE).sortedBy { it.tier }
            val augments = content.materials(MaterialCategory.AUGMENT).sortedBy { it.tier }
            fun have(m: MaterialId) = (s.materials[m] ?: 0) > 0
            fun afford(m: com.tinyblacksmith.core.content.MaterialDef) = have(m.id) || (s.gold >= engine.materialPrice(s, m.id) + 40 && (s.supplierStock[m.id] ?: 1) > 0)
            val rich = policy == GuildPolicy.HIGHEST_STAT || policy == GuildPolicy.GREEDY_DIVER
            // The spendthrifts buy the best metal they can; the others the best they can while a reserve of 150 gold stays in the till.
            fun comfortable(m: com.tinyblacksmith.core.content.MaterialDef) = have(m.id) || (s.gold >= engine.materialPrice(s, m.id) + 150 && (s.supplierStock[m.id] ?: 1) > 0)
            // The Plain Oath keeps the party in iron and bronze, and the Overflowing Cup gives its Warden a staff.
            val oath = plan(s, "blunt_oath") && bare != null
            val core = (if (oath) cores.firstOrNull { have(it.id) && it.tier <= 2 } ?: cores.firstOrNull { afford(it) } else if (rich) cores.lastOrNull { afford(it) } else if (policy == GuildPolicy.NOVICE || policy == GuildPolicy.PASSIVE || farming(s)) cores.firstOrNull { have(it.id) } ?: cores.firstOrNull { afford(it) }
                else cores.lastOrNull { comfortable(it) } ?: cores.firstOrNull { afford(it) }) ?: break
            val weak = s.siege?.factionId?.takeIf { readsScouts }?.let { content.faction(it).weakTo }
            if (policy == GuildPolicy.REPEAT_RECIPE) {
                // One recipe, whatever the day asks for.
                val iron = content.material(com.tinyblacksmith.core.content.LaunchContent.IRON); val resin = content.material(com.tinyblacksmith.core.content.LaunchContent.EMBER_RESIN)
                for (m in listOf(iron, resin)) if (!have(m.id) && s.gold >= engine.materialPrice(s, m.id)) s = s.send(Command.BuyMaterial(m.id))
                if (!have(iron.id) || !have(resin.id)) break
                val before = s
                s = s.send(Command.Forge(ForgeMode.QUICK, familyId, iron.id, resin.id, null, Risk.BALANCED))
                if (s === before) break
                continue
            }
            val L = com.tinyblacksmith.core.content.LaunchContent
            // What the opening's plan asks of this weapon, if it asks anything.
            val planned: MaterialId? = when {
                bare == null -> null
                plan(s, "overflow_basin") && bare.id == front(s) -> L.VERDANT_SAP
                plan(s, "storm_ledger") && traitOf(s, bare.id) == "storm_touched" -> L.STORMGLASS
                // Fire for the front, who meets them first. The others still answer what the scouts say.
                plan(s, "furnace_lung") && bare.id == front(s) -> L.EMBER_RESIN
                plan(s, "bone_music_box") && traitOf(s, bare.id) == "methodical" -> L.SUN_ASH
                else -> null
            }
            val augment = augments.firstOrNull { it.id == planned && afford(it) } ?: when (policy) {
                GuildPolicy.SYNERGY, GuildPolicy.ADAPTIVE, GuildPolicy.NO_SABOTAGE, GuildPolicy.LEGEND_SEEKER, GuildPolicy.IGNORE_COUNTER, GuildPolicy.SUPPLY_FARMER -> augments.firstOrNull { it.id == com.tinyblacksmith.core.content.LaunchContent.STORMGLASS && afford(it) && bare?.classId?.value == "guardian" } ?: augments.firstOrNull { it.element == weak && afford(it) }
                GuildPolicy.DEFENCE_FIRST -> augments.firstOrNull { it.element == weak && afford(it) }
                else -> null
            } ?: (if (rich) augments.lastOrNull { afford(it) } else augments.firstOrNull { have(it.id) } ?: augments.firstOrNull { afford(it) }) ?: break
            val catalyst = if (chains && s.energy >= cfg.advancedForgeEnergy)
                (if (plan(s, "storm_ledger") && bare != null) content.materials(MaterialCategory.CATALYST).firstOrNull { have(it.id) && it.id == L.BINDING_SALT && traitOf(s, bare.id) == "storm_touched" } else null)
                    ?: (if (plan(s, "furnace_lung") && bare != null) content.materials(MaterialCategory.CATALYST).firstOrNull { have(it.id) && it.id == L.VOID_INK } else null)
                    ?: content.materials(MaterialCategory.CATALYST).firstOrNull { have(it.id) && (it.id == com.tinyblacksmith.core.content.LaunchContent.BINDING_SALT) == (bare?.classId?.value == "guardian" || bare == null) }
                    ?: content.materials(MaterialCategory.CATALYST).firstOrNull { have(it.id) } else null
            for (m in listOf(core, augment)) if (!have(m.id) && s.gold >= engine.materialPrice(s, m.id)) s = s.send(Command.BuyMaterial(m.id))
            if (!have(core.id) || !have(augment.id)) break
            val risk = when (policy) { GuildPolicy.GREEDY_DIVER -> Risk.RECKLESS; GuildPolicy.NOVICE, GuildPolicy.RECOVERY_AWARE -> Risk.SAFE; else -> Risk.BALANCED }
            val before = s
            s = s.send(Command.Forge(if (catalyst != null) ForgeMode.ADVANCED else ForgeMode.QUICK, familyId, core.id, augment.id, catalyst?.id, risk))
            if (s === before) break
        }
        return s
    }

    private fun loans(s0: GameState): GameState {
        var s = s0
        val members = s.guild!!.members.filter { it.status == MemberStatus.HOME }
        // The shop-focused smith keeps the best of the stock for sale and loans from what is left.
        val keep = if (policy == GuildPolicy.SHOP_FOCUSED) s.storedWeapons().filter { it.promisedTo == null }.sortedByDescending { it.power }.take(3).map { it.id }.toSet() else emptySet()
        for (m in members) {
            // A member's oath is on their card: the smith offers only what it allows (living heroes).
            val stock = s.storedWeapons().filter { it.promisedTo == null && it.id !in keep && com.tinyblacksmith.core.guild.Agency.oathAllows(s.hero(m.heroId), it) }
            val current = s.loanOf(m.heroId) ?: s.equippedWeapon(m.heroId)
            val best = stock.maxByOrNull { worth(s, m.heroId, it) } ?: continue
            if (current == null || worth(s, m.heroId, best) > worth(s, m.heroId, current) + 2) { s = s.send(Command.LoanWeapon(m.heroId, best.id)); loans++ }
        }
        // A cracked or badly worn loan is mended by a smith who thinks about tomorrow.
        if (policy == GuildPolicy.RECOVERY_AWARE || adaptive || policy == GuildPolicy.SYNERGY) for (m in members) {
            val w = s.loanOf(m.heroId) ?: continue
            val mendAt = if (plan(s, "salvage_bell") && w.flaws.any { it.value == "brittle" }) 75 else 60
            if (w.condition < mendAt && s.energy >= cfg.honeEnergy && (s.materials[w.coreId] ?: 0) > 0) {
                s = s.send(Command.RecallLoan(w.id)); s = s.send(Command.Hone(w.id)); if (com.tinyblacksmith.core.guild.Agency.oathAllows(s.hero(m.heroId), s.weapon(w.id))) s = s.send(Command.LoanWeapon(m.heroId, w.id))
            }
        }
        return s
    }

    private fun shelf(s0: GameState): GameState {
        var s = s0
        val spare = if (policy == GuildPolicy.SHOP_FOCUSED) 0 else 1   // one blade is kept back as a reserve
        for (w in s.storedWeapons().filter { it.promisedTo == null }.sortedByDescending { it.power }.drop(spare)) {
            if (s.listedWeapons().size >= engine.shelfSlots(s)) break
            s = s.send(Command.ToggleShelf(w.id, true))
        }
        return s
    }

    private fun siegeTonight(s: GameState) = s.town.nextSiegeDay == s.day

    /** A reading of the forecast any player can make: three fit defenders whose health together is well above the besieger's. */
    private fun wallLooksStrong(s: GameState): Boolean {
        val f = engine.wallForecast(s) ?: return false
        if (f.defenders.size < cfg.guild.defenders || f.plan.isEmpty()) return false
        val ours = f.defenders.sumOf { (h, w) -> engine.fighterView(s, h.id, w, home = true)?.let { it.maxHealth + it.strikeMin * 4 } ?: 0 }
        val theirs = f.plan.sumOf { e -> content.combat!!.unit(e.unitId).let { u -> (u.health + u.strikeMax * 4) * e.percent / 100 } }
        return ours * 10 >= theirs * 9
    }

    private fun wall(s0: GameState): GameState {
        var s = s0
        if (policy != GuildPolicy.DEFENCE_FIRST && !adaptive) return s
        if (s.town.nextSiegeDay - s.day > 1) return s
        val fit = s.guild!!.members.filter { engine.memberUnavailable(s, it.heroId) == null && it.heroId !in s.guild!!.reserved && it.concern?.departure == null }
        val strongest = fit.sortedByDescending { engine.fighterView(s, it.heroId, home = true)?.let { f -> f.maxHealth + f.strikeMin * 3 } ?: 0 }.take(cfg.guild.defenders - s.guild!!.reserved.size)
        // Against knives for the back, the sturdiest stands last in the line: the wall is reserved in the order it stands.
        val order = if (readsScouts && engine.wallForecast(s)?.prep?.id == "cutpurses") strongest.sortedBy { engine.fighterView(s, it.heroId, home = true)?.maxHealth ?: 0 } else strongest
        for (m in order) s = s.send(Command.ReserveDefender(m.heroId, true))
        return s
    }

    private fun deploy(s0: GameState): GameState {
        val s = s0
        val g = s.guild!!
        if (g.mission != null) return s
        val fit = g.members.filter { engine.memberUnavailable(s, it.heroId) == null && it.heroId !in g.reserved && it.concern?.departure == null }.map { it.heroId }
        if (fit.isEmpty()) return s
        val keepHome = siegeTonight(s) && (adaptive || policy in setOf(GuildPolicy.DEFENCE_FIRST, GuildPolicy.RECOVERY_AWARE, GuildPolicy.NOVICE, GuildPolicy.SYNERGY, GuildPolicy.RECOMMENDED))
        if (keepHome) return s
        fun arch(o: MissionOffer) = guildCat.mission(o.defId).archetype
        // A two-day contract that would keep the party away over the siege night is one the careful leave on the board.
        val careful = adaptive || policy in setOf(GuildPolicy.DEFENCE_FIRST, GuildPolicy.RECOVERY_AWARE, GuildPolicy.SYNERGY, GuildPolicy.NOVICE, GuildPolicy.RECOMMENDED)
        val offers = g.offers.filter { o -> s.gold >= o.fee && !(careful && s.day + o.days - 1 >= s.town.nextSiegeDay) && !(personal == "ignore" && com.tinyblacksmith.core.guild.Hooks.sponsored(o)) }
        val health = fit.sumOf { s.heroes[it]?.health ?: 0 } / fit.size
        val order: List<MissionArchetype> = when (policy) {
            GuildPolicy.NOVICE -> listOf(MissionArchetype.SUPPLY)
            GuildPolicy.SHOP_FOCUSED -> listOf(MissionArchetype.SUPPLY, MissionArchetype.ESCORT)
            GuildPolicy.HIGHEST_STAT -> listOf(MissionArchetype.RECOVERY, MissionArchetype.DELVE, MissionArchetype.HUNT, MissionArchetype.ESCORT, MissionArchetype.SUPPLY)
            GuildPolicy.GREEDY_DIVER -> listOf(MissionArchetype.DELVE, MissionArchetype.HUNT, MissionArchetype.RECOVERY, MissionArchetype.ESCORT, MissionArchetype.SUPPLY)
            GuildPolicy.DEFENCE_FIRST -> listOf(MissionArchetype.SABOTAGE, MissionArchetype.RESCUE, MissionArchetype.SUPPLY, MissionArchetype.ESCORT) + (if (s.town.integrity < 70) listOf(MissionArchetype.EMERGENCY) else emptyList())
            GuildPolicy.RECOVERY_AWARE -> (if (s.town.integrity < 60) listOf(MissionArchetype.EMERGENCY) else emptyList()) + listOf(MissionArchetype.RESCUE, MissionArchetype.RECOVERY, MissionArchetype.SUPPLY, MissionArchetype.ESCORT, MissionArchetype.SABOTAGE)
            GuildPolicy.SYNERGY -> listOf(MissionArchetype.RESCUE, MissionArchetype.HUNT, MissionArchetype.SABOTAGE, MissionArchetype.ESCORT, MissionArchetype.SUPPLY)
            // With the wall in doubt the sabotage comes first; with a strong wall the siege's opportunity is worth more than spoiling what would not have got through.
            GuildPolicy.ADAPTIVE -> listOf(MissionArchetype.RESCUE) + (if (s.town.integrity < 50) listOf(MissionArchetype.EMERGENCY) else emptyList()) +
                (if (wallLooksStrong(s) && health >= 75) listOf(MissionArchetype.OPPORTUNITY, MissionArchetype.SABOTAGE) else listOf(MissionArchetype.SABOTAGE, MissionArchetype.OPPORTUNITY)) +
                (if (health >= 75 && fit.size >= 2) listOf(MissionArchetype.RECOVERY, MissionArchetype.HUNT, MissionArchetype.ESCORT) else emptyList()) + listOf(MissionArchetype.SUPPLY)
            GuildPolicy.NO_SABOTAGE -> listOf(MissionArchetype.RESCUE) + (if (s.town.integrity < 50) listOf(MissionArchetype.EMERGENCY) else emptyList()) +
                (if (health >= 75) listOf(MissionArchetype.OPPORTUNITY) else emptyList()) +
                (if (health >= 75 && fit.size >= 2) listOf(MissionArchetype.RECOVERY, MissionArchetype.HUNT, MissionArchetype.ESCORT) else emptyList()) + listOf(MissionArchetype.SUPPLY)
            GuildPolicy.PASSIVE -> emptyList()
            // The adaptive smith's order, with one thing changed in each.
            GuildPolicy.REPEAT_RECIPE -> listOf(MissionArchetype.RESCUE) + (if (s.town.integrity < 50) listOf(MissionArchetype.EMERGENCY) else emptyList()) + listOf(MissionArchetype.SABOTAGE) +
                (if (health >= 75 && fit.size >= 2) listOf(MissionArchetype.RECOVERY, MissionArchetype.HUNT, MissionArchetype.ESCORT) else emptyList()) + listOf(MissionArchetype.SUPPLY)
            GuildPolicy.IGNORE_COUNTER -> listOf(MissionArchetype.RESCUE) + (if (s.town.integrity < 50) listOf(MissionArchetype.EMERGENCY) else emptyList()) +
                (if (health >= 75 && fit.size >= 2) listOf(MissionArchetype.RECOVERY, MissionArchetype.HUNT, MissionArchetype.ESCORT) else emptyList()) + listOf(MissionArchetype.SUPPLY)
            GuildPolicy.LEGEND_SEEKER -> listOf(MissionArchetype.RESCUE) + (if (s.town.integrity < 50) listOf(MissionArchetype.EMERGENCY) else emptyList()) +
                (if (health >= 75 && fit.size >= 2) listOf(MissionArchetype.RECOVERY, MissionArchetype.OPPORTUNITY, MissionArchetype.DELVE) else emptyList()) + listOf(MissionArchetype.SABOTAGE) +
                (if (health >= 75 && fit.size >= 2) listOf(MissionArchetype.HUNT, MissionArchetype.ESCORT) else emptyList()) + listOf(MissionArchetype.SUPPLY)
            GuildPolicy.RECOMMENDED -> emptyList()
            // Ten days of the safe road and nothing else, then the adaptive smith's order.
            GuildPolicy.SUPPLY_FARMER -> if (farming(s)) listOf(MissionArchetype.SUPPLY) else listOf(MissionArchetype.RESCUE) + (if (s.town.integrity < 50) listOf(MissionArchetype.EMERGENCY) else emptyList()) +
                (if (wallLooksStrong(s) && health >= 75) listOf(MissionArchetype.OPPORTUNITY, MissionArchetype.SABOTAGE) else listOf(MissionArchetype.SABOTAGE, MissionArchetype.OPPORTUNITY)) +
                (if (health >= 75 && fit.size >= 2) listOf(MissionArchetype.RECOVERY, MissionArchetype.HUNT, MissionArchetype.ESCORT) else emptyList()) + listOf(MissionArchetype.SUPPLY)
        }.let { o -> if (plan(s, "cowards_medal") && s.town.integrity < 70 && MissionArchetype.EMERGENCY !in o) listOf(MissionArchetype.EMERGENCY) + o else o }
        // The deep road (rules 7) opens the big ingredients: every bot that does more than the safe road goes for it once it is posted, after a rescue.
        val road = offers.firstOrNull { it.defId == GuildContent.DEEP_ROAD }?.takeIf {
            policy !in setOf(GuildPolicy.PASSIVE, GuildPolicy.NOVICE, GuildPolicy.SHOP_FOCUSED, GuildPolicy.RECOMMENDED) && (MissionArchetype.RESCUE !in order || offers.none { o -> arch(o) == MissionArchetype.RESCUE })
        }
        // The first card on the board that the till can pay for, whatever it is.
        val mine = if (personal == "answer" && health >= 75 && fit.size >= 2 && offers.none { arch(it) == MissionArchetype.RESCUE }) offers.firstOrNull { com.tinyblacksmith.core.guild.Hooks.sponsored(it) } else null
        val offer = mine ?: road ?: (if (policy == GuildPolicy.RECOMMENDED) offers.firstOrNull { arch(it) != MissionArchetype.EMERGENCY } else order.firstNotNullOfOrNull { a -> offers.firstOrNull { arch(it) == a } }) ?: return s
        val posture = when (policy) {
            GuildPolicy.GREEDY_DIVER -> Posture.RECKLESS
            GuildPolicy.NOVICE, GuildPolicy.RECOVERY_AWARE -> Posture.CAUTIOUS
            // Careful Books: a cautious plan, and pulling back still pays.
            else -> if (plan(s, "cowards_medal") && (arch(offer) == MissionArchetype.HUNT || arch(offer) == MissionArchetype.DELVE)) Posture.CAUTIOUS else Posture.BALANCED
        }
        // The front first: whoever intercepts stands in the first place, the healer behind.
        // The Overflowing Cup and Paid in Blood are nothing without the healer: she goes, behind the front.
        val healerGoes = plan(s, "overflow_basin") || plan(s, "blood_receipt")
        val size = if (arch(offer) == MissionArchetype.EMERGENCY) 1 else engine.partyMax(s, offer.id)
        val ranked = fit.sortedBy { classRank(s, it) }
        val healer = ranked.firstOrNull { s.heroes[it]?.classId?.value == "warden" }.takeIf { healerGoes && size >= 2 }
        val party = if (healer != null) ranked.filter { it != healer }.take(size - 1) + healer else ranked.take(size)
        return s.send(Command.PlanDeployment(offer.id, party, posture))
    }
}

data class GuildReport(val policy: GuildPolicy, val runs: List<GuildRunStats>) {
    fun render(): String {
        val n = runs.size.toDouble()
        fun pct(c: Int) = "%3.0f%%".format(100 * c / n)
        val secured = runs.count { it.charterDay != null }
        val defeated = runs.count { it.defeated }
        val censored = runs.count { !it.defeated && it.charterDay == null }
        val days = runs.map { it.days }.sorted()
        val contracts = runs.flatMap { it.contracts.entries }.groupBy({ it.key }, { it.value }).mapValues { it.value.sum() }
        val total = contracts.values.sum().coerceAtLeast(1)
        val byArch = contracts.entries.groupBy({ it.key.substringBefore(':') }, { it.key.substringAfter(':') to it.value })
        val sieges = SiegeVerdict.entries.associateWith { v -> runs.sumOf { it.sieges[v] ?: 0 } }
        val siegeTotal = sieges.values.sum().coerceAtLeast(1)
        return buildString {
            append("%-15s charter %s  defeated %s  neither by day %d %s | days med %2d mean %4.1f | first siege leaves %3.0f | ".format(policy.name, pct(secured), pct(defeated), runs.maxOf { it.days }, pct(censored), days[days.size / 2], days.average(),
                runs.mapNotNull { it.firstSiegeIntegrity }.average()))
            append("sieges held %2.0f%% cost %2.0f%% breached %2.0f%% | ".format(100.0 * sieges[SiegeVerdict.HELD]!! / siegeTotal, 100.0 * sieges[SiegeVerdict.HELD_AT_A_COST]!! / siegeTotal, 100.0 * sieges[SiegeVerdict.BREACHED]!! / siegeTotal))
            append("per run: contracts %.1f captured %.2f rescued %.2f dead %.2f chains %.1f (first day %s) members %.1f gold %.0f rejected %d\n".format(contracts.values.sum() / n, runs.sumOf { it.captured } / n, runs.sumOf { it.rescued } / n,
                runs.sumOf { it.membersDead } / n, runs.sumOf { it.combos } / n, runs.mapNotNull { it.firstComboDay }.let { if (it.isEmpty()) "-" else "%.1f".format(it.average()) }, runs.sumOf { it.members } / n, runs.sumOf { it.goldAtEnd } / n, runs.sumOf { it.rejected }))
            append("                contracts: " + byArch.entries.sortedBy { it.key }.joinToString("  ") { (a, list) ->
                val all = list.sumOf { it.second }
                "$a ${"%.0f%%".format(100.0 * all / total)} (won ${"%.0f%%".format(100.0 * (list.firstOrNull { it.first == "WON" }?.second ?: 0) / all)})"
            } + "\n")
            val forges = runs.sumOf { it.forged.values.sum() }.coerceAtLeast(1)
            val perRun = runs.map { it.legends.values.sum() }.sorted()
            val sources = runs.flatMap { it.legends.entries }.groupBy({ it.key }, { it.value }).mapValues { it.value.sum() }
            append("                forge: %.1f a run (%.1f recipes), %s | legendary a run: mean %.2f median %d p90 %d, runs with one %s, first on day %s, by source %s | overwork days %.1f\n".format(
                forges / n, runs.sumOf { it.recipes } / n, Rarity.entries.joinToString(" ") { r -> "%s=%.0f%%".format(r.name.take(1), 100.0 * runs.sumOf { it.forged[r.name] ?: 0 } / forges) },
                perRun.average(), perRun[perRun.size / 2], perRun[(perRun.size * 9 / 10).coerceAtMost(perRun.size - 1)], pct(perRun.count { it > 0 }),
                runs.mapNotNull { it.firstLegendDay }.let { if (it.isEmpty()) "-" else "%.1f".format(it.average()) }, sources.entries.sortedBy { it.key }.joinToString(" ") { "${it.key}=%.2f".format(it.value / n) }.ifEmpty { "-" },
                runs.sumOf { it.overworkDays } / n))
            // Each siege in turn: how it ended and what it cost the forge, over the runs that reached it.
            append("                sieges: " + (0 until 4).mapNotNull { i ->
                val reached = runs.mapNotNull { it.siegeLog.getOrNull(i) }
                if (reached.isEmpty()) null else "#${i + 1} held %.0f%% cost %.0f%% breached %.0f%% damage %.1f (n=%d)".format(100.0 * reached.count { it.first == SiegeVerdict.HELD } / reached.size,
                    100.0 * reached.count { it.first == SiegeVerdict.HELD_AT_A_COST } / reached.size, 100.0 * reached.count { it.first == SiegeVerdict.BREACHED } / reached.size, reached.map { it.second }.average(), reached.size)
            }.joinToString("  ") + "\n")
            append("                heartfire a run: earned %.2f, forgings %.2f | deeds %.2f | sabotage done %.2f | days with nobody fit %.2f | charter rate 95%% interval %s\n".format(
                runs.sumOf { it.heartfireGained } / n, runs.sumOf { it.heartfireForges } / n, runs.sumOf { it.deeds } / n, runs.sumOf { it.sabotaged } / n, runs.sumOf { it.stalledDays } / n, interval(secured, runs.size)))
            val epicDays = runs.mapNotNull { it.firstEpicDay }.sorted()
            val at5 = runs.filter { it.bestDay5 != null }; val at20 = runs.filter { it.bestDay20 != null }
            fun share(list: List<GuildRunStats>, f: (GuildRunStats) -> Boolean) = if (list.isEmpty()) "-" else "%.0f%%".format(100.0 * list.count(f) / list.size)
            append("                epic: carried on day 5 %s, rare or better on day 5 %s, carried on day 20 %s (n=%d) | first Epic: median day %s, runs with one %s, by %s\n".format(
                share(at5) { it.bestDay5!! >= Rarity.EPIC }, share(at5) { it.bestDay5!! >= Rarity.RARE }, share(at20) { it.bestDay20!! >= Rarity.EPIC }, at20.size,
                if (runs.count { it.firstEpicDay == null } * 2 >= runs.size) "none" else (runs.map { it.firstEpicDay ?: Int.MAX_VALUE }.sorted()[runs.size / 2]).toString(), pct(epicDays.size),
                runs.mapNotNull { it.firstEpicBy }.groupingBy { it }.eachCount().entries.sortedBy { it.key }.joinToString(" ") { "${it.key}=${it.value}" }.ifEmpty { "-" }))
            // Rescue beside the other dangerous work, all parties and then only full, rested ones (three members, mean health 80 or more).
            val tries = runs.flatMap { it.attempts }
            fun won(list: List<Attempt>) = if (list.isEmpty()) "-" else "%.0f%% (%s, n=%d)".format(100.0 * list.count { it.won } / list.size, interval(list.count { it.won }, list.size), list.size)
            append("                readiness: " + listOf("RESCUE", "HUNT", "RECOVERY", "SABOTAGE", "DELVE", "OPPORTUNITY").joinToString("  ") { a -> val l = tries.filter { it.archetype == a }; "$a all ${won(l)} full party ${won(l.filter { it.party >= 3 && it.health >= 80 })}" } + "\n")
            append("                rescue by party: " + (1..3).joinToString("  ") { k -> "$k: ${won(tries.filter { it.archetype == "RESCUE" && it.party == k })}" } + "  rested (health 80 or more): ${won(tries.filter { it.archetype == "RESCUE" && it.health >= 80 })}  hurt: ${won(tries.filter { it.archetype == "RESCUE" && it.health < 80 })}\n")
            append("                wealth a run: gold %.0f, weapons and stock at the going rate %.0f | contracts by modifier: %s\n".format(runs.sumOf { it.goldAtEnd } / n, runs.sumOf { it.stockValue } / n,
                runs.flatMap { it.modifiers.entries }.groupBy({ it.key }, { it.value }).mapValues { it.value.sum() }.entries.sortedBy { it.key }.joinToString(" ") { "${it.key}=%.2f".format(it.value / n) }.ifEmpty { "-" }))
            append("                till a run: " + IncomeKind.entries.mapNotNull { k -> runs.sumOf { it.income[k] ?: 0 }.takeIf { it > 0 }?.let { "${k.name.lowercase()} %.0f".format(it / n) } }.joinToString(", ") + ", spent on stock and tools %.0f\n".format(runs.sumOf { it.spent } / n))
        }
    }
}

/** The 95 % Wilson interval of [k] in [n], as "a to b %". */
fun interval(k: Int, n: Int): String {
    if (n == 0) return "-"
    val z = 1.96; val p = k.toDouble() / n
    val centre = (p + z * z / (2 * n)) / (1 + z * z / n)
    val half = z * Math.sqrt(p * (1 - p) / n + z * z / (4.0 * n * n)) / (1 + z * z / n)
    return "%.0f to %.0f %%".format(100 * (centre - half), 100 * (centre + half))
}

object GuildSim {
    /** The same seeds played by two policies: how often each secured the charter where the other did not, and the first sieges side by side. */
    fun paired(a: GuildReport, b: GuildReport): String {
        val pairs = a.runs.zip(b.runs).filter { it.first.seed == it.second.seed }
        val onlyA = pairs.count { it.first.charterDay != null && it.second.charterDay == null }
        val onlyB = pairs.count { it.first.charterDay == null && it.second.charterDay != null }
        fun damage(r: GuildRunStats) = r.siegeLog.take(4).sumOf { it.second }
        val diff = pairs.map { damage(it.second) - damage(it.first) }
        val mean = diff.average()
        val sd = Math.sqrt(diff.sumOf { (it - mean) * (it - mean) } / maxOf(1, diff.size - 1))
        return "paired %s against %s on %d seeds: charter only %s %d, only %s %d, both %d, neither %d | forge fallen: %s %d, %s %d | forge damage in the first four sieges: %s takes %.1f more a run (standard error %.1f) | gold at the end %.0f against %.0f, weapons and stock %.0f against %.0f\n".format(
            a.policy.name, b.policy.name, pairs.size, a.policy.name, onlyA, b.policy.name, onlyB, pairs.count { it.first.charterDay != null && it.second.charterDay != null },
            pairs.count { it.first.charterDay == null && it.second.charterDay == null }, a.policy.name, pairs.count { it.first.defeated }, b.policy.name, pairs.count { it.second.defeated }, b.policy.name, mean, sd / Math.sqrt(diff.size.toDouble()),
            pairs.map { it.first.goldAtEnd }.average(), pairs.map { it.second.goldAtEnd }.average(), pairs.map { it.first.stockValue }.average(), pairs.map { it.second.stockValue }.average())
    }

    /** The same smith on the same seeds with the opening's plan followed ([a]) and ignored ([b]), package by package. */
    fun planPaired(a: GuildReport, b: GuildReport): String = a.runs.zip(b.runs).filter { it.first.seed == it.second.seed && it.first.opening == it.second.opening }.groupBy { it.first.opening ?: "-" }.entries.sortedBy { it.key }.joinToString("") { (id, pairs) ->
        val onlyA = pairs.count { it.first.charterDay != null && it.second.charterDay == null }; val onlyB = pairs.count { it.first.charterDay == null && it.second.charterDay != null }
        fun rate(f: (Pair<GuildRunStats, GuildRunStats>) -> GuildRunStats) = 100.0 * pairs.count { f(it).charterDay != null } / pairs.size
        fun dmg(r: GuildRunStats) = r.siegeLog.take(4).sumOf { it.second }
        "                plan %-14s n=%3d charter with the plan %3.0f%% ignoring it %3.0f%% (only with %d, only without %d) | forge damage in four sieges %.1f against %.1f | chains a run %.1f against %.1f | first siege damage %.1f\n".format(id, pairs.size,
            rate { it.first }, rate { it.second }, onlyA, onlyB, pairs.map { dmg(it.first) }.average(), pairs.map { dmg(it.second) }.average(), pairs.map { it.first.combos }.average(), pairs.map { it.second.combos }.average(),
            pairs.mapNotNull { it.first.siegeLog.firstOrNull()?.second }.average())
    }

    /** Charter rate by the opening package the runs began with. */
    fun byOpening(report: GuildReport): String = report.runs.groupBy { it.opening ?: "-" }.entries.sortedBy { it.key }.joinToString("") { (id, list) ->
        "                opening %-14s n=%3d charter %3.0f%% (%s) defeated %3.0f%% first siege damage %.1f loans on day 5: %s\n".format(id, list.size, 100.0 * list.count { it.charterDay != null } / list.size, interval(list.count { it.charterDay != null }, list.size),
            100.0 * list.count { it.defeated } / list.size, list.mapNotNull { it.siegeLog.firstOrNull()?.second }.average(),
            list.flatMap { it.firstLoans.entries }.groupBy({ it.key }, { it.value }).mapValues { it.value.sum() }.entries.sortedByDescending { it.value }.take(4).joinToString(" ") { "${it.key}=${it.value}" })
    }

    fun run(policy: GuildPolicy, runs: Int, seed: Long, config: BalanceConfig = BalanceConfig.DEFAULT, charterId: String = GuildContent.TOWNS_LAST_HOPE, horizon: Int = 30, legacy: (GameEngine) -> LegacyProfile = { LegacyProfile() },
        opening: String? = null, retire: Boolean = true, followPlan: Boolean = true, noDrawback: Boolean = false,
        /** Another catalog for the same runs (`ForgeLab.guild`: the metals' fight rules on and off). */
        content: com.tinyblacksmith.core.content.ContentCatalog? = null,
        /** The era feature revision of every run: living heroes, or `LivingRevision.LEGACY` for the feature-off baseline. */
        livingRevision: Int = com.tinyblacksmith.core.model.LivingRevision.CURRENT, personal: String? = null): GuildReport {
        val list = IntStream.range(0, runs).parallel().mapToObj { i ->
            // Without drawbacks: every opening keeps its relic, trait, stock and weapon and loses only what it takes away (one gold is left, the catalog insists on a drawback). The measure of what a drawback costs.
            val engine = if (noDrawback) GameEngine(config = config).let { e -> GameEngine(e.content.copy(guild = e.content.guild!!.copy(openings = e.content.guild.openings.map { it.copy(goldDelta = -1, militiaDelta = 0, integrityDelta = 0) })), config) } else if (content != null) GameEngine(content, config) else GameEngine(config = config)
            GuildBot(engine, policy, charterId, horizon, opening, retire, followPlan).also { it.livingRevision = livingRevision; it.personal = personal }.play(seed + i, legacy(engine)).first
        }.collect(Collectors.toList())
        return GuildReport(policy, list.sortedBy { it.seed })
    }

    /** What the members did of their own accord, per run: means over the report's runs, from each day's record (`GuildRunStats.living`). */
    fun livingTable(r: GuildReport): String = buildString {
        val n = r.runs.size.coerceAtLeast(1)
        fun mean(key: String) = r.runs.sumOf { it.living[key] ?: 0 }.toDouble() / n
        fun share(pred: (GuildRunStats) -> Boolean) = 100.0 * r.runs.count(pred) / n
        appendLine("${r.policy.name} living heroes, a run: purchases %.2f | returns announced %.2f, done %.2f, answered by the smith %.2f | concerns %.2f, departure notices %.2f, departures %.2f (runs with one %.0f%%) | visitors: creditor %.2f, lesson %.2f, healer %.2f".format(
            mean("purchase"), mean("return_announced"), mean("returned"), mean("return_cancelled"), mean("concern"), mean("departure_notice"), mean("departed"), share { (it.living["departed"] ?: 0) > 0 },
            mean("visitor:creditor_calls"), mean("visitor:a_lesson"), mean("visitor:wounded_friend")))
        val specs = r.runs.flatMap { s -> s.living.filterKeys { it.startsWith("speciality:") }.entries }.groupBy({ it.key.removePrefix("speciality:") }, { it.value }).mapValues { it.value.sum() }
        appendLine("  Specialities chosen (all runs): " + specs.entries.sortedByDescending { it.value }.joinToString(", ") { "${it.key} ${it.value}" })
        val offers = r.runs.flatMap { s -> s.living.filterKeys { it.startsWith("offer:") }.entries }.groupBy({ it.key.removePrefix("offer:") }, { it.value }).mapValues { it.value.sum() }
        val taken = r.runs.flatMap { s -> s.living.filterKeys { it.startsWith("taken:") }.entries }.groupBy({ it.key.removePrefix("taken:") }, { it.value }).mapValues { it.value.sum() }
        appendLine("  Personal offers posted (all runs): " + offers.entries.sortedByDescending { it.value }.joinToString(", ") { "${it.key} ${it.value}" } + " | taken: " + taken.entries.sortedByDescending { it.value }.joinToString(", ") { "${it.key} ${it.value}" })
        appendLine("  Hooks answered or changed (all runs): " + r.runs.flatMap { s -> s.living.filterKeys { it.startsWith("hook:") }.entries }.groupBy({ it.key }, { it.value }).mapValues { it.value.sum() }.entries.joinToString(", ") { "${it.key.removePrefix("hook:").lowercase()} ${it.value}" })
    }
}

/** `./gradlew :core:guildsim --args="--runs 300 --seed 1 [--policy NAME[,NAME]] [--charter ID] [--horizon N] [--maxed]"` */
fun main(args: Array<String>) {
    val a = args.toList().windowed(2, 2, partialWindows = true).associate { it[0] to it.getOrElse(1) { "" } }
    val runs = a["--runs"]?.toInt() ?: 200
    val seed = a["--seed"]?.toLong() ?: 1L
    val horizon = a["--horizon"]?.toInt() ?: 30
    val charter = a["--charter"] ?: GuildContent.TOWNS_LAST_HOPE
    val policies = a["--policy"]?.split(",")?.map { GuildPolicy.valueOf(it.uppercase()) } ?: GuildPolicy.entries
    val maxed = "--maxed" in args
    // --set enemyPercentBase=150,siegePercentBase=140: a few guild numbers, for tuning runs.
    val g0 = BalanceConfig.DEFAULT.guild
    val sets = a["--set"]?.split(",")?.associate { it.substringBefore('=') to it.substringAfter('=') }.orEmpty()
    val d0 = BalanceConfig.DEFAULT.depth
    val config = BalanceConfig.DEFAULT.copy(qualityExceptionalBonus = sets["qualityExceptionalBonus"]?.toInt() ?: BalanceConfig.DEFAULT.qualityExceptionalBonus, epicMin = sets["epicMin"]?.toInt() ?: BalanceConfig.DEFAULT.epicMin,
        // --noRoad: every limited material on sale from day 1, the deep road (rules 7) left out, for a paired comparison.
        depth = d0.copy(legend = d0.legend.copy(roadStageByTier = if ("--noRoad" in args) emptyMap() else d0.legend.roadStageByTier, fineCoreTier = sets["fineCoreTier"]?.toInt() ?: d0.legend.fineCoreTier)), guild = g0.copy(
        enemyPercentBase = sets["enemyPercentBase"]?.toInt() ?: g0.enemyPercentBase, enemyPercentPerDay = sets["enemyPercentPerDay"]?.toDouble() ?: g0.enemyPercentPerDay,
        siegePercentBase = sets["siegePercentBase"]?.toInt() ?: g0.siegePercentBase, siegePercentPerDay = sets["siegePercentPerDay"]?.toDouble() ?: g0.siegePercentPerDay,
        charterWarlordPercent = sets["charterWarlordPercent"]?.toInt() ?: g0.charterWarlordPercent, outerHealthPerPoint = sets["outerHealthPerPoint"]?.toDouble() ?: g0.outerHealthPerPoint,
        siegeGrunts = sets["siegeGrunts"]?.toInt() ?: g0.siegeGrunts, siegePercentPerPressure = sets["siegePercentPerPressure"]?.toDouble() ?: g0.siegePercentPerPressure,
        enemyPercentPerPressure = sets["enemyPercentPerPressure"]?.toDouble() ?: g0.enemyPercentPerPressure, outerMaxPercent = sets["outerMaxPercent"]?.toInt() ?: g0.outerMaxPercent,
        supplyThinPercent = sets["supplyThin"]?.split("/")?.map { it.toInt() } ?: g0.supplyThinPercent, nemesisPercent = sets["nemesisPercent"]?.toInt() ?: g0.nemesisPercent,
        damageHeldPerDowned = sets["damageHeldPerDowned"]?.toInt() ?: g0.damageHeldPerDowned, rewardPercentPerDay = sets["rewardPercentPerDay"]?.toDouble() ?: g0.rewardPercentPerDay,
        siegeGruntEvery = sets["siegeGruntEvery"]?.toInt() ?: g0.siegeGruntEvery, damageCostPercent = sets["damageCostPercent"]?.toInt() ?: g0.damageCostPercent,
        wallWorkExtraRecovery = sets["wallWorkExtraRecovery"]?.toInt() ?: g0.wallWorkExtraRecovery, contractGoldPercent = sets["contractGoldPercent"]?.toInt() ?: g0.contractGoldPercent),
        living = BalanceConfig.DEFAULT.living.copy(armyPercent = sets["armyPercent"]?.toInt() ?: BalanceConfig.DEFAULT.living.armyPercent))
    // --transcript: one run told from its own records, for the evidence report. Reproducible: seed, policy, charter and opening are all it needs.
    if ("--transcript" in args) {
        val told = setOf(EventType.OPENING_CHOSEN, EventType.GUILD_RECRUITED, EventType.SIEGE_PREPARATION, EventType.MISSION_WON, EventType.MISSION_FAILED, EventType.MEMBER_CAPTURED, EventType.MEMBER_RESCUED, EventType.SABOTAGE_DONE,
            EventType.SIEGE_WON, EventType.SIEGE_LOST, EventType.FORGE_DAMAGED, EventType.HEARTFIRE_GAINED, EventType.LEGEND_EARNED, EventType.CHARTER_SECURED, EventType.SUPPLIER_NEWS, EventType.WEAPON_RECOVERED, EventType.WEAPON_STOLEN,
            EventType.RELIC_CHOSEN, EventType.FORGE_DESTROYED, EventType.RUN_RETIRED)
        for (p in policies) {
            val engine = GameEngine(config = config)
            val bot = GuildBot(engine, p, charter, horizon, a["--opening"])
            val lines = ArrayList<String>()
            val seen = HashSet<String>()
            val (stats, end) = bot.play(seed) { s ->
                // The morning's plan, once the bot has made it: what each member carries, and what the day's forging was.
                if (s.day == 5 || s.day == 10 || s.day == 20) lines += "day ${s.day} | carried: " + s.guild!!.members.joinToString(", ") { m -> "${s.hero(m.heroId).name} the ${engine.content.heroClass(s.hero(m.heroId).classId).name} (${m.traitId}) " + (s.loanOf(m.heroId)?.let { w -> "${w.name}, ${w.rarity.name.lowercase()}" + (if (w.flaws.isNotEmpty()) ", flawed" else "") } ?: "nothing") } + " | gold ${s.gold}, forge health ${s.town.integrity}, Heartfire ${s.heartfire}"
                for (e in s.events) if (e.id !in seen && (e.type in told || (e.type == EventType.WEAPON_FORGED && e.data["heartfire"] == "true"))) { seen += e.id; lines += "day ${e.day} | ${e.text.take(230)}" }
            }
            for (e in end.events) if (e.id !in seen && (e.type in told || (e.type == EventType.WEAPON_FORGED && e.data["heartfire"] == "true"))) lines += "day ${e.day} | ${e.text.take(230)}"
            println("Transcript: seed $seed, charter $charter, policy ${p.name}, opening ${stats.opening}, balance ${config.version}. Ended on day ${stats.days}: " + (if (stats.retired) "charter secured on day ${stats.charterDay}, chapter closed" else if (stats.defeated) "the forge fell" else "still standing at the horizon"))
            lines.sortedBy { it.substringAfter("day ").substringBefore(" ").toIntOrNull() ?: 0 }.forEach { println("  $it") }
            println()
        }
        return
    }
    // --classic: what rules 6 do to a run without a charter. Legendary weapons by origin, renown earned and Heartfire left in the forge at the end.
    if ("--classic" in args) {
        println("Classic runs (no charter): $runs seeds from $seed to day $horizon, ${if (maxed) "maxed legacy" else "new account"}, balance ${config.version}. Smiths spend Heartfire${if ("--noSpend" in args) " never" else " when the workbench shows a chance worth it"}.")
        for (policy in listOf(Policy.BALANCED_ACTIVE, Policy.EXPERT)) {
            val rows = IntStream.range(0, runs).parallel().mapToObj { i ->
                val driver = SimulationDriver(GameEngine(config = config), maxDays = horizon, spendHeartfire = "--noSpend" !in args)
                val end = driver.playRun(if (maxed) Simulator.maxedLegacy(GameEngine()) else LegacyProfile(), seed + i, policy).second
                Triple(end, driver.heartfireForges, driver.heartfireBreakthroughs)
            }.collect(Collectors.toList())
            fun made(e: GameState) = e.weapons.values.filter { it.forgedDay > 0 && it.legendKey == null }
            val ends = rows.map { it.first }
            val n = runs.toDouble()
            val deeds = ends.map { e -> e.weapons.values.count { it.legend == LegendOrigin.DEEDS } }.sorted()
            val all = ends.map { e -> e.weapons.values.count { it.legend != null } }.sorted()
            // Heartfire earned = what was spent plus what is left: a classic run gains it only from sieges won.
            val earned = rows.sumOf { it.second + it.first.heartfire } / n
            val firstEpic = ends.map { e -> made(e).filter { it.rarity >= Rarity.EPIC }.minOfOrNull { it.forgedDay } ?: Int.MAX_VALUE }.sorted()
            val firstLegend = ends.mapNotNull { e -> e.events.filter { it.type == EventType.LEGEND_EARNED || (it.type == EventType.WEAPON_FORGED && it.data["breakthrough"] == "true") }.minOfOrNull { it.day } }
            println("classic %-16s days mean %.1f | Legendary forged from quality %d | Heartfire a run: earned %.2f, forgings %.2f, broke through %.2f, left at the end %.2f | Legendary a run: mean %.2f median %d p90 %d, runs with one %.0f%% (by deeds %.2f, by Heartfire %.2f), first on day %s".format(
                policy.name, ends.map { it.day }.average(), ends.sumOf { e -> e.weapons.values.count { it.rarity == Rarity.LEGENDARY && it.legend == null && it.legendKey == null } },
                earned, rows.sumOf { it.second } / n, rows.sumOf { it.third } / n, ends.map { it.heartfire }.average(), all.average(), all[all.size / 2], all[all.size * 9 / 10], 100.0 * all.count { it > 0 } / n,
                deeds.average(), ends.sumOf { e -> e.weapons.values.count { it.legend == LegendOrigin.HEARTFIRE } } / n, if (firstLegend.isEmpty()) "-" else "%.1f".format(firstLegend.average())))
            println("        %-16s Epic: forged by day 5 in %.0f%% of runs, by day 10 in %.0f%%, by day 20 in %.0f%%, median first day %s | weapons with renown a run %.1f | of what was forged: %s".format("",
                100.0 * firstEpic.count { it <= 5 } / n, 100.0 * firstEpic.count { it <= 10 } / n, 100.0 * firstEpic.count { it <= 20 } / n, firstEpic[firstEpic.size / 2].let { if (it == Int.MAX_VALUE) "none" else it.toString() },
                ends.sumOf { e -> e.weapons.values.count { it.renown > 0 } } / n,
                Rarity.entries.joinToString(" ") { r -> "%s=%.0f%%".format(r.name.take(1), 100.0 * ends.sumOf { e -> made(e).count { it.rarity == r } } / ends.sumOf { e -> made(e).size }.coerceAtLeast(1)) }))
        }
        return
    }
    println("Guild runs: $runs seeds from $seed, charter $charter, horizon day $horizon, ${if (maxed) "maxed legacy" else "new account"}, balance ${BalanceConfig.DEFAULT.version}")
    // --drawbackPair POLICY: the policy on the same seeds with every opening's drawback and without it, package by package.
    a["--drawbackPair"]?.let { name ->
        val p = GuildPolicy.valueOf(name.uppercase())
        val with = GuildSim.run(p, runs, seed, config, charterId = charter, horizon = horizon)
        val without = GuildSim.run(p, runs, seed, config, charterId = charter, horizon = horizon, noDrawback = true)
        println("${p.name}: 'plan' below reads 'with the drawback', 'ignoring it' reads 'without the drawback'")
        print(GuildSim.planPaired(with, without))
        return
    }
    // --planPair POLICY: the policy on the same seeds with its opening's plan followed and ignored, package by package.
    a["--planPair"]?.let { name ->
        val p = GuildPolicy.valueOf(name.uppercase())
        val with = GuildSim.run(p, runs, seed, config, charterId = charter, horizon = horizon, opening = a["--opening"])
        val without = GuildSim.run(p, runs, seed, config, charterId = charter, horizon = horizon, opening = a["--opening"], followPlan = false)
        println("${p.name} with its opening's plan: charter ${"%.0f".format(100.0 * with.runs.count { it.charterDay != null } / runs)} % (${interval(with.runs.count { it.charterDay != null }, runs)}). Ignoring it: ${"%.0f".format(100.0 * without.runs.count { it.charterDay != null } / runs)} % (${interval(without.runs.count { it.charterDay != null }, runs)})")
        print(GuildSim.planPaired(with, without)); print(GuildSim.byOpening(with))
        return
    }
    if ("--why" in args) for (p in policies) { val bot = GuildBot(GameEngine(config = config), p, charter, horizon); bot.play(seed); println("${p.name}: ${bot.rejected} rejected"); bot.rejections.forEach { println("  $it") } }
    // --legacy: every run begun before living heroes (the feature-off baseline). --living: the members' own decisions, per policy.
    val revision = if ("--legacy" in args) com.tinyblacksmith.core.model.LivingRevision.LEGACY else com.tinyblacksmith.core.model.LivingRevision.CURRENT
    val reports = policies.map { p -> GuildSim.run(p, runs, seed, config, charterId = charter, horizon = horizon, legacy = { if (maxed) Simulator.maxedLegacy(it) else LegacyProfile() }, opening = a["--opening"], retire = "--playOn" !in args, livingRevision = revision, personal = a["--personal"]) }
    for (r in reports) { print(r.render()); if ("--byOpening" in args) print(GuildSim.byOpening(r)); if ("--living" in args) print(GuildSim.livingTable(r)) }
    // --pair A,B[+C,D]: two policies on the same seeds (both must be among --policy). Several pairs are joined with a plus.
    a["--pair"]?.split("+")?.forEach { pair -> pair.split(",").map { GuildPolicy.valueOf(it.uppercase()) }.let { (x, y) -> print(GuildSim.paired(reports.first { it.policy == x }, reports.first { it.policy == y })) } }
}
