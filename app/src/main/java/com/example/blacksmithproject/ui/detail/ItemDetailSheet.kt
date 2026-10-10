package com.example.blacksmithproject.ui.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.unit.dp
import com.example.blacksmithproject.ui.InlineActionButton
import com.example.blacksmithproject.ui.SecondaryActionButton
import com.example.blacksmithproject.ui.Labels
import com.example.blacksmithproject.ui.NoticeLine
import com.example.blacksmithproject.ui.PrimaryActionButton
import com.example.blacksmithproject.ui.Secondary
import com.example.blacksmithproject.ui.SecondaryActionButton
import com.example.blacksmithproject.ui.Sprites
import com.example.blacksmithproject.ui.theme.Space
import com.tinyblacksmith.core.content.Element
import com.tinyblacksmith.core.engine.Command
import com.tinyblacksmith.core.engine.GameEngine
import com.tinyblacksmith.core.model.AffixId
import com.tinyblacksmith.core.model.GameState
import com.tinyblacksmith.core.model.HeroId
import com.tinyblacksmith.core.model.Rarity
import com.tinyblacksmith.core.model.Weapon
import com.tinyblacksmith.core.model.WeaponId
import com.tinyblacksmith.core.model.WeaponLocation
import com.tinyblacksmith.core.model.WeaponSnapshot
import com.tinyblacksmith.core.shopday.Demand
import com.tinyblacksmith.core.shopday.Lines as ShopLines

/** An affix or a flaw with the sentence that says what it does. */
@Immutable
data class Property(val name: String, val description: String)

/**
 * One number of a blade exactly as core holds it. [max] is the bound core enforces on it (null when it has none), [word]
 * the band the number falls in, and [was] the same number on the visit snapshot when the blade has changed since.
 */
@Immutable
data class Stat(val label: String, val value: Int, val word: String? = null, val max: Int? = null, val was: Int? = null)

/** What the shop may do with a blade it holds, with the labels that state each cost. Null on [ItemDetail] for a blade that is not in the shop. */
@Immutable
data class Stock(
    val listedPrice: Int?,
    val suggestedPrice: Int,
    /** What each living hero can pay at the counter today (`Demand.funds`), for the count under the price. */
    val funds: List<Int>,
    /** Free slots on the shelf, of [slots] (`GameEngine.shelfSlots`): the engine refuses a listing when there is none. */
    val shelfFree: Int,
    val slots: Int,
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
    val rarity: Rarity,
    val element: Element?,
    val title: String?,
    /** Power, quality, condition and renown of the blade of today (of the snapshot once the blade is gone). */
    val stats: List<Stat>,
    val counter: List<Fact>,
    val now: List<Fact>,
    val affixes: List<Property>,
    val flaws: List<Property>,
    /** A returned legend's sleeping affixes and what wakes them (`Lines.dormant`); they are not among [affixes]. Null when nothing sleeps. */
    val dormant: String?,
    val recipe: List<Fact>,
    /** The maker's ledger (`Lines.story`): what is worth telling of the blade, oldest first. Empty once the blade has left the save. */
    val story: List<String>,
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
        rarity = shown.rarity,
        element = shown.element,
        title = shown.title,
        stats = weaponStats(shown, before = first.takeIf { differs && present != null }),
        counter = if (differs) facts(first) else emptyList(),
        now = when {
            weapon == null -> listOf(Fact("Where", "No longer in the forge's records"))
            differs -> listOf(whereabouts(state, weapon)) + changed(facts(first), facts(present!!))
            else -> listOf(whereabouts(state, weapon)) + facts(present!!)
        },
        affixes = shown.affixes.map { property(it) },
        flaws = shown.flaws.map { property(it) },
        dormant = ShopLines.dormant(shown.dormantAffixes, content),
        recipe = recipe(shown, weapon),
        story = weapon?.let { ShopLines.story(it, state.era) }.orEmpty(),
        history = weapon?.history?.asReversed()?.map { dated(state, it.era, it.day, it.text) }
            ?: recordsOf(state, weaponId.value).asReversed().map { dated(state, it.era, it.day, it.text) },
        stock = if (inShop) stock(state, weapon!!) else null,
    )
}

private fun GameEngine.property(id: AffixId) = content.affixById[id]?.let { Property(it.name, it.description) } ?: Property(id.value, "")

private fun qualityWord(w: WeaponSnapshot) = Labels.quality(w.quality).replaceFirstChar { it.uppercase() }
private fun conditionWord(w: WeaponSnapshot) = Labels.condition(w.condition)?.replaceFirstChar { it.uppercase() } ?: "Sound"
private fun renownWord(w: WeaponSnapshot) = Labels.fame(w.fame)?.replaceFirstChar { it.uppercase() } ?: "Unsung"

private fun facts(w: WeaponSnapshot): List<Fact> = listOfNotNull(
    Fact("Rarity", Labels.rarity(w.rarity)),
    Fact("Quality", "${qualityWord(w)} (${w.quality})"),
    Fact("Condition", conditionWord(w)),
    Fact("Renown", renownWord(w)),
    w.title?.let { Fact("Title", "\"$it\"") },
)

// Quality is 1..100 and condition 0..100 by the engine's invariants (core Invariants.kt); power and fame have no ceiling.
private const val QUALITY_MAX = 100
private const val CONDITION_MAX = 100

/** The numbers of [w] as stored; [before] is the visit snapshot of a blade that has changed since, for the "was" of each. The card and the stock rows both read this. */
internal fun weaponStats(w: WeaponSnapshot, before: WeaponSnapshot? = null): List<Stat> = listOf(
    Stat("Power", w.power, was = before?.power?.takeIf { it != w.power }),
    Stat("Quality", w.quality, qualityWord(w), QUALITY_MAX, before?.quality?.takeIf { it != w.quality }),
    Stat("Condition", w.condition, conditionWord(w), CONDITION_MAX, before?.condition?.takeIf { it != w.condition }),
    Stat("Renown", w.fame, renownWord(w), was = before?.fame?.takeIf { it != w.fame }),
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
        funds = Demand.funds(state, content, config),
        shelfFree = shelfSlots(state) - state.listedWeapons().size,
        slots = shelfSlots(state),
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
    /** What the last stock change did, said at the head of the sheet. */
    notice: String? = null,
) {
    // A sheet is its own window: it does not inherit the root's resource-id exposure that the emulator scripts rely on.
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        dragHandle = { BottomSheetDefaults.DragHandle(width = 48.dp) },   // the handle is a target too: 48 dp, not the default 32
        modifier = modifier.semantics { testTagsAsResourceId = true }.testTag("item_sheet"),
    ) {
        Column {
            notice?.let { NoticeLine(it) }
            ItemDetailContent(detail, planning, onOpenHero, onStock, onDismiss, enabled)
        }
    }
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
    closeLabel: String = "Close",
) {
    Column(modifier.fillMaxWidth().imePadding().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(horizontal = Space.md).padding(bottom = Space.lg)) {
        WeaponStatCard(detail)
        FactBlock(AT_THE_COUNTER.takeIf { detail.counter.isNotEmpty() }, detail.counter, "sheet_counter", onOpenHero, onOpenItem = {})
        // The card already says the whole blade; "then and now" in words stays only for a blade that has changed since.
        val onCard = setOf("Rarity", "Title") + detail.stats.map { it.label }
        FactBlock(NOW.takeIf { detail.counter.isNotEmpty() }, if (detail.counter.isEmpty()) detail.now.filterNot { it.label in onCard } else detail.now, "sheet_now", onOpenHero, onOpenItem = {})

        // The price comes before the lore: on a blade of the shop it is what the player came to change.
        detail.stock?.let { stock ->
            SheetSection("Price")
            if (planning) StockEditor(detail.weaponId, stock, enabled, onStock)
            else {
                Text(stock.listedPrice?.let { "Asking $it gold." } ?: "In storage, not for sale.", style = MaterialTheme.typography.bodyMedium)
                Secondary("Suggested price ${stock.suggestedPrice} gold. Stock can be changed once the shop day is over.")
            }
        }

        FactBlock("Recipe", detail.recipe, "sheet_recipe", onOpenHero, onOpenItem = {})

        // The story is what is worth telling, oldest first; the history under it is every record, newest first.
        if (detail.story.isNotEmpty()) {
            SheetSection("Story")
            Column(Modifier.testTag("sheet_story")) { Lines(detail.story, "") }
        }

        SheetSection("History")
        Column(Modifier.testTag("sheet_history")) { Lines(detail.history, "Its story has not been written yet.") }
        SecondaryActionButton(closeLabel, onDismiss, Modifier.fillMaxWidth().padding(top = Space.md).heightIn(min = 48.dp).testTag("sheet_close"))
    }
}

/** Price and the stock actions of the planning phase. The typed price is the only state the sheet keeps. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StockEditor(weaponId: WeaponId, stock: Stock, enabled: Boolean, onStock: (StockAction) -> Unit) {
    var priceText by rememberSaveable(weaponId.value, stock.listedPrice) { mutableStateOf((stock.listedPrice ?: stock.suggestedPrice).toString()) }
    // Where the blade is now, in plain text: after "List at" or "Set price" this line is what changes.
    Text(stock.listedPrice?.let { "On the shelf, asking $it gold." } ?: "In storage, not for sale.", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.testTag("item_where"))
    PriceEditor(priceText, { priceText = it }, stock.suggestedPrice, stock.funds, "item", Modifier.padding(top = Space.sm)) { price ->
        // The engine refuses a listing on a full shelf; the button says so before the tap.
        val full = stock.listedPrice == null && stock.shelfFree <= 0
        if (full) Text("${shelfFullLine(stock.slots)} Unlist another blade to make room.", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = Space.sm).testTag("item_shelf_full"))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.sm), modifier = Modifier.padding(top = Space.sm)) {
            if (stock.listedPrice != null) {
                PrimaryActionButton("Set price", { price?.let { onStock(StockAction.SetPrice(it)) } }, Modifier.testTag("item_set_price"), enabled && price != null)
                SecondaryActionButton("Unlist", { onStock(StockAction.Unlist) }, Modifier.heightIn(min = 52.dp).testTag("item_unlist"), enabled = enabled)
            } else {
                PrimaryActionButton(
                    price?.let { "List at $it" } ?: "List", { price?.let { onStock(StockAction.ListAt(it)) } },
                    Modifier.testTag("item_list").semantics { if (full) contentDescription = "List, unavailable: ${shelfFullLine(stock.slots)}" },
                    enabled && price != null && !full,
                )
            }
        }
    }
    InlineActionButton(stock.salvage, { onStock(StockAction.Salvage) }, Modifier.heightIn(min = 48.dp).testTag("item_salvage"), enabled = enabled)
    InlineActionButton(stock.hone, { onStock(StockAction.Hone) }, Modifier.heightIn(min = 48.dp).testTag("item_hone"), enabled = enabled && stock.canHone)
    InlineActionButton(stock.donate, { onStock(StockAction.Donate) }, Modifier.heightIn(min = 48.dp).testTag("item_donate"), enabled = enabled && stock.canDonate)
}
