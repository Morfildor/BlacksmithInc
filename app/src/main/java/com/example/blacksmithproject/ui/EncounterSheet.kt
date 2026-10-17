package com.example.blacksmithproject.ui

import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Spacer
import com.tinyblacksmith.core.content.Depth
import com.example.blacksmithproject.R
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
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
import com.example.blacksmithproject.ui.shopday.FaceUi
import com.example.blacksmithproject.ui.shopday.PersonChip
import com.example.blacksmithproject.ui.theme.BronzeDeep
import com.example.blacksmithproject.ui.theme.ForgeSlot
import com.example.blacksmithproject.ui.theme.Cream
import com.example.blacksmithproject.ui.theme.CreamMuted
import com.example.blacksmithproject.ui.theme.Gold
import com.example.blacksmithproject.ui.theme.SectionLabel
import com.example.blacksmithproject.ui.theme.Space
import com.tinyblacksmith.core.content.RelicDef
import com.tinyblacksmith.core.engine.Encounters
import com.tinyblacksmith.core.engine.Relics
import com.tinyblacksmith.core.model.EncounterStatus
import com.tinyblacksmith.core.model.GameState

/** What the Shop says of a visitor who has had an answer today: the answer in the engine's own words. Null while they wait, and when nobody came. */
fun visitorNote(view: Encounters.View?): String? = view?.instance?.takeIf { it.status == EncounterStatus.RESOLVED }?.let { inst ->
    "${view.name}: " + (view.options.firstOrNull { it.id == inst.chosen }?.label?.let { "you answered \"$it\"." } ?: "answered.")
}

/**
 * The visitor on the Shop: who is at the forge and until when, as one row like the Shop's other doors (no plate). The
 * whole row opens the sheet ("Hear them out"); nothing is answered from here, and the gold plate stays End Day's.
 */
@Composable
fun VisitorCard(view: Encounters.View, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().testTag("visitor_card")) {
        Row(
            Modifier.fillMaxWidth().hairline().clickable(onClickLabel = "Hear them out", role = Role.Button, onClick = onOpen)
                .heightIn(min = 56.dp).padding(horizontal = Space.xs, vertical = Space.sm).testTag("visitor_open"),
            horizontalArrangement = Arrangement.spacedBy(Space.md), verticalAlignment = Alignment.CenterVertically,
        ) {
            PixelIcon(R.drawable.icon_visitor, 48.dp)
            Column(Modifier.weight(1f)) {
                Text("A visitor at the forge", style = SectionLabel, color = CreamMuted)
                Text(view.name, style = MaterialTheme.typography.titleMedium, color = Cream, modifier = Modifier.semantics { heading() })
                // When they go is said here, before the sheet: an unanswered visitor is told the default at End Day.
                Secondary("Waits for your answer. Leaves at End Day.")
                Row(Modifier.padding(top = Space.xs), verticalAlignment = Alignment.CenterVertically) {
                    Text("Hear them out", style = MaterialTheme.typography.labelLarge, color = Cream)
                    Chevron(ChevronWay.RIGHT, Modifier.padding(start = Space.xs), tint = CreamMuted, size = 18.dp)
                }
            }
        }
    }
}

/**
 * The visitor as a bottom sheet. Everything on it is the engine's view of the saved visitor; the sheet keeps only which
 * answer is marked, and nothing is sent until Commit. An answer that leaves the visitor at the forge (the merchant's
 * inspection) comes back as a new [view] and the sheet stays open on it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EncounterSheet(view: Encounters.View, state: GameState, busy: Boolean, onCommit: (String) -> Unit, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    // A sheet is its own window: it does not inherit the root's resource-id exposure that the emulator scripts rely on.
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        dragHandle = { BottomSheetDefaults.DragHandle(width = 48.dp) },
        modifier = modifier.semantics { testTagsAsResourceId = true }.testTag("visitor_sheet"),
    ) { EncounterContent(view, state, busy, onCommit, onDismiss) }
}

/** The body of the visitor sheet: one scrolling column, usable outside a sheet (tests). */
@Composable
fun EncounterContent(view: Encounters.View, state: GameState, busy: Boolean, onCommit: (String) -> Unit, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    val inst = view.instance
    // The answers change after the inspection, so what was marked before it is forgotten with them.
    var marked by rememberSaveable(inst.id, inst.inspected) { mutableStateOf<String?>(null) }
    val selected = view.options.firstOrNull { it.id == marked && it.blocked == null }
    Column(modifier.fillMaxWidth().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(horizontal = Space.md).padding(bottom = Space.lg)) {
        Text("A visitor at the forge", style = SectionLabel, color = CreamMuted)
        Text(view.name, style = MaterialTheme.typography.titleLarge, color = Gold, modifier = Modifier.padding(top = Space.xs).semantics { heading() })

        // Who and what the visit is about, as the save has them today. Decorative: the text below names them too.
        listOfNotNull(inst.heroId, inst.otherHeroId).mapNotNull { state.heroes[it] }.forEach { h ->
            PersonChip(FaceUi(null, h.fullName, hero = h), onOpenHero = {}, modifier = Modifier.padding(top = Space.xs))
        }
        (inst.weaponId?.let { state.weapons[it] } ?: inst.blade?.takeIf { inst.inspected })?.let { w ->
            Row(Modifier.padding(top = Space.xs).heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                Box(Modifier.background(ForgeSlot).border(1.dp, BronzeDeep).padding(2.dp)) { WeaponSprite(w, size = 44.dp) }
                Text(w.name, style = MaterialTheme.typography.titleSmall)
            }
        }

        Text(view.text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = Space.sm).testTag("visitor_text"))

        view.options.forEach { o ->
            val open = o.blocked == null
            // A choice row: a tap marks the answer, the gold action sends it. What it brings ("+") and what it costs ("−"),
            // each on a line of its own, are read before choosing; an answer that cannot be taken says why.
            ChoiceRow(selected?.id == o.id, { marked = o.id }, Modifier.padding(top = Space.sm).testTag("visitor_option_${o.id}"), enabled = open && !busy) {
                Text(o.label, style = MaterialTheme.typography.titleSmall)
                if (o.effect.isNotEmpty()) EffectBullets(EffectKind.BUFF, listOf(o.effect))
                if (o.cost == "Free") Secondary("Free") else EffectBullets(EffectKind.FLAW, listOf("Costs ${o.cost}"))
                o.blocked?.let { Text("Not possible: $it", style = MaterialTheme.typography.bodySmall, color = EffectKind.FLAW.color, modifier = Modifier.testTag("visitor_blocked_${o.id}")) }
            }
        }
        if (view.options.none { it.id != Encounters.PASS && it.blocked == null }) Secondary("You can't take the other options today. The free option is still available.", Modifier.padding(top = Space.sm))
        Secondary("If you don't answer by End Day: ${view.defaultLabel}.", Modifier.padding(top = Space.sm).testTag("visitor_default"))

        FlowRow(Modifier.fillMaxWidth().padding(top = Space.md), horizontalArrangement = Arrangement.spacedBy(Space.sm), verticalArrangement = Arrangement.spacedBy(Space.xs)) {
            // V2: the action says the answer it sends; with none marked it asks for one. V3: the cost is said again on it.
            PrimaryActionButton(
                selected?.label ?: "Choose an answer", { selected?.let { onCommit(it.id) } }, Modifier.weight(1f).testTag("visitor_commit"), enabled = selected != null && !busy,
                detail = selected?.cost?.takeIf { it != "Free" }?.let { "Costs $it" },
            )
            SecondaryActionButton("Later", onDismiss, Modifier.heightIn(min = 52.dp).testTag("visitor_later"))
        }
    }
}

/**
 * The workshop's relics on the Shop: one row per held relic with where it stands today; a tap on the row (or its ringed
 * "i") opens its card, with what it does. The free slots are one line, and with an offer waiting, the row that opens it again.
 */
@Composable
fun RelicRows(relics: List<Relics.View>, slots: Int, offerPending: Boolean, onOpenOffer: () -> Unit, modifier: Modifier = Modifier) {
    val inspector = LocalInspector.current
    Column(modifier.fillMaxWidth().testTag("shop_relics")) {
        SectionTitle("Workshop relics · ${relics.size} of $slots")
        relics.forEach { r ->
            key(r.id) {
                val anchor = remember { InspectAnchor() }
                val tip = { relicTip(r) }
                Row(
                    Modifier.fillMaxWidth().hairline().inspectAnchor(anchor)
                        .clickable(enabled = inspector != null, onClickLabel = "Inspect", role = Role.Button) { inspector?.show(tip(), anchor.rect) }
                        .heightIn(min = 56.dp).padding(vertical = Space.sm).testTag("relic_held_${r.id}"),
                    horizontalArrangement = Arrangement.spacedBy(Space.sm), verticalAlignment = Alignment.CenterVertically,
                ) {
                    PixelImage(relicIcon(r.id), wholePixelDp(48, 44.dp), description = null)
                    Column(Modifier.weight(1f)) {
                        Text(r.name, style = MaterialTheme.typography.titleSmall)
                        Secondary(r.status)
                    }
                    InfoButton(r.name, tip, anchor = anchor, tag = "relic_held_${r.id}_info")
                }
            }
        }
        // V2: the free slots are one line, not a plate each: "2 empty slots".
        val empty = (slots - relics.size).coerceAtLeast(0)
        if (empty > 0) Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(vertical = Space.xs), horizontalArrangement = Arrangement.spacedBy(Space.sm), verticalAlignment = Alignment.CenterVertically) {
            PixelImage(R.drawable.relic_slot_empty, wholePixelDp(48, 28.dp), description = null)
            Secondary(if (empty == 1) "1 empty slot" else "$empty empty slots")
        }
        if (offerPending) SecondaryActionButton("Choose your offered relic", onOpenOffer, Modifier.fillMaxWidth().padding(top = Space.xs).testTag("relic_reopen"))
    }
}

/** A held relic as an inspect card: what it does in the catalog's words, and where it stands today (`Relics.View`). */
internal fun relicTip(r: Relics.View): TipUi = TipUi(
    name = r.name, kind = "Workshop relic", icon = relicIcon(r.id),
    sections = listOf(TipSection("What it does", listOf(TipLine(r.description))), TipSection("Today", listOf(TipLine(r.status)))),
    tag = "relic_inspect",
)

/**
 * The relic offer as choice rows: a tap marks a relic, and the one gold action takes it. With every slot taken, "Choose
 * what to replace" goes on to the held relics, marked the same way; nothing is sent before "Replace …". "Take none"
 * gives the offer up; "Decide later" keeps it for another day.
 */
@Composable
fun RelicDialog(
    offer: List<RelicDef>, held: List<Relics.View>, slots: Int, busy: Boolean,
    onChoose: (relicId: String, replaceId: String?) -> Unit, onDecline: () -> Unit, onLater: () -> Unit,
) {
    var marked by rememberSaveable { mutableStateOf<String?>(null) }
    var replacing by rememberSaveable { mutableStateOf(false) }
    var old by rememberSaveable { mutableStateOf<String?>(null) }
    val taking = offer.firstOrNull { it.id == marked }
    val full = held.size >= slots
    val out = held.firstOrNull { it.id == old }
    if (!replacing || taking == null) ForgeDialog(
        "A relic for the workshop", onDismiss = {}, dismissOnClickOutside = false,
        actions = {
            InlineActionButton("Take none", onDecline, Modifier.heightIn(min = 48.dp).testTag("relic_none"), enabled = !busy)
            InlineActionButton("Decide later", onLater, Modifier.heightIn(min = 48.dp).testTag("relic_later"))
            PrimaryActionButton(
                when { taking == null -> "Take"; full -> "Choose what to replace"; else -> "Take ${taking.name}" },
                { if (taking != null) { if (full) replacing = true else onChoose(taking.id, null) } },
                Modifier.testTag("relic_take"), enabled = taking != null && !busy,
            )
        },
    ) {
        if (full) Text("Your relic slots are full. A new relic replaces one you hold, and that one's progress is lost.", style = MaterialTheme.typography.bodyMedium)
        offer.forEach { r ->
            ChoiceRow(taking?.id == r.id, { marked = r.id }, Modifier.testTag("relic_${r.id}"), enabled = !busy, leading = { PixelImage(relicIcon(r.id), wholePixelDp(48, 44.dp), description = null) }) {
                Text(r.name, style = MaterialTheme.typography.titleSmall)
                EffectBullets(EffectKind.BUFF, listOf(r.description))
            }
        }
    }
    else ForgeDialog(
        "Which relic will ${taking.name} replace?", onDismiss = {}, dismissOnClickOutside = false,
        actions = {
            InlineActionButton("Back", { replacing = false; old = null }, Modifier.heightIn(min = 48.dp).testTag("relic_back"))
            PrimaryActionButton(out?.let { "Replace ${it.name}" } ?: "Replace", { out?.let { onChoose(taking.id, it.id) } }, Modifier.testTag("relic_replace"), enabled = out != null && !busy)
        },
    ) {
        held.forEach { r ->
            ChoiceRow(out?.id == r.id, { old = r.id }, Modifier.testTag("relic_replace_${r.id}"), enabled = !busy, leading = { PixelImage(relicIcon(r.id), wholePixelDp(48, 44.dp), description = null) }) {
                Text(r.name, style = MaterialTheme.typography.titleSmall)
                EffectBullets(EffectKind.FLAW, listOf("Lost: ${r.status}"))
            }
        }
    }
}

/** The picture of a relic by its id; a relic without art gets the empty-slot plate. */
private fun relicIcon(id: String): Int = when (id) {
    Depth.SALVAGERS_CRUCIBLE -> R.drawable.relic_salvagers_crucible
    Depth.TEMPERING_LEDGER -> R.drawable.relic_tempering_ledger
    Depth.COLLECTORS_SEAL -> R.drawable.relic_collectors_seal
    Depth.ASHEN_BELLOWS -> R.drawable.relic_ashen_bellows
    else -> R.drawable.relic_slot_empty
}
