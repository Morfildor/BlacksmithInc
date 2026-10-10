package com.example.blacksmithproject.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.blacksmithproject.ui.detail.WeaponStatLine
import com.example.blacksmithproject.ui.detail.rarityColor
import com.example.blacksmithproject.ui.shopday.BladeUi
import com.example.blacksmithproject.ui.shopday.CounterScene
import com.example.blacksmithproject.ui.shopday.PersonChip
import com.example.blacksmithproject.ui.shopday.ShelfBand
import com.example.blacksmithproject.ui.theme.BronzeDeep
import com.example.blacksmithproject.ui.theme.BuffGreen
import com.example.blacksmithproject.ui.theme.CreamMuted
import com.example.blacksmithproject.ui.theme.ForgeSlot
import com.example.blacksmithproject.ui.theme.Gold
import com.example.blacksmithproject.ui.theme.Space
import com.tinyblacksmith.core.model.CommissionId
import com.tinyblacksmith.core.model.HeroId
import com.tinyblacksmith.core.model.WeaponFamilyId
import com.tinyblacksmith.core.model.WeaponId
import com.tinyblacksmith.core.model.WeaponSnapshot

/**
 * The Shop destination: where the day is planned. From the top: the counter with the live shelf and how many it seats,
 * the one lead of the day, the requests, who is buying, yesterday at the counter, then the shelf rows and the way into
 * storage. One lazy list of keyed rows; data in, events out. [tip] is the first-run banner, shown with the shelf it is
 * about; [more] appends rows.
 */
@Composable
fun ShopPanel(
    shop: ShopUi,
    busy: Boolean,
    reducedMotion: Boolean,
    onLead: (LeadUi) -> Unit,
    onOpenBlade: (WeaponId) -> Unit,
    onOpenHero: (HeroId) -> Unit,
    onAnswer: (CommissionId, Boolean) -> Unit,
    onOpenStorage: () -> Unit,
    onOpenNews: () -> Unit,
    modifier: Modifier = Modifier,
    onForgeThis: (CommissionId) -> Unit = {},
    onForgeWant: (WeaponFamilyId) -> Unit = {},
    onOpenSupplies: (() -> Unit)? = null,
    tip: (@Composable () -> Unit)? = null,
    more: LazyListScope.() -> Unit = {},
) {
    val side = Modifier.padding(horizontal = Space.md)
    // A low screen or large text: the scene gives height to the lead, which must be readable without scrolling.
    val short = LocalConfiguration.current.screenHeightDp < 700 || LocalDensity.current.fontScale > 1.15f
    LazyColumn(modifier.fillMaxSize().testTag("shop_list"), contentPadding = PaddingValues(bottom = Space.lg)) {
        item(key = "counter") {
            Column(Modifier.testTag("shop_counter")) {
                CounterScene(
                    plate = "Seats ${shop.seats} · shelf ${shop.shelf.size} of ${shop.slots}",
                    // Under the seats: when the siege comes and what tells against the besieger, as the Forge's plate has it.
                    detail = shop.threat?.let { listOfNotNull(it.plate, it.note).joinToString(" ") }, customer = null, customerKey = null,
                    reducedMotion = reducedMotion, onOpenHero = {}, backdropHeight = if (short) 56.dp else 88.dp,
                )
                // An empty shelf is already on the plate; the band is for blades.
                if (shop.shelf.isNotEmpty()) ShelfBand(
                    shop.shelf.map { BladeUi(WeaponSnapshot.of(it.weapon), it.price ?: 0) }, gone = emptySet(), looking = emptySet(), sold = null,
                    reducedMotion = reducedMotion, onOpenBlade = { onOpenBlade(it.blade.weaponId) },
                )
            }
        }
        item(key = "lead") { LeadCard(shop.lead, onAct = { onLead(shop.lead) }, modifier = side.padding(top = Space.md)) }

        if (shop.requests.isNotEmpty()) {
            item(key = "requests") { SectionTitle("Requests · ${shop.requests.size} of ${shop.requestSlots} open", side.testTag("shop_requests")) }
            items(shop.requests, key = { "request_${it.id.value}" }) { RequestCard(it, busy, onOpenHero, onAnswer, onForgeThis, side) }
        }

        item(key = "demand") {
            Column(side.testTag("shop_demand")) {
                SectionTitle("Who is buying")
                shop.demand.forEach { DemandLine(it) }
            }
        }
        // Who left without the blade they came for, each in the counter's own words; one the shelf answers today says so.
        items(shop.wants, key = { "want_${it.heroId.value}" }) { WantRow(it, onOpenHero, onForgeWant, side) }

        shop.yesterday?.let { y ->
            item(key = "yesterday") {
                Column(side.testTag("shop_yesterday")) {
                    SectionTitle("Yesterday at the counter")
                    listOfNotNull(y.took, y.counts).takeIf { it.isNotEmpty() }?.let { Text(it.joinToString(" · "), style = MaterialTheme.typography.titleSmall) }
                    y.lines.forEach { Text(it, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = Space.xs)) }
                    TextButton(onClick = onOpenNews, modifier = Modifier.heightIn(min = 48.dp).testTag("shop_news")) { Text("Read the Gazette") }
                }
            }
        }

        item(key = "shelf") {
            Column(side.testTag("shop_shelf")) {
                SectionTitle("On the shelf · ${shop.shelf.size} of ${shop.slots}")
                tip?.invoke()
                if (shop.shelf.isEmpty()) Secondary("Nothing on display.")
            }
        }
        items(shop.shelf, key = { "stock_${it.weapon.id.value}" }) { StockRow(it, busy, onOpen = { onOpenBlade(it.weapon.id) }, onList = null, modifier = side) }

        item(key = "storage") { DoorRow("Storage · ${shop.storage.size}", "Open storage", "shop_storage", onOpenStorage, side.padding(top = Space.md)) }
        if (onOpenSupplies != null) item(key = "supplies") { DoorRow("Supplies and tools", "Open supplies", "shop_supplies", onOpenSupplies, side.padding(top = Space.sm)) }
        more()
    }
}

/** One wide row that opens a sheet. */
@Composable
private fun DoorRow(title: String, action: String, tag: String, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().forgeRow().clickable(onClickLabel = action, role = Role.Button, onClick = onOpen).heightIn(min = 56.dp).padding(horizontal = Space.md).testTag(tag),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        Text("Open  ›", style = MaterialTheme.typography.labelLarge, color = Gold)
    }
}

/** A request: what is asked, by whom and on what terms; then Accept and Decline, or which blade will be handed over. */
@Composable
internal fun RequestCard(r: RequestUi, busy: Boolean, onOpenHero: (HeroId) -> Unit, onAnswer: (CommissionId, Boolean) -> Unit, onForgeThis: (CommissionId) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(vertical = Space.xs).forgeRow().padding(horizontal = Space.md, vertical = Space.sm)) {
        Text(r.asks, style = MaterialTheme.typography.titleMedium)
        r.why?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = CreamMuted, modifier = Modifier.testTag("request_why_${r.id.value}")) }
        PersonChip(r.buyer, onOpenHero = { face -> face.heroId?.let(onOpenHero) }, note = r.terms)
        r.readiness?.let { Text(it, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = Space.xs)) }
        r.fits.forEach { Secondary(it) }
        // Accepting is the card's one gold action; the others are the same height beside it and wrap under it when the text is large.
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.sm), verticalArrangement = Arrangement.spacedBy(Space.xs), modifier = Modifier.padding(top = Space.sm)) {
            if (r.offered) {
                PrimaryActionButton("Accept", { onAnswer(r.id, true) }, enabled = !busy)
                SecondaryActionButton("Decline", { onAnswer(r.id, false) }, Modifier.heightIn(min = 52.dp), enabled = !busy)
            }
            SecondaryActionButton("Forge this", { onForgeThis(r.id) }, Modifier.heightIn(min = 52.dp).testTag("forge_this_${r.id.value}"))
        }
    }
}

/**
 * A standing want: the line opens the hero's sheet; under it, "Forge this" (the forge opens on the family asked for) or,
 * once a listed blade answers it, a mark that says so. Stacked, so the sentence keeps the row's width at any text size.
 */
@Composable
internal fun WantRow(want: WantUi, onOpenHero: (HeroId) -> Unit, onForge: (WeaponFamilyId) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(vertical = Space.xs).forgeRow().testTag("want_${want.heroId.value}")) {
        Text(
            want.line, style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.fillMaxWidth().clickable(onClickLabel = "Open details", role = Role.Button) { onOpenHero(want.heroId) }.heightIn(min = 48.dp).padding(horizontal = Space.md, vertical = Space.sm),
        )
        if (want.answered) Text("✓ A blade on the shelf answers this", style = MaterialTheme.typography.labelLarge, color = BuffGreen, modifier = Modifier.padding(start = Space.md, end = Space.md, bottom = Space.sm).testTag("want_answered_${want.heroId.value}"))
        else SecondaryActionButton("Forge this", { onForge(want.familyId) }, Modifier.padding(start = Space.md, end = Space.md, bottom = Space.sm).testTag("forge_want_${want.heroId.value}"))
    }
}

@Composable
private fun DemandLine(row: DemandRow) {
    Column(Modifier.fillMaxWidth().padding(vertical = 3.dp).semantics(mergeDescendants = true) {}) {
        Row(verticalAlignment = Alignment.Top) {
            Text(row.label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f).padding(end = Space.sm))
            Text(row.value, style = MaterialTheme.typography.titleSmall, color = Gold, textAlign = TextAlign.End)
        }
        row.detail?.let { Secondary(it) }
    }
}

/**
 * One blade on the shelf or in storage: sprite, name in its rarity's colour, the item card's numbers in one line with
 * its buffs and flaws, who favours it, and its price. Tapping the row opens
 * the blade's sheet, where it is priced, listed, unlisted, salvaged, honed or given to the watch. A stored blade also
 * has the quick "List at" its suggested price ([onList]). With [selected] set the row is a checkbox (Storage's
 * "Select blades") and [onOpen] is the tap that flips it.
 */
@Composable
internal fun StockRow(stock: StockUi, busy: Boolean, onOpen: () -> Unit, onList: ((Int) -> Unit)?, modifier: Modifier = Modifier, selected: Boolean? = null) {
    val w = stock.weapon
    // With large text the quick "List at" goes under the blade: beside it, it would leave the name a few dp.
    val listBelow = LocalDensity.current.fontScale > 1.3f
    Column(
        modifier.fillMaxWidth().padding(vertical = Space.xs).forgeRow()
            .then(if (selected == null) Modifier.clickable(onClickLabel = "Open ${w.name}", onClick = onOpen) else Modifier.toggleable(selected, role = Role.Checkbox, onValueChange = { onOpen() }))
            .testTag("stock_${w.id.value}").padding(horizontal = 12.dp, vertical = 10.dp)
            .semantics(mergeDescendants = true) { contentDescription = "${w.name}, ${stock.summary}, ${stock.price?.let { "$it gold" } ?: "in storage"}.${stock.threat?.let { " ${it.label}." } ?: ""}${stock.dormant?.let { " $it" } ?: ""}${if (selected == null) " Tap for details and price." else ""}" },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (selected != null) Checkbox(checked = selected, onCheckedChange = null)
            Box(Modifier.background(ForgeSlot).border(1.dp, BronzeDeep).padding(2.dp)) { WeaponSprite(w, size = 44.dp) }
            Column(Modifier.weight(1f)) {
                Text(w.name + (w.title?.let { " · \"$it\"" } ?: ""), style = MaterialTheme.typography.titleSmall, color = rarityColor(w.rarity))
                WeaponStatLine(w.rarity, stock.stats, stock.buffs, stock.flaws)
                stock.dormant?.let { Text("${EffectKind.NEUTRAL.sign} $it", style = MaterialTheme.typography.bodySmall, color = Gold) }
                stock.threat?.let { Text("${it.kind.sign} ${it.label}", style = MaterialTheme.typography.bodySmall, color = it.kind.color) }
                stock.favoured?.let { Secondary(it) }
            }
            if (stock.price != null) Text("${stock.price} g", style = MaterialTheme.typography.titleMedium, color = Gold)
            else if (onList != null && !listBelow) SecondaryActionButton("List at ${stock.suggested}", { onList(stock.suggested) }, enabled = !busy)
        }
        if (stock.price == null && onList != null && listBelow) SecondaryActionButton("List at ${stock.suggested}", { onList(stock.suggested) }, Modifier.fillMaxWidth().padding(top = Space.sm), enabled = !busy)
    }
}
