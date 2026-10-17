package com.example.blacksmithproject.ui

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.blacksmithproject.R
import com.example.blacksmithproject.ui.shopday.CounterScene
import com.example.blacksmithproject.ui.shopday.PersonChip
import com.example.blacksmithproject.ui.theme.BuffGreen
import com.example.blacksmithproject.ui.theme.Cream
import com.example.blacksmithproject.ui.theme.CreamMuted
import com.example.blacksmithproject.ui.theme.Ember
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
 * commissions (answered here, [onAnswer]) and the weapons that came back since yesterday (listed, salvaged or kept here,
 * [onList], [onSalvage]), each only while there is one, then the counter: the scene and its plate head the shelf,
 * whose weapons are listed once, as rows (a tap opens the sheet, the ringed "i" the card). Then the ways into the board,
 * storage and supplies, then the reports: who is buying and yesterday at the counter, folded. The next thing to do
 * before merchandise, merchandise before reports. V3: the lead is the one plate; everything under it is rows divided by
 * hairlines and spacing. One lazy list of keyed rows; data in, events out. [tip] is the first-run banner about listing,
 * shown until a weapon is listed; [showLead] false while the guided first day speaks for it (one voice); [more] appends rows.
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
    /** This morning's visitor: a row while they wait for an answer, one line once they have had it. */
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

        // The counter heads the shelf: its plate counts the weapons and the free places, and each weapon is one row under it.
        item(key = "counter") {
            CounterScene(
                plate = shop.plate, detail = null, customer = null, customerKey = null,
                reducedMotion = reducedMotion, onOpenHero = {}, backdropHeight = if (short || shop.shelf.isEmpty()) 56.dp else 88.dp,
                modifier = Modifier.padding(top = Space.md).testTag("shop_counter"),
            )
        }
        item(key = "shelf") {
            // An empty shelf is said by the plate; the first-run tip is the only thing under it until a weapon is listed.
            Column(side.testTag("shop_shelf")) {
                if (shop.shelf.isEmpty()) tip?.let { Box(Modifier.padding(top = Space.md)) { it() } }
            }
        }
        items(shop.shelf, key = { "stock_${it.weapon.id.value}" }) { WeaponRow(it, onOpen = { onOpenBlade(it.weapon.id) }, modifier = side) }

        // Commissions and customer wants live on one board; the Shop says how many there are and whether an offer waits.
        // V2: the ways into the workshop's sheets are a quiet list under the shelf, divided by hairlines: no plates and no
        // "Open" on every row, the chevron and the row's tap say it.
        item(key = "board") {
            val board = remember(shop.requests, shop.wants) { shop.board() }
            DoorRow(R.drawable.icon_action_commission, "Commissions & customers", "Open the board", "shop_board", onOpenBoard, side.padding(top = Space.md), detail = listOfNotNull(board.summary, board.pending).joinToString(" · "))
        }

        item(key = "storage") { DoorRow(R.drawable.icon_action_storage, "Storage", "Open storage", "shop_storage", onOpenStorage, side, count = "${shop.storage.size}") }
        if (onOpenSupplies != null) item(key = "supplies") { DoorRow(R.drawable.icon_action_supplies, "Supplies and tools", "Open supplies", "shop_supplies", onOpenSupplies, side) }

        // Who is buying is a report: one line says the most of it, the rows open under it.
        item(key = "demand") {
            Disclosure(
                "Who is buying", key = "shop_demand_open", summary = shop.demand.take(2).joinToString(" · ") { "${it.value} ${it.label.replaceFirstChar(Char::lowercase)}" },
                modifier = side.padding(top = Space.md).testTag("shop_demand"), tag = "shop_demand_toggle",
            ) { shop.demand.forEach { DemandLine(it) } }
        }

        // Yesterday is a report: one row with the till and the counts, and the lines under it once opened.
        shop.yesterday?.let { y ->
            item(key = "yesterday") {
                var open by rememberSaveable { mutableStateOf(false) }
                Column(side.fillMaxWidth().testTag("shop_yesterday")) {
                    Row(
                        Modifier.fillMaxWidth().clickable(onClickLabel = if (open) "Hide yesterday's lines" else "Show yesterday's lines", role = Role.Button) { open = !open }
                            .semantics { stateDescription = if (open) "Expanded" else "Collapsed" }
                            .heightIn(min = 56.dp).testTag("shop_yesterday_toggle"),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f).padding(vertical = Space.sm)) {
                            Text("Yesterday at the counter", style = MaterialTheme.typography.titleSmall, color = Cream)
                            listOfNotNull(y.took, y.counts).takeIf { it.isNotEmpty() }?.let { Secondary(it.joinToString(" · ")) }
                        }
                        Chevron(if (open) ChevronWay.UP else ChevronWay.DOWN, Modifier.padding(start = Space.sm), tint = CreamMuted)
                    }
                    if (open) Column(Modifier.padding(bottom = Space.sm)) {
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

/** One row that opens a sheet: icon, title, an optional count at the end and a line under it, divided from the next by a hairline. */
@Composable
private fun DoorRow(icon: Int, title: String, action: String, tag: String, onOpen: () -> Unit, modifier: Modifier = Modifier, detail: String? = null, count: String? = null) {
    Row(
        modifier.fillMaxWidth().hairline()
            .clickable(onClickLabel = action, role = Role.Button, onClick = onOpen).heightIn(min = 52.dp).padding(horizontal = Space.xs).testTag(tag),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        PixelIcon(icon, 28.dp)
        Column(Modifier.weight(1f).padding(vertical = Space.sm)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            detail?.let { Secondary(it, Modifier.testTag("${tag}_detail")) }
        }
        count?.let { Text(it, style = MaterialTheme.typography.titleSmall, color = CreamMuted) }
        Chevron(ChevronWay.RIGHT, tint = CreamMuted)
    }
}

/**
 * The open commissions as one section above the counter: its head counts them, marks the new offers (the one thing on
 * the Shop that waits for an answer, in gold) and opens the board; under it each commission as a row: what it asks, then
 * its reward, due day and buyer as facts, then Accept and Decline for an offer, or for an accepted one whether a weapon
 * in the shop answers it (`Labels.readiness`). The board keeps the rest: the terms in full, the fits, "Forge this".
 */
@Composable
private fun CommissionsCard(rows: List<RequestUi>, busy: Boolean, onOpenBoard: () -> Unit, onAnswer: (CommissionId, Boolean) -> Unit, modifier: Modifier = Modifier) {
    val new = rows.count { it.offered }
    Column(modifier.fillMaxWidth().testTag("shop_commissions")) {
        Row(
            Modifier.fillMaxWidth().hairline().clickable(onClickLabel = "Open the board", role = Role.Button, onClick = onOpenBoard).heightIn(min = 52.dp).padding(horizontal = Space.xs).testTag("shop_commissions_open"),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            PixelIcon(R.drawable.icon_action_commission, 28.dp)
            Text("Commissions · ${rows.size}", style = MaterialTheme.typography.titleSmall, color = Cream, modifier = Modifier.weight(1f).semantics { heading() })
            // The count of offers that wait, beside a gold dot as on the Guild's tabs: the thing on the Shop that needs an answer.
            if (new > 0) Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.testTag("shop_commissions_new")) {
                Box(Modifier.padding(end = 6.dp).size(7.dp).background(Gold, CircleShape))
                Text("$new new", style = MaterialTheme.typography.labelLarge, color = Gold)
            }
            Chevron(ChevronWay.RIGHT, tint = CreamMuted)
        }
        rows.forEach { r ->
            Column(Modifier.fillMaxWidth().hairline().padding(horizontal = Space.xs, vertical = Space.sm).testTag("shop_commission_${r.id.value}")) {
                Text(r.asks, style = MaterialTheme.typography.titleSmall, color = Cream)
                FactLine(listOfNotNull(Fact("New offer").takeIf { r.offered }, Fact("${r.reward} gold", "Reward"), Fact(r.due), Fact(r.buyer.name)))
                if (r.offered) FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.sm), verticalArrangement = Arrangement.spacedBy(Space.xs), modifier = Modifier.padding(top = Space.xs)) {
                    // V2: End Day is the Shop's one gold action; answering an offer is an outline beside a plain "Decline".
                    SecondaryActionButton("Accept", { onAnswer(r.id, true) }, Modifier.testTag("shop_accept_${r.id.value}"), enabled = !busy)
                    InlineActionButton("Decline", { onAnswer(r.id, false) }, Modifier.testTag("shop_decline_${r.id.value}"), enabled = !busy)
                }
                else r.readiness?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = if (r.ready) BuffGreen else CreamMuted, modifier = Modifier.padding(top = 2.dp).testTag("shop_commission_ready_${r.id.value}")) }
            }
        }
    }
}

/**
 * The weapons that came back since yesterday, as one section above the counter: each is a weapon row (a tap opens its
 * sheet, where any price can be set; the ringed "i" its card) that says how it came back ([ReturnedUi.how]), then List
 * at its suggested price, Salvage with what it costs, or Keep in storage (it stays in storage; the section lets it go).
 * A weapon kept for an order can be neither listed nor salvaged, as the engine has it.
 */
@Composable
private fun ReturnedCard(
    rows: List<ReturnedUi>, shelfFree: Int, busy: Boolean, onOpen: (WeaponId) -> Unit, onList: (WeaponId, Int) -> Unit, onSalvage: (WeaponId) -> Unit,
    onKeep: (ReturnedUi) -> Unit, modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth().testTag("shop_returned")) {
        Text("Back at the forge · ${rows.size}", style = MaterialTheme.typography.titleSmall, color = Cream, modifier = Modifier.padding(horizontal = Space.xs).semantics { heading() })
        Secondary(
            if (shelfFree > 0) "In storage now. List, salvage or keep each one." else "In storage now. The shelf is full: salvage or keep, or make room to list.",
            Modifier.padding(horizontal = Space.xs),
        )
        rows.forEach { r ->
            val w = r.stock.weapon
            val free = r.stock.promised == null
            Column(Modifier.fillMaxWidth().hairline().padding(bottom = Space.sm).testTag("returned_${w.id.value}")) {
                WeaponRow(r.stock, onOpen = { onOpen(w.id) }, tag = "returned_open_${w.id.value}", note = r.how, divider = false, promisedTag = "returned_promised_${w.id.value}")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.sm), verticalArrangement = Arrangement.spacedBy(Space.xs)) {
                    SecondaryActionButton("List at ${r.stock.suggested}", { onList(w.id, r.stock.suggested) }, Modifier.testTag("returned_list_${w.id.value}"), enabled = !busy && free && shelfFree > 0)
                    SecondaryActionButton(r.salvage, { onSalvage(w.id) }, Modifier.testTag("returned_salvage_${w.id.value}"), enabled = !busy && free)
                    InlineActionButton("Keep in storage", { onKeep(r) }, Modifier.testTag("returned_keep_${w.id.value}"))
                }
            }
        }
    }
}

/**
 * A commission on the board, chosen: the plate of a quest card. What it asks as facts with its standing in words, then
 * why it was made, the buyer with the terms (reward and due day, once), whether a weapon in the shop answers it and how
 * each weapon of the type fits. Accept and Decline for an offer, "Forge this" for any.
 */
@Composable
internal fun RequestCard(r: RequestUi, busy: Boolean, onOpenHero: (HeroId) -> Unit, onAnswer: (CommissionId, Boolean) -> Unit, onForgeThis: (CommissionId) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(vertical = Space.xs).forgeRow().padding(horizontal = Space.md, vertical = Space.sm)) {
        Row(horizontalArrangement = Arrangement.spacedBy(Space.sm), verticalAlignment = Alignment.CenterVertically) {
            PixelIcon(R.drawable.icon_action_commission, 36.dp)
            Column(Modifier.weight(1f)) {
                Text(r.asks, style = MaterialTheme.typography.titleMedium)
                FactLine(r.askFacts())
            }
            StatusWord(r)
        }
        r.why?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = CreamMuted, modifier = Modifier.padding(top = Space.xs).testTag("request_why_${r.id.value}")) }
        PersonChip(r.buyer, onOpenHero = { face -> face.heroId?.let(onOpenHero) }, note = r.terms)
        r.readiness?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = if (r.ready) BuffGreen else Cream, modifier = Modifier.padding(top = Space.xs)) }
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

/** What a commission asks, as short facts: the weapon type, its element and the least quality (`RequestUi`'s own fields). */
internal fun RequestUi.askFacts(): List<Fact> =
    listOfNotNull(Fact(family).takeIf { family.isNotEmpty() }, element?.let { Fact(it.word()) }, Fact("$minQuality or more", "Quality"))

/**
 * Where a commission stands, as words: an offer that waits for an answer (gold), accepted, ready for End Day ("✓",
 * green) or due today (ember). The word says it; the colour only repeats it.
 */
@Composable
internal fun StatusWord(r: RequestUi, modifier: Modifier = Modifier) {
    val color = when { r.offered -> Gold; r.ready -> BuffGreen; r.daysLeft <= 0 -> Ember; else -> CreamMuted }
    Text((if (r.ready) "✓ " else "") + r.status, style = MaterialTheme.typography.labelMedium, color = color, textAlign = TextAlign.End, modifier = modifier.padding(start = Space.sm))
}

/**
 * A standing want: the line opens the hero's sheet; under it, "Forge this" (the forge opens on the family asked for, with who wants it) or,
 * once a listed weapon answers it, a mark that says so. Stacked, so the sentence keeps the row's width at any text size.
 * A want is not an order: a row divided by a hairline, never a commission's plate.
 */
@Composable
internal fun WantRow(want: WantUi, onOpenHero: (HeroId) -> Unit, onForge: (HeroId) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().hairline().padding(bottom = Space.xs).testTag("want_${want.heroId.value}")) {
        Row(
            Modifier.fillMaxWidth().clickable(onClickLabel = "Open details", role = Role.Button) { onOpenHero(want.heroId) }.heightIn(min = 48.dp).padding(start = Space.md, top = Space.sm, bottom = Space.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(want.line, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Chevron(ChevronWay.RIGHT, Modifier.padding(start = Space.sm), tint = CreamMuted)
        }
        if (want.answered) Text("✓ A suitable weapon is on the shelf", style = MaterialTheme.typography.labelLarge, color = BuffGreen, modifier = Modifier.padding(start = Space.md, end = Space.md, bottom = Space.sm).testTag("want_answered_${want.heroId.value}"))
        else SecondaryActionButton("Forge this", { onForge(want.heroId) }, Modifier.padding(start = Space.md, end = Space.md, bottom = Space.sm).testTag("forge_want_${want.heroId.value}"))
    }
}

@Composable
private fun DemandLine(row: DemandRow) {
    Column(Modifier.fillMaxWidth().padding(vertical = 3.dp).semantics(mergeDescendants = true) {}) {
        Row(verticalAlignment = Alignment.Top) {
            Text(row.label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f).padding(end = Space.sm))
            Text(row.value, style = MaterialTheme.typography.titleSmall, color = Cream, textAlign = TextAlign.End)
        }
        row.detail?.let { Secondary(it) }
    }
}
