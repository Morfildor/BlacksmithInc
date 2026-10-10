package com.tinyblacksmith.core.sim

import com.tinyblacksmith.core.content.Depth
import com.tinyblacksmith.core.content.MaterialCategory
import com.tinyblacksmith.core.engine.Command
import com.tinyblacksmith.core.engine.CommandOutcome
import com.tinyblacksmith.core.engine.Encounters
import com.tinyblacksmith.core.engine.GameEngine
import com.tinyblacksmith.core.model.*
import kotlinx.serialization.Serializable

/** Runs that ended holding one set of relics. */
@Serializable
data class RelicCombo(val relics: String, val runs: Int, val daysMean: Double, val daysMedian: Int)

/** Sieges of one trait (`plain` = none): fought, won, and the share won. */
@Serializable
data class TraitRow(val trait: String, val fought: Int, val won: Int, val winRate: Double)

/** The depth systems for one policy row (`--depth`, and always in `--json`). Shares are 0-1; "per run" divides by all runs. */
@Serializable
data class DepthSummary(
    val encounters: String, val relic: String, val runs: Int,
    val visitorMorningsPerRun: Double,
    /** Mornings with a visitor as a share of the days played. */
    val visitorMorningShare: Double,
    val offersPerRun: Map<String, Double>,
    /** Per encounter: each answer's share of its offers (`pass` is the free answer sent, `expired` an unanswered one; the merchant's `inspect` does not close the card, so his row can exceed 1). */
    val optionShare: Map<String, Map<String, Double>>,
    /** Share of an encounter's offers on which at least one paid answer was closed, and the same per `encounter/option`. */
    val blockedOfferShare: Map<String, Double>, val blockedOptionShare: Map<String, Double>,
    /** Dead content over these runs: encounters never offered, and `encounter/option` answers never chosen or never open (of encounters that were offered). */
    val neverOffered: List<String>, val neverChosen: List<String>, val neverAvailable: List<String>,
    val relicOffersPerRun: Double, val relicsChosenPerRun: Map<String, Double>, val relicsReplacedPerRun: Double, val relicOffersDeclinedPerRun: Double,
    val combinations: List<RelicCombo>,
    val bellowsForgesPerRun: Double, val crucibleReturnsPerRun: Double, val sealsEarnedPerRun: Double, val sealMaterialsPerRun: Double,
    val ledgerForgesPerRun: Double, val ledgerQualityPerRun: Double,
    val sieges: List<TraitRow>,
    /** The wall pledge per run: pledged, delivered, each way it ended without a visit (lapsed, dead, retired, parted), returned (the hero came back), and the answers given then. */
    val pledge: Map<String, Double>,
    val wagersTakenPerRun: Double, val wagersWonPerRun: Double, val wagersLostPerRun: Double,
    val visitorGoldInPerRun: Double, val visitorGoldOutPerRun: Double, val bountyGoldPerRun: Double, val bountyGoldMax: Int, val orderGoldPerRun: Double,
    val rejected: Int,
) {
    fun render(): String {
        fun pct(v: Double) = "%.0f%%".format(100.0 * v)
        fun f2(v: Double) = "%.2f".format(v)
        return buildString {
            appendLine("  depth (visitors=$encounters relic=$relic): mornings with a visitor/run=${f2(visitorMorningsPerRun)} (${pct(visitorMorningShare)} of days)  rejected commands=$rejected")
            for ((enc, n) in offersPerRun) appendLine(
                "    %-21s offers/run=%5.2f  ".format(enc, n) + (optionShare[enc] ?: emptyMap()).entries.joinToString(" ") { "${it.key}=${pct(it.value)}" } +
                    "  blocked: any=${pct(blockedOfferShare[enc] ?: 0.0)}" + blockedOptionShare.filterKeys { it.startsWith("$enc/") }.entries.joinToString("") { " ${it.key.substringAfter('/')}=${pct(it.value)}" },
            )
            appendLine("    dead content: never offered=${neverOffered.ifEmpty { "none" }}  never chosen=${neverChosen.ifEmpty { "none" }}  never open=${neverAvailable.ifEmpty { "none" }}")
            appendLine("    relics: offers/run=${f2(relicOffersPerRun)} declined/run=${f2(relicOffersDeclinedPerRun)} replaced/run=${f2(relicsReplacedPerRun)}  chosen/run: " + relicsChosenPerRun.entries.joinToString(" ") { "${it.key}=${f2(it.value)}" })
            appendLine("    relic triggers/run: bellows forges=${f2(bellowsForgesPerRun)}  crucible returns=${f2(crucibleReturnsPerRun)}  seals=${f2(sealsEarnedPerRun)} (materials ${f2(sealMaterialsPerRun)})  ledger forges=${f2(ledgerForgesPerRun)} (+${f2(ledgerQualityPerRun)} quality)")
            appendLine("    relics held at run end (runs, days mean / median): " + combinations.joinToString("; ") { "${it.relics} n=${it.runs} ${"%.1f".format(it.daysMean)}/${it.daysMedian}" })
            appendLine("    sieges by trait (fought, won): " + sieges.joinToString("  ") { "${it.trait} n=${it.fought} ${pct(it.winRate)}" })
            appendLine("    wall pledge/run: " + pledge.entries.joinToString(" ") { "${it.key}=${f2(it.value)}" } + "   wagers/run: taken=${f2(wagersTakenPerRun)} won=${f2(wagersWonPerRun)} lost=${f2(wagersLostPerRun)}")
            appendLine("    gold through visitors/run: in=${f2(visitorGoldInPerRun)} out=${f2(visitorGoldOutPerRun)}  watch bounties=${f2(bountyGoldPerRun)} (max in a run $bountyGoldMax)  rewards of visitors' orders=${f2(orderGoldPerRun)}")
        }
    }

    companion object {
        /** Every encounter of the launch catalog and its paid answers: the universe the dead-content lists are read against (an answer the card never showed cannot be learned from the cards). */
        val OPTIONS: Map<String, List<String>> = linkedMapOf(
            Depth.LAST_CRATE to listOf("crate", "metal"), Depth.BLADE_FOR_THE_WALL to listOf("pledge", "patron"), Depth.MASTERS_AFTERNOON to listOf("study", "lesson"),
            Depth.COLLECTORS_OFFER to listOf("sell"), Depth.CRACKED_FAMILY_BLADE to listOf("restore", "collector"), Depth.CROOKED_MERCHANT to listOf("buy", "inspect"),
            Depth.SMITHS_WAGER to listOf("wager", "order"), Depth.FESTIVAL_CONTRACT to listOf("stall", "watch"), Depth.DEBT_REPAID to listOf("collect", "forgive", "speak"),
        )

        /** Null when the runs carry no depth numbers (a catalog without the depth content). */
        fun of(runs: List<RunStats>): DepthSummary? {
            val rs = runs.mapNotNull { it.depth }
            if (rs.isEmpty()) return null
            val n = rs.size.toDouble()
            fun total(f: (DepthRunStats) -> Map<String, Int>): Map<String, Int> = sortedMapOf<String, Int>().also { m -> rs.forEach { r -> f(r).forEach { (k, v) -> m.merge(k, v, Int::plus) } } }
            fun perRun(f: (DepthRunStats) -> Int) = rs.sumOf(f) / n
            val offers = total { it.offers }
            val chosen = total { it.chosen }
            val expired = total { it.expired }
            val available = total { it.available }
            val blocked = total { it.blocked }
            val blockedOffers = total { it.blockedOffers }
            fun share(count: Int, enc: String) = count.toDouble() / (offers[enc] ?: 1)
            val all = OPTIONS.flatMap { (enc, opts) -> opts.map { "$enc/$it" } }
            val won = total { it.siegesWon }
            val lost = total { it.siegesLost }
            val pledge = linkedMapOf(
                "pledged" to (chosen["${Depth.BLADE_FOR_THE_WALL}/pledge"] ?: 0) / n, "delivered" to perRun { it.pledgesDelivered },
                "lapsed" to perRun { it.pledgeOutcomes["lapsed"] ?: 0 }, "dead" to perRun { it.pledgeOutcomes["dead"] ?: 0 },
                "retired" to perRun { it.pledgeOutcomes["retired"] ?: 0 }, "parted" to perRun { it.pledgeOutcomes["parted"] ?: 0 },
                "returned" to (offers[Depth.DEBT_REPAID] ?: 0) / n,
            )
            for (answer in OPTIONS.getValue(Depth.DEBT_REPAID) + Encounters.PASS) pledge[answer] = (chosen["${Depth.DEBT_REPAID}/$answer"] ?: 0) / n
            return DepthSummary(
                encounters = rs.map { it.encounters }.distinct().joinToString("/"), relic = rs.map { it.relic }.distinct().joinToString("/"), runs = rs.size,
                visitorMorningsPerRun = perRun { it.visitorMornings },
                visitorMorningShare = rs.sumOf { it.visitorMornings }.toDouble() / runs.sumOf { it.daysSurvived }.coerceAtLeast(1),
                offersPerRun = offers.mapValues { it.value / n },
                optionShare = offers.keys.associateWith { enc ->
                    chosen.filterKeys { it.startsWith("$enc/") }.entries.associate { it.key.substringAfter('/') to share(it.value, enc) } +
                        (expired[enc]?.let { mapOf("expired" to share(it, enc)) } ?: emptyMap())
                },
                blockedOfferShare = offers.keys.associateWith { share(blockedOffers[it] ?: 0, it) },
                blockedOptionShare = blocked.mapValues { share(it.value, it.key.substringBefore('/')) },
                neverOffered = OPTIONS.keys.filter { it !in offers },
                neverChosen = all.filter { it.substringBefore('/') in offers && it !in chosen },
                neverAvailable = all.filter { it.substringBefore('/') in offers && it !in available },
                relicOffersPerRun = perRun { it.relicOffers }, relicsChosenPerRun = total { it.relicsChosen }.mapValues { it.value / n },
                relicsReplacedPerRun = perRun { it.relicsReplaced }, relicOffersDeclinedPerRun = perRun { it.relicsDeclined },
                combinations = runs.filter { it.depth != null }.groupBy { it.depth!!.relicsHeld.joinToString("+").ifEmpty { "none" } }.toSortedMap().map { (k, g) ->
                    RelicCombo(k, g.size, g.map { it.daysSurvived }.average(), percentile(g.map { it.daysSurvived }, 0.5))
                },
                bellowsForgesPerRun = perRun { it.bellowsForges }, crucibleReturnsPerRun = perRun { it.crucibleReturns }, sealsEarnedPerRun = perRun { it.sealsEarned },
                sealMaterialsPerRun = perRun { it.sealMaterials }, ledgerForgesPerRun = perRun { it.ledgerForges }, ledgerQualityPerRun = perRun { it.ledgerQuality },
                sieges = (won.keys + lost.keys).toSortedSet().map { t -> ((won[t] ?: 0) + (lost[t] ?: 0)).let { fought -> TraitRow(t, fought, won[t] ?: 0, (won[t] ?: 0).toDouble() / fought) } },
                pledge = pledge,
                wagersTakenPerRun = (chosen["${Depth.SMITHS_WAGER}/wager"] ?: 0) / n, wagersWonPerRun = perRun { it.wagersWon }, wagersLostPerRun = perRun { it.wagersLost },
                visitorGoldInPerRun = perRun { it.visitorGoldIn }, visitorGoldOutPerRun = perRun { it.visitorGoldOut },
                bountyGoldPerRun = perRun { it.bountyGold }, bountyGoldMax = rs.maxOf { it.bountyGold }, orderGoldPerRun = perRun { it.orderGold },
                rejected = rs.sumOf { it.rejected },
            )
        }
    }
}

/** One exploit probe: what was checked, whether it held, and the numbers it saw. */
data class ProbeResult(val name: String, val ok: Boolean, val detail: String) {
    fun render(): String = "  [${if (ok) "ok" else "FAIL"}] $name: $detail"
}

/**
 * Exploit probes for the depth systems (`--probe`): each plays real commands and checks a bound a loop would have to
 * break. A probe starts from a run with the relic or the visitor it needs put in place, the one step that is not a command.
 */
object DepthProbes {
    fun run(engine: GameEngine, seeds: Int, baseSeed: Long = 1): List<ProbeResult> {
        val range = baseSeed until baseSeed + seeds
        return listOf(
            crucible(engine, range, LegacyProfile(), "new account", thrifty = false),
            crucible(engine, range, Simulator.maxedLegacy(engine), "maxed account", thrifty = true),
            bounty(engine, range), merchant(engine, range, crucible = false), merchant(engine, range, crucible = true),
            sealZeroCash(engine, range), sealCommissions(engine, range), sealCollector(engine, range), sealEarned(engine, range),
        )
    }

    /** Salvages the crucible probe tries a day, so the once-a-day charge is tested and not merely respected. */
    private const val SALVAGES_A_DAY = 3

    private fun wealth(engine: GameEngine, s: GameState): Int = s.gold + s.materials.entries.sumOf { it.value * engine.materialPrice(s, it.key) }

    private fun GameState.with(relicId: String) = copy(pendingRelicOffer = emptyList(), relics = listOf(ActiveRelic(relicId)))

    private fun accepted(engine: GameEngine, s: GameState, cmd: Command): GameState? = (engine.handle(s, cmd) as? CommandOutcome.Accepted)?.state

    private fun endDay(engine: GameEngine, s: GameState): GameState = accepted(engine, s, Command.EndDay(CommandId("${s.runId.value}:probe${s.day}"))) ?: error("End Day rejected")

    /** Buys what is missing and forges the best fine recipe of the first family while [keep] energy stays; returns the state and the forges made. */
    private fun forgeAll(engine: GameEngine, state: GameState, keep: Int, family: WeaponFamilyId = engine.content.families.first().id, minQuality: Int = engine.config.rareMin): GameState {
        var s = state
        while (s.energy >= engine.config.quickForgeEnergy + keep) {
            val (core, augment) = Recipes.pick(engine, s, engine.content.family(family), minQuality) ?: break
            for (m in listOf(core, augment)) if ((s.materials[m.id] ?: 0) < 1) s = accepted(engine, s, Command.BuyMaterial(m.id)) ?: return s
            s = accepted(engine, s, Command.Forge(ForgeMode.QUICK, family, core.id, augment.id, null, Risk.BALANCED)) ?: break
        }
        return s
    }

    /**
     * (a) Forge and crucible-salvage every day, selling nothing. In planning no sale happens, so gold plus materials at
     * the supplier's price must never rise between the morning and End Day, and the crucible returns one augment a day at most.
     * With Thrifty Hands ([thrifty]) a forge may spare its augment and the crucible then return it as well: the day's gain
     * is allowed up to the dearest augment, once, and the number is reported. Thrifty Hands alone already gains in this loop
     * (a spared core comes back from the melt), so that account is measured against the same days played without the relic.
     */
    fun crucible(engine: GameEngine, seeds: LongRange, legacy: LegacyProfile, label: String, thrifty: Boolean, days: Int = 30): ProbeResult {
        val without = if (thrifty) loopGain(engine, seeds, legacy, days, relic = false, allow = false).first else 0
        var worstGain = Int.MIN_VALUE; var worstAt = ""; var returns = 0; var mostADay = 0; var daysPlayed = 0; var coarse = 0
        for (seed in seeds) {
            var s = engine.newRun(legacy, seed).with(Depth.SALVAGERS_CRUCIBLE)
            while (!s.isEnded && s.day <= days) {
                val before = wealth(engine, s)
                val allowed = if (thrifty) engine.content.materials(MaterialCategory.AUGMENT).maxOf { engine.materialPrice(s, it.id) } else 0
                s = forgeAll(engine, s, keep = SALVAGES_A_DAY * engine.config.salvageEnergy)
                var today = 0
                for (w in s.storedWeapons().sortedByDescending { it.quality }) {
                    if (s.energy < engine.config.salvageEnergy) break
                    val keeps = engine.salvageKeepsAugment(s, w)
                    val augmentBefore = s.materials[w.augmentId] ?: 0
                    s = accepted(engine, s, Command.Salvage(w.id)) ?: break
                    if ((s.materials[w.augmentId] ?: 0) > augmentBefore) { today++; if (!keeps || w.quality < engine.config.rareMin) coarse++ }
                }
                returns += today; mostADay = maxOf(mostADay, today); daysPlayed++
                val gain = wealth(engine, s) - before - allowed
                if (gain > worstGain) { worstGain = gain; worstAt = "seed $seed day ${s.day}" }
                s = endDay(engine, s)
            }
        }
        return ProbeResult("crucible forge-and-salvage loop ($label)", worstGain <= maxOf(0, without) && mostADay <= 1 && coarse == 0,
            "largest planning-phase gain in gold + materials" + (if (thrifty) " beyond one spared augment" else "") + " = $worstGain ($worstAt)" +
                (if (thrifty) ", against $without for the same loop without the relic" else "") + "; augments returned=$returns over $daysPlayed days, most in a day=$mostADay, from a blade below fine=$coarse")
    }

    /** The largest one-day planning gain of the forge-and-salvage loop without any relic: what the legacy account does by itself. */
    private fun loopGain(engine: GameEngine, seeds: LongRange, legacy: LegacyProfile, days: Int, relic: Boolean, allow: Boolean): Pair<Int, String> {
        var worst = Int.MIN_VALUE; var at = ""
        for (seed in seeds) {
            var s = engine.newRun(legacy, seed).let { if (relic) it.with(Depth.SALVAGERS_CRUCIBLE) else it.copy(pendingRelicOffer = emptyList()) }
            while (!s.isEnded && s.day <= days) {
                val before = wealth(engine, s)
                val allowed = if (allow) engine.content.materials(MaterialCategory.AUGMENT).maxOf { engine.materialPrice(s, it.id) } else 0
                s = forgeAll(engine, s, keep = SALVAGES_A_DAY * engine.config.salvageEnergy)
                for (w in s.storedWeapons().sortedByDescending { it.quality }) {
                    if (s.energy < engine.config.salvageEnergy) break
                    s = accepted(engine, s, Command.Salvage(w.id)) ?: break
                }
                val gain = wealth(engine, s) - before - allowed
                if (gain > worst) { worst = gain; at = "seed $seed day ${s.day}" }
                s = endDay(engine, s)
            }
        }
        return worst to at
    }

    /** (b) Gold from the council's bounty in a run is at most contracts a run x blades a contract x gold a blade. */
    fun bounty(engine: GameEngine, seeds: LongRange): ProbeResult {
        val cfg = engine.config.depth
        val bound = (engine.content.encounter(Depth.FESTIVAL_CONTRACT)?.maxPerRun ?: 0) * cfg.watchBountyBlades * cfg.watchBounty
        val driver = SimulationDriver(engine, encounters = EncounterPref.DEFENSE)
        val golds = listOf(Policy.BALANCED_ACTIVE, Policy.SIEGE_PREP).flatMap { p -> seeds.map { driver.playRun(LegacyProfile(), it, p).first.depth?.bountyGold ?: 0 } }
        return ProbeResult("watch bounty bound", golds.all { it <= bound } && golds.any { it > 0 },
            "bound=$bound gold a run; most paid in a run=${golds.maxOrNull() ?: 0}, mean=${"%.1f".format(golds.average())}, runs paid=${golds.count { it > 0 }} of ${golds.size}")
    }

    /** (c) The crooked merchant's blade, bought and then scrapped or salvaged (with or without the crucible), gives back less than its price. */
    fun merchant(engine: GameEngine, seeds: LongRange, crucible: Boolean): ProbeResult {
        var worst = Int.MIN_VALUE; var worstAt = ""; var bought = 0; var scrapUnits = 0; var over = 0
        for (seed in seeds) {
            val start = engine.newRun(LegacyProfile(), seed).let { if (crucible) it.with(Depth.SALVAGERS_CRUCIBLE) else it.copy(pendingRelicOffer = emptyList()) }.copy(gold = 100_000)
            val offered = Encounters.force(start, engine.content, engine.config, Depth.CROOKED_MERCHANT) ?: continue
            val inst = offered.encounter ?: continue
            val price = inst.amounts["price"] ?: 0
            val s = accepted(engine, offered, Command.ResolveEncounter(inst.id, "buy", CommandId("probe:buy:$seed"))) ?: continue
            val blade = s.weapons.values.single { it.id !in offered.weapons }
            bought++
            val salvaged = accepted(engine, s, Command.Salvage(blade.id)) ?: continue
            val back = wealth(engine, salvaged) - wealth(engine, s)
            if (back > price) over++
            if (back - price > worst) { worst = back - price; worstAt = "seed $seed: ${blade.name} (quality ${blade.quality}) bought for $price, salvage returned $back" }
            scrapUnits += accepted(engine, s, Command.Scrap(listOf(blade.id)))?.let { it.materials.values.sum() - s.materials.values.sum() } ?: 0
        }
        return ProbeResult("merchant blade salvaged or scrapped" + (if (crucible) " (with the crucible)" else ""), bought > 0 && worst <= 0 && scrapUnits == 0,
            "blades bought=$bought, worth more melted than paid=$over; best salvage return minus price=$worst ($worstAt); material units from scrapping one blade=$scrapUnits")
    }

    private fun sealProgress(s: GameState) = s.relics.firstOrNull { it.id == Depth.COLLECTORS_SEAL }?.progress ?: 0

    /** (d1) A blade given away for nothing earns no seal. */
    fun sealZeroCash(engine: GameEngine, seeds: LongRange, days: Int = 40): ProbeResult {
        val driver = SimulationDriver(engine, maxDays = days, relic = RelicPref.Fixed(Depth.COLLECTORS_SEAL))
        val runs = seeds.map { driver.playRun(LegacyProfile(), it, Policy.FREE_LISTINGS).first }
        val held = runs.count { Depth.COLLECTORS_SEAL in (it.depth?.relicsHeld ?: emptyList()) }
        val seals = runs.sumOf { it.depth?.sealsEarned ?: 0 }
        return ProbeResult("collector's seal: zero-cash sales", seals == 0 && held > 0 && runs.sumOf { it.weaponsSold } > 0, "runs holding the seal=$held, blades given away=${runs.sumOf { it.weaponsSold }}, seals earned=$seals")
    }

    /** (d2) A shop that only fills commissions (nothing on the shelf) earns no seal. */
    fun sealCommissions(engine: GameEngine, seeds: LongRange, days: Int = 30): ProbeResult {
        var completed = 0; var seals = 0; var rich = 0
        for (seed in seeds) {
            var s = engine.newRun(LegacyProfile(), seed).with(Depth.COLLECTORS_SEAL)
            while (!s.isEnded && s.day <= days) {
                for (c in s.commissions.values.filter { it.status == CommissionStatus.OFFERED }) s = accepted(engine, s, Command.AcceptCommission(c.id)) ?: s
                for (c in s.commissions.values.filter { it.status == CommissionStatus.ACCEPTED && it.kind != CommissionKind.HEIRLOOM }) {
                    if (s.energy < engine.config.quickForgeEnergy) break
                    val (core, augment) = Recipes.pick(engine, s, engine.content.family(c.familyId), c.minQuality, c.element) ?: continue
                    for (m in listOf(core, augment)) if ((s.materials[m.id] ?: 0) < 1) s = accepted(engine, s, Command.BuyMaterial(m.id)) ?: s
                    s = accepted(engine, s, Command.Forge(ForgeMode.QUICK, c.familyId, core.id, augment.id, null, Risk.BALANCED)) ?: s
                }
                s = endDay(engine, s)
                val res = s.lastResolution ?: continue
                completed += res.events.count { it.type == EventType.COMMISSION_COMPLETED }
                rich += res.events.count { it.type == EventType.COMMISSION_COMPLETED && (it.data["reward"]?.toIntOrNull() ?: 0) >= engine.config.depth.sealMinCash }
                seals += res.events.count { it.type == EventType.RELIC_TRIGGERED }
            }
            seals += sealProgress(s)
        }
        return ProbeResult("collector's seal: commissions", seals == 0 && rich > 0, "commissions collected=$completed (paying ${engine.config.depth.sealMinCash}+ gold: $rich), seals earned=$seals")
    }

    /** (d3) Selling a storied blade to the collector visitor earns no seal, whatever he pays. */
    fun sealCollector(engine: GameEngine, seeds: LongRange): ProbeResult {
        var sold = 0; var seals = 0; var rich = 0
        for (seed in seeds) {
            val forged = forgeAll(engine, engine.newRun(LegacyProfile(), seed).with(Depth.COLLECTORS_SEAL), keep = 0)
            val prized = forged.storedWeapons().maxByOrNull { it.power } ?: continue
            // Scenario setup: the blade is made famous so the collector asks for it.
            val famous = forged.copy(weapons = forged.weapons + (prized.id to prized.copy(fame = 5)))
            val offered = Encounters.force(famous, engine.content, engine.config, Depth.COLLECTORS_OFFER) ?: continue
            val inst = offered.encounter ?: continue
            val out = engine.handle(offered, Command.ResolveEncounter(inst.id, "sell", CommandId("probe:sell:$seed"))) as? CommandOutcome.Accepted ?: continue
            sold++
            if (out.state.gold - offered.gold >= engine.config.depth.sealMinCash) rich++
            seals += sealProgress(out.state) + out.events.count { it.type == EventType.RELIC_TRIGGERED }
        }
        return ProbeResult("collector's seal: the collector visitor", seals == 0 && rich > 0, "blades sold to the collector=$sold (for ${engine.config.depth.sealMinCash}+ gold: $rich), seals earned=$seals")
    }

    /**
     * (d4) The control, and the rule read from the other side: with dear shelves the seal is earned, never more than once
     * a day, and only on a day a browser paid at least `sealMinCash` in coin for a blade off the shelf.
     */
    fun sealEarned(engine: GameEngine, seeds: LongRange, days: Int = 40): ProbeResult {
        var seals = 0; var doubles = 0; var unexplained = 0; var where = ""
        for (seed in seeds) {
            val driver = SimulationDriver(engine, maxDays = days, relic = RelicPref.Fixed(Depth.COLLECTORS_SEAL), onDayResolved = { s, _ ->
                val res = s.lastResolution
                val today = res?.events?.count { it.type == EventType.RELIC_TRIGGERED } ?: 0
                seals += today
                if (today > 1) doubles++
                if (today > 0 && res!!.browsers.none { v -> v.sale?.let { it.commissionId == null && it.cashPaid >= engine.config.depth.sealMinCash && (it.listedPrice ?: 0) >= it.cashPaid } == true }) { unexplained++; where = "seed $seed day ${res.day}" }
            })
            for (policy in listOf(Policy.BALANCED_FAIR, Policy.BALANCED_EXPENSIVE)) driver.playRun(LegacyProfile(), seed, policy)
        }
        return ProbeResult("collector's seal: earned only by a paid shelf sale", seals > 0 && doubles == 0 && unexplained == 0,
            "seals earned=$seals, days with more than one=$doubles, days with a seal and no qualifying sale=$unexplained $where")
    }
}
