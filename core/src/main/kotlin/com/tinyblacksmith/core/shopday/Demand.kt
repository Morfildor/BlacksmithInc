package com.tinyblacksmith.core.shopday

import com.tinyblacksmith.core.config.BalanceConfig
import com.tinyblacksmith.core.content.ContentCatalog
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
)

object Demand {
    fun summary(state: GameState, content: ContentCatalog, config: BalanceConfig): DemandSummary {
        val heroes = state.aliveHeroes()
        val listed = state.listedWeapons()
        val carried = heroes.associate { it.id to state.equippedWeapon(it.id) }
        val prices = listed.mapNotNull { it.listedPrice }.sorted()
        val cheapest = prices.firstOrNull()
        val median = prices.getOrNull((prices.size - 1) / 2)
        fun canAfford(price: Int?) = if (price == null) 0 else heroes.count { it.gold + Market.tradeInCredit(carried[it.id], config) >= price }
        return DemandSummary(
            living = heroes.size,
            unarmed = heroes.filter { carried[it.id] == null }.map { it.id },
            worn = heroes.filter { h -> carried[h.id]?.let { it.condition < config.wornConditionThreshold } == true }.map { it.id },
            unservedClasses = heroes.map { it.classId }.distinct().sortedBy { it.value }
                .filter { c -> listed.none { (content.family(it.familyId).classFit[c] ?: config.offFamilyFit) >= 1.0 } },
            cheapestPrice = cheapest, medianPrice = median,
            canAffordCheapest = canAfford(cheapest), canAffordMedian = canAfford(median),
        )
    }
}
