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
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.blacksmithproject.ui.shopday.FaceUi
import com.example.blacksmithproject.ui.shopday.PersonChip
import com.example.blacksmithproject.ui.theme.BronzeDeep
import com.example.blacksmithproject.ui.theme.ForgeSlot
import com.example.blacksmithproject.ui.theme.Gold
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

/** The visitor on the Shop: who is at the forge and until when. Opens the sheet; nothing is answered from here. */
@Composable
fun VisitorCard(view: Encounters.View, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    FramedPanel(modifier = modifier.fillMaxWidth().testTag("visitor_card")) {
        Row(horizontalArrangement = Arrangement.spacedBy(Space.md), verticalAlignment = Alignment.CenterVertically) {
            PixelImage(R.drawable.icon_visitor, wholePixelDp(80, 48.dp), description = null)
            Column(Modifier.weight(1f)) {
                Text("A VISITOR AT THE FORGE", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = Gold)
                Text(view.name, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = Space.xs).semantics { heading() })
            }
        }
        Secondary("Leaves at End Day.", Modifier.padding(top = Space.xs))
        PrimaryActionButton("Hear them out", onOpen, Modifier.fillMaxWidth().padding(top = Space.sm).testTag("visitor_open"))
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
        Text("A VISITOR AT THE FORGE", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = Gold)
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
            Row(
                Modifier.fillMaxWidth().padding(top = Space.sm).forgeRow()
                    .selectable(selected = selected?.id == o.id, enabled = open && !busy, role = Role.RadioButton) { marked = o.id }
                    .heightIn(min = 56.dp).padding(horizontal = Space.sm, vertical = Space.sm).testTag("visitor_option_${o.id}"),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.sm),
            ) {
                RadioButton(selected = selected?.id == o.id, onClick = null, enabled = open)
                Column(Modifier.weight(1f)) {
                    Text(o.label, style = MaterialTheme.typography.titleSmall, color = if (open) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Cost: ${o.cost}", style = MaterialTheme.typography.bodyMedium, color = if (open) Gold else MaterialTheme.colorScheme.onSurfaceVariant)
                    if (o.effect.isNotEmpty()) Text(o.effect, style = MaterialTheme.typography.bodyMedium, color = if (open) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
                    o.blocked?.let { Text("Not possible: $it", style = MaterialTheme.typography.bodySmall, color = EffectKind.FLAW.color, modifier = Modifier.testTag("visitor_blocked_${o.id}")) }
                }
            }
        }
        if (view.options.none { it.id != Encounters.PASS && it.blocked == null }) Secondary("You can't take the other options today. The free option is still available.", Modifier.padding(top = Space.sm))
        Secondary("No answer by End Day means ${view.defaultLabel.lowercase()}.", Modifier.padding(top = Space.sm).testTag("visitor_default"))

        FlowRow(Modifier.fillMaxWidth().padding(top = Space.md), horizontalArrangement = Arrangement.spacedBy(Space.sm), verticalArrangement = Arrangement.spacedBy(Space.xs)) {
            PrimaryActionButton("Commit", { selected?.let { onCommit(it.id) } }, Modifier.weight(1f).testTag("visitor_commit"), enabled = selected != null && !busy)
            SecondaryActionButton("Later", onDismiss, Modifier.heightIn(min = 52.dp).testTag("visitor_later"))
        }
    }
}

/**
 * The workshop's relics on the Shop: one row per slot, a held relic with where it stands today (tap for what it does),
 * an empty slot as empty. With an offer waiting, the row that opens it again.
 */
@Composable
fun RelicRows(relics: List<Relics.View>, slots: Int, offerPending: Boolean, onOpenOffer: () -> Unit, modifier: Modifier = Modifier) {
    var open by rememberSaveable { mutableStateOf<String?>(null) }
    Column(modifier.fillMaxWidth().testTag("shop_relics")) {
        Row(horizontalArrangement = Arrangement.spacedBy(Space.sm), verticalAlignment = Alignment.Bottom) {
            PixelImage(R.drawable.icon_relic, wholePixelDp(48, 28.dp), description = null, modifier = Modifier.padding(bottom = Space.sm))
            SectionTitle("Workshop relics · ${relics.size} of $slots")
        }
        relics.forEach { r ->
            Column(
                Modifier.fillMaxWidth().padding(vertical = Space.xs).forgeRow()
                    .clickable(onClickLabel = if (open == r.id) "Hide what it does" else "Show what it does", role = Role.Button) { open = if (open == r.id) null else r.id }
                    .heightIn(min = 48.dp).padding(horizontal = Space.md, vertical = Space.sm).testTag("relic_held_${r.id}"),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(Space.sm), verticalAlignment = Alignment.CenterVertically) {
                    PixelImage(relicIcon(r.id), wholePixelDp(48, 44.dp), description = null)
                    Column(Modifier.weight(1f)) {
                        Text(r.name, style = MaterialTheme.typography.titleSmall)
                        Secondary(r.status)
                    }
                }
                if (open == r.id) Text(r.description, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = Space.xs))
            }
        }
        repeat((slots - relics.size).coerceAtLeast(0)) {
            Box(Modifier.fillMaxWidth().padding(vertical = Space.xs).forgeRow().heightIn(min = 48.dp).padding(horizontal = Space.md, vertical = Space.sm), contentAlignment = Alignment.CenterStart) {
                Row(horizontalArrangement = Arrangement.spacedBy(Space.sm), verticalAlignment = Alignment.CenterVertically) {
                    PixelImage(R.drawable.relic_slot_empty, wholePixelDp(48, 44.dp), description = null)
                    Secondary("Empty slot")
                }
            }
        }
        if (offerPending) SecondaryActionButton("Choose your offered relic", onOpenOffer, Modifier.fillMaxWidth().padding(top = Space.xs).testTag("relic_reopen"))
    }
}

/**
 * The relic offer, after the blessing dialog's pattern. With every slot taken, the relic picked first asks which held
 * relic it replaces; nothing is sent before that second pick. "Take none" gives the offer up.
 */
@Composable
fun RelicDialog(
    offer: List<RelicDef>, held: List<Relics.View>, slots: Int, busy: Boolean,
    onChoose: (relicId: String, replaceId: String?) -> Unit, onDecline: () -> Unit, onLater: () -> Unit,
) {
    var picked by rememberSaveable { mutableStateOf<String?>(null) }
    val taking = offer.firstOrNull { it.id == picked }
    AlertDialog(
        modifier = Modifier.semantics { testTagsAsResourceId = true },
        onDismissRequest = {},
        title = { Text(if (taking == null) "A relic for the workshop" else "Which relic will you replace?") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(Space.sm)) {
                if (taking == null) {
                    if (held.size >= slots) Text("Your relic slots are full. Choose a new relic, then pick one to replace.", style = MaterialTheme.typography.bodyMedium)
                    offer.forEach { r ->
                        RelicChoice(r.name, r.description, "relic_${r.id}", !busy, relicIcon(r.id)) { if (held.size >= slots) picked = r.id else onChoose(r.id, null) }
                    }
                } else {
                    Text("${taking.name} replaces the relic you choose. You'll lose the old relic and its progress.", style = MaterialTheme.typography.bodyMedium)
                    held.forEach { r -> RelicChoice(r.name, r.status, "relic_replace_${r.id}", !busy, relicIcon(r.id)) { onChoose(taking.id, r.id) } }
                }
            }
        },
        confirmButton = {
            if (taking == null) InlineActionButton("Decide later", onLater, Modifier.heightIn(min = 48.dp).testTag("relic_later"))
            else InlineActionButton("Back", { picked = null }, Modifier.heightIn(min = 48.dp).testTag("relic_back"))
        },
        dismissButton = { if (taking == null) InlineActionButton("Take none", onDecline, Modifier.heightIn(min = 48.dp).testTag("relic_none"), enabled = !busy) },
    )
}

@Composable
private fun RelicChoice(name: String, detail: String, tag: String, enabled: Boolean, icon: Int, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, enabled = enabled, shape = MaterialTheme.shapes.small, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag(tag)) {
        PixelImage(icon, wholePixelDp(48, 44.dp), description = null)
        Spacer(Modifier.width(Space.sm))
        Column(Modifier.weight(1f)) {
            Text(name, style = MaterialTheme.typography.titleSmall)
            Text(detail, style = MaterialTheme.typography.bodySmall)
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
