package com.example.blacksmithproject.ui.shopday

import androidx.compose.foundation.BorderStroke
import com.example.blacksmithproject.data.ShopDaySpeed
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Button
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
import com.example.blacksmithproject.ui.theme.SceneCream
import com.example.blacksmithproject.ui.theme.SceneGold
import com.example.blacksmithproject.ui.theme.SceneInk
import com.example.blacksmithproject.ui.theme.Space

/**
 * The strip above the scene: which day and which part of it, the speed chip and Skip day. Skip day is one tap and never
 * asks (nothing is lost by skipping). The speed chip cycles Tap, 1x, 2x; with reduced motion nothing runs on a timer,
 * so it shows Tap and is off.
 */
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
    Row(modifier.fillMaxWidth().background(SceneInk).heightIn(min = 48.dp).padding(start = 12.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(vertical = 4.dp)) {
            Text("DAY $day", style = MaterialTheme.typography.labelSmall, color = SceneGold, maxLines = 1)
            Text(progress, style = MaterialTheme.typography.labelLarge, color = SceneCream, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.testTag("shopday_progress"))
        }
        val shown = if (reducedMotion) ShopDaySpeed.TAP else speed
        // The cards that end the day wait for a choice at any speed, so they show neither control.
        if (canSkip) OutlinedButton(
            onClick = { onSpeedChange(speed.next) }, enabled = !reducedMotion,
            border = BorderStroke(1.dp, SceneGold.copy(alpha = if (reducedMotion) 0.4f else 1f)),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = SceneCream, disabledContentColor = SceneCream.copy(alpha = 0.5f)),
            contentPadding = ButtonDefaults.TextButtonContentPadding,
            modifier = Modifier.heightIn(min = 48.dp).widthIn(min = 64.dp).testTag("shopday_speed")
                .semantics { contentDescription = "Speed: ${shown.label}. " + if (reducedMotion) "Off with reduced motion" else "Change speed" },
        ) { Text(shown.label, maxLines = 1) }
        if (canSkip) {
            TextButton(onClick = onSkipDay, colors = ButtonDefaults.textButtonColors(contentColor = SceneCream), modifier = Modifier.heightIn(min = 48.dp).testTag("shopday_skip")) {
                Text("Skip day", maxLines = 1)
            }
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
        OutlinedButton(onClick = onBack, enabled = canBack, modifier = Modifier.heightIn(min = 52.dp).testTag("shopday_back")) { Text("Back", maxLines = 1) }
        Button(onClick = onNext, modifier = Modifier.weight(1f).heightIn(min = 52.dp).testTag(nextTag)) { Text(nextLabel, style = MaterialTheme.typography.titleMedium, maxLines = 1) }
    }
}
