package com.example.blacksmithproject.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.ui.platform.LocalDensity
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
import com.tinyblacksmith.core.content.ToolDef
import com.tinyblacksmith.core.content.UpgradeEffect
import com.tinyblacksmith.core.crafting.Forge
import com.tinyblacksmith.core.engine.Command
import com.tinyblacksmith.core.engine.GameEngine
import com.tinyblacksmith.core.engine.WorldEvents
import com.tinyblacksmith.core.model.ConsequenceKind
import com.tinyblacksmith.core.model.GameState
import com.tinyblacksmith.core.model.MaterialId

/**
 * Where today's stock of a limited material comes from, beyond the supplier's own: the Caravan Ties legacy track, the
 * ore merchant in town this morning, or a caravan that did not arrive. Read from the save and the config; empty for a
 * material that is always in stock.
 */
/** When the deep road brings [id] (`DeepRoad`), for a material it does not bring yet; null once it is carried. */
fun GameEngine.roadLine(state: GameState, id: MaterialId): String? {
    if (onTheRoad(state, id)) return null
    val opens = state.deepRoadOpenDay?.takeIf { it > state.day && roadNeeds(id) == 1 }
    return when {
        opens != null -> "The caravan master's train brings it on day $opens."
        roadNeeds(id) == 1 -> "The supplier carries it once the deep road opens, after the first siege."
        else -> "The supplier carries it after the second siege."
    } + " Before that it comes only from dangerous work."
}

fun GameEngine.supplyNotes(state: GameState, m: MaterialDef): List<String> {
    if (m.dailySupplierStock == null) return emptyList()
    val ties = upgradeTotal(state.legacy, UpgradeEffect.CATALOG_ACCESS) * config.legacyTracks.catalogStockPerLevel
    return listOfNotNull(
        // What the deep road does not bring yet (rules 7): when it will, and where it comes from before that.
        roadLine(state, m.id),
        "Caravan delayed. No delivery today.".takeIf { state.worldFlags[WorldEvents.FLAG_CARAVAN_DELAYED] == state.day },
        "Caravan Ties: $ties more each day".takeIf { ties > 0 },
        "Ore merchant in town: ${config.worldEvents.oreMerchantStock} more today".takeIf { state.worldFlags[WorldEvents.FLAG_ORE_MERCHANT + m.id.value] == state.day },
    )
}

/** "Iron, Bronze and Silver". */
internal fun andList(names: List<String>): String = if (names.size <= 1) names.joinToString() else names.dropLast(1).joinToString(", ") + " and " + names.last()

/** The metals that stop at Rare and those that can reach Epic (`LegendConfig.fineCoreTier`), by name: "Iron, Bronze, Silver and Obsidian stop at Rare. Starsteel and Moonsteel can reach Epic." */
internal fun GameEngine.metalRarityLine(): String {
    val (epic, rare) = content.materials(MaterialCategory.CORE).sortedBy { it.tier }.partition { Forge.holdsEpic(it.tier, config) }
    return listOfNotNull(rare.takeIf { it.isNotEmpty() }?.let { "${andList(it.map { m -> m.name })} stop at Rare." }, epic.takeIf { it.isNotEmpty() }?.let { "${andList(it.map { m -> m.name })} can reach Epic." }).joinToString(" ")
}

/**
 * The supplier's mood after what the guild did for them or left undone, and the last morning it holds: limited materials
 * restock by one more or one fewer each morning (`Missions.supplierStockDelta`). Null while the supplier has no mood.
 */
fun GameEngine.supplierMood(state: GameState): String? {
    val mood = state.consequences.firstOrNull { (it.kind == ConsequenceKind.SUPPLIER_GRATEFUL || it.kind == ConsequenceKind.SUPPLIER_SHAKEN) && state.day <= it.dueDay } ?: return null
    return if (mood.kind == ConsequenceKind.SUPPLIER_GRATEFUL) "The supplier is grateful. ${config.guild.supplierGratefulStock} more of every limited material each morning, until day ${mood.dueDay}."
    else "The supplier is shaken. ${config.guild.supplierShakenStock} fewer of every limited material each morning, until day ${mood.dueDay}."
}

/** A tool's level and what the next one costs (`GameEngine.toolCost`; null once there is no next level). */
internal fun toolLine(level: Int, maxLevel: Int, cost: Int?): String = "Level $level of $maxLevel · " + (cost?.let { "$it gold" } ?: "fully built")

/**
 * What a limited material's row says of today's stock: "Sold out today" (a warning: it is why Buy is grey) or "n left
 * today". Null for a material that is always in stock, and for one the deep road does not carry yet: its note says when.
 */
internal fun stockFact(stock: Int?, onTheRoad: Boolean): Fact? = when {
    stock == null || !onTheRoad -> null
    stock == 0 -> Fact("Sold out today", tone = FactTone.WARN)
    else -> Fact("$stock left today")
}

/** The test tag of the cards the supplies open, and the prefix of their parts ("supplies_inspect_close"). */
internal const val SUPPLY_CARD = "supplies_inspect"

/** A workshop tool as an inspect card: what it does in the catalog's words, the level it is at and what the next level costs. */
internal fun toolTip(t: ToolDef, level: Int, cost: Int?, tag: String = SUPPLY_CARD): TipUi = TipUi(
    name = t.name, kind = "Workshop tool",
    stats = listOf(TipStat("Level", "$level of ${t.maxLevel}"), TipStat("Next level", cost?.let { "$it gold" } ?: "Fully built")),
    sections = listOf(TipSection(null, listOf(TipLine(t.description)))),
    note = "Tools last for the current era.",
    tag = tag,
)

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
    // Buy goes under the row's words when beside it the words would keep less than 150 dp (scaled with the text),
    // measured on each row's own width.
    val density = LocalDensity.current
    val buyWidth = buttonWidthPx("Buy")
    val minText = with(density) { 150.dp.toPx() * fontScale }
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
                Secondary("${andList(content.materials(MaterialCategory.CORE).filter { it.dailySupplierStock == null }.map { it.name })} are always in stock.")
                vm.engine.supplierMood(st)?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = Gold, modifier = Modifier.padding(top = Space.xs).testTag("supplies_mood")) }
            }
        }
        groups.forEach { (category, group) ->
            item(key = "supplier_${category.name}") {
                Column {
                    SectionHeader(when (category) { MaterialCategory.CORE -> "Metals";MaterialCategory.AUGMENT -> "Infusions"; MaterialCategory.CATALYST -> "Catalysts" })
                    if (category == MaterialCategory.CORE) Secondary(vm.engine.metalRarityLine())
                }
            }
            items(group, key = { "material_${it.id.value}" }) { m ->
                val price = vm.engine.materialPrice(st, m.id)
                val stock = st.supplierStock[m.id]
                val have = st.materials[m.id] ?: 0
                val needed = m.id == focus
                // A greyed Buy says why to a screen reader; on screen the row's own lines do (the price, "Sold out today", the road).
                val blocked = when { stock == 0 && !vm.engine.onTheRoad(st, m.id) -> "it comes down the deep road later in the era"; stock == 0 -> "none left today"; st.gold < price -> "it costs $price gold and you have ${st.gold}"; else -> null }
                val buy: @Composable (Modifier) -> Unit = { mod ->
                    SecondaryActionButton(
                        "Buy", { vm.dispatch(Command.BuyMaterial(m.id, 1)) },
                        mod.testTag("buy_${m.id.value}").semantics { contentDescription = blocked?.let { "Buy ${m.name}, unavailable: $it" } ?: "Buy ${m.name} for $price gold" },
                        enabled = blocked == null && !s.busy,
                    )
                }
                // V3: the row is for buying (art, name, what is owned, the price, what is left today, Buy); what the
                // material is and does (its rarity cap, its rule in a fight, the besieger's view of it) is on its card.
                Column(
                    // The row the Forge asked for: a gold line and its own words, never the colour alone.
                    Modifier.fillMaxWidth().then(if (needed) Modifier.padding(vertical = Space.xs).border(1.dp, Gold, MaterialTheme.shapes.small).padding(Space.xs) else Modifier.hairline())
                        .padding(vertical = Space.xs).testTag("material_${m.id.value}"),
                ) {
                    BoxWithConstraints(Modifier.fillMaxWidth()) {
                        val art = Sprites.material(m.id)
                        val artWidth = if (art != null) wholePixelDp(48, 40.dp) else null
                        // Fixed: the picture, the "i" and the gaps between them and the words; the trade is Buy and its gap.
                        val stack = with(density) {
                            val fixed = (artWidth?.let { it + 12.dp } ?: 0.dp).toPx() + (48 + 12).dp.toPx()
                            !roomBeside(constraints.maxWidth.toFloat(), fixed, buyWidth + 12.dp.toPx(), minText)
                        }
                        Column(Modifier.fillMaxWidth()) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                if (art != null && artWidth != null) PixelImage(art, artWidth, description = null)
                                Column(Modifier.weight(1f)) {
                                    if (needed) Text("Needed on the workbench", style = MaterialTheme.typography.labelSmall, color = Gold)
                                    Text(m.name, style = MaterialTheme.typography.titleSmall)
                                    FactLine(listOfNotNull(
                                        Fact("$have", "Owned"), Fact("$price gold"),
                                        Fact("${Forge.qualityCap(m.tier, vm.engine.config)}", "Quality cap").takeIf { m.category == MaterialCategory.CORE },
                                        stockFact(stock, vm.engine.onTheRoad(st, m.id)),
                                    ))
                                    // Why a limited material is short or comes later stays on the row: it is why Buy may be grey.
                                    vm.engine.supplyNotes(st, m).forEach { Secondary(it) }
                                }
                                InfoButton(m.name, { vm.engine.materialTip(st, m.id, s.shop.threat, tag = SUPPLY_CARD) }, tag = "material_${m.id.value}_info")
                                if (!stack) buy(Modifier)
                            }
                            if (stack) buy(Modifier.fillMaxWidth().padding(top = Space.xs))
                        }
                    }
                }
            }
        }
        item(key = "tools") {
            Column {
                SectionHeader("Workshop tools")
                Secondary("Buy with gold. Tools last for the current era.")
            }
        }
        items(content.tools, key = { "tool_${it.id}" }) { t ->
            val cost = vm.engine.toolCost(st, t.id)
            val level = st.tools[t.id] ?: 0
            // A tool's row is a material's row: the name and its level, what the next level costs, the same "Buy"; what it
            // does is on its card.
            Column(Modifier.fillMaxWidth().hairline().padding(vertical = Space.xs).testTag("tool_${t.id}")) {
                BoxWithConstraints(Modifier.fillMaxWidth()) {
                    // Fixed: the "i" and its gap; the trade is Buy and its gap.
                    val stack = with(density) { !roomBeside(constraints.maxWidth.toFloat(), (48 + 12).dp.toPx(), buyWidth + 12.dp.toPx(), minText) }
                    Column(Modifier.fillMaxWidth()) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Column(Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                                    Text(t.name, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f, fill = false))
                                    LevelDots(level, t.maxLevel)
                                }
                                Secondary(toolLine(level, t.maxLevel, cost))
                            }
                            InfoButton(t.name, { toolTip(t, level, cost) }, tag = "tool_${t.id}_info")
                            if (cost != null && !stack) ToolBuy(t, cost, st.gold, s.busy, Modifier) { vm.dispatch(Command.BuyTool(t.id)) }
                        }
                        if (cost != null && stack) ToolBuy(t, cost, st.gold, s.busy, Modifier.fillMaxWidth().padding(top = Space.xs)) { vm.dispatch(Command.BuyTool(t.id)) }
                    }
                }
            }
        }
        if (onClose != null) item(key = "close") {
            SecondaryActionButton("Close", onClose, Modifier.fillMaxWidth().padding(top = Space.md).testTag("supplies_close"))
        }
    }
}

/** A tool's "Buy": greyed while the purse is short, and then it says why to a screen reader. */
@Composable
private fun ToolBuy(t: ToolDef, cost: Int, gold: Int, busy: Boolean, modifier: Modifier, onBuy: () -> Unit) {
    SecondaryActionButton(
        "Buy", onBuy,
        modifier.testTag("buy_tool_${t.id}").semantics { contentDescription = if (gold >= cost) "Buy ${t.name} for $cost gold" else "Buy ${t.name}, unavailable: it costs $cost gold and you have $gold" },
        enabled = gold >= cost && !busy,
    )
}
