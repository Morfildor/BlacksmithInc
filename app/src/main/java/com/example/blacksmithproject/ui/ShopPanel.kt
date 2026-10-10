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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
import com.example.blacksmithproject.R
import com.example.blacksmithproject.ui.detail.WeaponStatLine
import com.example.blacksmithproject.ui.detail.rarityColor
import com.example.blacksmithproject.ui.shopday.BladeUi
import com.example.blacksmithproject.ui.shopday.CounterScene
import com.example.blacksmithproject.ui.shopday.PersonChip
import com.example.blacksmithproject.ui.shopday.ShelfBand
import com.example.blacksmithproject.ui.theme.BronzeDeep
import com.example.blacksmithproject.ui.theme.BuffGreen
import com.example.blacksmithproject.ui.theme.CreamMuted
import com.example.blacksmithproject.ui.theme.Ember
import com.example.blacksmithproject.ui.theme.ForgeSlot
import com.example.blacksmithproject.ui.theme.Gold
import com.example.blacksmithproject.ui.theme.Space
import com.tinyblacksmith.core.model.CommissionId
import com.tinyblacksmith.core.model.HeroId
import com.tinyblacksmith.core.model.WeaponFamilyId
import com.tinyblacksmith.core.model.WeaponId
import com.tinyblacksmith.core.model.WeaponSnapshot

/**
 * The Shop destination: where the day is planned. From the top: [strip] (the siege and the forge's health, as on the
 * Forge), the counter with the shelf and its free places, the one lead of the day (under "Siege today" on that day),
 * the shelf rows, the ways into the board, storage and supplies, then the reports: who is buying and yesterday at the
 * counter, folded. Merchandise before reports. One lazy list of keyed rows; data in, events out. [tip] is the first-run
 * banner about listing, shown until a blade is listed; [more] appends rows.
 */
@Composable
fun ShopPanel(
    shop: ShopUi,
    busy: Boolean,
    reducedMotion: Boolean,
    onLead: (LeadUi) -> Unit,
    onOpenBlade: (WeaponId) -> Unit,
    onOpenBoard: () -> Unit,
    onOpenStorage: () -> Unit,
    onOpenNews: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenSupplies: (() -> Unit)? = null,
    tip: (@Composable () -> Unit)? = null,
    strip: (@Composable () -> Unit)? = null,
    more: LazyListScope.() -> Unit = {},
) {
    val side = Modifier.padding(horizontal = Space.md)
    // A low screen or large text: the scene gives height to the lead, which must be readable without scrolling.
    val short = LocalConfiguration.current.screenHeightDp < 700 || LocalDensity.current.fontScale > 1.15f
    val list = rememberLazyListState()
    LazyColumn(modifier.fillMaxSize().bottomFade(list.canScrollForward).testTag("shop_list"), state = list, contentPadding = PaddingValues(bottom = Space.lg)) {
        strip?.let { item(key = "strip") { it() } }
        item(key = "counter") {
            Column(Modifier.testTag("shop_counter")) {
                CounterScene(
                    plate = shop.plate, detail = null, customer = null, customerKey = null,
                    reducedMotion = reducedMotion, onOpenHero = {}, backdropHeight = if (short) 56.dp else 88.dp,
                )
                // The shelf as a thing: the blades for sale and, beside them, the places still free.
                ShelfBand(
                    shop.shelf.map { BladeUi(WeaponSnapshot.of(it.weapon), it.price ?: 0) }, gone = emptySet(), looking = emptySet(), sold = null,
                    reducedMotion = reducedMotion, onOpenBlade = { onOpenBlade(it.blade.weaponId) }, slots = shop.slots,
                )
            }
        }
        // The day of a siege says so first, in words; the lead keeps its place under it.
        shop.threat?.takeIf { it.today }?.let { threat ->
            item(key = "siege") {
                Column(side.padding(top = Space.md).fillMaxWidth().forgeRow().heightIn(min = 56.dp).padding(horizontal = Space.md, vertical = Space.sm).testTag("shop_siege").semantics(mergeDescendants = true) {}, verticalArrangement = Arrangement.Center) {
                    Text("Siege today, after today's trading", style = MaterialTheme.typography.titleMedium, color = Ember)
                    Secondary(listOfNotNull(threat.matchup, threat.outlook, threat.note).joinToString(" · "))
                }
            }
        }
        item(key = "lead") { LeadCard(shop.lead, onAct = { onLead(shop.lead) }, modifier = side.padding(top = Space.md)) }

        item(key = "shelf") {
            Column(side.testTag("shop_shelf")) {
                // The count is on the counter's plate and the free places are on the shelf: an empty shelf needs no heading here.
                if (shop.shelf.isEmpty()) tip?.let { Box(Modifier.padding(top = Space.md)) { it() } }
                else SectionTitle("On the shelf")
            }
        }
        items(shop.shelf, key = { "stock_${it.weapon.id.value}" }) { ShelfRow(it, onOpen = { onOpenBlade(it.weapon.id) }, modifier = side) }

        // Commissions and customer wants live on one board; the Shop says how many there are and whether an offer waits.
        item(key = "board") {
            val board = remember(shop.requests, shop.wants) { shop.board() }
            DoorRow(R.drawable.icon_action_commission, "Commissions & customers", "Open the board", "shop_board", onOpenBoard, side.padding(top = Space.md), detail = listOfNotNull(board.summary, board.pending).joinToString(" · "))
        }

        item(key = "storage") { DoorRow(R.drawable.icon_action_storage, "Storage · ${shop.storage.size}", "Open storage", "shop_storage", onOpenStorage, side.padding(top = Space.sm)) }
        if (onOpenSupplies != null) item(key = "supplies") { DoorRow(R.drawable.icon_action_supplies, "Supplies and tools", "Open supplies", "shop_supplies", onOpenSupplies, side.padding(top = Space.sm)) }

        item(key = "demand") {
            Column(side.testTag("shop_demand")) {
                SectionTitle("Who is buying")
                shop.demand.forEach { DemandLine(it) }
            }
        }

        // Yesterday is a report: one row with the till and the counts, and the lines behind "Show".
        shop.yesterday?.let { y ->
            item(key = "yesterday") {
                var open by rememberSaveable { mutableStateOf(false) }
                Column(side.padding(top = Space.md).fillMaxWidth().forgeRow().testTag("shop_yesterday")) {
                    Row(
                        Modifier.fillMaxWidth().clickable(onClickLabel = if (open) "Hide yesterday's lines" else "Show yesterday's lines", role = Role.Button) { open = !open }
                            .heightIn(min = 56.dp).padding(horizontal = Space.md).testTag("shop_yesterday_toggle"),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f).padding(vertical = Space.sm)) {
                            Text("Yesterday at the counter", style = MaterialTheme.typography.titleMedium)
                            listOfNotNull(y.took, y.counts).takeIf { it.isNotEmpty() }?.let { Secondary(it.joinToString(" · ")) }
                        }
                        Text(if (open) "Hide" else "Show", style = MaterialTheme.typography.labelLarge, color = Gold, modifier = Modifier.padding(start = Space.sm))
                    }
                    if (open) Column(Modifier.padding(horizontal = Space.md)) {
                        y.lines.forEach { Text(it, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(bottom = Space.xs)) }
                        TextButton(onClick = onOpenNews, modifier = Modifier.heightIn(min = 48.dp).testTag("shop_news")) { Text("Read the Gazette") }
                    }
                }
            }
        }
        more()
    }
}

/** One wide row that opens a sheet. */
@Composable
private fun DoorRow(icon: Int, title: String, action: String, tag: String, onOpen: () -> Unit, modifier: Modifier = Modifier, detail: String? = null) {
    Row(
        modifier.fillMaxWidth().forgeRow().clickable(onClickLabel = action, role = Role.Button, onClick = onOpen).heightIn(min = 56.dp).padding(horizontal = Space.md).testTag(tag),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        PixelImage(icon, 32.dp, description = null)
        Column(Modifier.weight(1f).padding(vertical = Space.sm)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            detail?.let { Secondary(it, Modifier.testTag("${tag}_detail")) }
        }
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
 * One blade in storage, in full (the Shop's shelf has the short [ShelfRow]): sprite, name in its rarity's colour, the item card's numbers in one line with
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
            .semantics(mergeDescendants = true) { contentDescription = stock.spoken(tap = selected == null) },
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

/** What TalkBack says for a blade's row: its name, summary and price, then how it stands against the besieger and what sleeps in it. */
private fun StockUi.spoken(tap: Boolean) =
    "${weapon.name}, $summary, ${price?.let { "$it gold" } ?: "in storage"}.${threat?.let { " ${it.label}." } ?: ""}${dormant?.let { " $it" } ?: ""}${if (tap) " Tap for details and price." else ""}"

/**
 * A blade on the Shop's shelf, short: sprite, name in its rarity's colour, then the rarity in words, its power and each
 * flaw ("−", red) by name, with the price at the end. The rest ([StockRow] has it in Storage) is one tap away, on the
 * blade's sheet; TalkBack hears the same sentence as there.
 */
@Composable
private fun ShelfRow(stock: StockUi, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val w = stock.weapon
    Row(
        modifier.fillMaxWidth().padding(vertical = Space.xs).forgeRow().clickable(onClickLabel = "Open ${w.name}", onClick = onOpen)
            .testTag("stock_${w.id.value}").heightIn(min = 56.dp).padding(horizontal = 12.dp, vertical = Space.sm)
            .semantics(mergeDescendants = true) { contentDescription = stock.spoken(tap = true) },
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.background(ForgeSlot).border(1.dp, BronzeDeep).padding(2.dp)) { WeaponSprite(w, size = 36.dp) }
        Column(Modifier.weight(1f)) {
            Text(w.name + (w.title?.let { " · \"$it\"" } ?: ""), style = MaterialTheme.typography.titleSmall, color = rarityColor(w.rarity))
            Row(verticalAlignment = Alignment.CenterVertically) {
                WeaponStatLine(w.rarity, stock.stats.filter { it.label == "Power" }, emptyList(), stock.flaws, Modifier.weight(1f).padding(end = Space.sm))
                stock.price?.let { Text("$it g", style = MaterialTheme.typography.titleMedium, color = Gold) }
            }
            stock.dormant?.let { Text("${EffectKind.NEUTRAL.sign} $it", style = MaterialTheme.typography.bodySmall, color = Gold) }
            stock.threat?.let { Text("${it.kind.sign} ${it.label}", style = MaterialTheme.typography.bodySmall, color = it.kind.color) }
        }
    }
}
