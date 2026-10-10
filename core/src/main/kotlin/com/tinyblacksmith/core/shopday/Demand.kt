package com.tinyblacksmith.core.shopday

import com.tinyblacksmith.core.config.BalanceConfig
import com.tinyblacksmith.core.content.ContentCatalog
import com.tinyblacksmith.core.engine.ResolutionContext
import com.tinyblacksmith.core.market.Market
import com.tinyblacksmith.core.model.GameState
import com.tinyblacksmith.core.model.HeroClassId
import com.tinyblacksmith.core.model.HeroId

/** "Who is buying": facts about the living heroes and the shelf as they stand. Lists are sorted by ID. */
data class DemandSummary(
    val living: Int,
    val unarmed: List<HeroId>,
    val worn: List<HeroId>,                       // carry a blade below `wornConditionThreshold`
    val unservedClasses: List<HeroClassId>,       // classes of living heroes that no listed blade suits
    val cheapestPrice: Int?, val medianPrice: Int?,   // of the listed blades; null on an empty shelf
    val canAffordCheapest: Int, val canAffordMedian: Int,   // purse plus trade-in credit, as the counter counts it
    val wants: List<HeroId> = emptyList(),        // living heroes with a standing want (`Hero.want`; words: `Lines.want`)
    val wantsAnswered: List<HeroId> = emptyList(),   // those of them a listed blade answers today (`Market.answersWant`)
)

/**
 * The besieger as things stand (the faction with the most pressure) and what its hide is weak to and resists. [warned]:
 * the siege warning is out, so today's customers weigh it (`Battle.warnedFaction`); on other days the labels are advice only.
 */
data class Threat(val factionId: com.tinyblacksmith.core.model.FactionId, val weakTo: com.tinyblacksmith.core.content.Element?, val resists: com.tinyblacksmith.core.content.Element?, val daysToSiege: Int, val warned: Boolean)

enum class ThreatMark { COUNTERS, RESISTED }

object Threats {
    /** Null only in a world without factions. Reads the state; draws nothing. */
    fun of(state: GameState, content: ContentCatalog, config: BalanceConfig): Threat? {
        val leader = state.factions.values.sortedBy { it.id.value }.maxByOrNull { it.pressure } ?: return null   // Battle.leadingFaction's rule
        val def = content.factionById[leader.id] ?: return null
        val days = state.town.nextSiegeDay - state.day
        return Threat(def.id, def.weakTo, def.resists, days, warned = config.customers.threatUtility > 0 && days in 0 until config.combat.siegeWarningDays)
    }

    /** How a blade or a material of [element] stands against [threat]; null for no element, no threat or an element the besieger does not care about. */
    fun mark(element: com.tinyblacksmith.core.content.Element?, threat: Threat?): ThreatMark? = when {
        element == null || threat == null -> null
        element == threat.weakTo -> ThreatMark.COUNTERS
        element == threat.resists -> ThreatMark.RESISTED
        else -> null
    }
}

object Demand {
    fun summary(state: GameState, content: ContentCatalog, config: BalanceConfig): DemandSummary {
        val heroes = state.aliveHeroes()
        val listed = state.listedWeapons()
        val carried = heroes.associate { it.id to state.equippedWeapon(it.id) }
        val prices = listed.mapNotNull { it.listedPrice }.sorted()
        val cheapest = prices.firstOrNull()
        val median = prices.getOrNull((prices.size - 1) / 2)
        fun canAfford(price: Int?) = if (price == null) 0 else heroes.count { it.gold + Market.tradeInCredit(carried[it.id], config) >= price }
        val wanting = heroes.filter { it.want != null }
        val ctx = if (wanting.isEmpty() || listed.isEmpty()) null else ResolutionContext(state, content, config)   // read only: the counter's own rule, no draw
        return DemandSummary(
            living = heroes.size,
            unarmed = heroes.filter { carried[it.id] == null }.map { it.id },
            worn = heroes.filter { h -> carried[h.id]?.let { it.condition < config.wornConditionThreshold } == true }.map { it.id },
            unservedClasses = heroes.map { it.classId }.distinct().sortedBy { it.value }
                .filter { c -> listed.none { (content.family(it.familyId).classFit[c] ?: config.offFamilyFit) >= 1.0 } },
            cheapestPrice = cheapest, medianPrice = median,
            canAffordCheapest = canAfford(cheapest), canAffordMedian = canAfford(median),
            wants = wanting.map { it.id },
            wantsAnswered = if (ctx == null) emptyList() else wanting.filter { h -> listed.any { Market.answersWant(ctx, h, it) } }.map { it.id },
        )
    }
}
