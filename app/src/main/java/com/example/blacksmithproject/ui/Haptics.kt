package com.example.blacksmithproject.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback

/** The few moments that buzz. Names say what happened in the shop, not how it feels; the feel is [Effect]. */
enum class Moment(val effect: Effect) {
    /** The blade is revealed after a forge. */
    FORGE_STRIKE(Effect.TICK),
    /** A signature or an epic-or-better result: stronger than a plain strike. */
    FORGE_SIGNATURE(Effect.THUD),
    END_DAY(Effect.CONFIRM),
    /** A sale at the counter (the shop-day screen calls this). */
    SALE(Effect.LIGHT_TICK),
    REJECTED(Effect.REJECT),
    /** The forge has fallen. */
    RUN_END(Effect.TOLL),
}

/** Platform-free names for the effects, so the gate is testable on the JVM; only [toType] touches Compose. */
enum class Effect { LIGHT_TICK, TICK, CONFIRM, REJECT, THUD, TOLL }

private fun Effect.toType(): HapticFeedbackType = when (this) {
    Effect.LIGHT_TICK -> HapticFeedbackType.SegmentTick
    Effect.TICK -> HapticFeedbackType.ContextClick
    Effect.CONFIRM -> HapticFeedbackType.Confirm
    Effect.REJECT -> HapticFeedbackType.Reject
    Effect.THUD -> HapticFeedbackType.LongPress
    Effect.TOLL -> HapticFeedbackType.GestureThresholdActivate
}

/**
 * Every buzz goes through [play], and [play] does nothing while the player has haptics off, so the setting gates all
 * of them. No VIBRATE permission is involved: the effects are the system's own view haptics.
 */
class Haptics(private val enabled: () -> Boolean, private val perform: (Effect) -> Unit) {
    fun play(moment: Moment) {
        if (enabled()) perform(moment.effect)
    }
}

/** Silent until the app root provides [rememberHaptics], so previews and tests never buzz. */
val LocalHaptics = compositionLocalOf { Haptics(enabled = { false }, perform = {}) }

/** Builds the [Haptics] for the screen from the player's setting and the platform feedback; read it with [LocalHaptics]. */
@Composable
fun rememberHaptics(enabled: Boolean): Haptics {
    val feedback = LocalHapticFeedback.current
    val on by rememberUpdatedState(enabled)
    return remember(feedback) { Haptics(enabled = { on }, perform = { feedback.performHapticFeedback(it.toType()) }) }
}
