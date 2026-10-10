package com.example.blacksmithproject.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
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
import com.example.blacksmithproject.ui.shopday.BladeUi
import com.example.blacksmithproject.ui.shopday.CounterScene
import com.example.blacksmithproject.ui.shopday.PersonChip
import com.example.blacksmithproject.ui.shopday.ShelfBand
import com.example.blacksmithproject.ui.theme.Space
import com.tinyblacksmith.core.model.CommissionId
import com.tinyblacksmith.core.model.HeroId
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
                    plate = "Seats ${shop.seats} · shelf ${shop.shelf.size} of ${shop.slots}", detail = null, customer = null, customerKey = null,
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
            item(key = "requests") { SectionTitle("Requests", side.testTag("shop_requests")) }
            items(shop.requests, key = { "request_${it.id.value}" }) { RequestCard(it, busy, onOpenHero, onAnswer, onForgeThis, side) }
        }

        item(key = "demand") {
            Column(side.testTag("shop_demand")) {
                SectionTitle("Who is buying")
                shop.demand.forEach { DemandLine(it) }
            }
        }

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
    Surface(tonalElevation = 2.dp, shape = MaterialTheme.shapes.medium, modifier = modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().clickable(onClickLabel = action, role = Role.Button, onClick = onOpen).heightIn(min = 56.dp).padding(horizontal = Space.md).testTag(tag),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Text("Open  ›", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        }
    }
}

/** A request: what is asked, by whom and on what terms; then Accept and Decline, or which blade will be handed over. */
@Composable
internal fun RequestCard(r: RequestUi, busy: Boolean, onOpenHero: (HeroId) -> Unit, onAnswer: (CommissionId, Boolean) -> Unit, onForgeThis: (CommissionId) -> Unit, modifier: Modifier = Modifier) {
    Surface(tonalElevation = 1.dp, shape = MaterialTheme.shapes.medium, modifier = modifier.fillMaxWidth().padding(vertical = Space.xs)) {
        Column(Modifier.padding(horizontal = Space.md, vertical = Space.sm)) {
            Text(r.asks, style = MaterialTheme.typography.titleMedium)
            PersonChip(r.buyer, onOpenHero = { face -> face.heroId?.let(onOpenHero) }, note = r.terms)
            r.readiness?.let { Text(it, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = Space.xs)) }
            r.fits.forEach { Secondary(it) }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.sm), modifier = Modifier.padding(top = Space.sm)) {
                if (r.offered) {
                    Button(onClick = { onAnswer(r.id, true) }, enabled = !busy, modifier = Modifier.heightIn(min = 48.dp)) { Text("Accept") }
                    OutlinedButton(onClick = { onAnswer(r.id, false) }, enabled = !busy, modifier = Modifier.heightIn(min = 48.dp)) { Text("Decline") }
                }
                TextButton(onClick = { onForgeThis(r.id) }, modifier = Modifier.heightIn(min = 48.dp).testTag("forge_this_${r.id.value}")) { Text("Forge this") }
            }
        }
    }
}

@Composable
private fun DemandLine(row: DemandRow) {
    Column(Modifier.fillMaxWidth().padding(vertical = 3.dp).semantics(mergeDescendants = true) {}) {
        Row(verticalAlignment = Alignment.Top) {
            Text(row.label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f).padding(end = Space.sm))
            Text(row.value, style = MaterialTheme.typography.titleSmall, textAlign = TextAlign.End)
        }
        row.detail?.let { Secondary(it) }
    }
}

/**
 * One blade on the shelf or in storage: sprite, name, what it is, who favours it, and its price. Tapping the row opens
 * the blade's sheet, where it is priced, listed, unlisted, salvaged, honed or given to the watch. A stored blade also
 * has the quick "List at" its suggested price ([onList]).
 */
@Composable
internal fun StockRow(stock: StockUi, busy: Boolean, onOpen: () -> Unit, onList: ((Int) -> Unit)?, modifier: Modifier = Modifier) {
    val w = stock.weapon
    Surface(tonalElevation = 1.dp, shape = MaterialTheme.shapes.medium, modifier = modifier.fillMaxWidth().padding(vertical = Space.xs)) {
        Row(
            Modifier.fillMaxWidth().clickable(onClickLabel = "Open ${w.name}", onClick = onOpen).testTag("stock_${w.id.value}").padding(horizontal = 12.dp, vertical = 10.dp)
                .semantics(mergeDescendants = true) { contentDescription = "${w.name}, ${stock.summary}, ${stock.price?.let { "$it gold" } ?: "in storage"}. Tap for details and price." },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            WeaponSprite(w, size = 48.dp)
            Column(Modifier.weight(1f)) {
                Text(w.name, style = MaterialTheme.typography.titleSmall)
                Secondary(stock.summary)
                stock.favoured?.let { Secondary(it) }
            }
            if (stock.price != null) Text("${stock.price} g", style = MaterialTheme.typography.titleMedium)
            else if (onList != null) Button(onClick = { onList(stock.suggested) }, enabled = !busy, modifier = Modifier.heightIn(min = 48.dp)) { Text("List at ${stock.suggested}", maxLines = 1) }
        }
    }
}
