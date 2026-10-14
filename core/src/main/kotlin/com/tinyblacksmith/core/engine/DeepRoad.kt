package com.tinyblacksmith.core.engine

import com.tinyblacksmith.core.config.BalanceConfig
import com.tinyblacksmith.core.content.ContentCatalog
import com.tinyblacksmith.core.content.Depth
import com.tinyblacksmith.core.content.MaterialDef
import com.tinyblacksmith.core.model.EncounterInstance
import com.tinyblacksmith.core.model.EventType
import com.tinyblacksmith.core.model.GameState
import com.tinyblacksmith.core.model.Town

/**
 * Rules 7: the deep road. The supplier carries the big ingredients only while the road is open far enough for them
 * (`LegendConfig.roadStageByTier`). Stage 1 opens on [GameState.deepRoadOpenDay], which the first siege's story sets: in
 * a classic run the caravan master's visit ([Depth.DEEP_ROAD], answered in `EncounterCatalog`), in a guild run the
 * contract of the same name (`GuildContent.DEEP_ROAD`, won in `guild.Missions`). Stage 2, and stage 1 for a road nobody
 * opened, come the morning after the second siege, so no run is left without them. Draws nothing.
 */
object DeepRoad {
    /** `eventCounters` keys: each stage is said in the Gazette once. */
    const val OPENED = "road:open"
    const val SECOND = "road:second"

    fun fought(town: Town): Int = town.siegesSurvived + town.siegesLost

    fun stage(day: Int, openDay: Int?, town: Town): Int = when {
        fought(town) >= 2 -> 2
        openDay != null && day >= openDay -> 1
        else -> 0
    }
    fun stage(ctx: ResolutionContext): Int = stage(ctx.day, ctx.deepRoadOpenDay, ctx.town)
    fun stage(state: GameState): Int = stage(state.day, state.deepRoadOpenDay, state.town)

    /** The road stage [material] waits for: 0 for what the supplier always carries or what no stage holds back. */
    fun needs(material: MaterialDef, config: BalanceConfig): Int =
        if (material.dailySupplierStock == null) 0 else config.depth.legend.roadStageByTier[material.tier] ?: 0

    fun carried(material: MaterialDef, stage: Int, config: BalanceConfig): Boolean = needs(material, config) <= stage
    fun carried(ctx: ResolutionContext, material: MaterialDef): Boolean = carried(material, stage(ctx), ctx.config)

    /** False for a catalog the first stage holds nothing back from (the vertical slice): there is no road to open. */
    fun matters(content: ContentCatalog, config: BalanceConfig): Boolean = content.materials.any { needs(it, config) == 1 }

    /** The materials stage [n] brings, in catalog order: "Obsidian, Starsteel and Sun Ash". */
    fun names(content: ContentCatalog, config: BalanceConfig, n: Int): String =
        content.materials.filter { needs(it, config) == n }.map { it.name }.let { if (it.size <= 1) it.joinToString() else it.dropLast(1).joinToString(", ") + " and " + it.last() }

    /** A guild run with the road still shut after its first siege: the contract is posted until it is won or the second siege opens the road. */
    fun contractDue(ctx: ResolutionContext): Boolean =
        ctx.guild != null && matters(ctx.content, ctx.config) && ctx.deepRoadOpenDay == null && fought(ctx.town) == 1

    /**
     * New morning, before the restock. A classic run with no visitors in its catalog opens the road on the first morning
     * after the first siege, without a story. Each stage that is open by now is said once.
     */
    fun morning(ctx: ResolutionContext) {
        if (!matters(ctx.content, ctx.config)) return
        if (ctx.guild == null && ctx.content.encounters.isEmpty() && ctx.deepRoadOpenDay == null && fought(ctx.town) >= 1) ctx.deepRoadOpenDay = ctx.day
        val s = stage(ctx)
        if (s >= 1 && (ctx.eventCounters[OPENED] ?: 0) == 0) announce(ctx, OPENED, "The caravan master's train came down the deep road. The supplier carries ${names(ctx.content, ctx.config, 1)} from today.")
        if (s >= 2 && (ctx.eventCounters[SECOND] ?: 0) == 0) announce(ctx, SECOND, "After the second siege the caravan master brought ${names(ctx.content, ctx.config, 2)} down the deep road. The supplier carries it from today.")
    }

    /** The road opens today, with this morning's stock of what it brings, and the Gazette says so. */
    fun openNow(ctx: ResolutionContext) {
        ctx.deepRoadOpenDay = ctx.day
        for (m in ctx.content.materials) if (needs(m, ctx.config) == 1) ctx.supplierStock[m.id] = (ctx.supplierStock[m.id] ?: 0) + (m.dailySupplierStock ?: 0)
        announce(ctx, OPENED, "The deep road is open. The supplier carries ${names(ctx.content, ctx.config, 1)} from today.")
    }

    /** A guild's contract won tonight: the road opens with tomorrow's restock. */
    fun openTomorrow(ctx: ResolutionContext, why: String): String {
        ctx.deepRoadOpenDay = ctx.day + 1
        return "$why The deep road opens tomorrow: ${names(ctx.content, ctx.config, 1)}."
    }

    private fun announce(ctx: ResolutionContext, key: String, text: String) {
        ctx.eventCounters[key] = 1
        ctx.emit(EventType.SUPPLIER_NEWS, 6, text, data = mapOf("road" to key))
    }

    /**
     * A classic run, the first morning after the first siege that no follow-up has taken: the caravan master. Her train
     * comes in [LegendConfig.deepRoadWaitDays] days whatever the answer, so an unanswered visit costs only the wait.
     */
    fun visitor(ctx: ResolutionContext): EncounterInstance? {
        val key = Encounters.COUNTER_PREFIX + Depth.DEEP_ROAD
        if (ctx.guild != null || ctx.content.encounter(Depth.DEEP_ROAD) == null || !matters(ctx.content, ctx.config) || ctx.deepRoadOpenDay != null || fought(ctx.town) < 1 || (ctx.eventCounters[key] ?: 0) > 0) return null
        val legend = ctx.config.depth.legend
        ctx.deepRoadOpenDay = ctx.day + legend.deepRoadWaitDays
        return EncounterInstance(Encounters.newId(ctx), Depth.DEEP_ROAD, ctx.day, amounts = mapOf("gold" to legend.deepRoadCrateGold, "arrives" to ctx.day + legend.deepRoadWaitDays))
    }
}
