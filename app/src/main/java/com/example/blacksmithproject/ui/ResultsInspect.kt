package com.example.blacksmithproject.ui

import androidx.compose.runtime.Immutable
import com.example.blacksmithproject.R
import com.example.blacksmithproject.ui.detail.ItemDetail
import com.example.blacksmithproject.ui.detail.Stat as WeaponStat
import com.example.blacksmithproject.ui.theme.Ember
import com.example.blacksmithproject.ui.theme.Gold
import com.tinyblacksmith.core.battle.Battle
import com.tinyblacksmith.core.config.BalanceConfig
import com.tinyblacksmith.core.content.CombatContent
import com.tinyblacksmith.core.content.Depth
import com.tinyblacksmith.core.content.SiegeTraitDef
import com.tinyblacksmith.core.content.UpgradeDef
import com.tinyblacksmith.core.legacy.RunEndResult
import kotlin.math.roundToInt

/*
 * What the result and record surfaces say in their inspect cards (V3): the forge result, the practice fight, the siege
 * trait and the besieger on Town, the legacy upgrades and the run's reward. Pure: every line reads a field the screen
 * already holds (the weapon's ItemDetail, the bench's report, the trait's own numbers, the run's result), never a draw.
 */

/** The forge result's lead: its numbers worth a glance, each property by name with its sign, and (a guild run) its fight effects by name. */
@Immutable data class ResultLeadUi(val stats: List<WeaponStat>, val properties: List<Fact>, val rules: List<String> = emptyList())

/**
 * The lead of a weapon just made, from the result's own [ItemDetail]: power, quality, a condition only once it has
 * moved from full and renown only once earned ([forResult] keeps the rest out), then every buff ("+") and flaw ("−")
 * by name, and in a guild run the name of every effect it fights by. What each does is on the card ([resultTip]).
 * An effect named exactly as a property ("Flaming") is said once, as the property; the weapon's card keeps both.
 */
internal fun resultLead(d: ItemDetail): ResultLeadUi {
    val fresh = d.forResult()
    val properties = d.affixes.map { Fact(it.name, tone = FactTone.GOOD) } + d.flaws.map { Fact(it.name, tone = FactTone.WARN) }
    val named = properties.map { it.value }.toSet()
    return ResultLeadUi(
        // A full condition that never moved is no news on a new weapon: it waits on the card.
        stats = fresh.stats.filterNot { it.label == "Condition" && it.max == null },
        properties = properties,
        // A metal's or an element's effect is named after its source, so its name comes with what it does.
        rules = d.rules?.let { r -> (r.road + r.homeOnly).map { CombatContent.label(it.id, it.name) } }.orEmpty().filterNot { it in named },
    )
}

/**
 * The forge result as a card: the weapon's own card ([weaponTip], the same one Storage and the Forge open), then what
 * it was made of and, when [rarityRule], what its rarity word buys. [actions] is "Full details", the weapon's sheet.
 */
fun resultTip(d: ItemDetail, config: BalanceConfig, recipe: String, rarityRule: Boolean, actions: List<TipAction>, tag: String = "reveal_tip"): TipUi =
    weaponTip(d.forResult(), config, actions, tag).let { tip ->
        tip.copy(
            sections = tip.sections + listOf(
                TipSection("Made of", listOf(TipLine(recipe.removeSuffix("."), TipTone.PLAIN))),
                TipSection("Rarity", if (rarityRule) RARITY_RULE.split(Regex("(?<=\\.)\\s+")).map { TipLine(it, TipTone.PLAIN) } else emptyList()),
            ),
        )
    }

/** What the notebook took from this forge in one line: the kind of each gain by name, else the note that nothing moved, else the attempt. */
internal fun learningLine(l: ForgeLearningUi): String? = when {
    l.changes.isNotEmpty() -> l.changes.map { it.title }.distinct().joinToString(" · ")
    l.note != null -> l.note
    else -> l.attempt
}

/** Everything the notebook took from this forge, each gain with the journal's own words, then the attempt at a signature. */
fun learningTip(l: ForgeLearningUi, tag: String = "reveal_learning_tip"): TipUi = TipUi(
    name = "Notebook", kind = "What this forge taught", icon = R.drawable.icon_nav_journal,
    sections = listOf(
        TipSection(null, l.changes.map { TipLine(it.line, TipTone.BONUS, title = it.title) } + listOfNotNull(l.note?.let { TipLine(it, TipTone.PLAIN) })),
        TipSection("Signature", listOfNotNull(l.attempt?.let { TipLine(it, TipTone.PLAIN) })),
    ),
    tag = tag,
)

private fun pct(x: Double) = (x * 100).roundToInt()

/**
 * A siege trait's effects as signed lines, from its numbers (`Battle.outlook` and the classic siege read exactly these),
 * never from its wording: the raid, the militia and the watch, what a worn weapon still counts for, and the wear the
 * defenders' weapons take. A number left at its usual value says nothing.
 */
internal fun siegeTraitLines(t: SiegeTraitDef, config: BalanceConfig): List<TipLine> = buildList {
    when {
        t.raidMultiplier > 1.0 -> add(TipLine("The attackers are ${pct(t.raidMultiplier - 1.0)}% stronger", TipTone.DRAWBACK))
        t.raidMultiplier < 1.0 -> add(TipLine("The attackers are ${pct(1.0 - t.raidMultiplier)}% weaker", TipTone.BONUS))
    }
    when {
        t.watchMultiplier <= 0.0 -> add(TipLine("The militia and the armory do not fight", TipTone.DRAWBACK))
        t.watchMultiplier > 1.0 -> add(TipLine("The militia and the armory count ${pct(t.watchMultiplier - 1.0)}% more", TipTone.BONUS))
        t.watchMultiplier < 1.0 -> add(TipLine("The militia and the armory count ${pct(1.0 - t.watchMultiplier)}% less", TipTone.DRAWBACK))
    }
    t.conditionFloorFactor?.takeIf { it != config.conditionFloorFactor }?.let { f ->
        add(TipLine("A weapon at condition 0 counts for ${pct(f)}% of its power, usually ${pct(config.conditionFloorFactor)}%", if (f < config.conditionFloorFactor) TipTone.DRAWBACK else TipTone.BONUS))
    }
    if (t.wearMultiplier != 1.0) {
        val wear = (config.wearPerSiege * t.wearMultiplier).roundToInt()
        add(TipLine("The defenders' weapons lose $wear condition in the siege, usually ${config.wearPerSiege}", if (t.wearMultiplier > 1.0) TipTone.DRAWBACK else TipTone.BONUS))
    }
}

/** The trait's picture where it has one of its own. */
internal fun traitIcon(id: String): Int? = when (id) { Depth.LONG_ASSAULT -> R.drawable.icon_trait_long_assault; Depth.MANY_BREACHES -> R.drawable.icon_trait_many_breaches; else -> null }

/** A siege trait as a card: what it changes in this siege ([siegeTraitLines]), then what helps, a line a sentence. */
fun siegeTraitTip(t: SiegeTraitDef, config: BalanceConfig, day: Int?, tag: String = "town_trait_tip"): TipUi = TipUi(
    name = t.name, kind = "Siege modifier" + (day?.let { " · day $it" } ?: ""), icon = traitIcon(t.id), nameColor = Ember,
    sections = listOf(
        TipSection("In this siege", siegeTraitLines(t, config)),
        TipSection("What helps", t.counsel.split(Regex("(?<=\\.)\\s+")).filter { it.isNotBlank() }.map { TipLine(it, TipTone.PLAIN) }),
    ),
    tag = tag,
)

/** How the forecast reads, as a fact's tone: a strong position helps, an even one is plain, the rest warn. */
internal fun oddsTone(odds: Battle.SiegeOdds?): FactTone = when (odds) {
    Battle.SiegeOdds.STRONG -> FactTone.GOOD
    Battle.SiegeOdds.OUTMATCHED, Battle.SiegeOdds.DIRE -> FactTone.WARN
    else -> FactTone.PLAIN
}

/**
 * The besieger as a card (a classic run's Town): who, how hard they press, the two forecast numbers and how they read,
 * what bites them and what they shrug off, and the warlord when one leads them. Null with nobody pressing.
 */
fun besiegerTip(siege: TownSiegeUi, outlook: String?, odds: Battle.SiegeOdds?, icon: Int?, tag: String = "town_enemy_tip"): TipUi? {
    val foe = siege.foe ?: return null
    return TipUi(
        name = foe, kind = "Attackers · pressure ${siege.pressure.lowercase()}", icon = icon, nameColor = Ember,
        stats = listOfNotNull(
            siege.raid?.let { TipStat("Raid", "$it") },
            siege.defense?.let { TipStat("Town defense", "$it") },
            outlook?.let { TipStat("Outlook", it, oddsTone(odds)) },
        ),
        sections = listOf(
            TipSection("Against them", listOfNotNull(
                siege.weakTo?.let { TipLine("Weak to $it", TipTone.BONUS) },
                siege.resists?.let { TipLine("Resists $it", TipTone.DRAWBACK) },
                siege.warlord?.let { TipLine(it, TipTone.DRAWBACK) },
            )),
        ),
        tag = tag,
    )
}

/** A permanent upgrade as a card: its level, what the next level costs and gives, and what the upgrade does. */
fun upgradeTip(u: UpgradeDef, level: Int, next: String?, cost: Int?, tag: String): TipUi = TipUi(
    name = u.name, kind = "Legacy perk",
    stats = listOfNotNull(TipStat("Level", "$level of ${u.maxLevel}"), cost?.let { TipStat("Next level", "$it points") }),
    sections = listOf(
        TipSection("Next level", listOf(next?.let { TipLine(it, TipTone.BONUS) } ?: TipLine("Fully unlocked", TipTone.PLAIN))),
        TipSection("What it does", listOf(TipLine(u.description, TipTone.PLAIN))),
    ),
    tag = tag,
)

/** The run's legacy reward as a card: each part of the total under its label, then the milestones that earned theirs. */
fun runPointsTip(end: RunEndResult, milestone: (String) -> String, tag: String = "run_points_tip"): TipUi = TipUi(
    name = "${end.totalPoints} legacy points", kind = "Era ${end.era} · ${end.daysSurvived} days", nameColor = Gold,
    stats = listOf(
        TipStat("Baseline", "${end.basePoints}"), TipStat("Survival", "${end.survivalPoints}"),
        TipStat("Discoveries", "${end.discoveryPoints}"), TipStat("Milestones", "${end.milestonePoints}"),
    ),
    sections = listOf(TipSection("Milestones reached", end.milestones.map { TipLine(milestone(it), TipTone.BONUS) })),
    tag = tag,
)

/** A Legend Board entry as a card: the weapon's line as the head, then every line the board keeps of it. */
fun legendTip(l: LegendUi, tag: String): TipUi = TipUi(
    name = l.head, kind = "Hall of Legends",
    sections = listOf(TipSection(null, l.lines.map { TipLine(it, TipTone.PLAIN) })),
    tag = tag,
)

/**
 * The practice fight's effects as ability rows, always open: each that triggered by name, how often, and the chain that
 * led to it (cause first, the effect's own line last), then each that did not with the reason the bench read off the fight.
 */
internal fun benchFiredLines(ui: BenchUi): List<TipLine> = ui.fired.map { f ->
    TipLine(
        f.chain.lastOrNull().orEmpty(), TipTone.ABILITY, title = f.name,
        trigger = f.chain.dropLast(1).takeIf { it.isNotEmpty() }?.joinToString(" → "),
        limit = if (f.times == 1) "Triggered once" else "Triggered ${f.times} times",
    )
}

internal fun benchSilentLines(ui: BenchUi): List<TipLine> = ui.silent.map { TipLine(it.why, TipTone.CONDITION, title = it.name) }

/** Everything the practice fight made up, as its card: the bench's own list, each a line. */
fun benchSyntheticTip(ui: BenchUi, tag: String = "bench_synthetic_tip"): TipUi = TipUi(
    name = "Practice fight", kind = "What is made up",
    sections = listOf(TipSection(null, ui.synthetic.map { TipLine(it, TipTone.CONDITION) })),
    tag = tag,
)
