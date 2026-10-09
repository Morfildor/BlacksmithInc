package com.example.blacksmithproject.ui.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.blacksmithproject.ui.Labels
import com.example.blacksmithproject.ui.PixelImage
import com.example.blacksmithproject.ui.Secondary
import com.example.blacksmithproject.ui.Sprites
import com.example.blacksmithproject.ui.theme.Space
import com.tinyblacksmith.core.engine.Command
import com.tinyblacksmith.core.engine.GameEngine
import com.tinyblacksmith.core.model.AffixId
import com.tinyblacksmith.core.model.GameState
import com.tinyblacksmith.core.model.HeroId
import com.tinyblacksmith.core.model.Weapon
import com.tinyblacksmith.core.model.WeaponId
import com.tinyblacksmith.core.model.WeaponLocation
import com.tinyblacksmith.core.model.WeaponSnapshot

/** An affix or a flaw with the sentence that says what it does. */
@Immutable
data class Property(val name: String, val description: String)

/** What the shop may do with a blade it holds, with the labels that state each cost. Null on [ItemDetail] for a blade that is not in the shop. */
@Immutable
data class Stock(
    val listedPrice: Int?,
    val suggestedPrice: Int,
    val salvage: String,
    val hone: String,
    val canHone: Boolean,
    val donate: String,
    val canDonate: Boolean,
)

/** A stock change the player asked for. The sheet only reports it; the caller turns it into a command. */
sealed interface StockAction {
    data class ListAt(val price: Int) : StockAction
    data class SetPrice(val price: Int) : StockAction
    data object Unlist : StockAction
    data object Salvage : StockAction
    data object Hone : StockAction
    data object Donate : StockAction
}

/** The engine command a stock change stands for. */
fun StockAction.toCommand(weaponId: WeaponId): Command = when (this) {
    is StockAction.ListAt -> Command.ToggleShelf(weaponId, true, price)
    is StockAction.SetPrice -> Command.SetPrice(weaponId, price)
    StockAction.Unlist -> Command.ToggleShelf(weaponId, false)
    StockAction.Salvage -> Command.Salvage(weaponId)
    StockAction.Hone -> Command.Hone(weaponId)
    StockAction.Donate -> Command.DonateWeapon(weaponId)
}

/**
 * A blade as the sheet shows it. When a visit snapshot was supplied and the blade has changed since (or is gone),
 * [counter] holds the blade as it lay on the counter and [now] where it is and what is different today; otherwise
 * [counter] is empty and [now] is the whole blade.
 */
@Immutable
data class ItemDetail(
    val weaponId: WeaponId,
    val name: String,
    /** Drawables of the blade and of its rarity pip; the rarity is always also a word in the facts. */
    val sprite: Int,
    val badge: Int,
    val signature: Boolean,
    /** "Spear", or "Frost spear". */
    val kind: String,
    val counter: List<Fact>,
    val now: List<Fact>,
    val affixes: List<Property>,
    val flaws: List<Property>,
    val recipe: List<Fact>,
    /** Newest first. */
    val history: List<String>,
    val stock: Stock?,
)

/**
 * Builds the sheet for [weaponId] from the save, and from [snapshot] (a visit record) when one is supplied. Null only
 * when there is neither: a blade pruned from the save still opens from its snapshot, with the recipe the snapshot
 * kept and whatever the event log still says about it.
 */
fun GameEngine.itemDetail(state: GameState, weaponId: WeaponId, snapshot: WeaponSnapshot? = null): ItemDetail? {
    val weapon = state.weapons[weaponId]
    val present = weapon?.let { WeaponSnapshot.of(it) }
    val first = snapshot ?: present ?: return null
    val differs = snapshot != null && snapshot != present
    val shown = present ?: first     // properties and the picture follow the blade of today while it exists
    val family = content.familyById[shown.familyId]?.name ?: shown.familyId.value
    val inShop = weapon != null && (weapon.isListed || weapon.isInStorage)
    return ItemDetail(
        weaponId = weaponId,
        name = shown.name,
        sprite = Sprites.weapon(shown.familyId, shown.coreId, shown.element, shown.rarity, signature = shown.signatureId != null),
        badge = Sprites.badge(shown.rarity),
        signature = shown.signatureId != null,
        kind = shown.element?.let { "${it.name.lowercase().replaceFirstChar { c -> c.uppercase() }} ${family.lowercase()}" } ?: family,
        counter = if (differs) facts(first) else emptyList(),
        now = when {
            weapon == null -> listOf(Fact("Where", "No longer in the forge's records"))
            differs -> listOf(whereabouts(state, weapon)) + changed(facts(first), facts(present!!))
            else -> listOf(whereabouts(state, weapon)) + facts(present!!)
        },
        affixes = shown.affixes.map { property(it) },
        flaws = shown.flaws.map { property(it) },
        recipe = recipe(shown, weapon),
        history = weapon?.history?.asReversed()?.map { dated(state, it.era, it.day, it.text) }
            ?: recordsOf(state, weaponId.value).asReversed().map { dated(state, it.era, it.day, it.text) },
        stock = if (inShop) stock(state, weapon!!) else null,
    )
}

private fun GameEngine.property(id: AffixId) = content.affixById[id]?.let { Property(it.name, it.description) } ?: Property(id.value, "")

private fun facts(w: WeaponSnapshot): List<Fact> = listOfNotNull(
    Fact("Rarity", Labels.rarity(w.rarity)),
    Fact("Quality", "${Labels.quality(w.quality).replaceFirstChar { it.uppercase() }} (${w.quality})"),
    Fact("Condition", when { w.condition < 40 -> "Battered"; w.condition < 70 -> "Worn"; else -> "Sound" }),
    Fact("Renown", Labels.fame(w.fame)?.replaceFirstChar { it.uppercase() } ?: "Unsung"),
    w.title?.let { Fact("Title", "\"$it\"") },
)

/** Where the blade is today; the holder's line opens their sheet while they are still in the save. */
private fun whereabouts(state: GameState, w: Weapon): Fact = when (val l = w.location) {
    WeaponLocation.Storage -> Fact("Where", "In your storage")
    is WeaponLocation.Shelf -> Fact("Where", "On your shelf, asking ${l.price} gold")
    is WeaponLocation.Owned -> state.heroes[l.heroId].let { h ->
        Fact(if (l.equipped) "Carried by" else "Kept by", h?.fullName ?: "A hero no longer in the records", heroId = h?.id)
    }
    is WeaponLocation.Lost -> Fact("Where", "${l.reason.replaceFirstChar { it.uppercase() }} since day ${l.day}")
    is WeaponLocation.Destroyed -> Fact("Where", "Destroyed on day ${l.day}")
}

/** What it was made of. A snapshot kept the family, core and augment; the catalyst, method and day need the blade itself. */
private fun GameEngine.recipe(shown: WeaponSnapshot, w: Weapon?): List<Fact> {
    fun material(id: com.tinyblacksmith.core.model.MaterialId) = content.materialById[id]?.name ?: id.value
    return listOfNotNull(
        Fact("Family", content.familyById[shown.familyId]?.name ?: shown.familyId.value),
        Fact("Core", material(shown.coreId)),
        Fact("Augment", material(shown.augmentId)),
        w?.catalystId?.let { Fact("Catalyst", material(it)) },
        w?.let { Fact("Method", "${it.mode.name.lowercase().replaceFirstChar { c -> c.uppercase() }} forge, ${it.risk.name.lowercase()} risk" + if (it.honed) ", honed" else "") },
        w?.let { Fact("Forged", "Era ${it.forgedEra}, day ${it.forgedDay}") },
        shown.signatureId?.let { Fact("Signature", w?.history?.firstOrNull { it.kind == "SIGNATURE" }?.text ?: "A signature work") },
    )
}

private fun GameEngine.stock(state: GameState, w: Weapon): Stock {
    val core = content.materialById[w.coreId]?.name ?: w.coreId.value
    val room = config.armoryMax - state.town.armory
    return Stock(
        listedPrice = w.listedPrice,
        suggestedPrice = suggestedPrice(w),
        salvage = "Salvage (${config.salvageEnergy} energy, returns 1 $core)",
        hone = if (!w.canBeHoned) "Honed" else "${if (w.honed) "Re-hone" else "Hone"} (${config.honeEnergy} energy, 1 $core)",
        canHone = w.canBeHoned,
        donate = if (room > 0) "Arm the watch (+${minOf(armoryValue(w), room)} defense)" else "Arm the watch (armory full)",
        canDonate = room > 0,
    )
}

/**
 * The blade sheet as a modal bottom sheet. Stock can be changed only while [planning] is true; during the shop day the
 * same sheet is read-only. [enabled] greys the stock buttons while a command is being saved.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ItemDetailSheet(
    detail: ItemDetail,
    planning: Boolean,
    onOpenHero: (HeroId) -> Unit,
    onStock: (StockAction) -> Unit,
    onDismiss: () -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
) {
    // A sheet is its own window: it does not inherit the root's resource-id exposure that the emulator scripts rely on.
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        dragHandle = { BottomSheetDefaults.DragHandle(width = 48.dp) },   // the handle is a target too: 48 dp, not the default 32
        modifier = modifier.semantics { testTagsAsResourceId = true }.testTag("item_sheet"),
    ) { ItemDetailContent(detail, planning, onOpenHero, onStock, onDismiss, enabled) }
}

/** The body of the blade sheet: one scrolling column, usable outside a sheet (the shop day's overlay, tests). */
@Composable
fun ItemDetailContent(
    detail: ItemDetail,
    planning: Boolean,
    onOpenHero: (HeroId) -> Unit,
    onStock: (StockAction) -> Unit,
    onDismiss: () -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(horizontal = Space.md).padding(bottom = Space.lg)) {
        Row(Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.md)) {
            Box(Modifier.size(72.dp).drawBehind { if (detail.signature) drawCircle(Sprites.signatureRing, radius = size.minDimension / 2 - 1.dp.toPx(), style = Stroke(2.dp.toPx())) }) {
                PixelImage(detail.sprite, 72.dp, description = null)
                PixelImage(detail.badge, 24.dp, description = null)
            }
            Column(Modifier.weight(1f)) {
                Text(detail.name, style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
                Secondary(detail.kind + if (detail.signature) " · signature work" else "")
            }
        }
        FactBlock(AT_THE_COUNTER.takeIf { detail.counter.isNotEmpty() }, detail.counter, "sheet_counter", onOpenHero, onOpenItem = {})
        FactBlock(NOW.takeIf { detail.counter.isNotEmpty() }, detail.now, "sheet_now", onOpenHero, onOpenItem = {})

        // The price comes before the lore: on a blade of the shop it is what the player came to change.
        detail.stock?.let { stock ->
            SheetSection("Price")
            if (planning) StockEditor(detail.weaponId, stock, enabled, onStock)
            else {
                Text(stock.listedPrice?.let { "Asking $it gold." } ?: "In storage, not for sale.", style = MaterialTheme.typography.bodyMedium)
                Secondary("Suggested price ${stock.suggestedPrice} gold. Stock can be changed once the shop day is over.")
            }
        }

        SheetSection("Properties")
        if (detail.affixes.isEmpty()) Secondary("No special properties.")
        detail.affixes.forEach { PropertyRow(it, flaw = false) }
        if (detail.flaws.isNotEmpty()) {
            SheetSection("Flaws")
            detail.flaws.forEach { PropertyRow(it, flaw = true) }
        }

        FactBlock("Recipe", detail.recipe, "sheet_recipe", onOpenHero, onOpenItem = {})

        SheetSection("History")
        Lines(detail.history, "Its story has not been written yet.")
        OutlinedButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth().padding(top = Space.md).heightIn(min = 48.dp).testTag("sheet_close")) { Text("Close") }
    }
}

/** Name and what it does. A flaw carries the flaw pip, and the section heading says "Flaws" in words. */
@Composable
private fun PropertyRow(p: Property, flaw: Boolean) {
    Column(Modifier.fillMaxWidth().padding(vertical = Space.xs).semantics(mergeDescendants = true) {}) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
            if (flaw) PixelImage(Sprites.badgeFlaw, 16.dp, description = null)
            Text(p.name, style = MaterialTheme.typography.titleSmall)
        }
        if (p.description.isNotEmpty()) Secondary(p.description)
    }
}

/** Price and the stock actions of the planning phase. The typed price is the only state the sheet keeps. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StockEditor(weaponId: WeaponId, stock: Stock, enabled: Boolean, onStock: (StockAction) -> Unit) {
    var priceText by rememberSaveable(weaponId.value, stock.listedPrice) { mutableStateOf((stock.listedPrice ?: stock.suggestedPrice).toString()) }
    val price = priceText.toIntOrNull() ?: 0
    Secondary(stock.listedPrice?.let { "Asking $it gold. Suggested price ${stock.suggestedPrice} gold." } ?: "In storage. Suggested price ${stock.suggestedPrice} gold.")
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.sm), modifier = Modifier.fillMaxWidth().padding(top = Space.sm)) {
        StepButton("−10", "Lower price by 10") { priceText = (price - 10).coerceAtLeast(0).toString() }
        OutlinedTextField(
            value = priceText,
            onValueChange = { priceText = it.filter { c -> c.isDigit() }.take(6) },
            label = { Text("Price") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.weight(1f).testTag("item_price"),
        )
        StepButton("+10", "Raise price by 10") { priceText = (price + 10).coerceAtMost(999999).toString() }
    }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.sm), modifier = Modifier.padding(top = Space.sm)) {
        if (stock.listedPrice != null) {
            Button(onClick = { onStock(StockAction.SetPrice(price)) }, enabled = enabled, modifier = Modifier.heightIn(min = 48.dp).testTag("item_set_price")) { Text("Set price") }
            OutlinedButton(onClick = { onStock(StockAction.Unlist) }, enabled = enabled, modifier = Modifier.heightIn(min = 48.dp).testTag("item_unlist")) { Text("Unlist") }
        } else {
            Button(onClick = { onStock(StockAction.ListAt(price)) }, enabled = enabled, modifier = Modifier.heightIn(min = 48.dp).testTag("item_list")) { Text("List at $price") }
        }
    }
    TextButton(onClick = { onStock(StockAction.Salvage) }, enabled = enabled, modifier = Modifier.heightIn(min = 48.dp).testTag("item_salvage")) { Text(stock.salvage) }
    TextButton(onClick = { onStock(StockAction.Hone) }, enabled = enabled && stock.canHone, modifier = Modifier.heightIn(min = 48.dp).testTag("item_hone")) { Text(stock.hone) }
    TextButton(onClick = { onStock(StockAction.Donate) }, enabled = enabled && stock.canDonate, modifier = Modifier.heightIn(min = 48.dp).testTag("item_donate")) { Text(stock.donate) }
}

@Composable
private fun StepButton(label: String, description: String, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        contentPadding = PaddingValues(4.dp),
        modifier = Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp).semantics { contentDescription = description },
    ) { Text(label) }
}
