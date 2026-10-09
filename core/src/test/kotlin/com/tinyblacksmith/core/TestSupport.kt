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
