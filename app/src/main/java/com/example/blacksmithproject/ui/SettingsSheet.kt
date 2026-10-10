package com.example.blacksmithproject.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import com.example.blacksmithproject.ui.theme.Bronze
import com.example.blacksmithproject.ui.theme.ForgeSlot
import com.example.blacksmithproject.ui.theme.Gold
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.unit.dp
import com.example.blacksmithproject.data.ShopDaySpeed
import com.example.blacksmithproject.ui.theme.Space

/**
 * The settings behind the gear. Only what the game reads today: reduced motion, haptics, the shop-day speed and the
 * version; there is no sound, so there is no sound setting (plan 10.3). The speed row shows once [onShopDaySpeed] is given.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSheet(
    reducedMotion: Boolean, onReducedMotion: (Boolean) -> Unit, haptics: Boolean, onHaptics: (Boolean) -> Unit, onDismiss: () -> Unit,
    shopDaySpeed: ShopDaySpeed = ShopDaySpeed.TAP, onShopDaySpeed: ((ShopDaySpeed) -> Unit)? = null, onMainMenu: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val version = remember { runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "unknown" }
    // A sheet is its own window: it does not inherit the root's resource-id exposure that the emulator scripts rely on.
    // Opened whole, as the other sheets are: half open, the speed row and the way to the main menu were under the fold.
    ModalBottomSheet(
        onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        dragHandle = { BottomSheetDefaults.DragHandle(width = 48.dp) },
        modifier = Modifier.semantics { testTagsAsResourceId = true }.testTag("settings_sheet"),
    ) {
        FramedPanel("Settings", Modifier.fillMaxWidth().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = Space.md).padding(bottom = Space.lg)) {
            SwitchRow("Reduced motion", "Stops the ember animation, the reveal fade and the stepped battle replay.", reducedMotion, onReducedMotion, "settings_reduced_motion")
            SwitchRow("Haptics", "A short vibration when a blade is revealed, a day ends, a request is refused or an era ends.", haptics, onHaptics, "settings_haptics")
            if (onShopDaySpeed != null) SpeedRow(if (reducedMotion) ShopDaySpeed.TAP else shopDaySpeed, enabled = !reducedMotion, onShopDaySpeed)
            onMainMenu?.let { SecondaryActionButton("Main menu", it, Modifier.fillMaxWidth().padding(top = Space.md).testTag("settings_main_menu")) }
            Secondary("Version $version", Modifier.padding(top = Space.md).testTag("settings_version"))
        }
    }
}

@Composable
private fun SwitchRow(title: String, detail: String, checked: Boolean, onChange: (Boolean) -> Unit, tag: String) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag(tag).toggleable(value = checked, role = Role.Switch, onValueChange = onChange),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(vertical = Space.sm).padding(end = Space.sm)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Secondary(detail)
        }
        // Gold when on, as every chosen thing in the workshop is; the thumb's place still says which way it is set.
        Switch(checked = checked, onCheckedChange = null, colors = SwitchDefaults.colors(checkedTrackColor = Gold, checkedThumbColor = ForgeSlot, uncheckedTrackColor = ForgeSlot, uncheckedThumbColor = Bronze, uncheckedBorderColor = Bronze))
    }
}

/** Tap is the default: the shop day waits for the player. 1x and 2x move it on by themselves; reduced motion keeps it at Tap. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SpeedRow(speed: ShopDaySpeed, enabled: Boolean, onChange: (ShopDaySpeed) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = Space.sm).testTag("settings_shopday_speed")) {
        Text("Shop-day speed", style = MaterialTheme.typography.titleSmall)
        Secondary(if (enabled) "Tap waits for you at every customer. 1x and 2x move the day on by themselves." else "Stays at Tap while reduced motion is on.")
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(top = Space.xs)) {
            ShopDaySpeed.entries.forEachIndexed { i, option ->
                SegmentedButton(
                    selected = option == speed, onClick = { onChange(option) }, enabled = enabled,
                    shape = SegmentedButtonDefaults.itemShape(i, ShopDaySpeed.entries.size),
                    modifier = Modifier.heightIn(min = 48.dp).testTag("settings_shopday_speed_${option.name.lowercase()}"),
                ) { Text(option.label) }
            }
        }
    }
}
