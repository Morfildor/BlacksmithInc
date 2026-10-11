package com.example.blacksmithproject.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.unit.dp
import com.example.blacksmithproject.ui.detail.StockAction
import com.example.blacksmithproject.ui.theme.Gold
import com.example.blacksmithproject.ui.theme.Space
import com.tinyblacksmith.core.model.Rarity
import com.tinyblacksmith.core.model.WeaponId

/** The order Storage is read in. STORED is the save's own order, the one the list had before it could be sorted. */
enum class StorageSort(val label: String) { STORED("As stored"), STRONGEST("Strongest"), WEAKEST("Weakest"), DEAREST("Highest value") }

/** Which stored blades are shown and in what order. [family] is a family's name and [rarity] a rarity, null for any; [unsold]: only blades no hero has carried. */
data class StorageFilter(val family: String? = null, val rarity: Rarity? = null, val unsold: Boolean = false, val sort: StorageSort = StorageSort.STORED)

/** The stored blades [filter] lets through, in its order. Reads the rows' own fields; a sort keeps the stored order among equals. */
fun List<StockUi>.shown(filter: StorageFilter): List<StockUi> {
    val kept = filter { (filter.family == null || it.family == filter.family) && (filter.rarity == null || it.weapon.rarity == filter.rarity) && (!filter.unsold || it.unsold) }
    return when (filter.sort) {
        StorageSort.STORED -> kept
        StorageSort.STRONGEST -> kept.sortedByDescending { it.weapon.power }
        StorageSort.WEAKEST -> kept.sortedBy { it.weapon.power }
        StorageSort.DEAREST -> kept.sortedByDescending { it.suggested }
    }
}

/**
 * What the two bulk actions cost today, for their confirmations: what one salvage costs, the energy and overwork left,
 * and the room left in the watch's armory. Read from the save and the balance config; nothing here decides an outcome.
 */
@Immutable data class BulkTerms(val salvageEnergy: Int, val energy: Int, val overworkLeft: Int, val armoryRoom: Int)

private fun blades(n: Int) = if (n == 1) "1 weapon" else "$n weapons"

/** The body of "Salvage n blades?": what it costs against what is left, and where it will stop. */
internal fun salvageTerms(n: Int, t: BulkTerms): String {
    val cost = n * t.salvageEnergy
    return "Destroys each weapon and returns one unit of its metal. Costs $cost energy. You have ${t.energy}." +
        if (cost > t.energy) "Extra energy is borrowed from tomorrow. You can borrow ${t.overworkLeft} more today. Salvaging stops when energy and overwork run out." else ""
}

/** The body of "Give n blades to the town watch?". */
internal fun donateTerms(t: BulkTerms): String =
    "Gives these weapons to the town watch permanently. The armory can gain ${t.armoryRoom} more defense before it's full."

private val FilterSaver = listSaver<StorageFilter, String>(
    save = { listOf(it.family.orEmpty(), it.rarity?.name.orEmpty(), it.unsold.toString(), it.sort.name) },
    restore = { v -> StorageFilter(v[0].ifEmpty { null }, Rarity.entries.firstOrNull { it.name == v[1] }, v[2].toBoolean(), StorageSort.entries.firstOrNull { it.name == v[3] } ?: StorageSort.STORED) },
)

/**
 * Storage as a bottom sheet over the Shop. A blade opened from the list is shown in this same sheet ([detail], under a
 * "Storage" row that goes back, as system Back does); the list keeps its filters, its order and its place meanwhile.
 * [notice] is what the last stock change did. [loans] (a guild run) are the blades out on loan: listed apart, under the
 * stock, never among the rows a bulk action can choose.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StorageSheet(
    storage: List<StockUi>, shelfFree: Int, busy: Boolean, onOpenBlade: (WeaponId) -> Unit, onList: (WeaponId, Int) -> Unit, onDismiss: () -> Unit, modifier: Modifier = Modifier,
    terms: BulkTerms? = null, onBulk: (StockAction, List<WeaponId>) -> Unit = { _, _ -> },
    scrapBack: (List<WeaponId>) -> String = { "" }, onScrap: (List<WeaponId>) -> Unit = {},
    // Remembered here, in the composition that opens the sheet, not inside the sheet's own window: there the order did
    // not come back when the sheet was restored (ShopDayPersistenceTest). Closing the sheet still forgets it.
    filterState: MutableState<StorageFilter> = rememberSaveable(stateSaver = FilterSaver) { mutableStateOf(StorageFilter()) },
    notice: String? = null, detail: (@Composable () -> Unit)? = null, onBack: () -> Unit = {},
    loans: List<LoanRowUi> = emptyList(),
) {
    val listState = rememberLazyListState()
    // A sheet is its own window: it does not inherit the root's resource-id exposure that the emulator scripts rely on.
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        dragHandle = { BottomSheetDefaults.DragHandle(width = 48.dp) },
        modifier = modifier.semantics { testTagsAsResourceId = true }.testTag("storage_sheet"),
    ) {
        Column {
            if (detail != null) {
                BackHandler(onBack = onBack)
                BackRow("Storage", onBack)
            }
            notice?.let { NoticeLine(it) }
            if (detail != null) detail() else StorageList(storage, shelfFree, busy, onOpenBlade, onList, terms = terms, onBulk = onBulk, scrapBack = scrapBack, onScrap = onScrap, filterState = filterState, listState = listState, onClose = onDismiss, loans = loans)
        }
    }
}

/**
 * The stored blades as one lazy, keyed list: a storeroom of thousands composes only the rows in view. Its head narrows
 * the list (family, rarity, never sold) and orders it. With [terms], "Select weapons" turns the rows into checkboxes and
 * a bar under the list salvages the chosen blades or gives them to the watch: each asks once, then [onBulk] gets the
 * action and the blades in the order shown. Only blades that are both chosen and shown are acted on. "Scrap" clears
 * the chosen blades in one command ([onScrap]); [scrapBack] is the engine's word on what comes back for them.
 * [loans] close the list as their own group, "On loan": blade and holder, a tap opens the blade.
 */
@Composable
fun StorageList(
    storage: List<StockUi>, shelfFree: Int, busy: Boolean, onOpenBlade: (WeaponId) -> Unit, onList: (WeaponId, Int) -> Unit, modifier: Modifier = Modifier,
    terms: BulkTerms? = null, onBulk: (StockAction, List<WeaponId>) -> Unit = { _, _ -> },
    scrapBack: (List<WeaponId>) -> String = { "" }, onScrap: (List<WeaponId>) -> Unit = {},
    filterState: MutableState<StorageFilter> = rememberSaveable(stateSaver = FilterSaver) { mutableStateOf(StorageFilter()) },
    listState: LazyListState = rememberLazyListState(),
    /** When given, "Close" stands beside the title: a storeroom can be far too long to end with it. */
    onClose: (() -> Unit)? = null,
    loans: List<LoanRowUi> = emptyList(),
) {
    var filter by filterState
    var scrapping by remember { mutableStateOf(false) }
    var selecting by rememberSaveable { mutableStateOf(false) }
    // Not saved: thousands of IDs do not belong in a saved-state Bundle. A restored process starts with nothing chosen.
    var picked by remember { mutableStateOf(emptySet<WeaponId>()) }
    var asking by remember { mutableStateOf<StockAction?>(null) }
    val shown = remember(storage, filter) { storage.shown(filter) }
    // A blade kept for an order can be neither salvaged, scrapped nor given away: it is never among the chosen.
    val free = remember(shown) { shown.filter { it.promised == null && !it.weapon.isLoaned }.map { it.weapon.id } }
    val chosen = remember(free, picked) { free.filter { it in picked } }
    val select = selecting && terms != null

    Column(modifier.fillMaxWidth().navigationBarsPadding()) {
        LazyColumn(Modifier.weight(1f, fill = false).fillMaxWidth().testTag("storage_list"), state = listState, contentPadding = PaddingValues(start = Space.md, end = Space.md, bottom = Space.lg)) {
            item(key = "head") {
                Column(Modifier.padding(bottom = Space.sm)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(if (shown.size == storage.size) "Storage · ${storage.size}" else "Storage · ${shown.size} of ${storage.size}", style = MaterialTheme.typography.titleLarge, color = Gold, modifier = Modifier.weight(1f).semantics { heading() })
                        if (onClose != null) InlineActionButton("Close", onClose, Modifier.testTag("storage_close"))
                    }
                    Secondary(
                        when {
                            storage.isEmpty() -> "Storage is empty. Keep forged weapons here until you're ready to sell them."
                            select -> "Tap weapons to select them. ${blades(chosen.size)} selected."
                            shelfFree <= 0 -> "The shelf is full. Tap a weapon to hone, salvage or donate it."
                            else -> "$shelfFree shelf spaces free. Tap a weapon to see its details and set a price."
                        },
                    )
                    if (storage.size > 1) StorageFilters(storage, filter) { filter = it }
                    if (terms != null && storage.isNotEmpty()) Row(Modifier.padding(top = Space.xs), horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                        if (!selecting) SecondaryActionButton("Select weapons", { selecting = true }, Modifier.testTag("storage_select"))
                        else {
                            SecondaryActionButton("Select all shown (${free.size})", { picked = picked + free }, Modifier.weight(1f).testTag("storage_select_all"))
                            SecondaryActionButton("Done", { selecting = false; picked = emptySet() }, Modifier.testTag("storage_select_done"))
                        }
                    }
                    if (shown.isEmpty() && storage.isNotEmpty()) Secondary("No stored weapons match these filters.", Modifier.padding(top = Space.sm))
                }
            }
            items(shown, key = { "stock_${it.weapon.id.value}" }) { s ->
                val id = s.weapon.id
                if (select) StockRow(s, busy, onOpen = { picked = if (id in picked) picked - id else picked + id }, onList = null, selected = id in picked)
                else StockRow(s, busy || shelfFree <= 0, onOpen = { onOpenBlade(id) }, onList = { price -> onList(id, price) })
            }
            if (loans.isNotEmpty()) {
                item(key = "loans") {
                    Column(Modifier.testTag("storage_loans")) {
                        SectionHeader("On loan · ${loans.size}")
                        Secondary("Carried by members of the guild. Tap a weapon to see it or call it back.")
                    }
                }
                items(loans, key = { "loan_${it.weaponId.value}" }) { l ->
                    Column(
                        Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(onClickLabel = "Open ${l.blade}", role = Role.Button) { onOpenBlade(l.weaponId) }
                            .padding(vertical = Space.xs).testTag("storage_loan_${l.weaponId.value}").semantics(mergeDescendants = true) {},
                    ) {
                        Text(l.blade, style = MaterialTheme.typography.titleSmall)
                        Secondary("With ${l.holder}")
                    }
                }
            }
        }
        if (select) SecondaryActionButton("Scrap ${chosen.size} (no energy)", { scrapping = true }, Modifier.fillMaxWidth().padding(horizontal = Space.md).padding(top = Space.sm).testTag("storage_bulk_scrap"), enabled = !busy && chosen.isNotEmpty())
        if (select) Row(Modifier.fillMaxWidth().padding(horizontal = Space.md, vertical = Space.sm), horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
            SecondaryActionButton("Salvage ${chosen.size}", { asking = StockAction.Salvage }, Modifier.weight(1f).testTag("storage_bulk_salvage"), enabled = !busy && chosen.isNotEmpty())
            SecondaryActionButton("Arm the watch ${chosen.size}", { asking = StockAction.Donate }, Modifier.weight(1f).testTag("storage_bulk_donate"), enabled = !busy && chosen.isNotEmpty() && terms!!.armoryRoom > 0)
        }
    }
    if (scrapping) AlertDialog(
        modifier = Modifier.semantics { testTagsAsResourceId = true },
        onDismissRequest = { scrapping = false },
        title = { Text("Scrap ${blades(chosen.size)}?") },
        text = { Text("Scraps these weapons permanently. Costs no energy. " + scrapBack(chosen)) },
        confirmButton = { InlineActionButton("Scrap", { scrapping = false; onScrap(chosen); picked = emptySet() }, Modifier.testTag("storage_scrap_confirm")) },
        dismissButton = { InlineActionButton("Keep them", { scrapping = false }) },
    )
    val action = asking
    if (action != null && terms != null) {
        val salvage = action == StockAction.Salvage
        // A dialog is its own window: it does not inherit the root's resource-id exposure that the emulator scripts rely on.
        AlertDialog(
            modifier = Modifier.semantics { testTagsAsResourceId = true },
            onDismissRequest = { asking = null },
            title = { Text(if (salvage) "Salvage ${blades(chosen.size)}?" else "Give ${blades(chosen.size)} to the town watch?") },
            text = { Text(if (salvage) salvageTerms(chosen.size, terms) else donateTerms(terms)) },
            confirmButton = { InlineActionButton(if (salvage) "Salvage" else "Arm the watch", { asking = null; onBulk(action, chosen); picked = emptySet() }, Modifier.testTag("storage_bulk_confirm")) },
            dismissButton = { InlineActionButton("Keep them", { asking = null }) },
        )
    }
}

/** The filter and order chips: only the families and rarities that are in storage, each row scrolling sideways when it is long. A chosen chip tapped again lets everything through. */
@Composable
private fun StorageFilters(storage: List<StockUi>, filter: StorageFilter, onChange: (StorageFilter) -> Unit) {
    val families = remember(storage) { storage.map { it.family }.distinct() }
    val rarities = remember(storage) { storage.map { it.weapon.rarity }.distinct().sorted() }
    val unsold = remember(storage) { storage.count { it.unsold } }
    @Composable
    fun ChipRow(tag: String, content: @Composable () -> Unit) =
        Row(Modifier.horizontalScroll(rememberScrollState()).testTag(tag), horizontalArrangement = Arrangement.spacedBy(Space.sm), verticalAlignment = Alignment.CenterVertically) { content() }
    if (families.size > 1 || filter.family != null) ChipRow("storage_families") {
        families.forEach { f -> FilterChip(selected = filter.family == f, onClick = { onChange(filter.copy(family = f.takeIf { filter.family != f })) }, label = { Text(f) }, modifier = Modifier.heightIn(min = 48.dp)) }
    }
    if (rarities.size > 1 || filter.rarity != null) ChipRow("storage_rarities") {
        rarities.forEach { r -> FilterChip(selected = filter.rarity == r, onClick = { onChange(filter.copy(rarity = r.takeIf { filter.rarity != r })) }, label = { Text(Labels.rarity(r)) }, modifier = Modifier.heightIn(min = 48.dp)) }
    }
    ChipRow("storage_order") {
        if (unsold in 1 until storage.size || filter.unsold) FilterChip(selected = filter.unsold, onClick = { onChange(filter.copy(unsold = !filter.unsold)) }, label = { Text("Never sold") }, modifier = Modifier.heightIn(min = 48.dp).testTag("storage_unsold"))
        StorageSort.entries.forEach { o -> FilterChip(selected = filter.sort == o, onClick = { onChange(filter.copy(sort = o)) }, label = { Text(o.label) }, modifier = Modifier.heightIn(min = 48.dp)) }
    }
}
