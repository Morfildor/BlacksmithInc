package com.example.blacksmithproject.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.unit.dp
import com.example.blacksmithproject.ui.theme.Gold
import com.example.blacksmithproject.ui.theme.Space
import com.tinyblacksmith.core.model.WeaponId

/** Storage as a bottom sheet over the Shop. A blade's own sheet opens over it, so Back returns here. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StorageSheet(storage: List<StockUi>, shelfFree: Int, busy: Boolean, onOpenBlade: (WeaponId) -> Unit, onList: (WeaponId, Int) -> Unit, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    // A sheet is its own window: it does not inherit the root's resource-id exposure that the emulator scripts rely on.
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        dragHandle = { BottomSheetDefaults.DragHandle(width = 48.dp) },
        modifier = modifier.semantics { testTagsAsResourceId = true }.testTag("storage_sheet"),
    ) { StorageList(storage, shelfFree, busy, onOpenBlade, onList) }
}

/** The stored blades as one lazy, keyed list: a storeroom of hundreds composes only the rows in view. */
@Composable
fun StorageList(storage: List<StockUi>, shelfFree: Int, busy: Boolean, onOpenBlade: (WeaponId) -> Unit, onList: (WeaponId, Int) -> Unit, modifier: Modifier = Modifier) {
    LazyColumn(modifier.fillMaxWidth().navigationBarsPadding().testTag("storage_list"), contentPadding = PaddingValues(start = Space.md, end = Space.md, bottom = Space.lg)) {
        item(key = "head") {
            Column(Modifier.padding(bottom = Space.sm)) {
                Text("Storage · ${storage.size}", style = MaterialTheme.typography.titleLarge, color = Gold, modifier = Modifier.semantics { heading() })
                Secondary(
                    when {
                        storage.isEmpty() -> "Nothing in storage. Forged blades wait here until you list them."
                        shelfFree <= 0 -> "The shelf is full. Tap a blade to salvage, hone or give it to the watch."
                        else -> "$shelfFree free on the shelf. Tap a blade for its details and price."
                    },
                )
            }
        }
        items(storage, key = { "stock_${it.weapon.id.value}" }) { s ->
            StockRow(s, busy || shelfFree <= 0, onOpen = { onOpenBlade(s.weapon.id) }, onList = { price -> onList(s.weapon.id, price) })
        }
    }
}
