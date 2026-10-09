package com.example.blacksmithproject.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
    ModalBottomSheet(onDismissRequest = onDismiss, modifier = Modifier.semantics { testTagsAsResourceId = true }.testTag("settings_sheet")) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = Space.md).padding(bottom = Space.lg)) {
            Text("Settings", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(bottom = Space.sm))
            SwitchRow("Reduced motion", "Stops the ember animation, the reveal fade and the stepped battle replay.", reducedMotion, onReducedMotion, "settings_reduced_motion")
            SwitchRow("Haptics", "A short vibration when a blade is revealed, a day ends, a request is refused or an era ends.", haptics, onHaptics, "settings_haptics")
            if (onShopDaySpeed != null) SpeedRow(if (reducedMotion) ShopDaySpeed.TAP else shopDaySpeed, enabled = !reducedMotion, onShopDaySpeed)
            onMainMenu?.let { TextButton(onClick = it, modifier = Modifier.padding(top = Space.sm).heightIn(min = 48.dp).testTag("settings_main_menu")) { Text("Main menu") } }
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
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Secondary(detail)
        }
        Switch(checked = checked, onCheckedChange = null)
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
