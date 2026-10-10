package com.tinyblacksmith.core.sim

import com.tinyblacksmith.core.combat.EventKind
import com.tinyblacksmith.core.combat.Posture
import com.tinyblacksmith.core.config.BalanceConfig
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
}

data class GuildRunStats(
    val seed: Long, val policy: GuildPolicy, val days: Int, val charterDay: Int?, val defeated: Boolean, val retired: Boolean,
    val contracts: Map<String, Int>, val captured: Int, val rescued: Int, val membersDead: Int, val sieges: Map<SiegeVerdict, Int>, val combos: Int, val firstComboDay: Int?,
    val goldAtEnd: Int, val members: Int, val rejected: Int, val firstSiegeIntegrity: Int?, val loansMade: Int,
)

class GuildBot(val engine: GameEngine, val policy: GuildPolicy, val charterId: String = GuildContent.TOWNS_LAST_HOPE, val horizon: Int = 30) {
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

    private fun GameState.send(cmd: Command): GameState = when (val o = engine.handle(this, cmd)) {
        is CommandOutcome.Accepted -> o.state
        is CommandOutcome.Rejected -> { rejected++; if (rejections.size < 20) rejections += "day $day ${cmd::class.simpleName}: ${o.error}"; this }
    }

    fun play(seed: Long, legacy: LegacyProfile = LegacyProfile(), onDay: (GameState) -> Unit = {}): Pair<GuildRunStats, GameState> {
        var s = engine.newRun(legacy, seed, charterId = charterId)
        while (!s.isEnded && s.day <= horizon) {
            s = planDay(s)
            onDay(s)
            val before = s
            s = s.send(Command.EndDay(CommandId("${s.runId.value}:day${s.day}")))
            record(before, s)
            // A bot that has secured the charter closes the chapter: a completed success, not a missing defeat.
            if (!s.isEnded && s.guild?.milestone(GuildContent.CHARTER_SECURED) != null && s.guild?.mission == null) s = s.send(Command.RetireAfterMilestone(GuildContent.CHARTER_SECURED))
        }
        val g = s.guild!!
        return GuildRunStats(seed, policy, s.day, g.milestone(GuildContent.CHARTER_SECURED)?.day, s.isEnded && !g.retired, g.retired, contracts.toMap(), captured, rescued, dead, sieges.toMap(), g.comboNotes.size, firstCombo,
            s.gold, g.members.size, rejected, firstSiegeIntegrity, loans) to s
    }

    private fun record(before: GameState, after: GameState) {
        val r = after.lastResolution ?: return
        r.mission?.let { m -> if (!m.continues) contracts.merge("${guildCat.mission(m.defId).archetype.name}:${m.outcome.name}", 1, Int::plus) }
        r.siege?.let { sieges.merge(it.verdict, 1, Int::plus); if (firstSiegeIntegrity == null) firstSiegeIntegrity = after.town.integrity }
        captured += r.events.count { it.type == EventType.MEMBER_CAPTURED }
        rescued += r.events.count { it.type == EventType.MEMBER_RESCUED }
        dead += before.guild!!.members.count { m -> after.heroes[m.heroId]?.isAlive != true }
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
        GuildPolicy.SYNERGY, GuildPolicy.ADAPTIVE -> s.guild!!.mission!!.party.all { (s.heroes[it]?.health ?: 0) >= 70 }
        else -> false
    }

    private fun relic(s: GameState): GameState {
        val offer = s.pendingRelicOffer
        val wanted = when (policy) {
            GuildPolicy.SYNERGY, GuildPolicy.ADAPTIVE -> listOf("overflow_basin", "salvage_bell", "storm_ledger", "blood_receipt", "furnace_lung", "bone_music_box")
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
        val target = when (policy) { GuildPolicy.SHOP_FOCUSED -> 2; GuildPolicy.NOVICE -> 3; else -> 4 }
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
        if (policy != GuildPolicy.SYNERGY && policy != GuildPolicy.ADAPTIVE) return base
        val cls = s.heroes[heroId]?.classId?.value
        val rules = f.effects.map { it.id }.toSet()
        var bonus = 0
        if ("catalyst_capacitor" in rules && cls == "guardian") bonus += 12
        if ("affix_brittle" in rules && cls == "guardian" && s.relics.any { it.id == "salvage_bell" }) bonus += 10
        if ("catalyst_runed" in rules && s.relics.any { it.id == "salvage_bell" }) bonus += 6
        if (w.element != null) bonus += 3
        return base + bonus
    }

    private fun forge(s0: GameState): GameState {
        var s = s0
        var guard = 0
        while (guard++ < 6 && s.energy >= cfg.quickForgeEnergy) {
            val members = s.guild!!.members.mapNotNull { s.heroes[it.heroId] }
            // For whom: the member with nothing in hand, else the shelf.
            val bare = members.firstOrNull { s.loanOf(it.id) == null && s.equippedWeapon(it.id) == null && s.storedWeapons().none { w -> w.promisedTo == null } }
            val familyId = bare?.let { content.heroClass(it.classId).preferredFamilies.first() } ?: content.families[(s.day + guard) % content.families.size].id
            val cores = content.materials(MaterialCategory.CORE).sortedBy { it.tier }
            val augments = content.materials(MaterialCategory.AUGMENT).sortedBy { it.tier }
            fun have(m: MaterialId) = (s.materials[m] ?: 0) > 0
            fun afford(m: com.tinyblacksmith.core.content.MaterialDef) = have(m.id) || (s.gold >= engine.materialPrice(s, m.id) + 40 && (s.supplierStock[m.id] ?: 1) > 0)
            val rich = policy == GuildPolicy.HIGHEST_STAT || policy == GuildPolicy.GREEDY_DIVER
            // The spendthrifts buy the best metal they can; the others the best they can while a reserve of 150 gold stays in the till.
            fun comfortable(m: com.tinyblacksmith.core.content.MaterialDef) = have(m.id) || (s.gold >= engine.materialPrice(s, m.id) + 150 && (s.supplierStock[m.id] ?: 1) > 0)
            val core = (if (rich) cores.lastOrNull { afford(it) } else if (policy == GuildPolicy.NOVICE || policy == GuildPolicy.PASSIVE) cores.firstOrNull { have(it.id) } ?: cores.firstOrNull { afford(it) }
                else cores.lastOrNull { comfortable(it) } ?: cores.firstOrNull { afford(it) }) ?: break
            val weak = s.siege?.factionId?.let { content.faction(it).weakTo }
            val augment = when (policy) {
                GuildPolicy.SYNERGY, GuildPolicy.ADAPTIVE -> augments.firstOrNull { it.id == com.tinyblacksmith.core.content.LaunchContent.STORMGLASS && afford(it) && bare?.classId?.value == "guardian" } ?: augments.firstOrNull { it.element == weak && afford(it) }
                GuildPolicy.DEFENCE_FIRST -> augments.firstOrNull { it.element == weak && afford(it) }
                else -> null
            } ?: (if (rich) augments.lastOrNull { afford(it) } else augments.firstOrNull { have(it.id) } ?: augments.firstOrNull { afford(it) }) ?: break
            val catalyst = if ((policy == GuildPolicy.SYNERGY || policy == GuildPolicy.ADAPTIVE) && s.energy >= cfg.advancedForgeEnergy)
                content.materials(MaterialCategory.CATALYST).firstOrNull { have(it.id) && (it.id == com.tinyblacksmith.core.content.LaunchContent.BINDING_SALT) == (bare?.classId?.value == "guardian" || bare == null) }
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
            val stock = s.storedWeapons().filter { it.promisedTo == null && it.id !in keep }
            val current = s.loanOf(m.heroId) ?: s.equippedWeapon(m.heroId)
            val best = stock.maxByOrNull { worth(s, m.heroId, it) } ?: continue
            if (current == null || worth(s, m.heroId, best) > worth(s, m.heroId, current) + 2) { s = s.send(Command.LoanWeapon(m.heroId, best.id)); loans++ }
        }
        // A cracked or badly worn loan is mended by a smith who thinks about tomorrow.
        if (policy == GuildPolicy.RECOVERY_AWARE || policy == GuildPolicy.ADAPTIVE || policy == GuildPolicy.SYNERGY) for (m in members) {
            val w = s.loanOf(m.heroId) ?: continue
            if (w.condition < 60 && s.energy >= cfg.honeEnergy && (s.materials[w.coreId] ?: 0) > 0) {
                s = s.send(Command.RecallLoan(w.id)); s = s.send(Command.Hone(w.id)); s = s.send(Command.LoanWeapon(m.heroId, w.id))
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

    private fun wall(s0: GameState): GameState {
        var s = s0
        if (policy != GuildPolicy.DEFENCE_FIRST && policy != GuildPolicy.ADAPTIVE) return s
        if (s.town.nextSiegeDay - s.day > 1) return s
        val fit = s.guild!!.members.filter { engine.memberUnavailable(s, it.heroId) == null && it.heroId !in s.guild!!.reserved }
        for (m in fit.sortedByDescending { engine.fighterView(s, it.heroId, home = true)?.let { f -> f.maxHealth + f.strikeMin * 3 } ?: 0 }.take(cfg.guild.defenders - s.guild!!.reserved.size)) s = s.send(Command.ReserveDefender(m.heroId, true))
        return s
    }

    private fun deploy(s0: GameState): GameState {
        val s = s0
        val g = s.guild!!
        if (g.mission != null) return s
        val fit = g.members.filter { engine.memberUnavailable(s, it.heroId) == null && it.heroId !in g.reserved }.map { it.heroId }
        if (fit.isEmpty()) return s
        val keepHome = siegeTonight(s) && policy in setOf(GuildPolicy.DEFENCE_FIRST, GuildPolicy.RECOVERY_AWARE, GuildPolicy.ADAPTIVE, GuildPolicy.NOVICE, GuildPolicy.SYNERGY)
        if (keepHome) return s
        fun arch(o: MissionOffer) = guildCat.mission(o.defId).archetype
        // A two-day contract that would keep the party away over the siege night is one the careful leave on the board.
        val careful = policy in setOf(GuildPolicy.DEFENCE_FIRST, GuildPolicy.RECOVERY_AWARE, GuildPolicy.ADAPTIVE, GuildPolicy.SYNERGY, GuildPolicy.NOVICE)
        val offers = g.offers.filter { o -> s.gold >= o.fee && !(careful && s.day + o.days - 1 >= s.town.nextSiegeDay) }
        val health = fit.sumOf { s.heroes[it]?.health ?: 0 } / fit.size
        val order: List<MissionArchetype> = when (policy) {
            GuildPolicy.NOVICE -> listOf(MissionArchetype.SUPPLY)
            GuildPolicy.SHOP_FOCUSED -> listOf(MissionArchetype.SUPPLY, MissionArchetype.ESCORT)
            GuildPolicy.HIGHEST_STAT -> listOf(MissionArchetype.RECOVERY, MissionArchetype.DELVE, MissionArchetype.HUNT, MissionArchetype.ESCORT, MissionArchetype.SUPPLY)
            GuildPolicy.GREEDY_DIVER -> listOf(MissionArchetype.DELVE, MissionArchetype.HUNT, MissionArchetype.RECOVERY, MissionArchetype.ESCORT, MissionArchetype.SUPPLY)
            GuildPolicy.DEFENCE_FIRST -> listOf(MissionArchetype.SABOTAGE, MissionArchetype.RESCUE, MissionArchetype.SUPPLY, MissionArchetype.ESCORT) + (if (s.town.integrity < 70) listOf(MissionArchetype.EMERGENCY) else emptyList())
            GuildPolicy.RECOVERY_AWARE -> (if (s.town.integrity < 60) listOf(MissionArchetype.EMERGENCY) else emptyList()) + listOf(MissionArchetype.RESCUE, MissionArchetype.RECOVERY, MissionArchetype.SUPPLY, MissionArchetype.ESCORT, MissionArchetype.SABOTAGE)
            GuildPolicy.SYNERGY -> listOf(MissionArchetype.RESCUE, MissionArchetype.HUNT, MissionArchetype.SABOTAGE, MissionArchetype.ESCORT, MissionArchetype.SUPPLY)
            GuildPolicy.ADAPTIVE -> listOf(MissionArchetype.RESCUE) + (if (s.town.integrity < 50) listOf(MissionArchetype.EMERGENCY) else emptyList()) + listOf(MissionArchetype.SABOTAGE) +
                (if (health >= 75 && fit.size >= 2) listOf(MissionArchetype.RECOVERY, MissionArchetype.HUNT, MissionArchetype.ESCORT) else emptyList()) + listOf(MissionArchetype.SUPPLY)
            GuildPolicy.PASSIVE -> emptyList()
        }
        val offer = order.firstNotNullOfOrNull { a -> offers.firstOrNull { arch(it) == a } } ?: return s
        val posture = when (policy) {
            GuildPolicy.GREEDY_DIVER -> Posture.RECKLESS
            GuildPolicy.NOVICE, GuildPolicy.RECOVERY_AWARE -> Posture.CAUTIOUS
            else -> Posture.BALANCED
        }
        // The front first: whoever intercepts stands in the first place, the healer behind.
        val party = fit.sortedBy { when (s.heroes[it]?.classId?.value) { "guardian" -> 0; "duelist" -> 1; "ranger" -> 2; "battlemage" -> 3; else -> 4 } }.take(if (arch(offer) == MissionArchetype.EMERGENCY) 1 else cfg.guild.partyMax)
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
        }
    }
}

object GuildSim {
    fun run(policy: GuildPolicy, runs: Int, seed: Long, config: BalanceConfig = BalanceConfig.DEFAULT, charterId: String = GuildContent.TOWNS_LAST_HOPE, horizon: Int = 30, legacy: (GameEngine) -> LegacyProfile = { LegacyProfile() }): GuildReport {
        val list = IntStream.range(0, runs).parallel().mapToObj { i ->
            val engine = GameEngine(config = config)
            GuildBot(engine, policy, charterId, horizon).play(seed + i, legacy(engine)).first
        }.collect(Collectors.toList())
        return GuildReport(policy, list.sortedBy { it.seed })
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
    val config = BalanceConfig.DEFAULT.copy(guild = g0.copy(
        enemyPercentBase = sets["enemyPercentBase"]?.toInt() ?: g0.enemyPercentBase, enemyPercentPerDay = sets["enemyPercentPerDay"]?.toDouble() ?: g0.enemyPercentPerDay,
        siegePercentBase = sets["siegePercentBase"]?.toInt() ?: g0.siegePercentBase, siegePercentPerDay = sets["siegePercentPerDay"]?.toDouble() ?: g0.siegePercentPerDay,
        charterWarlordPercent = sets["charterWarlordPercent"]?.toInt() ?: g0.charterWarlordPercent, outerHealthPerPoint = sets["outerHealthPerPoint"]?.toDouble() ?: g0.outerHealthPerPoint,
        siegeGrunts = sets["siegeGrunts"]?.toInt() ?: g0.siegeGrunts))
    println("Guild runs: $runs seeds from $seed, charter $charter, horizon day $horizon, ${if (maxed) "maxed legacy" else "new account"}, balance ${BalanceConfig.DEFAULT.version}")
    if ("--why" in args) for (p in policies) { val bot = GuildBot(GameEngine(config = config), p, charter, horizon); bot.play(seed); println("${p.name}: ${bot.rejected} rejected"); bot.rejections.forEach { println("  $it") } }
    for (p in policies) print(GuildSim.run(p, runs, seed, config, charterId = charter, horizon = horizon, legacy = { if (maxed) Simulator.maxedLegacy(it) else LegacyProfile() }).render())
}
