package com.tinyblacksmith.core.engine

import com.tinyblacksmith.core.config.BalanceConfig
import com.tinyblacksmith.core.content.ContentCatalog
import com.tinyblacksmith.core.content.ToolEffect
import com.tinyblacksmith.core.model.GameState

/**
 * The gate between a decoded save and the engine: can this build go on playing that run? A run from older rules,
 * content or balance continues forward under the installed ones; a run this build cannot play is refused with
 * reasons instead of crashing somewhere inside a later command.
 */
object Compatibility {
    sealed interface Result {
        /** [state] is the run with its rules, content and balance versions stamped to the installed ones; nothing else differs. */
        data class Admitted(val state: GameState) : Result
        data class Unsupported(val problems: List<String>) : Result
    }

    /** Pure. Never throws. Never touches events, weapon histories, serials or RNG stream states. */
    fun admit(state: GameState, content: ContentCatalog, config: BalanceConfig): Result {
        val problems = mutableListOf<String>()
        if (state.rulesVersion > GameEngine.RULES_VERSION) problems += "Run uses rules ${state.rulesVersion}, newer than this build's ${GameEngine.RULES_VERSION}"
        if (state.contentVersion > content.version) problems += "Run uses content ${state.contentVersion}, newer than this build's ${content.version}"
        // The shelf the engine allows (GameEngine.shelfSlots): a tool can add slots to the configured ones.
        val shelfSlots = config.shelfSlots + content.tools.filter { it.effect == ToolEffect.SHELF_SLOTS }.sumOf { it.magnitudePerLevel * (state.tools[it.id] ?: 0) }
        problems += Invariants.check(state, config, shelfSlots, content)
        if (problems.isNotEmpty()) return Result.Unsupported(problems)
        return Result.Admitted(state.copy(rulesVersion = GameEngine.RULES_VERSION, contentVersion = content.version, balanceVersion = config.version))
    }
}
