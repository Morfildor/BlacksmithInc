package com.example.blacksmithproject

import com.tinyblacksmith.core.model.HeroId
import com.tinyblacksmith.core.model.WeaponId
import com.tinyblacksmith.core.persistence.DayCursor
import com.tinyblacksmith.core.shopday.Ending
import com.tinyblacksmith.core.shopday.ShopDayScript

/**
 * One card of the shop day (plan 6.5). What moves inside a card (a visit's arrive, browse, decide and transact, merged
 * under reduced motion) belongs to the screen: the saved position is the card, so a kill repeats at most one visit.
 */
sealed interface Beat {
    val stage: DayCursor.Stage

    data object ShopOpens : Beat { override val stage get() = DayCursor.Stage.COUNTER }
    /** [visit] indexes `script.featured`. */
    data class Visit(val visit: Int) : Beat { override val stage get() = DayCursor.Stage.COUNTER }
    data object Tally : Beat { override val stage get() = DayCursor.Stage.COUNTER }
    data object ShopCloses : Beat { override val stage get() = DayCursor.Stage.COUNTER }
    /** A day with nobody to serve: replaces every other counter card. */
    data object Quiet : Beat { override val stage get() = DayCursor.Stage.COUNTER }
    /** [card] indexes `script.aftermath`. */
    data class Aftermath(val card: Int) : Beat { override val stage get() = DayCursor.Stage.AFTERMATH }
    data object Fallen : Beat { override val stage get() = DayCursor.Stage.TOMORROW }
    data object Blessing : Beat { override val stage get() = DayCursor.Stage.TOMORROW }
    data object Tomorrow : Beat { override val stage get() = DayCursor.Stage.TOMORROW }
}

/** Where the player stands in the day: [beats] is the whole day in order, [at] the card on screen. */
data class ShopDayPosition(val beats: List<Beat>, val at: Int) {
    val beat: Beat get() = beats[at]
    val isFirst: Boolean get() = at == 0
    val isLast: Boolean get() = at == beats.lastIndex
    /** Where Skip day lands: Forge fallen, Blessing or Tomorrow. */
    val ending: Int get() = beats.indexOfFirst { it.stage == DayCursor.Stage.TOMORROW }

    /** The cursor row for this card: its stage and its place among that stage's cards. */
    fun cursor(commandId: String): DayCursor = DayCursor(commandId, beat.stage, beats.take(at).count { it.stage == beat.stage })

    companion object {
        fun beats(script: ShopDayScript): List<Beat> = buildList {
            if (script.quiet != null) add(Beat.Quiet) else {
                add(Beat.ShopOpens)
                script.featured.indices.forEach { add(Beat.Visit(it)) }
                if (script.tally.isNotEmpty()) add(Beat.Tally)
                add(Beat.ShopCloses)
            }
            script.aftermath.indices.forEach { add(Beat.Aftermath(it)) }
            when (script.ending) {
                Ending.FALLEN -> add(Beat.Fallen)
                Ending.BLESSING -> { add(Beat.Blessing); add(Beat.Tomorrow) }
                Ending.TOMORROW -> add(Beat.Tomorrow)
            }
        }

        /**
         * The card a stored cursor names, clamped to the day: an index past the stage's last card is that last card, a
         * stage the day does not have is the first card after it, and no cursor is the first card.
         */
        fun index(beats: List<Beat>, cursor: DayCursor?): Int {
            if (cursor == null) return 0
            val inStage = beats.indices.filter { beats[it].stage == cursor.stage }
            if (inStage.isNotEmpty()) return inStage[cursor.index.coerceIn(inStage.indices)]
            return beats.indexOfFirst { it.stage > cursor.stage }.takeIf { it >= 0 } ?: beats.lastIndex
        }
    }
}

/** A detail sheet opened from the shop day; read-only there. */
sealed interface ShopDaySheet {
    data class Hero(val heroId: HeroId) : ShopDaySheet
    data class Blade(val weaponId: WeaponId) : ShopDaySheet
}
