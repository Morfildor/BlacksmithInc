package com.example.blacksmithproject.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.tinyblacksmith.core.model.MaterialId

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

/** A tool's level and what the next one costs (`GameEngine.toolCost`; null once there is no next level). */
internal fun toolLine(level: Int, maxLevel: Int, cost: Int?): String = "Level $level of $maxLevel · " + (cost?.let { "$it gold" } ?: "fully built")

/** The supplier and the workshop tools, as a sheet over the Shop or the Forge; with [focus] it opens on that material's row. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SuppliesSheet(s: UiState.Playing, vm: GameViewModel, onDismiss: () -> Unit, modifier: Modifier = Modifier, focus: MaterialId? = null) {
    // A sheet is its own window: it does not inherit the root's resource-id exposure that the emulator scripts rely on.
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        dragHandle = { BottomSheetDefaults.DragHandle(width = 48.dp) },
        modifier = modifier.semantics { testTagsAsResourceId = true }.testTag("supplies_sheet"),
    ) { SuppliesList(s, vm, focus = focus, onClose = onDismiss) }
}

/** [onClose], when given, ends the list with a "Close" button: the list is long and its handle is far away by then. */
@Composable
fun SuppliesList(s: UiState.Playing, vm: GameViewModel, modifier: Modifier = Modifier, focus: MaterialId? = null, onClose: (() -> Unit)? = null) {
    val content = vm.engine.content
    val st = s.state
    val groups = MaterialCategory.entries.map { it to content.materials(it) }.filter { it.second.isNotEmpty() }
    val listState = rememberLazyListState()
    // The asked-for row's place in the list below, by the keys the list gives its items up to the last material.
    LaunchedEffect(focus) {
        val keys = listOf("supplier") + groups.flatMap { (category, group) -> listOf("supplier_${category.name}") + group.map { "material_${it.id.value}" } }
        val at = keys.indexOf("material_${focus?.value}")
        if (at >= 0) listState.scrollToItem(at)
    }
    LazyColumn(modifier.fillMaxWidth().navigationBarsPadding().testTag("supplies_list"), state = listState, contentPadding = PaddingValues(start = Space.md, end = Space.md, bottom = Space.lg)) {
        item(key = "supplier") {
            Column {
                // The purse beside the title: it is the one number every row below is read against.
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Supplies", style = MaterialTheme.typography.titleLarge, color = Gold, modifier = Modifier.weight(1f).semantics { heading() })
                    Text("${st.gold} gold", style = MaterialTheme.typography.titleMedium, color = Gold, modifier = Modifier.semantics { contentDescription = "${st.gold} gold in the purse" })
                }
                Secondary("Buy one at a time. Basic metals are always in stock.")
            }
        }
        groups.forEach { (category, group) ->
            item(key = "supplier_${category.name}") {
                SectionHeader(when (category) { MaterialCategory.CORE -> "Metals";MaterialCategory.AUGMENT -> "Augments"; MaterialCategory.CATALYST -> "Catalysts" })
            }
            items(group, key = { "material_${it.id.value}" }) { m ->
                val price = vm.engine.materialPrice(st, m.id)
                val stock = st.supplierStock[m.id]
                val have = st.materials[m.id] ?: 0
                val needed = m.id == focus
                Row(
                    // The row the Forge asked for: a gold line and its own words, never the colour alone.
                    Modifier.fillMaxWidth().padding(vertical = Space.xs).then(if (needed) Modifier.border(1.dp, Gold, MaterialTheme.shapes.small).padding(Space.xs) else Modifier),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Sprites.material(m.id)?.let { PixelImage(it, wholePixelDp(48, 40.dp), description = null) }
                    Column(Modifier.weight(1f)) {
                        if (needed) Text("Needed on the workbench", style = MaterialTheme.typography.labelSmall, color = Gold)
                        Text(m.name, style = MaterialTheme.typography.titleSmall)
                        Secondary("Owned $have · $price gold" + (stock?.let { " · $it left today" } ?: ""))
                        vm.engine.supplyNotes(st, m).forEach { Secondary(it) }
                        // A guild run: what an augment or a catalyst gives a blade in a fight, in the catalog's own words.
                        if (st.guild != null) materialRules(content, m.id).forEach { Secondary("In a fight · ${it.name}: ${it.description}") }
                    }
                    // A greyed Buy says why to a screen reader; on screen the row's own lines do (the price, "0 left today").
                    val blocked = when { stock == 0 -> "none left today"; st.gold < price -> "it costs $price gold and you have ${st.gold}"; else -> null }
                    SecondaryActionButton(
                        "Buy", { vm.dispatch(Command.BuyMaterial(m.id, 1)) },
                        Modifier.testTag("buy_${m.id.value}").semantics { contentDescription = blocked?.let { "Buy ${m.name}, unavailable: $it" } ?: "Buy ${m.name} for $price gold" },
                        enabled = blocked == null && !s.busy,
                    )
                }
            }
        }
        item(key = "tools") {
            Column {
                SectionHeader("Workshop tools")
                Secondary("Bought with gold; they last until the forge falls.")
            }
        }
        items(content.tools, key = { "tool_${it.id}" }) { t ->
            val cost = vm.engine.toolCost(st, t.id)
            val level = st.tools[t.id] ?: 0
            // A tool's row is a material's row: the name, what it does, then its level and price, and the same "Buy".
            Row(Modifier.fillMaxWidth().padding(vertical = Space.xs), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                        Text(t.name, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f, fill = false))
                        LevelDots(level, t.maxLevel)
                    }
                    Secondary(t.description)
                    Secondary(toolLine(level, t.maxLevel, cost))
                }
                if (cost != null) SecondaryActionButton(
                    "Buy", { vm.dispatch(Command.BuyTool(t.id)) },
                    Modifier.testTag("buy_tool_${t.id}").semantics { contentDescription = if (st.gold >= cost) "Buy ${t.name} for $cost gold" else "Buy ${t.name}, unavailable: it costs $cost gold and you have ${st.gold}" },
                    enabled = st.gold >= cost && !s.busy,
                )
            }
        }
        if (onClose != null) item(key = "close") {
            SecondaryActionButton("Close", onClose, Modifier.fillMaxWidth().padding(top = Space.md).testTag("supplies_close"))
        }
    }
}
