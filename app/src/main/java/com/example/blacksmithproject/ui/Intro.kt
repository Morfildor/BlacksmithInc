package com.example.blacksmithproject.ui

import androidx.compose.runtime.Immutable
import com.example.blacksmithproject.Dest
import com.example.blacksmithproject.ForgeDraft
import com.example.blacksmithproject.data.IntroRecord
import com.example.blacksmithproject.data.IntroStatus
import com.tinyblacksmith.core.content.MaterialCategory
import com.tinyblacksmith.core.crafting.Forge
import com.tinyblacksmith.core.engine.GameEngine
import com.tinyblacksmith.core.gazette.Gazette
import com.tinyblacksmith.core.model.DayResolution
import com.tinyblacksmith.core.model.ForgeMode
import com.tinyblacksmith.core.model.GameState
import com.tinyblacksmith.core.model.LegacyProfile
import com.tinyblacksmith.core.model.VisitReason

/**
 * The guided first day (GDD 3.3 onboarding): the steps of one real day in a real run, in the order they are taught.
 * Nothing here plays the game: each step is finished by what the save shows, and the screen only says the next one.
 */
enum class IntroStep { RECIPE, FORGE, LIST, SECOND, END_DAY, OUTCOME }

/** The one control a step points at; drawn as an outline with a pointer, never as something over the screen. */
enum class IntroMark { NAV_FORGE, NAV_SHOP, FORGE, END_DAY }

/** A step in words: what to do, one sentence of why it works that way, and more for whoever asks ("Why?"). */
@Immutable data class IntroLine(val objective: String, val sentence: String, val why: String? = null)

/**
 * The step on screen. [line] is the strip over the workshop; [result] is what the forge result says instead while it
 * is open (the strip is under that card). [last]: the day is over and the strip closes with "Done".
 */
@Immutable data class IntroUi(val step: IntroStep, val overline: String, val line: IntroLine, val mark: IntroMark? = null, val result: IntroLine? = null) {
    val last: Boolean get() = step == IntroStep.OUTCOME
}

/** The guidance in Settings: where it stands and the one thing that can be done about it. */
@Immutable data class IntroRowUi(val detail: String, val action: String)

/** The numbered steps; the day's outcome closes them and has no number. */
private const val STEPS = 5

/** The morning the guidance can begin in: the first day of a run, before its first End Day, with a guild's opening already chosen. */
fun introCanBegin(run: GameState?): Boolean =
    run != null && !run.isEnded && run.day == 1 && run.lastResolution == null && run.guild?.openingOffer.isNullOrEmpty()

/**
 * An installation that has no guidance record yet. Whoever has forged, finished an era or played past a first day is
 * not a beginner: nothing is shown. Whoever only put a tip away may be one: they are asked. Everyone else is new.
 */
fun introFor(legacy: LegacyProfile, run: GameState?, seenTips: Set<String>): IntroRecord = when {
    legacy.eras.isNotEmpty() || legacy.claimedRunIds.isNotEmpty() || legacy.totalPointsEarned > 0 || legacy.journal.interactions.isNotEmpty() || (run != null && run.day > 1) ->
        IntroRecord(IntroStatus.SKIPPED)
    seenTips.isNotEmpty() -> IntroRecord(IntroStatus.OFFERED)
    else -> IntroRecord(IntroStatus.QUEUED)
}

/**
 * The step to show. Choosing and forging follow the workbench as it stands: a recipe emptied (or lost with the process)
 * is asked for again, because nothing was made yet. From the first forge on a step never comes back, whatever is
 * undone afterwards (a blade taken off the shelf, melted down, lent out): [IntroRecord.reached] remembers it.
 */
internal fun GameEngine.introStep(record: IntroRecord, state: GameState, draft: ForgeDraft): IntroStep {
    val floor = introFloor(record, state, draft)
    return if (floor < IntroStep.LIST && record.reached < IntroStep.LIST.ordinal) floor else IntroStep.entries[maxOf(record.reached, floor.ordinal).coerceAtMost(IntroStep.entries.lastIndex)]
}

/** The furthest step the save and the workbench show today; on its own it would fall back when a choice is undone. */
internal fun GameEngine.introFloor(record: IntroRecord, state: GameState, draft: ForgeDraft): IntroStep {
    val forged = state.nextWeaponSerial - record.startSerial
    return when {
        state.day > 1 -> IntroStep.OUTCOME
        forged >= 2 -> IntroStep.END_DAY
        forged == 1 -> when {
            state.listedWeapons().isEmpty() && state.storedWeapons().isNotEmpty() -> IntroStep.LIST
            canForgeAgain(state) -> IntroStep.SECOND
            else -> IntroStep.END_DAY
        }
        draft.command() != null -> IntroStep.FORGE
        else -> IntroStep.RECIPE
    }
}

/** A Quick forge is still within today's energy (overwork counted) and there is a metal and an augment to make it from. */
private fun GameEngine.canForgeAgain(state: GameState): Boolean {
    val short = (config.quickForgeEnergy - state.energy).coerceAtLeast(0)
    fun has(category: MaterialCategory) = content.materials(category).any { (state.materials[it.id] ?: 0) > 0 }
    return short <= config.maxOverworkPerDay - state.overworkToday && has(MaterialCategory.CORE) && has(MaterialCategory.AUGMENT)
}

/**
 * [record] brought up to what the save shows: a queued guidance begins in a run's first morning, a running one moves
 * on, ends with its second day, and stops (to be asked about, never restarted by itself) once its run is gone.
 */
fun GameEngine.introSynced(record: IntroRecord, run: GameState?, draft: ForgeDraft): IntroRecord = when (record.status) {
    IntroStatus.QUEUED -> if (run != null && introCanBegin(run)) IntroRecord(IntroStatus.ACTIVE, run.runId.value, 0, run.nextWeaponSerial, record.completedOnce) else record
    IntroStatus.ACTIVE -> when {
        run == null || run.runId.value != record.runId -> IntroRecord(IntroStatus.OFFERED, completedOnce = record.completedOnce)
        run.isEnded || run.day > 2 -> IntroRecord(IntroStatus.COMPLETED, completedOnce = true)
        else -> record.copy(reached = introStep(record, run, draft).ordinal.takeIf { it >= IntroStep.LIST.ordinal } ?: record.reached)
    }
    else -> record
}

/** The step to show in [state], or null where the guidance is not running in this run. */
fun GameEngine.introUi(record: IntroRecord?, state: GameState, dest: Dest, draft: ForgeDraft): IntroUi? {
    if (record == null || record.status != IntroStatus.ACTIVE || record.runId != state.runId.value || state.isEnded || state.day > 2 || !state.guild?.openingOffer.isNullOrEmpty()) return null
    val step = introStep(record, state, draft)
    val overline = if (step == IntroStep.OUTCOME) "FIRST DAY" else "FIRST DAY · ${step.ordinal + 1} of $STEPS"
    val onForge = dest == Dest.FORGE
    val toForge = "Open the Forge to do this."
    return when (step) {
        IntroStep.RECIPE -> IntroUi(
            step, overline,
            IntroLine(
                "Choose a weapon, a metal and an augment.",
                if (onForge) "The metal sets its strength and the augment sets its element. You learn how well the two work together by forging them." else toForge,
                "A more expensive metal gives more quality and power. Each metal and augment pair also has a hidden fit. Your Notebook records what you learn each time you forge.",
            ),
            mark = IntroMark.NAV_FORGE.takeIf { !onForge },
        )
        IntroStep.FORGE -> {
            val cost = draft.command()?.let { Forge.energyCost(config, it) } ?: if (draft.mode == ForgeMode.ADVANCED) config.advancedForgeEnergy else config.quickForgeEnergy
            val spends = if (state.energy >= cost) "$cost of your ${state.energy} energy" else "$cost energy"
            IntroUi(
                step, overline,
                IntroLine(
                    "Forge it.",
                    if (onForge) "It costs $spends and one of each ingredient. The quality lands close to what the recipe promises, sometimes a little higher and sometimes a little lower." else toForge,
                    "The Risk choice on the workbench sets how far the quality can move. Every forge gives a usable weapon, even one with a flaw.",
                ),
                mark = if (onForge) IntroMark.FORGE else IntroMark.NAV_FORGE,
            )
        }
        IntroStep.LIST -> IntroUi(
            step, overline,
            IntroLine("Put it on the shelf.", "Your new weapon is in Storage, on the Shop. Open Storage and list it for sale.", LIST_WHY),
            mark = IntroMark.NAV_SHOP.takeIf { dest != Dest.SHOP },
            result = IntroLine("List it for sale.", "Its quality comes from your recipe plus a random roll at the forge. Quality sets the weapon's power and its suggested price.", LIST_WHY),
        )
        IntroStep.SECOND -> IntroUi(
            step, overline,
            IntroLine(
                "Forge one more, your way.",
                if (onForge) "Change the metal or the augment, then compare the two results." else if (dest == Dest.SHOP) "Open the Forge to do this, or end the day here." else toForge,
                "This step is optional. You can end the day whenever you are ready.",
            ),
            mark = IntroMark.NAV_FORGE.takeIf { !onForge },
        )
        IntroStep.END_DAY -> IntroUi(
            step, overline,
            IntroLine(
                "End the day.",
                if (dest == Dest.SHOP) "The shop opens and heroes come in to buy on their own. Then they go out to fight with the weapons they carry." else "The End Day button is on the Shop.",
                "A sale is never certain. A hero without a good weapon may not come back from a fight. After End Day, the day's cards show what each hero did.",
            ),
            mark = if (dest == Dest.SHOP) IntroMark.END_DAY else IntroMark.NAV_SHOP,
        )
        IntroStep.OUTCOME -> IntroUi(step, overline, state.lastResolution?.let(::dayOutcome) ?: IntroLine("Day 1 is over.", NEXT_STEP))
    }
}

private const val LIST_WHY = "Only weapons on the shelf can sell. Heroes choose what to buy at End Day, and no hero can pay more gold than they carry."

private const val NEXT_STEP = "Each morning, \"Worth doing first\" on the Shop names a useful next step."

/**
 * What the first day came to, from its record alone. A sale is named. A day without one says which plain fact left the
 * till empty; where heroes looked and left, it counts the reasons the engine recorded for them (`MarketVisit.reason`,
 * in the Gazette's own words) and says where each hero is named: the Gazette's "All details". Nothing is guessed.
 */
internal fun dayOutcome(day: DayResolution): IntroLine {
    val sales = day.browsers.filter { it.purchasedWeaponId != null }
    val gold = sales.sumOf { it.sale?.cashPaid ?: 0 }
    val left = day.browsers.filter { it.purchasedWeaponId == null }
    val head = "Day ${day.day}: "
    return when {
        sales.size == 1 -> IntroLine(head + "${day.shopWeapons.firstOrNull { it.weaponId == sales[0].purchasedWeaponId }?.name ?: "your weapon"} sold for $gold gold.", NEXT_STEP)
        sales.size > 1 -> IntroLine(head + "${sales.size} weapons sold for $gold gold.", NEXT_STEP)
        day.shelfPrices.isEmpty() -> IntroLine(head + "nothing was on the shelf, so nothing could sell.", "List a weapon before the next End Day. Storage is on the Shop.")
        left.isEmpty() -> IntroLine(head + "no hero came in.", NEXT_STEP)
        else -> {
            val reasons = left.groupingBy { it.reason }.eachCount().entries.sortedWith(compareByDescending<Map.Entry<VisitReason, Int>> { it.value }.thenBy { it.key.ordinal })
                .joinToString(", ") { "${it.value} ${Gazette.visitReason(it.key)}" }
            IntroLine(
                head + if (left.size == 1) "one hero looked and did not buy." else "${left.size} heroes looked and none bought.",
                "Why they left: $reasons. To see each hero by name, open the Gazette in Records and tap \"All details\".",
                NEXT_STEP,
            )
        }
    }
}

/** [startsNow]: a replay asked for now would begin at once (the run is in its first morning); otherwise it waits for the next new game. */
fun introRow(record: IntroRecord, startsNow: Boolean): IntroRowUi {
    val replay = if (startsNow) "Replay now" else "Replay in next new game"
    return when (record.status) {
        IntroStatus.ACTIVE -> IntroRowUi("In progress in this run.", "Skip guidance")
        IntroStatus.QUEUED -> IntroRowUi("Starts with your next new game.", "Cancel")
        IntroStatus.OFFERED -> IntroRowUi("Stopped before the end.", replay)
        IntroStatus.COMPLETED -> IntroRowUi("Completed.", replay)
        IntroStatus.SKIPPED, IntroStatus.UNSET -> IntroRowUi("Off.", replay)
    }
}
