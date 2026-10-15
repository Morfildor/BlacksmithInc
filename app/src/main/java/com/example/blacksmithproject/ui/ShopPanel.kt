package com.example.blacksmithproject.ui

import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.shape.CircleShape
import com.example.blacksmithproject.R
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
import com.example.blacksmithproject.ui.detail.WeaponStatLine
import com.example.blacksmithproject.ui.detail.rarityColor
import com.example.blacksmithproject.ui.shopday.CounterScene
import com.example.blacksmithproject.ui.shopday.PersonChip
import com.example.blacksmithproject.ui.theme.BronzeDeep
import com.example.blacksmithproject.ui.theme.BuffGreen
import com.example.blacksmithproject.ui.theme.Cream
import com.example.blacksmithproject.ui.theme.CreamMuted
import com.example.blacksmithproject.ui.theme.ForgeSlot
import com.example.blacksmithproject.ui.theme.Gold
import com.example.blacksmithproject.ui.theme.Space
import com.tinyblacksmith.core.engine.Encounters
import com.tinyblacksmith.core.engine.Relics
import com.tinyblacksmith.core.model.CommissionId
import com.tinyblacksmith.core.model.HeroId
import com.tinyblacksmith.core.model.WeaponId

/**
 * The Shop destination: where the day is planned. From the top: [strip] (the siege and the forge's health, as on the
 * Forge; on the day of a siege a row of its own says so instead), the one lead of the day, a visitor who waits, the open
 * commissions (answered here, [onAnswer]) and the blades that came back since yesterday (listed, salvaged or kept here,
 * [onList], [onSalvage]), each only while there is one, then the counter: the scene and its plate head the shelf,
 * whose blades are listed once, as rows. Then the ways into the board,
 * storage and supplies, then the reports: who is buying and yesterday at the counter, folded. The next thing to do
 * before merchandise, merchandise before reports. One lazy list of keyed rows; data in, events out. [tip] is the
 * first-run banner about listing, shown until a blade is listed; [showLead] false while the guided first day speaks
 * for it (one voice); [more] appends rows.
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
    /** This morning's visitor: a card while they wait for an answer, one line once they have had it. */
    visitor: Encounters.View? = null,
    onOpenVisitor: () -> Unit = {},
    /** The workshop's relics; [relicSlots] 0 leaves the block out (a catalog without relics). */
    relics: List<Relics.View> = emptyList(),
    relicSlots: Int = 0,
    relicOffer: Boolean = false,
    onOpenRelicOffer: () -> Unit = {},
    strip: (@Composable () -> Unit)? = null,
    showLead: Boolean = true,
    onAnswer: (CommissionId, Boolean) -> Unit = { _, _ -> },
    onList: (WeaponId, Int) -> Unit = { _, _ -> },
    onSalvage: (WeaponId) -> Unit = {},
    more: LazyListScope.() -> Unit = {},
) {
    val side = Modifier.padding(horizontal = Space.md)
    // A low screen or large text: the scene gives its height to the shelf rows under it.
    val short = LocalConfiguration.current.screenHeightDp < 700 || LocalDensity.current.fontScale > 1.15f
    val list = rememberLazyListState()
    // "Keep" on a returned blade only lets the card go: the blade stays in storage. Remembered by blade and the day it came back.
    var kept by rememberSaveable { mutableStateOf(emptyList<String>()) }
    LazyColumn(modifier.fillMaxSize().bottomFade(list.canScrollForward).testTag("shop_list"), state = list, contentPadding = PaddingValues(bottom = Space.lg)) {
        strip?.let { item(key = "strip") { it() } }
        if (showLead) item(key = "lead") { LeadCard(shop.lead, onAct = { onLead(shop.lead) }, modifier = side.padding(top = Space.md)) }
        if (showLead) shop.siegeLead?.let { lead -> item(key = "siege_lead") {
            LeadCard(lead, onAct = { onLead(lead) }, modifier = side.padding(top = Space.sm), tag = "shop_siege_lead")
        } }
        if (visitor?.instance?.isOpen == true) item(key = "visitor") { VisitorCard(visitor, onOpenVisitor, side.padding(top = Space.sm)) }
        visitorNote(visitor)?.let { note -> item(key = "visitor_note") { Secondary(note, side.padding(top = Space.sm).testTag("visitor_note")) } }
        if (shop.requests.isNotEmpty()) item(key = "commissions") {
            val board = remember(shop.requests, shop.wants) { shop.board() }
            CommissionsCard(board.onShop, busy, onOpenBoard, onAnswer, side.padding(top = Space.md))
        }
        val returned = shop.returned.filter { "${it.stock.weapon.id.value}@${it.day}" !in kept }
        if (returned.isNotEmpty()) item(key = "returned") {
            ReturnedCard(
                returned, shelfFree = shop.slots - shop.shelf.size, busy, onOpenBlade, onList, onSalvage,
                onKeep = { r -> kept = kept + "${r.stock.weapon.id.value}@${r.day}" }, modifier = side.padding(top = Space.md),
            )
        }

        // The counter heads the shelf: its plate counts the blades and the free places, and each blade is one row under it.
        item(key = "counter") {
            CounterScene(
                plate = shop.plate, detail = null, customer = null, customerKey = null,
                reducedMotion = reducedMotion, onOpenHero = {}, backdropHeight = if (short || shop.shelf.isEmpty()) 56.dp else 88.dp,
                modifier = Modifier.padding(top = Space.md).testTag("shop_counter"),
            )
        }
        item(key = "shelf") {
            // An empty shelf is said by the plate; the first-run tip is the only thing under it until a blade is listed.
            Column(side.testTag("shop_shelf")) {
                if (shop.shelf.isEmpty()) tip?.let { Box(Modifier.padding(top = Space.md)) { it() } }
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
                        InlineActionButton("Read the Gazette", onOpenNews, Modifier.heightIn(min = 48.dp).testTag("shop_news"))
                    }
                }
            }
        }
        if (relicSlots > 0) item(key = "relics") { RelicRows(relics, relicSlots, relicOffer, onOpenRelicOffer, side) }
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

/**
 * The open commissions as one card above the counter: its head counts them, marks the new offers and opens the board;
 * under it each commission in two lines, an offer with Accept and Decline, an accepted one with whether a blade in the
 * shop answers it (`Labels.readiness`). The board keeps the rest: the terms in full, the fits, "Forge this".
 */
@Composable
private fun CommissionsCard(rows: List<RequestUi>, busy: Boolean, onOpenBoard: () -> Unit, onAnswer: (CommissionId, Boolean) -> Unit, modifier: Modifier = Modifier) {
    val new = rows.count { it.offered }
    Column(modifier.fillMaxWidth().forgeRow().testTag("shop_commissions")) {
        Row(
            Modifier.fillMaxWidth().clickable(onClickLabel = "Open the board", role = Role.Button, onClick = onOpenBoard).heightIn(min = 56.dp).padding(horizontal = Space.md).testTag("shop_commissions_open"),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            PixelImage(R.drawable.icon_action_commission, 32.dp, description = null)
            Text("Commissions · ${rows.size}", style = MaterialTheme.typography.titleMedium, color = Cream, modifier = Modifier.weight(1f))
            // The count of offers that wait, beside a gold dot as on the Guild's tabs: the thing on the Shop that needs an answer.
            if (new > 0) Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.testTag("shop_commissions_new")) {
                Box(Modifier.padding(end = 6.dp).size(7.dp).background(Gold, CircleShape))
                Text("$new new", style = MaterialTheme.typography.labelLarge, color = Gold)
            }
            Text("Open  ›", style = MaterialTheme.typography.labelLarge, color = Gold)
        }
        rows.forEach { r ->
            Column(Modifier.fillMaxWidth().padding(start = Space.md, end = Space.md, bottom = Space.sm).testTag("shop_commission_${r.id.value}")) {
                Text("${r.asks} · ${r.reward} gold", style = MaterialTheme.typography.titleSmall, color = Cream)
                Secondary("${r.buyer.name} · ${if (r.offered) "New offer" else r.due}")
                if (r.offered) FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.sm), verticalArrangement = Arrangement.spacedBy(Space.xs), modifier = Modifier.padding(top = Space.xs)) {
                    PrimaryActionButton("Accept", { onAnswer(r.id, true) }, Modifier.testTag("shop_accept_${r.id.value}"), enabled = !busy)
                    SecondaryActionButton("Decline", { onAnswer(r.id, false) }, Modifier.heightIn(min = 52.dp).testTag("shop_decline_${r.id.value}"), enabled = !busy)
                }
                else r.readiness?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = if (r.ready) BuffGreen else CreamMuted, modifier = Modifier.testTag("shop_commission_ready_${r.id.value}")) }
            }
        }
    }
}

/**
 * The blades that came back since yesterday, as one card above the counter: each says how ([ReturnedUi.how]), then
 * List at its suggested price, Salvage, or Keep in storage (it stays in storage; the card lets it go). The name opens the blade's
 * sheet, where any price can be set. A blade kept for an order can be neither listed nor salvaged, as the engine has it.
 */
@Composable
private fun ReturnedCard(
    rows: List<ReturnedUi>, shelfFree: Int, busy: Boolean, onOpen: (WeaponId) -> Unit, onList: (WeaponId, Int) -> Unit, onSalvage: (WeaponId) -> Unit,
    onKeep: (ReturnedUi) -> Unit, modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth().forgeRow().padding(horizontal = Space.md, vertical = Space.sm).testTag("shop_returned")) {
        Text("Back at the forge · ${rows.size}", style = MaterialTheme.typography.titleMedium, color = Cream)
        Secondary(if (shelfFree > 0) "In storage now. List, salvage or keep each one." else "In storage now. The shelf is full: salvage or keep, or make room to list.")
        rows.forEach { r ->
            val w = r.stock.weapon
            val free = r.stock.promised == null
            Column(Modifier.fillMaxWidth().padding(top = Space.sm).testTag("returned_${w.id.value}")) {
                Row(
                    Modifier.fillMaxWidth().clickable(onClickLabel = "Open ${w.name}", role = Role.Button) { onOpen(w.id) }.heightIn(min = 48.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(Modifier.background(ForgeSlot).border(1.dp, BronzeDeep).padding(2.dp)) { WeaponSprite(w, size = 36.dp) }
                    Column(Modifier.weight(1f)) {
                        Text(w.name + (w.title?.let { " · \"$it\"" } ?: ""), style = MaterialTheme.typography.titleSmall, color = rarityColor(w.rarity))
                        Secondary(r.how)
                        r.stock.promised?.let { Text("${EffectKind.NEUTRAL.sign} $it", style = MaterialTheme.typography.bodySmall, color = Gold) }
                    }
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.sm), verticalArrangement = Arrangement.spacedBy(Space.xs), modifier = Modifier.padding(top = Space.xs)) {
                    SecondaryActionButton("List at ${r.stock.suggested}", { onList(w.id, r.stock.suggested) }, Modifier.testTag("returned_list_${w.id.value}"), enabled = !busy && free && shelfFree > 0)
                    SecondaryActionButton(r.salvage, { onSalvage(w.id) }, Modifier.testTag("returned_salvage_${w.id.value}"), enabled = !busy && free)
                    InlineActionButton("Keep in storage", { onKeep(r) }, Modifier.testTag("returned_keep_${w.id.value}"))
                }
            }
        }
    }
}

/** A request: what is asked, by whom and on what terms; then Accept and Decline, or which blade will be handed over. */
@Composable
internal fun RequestCard(r: RequestUi, busy: Boolean, onOpenHero: (HeroId) -> Unit, onAnswer: (CommissionId, Boolean) -> Unit, onForgeThis: (CommissionId) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(vertical = Space.xs).forgeRow().padding(horizontal = Space.md, vertical = Space.sm)) {
        Row(horizontalArrangement = Arrangement.spacedBy(Space.sm), verticalAlignment = Alignment.CenterVertically) {
            PixelImage(R.drawable.icon_action_commission, wholePixelDp(48, 36.dp), description = null)
            Text(r.asks, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        }
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
 * A standing want: the line opens the hero's sheet; under it, "Forge this" (the forge opens on the family asked for, with who wants it) or,
 * once a listed blade answers it, a mark that says so. Stacked, so the sentence keeps the row's width at any text size.
 */
@Composable
internal fun WantRow(want: WantUi, onOpenHero: (HeroId) -> Unit, onForge: (HeroId) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(vertical = Space.xs).forgeRow().testTag("want_${want.heroId.value}")) {
        Text(
            want.line, style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.fillMaxWidth().clickable(onClickLabel = "Open details", role = Role.Button) { onOpenHero(want.heroId) }.heightIn(min = 48.dp).padding(horizontal = Space.md, vertical = Space.sm),
        )
        if (want.answered) Text("✓ A suitable weapon is on the shelf", style = MaterialTheme.typography.labelLarge, color = BuffGreen, modifier = Modifier.padding(start = Space.md, end = Space.md, bottom = Space.sm).testTag("want_answered_${want.heroId.value}"))
        else SecondaryActionButton("Forge this", { onForge(want.heroId) }, Modifier.padding(start = Space.md, end = Space.md, bottom = Space.sm).testTag("forge_want_${want.heroId.value}"))
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
    // A blade kept for an order cannot go on the shelf: it has no quick "List at".
    @Suppress("NAME_SHADOWING") val onList = onList.takeIf { stock.promised == null }
    Column(
        modifier.fillMaxWidth().padding(vertical = Space.xs).forgeRow()
            .then(if (selected == null) Modifier.clickable(onClickLabel = "Open ${w.name}", onClick = onOpen) else Modifier.toggleable(selected, enabled = stock.promised == null, role = Role.Checkbox, onValueChange = { onOpen() }))
            .testTag("stock_${w.id.value}").padding(horizontal = 12.dp, vertical = 10.dp)
            .semantics(mergeDescendants = true) { contentDescription = stock.spoken(tap = selected == null) },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (selected != null) Checkbox(checked = selected, onCheckedChange = null, enabled = stock.promised == null)
            Box(Modifier.background(ForgeSlot).border(1.dp, BronzeDeep).padding(2.dp)) { WeaponSprite(w, size = 44.dp) }
            Column(Modifier.weight(1f)) {
                Text(w.name + (w.title?.let { " · \"$it\"" } ?: ""), style = MaterialTheme.typography.titleSmall, color = rarityColor(w.rarity))
                WeaponStatLine(w.rarity, stock.stats, stock.buffs, stock.flaws)
                stock.dormant?.let { Text("${EffectKind.NEUTRAL.sign} $it", style = MaterialTheme.typography.bodySmall, color = Gold) }
                stock.threat?.let { Text("${it.kind.sign} ${it.label}", style = MaterialTheme.typography.bodySmall, color = it.kind.color) }
                stock.favoured?.let { Secondary(it) }
                stock.promised?.let {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                        PixelImage(R.drawable.icon_promised, wholePixelDp(48, 20.dp), description = null)
                        Text("${EffectKind.NEUTRAL.sign} $it", style = MaterialTheme.typography.bodySmall, color = Gold, modifier = Modifier.testTag("stock_promised_${w.id.value}"))
                    }
                }
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
