package com.tinyblacksmith.core

import com.tinyblacksmith.core.content.LaunchContent
import com.tinyblacksmith.core.engine.Command
import com.tinyblacksmith.core.engine.CommandOutcome
import com.tinyblacksmith.core.engine.Compatibility
import com.tinyblacksmith.core.engine.GameEngine
import com.tinyblacksmith.core.engine.stateOrThrow
import com.tinyblacksmith.core.model.*

object TestSupport {
    /** The engine default (launch content, default balance). Slice-specific tests construct their own engine. */
    val engine = GameEngine()

    /**
     * The launch catalog without opening packages: a guild run founded here signs its charter's two classes at once and
     * is offered the opening relic, as rules 5 did. For tests of rules the opening has no part in.
     */
    val engineWithoutOpenings = GameEngine(LaunchContent.catalog.copy(guild = LaunchContent.catalog.guild!!.copy(openings = emptyList())))

    /** The default balance with offers on a premium listing turned off: for tests of the price ceiling itself (`OffersTest` has the offers). */
    val engineWithoutOffers = GameEngine(config = engine.config.copy(customers = engine.config.customers.copy(offers = false)))

    /** A guild run with the first offered opening taken. */
    fun GameEngine.guildRun(legacy: LegacyProfile, seed: Long, charterId: String, openingId: String? = null, livingRevision: Int = LivingRevision.CURRENT): GameState =
        newRun(legacy, seed, charterId = charterId, livingRevision = livingRevision).let { s -> handle(s, Command.ChooseOpening(openingId ?: s.guild!!.openingOffer.first().defId)).stateOrThrow() }

    /** A run begun before living heroes: for tests of a legacy rule (old name pools, manual Specialities, the old siege roster). */
    fun GameEngine.legacyRun(legacy: LegacyProfile, seed: Long, charterId: String? = null): GameState = newRun(legacy, seed, charterId = charterId, livingRevision = LivingRevision.LEGACY)

    fun quickSword(risk: Risk = Risk.BALANCED, core: MaterialId = LaunchContent.IRON, augment: MaterialId = LaunchContent.EMBER_RESIN) =
        Command.Forge(ForgeMode.QUICK, LaunchContent.SWORD, core, augment, null, risk)

    fun endDayId(state: GameState) = CommandId("${state.runId.value}:day${state.day}")

    /** A decoded save brought forward to the installed rules, content and balance; what `GameSession.load` does before the engine sees it. */
    fun GameState.admitted(): GameState = (Compatibility.admit(this, engine.content, engine.config) as Compatibility.Result.Admitted).state

    fun GameState.endDay(): GameState = engine.handle(this, Command.EndDay(endDayId(this))).stateOrThrow()

    fun GameState.endDayAccepted(): CommandOutcome.Accepted = engine.handle(this, Command.EndDay(endDayId(this))) as CommandOutcome.Accepted

    fun GameState.run(cmd: Command): GameState = engine.handle(this, cmd).stateOrThrow()

    fun GameState.forgeAccepted(cmd: Command.Forge): CommandOutcome.Accepted = engine.handle(this, cmd) as CommandOutcome.Accepted

    fun GameState.withMaterials(amount: Int = 50): GameState = copy(materials = engine.content.materials.associate { it.id to amount })
}
