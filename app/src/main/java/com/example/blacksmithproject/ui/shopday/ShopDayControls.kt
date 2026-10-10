package com.example.blacksmithproject.ui.shopday

import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.BorderStroke
import com.example.blacksmithproject.data.ShopDaySpeed
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.blacksmithproject.ui.SKIP_TO_EVENING
import com.example.blacksmithproject.ui.SecondaryActionButton
import com.example.blacksmithproject.ui.PrimaryActionButton
import com.example.blacksmithproject.ui.theme.SceneCream
import com.example.blacksmithproject.ui.theme.SceneGold
import com.example.blacksmithproject.ui.theme.SceneInk
import com.example.blacksmithproject.ui.theme.Space

/**
 * The strip above the scene: which day and which part of it, the playback chip and the skip. The skip is one tap and never
 * asks (nothing is lost by skipping). The chip cycles Manual, Auto 1x, Auto 2x; with reduced motion nothing runs on a
 * timer, so it shows Manual and is off.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ShopDayTopBar(
    day: Int,
    progress: String,
    speed: ShopDaySpeed,
    reducedMotion: Boolean,
    canSkip: Boolean,
    onSpeedChange: (ShopDaySpeed) -> Unit,
    onSkipDay: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // The strip takes its own taps: one that misses the chip or the skip must not count as the screen's "tap anywhere".
    // The controls stand beside the day's label while both fit; with large text or a narrow screen they take the line
    // under it, so the label is never cut short to make room for them.
    FlowRow(
        modifier.fillMaxWidth().background(SceneInk).pointerInput(Unit) { detectTapGestures { } }.heightIn(min = 48.dp).padding(start = 12.dp, end = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween, verticalArrangement = Arrangement.Center,
    ) {
        Column(Modifier.align(Alignment.CenterVertically).padding(vertical = 4.dp)) {
            Text("DAY $day", style = MaterialTheme.typography.labelSmall, color = SceneGold, maxLines = 1)
            Text(progress, style = MaterialTheme.typography.labelLarge, color = SceneCream, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.testTag("shopday_progress"))
        }
        val shown = if (reducedMotion) ShopDaySpeed.TAP else speed
        // The cards that end the day wait for a choice at any speed, so they show neither control.
        if (canSkip) Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(
                onClick = { onSpeedChange(speed.next) }, enabled = !reducedMotion,
                border = BorderStroke(1.dp, SceneGold.copy(alpha = if (reducedMotion) 0.4f else 1f)), shape = MaterialTheme.shapes.small,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = SceneCream, disabledContentColor = SceneCream.copy(alpha = 0.5f)),
                contentPadding = ButtonDefaults.TextButtonContentPadding,
                modifier = Modifier.heightIn(min = 48.dp).widthIn(min = 64.dp).testTag("shopday_speed")
                    .semantics {
                        contentDescription = "Playback: " + (if (shown == ShopDaySpeed.TAP) "manual, each card waits for you" else "automatic, ${shown.label.substringAfter(' ')} speed") + ". " +
                            if (reducedMotion) "Stays manual with reduced motion" else "Change playback"
                    },
            ) { Text(shown.label, maxLines = 1) }
            TextButton(
                onClick = onSkipDay, colors = ButtonDefaults.textButtonColors(contentColor = SceneCream),
                modifier = Modifier.heightIn(min = 48.dp).testTag("shopday_skip").semantics { contentDescription = "$SKIP_TO_EVENING: jump to the day's last card. Nothing is lost." },
            ) { Text(SKIP_TO_EVENING, maxLines = 1) }
        }
    }
}

/**
 * Back steps one beat and never closes the day. The wide button is the one way forward: Next (the same as a tap
 * anywhere) during the day, and on the last cards the choice that ends it, under its own [nextTag].
 */
@Composable
fun ShopDayControls(canBack: Boolean, nextLabel: String, onBack: () -> Unit, onNext: () -> Unit, modifier: Modifier = Modifier, nextTag: String = "shopday_next") {
    Row(modifier.fillMaxWidth().padding(horizontal = Space.md, vertical = Space.sm), horizontalArrangement = Arrangement.spacedBy(Space.sm), verticalAlignment = Alignment.CenterVertically) {
        SecondaryActionButton("Back", onBack, Modifier.heightIn(min = 52.dp).testTag("shopday_back"), enabled = canBack)
        PrimaryActionButton(nextLabel, onNext, Modifier.weight(1f).testTag(nextTag))
    }
}
