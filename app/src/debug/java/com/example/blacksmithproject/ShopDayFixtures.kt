package com.example.blacksmithproject

import com.tinyblacksmith.core.content.LaunchContent
import com.tinyblacksmith.core.engine.Command
import com.tinyblacksmith.core.engine.CommandOutcome
import com.tinyblacksmith.core.engine.GameEngine
import com.tinyblacksmith.core.model.CommandId
import com.tinyblacksmith.core.model.CommissionStatus
import com.tinyblacksmith.core.model.DayResolution
import com.tinyblacksmith.core.model.ForgeMode
import com.tinyblacksmith.core.model.GameState
import com.tinyblacksmith.core.model.LegacyProfile
import com.tinyblacksmith.core.model.Risk
import com.tinyblacksmith.core.model.VisitKind
import com.tinyblacksmith.core.model.VisitReason
import com.tinyblacksmith.core.shopday.AftermathKind
import com.tinyblacksmith.core.shopday.Ending
import com.tinyblacksmith.core.shopday.QuietKind
import com.tinyblacksmith.core.shopday.ShopDay
import com.tinyblacksmith.core.shopday.ShopDayScript

/**
 * Debug builds only: shop days for the preview activity and the screen tests, made by playing the real engine on a
 * seed. Nothing here is written by hand: every visit, sale and field result is the engine's own record of that day.
 */
object ShopDayFixtures {
    /** One resolved day: its script and the state End Day returned with it. */
    class Day(val engine: GameEngine, val seed: Long, val resolution: DayResolution, val state: GameState, val script: ShopDayScript)

    val engine = GameEngine()

    /** The same rules with a crowded town, for a ten-visitor day. A constructed case: the default seats four. */
    val busy = GameEngine(config = engine.config.copy(customers = engine.config.customers.copy(shopCapacity = 10, baseVisitChance = 0.9, startingHeroes = 12)))

    private val families = listOf(LaunchContent.SWORD, LaunchContent.BOW, LaunchContent.SPEAR, LaunchContent.AXE, LaunchContent.DAGGER, LaunchContent.STAFF)
    private val augments = listOf(LaunchContent.EMBER_RESIN, LaunchContent.FROST_BLOOM, LaunchContent.STORMGLASS)

    private fun GameState.tryRun(e: GameEngine, command: Command): GameState = (e.handle(this, command) as? CommandOutcome.Accepted)?.state ?: this

    /** A plain morning: take the blessing and the requests on offer, restock, forge up to three quick blades, list what is stored. */
    private fun GameState.morning(e: GameEngine): GameState {
        var s = this
        s.pendingBlessingOffer.firstOrNull()?.let { s = s.tryRun(e, Command.ChooseBlessing(it)) }
        for (c in s.commissions.values.filter { it.status == CommissionStatus.OFFERED }) s = s.tryRun(e, Command.AcceptCommission(c.id))
        repeat(3) { i ->
            val augment = augments[(s.day + i) % augments.size]
            if ((s.materials[LaunchContent.IRON] ?: 0) == 0) s = s.tryRun(e, Command.BuyMaterial(LaunchContent.IRON))
            if ((s.materials[augment] ?: 0) == 0) s = s.tryRun(e, Command.BuyMaterial(augment))
            s = s.tryRun(e, Command.Forge(ForgeMode.QUICK, families[(s.day + i) % families.size], LaunchContent.IRON, augment, null, Risk.BALANCED))
        }
        for (w in s.storedWeapons()) s = s.tryRun(e, Command.ToggleShelf(w.id, listed = true))
        return s
    }

    /** Every day of one run, up to [days] or its end. [forge] false is a smith who does nothing: bare shelves, then the fall. */
    fun run(seed: Long, days: Int, e: GameEngine = engine, forge: Boolean = true): Sequence<Day> = sequence {
        var s = e.newRun(LegacyProfile(), seed)
        repeat(days) {
            if (s.isEnded) return@sequence
            val prepared = if (forge) s.morning(e) else s
            val out = e.handle(prepared, Command.EndDay(CommandId("${s.runId.value}:day${s.day}"))) as CommandOutcome.Accepted
            val r = out.resolution!!
            yield(Day(e, seed, r, out.state, ShopDay.script(r, out.state, e.content, e.config)))
            s = out.state
        }
    }

    private fun search(e: GameEngine = engine, forge: Boolean = true, seeds: LongRange = 42L..53L, days: Int = 40, match: (ShopDayScript) -> Boolean): Day? =
        seeds.firstNotNullOfOrNull { seed -> run(seed, days, e, forge).firstOrNull { match(it.script) } }

    private fun ShopDayScript.sale(tradeIn: Boolean? = null) =
        featured.any { it.kind == VisitKind.BROWSE && it.purchasedWeaponId != null && (tradeIn == null || (it.sale?.tradeInWeaponId != null) == tradeIn) }

    val cases = listOf("first", "purchase", "tradein", "refusal", "notbetter", "commission", "empty", "novisitors", "busy", "siege", "blessing", "fallen") +
        AftermathKind.entries.map { "aftermath_${it.name.lowercase()}" }

    /** The first day on seeds 42 and up that shows [case]; null when the engine never produced one in the search window. */
    fun find(case: String): Day? = when (case) {
        "first" -> run(42, 1).firstOrNull()
        "purchase" -> search { it.sale(tradeIn = false) }
        "tradein" -> search { it.sale(tradeIn = true) }
        "refusal" -> search { s -> s.featured.any { it.reason == VisitReason.TOO_EXPENSIVE || it.reason == VisitReason.OVERPRICED } }
        "notbetter" -> search { s -> s.featured.any { it.reason == VisitReason.NOT_BETTER || it.reason == VisitReason.NOT_SUITED } }
        "commission" -> search { s -> s.featured.any { it.kind == VisitKind.COMMISSION } }
        "empty" -> search(forge = false) { it.quiet?.kind == QuietKind.EMPTY_SHELF }
        "novisitors" -> search(forge = false) { it.quiet?.kind == QuietKind.NO_VISITORS }
        "busy" -> search(busy) { it.visits.size >= 10 && it.featured.size == 3 }
        "siege" -> search { s -> s.aftermath.any { it.kind == AftermathKind.SIEGE_HELD || it.kind == AftermathKind.SIEGE_LOST } }
        "blessing" -> search { it.ending == Ending.BLESSING }
        "fallen" -> search(forge = false, days = 120) { it.ending == Ending.FALLEN }
        else -> AftermathKind.entries.firstOrNull { "aftermath_${it.name.lowercase()}" == case }?.let { kind -> search { s -> s.aftermath.any { it.kind == kind } } }
    }
}
