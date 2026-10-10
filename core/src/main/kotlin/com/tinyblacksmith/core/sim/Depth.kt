package com.tinyblacksmith.core.sim

import com.tinyblacksmith.core.battle.Battle
import com.tinyblacksmith.core.content.ContentCatalog
import com.tinyblacksmith.core.content.Depth
import com.tinyblacksmith.core.content.Element
import com.tinyblacksmith.core.content.MaterialCategory
import com.tinyblacksmith.core.content.MaterialDef
import com.tinyblacksmith.core.content.ToolEffect
import com.tinyblacksmith.core.content.UpgradeEffect
import com.tinyblacksmith.core.content.WeaponFamilyDef
import com.tinyblacksmith.core.engine.Command
import com.tinyblacksmith.core.engine.CommandOutcome
import com.tinyblacksmith.core.engine.Encounters
import com.tinyblacksmith.core.engine.GameEngine
import com.tinyblacksmith.core.engine.WorldEvents
import com.tinyblacksmith.core.market.Commissions
import com.tinyblacksmith.core.model.*
import kotlinx.serialization.Serializable

/*
 * How the simulator's bots play the gameplay-depth systems (docs/GAMEPLAY_DEPTH_PLAN.md): morning visitors, workshop
 * relics and siege traits. Every action is a real engine command sent through [DepthPlay]; a command the engine
 * rejects is counted ([DepthRunStats.rejected]) and the tests assert it stays zero. The numbers named here are bot
 * habits, not game rules.
 */

/** Recipe arithmetic shared by the bots: what a forge averages and the cheapest recipe that reaches a quality floor. */
internal object Recipes {
    /** A recipe is taken as reaching a floor when its average quality clears it by this much (the roll is plus or minus `qualityRollSpread`). */
    const val QUALITY_MARGIN = 4

    fun affinity(content: ContentCatalog, core: MaterialDef, augment: MaterialDef, family: WeaponFamilyDef): Int =
        (content.coreAugmentAffinity[core.id to augment.id] ?: 0) + (content.augmentFamilyAffinity[augment.id to family.id] ?: 0)

    /** The quality a forge averages: formula of `Forge.apply` without roll, exceptional, defect or relic. */
    fun expectedQuality(engine: GameEngine, state: GameState, family: WeaponFamilyDef, core: MaterialDef, augment: MaterialDef, catalyst: Boolean): Int {
        val cfg = engine.config
        return cfg.qualityBase + cfg.qualityPerCoreTier * core.tier + cfg.qualityPerAugmentTier * augment.tier + affinity(engine.content, core, augment, family) +
            engine.upgradeTotal(state.legacy, UpgradeEffect.QUALITY_BONUS) + engine.toolTotal(state, ToolEffect.QUALITY_BONUS) + (if (catalyst) cfg.catalystQualityBonus else 0)
    }

    /** Gold to buy what the shop does not hold of [mats]; null when one of them is out of stock. */
    fun cost(engine: GameEngine, state: GameState, mats: List<MaterialDef>): Int? {
        var gold = 0
        for (m in mats) {
            if ((state.materials[m.id] ?: 0) > 0) continue
            if ((state.supplierStock[m.id] ?: 1) < 1) return null
            gold += engine.materialPrice(state, m.id)
        }
        return gold
    }

    /**
     * A core and an augment for a [family] blade of [minQuality] that the shop holds or can buy within [budget]: the
     * cheapest pair whose average clears the floor, else the pair with the best average. Null when nothing is obtainable.
     */
    fun pick(engine: GameEngine, state: GameState, family: WeaponFamilyDef, minQuality: Int, element: Element? = null, budget: Int = state.gold): Pair<MaterialDef, MaterialDef>? {
        val content = engine.content
        val options = content.materials(MaterialCategory.CORE).flatMap { core ->
            content.materials(MaterialCategory.AUGMENT).filter { element == null || it.element == element }.mapNotNull { aug ->
                cost(engine, state, listOf(core, aug))?.takeIf { it <= budget }?.let { Triple(core, aug, it) }
            }
        }
        val sure = options.filter { reaches(engine, state, family, it.first, it.second, minQuality) }
        return (sure.minByOrNull { it.third } ?: options.maxByOrNull { expectedQuality(engine, state, family, it.first, it.second, false) })?.let { it.first to it.second }
    }

    fun reaches(engine: GameEngine, state: GameState, family: WeaponFamilyDef, core: MaterialDef, augment: MaterialDef, minQuality: Int): Boolean =
        expectedQuality(engine, state, family, core, augment, false) >= minQuality + QUALITY_MARGIN
}

/**
 * How a bot answers a morning visitor (`--encounters`). [choose] never returns an option the view shows as blocked;
 * when nothing it wants is open it returns [Encounters.PASS], the free answer every visitor has.
 */
enum class EncounterPref {
    /** Sends every visitor away. */
    DECLINE,
    /** The first answer that is open, in the order the card lists them: the simple heuristic. */
    FIRST,
    /** Whatever brings gold now or soon; never spends on the town's defense. */
    CASH,
    /** Whatever arms the town; otherwise passes. */
    DEFENSE,
    /** Reads the run: the siege forecast, the purse, the energy and what it can actually make ([adaptive]). */
    ADAPTIVE;

    fun choose(view: Encounters.View, state: GameState, engine: GameEngine): String {
        val open = view.options.filter { it.blocked == null && it.id != Encounters.PASS }.map { it.id }
        fun first(vararg wanted: String): String = wanted.firstOrNull { it in open } ?: Encounters.PASS
        return when (this) {
            DECLINE -> Encounters.PASS
            FIRST -> open.firstOrNull() ?: Encounters.PASS
            CASH -> first("sell", "collector", "patron", "order", "stall", "collect")
            DEFENSE -> first("crate", "pledge", "restore", "watch", "forgive", "speak")
            ADAPTIVE -> adaptive(view.instance, state, engine, ::first)
        }
    }

    private fun adaptive(inst: EncounterInstance, state: GameState, engine: GameEngine, first: (Array<out String>) -> String): String {
        val cfg = engine.config
        fun n(key: String) = inst.amounts[key] ?: 0
        fun take(vararg wanted: String) = first(wanted)
        val outlook = engine.siegeForecast(state)
        // The odds enum runs from STRONG down to DIRE.
        val outmatched = outlook != null && outlook.odds > Battle.SiegeOdds.EVEN
        val threatened = outmatched || state.town.nextSiegeDay - state.day <= SIEGE_NEAR_DAYS
        val poor = state.gold < LOW_GOLD
        fun keeps(spend: Int) = state.gold - spend >= LOW_GOLD
        return when (inst.defId) {
            Depth.LAST_CRATE -> if (keeps(n("cratePrice"))) take("crate") else Encounters.PASS
            Depth.BLADE_FOR_THE_WALL -> if (threatened && !poor) take("pledge", "patron") else take("patron", "pledge")
            Depth.MASTERS_AFTERNOON -> {
                val busy = threatened || state.consequences.any { it.kind == ConsequenceKind.WAGER }
                val spare = !busy && state.energy - n("energy") >= cfg.advancedForgeEnergy
                val rich = !threatened && state.gold - n("gold") >= 2 * LOW_GOLD
                if (spare) take("study", *(if (rich) arrayOf("lesson") else emptyArray())) else if (rich) take("lesson") else Encounters.PASS
            }
            // A storied blade is worth more on the wall than in a vault while the town is in danger.
            Depth.COLLECTORS_OFFER -> if (poor || !threatened) take("sell") else Encounters.PASS
            Depth.CRACKED_FAMILY_BLADE -> if (poor) take("collector", "restore") else take("restore")
            Depth.CROOKED_MERCHANT -> {
                val blade = inst.blade
                when {
                    // Unseen, the flaw is not the bot's to read: it pays to look first, and only when it could then also buy.
                    !inst.inspected -> if (keeps(n("price") + n("fee"))) take("inspect") else Encounters.PASS
                    blade != null && engine.suggestedPrice(blade) >= n("price") * MERCHANT_MARGIN && blade.flaws.none { harmful(engine, it) } && keeps(n("price")) -> take("buy")
                    else -> Encounters.PASS
                }
            }
            Depth.SMITHS_WAGER -> {
                val family = inst.familyId?.let { engine.content.familyById[it] }
                val recipe = family?.let { Recipes.pick(engine, state, it, n("quality"), budget = state.gold - n("stake")) }
                val can = family != null && recipe != null && state.energy >= cfg.quickForgeEnergy && Recipes.reaches(engine, state, family, recipe.first, recipe.second, n("quality"))
                // The prize is a relic: worth the stake while a slot is free, or when every relic is held and he pays in coin instead.
                val prize = state.relics.size < cfg.depth.relicSlots || engine.content.relics.all { r -> state.relics.any { it.id == r.id } }
                if (can && prize) take("wager", "order") else take("order")
            }
            Depth.FESTIVAL_CONTRACT -> {
                val stock = state.weapons.values.count { (it.isListed || it.isInStorage) && it.promisedTo == null }
                when {
                    threatened && state.town.armory < cfg.armoryMax -> take("watch")
                    !poor && stock >= STALL_STOCK -> take("stall")
                    poor -> take("watch")
                    else -> Encounters.PASS
                }
            }
            Depth.DEBT_REPAID -> if (poor) take("collect", "forgive") else if (outmatched) take("forgive") else take("speak", "forgive")
            else -> Encounters.PASS
        }
    }

    companion object {
        /** Bot habits. */
        const val SIEGE_NEAR_DAYS = 3
        const val LOW_GOLD = 60
        /** The merchant's blade must be worth this much more at the going rate than he asks. */
        const val MERCHANT_MARGIN = 1.25
        /** Blades in the shop that make a festival crowd worth paying for. */
        const val STALL_STOCK = 3

        /** A flaw that hurts or disarms whoever carries the blade. */
        private fun harmful(engine: GameEngine, flaw: AffixId): Boolean =
            engine.content.affix(flaw).let { it.breakChanceOnLoss > 0.0 || it.damageTakenMultiplier > 1.0 || it.selfDamageOnWin > 0 }

        /** `--encounters`: throws [IllegalArgumentException] on an unknown name. */
        fun parse(arg: String): EncounterPref =
            requireNotNull(entries.firstOrNull { it.name.equals(arg, ignoreCase = true) }) { "unknown '$arg'; allowed: ${entries.joinToString(", ") { it.name.lowercase() }}" }
    }
}

/**
 * Which relic a bot takes from an offer (`--relic`). With every slot filled, a bot that wants the offered relic puts
 * away the held one that has triggered least, but only if that one never triggered; otherwise it declines.
 */
sealed interface RelicPref {
    val label: String

    /** The first relic offered. */
    data object First : RelicPref { override val label = "first" }
    /** Declines every offer. */
    data object None : RelicPref { override val label = "none" }
    /** By policy: the bellows for a bot that overworks, the crucible for one that salvages, the seal for one that prices high, the ledger otherwise. */
    data object Adaptive : RelicPref { override val label = "adaptive" }
    /** Only this relic: taken when offered, every other offer declined, so a sweep isolates it. */
    data class Fixed(val id: String) : RelicPref { override val label: String get() = id }

    companion object {
        /** `--relic first|adaptive|none|<relicId>`: throws [IllegalArgumentException] on anything else. */
        fun parse(arg: String, content: ContentCatalog): RelicPref = when (arg.lowercase()) {
            "first" -> First
            "none" -> None
            "adaptive" -> Adaptive
            else -> Fixed(requireNotNull(content.relic(arg.lowercase())) { "unknown '$arg'; allowed: first, adaptive, none, ${content.relics.joinToString(", ") { it.id }}" }.id)
        }
    }
}

/** The visitor habit of a policy when `--encounters` is not given. "Price-driven" is read as the bots whose whole point is the purse. */
fun Policy.defaultEncounters(): EncounterPref = when {
    this == Policy.PASSIVE -> EncounterPref.DECLINE
    this == Policy.EXPERT || this == Policy.EXPERT_ACTIVE -> EncounterPref.ADAPTIVE
    this == Policy.SPENDTHRIFT || this == Policy.BROKE_START || this == Policy.FREE_LISTINGS -> EncounterPref.CASH
    rules?.siegePrep == true -> EncounterPref.DEFENSE
    else -> EncounterPref.FIRST
}

/** The relic habit of a policy when `--relic` is not given. */
fun Policy.defaultRelic(): RelicPref = when (this) {
    Policy.PASSIVE -> RelicPref.None
    Policy.EXPERT, Policy.EXPERT_ACTIVE -> RelicPref.Adaptive
    else -> RelicPref.First
}

/** A catalog and config without the depth systems: the old automatic collector, wandering master and festival, and the old besieger rule (`--noDepth`). */
fun withoutDepth(content: ContentCatalog, config: com.tinyblacksmith.core.config.BalanceConfig): Pair<ContentCatalog, com.tinyblacksmith.core.config.BalanceConfig> =
    content.copy(encounters = emptyList(), relics = emptyList(), siegeTraits = emptyList()) to config.copy(depth = config.depth.copy(commitBesieger = false))

/** One run's play of the depth systems inside [SimulationDriver.playRun]: decides, sends the commands and counts. */
internal class DepthPlay(private val d: SimulationDriver, private val policy: Policy, private val encounters: EncounterPref, private val relic: RelicPref) {
    private val engine = d.engine
    private val content = engine.content
    private val cfg = engine.config
    private val adaptive = encounters == EncounterPref.ADAPTIVE || relic == RelicPref.Adaptive

    private val seen = HashSet<String>()
    /** Orders a visitor's answer put in the book, by commission ID. */
    private val orders = HashSet<String>()
    private val attempts = HashMap<String, Int>()
    private val triggers = HashMap<String, Int>()

    private var mornings = 0
    private val offers = sortedMapOf<String, Int>()
    private val chosen = sortedMapOf<String, Int>()
    private val expired = sortedMapOf<String, Int>()
    private val available = sortedMapOf<String, Int>()
    private val blocked = sortedMapOf<String, Int>()
    private val blockedOffers = sortedMapOf<String, Int>()
    private var relicOffers = 0
    private val relicsChosen = sortedMapOf<String, Int>()
    private var relicsReplaced = 0
    private var relicsDeclined = 0
    private var bellowsForges = 0
    private var crucibleReturns = 0
    private var sealsEarned = 0
    private var sealMaterials = 0
    private var ledgerForges = 0
    private var ledgerQuality = 0
    private val siegesWon = sortedMapOf<String, Int>()
    private val siegesLost = sortedMapOf<String, Int>()
    private var pledgesDelivered = 0
    private val pledgeOutcomes = sortedMapOf<String, Int>()
    private var wagersWon = 0
    private var wagersLost = 0
    private var visitorGoldIn = 0
    private var visitorGoldOut = 0
    private var bountyGold = 0
    private var orderGold = 0
    private var rejected = 0

    private fun MutableMap<String, Int>.inc(key: String, by: Int = 1) { this[key] = (this[key] ?: 0) + by }

    private fun send(state: GameState, cmd: Command): GameState = when (val out = engine.handle(state, cmd)) {
        is CommandOutcome.Accepted -> out.state
        is CommandOutcome.Rejected -> { rejected++; state }
    }

    private fun holds(state: GameState, relicId: String) = state.relics.any { it.id == relicId }

    /** A blade the bot may part with: in the shop, not kept for an order, not a returned legend, and not the answer to an accepted commission. */
    private fun spare(state: GameState): List<Weapon> {
        val open = state.commissions.values.filter { it.status == CommissionStatus.ACCEPTED }
        return (state.storedWeapons() + state.listedWeapons()).filter { w -> w.promisedTo == null && w.legendKey == null && open.none { Commissions.fit(w, it) == Commissions.Fit.OK } }
    }

    // ---- morning: the relic offer, the visitor, the crucible --------------------------------------------------

    fun morning(state: GameState): GameState {
        var s = state
        if (s.pendingRelicOffer.isNotEmpty()) s = answerRelicOffer(s)
        // The merchant's inspection leaves him at the forge, so one visitor can take two answers.
        var answers = 0
        while (s.encounter?.isOpen == true && answers++ < MAX_ANSWERS) {
            val view = engine.encounterView(s) ?: break
            val def = view.instance.defId
            if (seen.add(view.instance.id)) {
                mornings++
                offers.inc(def)
                for (o in view.options) if (o.id != Encounters.PASS) (if (o.blocked == null) available else blocked).inc("$def/${o.id}")
                if (view.options.any { it.id != Encounters.PASS && it.blocked != null }) blockedOffers.inc(def)
            }
            if (lacksTheCoreToRestore(s, view)) {
                // A bot that would make the family blade whole buys the unit of its core the work takes, then reads the card again.
                val stocked = send(s, Command.BuyMaterial(view.instance.materialId!!))
                visitorGoldOut += s.gold - stocked.gold
                if (stocked !== s) { s = stocked; continue }
            }
            val pick = encounters.choose(view, s, engine)
            val next = send(s, Command.ResolveEncounter(view.instance.id, pick, CommandId("${s.runId.value}:enc:${view.instance.id}:$pick")))
            if (next === s) break
            if (next.gold >= s.gold) visitorGoldIn += next.gold - s.gold else visitorGoldOut += s.gold - next.gold
            orders += (next.commissions.keys - s.commissions.keys).map { it.value }
            s = next
        }
        return meltForTheCrucible(s)
    }

    /** The cracked family blade, for a bot that restores (the defense and adaptive answers): the restoration is closed, its core is not on hand, and the supplier has one the purse covers. */
    private fun lacksTheCoreToRestore(state: GameState, view: Encounters.View): Boolean {
        val core = view.instance.materialId ?: return false
        return view.instance.defId == Depth.CRACKED_FAMILY_BLADE && (encounters == EncounterPref.DEFENSE || encounters == EncounterPref.ADAPTIVE) &&
            view.options.any { it.id == "restore" && it.blocked != null } && (state.materials[core] ?: 0) < 1 &&
            (state.supplierStock[core] ?: 1) >= 1 && engine.materialPrice(state, core) <= state.gold
    }

    private fun answerRelicOffer(state: GameState): GameState {
        relicOffers++
        val offer = state.pendingRelicOffer
        val want = when (relic) {
            RelicPref.None -> null
            RelicPref.First -> offer.first()
            RelicPref.Adaptive -> relicOrder().firstOrNull { it in offer }
            is RelicPref.Fixed -> relic.id.takeIf { it in offer }
        }
        val idle = state.relics.minByOrNull { triggers[it.id] ?: 0 }?.takeIf { (triggers[it.id] ?: 0) == 0 }
        val full = state.relics.size >= cfg.depth.relicSlots
        return when {
            want == null || (full && idle == null) -> { relicsDeclined++; send(state, Command.DeclineRelicOffer) }
            else -> send(state, Command.ChooseRelic(want, if (full) idle!!.id else null))
        }
    }

    /** What an adaptive bot wants first, by how its policy plays. */
    private fun relicOrder(): List<String> = (listOfNotNull(
        Depth.ASHEN_BELLOWS.takeIf { policy.overwork },
        Depth.SALVAGERS_CRUCIBLE.takeIf { policy.active },
        Depth.COLLECTORS_SEAL.takeIf { policy.priceFactor > 1.0 || policy.reputed },
    ) + listOf(Depth.TEMPERING_LEDGER, Depth.COLLECTORS_SEAL, Depth.SALVAGERS_CRUCIBLE, Depth.ASHEN_BELLOWS)).distinct()

    /**
     * Salvager's Crucible: a fine blade that has sat unsold is melted while the charge is ready, so its augment comes
     * back for another try. In the morning, because the forging that follows spends the day's energy. Only for a policy
     * that salvages anyway, an adaptive bot, or a run that asked for the crucible.
     */
    private fun meltForTheCrucible(state: GameState): GameState {
        if (!holds(state, Depth.SALVAGERS_CRUCIBLE) || !(policy.active || adaptive || relic == RelicPref.Fixed(Depth.SALVAGERS_CRUCIBLE))) return state
        if (state.energy < cfg.salvageEnergy) return state
        val blade = spare(state).filter { state.day - it.forgedDay >= UNSOLD_DAYS && engine.salvageKeepsAugment(state, it) }.minByOrNull { it.power } ?: return state
        return send(state, Command.Salvage(blade.id))
    }

    // ---- forging: what a visitor's answer promised, the ledger, the bellows -----------------------------------

    /** The forge a wager or a visitor's order asks for, a few tries each; null when nothing is owed or it cannot be made now. */
    fun obligationForge(state: GameState): Command.Forge? {
        if (state.energy < cfg.quickForgeEnergy) return null
        for (q in state.consequences.filter { it.kind == ConsequenceKind.WAGER }) {
            val family = q.familyId?.let { content.familyById[it] } ?: continue
            val quality = q.amounts["quality"] ?: 0
            val serial = q.amounts["serial"] ?: 0
            if (state.weapons.values.any { (it.id.value.drop(1).toIntOrNull() ?: 0) >= serial && it.familyId == family.id && it.quality >= quality }) continue
            tryFor(state, q.id, family, quality, null)?.let { return it }
        }
        // A bot with the requests rule forges for every accepted commission itself.
        if (policy.rules?.requests == true) return null
        for (c in state.commissions.values.filter { it.id.value in orders && it.status == CommissionStatus.ACCEPTED && it.kind != CommissionKind.HEIRLOOM }.sortedBy { it.deadlineDay }) {
            if (state.weapons.values.any { (it.isInStorage || it.isListed) && it.promisedTo == null && Commissions.fit(it, c) == Commissions.Fit.OK }) continue
            val family = content.familyById[c.familyId] ?: continue
            tryFor(state, c.id.value, family, c.minQuality, c.element)?.let { return it }
        }
        return null
    }

    private fun tryFor(state: GameState, key: String, family: WeaponFamilyDef, quality: Int, element: Element?): Command.Forge? {
        if ((attempts[key] ?: 0) >= MAX_TRIES) return null
        val (core, augment) = Recipes.pick(engine, state, family, quality, element) ?: return null
        attempts[key] = (attempts[key] ?: 0) + 1
        return Command.Forge(ForgeMode.QUICK, family.id, core.id, augment.id, null, Risk.BALANCED)
    }

    /**
     * What the held relics add to a forge the policy chose. Tempering Ledger: when the family is the bot's to choose
     * ([familyFree]) and already in the streak, a family outside it that suits the augment no worse. Ashen Bellows: the
     * day's first forge, when the overwork budget holds the debt.
     */
    fun decorate(state: GameState, cmd: Command.Forge, familyFree: Boolean, forgesToday: Int): Command.Forge {
        var c = cmd
        val streak = state.relics.firstOrNull { it.id == Depth.TEMPERING_LEDGER }?.families
        if (familyFree && streak != null && c.familyId in streak) {
            val own = content.augmentFamilyAffinity[c.augmentId to c.familyId] ?: 0
            content.families.firstOrNull { it.id !in streak && (content.augmentFamilyAffinity[c.augmentId to it.id] ?: 0) >= own }?.let { c = c.copy(familyId = it.id) }
        }
        if (forgesToday == 0 && wantsBellows(state, c)) c = c.copy(bellows = true)
        return c
    }

    /** Mirrors `Relics.bellowsError`: held, not used today, and room for the debt beside what the forge itself overworks. */
    private fun wantsBellows(state: GameState, cmd: Command.Forge): Boolean {
        if (!holds(state, Depth.ASHEN_BELLOWS) || state.relicUses[Depth.ASHEN_BELLOWS] == state.day) return false
        val cost = if (cmd.mode == ForgeMode.ADVANCED) cfg.advancedForgeEnergy else cfg.quickForgeEnergy
        if (maxOf(0, cost - state.energy) + cfg.depth.bellowsDebt > cfg.maxOverworkPerDay - state.overworkToday) return false
        if (policy.overwork || relic == RelicPref.Fixed(Depth.ASHEN_BELLOWS)) return true
        // An adaptive bot spends tomorrow only on a blade that should come out fine.
        return adaptive && Recipes.expectedQuality(engine, state, content.family(cmd.familyId), content.material(cmd.coreId), content.material(cmd.augmentId), cmd.catalystId != null) >= cfg.rareMin
    }

    // ---- evening: the council's bounty ----------------------------------------------------------------------

    /** While the council pays for arming the watch, the weakest spare blades go to it, as many as it still pays for. */
    fun evening(state: GameState): GameState {
        var s = state
        val bounty = s.consequences.firstOrNull { it.kind == ConsequenceKind.WATCH_BOUNTY && s.day <= it.dueDay } ?: return s
        repeat(bounty.amounts["left"] ?: 0) {
            if (s.town.armory >= cfg.armoryMax) return s
            val blade = spare(s).minByOrNull { it.power } ?: return s
            s = send(s, Command.DonateWeapon(blade.id))
        }
        return s
    }

    // ---- counting -------------------------------------------------------------------------------------------

    /** After End Day. The day's resolution carries every record of the day, the planning ones included, so each is counted once, before the log forgets it. */
    fun observe(pre: GameState, out: GameState, res: DayResolution) {
        var lostToday = 0
        for (e in res.events) when (e.type) {
            EventType.ENCOUNTER_RESOLVED -> if (e.data["option"] == "lost") { wagersLost++; lostToday++ } else chosen.inc("${e.data["encounter"]}/${e.data["option"]}")
            EventType.ENCOUNTER_EXPIRED -> expired.inc(e.data["encounter"] ?: "?")
            EventType.RELIC_CHOSEN -> { relicsChosen.inc(e.data["relic"] ?: "?"); if ("replaced" in e.data) relicsReplaced++ }
            EventType.RELIC_TRIGGERED -> { sealsEarned++; triggers.inc(Depth.COLLECTORS_SEAL); if ("material" in e.data) sealMaterials++ }
            EventType.WEAPON_FORGED -> {
                if (e.data["bellows"] == "true") { bellowsForges++; triggers.inc(Depth.ASHEN_BELLOWS) }
                e.data["ledger"]?.toIntOrNull()?.let { ledgerForges++; ledgerQuality += it; triggers.inc(Depth.TEMPERING_LEDGER) }
            }
            EventType.WEAPON_SALVAGED -> if ("augment" in e.data) { crucibleReturns++; triggers.inc(Depth.SALVAGERS_CRUCIBLE) }
            EventType.WEAPON_DONATED -> bountyGold += e.data["bounty"]?.toIntOrNull() ?: 0
            EventType.SIEGE_WON -> siegesWon.inc(e.data["trait"] ?: PLAIN)
            EventType.SIEGE_LOST -> siegesLost.inc(e.data["trait"] ?: PLAIN)
            EventType.PLEDGE_RESOLVED -> pledgeOutcomes.inc(e.data["outcome"] ?: "?")
            EventType.COMMISSION_COMPLETED -> {
                if (e.data["kind"] == CommissionKind.WALL_PLEDGE.name) pledgesDelivered++
                if (e.subjectIds.getOrNull(2)?.let { it in orders } == true) orderGold += e.data["reward"]?.toIntOrNull() ?: 0
            }
            else -> {}
        }
        // A wager that left the book without a loss was won.
        val open = pre.consequences.filter { it.kind == ConsequenceKind.WAGER }.map { it.id }
        wagersWon += open.count { id -> out.consequences.none { it.id == id } } - lostToday
    }

    fun finish(state: GameState) = DepthRunStats(
        encounters = encounters.name.lowercase(), relic = relic.label, visitorMornings = mornings,
        offers = offers, chosen = chosen, expired = expired, available = available, blocked = blocked, blockedOffers = blockedOffers,
        relicOffers = relicOffers, relicsChosen = relicsChosen, relicsReplaced = relicsReplaced, relicsDeclined = relicsDeclined, relicsHeld = state.relics.map { it.id }.sorted(),
        bellowsForges = bellowsForges, crucibleReturns = crucibleReturns, sealsEarned = sealsEarned, sealMaterials = sealMaterials, ledgerForges = ledgerForges, ledgerQuality = ledgerQuality,
        siegesWon = siegesWon, siegesLost = siegesLost, pledgesDelivered = pledgesDelivered, pledgeOutcomes = pledgeOutcomes, wagersWon = wagersWon, wagersLost = wagersLost,
        visitorGoldIn = visitorGoldIn, visitorGoldOut = visitorGoldOut, bountyGold = bountyGold, orderGold = orderGold, rejected = rejected,
    )

    companion object {
        const val PLAIN = "plain"
        /** Bot habits. */
        const val MAX_ANSWERS = 3
        const val MAX_TRIES = 3
        /** Days a fine blade has gone unsold before the crucible bot melts it for another try. */
        const val UNSOLD_DAYS = 3
    }
}

/**
 * What one run saw of the depth systems. Keys of [chosen], [available] and [blocked] are `encounter/option`. [chosen]
 * counts every answer sent, the free one and the merchant's inspection (which does not close the card) included.
 */
@Serializable
data class DepthRunStats(
    val encounters: String, val relic: String,
    val visitorMornings: Int,
    val offers: Map<String, Int>, val chosen: Map<String, Int>, val expired: Map<String, Int>,
    /** Offers on which an answer other than the free one was open, or closed, when the bot first read the card; offers with at least one closed. */
    val available: Map<String, Int>, val blocked: Map<String, Int>, val blockedOffers: Map<String, Int>,
    val relicOffers: Int, val relicsChosen: Map<String, Int>, val relicsReplaced: Int, val relicsDeclined: Int,
    /** Relics held when the run ended, sorted. */
    val relicsHeld: List<String>,
    /** Triggers: forges blown with the bellows, augments the crucible gave back, seals earned and the materials they brought, forges the ledger improved and by how much in all. */
    val bellowsForges: Int, val crucibleReturns: Int, val sealsEarned: Int, val sealMaterials: Int, val ledgerForges: Int, val ledgerQuality: Int,
    /** Sieges by trait ID, `plain` for one without. */
    val siegesWon: Map<String, Int>, val siegesLost: Map<String, Int>,
    val pledgesDelivered: Int, val pledgeOutcomes: Map<String, Int>,
    val wagersWon: Int, val wagersLost: Int,
    /** Gold that came in and went out as visitors were answered; council bounties paid for arming the watch; rewards of the orders visitors placed. */
    val visitorGoldIn: Int, val visitorGoldOut: Int, val bountyGold: Int, val orderGold: Int,
    /** Depth commands the engine rejected. Must stay 0. */
    val rejected: Int,
)
