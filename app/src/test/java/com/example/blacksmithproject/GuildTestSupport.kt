package com.example.blacksmithproject

import com.tinyblacksmith.core.content.GuildContent
import com.tinyblacksmith.core.engine.Command
import com.tinyblacksmith.core.engine.CommandOutcome
import com.tinyblacksmith.core.engine.GameEngine
import com.tinyblacksmith.core.model.GameState
import com.tinyblacksmith.core.model.LegacyProfile

/** A guild run past its first choice: the first of the opening packages offered is taken, as the simulator's bots take it. */
internal fun GameEngine.guildRun(seed: Long, charterId: String = GuildContent.TOWNS_LAST_HOPE): GameState =
    newRun(LegacyProfile(), seed, charterId = charterId).let { s -> (handle(s, Command.ChooseOpening(s.guild!!.openingOffer.first().defId)) as CommandOutcome.Accepted).state }
