package com.example.blacksmithproject.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.blacksmithproject.ForgeDraft
import com.example.blacksmithproject.GameViewModel
import com.example.blacksmithproject.R
import com.example.blacksmithproject.RecordsPage
import com.example.blacksmithproject.UiState
import com.example.blacksmithproject.ui.shopday.Backdrop
import com.example.blacksmithproject.ui.theme.Bronze
import com.example.blacksmithproject.ui.theme.BronzeContainer
import com.example.blacksmithproject.ui.theme.BronzeDeep
import com.example.blacksmithproject.ui.theme.BuffGreen
import com.example.blacksmithproject.ui.theme.Cream
import com.example.blacksmithproject.ui.theme.CreamMuted
import com.example.blacksmithproject.ui.theme.Ember
import com.example.blacksmithproject.ui.theme.FlawRed
import com.example.blacksmithproject.ui.theme.ForgeNight
import com.example.blacksmithproject.ui.theme.ForgePanel
import com.example.blacksmithproject.ui.theme.ForgePanelRaised
import com.example.blacksmithproject.ui.theme.ForgeSlot
import com.example.blacksmithproject.ui.theme.Gold
import com.example.blacksmithproject.ui.theme.GoldBright
import com.example.blacksmithproject.ui.theme.SceneDeep
import com.example.blacksmithproject.ui.theme.Space
import com.tinyblacksmith.core.content.ContentCatalog
import com.tinyblacksmith.core.content.MaterialCategory
import com.tinyblacksmith.core.crafting.Journal
import com.tinyblacksmith.core.model.ForgeMode
import com.tinyblacksmith.core.model.Journal as JournalModel
import com.tinyblacksmith.core.model.KnowledgeState
import com.tinyblacksmith.core.model.MaterialId
import com.tinyblacksmith.core.model.Risk
import com.tinyblacksmith.core.model.WeaponFamilyId

private const val NO_SLOT = "none"
private val Card = RoundedCornerShape(10.dp)
private val Chip = RoundedCornerShape(6.dp)

/**
 * The Forge as a workbench (GDD 12): the blade over the anvil, the recipe as three places under it, one tray of
 * choices for the place being filled, and what the journal knows of the pairing beside the recipe. One gold action
 * stays pinned at the foot. Choosing is free; only "Forge" sends a command. The place that is open and the scroll
 * position are kept while the player is on another destination.
 */
@Composable
fun ForgePanel(
    s: UiState.Playing, vm: GameViewModel, reducedMotion: Boolean, tip: Tips.Tip?,
    onOpenSupplies: () -> Unit = {}, onOpenBoard: () -> Unit = {}, onEndDay: () -> Unit = {},
) {
    val engine = vm.engine
    val d = s.draft
    val bench = remember(s.state, d, s.shop.requests) { engine.forgeWorkbench(s.state, d, s.shop.requests) }

    // null = the first empty place is open; NO_SLOT = no tray; otherwise the place the player opened.
    var opened by rememberSaveable { mutableStateOf<String?>(null) }
    val open = when (opened) { null -> bench.next; NO_SLOT -> null; else -> bench.slots.firstOrNull { it.slot.name == opened }?.slot }
    // "Forge this" or "Use recipe" was tapped elsewhere since this panel last looked: back to the first empty place.
    var revealed by rememberSaveable { mutableIntStateOf(0) }
    LaunchedEffect(s.forgeReveal) {
        if (s.forgeReveal > revealed) opened = null
        revealed = s.forgeReveal
    }

    Column(Modifier.fillMaxSize()) {
        EventStrip(s)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = Space.md).padding(bottom = Space.md)) {
            ForgeHeading(s.shop.requests.size, onOpenBoard, onOpenSupplies, { vm.selectRecords(RecordsPage.JOURNAL) }, onEndDay, s.busy)
            Workbench(bench.title, d, vm.engine.content, compact = open != null)
            bench.brief?.let { Brief(it, onOpenBoard) { vm.updateDraft { draft -> draft.copy(commissionId = null) } } }
            SlotRow(bench.slots.take(3), d, engine.content, open) { opened = if (open == it) NO_SLOT else it.name }
            if (bench.slots.size > 3) SlotRow(bench.slots.drop(3), d, engine.content, open) { opened = if (open == it) NO_SLOT else it.name }
            if (open != null) {
                val options = remember(s.state, d, open, s.shop.threat) { engine.forgeOptions(s.state, d, open, s.shop.threat) }
                Tray(open, options, engine.content, onOpenSupplies, onDone = { opened = NO_SLOT }) { option -> vm.updateDraft { engine.place(it, open, option) }; opened = null }
            }
            if (bench.notes.isNotEmpty()) FieldNotes(bench.notes) { vm.selectRecords(RecordsPage.JOURNAL) }
            ForgingOptions(d) { change -> vm.updateDraft(change) }
            tip?.let { TipBanner(it, vm, Modifier.padding(top = Space.md)) }
        }
        ForgeAction(bench, s.busy, onOpenSupplies, onEndDay) { bench.command?.let(vm::dispatch) }
    }
}

/** When the siege comes, who brings it and what it is weak to, and how the forge stands: above the work, in two short lines. */
@Composable
private fun EventStrip(s: UiState.Playing) {
    val threat = s.shop.threat
    Column(Modifier.fillMaxWidth().background(SceneDeep).heightIn(min = 44.dp).padding(horizontal = Space.md, vertical = 6.dp).semantics(mergeDescendants = true) {}.testTag("forge_threat")) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(threat?.let { if (it.warned && !it.today) "Siege approaching · ${it.siege.removePrefix("Siege ")}" else it.siege } ?: "No siege in sight", style = MaterialTheme.typography.titleSmall, color = if (threat?.warned == true) Ember else Cream, modifier = Modifier.weight(1f))
            PixelImage(R.drawable.icon_integrity, wholePixelDp(24, 22.dp), description = null)
            Text("Forge health ${s.state.town.integrity}", style = MaterialTheme.typography.labelMedium, color = Cream)
        }
        listOfNotNull(threat?.matchup, threat?.outlook).takeIf { it.isNotEmpty() }?.let { Text(it.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = CreamMuted) }
    }
}

/** The destination's name, the way to the commission board, and the rarer actions behind "More". */
@Composable
private fun ForgeHeading(commissions: Int, onOpenBoard: () -> Unit, onOpenSupplies: () -> Unit, onOpenJournal: () -> Unit, onEndDay: () -> Unit, busy: Boolean) {
    Row(Modifier.fillMaxWidth().padding(top = Space.xs), verticalAlignment = Alignment.CenterVertically) {
        Text("Forge", style = MaterialTheme.typography.headlineMedium, color = Cream, modifier = Modifier.weight(1f).semantics { heading() })
        TextButton(onClick = onOpenBoard, modifier = Modifier.heightIn(min = 48.dp).testTag("forge_commissions")) {
            Text(if (commissions == 0) "Commissions" else "Commissions · $commissions", color = CreamMuted)
        }
        Box {
            var more by remember { mutableStateOf(false) }
            TextButton(onClick = { more = true }, modifier = Modifier.heightIn(min = 48.dp).testTag("forge_more")) { Text("⋯", style = MaterialTheme.typography.titleLarge, color = CreamMuted, modifier = Modifier.semantics { contentDescription = "More" }) }
            DropdownMenu(expanded = more, onDismissRequest = { more = false }, containerColor = ForgePanel) {
                DropdownMenuItem(text = { Text("Supplies") }, onClick = { more = false; onOpenSupplies() }, modifier = Modifier.testTag("forge_supplies"))
                DropdownMenuItem(text = { Text("Journal") }, onClick = { more = false; onOpenJournal() }, modifier = Modifier.testTag("forge_journal"))
                DropdownMenuItem(text = { Text("End day") }, enabled = !busy, onClick = { more = false; onEndDay() }, modifier = Modifier.testTag("forge_end_day"))
            }
        }
    }
}

/**
 * The room, the anvil and the blade being planned, as one framed place with its name on a plate under it. The preview
 * is the ordinary art of the weapon type and the augment's element: what a forge will really give (quality, properties,
 * a signature) is shown only by the result. [compact] while a tray is open under it, so the choices get the height.
 */
@Composable
private fun Workbench(title: String, d: ForgeDraft, content: ContentCatalog, compact: Boolean) {
    val large = LocalDensity.current.fontScale > 1.3f || LocalConfiguration.current.screenHeightDp < 620
    val height = if (compact || large) 88.dp else 132.dp
    val blade = wholePixelDp(56, if (compact || large) 56.dp else 104.dp)
    Column(Modifier.fillMaxWidth().testTag("forge_workbench").semantics(mergeDescendants = true) { contentDescription = "On the anvil: $title" }) {
        Box(Modifier.fillMaxWidth().height(height).clip(Card).border(1.dp, BronzeDeep, Card), contentAlignment = Alignment.BottomCenter) {
            Backdrop(R.drawable.bg_counter_forge, Modifier.matchParentSize().clearAndSetSemantics {}, dim = 0.35f, sink = 0.2f)
            PixelImage(R.drawable.anvil, wholePixelDp(109, if (compact || large) 72.dp else 109.dp), description = null, modifier = Modifier.padding(bottom = 2.dp).height(if (compact || large) 48.dp else 72.dp))
            d.familyId?.let { family ->
                val core = d.coreId ?: content.materials(MaterialCategory.CORE).first().id
                PixelImage(
                    Sprites.weapon(family, core, d.augmentId?.let { content.material(it).element }), blade, description = null,
                    modifier = Modifier.padding(bottom = if (compact || large) 22.dp else 30.dp).alpha(if (d.coreId != null) 1f else 0.55f),
                )
            }
        }
        Text(
            title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Normal, color = Cream, textAlign = TextAlign.Center, maxLines = 2,
            modifier = Modifier.fillMaxWidth().padding(horizontal = Space.md).padding(top = 6.dp).testTag("forge_title"),
        )
    }
}

/** The commission the draft was started from: what it asks, whether the draft answers it, and that nothing is promised. */
@Composable
private fun Brief(b: BriefUi, onOpen: () -> Unit, onClear: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = Space.sm).forgeRow().clickable(onClickLabel = "Open commission", onClick = onOpen).padding(start = Space.md).testTag("forge_for"), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(vertical = Space.sm)) {
            Text(b.title, style = MaterialTheme.typography.labelLarge, color = Cream)
            Text(
                b.asks.joinToString(" · ") { if (it.matches == true) "✓ ${it.text}" else it.text } + if (b.accepted) " · Accepted" else " · Not accepted",
                style = MaterialTheme.typography.bodySmall, color = CreamMuted,
            )
        }
        TextButton(onClick = onClear, modifier = Modifier.heightIn(min = 48.dp).testTag("forge_clear_target")) { Text("Clear target", color = Gold) }
    }
}

private fun slotArt(slot: RecipeSlot, d: ForgeDraft, content: ContentCatalog): Int? = when (slot) {
    RecipeSlot.WEAPON -> d.familyId?.let { Sprites.weapon(it, content.materials(MaterialCategory.CORE).first().id, null) }
    RecipeSlot.METAL -> d.coreId?.let(Sprites::material)
    RecipeSlot.AUGMENT -> d.augmentId?.let(Sprites::material)
    RecipeSlot.CATALYST -> d.catalystId?.let(Sprites::material)
    RecipeSlot.TECHNIQUE -> null
}

/** The places of the recipe side by side: the thing chosen as an object, its kind over it, its name under it. */
@Composable
private fun SlotRow(slots: List<SlotUi>, d: ForgeDraft, content: ContentCatalog, open: RecipeSlot?, onToggle: (RecipeSlot) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = Space.sm), horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
        slots.forEach { slot ->
            val active = open == slot.slot
            Column(
                Modifier.weight(1f).heightIn(min = 84.dp).clip(Card).background(if (active) BronzeContainer else ForgePanelRaised).border(if (active) 1.5.dp else 1.dp, if (active) Gold else BronzeDeep, Card)
                    .clickable(role = Role.Button) { onToggle(slot.slot) }.padding(horizontal = Space.xs, vertical = 6.dp).testTag("forge_slot_${slot.slot.name.lowercase()}")
                    .semantics(mergeDescendants = true) { contentDescription = "${slot.slot.label}: ${slot.value ?: "not chosen"}${slot.stock?.let { n -> ", $n in stock" }.orEmpty()}, ${if (active) "choosing" else "tap to change"}" },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(slot.slot.label, style = MaterialTheme.typography.labelSmall, color = CreamMuted, maxLines = 1)
                Box(Modifier.fillMaxWidth().padding(vertical = 2.dp), contentAlignment = Alignment.Center) {
                    val art = slotArt(slot.slot, d, content)
                    if (art != null) PixelImage(art, 40.dp, description = null)
                    else Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) { Text(if (slot.value != null) "✦" else "+", style = MaterialTheme.typography.titleMedium, color = if (slot.value != null) Gold else Bronze) }
                    slot.stock?.let { Text("$it", style = MaterialTheme.typography.labelSmall, color = if (it == 0) FlawRed else CreamMuted, modifier = Modifier.align(Alignment.CenterEnd).background(ForgeSlot, Chip).padding(horizontal = 6.dp, vertical = 4.dp)) }
                }
                Text(
                    slot.value ?: slot.slot.empty, style = MaterialTheme.typography.labelMedium, fontWeight = if (slot.value != null) FontWeight.SemiBold else null,
                    color = if (slot.value != null) Cream else CreamMuted, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

private fun optionArt(slot: RecipeSlot, option: OptionUi, content: ContentCatalog): Int? = when (slot) {
    RecipeSlot.WEAPON -> option.id?.let { Sprites.weapon(WeaponFamilyId(it), content.materials(MaterialCategory.CORE).first().id, null) }
    RecipeSlot.TECHNIQUE -> null
    else -> option.id?.let { Sprites.material(MaterialId(it)) }
}

/**
 * The choices for one place, as a grid of equal tiles: three across, two on a narrow screen or with large text. A tap
 * places a tile that can be used. A material that has run out stays in the tray, apart and dimmed: a tap on it says
 * what is missing and offers the way to buy it. What the chosen tile does is said once, under the grid.
 */
@Composable
private fun Tray(slot: RecipeSlot, options: List<OptionUi>, content: ContentCatalog, onRestock: () -> Unit, onDone: () -> Unit, onPick: (OptionUi) -> Unit) {
    val columns = if (LocalDensity.current.fontScale > 1.3f || LocalConfiguration.current.screenWidthDp < 340) 2 else 3
    var inspected by remember(slot) { mutableStateOf<OptionUi?>(null) }
    Column(Modifier.fillMaxWidth().padding(top = Space.sm).clip(Card).background(ForgePanelRaised).border(1.dp, BronzeDeep, Card).padding(horizontal = 12.dp).padding(bottom = 12.dp).testTag("forge_tray_${slot.name.lowercase()}")) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(slot.choose, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Normal, color = CreamMuted, modifier = Modifier.weight(1f).semantics { heading() })
            TextButton(onClick = onDone, modifier = Modifier.heightIn(min = 48.dp).testTag("forge_tray_done")) { Text("Done", color = CreamMuted) }
        }
        @Composable
        fun grid(tiles: List<OptionUi>) = tiles.chunked(columns).forEach { row ->
            Row(Modifier.fillMaxWidth().padding(bottom = Space.sm), horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                row.forEach { o -> OptionTile(o, optionArt(slot, o, content), Modifier.weight(1f)) { if (o.usable) onPick(o) else inspected = o } }
                repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
        grid(options.filter { it.usable })
        val out = options.filter { !it.usable }
        if (out.isNotEmpty()) {
            Text("Out of stock", style = MaterialTheme.typography.labelMedium, color = CreamMuted, modifier = Modifier.padding(bottom = Space.xs))
            grid(out)
        }
        val shown = inspected ?: options.firstOrNull { it.selected && it.id != null }
        if (shown == null) Text(slot.hint, style = MaterialTheme.typography.bodySmall, color = CreamMuted)
        else Row(Modifier.fillMaxWidth().testTag("forge_tray_detail"), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(if (shown.usable) listOfNotNull(shown.name, shown.detail).joinToString(" · ") else "No ${shown.name} left", style = MaterialTheme.typography.bodyMedium, color = Cream)
                shown.mark?.let { Text("${it.kind.sign} ${it.label}", style = MaterialTheme.typography.bodySmall, color = it.kind.color, modifier = Modifier.testTag("forge_mark")) }
            }
            if (!shown.usable) TextButton(onClick = onRestock, modifier = Modifier.heightIn(min = 48.dp).testTag("forge_restock_tray")) { Text("Restock", color = Gold) }
        }
    }
}

@Composable
private fun OptionTile(o: OptionUi, art: Int?, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier.heightIn(min = 72.dp).clip(Chip).background(ForgeNight).border(if (o.selected) 1.5.dp else 1.dp, if (o.selected) Gold else BronzeDeep, Chip)
            .clickable(role = Role.Button, onClick = onClick).alpha(if (o.usable) 1f else 0.5f).testTag("forge_option_${o.id ?: "none"}")
            .semantics(mergeDescendants = true) {
                selected = o.selected
                contentDescription = listOfNotNull(o.name, o.stock?.let { if (it == 0) "none left" else "$it in stock" }, o.mark?.label).joinToString(", ")
            },
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = Space.xs, vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            if (art != null) PixelImage(art, 36.dp, description = null)
            Text(o.name, style = MaterialTheme.typography.labelMedium, color = Cream, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp))
        }
        o.stock?.let { Text("×$it", style = MaterialTheme.typography.labelSmall, color = CreamMuted, modifier = Modifier.align(Alignment.TopEnd).padding(horizontal = Space.xs, vertical = 2.dp)) }
        o.mark?.let { Text(it.kind.sign, style = MaterialTheme.typography.titleSmall, color = it.kind.color, modifier = Modifier.align(Alignment.TopStart).padding(horizontal = 6.dp)) }
    }
}

private fun KnowledgeState.glyph() = when (this) {
    KnowledgeState.UNKNOWN -> "?"; KnowledgeState.OBSERVED -> "◐"; KnowledgeState.UNDERSTOOD -> "●"; KnowledgeState.SIGNATURE_DISCOVERED -> "✦"
}

/**
 * What the journal knows of this recipe, beside the recipe: the two pairings apart, each with its stage in words and a
 * sign, and only what `Journal.hint` allows. Never one score for both, and nothing about the siege.
 */
@Composable
fun FieldNotes(notes: List<NoteUi>, modifier: Modifier = Modifier, onOpenJournal: (() -> Unit)? = null) {
    val stacked = LocalDensity.current.fontScale > 1.3f
    Column(modifier.fillMaxWidth().padding(top = Space.sm).clip(Card).background(SceneDeep).border(1.dp, BronzeDeep, Card).padding(horizontal = 12.dp).padding(bottom = 12.dp).testTag("forge_notes")) {
        Row(Modifier.heightIn(min = 40.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Field notes", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Normal, color = CreamMuted, modifier = Modifier.weight(1f).semantics { heading() })
            if (onOpenJournal != null) Text("Open book ›", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Normal, color = CreamMuted, modifier = Modifier.clickable(role = Role.Button, onClick = onOpenJournal).padding(vertical = Space.sm).testTag("forge_notes_journal"))
        }
        if (stacked) notes.forEachIndexed { i, n -> FieldNote(i, n, Modifier.fillMaxWidth().padding(top = if (i > 0) Space.sm else 0.dp)) }
        else Row(Modifier.height(IntrinsicSize.Min)) {
            notes.forEachIndexed { i, n ->
                if (i > 0) Box(Modifier.padding(horizontal = 12.dp).width(1.dp).fillMaxHeight().background(BronzeDeep))
                FieldNote(i, n, Modifier.weight(1f))
            }
        }
    }
}

/** An untried pairing leads with its stage; a known one with what the journal may say of it, its stage under it. */
@Composable
private fun FieldNote(i: Int, n: NoteUi, modifier: Modifier) {
    val untried = n.state == KnowledgeState.UNKNOWN
    Column(modifier.testTag("forge_note_$i").semantics(mergeDescendants = true) { contentDescription = "${n.label}, ${n.subject}, ${n.stage}: ${n.hint}" }) {
        Text(n.label, style = MaterialTheme.typography.labelMedium, color = CreamMuted)
        Text(
            if (untried) "? ${n.stage}" else n.hint, style = MaterialTheme.typography.titleSmall,
            color = when { untried -> Gold; n.tone == EffectKind.NEUTRAL -> Cream; else -> n.tone.color }, modifier = Modifier.padding(vertical = 2.dp),
        )
        Text(if (untried) n.hint else "${n.state.glyph()} ${n.stage}", style = MaterialTheme.typography.bodySmall, color = CreamMuted)
    }
}

/** Quick or Advanced as a two-part toggle (its cost is on the Forge action), and risk as one compact choice whose menu says what each means. */
@Composable
private fun ForgingOptions(d: ForgeDraft, onChange: ((ForgeDraft) -> ForgeDraft) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = Space.sm), horizontalArrangement = Arrangement.spacedBy(Space.sm), verticalAlignment = Alignment.CenterVertically) {
        Row(Modifier.clip(Card).border(1.dp, BronzeDeep, Card).padding(4.dp)) {
            ForgeMode.entries.forEach { mode ->
                val on = d.mode == mode
                Box(
                    Modifier.heightIn(min = 48.dp).clip(Chip).background(if (on) ForgePanelRaised else ForgeNight)
                        .clickable(role = Role.RadioButton) { onChange { it.copy(mode = mode, catalystId = it.catalystId?.takeIf { mode == ForgeMode.ADVANCED }, technique = it.technique?.takeIf { mode == ForgeMode.ADVANCED }) } }
                        .padding(horizontal = 12.dp).testTag("forge_mode_${mode.name.lowercase()}").semantics { selected = on },
                    contentAlignment = Alignment.Center,
                ) { Text(if (mode == ForgeMode.QUICK) "Quick" else "Advanced", style = MaterialTheme.typography.labelLarge, color = if (on) Cream else CreamMuted) }
            }
        }
        Spacer(Modifier.weight(1f))
        Choice("Risk", riskName(d.risk), "forge_risk", Modifier.width(132.dp), Risk.entries.map { Triple(riskName(it), riskExplanation(it), it) }) { risk -> onChange { it.copy(risk = risk) } }
    }
}

@Composable
private fun <T> Choice(label: String, value: String, tag: String, modifier: Modifier, choices: List<Triple<String, String, T>>, onPick: (T) -> Unit) {
    Box(modifier) {
        var open by remember { mutableStateOf(false) }
        Row(
            Modifier.fillMaxWidth().heightIn(min = 48.dp).clip(Card).border(1.dp, BronzeDeep, Card).clickable(role = Role.DropdownList) { open = true }.padding(horizontal = Space.md, vertical = 6.dp).testTag(tag)
                .semantics(mergeDescendants = true) { contentDescription = "$label: $value, tap to change" },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.labelSmall, color = CreamMuted)
                Text(value, style = MaterialTheme.typography.labelLarge, color = Cream)
            }
            Text("▾", color = CreamMuted)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, containerColor = ForgePanel) {
            choices.forEach { (name, detail, choice) ->
                DropdownMenuItem(
                    text = { Column(Modifier.padding(vertical = Space.xs).width(240.dp)) { Text(name, color = Cream); Text(detail, style = MaterialTheme.typography.bodySmall, color = CreamMuted) } },
                    onClick = { open = false; onPick(choice) }, modifier = Modifier.testTag("${tag}_${choice.toString().lowercase()}"),
                )
            }
        }
    }
}

/**
 * Pinned at the foot: the one thing in the way (with the way past it beside it), or the price of overwork, then the
 * one gold action with its energy cost in its own label. When no forge is possible today, End day stands beside it.
 */
@Composable
private fun ForgeAction(bench: ForgeWorkbenchUi, busy: Boolean, onRestock: () -> Unit, onEndDay: () -> Unit, onForge: () -> Unit) {
    val a = bench.action
    Column(
        Modifier.fillMaxWidth().background(ForgePanel).drawBehind { drawLine(BronzeDeep, Offset.Zero, Offset(size.width, 0f), strokeWidth = 1.dp.toPx()) }
            .padding(horizontal = Space.md).padding(top = Space.xs, bottom = Space.sm),
    ) {
        if (a.note != null) Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(a.note, style = MaterialTheme.typography.bodySmall, color = if (a.enabled) Cream else CreamMuted, modifier = Modifier.weight(1f).padding(vertical = Space.xs).testTag("forge_note"))
            if (a.restock != null) TextButton(onClick = onRestock, modifier = Modifier.heightIn(min = 48.dp).testTag("forge_restock")) { Text("Restock", color = Gold) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
            val enabled = a.enabled && !busy
            PrimaryActionButton(
                a.label, onForge,
                // A disabled button says why, so a screen reader is not left with a dead action.
                Modifier.weight(1f).testTag("forge_weapon").semantics { if (!enabled && a.note != null) contentDescription = "${a.label}, unavailable: ${a.note}" },
                enabled = enabled,
            )
            if (a.endDay) SecondaryActionButton("End day", onEndDay, Modifier.weight(1f).testTag("end_day"), enabled = !busy)
        }
    }
}

private fun riskName(r: Risk) = Labels.risk(r).substringBefore(" —")

/** Live affinity hint: an icon per knowledge state (never colour alone) plus the journal's descriptive text. */
@Composable
fun AffinityHint(journal: JournalModel, content: ContentCatalog, key: String) {
    val state = journal.state(key)
    val (glyph, stateLabel) = when (state) {
        KnowledgeState.UNKNOWN -> "?" to "unknown"
        KnowledgeState.OBSERVED -> "◐" to "observed"
        KnowledgeState.UNDERSTOOD -> "●" to "understood"
        KnowledgeState.SIGNATURE_DISCOVERED -> "✦" to "signature"
    }
    val subject = Journal.subjectName(content, key)
    val hint = Journal.hint(journal, content, key)
    Row(
        Modifier.padding(vertical = 6.dp).semantics(mergeDescendants = true) { contentDescription = "$subject, $stateLabel: $hint" },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Bronze, not ember: on navy an ember disc read as an error badge.
        Surface(shape = CircleShape, color = BronzeContainer, contentColor = GoldBright, modifier = Modifier.size(32.dp)) {
            Box(contentAlignment = Alignment.Center) { Text(glyph, style = MaterialTheme.typography.titleSmall) }
        }
        Spacer(Modifier.width(12.dp))
        Column {
            Text("$subject: $hint", style = MaterialTheme.typography.bodyMedium)
            Secondary(stateLabel)
        }
    }
}

private fun riskExplanation(r: Risk): String = when (r) {
    Risk.SAFE -> "Steady work with few surprises. Flaws are rare, but so are brilliant results."
    Risk.BALANCED -> "The usual gamble: some flaws, some brilliance, mostly honest work."
    Risk.RECKLESS -> "Push the metal hard. Brilliance comes more often, and so do flaws. Every forge still yields a usable weapon."
}
