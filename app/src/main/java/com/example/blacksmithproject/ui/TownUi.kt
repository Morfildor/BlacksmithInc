package com.example.blacksmithproject.ui

import androidx.compose.runtime.Immutable
import com.tinyblacksmith.core.battle.Battle
import com.tinyblacksmith.core.content.UpgradeEffect
import com.tinyblacksmith.core.engine.GameEngine
import com.tinyblacksmith.core.gazette.Gazette
import com.tinyblacksmith.core.model.FactionId
import com.tinyblacksmith.core.model.GameState
import kotlin.math.roundToInt

/** A faction that is not the besieger: its name, how hard it presses (`Battle.describePressure`) and the element that bites it. */
@Immutable data class FactionRowUi(val id: FactionId, val name: String, val pressure: Int, val pressureWord: String, val weakTo: String?)

/**
 * Town's siege card as facts, so the card can draw them (two marks, two numbers, a bar) instead of a sentence. [foe],
 * [weakTo] and [resists] are the three parts of `ThreatUi.matchup`; [defense] and [raid] are the forecast's own numbers
 * (null without one); [forgeHealthMax] is the ceiling End Day's recovery stops at. [standing] are recorded numbers under
 * fixed labels.
 */
@Immutable
data class TownSiegeUi(
    val factionId: FactionId?, val foe: String?, val weakTo: String?, val resists: String?,
    val defense: Int?, val raid: Int?, val warlord: String?,
    val forgeHealth: Int, val forgeHealthMax: Int,
    val pressure: String, val standing: List<Pair<String, String>>, val world: String,
    val others: List<FactionRowUi>,
)

private fun pressureWord(pressure: Int) = Battle.describePressure(pressure).replaceFirstChar { it.uppercase() }

/** Pure: reads the save and the forecast already at hand; the besieger is the forecast's (the engine's own choice), else the faction pressing hardest. */
fun GameEngine.townSiege(state: GameState, forecast: Battle.SiegeOutlook?): TownSiegeUi {
    val leader = forecast?.factionState ?: state.factions.values.sortedBy { it.id.value }.maxByOrNull { it.pressure }
    val def = leader?.let { content.factionById[it.id] }
    val town = state.town
    return TownSiegeUi(
        factionId = leader?.id, foe = def?.name, weakTo = def?.weakTo?.word(), resists = def?.resists?.word(),
        defense = forecast?.townDefense?.roundToInt(), raid = forecast?.raidPower?.roundToInt(),
        warlord = forecast?.takeIf { it.warlord }?.faction?.warlordName?.let { "$it leads them" },
        forgeHealth = town.integrity,
        // The engine's own ceiling (End Day's recovery stops there); never below what the forge has.
        forgeHealthMax = maxOf(town.integrity, config.startingForgeIntegrity + upgradeTotal(state.legacy, UpgradeEffect.STARTING_INTEGRITY)),
        pressure = leader?.let { pressureWord(it.pressure) } ?: "Quiet",
        standing = listOfNotNull("Militia" to "${town.militia}", "Sieges held" to "${town.siegesSurvived}", ("Armory" to "${town.armory}/${config.armoryMax}").takeIf { town.armory > 0 }),
        world = state.world.name,
        others = state.factions.values.filter { it.id != leader?.id }.sortedByDescending { it.pressure }
            .map { f -> content.faction(f.id).let { d -> FactionRowUi(f.id, d.name, f.pressure, pressureWord(f.pressure), d.weakTo?.word()) } },
    )
}

/** The Gazette's masthead in its two parts: the paper's name and the day as a dateline. */
@Immutable data class GazetteHeadUi(val paper: String, val dateline: String)

fun gazetteHead(day: Int): GazetteHeadUi = GazetteHeadUi(Gazette.PAPER, Gazette.dateline(day).lowercase().replaceFirstChar { it.uppercase() })
