package com.tinyblacksmith.core.sim

import com.tinyblacksmith.core.battle.Power
import com.tinyblacksmith.core.config.BalanceConfig
import com.tinyblacksmith.core.content.*
import com.tinyblacksmith.core.engine.*
import com.tinyblacksmith.core.model.*
import java.io.File
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Opt-in evidence writer; no gameplay policy or balance changes. Uses the existing driver for B/C and controls. */
class ClassicRescueMeasurementTest {
    private fun repeatSword(e: GameEngine, legacy: LegacyProfile, seed: Long, augment: MaterialId, horizon: Int, observe: (GameState) -> Unit): GameState {
        var s = e.newRun(legacy, seed)
        fun send(c: Command) { s = e.handle(s, c).stateOrThrow() }
        observe(s)
        while (!s.isEnded && s.day <= horizon) {
            if (s.pendingBlessingOffer.isNotEmpty()) send(Command.ChooseBlessing(s.pendingBlessingOffer.first()))
            if (s.pendingRelicOffer.isNotEmpty()) send(Command.DeclineRelicOffer)
            s.encounter?.takeIf { it.isOpen }?.let { send(Command.ResolveEncounter(it.id, Encounters.PASS, CommandId("repeat:visitor:${s.day}"))) }
            for (c in s.commissions.values.filter { it.status == CommissionStatus.OFFERED }) send(Command.AcceptCommission(c.id))
            while (s.energy >= e.config.quickForgeEnergy) {
                val missing = listOf(LaunchContent.IRON, augment).filter { (s.materials[it] ?: 0) < 1 }
                if (missing.sumOf { e.materialPrice(s, it) } > s.gold) break
                for (m in missing) send(Command.BuyMaterial(m))
                send(Command.Forge(ForgeMode.QUICK, LaunchContent.SWORD, LaunchContent.IRON, augment, null, Risk.BALANCED))
            }
            for (w in s.weapons.values.filter { it.isInStorage && it.promisedTo == null }) {
                if (s.listedWeapons().size >= e.shelfSlots(s)) break
                send(Command.ToggleShelf(w.id, true, e.suggestedPrice(w)))
            }
            send(Command.EndDay(CommandId("${s.runId.value}:day${s.day}")))
            observe(s)
        }
        return s
    }

    @Test fun repeatRecipeControlIsDeterministicAndUsesOnlyTheRequestedRecipe() {
        val e = GameEngine()
        val a = repeatSword(e, LegacyProfile(), 910001, LaunchContent.EMBER_RESIN, 15) {}
        val b = repeatSword(e, LegacyProfile(), 910001, LaunchContent.EMBER_RESIN, 15) {}
        assertEquals(a, b)
        val made = a.weapons.values.filter { it.forgedEra == a.era }
        assertTrue(made.isNotEmpty())
        assertTrue(made.all { it.familyId == LaunchContent.SWORD && it.coreId == LaunchContent.IRON && it.augmentId == LaunchContent.EMBER_RESIN })
    }

    @Test fun writePairedClassicEvidenceWhenRequested() {
        if (System.getenv("BLACKSMITH_RESCUE_MEASURE") != "1") return
        val n = System.getenv("BLACKSMITH_RESCUE_RUNS")?.toInt() ?: 96
        val horizon = 80
        val out = File("build/classic-rescue").apply { mkdirs() }
        val days = mutableListOf("variant,policy,seed,day,integrity,gold,materialSpend,materialUnits,forged,sold,recovered,damage,held,lost,heroDeaths")
        val sieges = mutableListOf("variant,policy,seed,siege,held,defense,raid,champions,militia,watch,damage,healthAfterRecovery,trait")
        val runs = mutableListOf("variant,policy,seed,days,ended,integrity,materialSpend,materialUnits,forged,sold,recovered,damage,heroDeaths")
        val snapshots = mutableListOf("policy,seed,siege,defense,raid,champions,militia,watch,noWeapons,noQualityPower,noElements,noFamilyFitOrWeight,noTraitDefense,noTraitRaid")
        val selections = mutableListOf("seed,day,world,classes,equippedFamilies,wantedFamilies,commissionFamilies,commissionElements,visitor,relicOffer,materialInventory")
        fun f(x: Double) = String.format(Locale.ROOT, "%.3f", x)
        for (variant in listOf("default", "maxed", "noRecovery", "noTraits", "nativeDepth")) {
            val cfg = if (variant == "noRecovery") BalanceConfig.DEFAULT.copy(maxIntegrityRecoveryPerDay = 0) else BalanceConfig.DEFAULT
            val content = if (variant == "noTraits") LaunchContent.catalog.copy(siegeTraits = emptyList()) else LaunchContent.catalog
            val e = GameEngine(content, cfg)
            val legacy = if (variant == "maxed") Simulator.maxedLegacy(e) else LegacyProfile()
            val policies = when (variant) { "default" -> listOf("A_FIRE", "A_FROST", "B_INVEST", "C_EXPERT", "SIEGE_PREP", "PASSIVE"); "nativeDepth" -> listOf("B_INVEST", "C_EXPERT"); else -> listOf("A_FIRE", "C_EXPERT") }
            for (name in policies) for (seed in 910001L until 910001L + n) {
                var spent = 0; var units = 0; var forged = 0; var sold = 0; var recovered = 0; var damage = 0
                val seen = mutableSetOf<Int>()
                val sampled = mutableSetOf<Int>()
                val selectionDays = mutableSetOf<Int>()
                fun observe(s: GameState) {
                    if (variant == "default" && name == "C_EXPERT" && s.day <= 10 && selectionDays.add(s.day)) {
                        fun count(ids: List<String>) = ids.groupingBy { it }.eachCount().toSortedMap().entries.joinToString("|") { "${it.key}:${it.value}" }
                        val open = s.commissions.values.filter { it.status == CommissionStatus.OFFERED || it.status == CommissionStatus.ACCEPTED }
                        selections += listOf(seed, s.day, s.world.name, count(s.aliveHeroes().map { it.classId.value }), count(s.weapons.values.filter { it.isEquipped }.map { it.familyId.value }), count(s.aliveHeroes().mapNotNull { it.want?.familyId?.value }), count(open.map { it.familyId.value }), count(open.map { it.element?.name ?: "any" }), s.encounter?.defId ?: "none", s.pendingRelicOffer.joinToString("|"), s.materials.entries.sortedBy { it.key.value }.joinToString("|") { "${it.key.value}:${it.value}" }).joinToString(",")
                    }
                    val r = s.lastResolution
                    if (r != null && seen.add(r.day)) {
                        for (event in r.events) when (event.type) {
                            EventType.MATERIAL_BOUGHT -> { spent += event.data["cost"]!!.toInt(); units += event.data["quantity"]?.toInt() ?: 1 }
                            EventType.WEAPON_FORGED -> forged++
                            EventType.WEAPON_SOLD, EventType.COMMISSION_COMPLETED -> sold++
                            EventType.TOWN_RECOVERED -> recovered += event.data["amount"]!!.toInt()
                            EventType.FORGE_DAMAGED -> damage += event.data["damage"]!!.toInt()
                            else -> Unit
                        }
                        days += listOf(variant, name, seed, r.day, s.town.integrity, s.gold, spent, units, forged, sold, recovered, damage, s.town.siegesSurvived, s.town.siegesLost, s.heroes.values.count { it.fate == HeroFate.DEAD }).joinToString(",")
                        val siege = r.events.firstOrNull { it.type == EventType.SIEGE_WON || it.type == EventType.SIEGE_LOST }
                        if (siege != null && r.day <= 20) {
                            val rounds = r.replays.single { it.kind == ReplayKind.SIEGE }.rounds
                            sieges += listOf(variant, name, seed, r.day / 5, siege.type == EventType.SIEGE_WON, siege.data["townDefense"], siege.data["raidPower"], rounds.filter { it.attackerId != null }.sumOf { it.damage }, rounds.filter { it.attacker == "Town militia" }.sumOf { it.damage }, rounds.filter { it.attacker == "Town watch" }.sumOf { it.damage }, r.events.filter { it.type == EventType.FORGE_DAMAGED }.sumOf { it.data["damage"]!!.toInt() }, s.town.integrity, siege.data["trait"] ?: "plain").joinToString(",")
                        }
                    }
                    // Forecast snapshot at the start of siege morning, before that day's shops/activities. Fixed selected heroes.
                    if (variant == "default" && !s.isEnded && s.day in listOf(5, 10, 15, 20) && sampled.add(s.day)) {
                        val o = e.siegeForecast(s)!!
                        val blessing = s.blessings.sumOf { if (e.content.blessing(it.id).effect == BlessingEffect.HERO_POWER) e.content.blessing(it.id).magnitude else 0 }
                        val effective = o.trait?.conditionFloorFactor?.let { cfg.copy(conditionFloorFactor = it) } ?: cfg
                        fun sum(mode: String): Double = o.champions.sumOf { (h, w) ->
                            val blade = when (mode) { "noWeapons" -> null; "noQuality" -> w?.copy(power = w.power - w.quality / cfg.powerPerQualityDivisor); "noElements" -> w?.copy(element = null); else -> w }
                            val cat = if (mode == "noFamily") content.copy(families = content.families.map { it.copy(classFit = emptyMap(), defensiveWeight = 1.0) }) else content
                            val conf = if (mode == "noFamily") effective.copy(offFamilyFit = 1.0) else if (mode == "noTrait") cfg else effective
                            Power.defensePower(h, blade, o.faction, cat, conf, blessing, o.warlord)
                        } + (if (mode == "noTrait") s.town.militia + s.town.armory.toDouble() else o.militia + o.armory)
                        snapshots += listOf(name, seed, s.day / 5, f(o.townDefense), f(o.raidPower), f(o.championPowers.sum()), f(o.militia), f(o.armory), f(sum("noWeapons")), f(sum("noQuality")), f(sum("noElements")), f(sum("noFamily")), f(sum("noTrait")), f(o.raidPower / (o.trait?.raidMultiplier ?: 1.0))).joinToString(",")
                    }
                }
                observe(e.newRun(legacy, seed))
                val final = if (name.startsWith("A_")) repeatSword(e, legacy, seed, if (name == "A_FIRE") LaunchContent.EMBER_RESIN else LaunchContent.FROST_BLOOM, horizon, ::observe)
                    else SimulationDriver(e, maxDays = horizon, eventRetentionDays = 30, blessing = BlessingPref.FIRST, encounters = if (variant == "nativeDepth") null else EncounterPref.DECLINE, relic = if (variant == "nativeDepth") null else RelicPref.None, onDayResolved = { s, _ -> observe(s) }).playRun(legacy, seed, when (name) { "B_INVEST" -> Policy.BALANCED_INVEST; "C_EXPERT" -> Policy.EXPERT; "SIEGE_PREP" -> Policy.SIEGE_PREP; else -> Policy.PASSIVE }).second
                runs += listOf(variant, name, seed, if (final.isEnded) final.day else final.day - 1, final.isEnded, final.town.integrity, spent, units, forged, sold, recovered, damage, final.heroes.values.count { it.fate == HeroFate.DEAD }).joinToString(",")
            }
            println("Classic rescue measured $variant ($n paired seeds per policy)")
        }
        File(out, "days.csv").writeText(days.joinToString("\n") + "\n")
        File(out, "sieges.csv").writeText(sieges.joinToString("\n") + "\n")
        File(out, "runs.csv").writeText(runs.joinToString("\n") + "\n")
        File(out, "snapshots.csv").writeText(snapshots.joinToString("\n") + "\n")
        File(out, "selections.csv").writeText(selections.joinToString("\n") + "\n")
        assertEquals(14 * n, runs.size - 1)
    }
}
