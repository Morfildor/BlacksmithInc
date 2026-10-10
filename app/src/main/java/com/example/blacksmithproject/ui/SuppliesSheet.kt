package com.example.blacksmithproject.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.unit.dp
import com.example.blacksmithproject.GameViewModel
import com.example.blacksmithproject.UiState
import com.example.blacksmithproject.ui.theme.Gold
import com.example.blacksmithproject.ui.theme.Space
import com.tinyblacksmith.core.content.MaterialCategory
import com.tinyblacksmith.core.content.MaterialDef
import com.tinyblacksmith.core.content.UpgradeEffect
import com.tinyblacksmith.core.engine.Command
import com.tinyblacksmith.core.engine.GameEngine
import com.tinyblacksmith.core.engine.WorldEvents
import com.tinyblacksmith.core.model.GameState

/**
 * Where today's stock of a limited material comes from, beyond the supplier's own: the Caravan Ties legacy track, the
 * ore merchant in town this morning, or a caravan that did not arrive. Read from the save and the config; empty for a
 * material that is always in stock.
 */
fun GameEngine.supplyNotes(state: GameState, m: MaterialDef): List<String> {
    if (m.dailySupplierStock == null) return emptyList()
    val ties = upgradeTotal(state.legacy, UpgradeEffect.CATALOG_ACCESS) * config.legacyTracks.catalogStockPerLevel
    return listOfNotNull(
        "The caravan is delayed: none came today".takeIf { state.worldFlags[WorldEvents.FLAG_CARAVAN_DELAYED] == state.day },
        "Caravan Ties: $ties more each day".takeIf { ties > 0 },
        "Ore merchant in town: ${config.worldEvents.oreMerchantStock} more today".takeIf { state.worldFlags[WorldEvents.FLAG_ORE_MERCHANT + m.id.value] == state.day },
    )
}

/** The supplier and the workshop tools, as a sheet over the Shop or the Forge. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SuppliesSheet(s: UiState.Playing, vm: GameViewModel, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    // A sheet is its own window: it does not inherit the root's resource-id exposure that the emulator scripts rely on.
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        dragHandle = { BottomSheetDefaults.DragHandle(width = 48.dp) },
        modifier = modifier.semantics { testTagsAsResourceId = true }.testTag("supplies_sheet"),
    ) { SuppliesList(s, vm) }
}

@Composable
fun SuppliesList(s: UiState.Playing, vm: GameViewModel, modifier: Modifier = Modifier) {
    val content = vm.engine.content
    val st = s.state
    LazyColumn(modifier.fillMaxWidth().navigationBarsPadding().testTag("supplies_list"), contentPadding = PaddingValues(start = Space.md, end = Space.md, bottom = Space.lg)) {
        item(key = "supplier") {
            Column {
                Text("Supplier", style = MaterialTheme.typography.titleLarge, color = Gold, modifier = Modifier.semantics { heading() })
                Secondary("${st.gold} gold in the purse. Buy one at a time. Basic metals are always in stock.")
            }
        }
        MaterialCategory.entries.forEach { category ->
            val group = content.materials(category)
            if (group.isEmpty()) return@forEach
            item(key = "supplier_${category.name}") {
                SectionHeader(when (category) { MaterialCategory.CORE -> "Cores"; MaterialCategory.AUGMENT -> "Augments"; MaterialCategory.CATALYST -> "Catalysts" })
            }
            items(group, key = { "material_${it.id.value}" }) { m ->
                val price = vm.engine.materialPrice(st, m.id)
                val stock = st.supplierStock[m.id]
                val have = st.materials[m.id] ?: 0
                Row(Modifier.fillMaxWidth().padding(vertical = Space.xs), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                    Sprites.material(m.id)?.let { PixelImage(it, wholePixelDp(48, 40.dp), description = null) }
                    Column(Modifier.weight(1f)) {
                        Text("${m.name} · $price gold", style = MaterialTheme.typography.bodyMedium)
                        Secondary("You have $have" + (stock?.let { " · $it left today" } ?: ""))
                        vm.engine.supplyNotes(st, m).forEach { Secondary(it) }
                    }
                    SecondaryActionButton(
                        "Buy", { vm.dispatch(Command.BuyMaterial(m.id, 1)) },
                        Modifier.testTag("buy_${m.id.value}").semantics { contentDescription = "Buy ${m.name} for $price gold" },
                        enabled = (stock == null || stock > 0) && st.gold >= price && !s.busy,
                    )
                }
            }
        }
        item(key = "tools") {
            Column {
                SectionTitle("Workshop tools")
                Secondary("Bought with gold; they last until the forge falls.")
            }
        }
        items(content.tools, key = { "tool_${it.id}" }) { t ->
            val cost = vm.engine.toolCost(st, t.id)
            Row(Modifier.fillMaxWidth().padding(vertical = Space.xs), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                Column(Modifier.weight(1f)) {
                    Text("${t.name} · level ${st.tools[t.id] ?: 0}/${t.maxLevel}", style = MaterialTheme.typography.bodyMedium)
                    Secondary(t.description)
                }
                if (cost == null) Secondary("Maxed")
                else SecondaryActionButton(
                    "Buy · $cost g", { vm.dispatch(Command.BuyTool(t.id)) },
                    Modifier.semantics { contentDescription = "Buy ${t.name} for $cost gold" },
                    enabled = st.gold >= cost && !s.busy,
                )
            }
        }
    }
}
