package com.example.blacksmithproject.ui

import com.tinyblacksmith.core.content.Depth
import com.tinyblacksmith.core.config.BalanceConfig
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Switch
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.semantics.testTagsAsResourceId
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
import androidx.compose.ui.unit.dp
import com.example.blacksmithproject.ForgeDraft
import com.example.blacksmithproject.GameViewModel
import com.example.blacksmithproject.R
import com.example.blacksmithproject.RecordsPage
import com.example.blacksmithproject.Sheet
import com.example.blacksmithproject.UiState
import com.example.blacksmithproject.ui.shopday.Backdrop
import com.example.blacksmithproject.ui.theme.Bronze
import com.example.blacksmithproject.ui.theme.BronzeContainer
import com.example.blacksmithproject.ui.theme.BronzeDeep
import com.example.blacksmithproject.ui.theme.BronzeLine
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
import com.tinyblacksmith.core.model.WeaponId
import com.tinyblacksmith.core.engine.GameEngine
import com.tinyblacksmith.core.engine.Technique

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
    onOpenSupplies: (MaterialId?) -> Unit = {}, onOpenBoard: () -> Unit = {}, onEndDay: () -> Unit = {},
    /** Opens the read-only list of what End Day sets in motion (the Shop's "What happens"). */
    onEndDayPreview: () -> Unit = {},
) {
    // What End Day would leave behind or bring on, said beside both of the Forge's ways to it.
    // V2: the Forge's End Day carries the same one line as the Shop's; the full list is on the Shop ("What happens").
    val endDayNote = endDayHeadline(endDayLines(s)).orEmpty()
    val engine = vm.engine
    val d = s.draft
    // Asked for and still possible: a draft that has lost its bellows since forges without them, and the outlook says the same.
    val bellows = d.bellows && bellowsBlocked(s, engine.config) == null
    val bench = remember(s.state, d, s.shop.requests, bellows) { engine.forgeWorkbench(s.state, d, s.shop.requests, bellows) }
    // The rehearsal of the recipe on the anvil, open over the Forge.
    var rehearsing by rememberSaveable { mutableStateOf(false) }
    // The recipe's full study (V3 details): every ingredient, what each costs, how the effects work together, each chance.
    var details by rememberSaveable { mutableStateOf(false) }
    // Restored open over a recipe that is no longer whole: closed, so it does not spring open when the recipe is completed again.
    LaunchedEffect(bench.outlook == null) { if (bench.outlook == null) { rehearsing = false; details = false } }

    // null = the first empty place is open; NO_SLOT = no tray; otherwise the place the player opened.
    var opened by rememberSaveable { mutableStateOf<String?>(null) }
    val open = when (opened) { null -> bench.next; NO_SLOT -> null; else -> bench.slots.firstOrNull { it.slot.name == opened }?.slot }
    // "Forge this" or "Use recipe" was tapped elsewhere since this panel last looked: back to the first empty place.
    var revealed by rememberSaveable { mutableIntStateOf(0) }
    LaunchedEffect(s.forgeReveal) {
        if (s.forgeReveal > revealed) opened = null
        revealed = s.forgeReveal
    }

    // A Heartfire forging is asked about once before it is sent: it spends something scarce whatever comes of it.
    var confirming by rememberSaveable { mutableStateOf(false) }
    val send = { bench.command?.copy(bellows = bellows)?.let(vm::dispatch); Unit }

    // Not saved: coming back to the Forge starts with it off, so nothing moves until the player acts. The guided first
    // day's first step is to choose: there the open tray comes into view by itself, under the step that asks for it.
    var follow by remember { mutableStateOf(s.intro?.step == IntroStep.RECIPE) }

    Column(Modifier.fillMaxSize()) {
        // The header owns the day and the countdown; once the siege is warned the Forge keeps who comes and what bites.
        val scroll = rememberScrollState()
        // With large text the risk is a choice among the others, so the pinned foot keeps only what stops or sends the forge.
        val largeText = LocalDensity.current.fontScale >= 1.3f
        // Larger text or a short screen: decoration and secondary ways give way first, so the recipe stays in view.
        val tight = LocalDensity.current.fontScale > 1.15f || LocalConfiguration.current.screenHeightDp < 700
        // The last choice closes the tray: the finished blade over the anvil comes back into view.
        LaunchedEffect(open) { if (open == null && follow) scroll.animateScrollTo(0) }
        Column(Modifier.weight(1f).bottomFade(scroll.canScrollForward).verticalScroll(scroll).padding(horizontal = Space.md).padding(bottom = Space.md)) {
            // V3: the weapon on the anvil and what it is, side by side; the rules behind "Inspect", the study behind "Full details".
            val inspector = LocalInspector.current
            ForgeHero(
                bench, d, engine.content, s.shop.threat, compact = open != null, tight = tight, reducedMotion = reducedMotion,
                inspect = bench.outlook?.let { o ->
                    {
                        val art = d.familyId?.let { f -> d.coreId?.let { c -> Sprites.weapon(f, c, d.augmentId?.let { engine.content.material(it).element }, catalystId = d.catalystId) } }
                        recipeTip(o, bench.title, art?.base, engine.config, look = art, accent = d.augmentId?.let { engine.content.material(it).element }?.let { com.example.blacksmithproject.ui.detail.elementColor(it) }, actions = listOf(
                            TipAction("Practice fight", "forge_tip_bench") { rehearsing = true },
                            TipAction("Full details", "forge_outlook_details") { details = true },
                        ))
                    }
                },
                onBench = { rehearsing = true }, inspector = inspector,
            )
            // How it is forged, and the ways to the board and the rarer actions, on one line under the anvil.
            ForgeBar(
                d, { change -> vm.updateDraft(change) }, s.shop.requests.size, s.shop.requests.count { it.offered }, endDayNote, onOpenBoard, { onOpenSupplies(null) },
                { vm.selectRecords(RecordsPage.JOURNAL) }, onEndDay, s.busy, onEndDayPreview.takeIf { endDayHasMore(endDayLines(s)) },
                onBench = if (tight && bench.outlook != null) { { rehearsing = true } } else null,
            )
            bench.brief?.let { b ->
                Brief(
                    b, onOpen = { b.heroId?.let { vm.openSheet(Sheet.Hero(it)) } ?: onOpenBoard() }, onOpenBlade = { vm.openSheet(Sheet.Item(it)) },
                    onClear = { vm.updateDraft { draft -> draft.copy(commissionId = null, wantHeroId = null) } },
                )
            }
            // The places and the open tray come into view together. A tray taller than the screen keeps its head in view,
            // so the labels of the places (Weapon, Metal, Augment) stay on screen while a choice is made.
            val recipe = remember { BringIntoViewRequester() }
            LaunchedEffect(open, follow) { if (open != null && follow) recipe.bringIntoView() }
            Column(Modifier.bringIntoViewRequester(recipe)) {
                SlotRow(bench.slots.take(3), d, engine.content, open, reducedMotion) { opened = if (open == it) NO_SLOT else it.name; follow = true }
                if (bench.slots.size > 3) SlotRow(bench.slots.drop(3), d, engine.content, open, reducedMotion) { opened = if (open == it) NO_SLOT else it.name; follow = true }
                if (open != null) {
                    val options = remember(s.state, d, open, s.shop.threat) { engine.forgeOptions(s.state, d, open, s.shop.threat) }
                    // What this place held when its tray opened: the comparison's baseline. Taken again when another place
                    // opens or the tray is reopened, and kept across a restart ("" is nothing chosen).
                    val baseId = rememberSaveable(open) { d.idIn(open) ?: "" }.ifEmpty { null }
                    val change = remember(s.state, d, open, baseId, s.shop.requests, s.shop.threat) { engine.trayChange(s.state, d.withSlot(open, baseId), d, open, s.shop.requests, s.shop.threat) }
                    // Who comes and what bites, where it decides: over the augments (the header keeps the countdown).
                    if (open == RecipeSlot.AUGMENT) s.shop.threat?.takeIf { it.warned && it.matchup != null }?.let { SiegeMatchup(it, "forge_threat", inset = false) }
                    val tipFor: (OptionUi) -> TipUi? = { o -> optionTip(engine, s, open, o, onOpenSupplies) }
                    Tray(open, options, engine.content, change, onOpenSupplies, onDone = { opened = NO_SLOT }, onPutBack = { vm.updateDraft { it.withSlot(open, baseId) } }, tipFor = tipFor) { option ->
                        // Filling an empty place moves on to the next one, the first time through. Changing a choice keeps
                        // the tray open, so another can be tried against the first and put back.
                        val filling = open.ordinal <= RecipeSlot.AUGMENT.ordinal && d.idIn(open) == null
                        vm.updateDraft { engine.place(it, open, option) }
                        if (filling) { opened = null; follow = true }
                    }
                }
            }
            if (largeText) RiskChoice(d.risk, engine.config, Modifier.fillMaxWidth().padding(top = Space.sm), true) { r -> vm.updateDraft { draft -> draft.copy(risk = r) } }
            if (bench.notes.isNotEmpty()) FieldNotes(bench.notes) { vm.selectRecords(RecordsPage.JOURNAL) }
            bench.heartfire?.let { h -> HeartfireRow(h) { v -> vm.updateDraft { it.copy(heartfire = v) } } }
            // The workshop's relics that bear on this forge: the bellows as a switch, the ledger's word on the family chosen.
            BellowsRow(s, vm)
            LedgerLine(s, vm)
            tip?.let { TipBanner(it, vm, Modifier.padding(top = Space.md)) }
        }
        ForgeAction(
            bench, s.busy, endDayNote, { onOpenSupplies(bench.action.restock) }, onEndDay, marked = s.intro?.mark == IntroMark.FORGE,
            risk = if (largeText) { _, _ -> } else { modifier, inline -> RiskChoice(d.risk, engine.config, modifier, inline) { r -> vm.updateDraft { draft -> draft.copy(risk = r) } } },
            // With the choice in the scroll, the action still says the temper it will forge at.
            riskNote = if (largeText) "Risk: ${riskName(d.risk)}" else null,
        ) {
            if (bench.command?.heartfire == true) confirming = true else send()
        }
    }
    val confirm = bench.heartfire?.confirm.orEmpty()
    if (confirming && bench.command?.heartfire == true && confirm.isNotEmpty()) ForgeDialog(
        "Forge with Heartfire?", onDismiss = { confirming = false }, tag = "forge_heartfire_confirm",
        actions = {
            SecondaryActionButton("Not now", { confirming = false }, Modifier.testTag("forge_heartfire_cancel"))
            PrimaryActionButton("Spend 1 Heartfire", { confirming = false; send() }, Modifier.testTag("forge_heartfire_go"), enabled = !s.busy)
        },
    ) {
        confirm.forEach { Text(it, style = MaterialTheme.typography.bodyMedium, color = Cream) }
        bench.heartfire?.warning?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = Ember) }
    }
    if (details) bench.outlook?.let { o -> RecipeDetailSheet(bench.title, o, engine.config, onBench = { details = false; rehearsing = true }) { details = false } }
    if (rehearsing) bench.outlook?.let { o ->
        val relics = remember(s.state.relics) { benchRelics(s.state, engine.content) }
        BenchSheet(o.demonstration, demonstration = true, engine.content, engine.config, relics) { rehearsing = false }
    }
}

/**
 * One line under the anvil: Quick or Advanced, then the commission board (its count, new offers in gold) and the rarer
 * ways on behind "More" (the supplier, the notebook, what End Day sets in motion, End day itself).
 */
@Composable
private fun ForgeBar(
    d: ForgeDraft, onMode: ((ForgeDraft) -> ForgeDraft) -> Unit, commissions: Int, offers: Int, endDayNote: String, onOpenBoard: () -> Unit, onOpenSupplies: () -> Unit,
    onOpenJournal: () -> Unit, onEndDay: () -> Unit, busy: Boolean, onEndDayPreview: (() -> Unit)? = null, onBench: (() -> Unit)? = null,
) {
    Row(Modifier.fillMaxWidth().padding(top = Space.sm), verticalAlignment = Alignment.CenterVertically) {
        ModeToggle(d, onMode)
        Spacer(Modifier.weight(1f))
        Row(
            Modifier.heightIn(min = 48.dp).clip(Chip).clickable(role = Role.Button, onClick = onOpenBoard).padding(horizontal = Space.sm).testTag("forge_commissions")
                .semantics(mergeDescendants = true) { contentDescription = if (offers > 0) "Commissions, $offers new" else "Commissions, $commissions" },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PixelIcon(R.drawable.icon_action_commission, 28.dp)
            if (commissions > 0) Text(
                if (offers > 0) "$offers new" else "$commissions", style = MaterialTheme.typography.labelLarge, color = if (offers > 0) Gold else CreamMuted,
                modifier = Modifier.padding(start = 6.dp),
            )
        }
        Box {
            var more by remember { mutableStateOf(false) }
            TextButton(onClick = { more = true }, modifier = Modifier.heightIn(min = 48.dp).testTag("forge_more")) { Text("⋯", style = MaterialTheme.typography.titleLarge, color = CreamMuted, modifier = Modifier.semantics { contentDescription = "More" }) }
            DropdownMenu(expanded = more, onDismissRequest = { more = false }, containerColor = ForgePanel) {
                onBench?.let { go -> DropdownMenuItem(text = { Text("Practice fight") }, onClick = { more = false; go() }, modifier = Modifier.testTag("forge_bench_open")) }
                DropdownMenuItem(text = { Text("Supplies") }, onClick = { more = false; onOpenSupplies() }, modifier = Modifier.testTag("forge_supplies"))
                DropdownMenuItem(text = { Text("Notebook") }, onClick = { more = false; onOpenJournal() }, modifier = Modifier.testTag("forge_journal"))
                onEndDayPreview?.let { open -> DropdownMenuItem(text = { Text("What happens at End Day") }, onClick = { more = false; open() }, modifier = Modifier.testTag("forge_end_day_preview")) }
                DropdownMenuItem(text = { Column { Text("End Day"); Text(endDayNote, style = MaterialTheme.typography.bodySmall, color = CreamMuted) } }, enabled = !busy, onClick = { more = false; onEndDay() }, modifier = Modifier.testTag("forge_end_day"))
            }
        }
    }
}

/**
 * The anvil with the weapon being planned, and beside it what that weapon is: its name and element, its part in a party
 * (a guild run), the names of the rules every forge of it carries, and the likely quality. "Effects" opens the recipe's
 * card. Where room is short ([tight]: larger text or a short screen) the scene shrinks first and "Practice fight" moves
 * to the card and the More menu, so the recipe's places stay in view. The preview is the ordinary art of the type and
 * element: what a forge really gives is shown only by the result. [compact] while a tray is open.
 */
@Composable
private fun ForgeHero(
    bench: ForgeWorkbenchUi, d: ForgeDraft, content: ContentCatalog, threat: ThreatUi?, compact: Boolean, tight: Boolean, reducedMotion: Boolean,
    inspect: (() -> TipUi)?, onBench: () -> Unit, inspector: Inspector?,
) {
    val element = d.augmentId?.let { content.material(it).element }
    val o = bench.outlook
    val anchor = remember { InspectAnchor() }
    val open = { if (inspect != null) inspector?.show(inspect(), anchor.rect) }
    Column(Modifier.fillMaxWidth().padding(top = Space.sm).inspectAnchor(anchor)) {
        Row(verticalAlignment = Alignment.Top) {
            // Very large text: the room gives way to the weapon alone, small, so the name keeps the width.
            if (LocalDensity.current.fontScale >= 1.8f) Box(
                Modifier.padding(top = 4.dp).size(40.dp).clip(Chip).background(ForgeSlot).testTag("forge_workbench").semantics(mergeDescendants = true) { contentDescription = "On the anvil: ${bench.title}" },
                contentAlignment = Alignment.Center,
            ) {
                val art = d.familyId?.let { f -> Sprites.weapon(f, d.coreId ?: content.materials(MaterialCategory.CORE).first().id, element, catalystId = d.catalystId) }
                val shade = Modifier.alpha(if (d.familyId == null || d.coreId != null) 1f else 0.55f)
                if (art != null) PixelImage(art, 32.dp, description = null, modifier = shade) else PixelImage(R.drawable.anvil, 32.dp, description = null, modifier = shade)
            } else {
                val scene = when { compact -> 56.dp; tight -> 72.dp; else -> 104.dp }
                AnvilScene(bench.title, d, content, Modifier.size(scene), compact = scene < 80.dp, reducedMotion)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f).then(if (o != null) Modifier.testTag("forge_outlook") else Modifier)) {
                // The name below is a plan, not a weapon the smith holds: said once, quietly, above it.
                if (d.familyId != null) Text("Forge preview", style = MaterialTheme.typography.labelMedium, color = CreamMuted, modifier = Modifier.testTag("forge_preview_label"))
                Row(verticalAlignment = Alignment.Top) {
                    Text(
                        bench.title + (element?.takeIf { bench.command != null }?.let { " · ${it.word()}" } ?: ""), style = MaterialTheme.typography.titleLarge, color = Cream,
                        modifier = Modifier.weight(1f).testTag("forge_title"),
                    )
                    // Tight: the recipe's card is the ringed "i" beside its name, the inspect mark used on every tile.
                    if (o != null && inspect != null && tight && !compact) InfoButton("${bench.title} effects", inspect, anchor = anchor, tag = "forge_inspect_recipe")
                }
                o?.let { Text(it.role, style = MaterialTheme.typography.bodyMedium, color = CreamMuted, modifier = Modifier.semantics { heading() }.testTag("forge_outlook_role")) }
                // What every forge of it carries, as the rules' names: the weapon's identity at a glance, each read on its card.
                if (o != null) o.certain.takeIf { it.isNotEmpty() && bench.command != null }?.let { rules ->
                    FlowRow(
                        Modifier.padding(top = 2.dp).testTag("forge_identity_rules").clearAndSetSemantics { contentDescription = "Always: ${rules.joinToString(", ") { o.label(it) }}" },
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        rules.forEach { r ->
                            // The mark sits on the first line of a phrase that wraps, at the text's own size.
                            Row(verticalAlignment = Alignment.Top) {
                                Text(EffectKind.NEUTRAL.sign, style = MaterialTheme.typography.bodyMedium, color = EffectKind.NEUTRAL.color, modifier = Modifier.padding(end = 4.dp))
                                Text(o.label(r), style = MaterialTheme.typography.bodyMedium, color = Cream)
                            }
                        }
                    }
                }
                val facts = identityFacts(o, bench.quality, element?.let { threat?.marks?.get(it) }, bench.command != null)
                FactLine(facts, Modifier.padding(top = 2.dp).testTag("forge_identity"))
            }
        }
        if (o != null && inspect != null && !compact && !tight) Row(Modifier.fillMaxWidth().padding(start = 104.dp + 12.dp, top = Space.xs), horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
            ForgeLink("Effects", "forge_inspect_recipe", info = true, onClick = open)
            ForgeLink("Practice fight", "forge_bench_open", onClick = onBench)
        }
    }
}

/**
 * A quiet way on from the anvil: cream text on a dim bronze edge (with the inspect ring for "Effects"), 48 dp tall, so it
 * reads as a button without competing with the gold Forge action.
 */
@Composable
private fun ForgeLink(label: String, tag: String, info: Boolean = false, onClick: () -> Unit) {
    // The whole 48 dp box is the touch target; the drawn edge inside it is the visible button.
    Box(Modifier.heightIn(min = 48.dp).clickable(role = Role.Button, onClick = onClick).testTag(tag), contentAlignment = Alignment.Center) {
        Row(
            Modifier.heightIn(min = 36.dp).border(1.dp, BronzeLine, Chip).padding(horizontal = Space.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (info) androidx.compose.material3.Icon(androidx.compose.ui.res.painterResource(R.drawable.ic_info), contentDescription = null, tint = CreamMuted, modifier = Modifier.padding(end = 6.dp).size(18.dp))
            Text(label, style = MaterialTheme.typography.labelLarge, color = Cream)
            if (!info) Chevron(ChevronWay.RIGHT, tint = CreamMuted, size = 18.dp)
        }
    }
}

/**
 * The recipe's deciding facts in one line, under the names of the rules every forge of it carries: the likely quality,
 * how the besieger takes its element, and an effect that still needs a partner. What only a roll may add is named on
 * the recipe's card, never counted here as if it were had.
 */
internal fun identityFacts(o: ForgeOutlookUi?, quality: Pair<Int, Int>?, mark: MarkUi?, whole: Boolean): List<Fact> = if (!whole) emptyList() else listOfNotNull(
    quality?.takeIf { it.second > 0 }?.let { (low, high) -> Fact(if (low == high) "$low" else "$low to $high", "Likely quality") },
    mark?.let { Fact(it.label, tone = if (it.counters) FactTone.GOOD else FactTone.WARN) },
    o?.needs?.takeIf { it.isNotEmpty() }?.let { n -> Fact(n.joinToString(", ") { it.rule }, "Needs a partner", FactTone.WARN) },
)

/** The room, the anvil and the blade being planned, framed. Decorative to a screen reader but for what is on the anvil. */
@Composable
private fun AnvilScene(title: String, d: ForgeDraft, content: ContentCatalog, modifier: Modifier, compact: Boolean, reducedMotion: Boolean) {
    val blade = wholePixelDp(56, if (compact) 48.dp else 80.dp)
    Box(
        modifier.clip(Card).border(1.dp, BronzeDeep, Card).testTag("forge_workbench").semantics(mergeDescendants = true) { contentDescription = "On the anvil: $title" },
        contentAlignment = Alignment.BottomCenter,
    ) {
        Backdrop(R.drawable.bg_counter_forge, Modifier.matchParentSize().clearAndSetSemantics {}, dim = 0.35f, sink = 0.2f)
        PixelImage(R.drawable.anvil, wholePixelDp(109, if (compact) 64.dp else 96.dp), description = null, modifier = Modifier.padding(bottom = 2.dp).height(if (compact) 40.dp else 60.dp))
        d.familyId?.let { family ->
            val core = d.coreId ?: content.materials(MaterialCategory.CORE).first().id
            PixelImage(
                Sprites.weapon(family, core, d.augmentId?.let { content.material(it).element }, catalystId = d.catalystId), blade, description = null,
                modifier = Modifier.padding(bottom = if (compact) 16.dp else 26.dp).alpha(if (d.coreId != null) 1f else 0.55f).landing(Triple(family, d.coreId, d.augmentId), reducedMotion),
            )
        }
    }
}

/**
 * Who the draft is for, as a compact strip: a commission or a hero's want, what it asks as facts (a met ask ticked) and
 * its standing. The weapons the shop already holds of that type are one tap away under a count, so stock is weighed
 * before another forge; a weapon End Day would hand over is said on the closed row. Nothing here is promised or set
 * aside. A purpose that has gone says so in place. "Forge freely" drops it and keeps the ingredients.
 */
@Composable
private fun Brief(b: BriefUi, onOpen: () -> Unit, onOpenBlade: (WeaponId) -> Unit, onClear: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = Space.sm).forgeRow().testTag("forge_for")) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(
                Modifier.weight(1f).clickable(onClickLabel = if (b.heroId != null) "Open hero" else "Open commission", onClick = onOpen)
                    .padding(start = Space.md, top = Space.sm, bottom = Space.sm, end = Space.xs),
            ) {
                Text(b.title, style = MaterialTheme.typography.labelLarge, color = if (b.gone == null) Cream else CreamMuted)
                if (b.gone != null) Text(b.gone, style = MaterialTheme.typography.bodySmall, color = Ember, modifier = Modifier.testTag("forge_for_gone"))
                else FactLine(b.facts())
            }
            TextButton(onClick = onClear, modifier = Modifier.heightIn(min = 48.dp).testTag("forge_clear_target")) { Text("Forge freely", color = Gold) }
        }
        if (b.gone == null && b.stock.isEmpty()) b.stockNote?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = CreamMuted, modifier = Modifier.padding(start = Space.md, end = Space.xs, bottom = Space.sm).testTag("forge_for_stock"))
        }
        if (b.gone == null && b.stock.isNotEmpty()) Disclosure(
            "Already in your shop · ${b.stock.size}", "forge_for_stock_${b.id?.value ?: b.heroId?.value}", Modifier.padding(start = Space.md, end = Space.xs),
            summary = b.stock.firstOrNull { it.handsOver }?.let { "End Day would hand over ${it.name}" }
                ?: b.stock.count { it.fits }.takeIf { it > 0 }?.let { "$it ${if (it == 1) "fits" else "fit"}" } ?: b.stockNote,
            tag = "forge_for_stock",
        ) {
            b.stock.forEach { w ->
                Row(
                    Modifier.fillMaxWidth().clickable(onClickLabel = "Open ${w.name}", role = Role.Button) { onOpenBlade(w.weaponId) }.heightIn(min = 48.dp).testTag("forge_for_stock_${w.weaponId.value}"),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text((if (w.fits) "✓ " else "") + w.name, style = MaterialTheme.typography.bodyMedium, color = if (w.fits) BuffGreen else Cream)
                        Text(w.line, style = MaterialTheme.typography.bodySmall, color = CreamMuted)
                    }
                    Text("Open ›", style = MaterialTheme.typography.labelLarge, color = Gold, modifier = Modifier.padding(horizontal = Space.sm))
                }
            }
            b.stockNote?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = CreamMuted, modifier = Modifier.padding(vertical = Space.xs)) }
        }
    }
}

/**
 * A thing just put in its place lands there: it starts a little small and settles. Only on a change the player made
 * while looking (never when the Forge opens with a recipe already on it), and not at all with reduced motion.
 */
@Composable
private fun Modifier.landing(what: Any?, reducedMotion: Boolean): Modifier {
    val scale = remember { Animatable(1f) }
    var shown by remember { mutableStateOf(what) }
    LaunchedEffect(what) {
        if (what == shown) return@LaunchedEffect
        shown = what
        if (!reducedMotion && what != null) { scale.snapTo(0.6f); scale.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessMedium)) }
    }
    return graphicsLayer { scaleX = scale.value; scaleY = scale.value }
}

private fun slotArt(slot: RecipeSlot, d: ForgeDraft, content: ContentCatalog): Int? = when (slot) {
    RecipeSlot.WEAPON -> d.familyId?.let { Sprites.weapon(it, content.materials(MaterialCategory.CORE).first().id, null).base }
    RecipeSlot.METAL -> d.coreId?.let(Sprites::material)
    RecipeSlot.AUGMENT -> d.augmentId?.let(Sprites::material)
    RecipeSlot.CATALYST -> d.catalystId?.let(Sprites::material)
    RecipeSlot.TECHNIQUE -> null
}

/** The places of the recipe side by side: the thing chosen as an object, its kind over it, its name under it. */
@Composable
private fun SlotRow(slots: List<SlotUi>, d: ForgeDraft, content: ContentCatalog, open: RecipeSlot?, reducedMotion: Boolean, onToggle: (RecipeSlot) -> Unit) {
    val large = LocalDensity.current.fontScale > 1.3f
    Row(Modifier.fillMaxWidth().then(if (large) Modifier.horizontalScroll(rememberScrollState()) else Modifier).padding(top = Space.sm).height(IntrinsicSize.Min).testTag("forge_slots_${slots.first().slot.name.lowercase()}"), horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
        slots.forEach { slot ->
            val active = open == slot.slot
            Column(
                (if (large) Modifier.width(156.dp) else Modifier.weight(1f)).fillMaxHeight().heightIn(min = if (large) 64.dp else 84.dp).clip(Card).background(if (active) BronzeContainer else ForgePanelRaised).border(if (active) 1.5.dp else 1.dp, if (active) Gold else BronzeLine, Card)
                    .clickable(role = Role.Button) { onToggle(slot.slot) }.padding(horizontal = Space.xs, vertical = 6.dp).testTag("forge_slot_${slot.slot.name.lowercase()}")
                    .semantics(mergeDescendants = true) { contentDescription = "${slot.slot.label}: ${slot.value ?: "not chosen"}${slot.stock?.let { n -> ", $n in stock" }.orEmpty()}, ${if (active) "choosing" else "tap to change"}" },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(slot.slot.label, style = MaterialTheme.typography.labelSmall, color = CreamMuted, textAlign = TextAlign.Center)
                if (!large) Box(Modifier.fillMaxWidth().padding(vertical = 2.dp), contentAlignment = Alignment.Center) {
                    val art = slotArt(slot.slot, d, content)
                    if (art != null) PixelImage(art, 40.dp, description = null, modifier = Modifier.landing(slot.value, reducedMotion))
                    else Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) { Text(if (slot.value != null) "✦" else "+", style = MaterialTheme.typography.titleMedium, color = if (slot.value != null) Gold else Bronze) }
                    slot.stock?.let { Text("×$it", style = MaterialTheme.typography.labelSmall, color = if (it == 0) FlawRed else CreamMuted, modifier = Modifier.align(Alignment.CenterEnd).background(ForgeSlot, Chip).padding(horizontal = 6.dp, vertical = 4.dp)) }
                }
                if (large) slot.stock?.let { Text(if (it == 0) "None left" else "$it in stock", style = MaterialTheme.typography.labelSmall, color = CreamMuted) }
                Text(
                    slot.value ?: slot.slot.empty, style = MaterialTheme.typography.labelMedium, fontWeight = if (slot.value != null) FontWeight.SemiBold else null,
                    color = if (slot.value != null) Cream else CreamMuted, textAlign = TextAlign.Center,
                )
            }
        }
    }
}

private fun optionArt(slot: RecipeSlot, option: OptionUi, content: ContentCatalog): Int? = when (slot) {
    RecipeSlot.WEAPON -> option.id?.let { Sprites.weapon(WeaponFamilyId(it), content.materials(MaterialCategory.CORE).first().id, null).base }
    RecipeSlot.TECHNIQUE -> null
    else -> option.id?.let { Sprites.material(MaterialId(it)) }
}

/**
 * The choices for one place in a horizontally scrolling row, each a tile: its art, its name, what the smith holds of it
 * and the name of the rule it gives. A tap on a tile that can be used places it and the tray stays open; its ringed "i"
 * (or a long press) opens its card. Stock-empty tiles stay dimmed: a tap says why they cannot be used and where more
 * comes from. Under the options, the choice in focus and how the draft now differs from the one the tray was opened on
 * ([change]), with the way back to it. Choosing is free: nothing is spent and nothing is drawn until "Forge".
 */
@Composable
internal fun Tray(
    slot: RecipeSlot, options: List<OptionUi>, content: ContentCatalog, change: TrayChangeUi?, onRestock: (MaterialId?) -> Unit, onDone: () -> Unit, onPutBack: () -> Unit,
    tipFor: ((OptionUi) -> TipUi?)? = null, onPick: (OptionUi) -> Unit,
) {
    val columns = if (LocalDensity.current.fontScale > 1.3f || LocalConfiguration.current.screenWidthDp < 340) 2 else 3
    var inspected by remember(slot) { mutableStateOf<String?>(null) }
    Column(Modifier.fillMaxWidth().padding(top = Space.sm).testTag("forge_tray_${slot.name.lowercase()}")) {
        // With large text the name takes its own line and the two actions sit under it, so it is never cut letter by letter.
        @Composable fun actions() {
            TextButton(onClick = { onRestock(null) }, modifier = Modifier.heightIn(min = 48.dp).testTag("forge_tray_buy")) { Text("Buy", color = CreamMuted) }
            TextButton(onClick = onDone, modifier = Modifier.heightIn(min = 48.dp).testTag("forge_tray_done")) { Text("Done", color = Gold) }
        }
        if (columns == 2) {
            Text(slot.choose, style = MaterialTheme.typography.titleSmall, color = Cream, modifier = Modifier.padding(top = Space.sm).semantics { heading() })
        } else Row(verticalAlignment = Alignment.CenterVertically) {
            Text(slot.choose, style = MaterialTheme.typography.titleSmall, color = Cream, modifier = Modifier.weight(1f).semantics { heading() })
            actions()
        }
        val choiceScroll = rememberSaveable(slot, saver = androidx.compose.foundation.ScrollState.Saver) { androidx.compose.foundation.ScrollState(0) }
        Row(Modifier.fillMaxWidth().horizontalScroll(choiceScroll).height(IntrinsicSize.Min).padding(top = Space.xs).testTag("forge_choices"), horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
            options.forEach { o ->
                OptionTile(
                    o, if (LocalDensity.current.fontScale > 1.3f) null else optionArt(slot, o, content), Modifier.width(if (columns == 2) 144.dp else 112.dp).fillMaxHeight(),
                    inspected = o.id != null && o.id == inspected, tip = if (o.id != null && tipFor != null) { { tipFor(o) } } else null,
                ) {
                    if (o.usable) { inspected = null; onPick(o) } else inspected = o.id
                }
            }
        }
        val focused = staleFree(inspected, options) ?: options.firstOrNull { it.selected }
        if (focused == null) Text(slot.hint, style = MaterialTheme.typography.bodySmall, color = CreamMuted, modifier = Modifier.padding(top = Space.sm))
        else TrayFocus(slot, focused, if (focused.usable) change else null, { onRestock(focused.id?.let(::MaterialId)) }, onPutBack, tip = if (focused.id != null && tipFor != null) { { tipFor(focused) } } else null)
        if (columns == 2) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { actions() }
    }
}

/**
 * The one choice in focus: its name, what it is, how the besieger takes it, why it cannot be used, and the draft against
 * the tray's baseline. What it does in a fight is on its card ([tip]), one tap away.
 */
@Composable
private fun TrayFocus(slot: RecipeSlot, o: OptionUi, change: TrayChangeUi?, onRestock: () -> Unit, onPutBack: () -> Unit, tip: (() -> TipUi?)? = null) {
    Column(Modifier.fillMaxWidth().padding(top = Space.sm).clip(Chip).background(ForgePanel).padding(start = 12.dp, end = Space.xs, bottom = Space.sm).testTag("forge_tray_detail")) {
        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).padding(vertical = Space.xs)) {
                Text(o.name, style = MaterialTheme.typography.titleSmall, color = if (o.usable) Cream else CreamMuted)
                // A long description belongs to the card; without one it stays here, whole.
                o.detail?.takeIf { it.length <= 64 || tip == null }?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = CreamMuted) }
            }
            tip?.let { t -> InfoButton(o.name, { t() ?: TipUi(o.name, slot.label) }, tag = "forge_tray_rules") }
        }
        o.mark?.let { mark ->
            Text("${mark.kind.sign} ${o.name} against the siege: ${mark.label}", style = MaterialTheme.typography.bodySmall, color = mark.kind.color, modifier = Modifier.padding(end = Space.sm).testTag("forge_siege_mark"))
        }
        o.blocked?.let { why ->
            Row(Modifier.fillMaxWidth().padding(top = Space.xs), verticalAlignment = Alignment.CenterVertically) {
                Text(why, style = MaterialTheme.typography.bodySmall, color = Ember, modifier = Modifier.weight(1f).testTag("forge_tray_blocked"))
                TextButton(onClick = onRestock, modifier = Modifier.heightIn(min = 48.dp).testTag("forge_restock_tray")) { Text("Buy", color = Gold) }
            }
        }
        change?.let { c ->
            Column(Modifier.padding(top = Space.xs, end = Space.sm).testTag("forge_tray_change")) {
                if (c.changes.isEmpty()) Text("Unchanged from ${c.from}", style = MaterialTheme.typography.labelMedium, color = CreamMuted)
                else {
                    // The decision facts as one wrapping line; what the notebook and the fight rules say is one tap away.
                    Text("Compared with ${c.from}", style = MaterialTheme.typography.labelMedium, color = CreamMuted)
                    FactLine(c.changes.filter { !it.rule }.map(::changeFact), Modifier.padding(top = 2.dp))
                    val more = c.changes.filter { it.rule }
                    if (more.isNotEmpty()) Disclosure("Effects and notes · ${more.size}", "forge_tray_change_more_${slot.name}_${o.id}", summary = more.first().text) {
                        more.forEach { ch -> FactLine(listOf(changeFact(ch))) }
                    }
                    InlineActionButton("Put back ${c.from}", onPutBack, Modifier.testTag("forge_tray_put_back"))
                }
            }
        }
    }
}

/** A change of the comparison as a fact: a gain with its "+", a cost with its "−", a plain difference with no sign. */
private fun changeFact(c: ChangeUi) = Fact(c.text, tone = when (c.good) { true -> FactTone.GOOD; false -> FactTone.WARN; null -> FactTone.PLAIN })

/** One choice of a tray: art, stock, name and the name of its rule; gold-edged once chosen. Long press or "i" inspects. */
@Composable
private fun OptionTile(o: OptionUi, art: Int?, modifier: Modifier, inspected: Boolean = false, tip: (() -> TipUi?)? = null, onClick: () -> Unit) {
    val inspector = LocalInspector.current
    val anchor = remember { InspectAnchor() }
    val card: (() -> TipUi)? = tip?.let { t -> { t() ?: TipUi(o.name, "") } }
    Box(
        modifier.heightIn(min = 76.dp).clip(Chip).background(if (o.selected) BronzeContainer else ForgeNight)
            .border(if (o.selected || inspected) 1.5.dp else 1.dp, if (o.selected) Gold else if (inspected) CreamMuted else BronzeDeep, Chip)
            .then(if (card != null) Modifier.inspectable(anchor, inspector, card, onClick = onClick) else Modifier.clickable(role = Role.Button, onClick = onClick))
            .testTag("forge_option_${o.id ?: "none"}")
            .semantics(mergeDescendants = true) {
                selected = o.selected
                contentDescription = listOfNotNull(o.name, o.stock?.let { if (it == 0) "none left" else "$it in stock" }, o.mark?.label, o.blocked?.let { "cannot be used now" }).joinToString(", ")
            },
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = Space.xs, vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            // Out of stock or blocked: the art and the name dim, the line that says why stays at full strength.
            val dim = Modifier.alpha(if (o.usable) 1f else 0.5f)
            if (art != null) PixelImage(art, 36.dp, description = null, modifier = dim)
            Text(o.name, style = MaterialTheme.typography.labelMedium, color = Cream, textAlign = TextAlign.Center, modifier = dim.padding(top = 2.dp))
            o.stock?.let { Text(if (it == 0) "None left" else "$it owned", style = MaterialTheme.typography.labelSmall, fontWeight = if (it == 0) FontWeight.SemiBold else null, color = if (it == 0) FlawRed else CreamMuted) }
            if (o.fight.isNotEmpty()) Text(o.fight.joinToString(" · ") { it.substringBefore(":") }, style = MaterialTheme.typography.labelSmall, color = CreamMuted, textAlign = TextAlign.Center)
        }
        o.mark?.let { Text(it.kind.sign, style = MaterialTheme.typography.titleSmall, color = it.kind.color, modifier = Modifier.align(Alignment.TopStart).padding(horizontal = 6.dp)) }
        if (card != null) InfoButton(o.name, card, Modifier.align(Alignment.TopEnd), anchor = anchor, tag = "forge_option_${o.id}_info")
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
    // Nothing tried yet and no clue held: one quiet row says so, not a card of question marks.
    if (notes.all { it.state == KnowledgeState.UNKNOWN && it.label != "Signature" }) {
        Row(
            modifier.fillMaxWidth().padding(top = Space.xs).heightIn(min = 48.dp).testTag("forge_notes")
                .semantics(mergeDescendants = true) { contentDescription = "Notebook: ${notes.joinToString(" and ") { it.label }} untried. ${notes.first().hint}" },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("? ", style = MaterialTheme.typography.titleSmall, color = KnowledgeState.UNKNOWN.color())
            Text("Untried combination${if (notes.size == 1) "" else "s"} · ${notes.first().hint}", style = MaterialTheme.typography.bodySmall, color = CreamMuted, modifier = Modifier.weight(1f))
            if (onOpenJournal != null) Row(Modifier.clickable(role = Role.Button, onClick = onOpenJournal).heightIn(min = 48.dp).padding(start = Space.sm).testTag("forge_notes_journal"), verticalAlignment = Alignment.CenterVertically) {
                Text("Notebook", style = MaterialTheme.typography.labelLarge, color = Cream)
                Chevron(ChevronWay.RIGHT, tint = CreamMuted, size = 18.dp)
            }
        }
        return
    }
    val stacked = LocalDensity.current.fontScale > 1.3f
    Column(modifier.fillMaxWidth().padding(top = Space.sm).clip(Card).background(SceneDeep).border(1.dp, BronzeDeep, Card).padding(horizontal = 12.dp).padding(bottom = 12.dp).testTag("forge_notes")) {
        Row(Modifier.heightIn(min = 40.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Notebook", style = MaterialTheme.typography.titleSmall, color = Cream, modifier = Modifier.weight(1f).semantics { heading() })
            if (onOpenJournal != null) Text("Open notebook ›", style = MaterialTheme.typography.labelLarge, color = Gold, modifier = Modifier.clickable(role = Role.Button, onClick = onOpenJournal).heightIn(min = 48.dp).wrapContentHeight().padding(start = Space.sm).testTag("forge_notes_journal"))
        }
        val pairings = notes.filter { it.label != "Signature" }
        if (stacked) pairings.forEachIndexed { i, n -> FieldNote(i, n, Modifier.fillMaxWidth().padding(top = if (i > 0) Space.sm else 0.dp)) }
        else Row(Modifier.height(IntrinsicSize.Min)) {
            pairings.forEachIndexed { i, n ->
                if (i > 0) Box(Modifier.padding(horizontal = 12.dp).width(1.dp).fillMaxHeight().background(BronzeDeep))
                FieldNote(i, n, Modifier.weight(1f))
            }
        }
        notes.firstOrNull { it.label == "Signature" }?.let { n ->
            Column(Modifier.fillMaxWidth().padding(top = Space.sm).drawBehind { drawLine(BronzeDeep, Offset.Zero, Offset(size.width, 0f), strokeWidth = 1.dp.toPx()) }.padding(top = Space.sm).testTag("forge_note_signature")
                .semantics(mergeDescendants = true) { contentDescription = "Signature, ${n.subject}, ${n.stage}: ${n.hint}" }) {
                Text("${n.state.glyph()} Signature · ${n.stage}", style = MaterialTheme.typography.labelMedium, color = n.state.color())
                Text(n.hint, style = MaterialTheme.typography.bodySmall, color = Cream, modifier = Modifier.padding(top = 2.dp))
            }
        }
    }
}

/**
 * The recipe on the anvil studied in full (a guild run; V3 details): each ingredient and what choosing it costs, every
 * rule every forge of it carries, what they still need, how they work together, and each chance with when it comes.
 * Certain and random stay apart; a rule is called certain only when every forge of this recipe has it.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
private fun RecipeDetailSheet(title: String, o: ForgeOutlookUi, config: BalanceConfig, onBench: () -> Unit, onClose: () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onClose, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        dragHandle = { BottomSheetDefaults.DragHandle(width = 48.dp) },
        modifier = Modifier.semantics { testTagsAsResourceId = true }.testTag("forge_recipe_sheet"),
    ) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = Space.md).padding(bottom = Space.lg).testTag("forge_outlook_expanded"), verticalArrangement = Arrangement.spacedBy(Space.md)) {
            Column {
                Text(title, style = MaterialTheme.typography.titleLarge, color = Cream, modifier = Modifier.semantics { heading() })
                Text(o.role + if (o.qualityHigh > 0) " · likely quality ${o.qualityLow} to ${o.qualityHigh}, before any exceptional result or flaw" else "", style = MaterialTheme.typography.bodySmall, color = CreamMuted)
            }
            TipSectionBlock(TipSection("Each choice", o.parts.map { TipLine(it.role, TipTone.PLAIN, title = it.name) }))
            Box(Modifier.testTag("forge_outlook_tradeoff")) {
                // Each caveat marked as what it is: a drawback, a condition its effect waits for, or a comparison only.
                TipSectionBlock(TipSection("Drawbacks and conditions", o.parts.mapNotNull { p -> p.constraint?.let { TipLine("${p.name}: $it", p.constraintKind.tone()) } }))
            }
            TipSectionBlock(TipSection("Every forge of it has", if (o.certain.isEmpty()) listOf(TipLine("No effect beyond its normal attack", TipTone.PLAIN)) else o.certain.map { it.ability() }))
            if (o.needs.isNotEmpty()) Box(Modifier.testTag("forge_outlook_needs")) {
                TipSectionBlock(TipSection("Its effects need", o.needs.flatMap { n -> n.needs.map { TipLine("${n.rule}: $it", TipTone.CONDITION) } }))
            }
            TipSectionBlock(TipSection("How its effects work together", o.notes.map { TipLine(it, TipTone.PLAIN) }))
            TipSectionBlock(TipSection("By chance, never certain", o.possible.map { TipLine(it.text, TipTone.CHANCE, title = it.name) }))
            TipKeywords(keywords(o.certain.flatMap { it.stats }, config))
            SecondaryActionButton("Practice fight", onBench, Modifier.fillMaxWidth().testTag("forge_details_bench"))
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
            color = n.state.color(), modifier = Modifier.padding(vertical = 2.dp),
        )
        Text(if (untried) n.hint else "${n.state.glyph()} ${n.stage}", style = MaterialTheme.typography.bodySmall, color = CreamMuted)
    }
}

/**
 * Quick or Advanced as a two-part toggle, before the places it changes (its cost is on the Forge action). Going back to
 * Quick drops the catalyst, the technique and the Heartfire, which only an Advanced forge takes.
 */
@Composable
private fun ModeToggle(d: ForgeDraft, onChange: ((ForgeDraft) -> ForgeDraft) -> Unit) {
    Row(Modifier.clip(Card).background(ForgeSlot).border(1.dp, BronzeDeep, Card).padding(2.dp)) {
        ForgeMode.entries.forEach { mode ->
            val on = d.mode == mode
            Box(
                Modifier.heightIn(min = 48.dp).clip(Chip).background(if (on) BronzeContainer else ForgeSlot)
                    .clickable(role = Role.RadioButton) { onChange { it.inMode(mode) } }
                    .padding(horizontal = 14.dp).testTag("forge_mode_${mode.name.lowercase()}").semantics { selected = on },
                contentAlignment = Alignment.Center,
            ) { Text(if (mode == ForgeMode.QUICK) "Quick" else "Advanced", style = MaterialTheme.typography.labelLarge, color = if (on) Cream else CreamMuted) }
        }
    }
}

/** Risk as one compact choice whose menu says what each means, beside the action it changes. */
@Composable
private fun RiskChoice(risk: Risk, config: BalanceConfig, modifier: Modifier, inline: Boolean, onPick: (Risk) -> Unit) =
    Choice("Risk", riskName(risk), "forge_risk", modifier, Risk.entries.map { Triple(riskName(it), riskExplanation(it, config), it) }, inline, onPick)

@Composable
private fun <T> Choice(label: String, value: String, tag: String, modifier: Modifier, choices: List<Triple<String, String, T>>, inline: Boolean = false, onPick: (T) -> Unit) {
    Box(modifier) {
        var open by remember { mutableStateOf(false) }
        Row(
            Modifier.fillMaxWidth().heightIn(min = 48.dp).clip(Card).border(1.dp, BronzeDeep, Card).clickable(role = Role.DropdownList) { open = true }.padding(horizontal = Space.md, vertical = 6.dp).testTag(tag)
                .semantics(mergeDescendants = true) { contentDescription = "$label: $value, tap to change" },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // [inline]: one line, for a row of its own under large text, where two lines would take the recipe's room.
            if (inline) Text("$label: $value", style = MaterialTheme.typography.labelLarge, color = Cream, modifier = Modifier.weight(1f))
            else Column(Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.labelSmall, color = CreamMuted)
                Text(value, style = MaterialTheme.typography.labelLarge, color = Cream)
            }
            Chevron(ChevronWay.DOWN, tint = CreamMuted, size = 18.dp)
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
 * temper ([risk]) and the one gold action with its energy cost in its own label: what the forge will cost and risk,
 * together. When no forge is possible today, End day stands beside it. With large text, on a narrow screen or beside End
 * day, the risk takes a row of its own rather than squeezing the action.
 */
@Composable
private fun ForgeAction(
    bench: ForgeWorkbenchUi, busy: Boolean, endDayNote: String, onRestock: () -> Unit, onEndDay: () -> Unit, marked: Boolean = false,
    risk: @Composable (Modifier, Boolean) -> Unit = { _, _ -> }, riskNote: String? = null, onForge: () -> Unit,
) {
    val a = bench.action
    Column(
        Modifier.fillMaxWidth().background(ForgePanel).drawBehind { drawLine(BronzeDeep, Offset.Zero, Offset(size.width, 0f), strokeWidth = 1.dp.toPx()) }
            .padding(horizontal = Space.md).padding(top = Space.xs, bottom = Space.sm),
    ) {
        if (a.note != null) Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(a.note, style = MaterialTheme.typography.bodySmall, color = if (a.enabled) Cream else CreamMuted, modifier = Modifier.weight(1f).padding(vertical = Space.xs).testTag("forge_note"))
            if (a.restock != null) TextButton(onClick = onRestock, modifier = Modifier.heightIn(min = 48.dp).testTag("forge_restock")) { Text("Buy", color = Gold) }
        }
        val stacked = a.endDay || LocalConfiguration.current.screenWidthDp < 340
        if (stacked) risk(Modifier.fillMaxWidth().padding(bottom = Space.xs), true)
        Row(horizontalArrangement = Arrangement.spacedBy(Space.sm), verticalAlignment = Alignment.CenterVertically) {
            // Its own width (never a word cut letter by letter), the action takes the rest.
            if (!stacked) risk(Modifier.widthIn(min = 124.dp).width(IntrinsicSize.Max), false)
            val enabled = a.enabled && !busy
            PrimaryActionButton(
                a.label, onForge,
                // A disabled button says why, so a screen reader is not left with a dead action.
                Modifier.weight(1f).testTag("forge_weapon").introMark(marked).semantics { if (!enabled && a.note != null) contentDescription = "${a.label}, unavailable: ${a.note}" },
                enabled = enabled, detail = riskNote,
            )
            if (a.endDay) SecondaryActionButton("End Day", onEndDay, Modifier.weight(1f).testTag("end_day"), enabled = !busy, detail = endDayNote)
        }
    }
}

private fun riskName(r: Risk) = Labels.risk(r).substringBefore(".")

/** Live affinity hint: an icon per knowledge state (never colour alone) plus the journal's descriptive text. */
@Composable
fun AffinityHint(journal: JournalModel, content: ContentCatalog, key: String) {
    val state = journal.state(key)
    val (glyph, stateLabel) = when (state) {
        KnowledgeState.UNKNOWN -> "?" to "unknown"
        KnowledgeState.OBSERVED -> "◐" to "being studied"
        KnowledgeState.UNDERSTOOD -> "●" to "learned"
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

/**
 * Heartfire on the workbench: how much the forge holds, always. In Advanced with some held, a switch to forge with it.
 * While the switch is on, everything it spends and can give stands here before the Forge action is tapped. When it
 * cannot be used (none held, a Quick forge) it is one quiet row: what is held, why not, and how it works one tap away.
 */
@Composable
private fun HeartfireRow(h: HeartfireUi, onToggle: (Boolean) -> Unit) {
    if (h.blocked != null) {
        val large = LocalDensity.current.fontScale > 1.3f
        Column(Modifier.fillMaxWidth().padding(top = Space.xs).testTag("forge_heartfire")) {
            @Composable fun said(modifier: Modifier) = Row(modifier, verticalAlignment = Alignment.CenterVertically) {
                Text(h.held, style = MaterialTheme.typography.labelLarge, color = Cream, modifier = Modifier.testTag("forge_heartfire_held"))
                Text("  ·  ", style = MaterialTheme.typography.bodySmall, color = CreamMuted)
                Text(h.blocked, style = MaterialTheme.typography.bodySmall, color = CreamMuted, modifier = Modifier.weight(1f).testTag("forge_heartfire_state"))
            }
            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                said(Modifier.weight(1f))
                InfoButton("Heartfire", { heartfireTip(h) }, tag = "forge_heartfire_how")
            }
        }
        return
    }
    Column(
        Modifier.fillMaxWidth().padding(top = Space.sm).forgeRow()
            .then(if (h.blocked == null) Modifier.toggleable(value = h.on, role = Role.Switch, onValueChange = onToggle) else Modifier)
            .heightIn(min = 56.dp).padding(horizontal = Space.md, vertical = Space.sm).testTag("forge_heartfire"),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
            Column(Modifier.weight(1f)) {
                Text(h.held, style = MaterialTheme.typography.titleSmall, color = if (h.on) Gold else Cream, modifier = Modifier.testTag("forge_heartfire_held"))
                Secondary(h.blocked ?: "Forge with Heartfire", Modifier.testTag("forge_heartfire_state"))
            }
            // How Heartfire works is the same every day: on its card, not under every forge.
            InfoButton("Heartfire", { heartfireTip(h) }, tag = "forge_heartfire_how")
            if (h.blocked == null) Switch(checked = h.on, onCheckedChange = null)
        }
        if (h.outlook.isNotEmpty()) Column(Modifier.padding(top = Space.xs).testTag("forge_heartfire_outlook")) {
            h.outlook.forEach { Text(it, style = MaterialTheme.typography.bodySmall, color = Cream, modifier = Modifier.padding(top = 2.dp)) }
            h.warning?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Ember, modifier = Modifier.padding(top = 2.dp).testTag("forge_heartfire_warning")) }
        }
    }
}

/**
 * Why the Ashen Bellows cannot be asked for on this draft now, or null: the engine's own two reasons (`Relics.bellowsError`),
 * said before the tap instead of after it. Only meaningful while the bellows are held.
 */
internal fun bellowsBlocked(s: UiState.Playing, config: BalanceConfig): String? {
    val st = s.state
    val cost = if (s.draft.mode == ForgeMode.QUICK) config.quickForgeEnergy else config.advancedForgeEnergy
    val debt = config.depth.bellowsDebt
    return when {
        s.relics.firstOrNull { it.id == Depth.ASHEN_BELLOWS }?.ready == false -> "Used today."
        (cost - st.energy).coerceAtLeast(0) + debt > config.maxOverworkPerDay - st.overworkToday -> "Not enough energy left to borrow today. The bellows borrow $debt energy from tomorrow, on top of this forge."
        else -> null
    }
}

/** The Ashen Bellows as a switch on the draft; shown only while the workshop holds them. Off and explained when they cannot be used. */
@Composable
private fun BellowsRow(s: UiState.Playing, vm: GameViewModel) {
    if (s.relics.none { it.id == Depth.ASHEN_BELLOWS }) return
    val config = vm.engine.config
    val blocked = bellowsBlocked(s, config)
    val on = s.draft.bellows && blocked == null
    Row(
        Modifier.fillMaxWidth().padding(top = Space.sm).forgeRow()
            .toggleable(value = on, enabled = blocked == null, role = Role.Switch) { v -> vm.updateDraft { it.copy(bellows = v) } }
            .heightIn(min = 56.dp).padding(horizontal = Space.md, vertical = Space.sm).testTag("forge_bellows"),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.sm),
    ) {
        Column(Modifier.weight(1f)) {
            Text("Ashen Bellows: adds ${config.depth.bellowsExtraSlots.let { if (it == 1) "1 more property" else "$it more properties" }} and borrows ${config.depth.bellowsDebt} energy from tomorrow.", style = MaterialTheme.typography.titleSmall)
            blocked?.let { Secondary(it, Modifier.testTag("forge_bellows_blocked")) }
        }
        Switch(checked = on, onCheckedChange = null, enabled = blocked == null)
    }
}

/** The Tempering Ledger's word on the family on the anvil: what it adds now, or that this forge starts its streak again. */
@Composable
private fun LedgerLine(s: UiState.Playing, vm: GameViewModel) {
    val st = s.state
    val family = s.draft.familyId ?: return
    val bonus = remember(st, family) { vm.engine.ledgerBonus(st, family) }
    val line = when {
        bonus > 0 -> "Tempering Ledger: +$bonus quality"
        st.relics.any { it.id == Depth.TEMPERING_LEDGER && family in it.families } -> "Tempering Ledger: you have already forged this weapon type in the current streak. Forging it again starts the streak over."
        else -> return
    }
    Text(line, style = MaterialTheme.typography.bodySmall, color = Gold, modifier = Modifier.padding(top = Space.xs).testTag("forge_ledger"))
}

/** Heartfire as a card: what is held, how a weapon becomes Legendary, and where more comes from, a line a sentence. */
private fun heartfireTip(h: HeartfireUi) = TipUi(
    name = "Heartfire", kind = "For Legendary forging · Advanced forge only", icon = R.drawable.blessing_forgefire, nameColor = Ember, meta = h.held,
    sections = listOf(
        TipSection("How it works", h.rule.split(Regex("(?<=\\.)\\s+")).map { TipLine(it, TipTone.PLAIN) }),
        TipSection("Where it comes from", listOf(TipLine(h.source.substringAfter(": ", h.source).replaceFirstChar { it.uppercase() }, TipTone.PLAIN))),
    ),
    tag = "forge_heartfire_tip",
)

/** The card of a tray's choice: a weapon type, a technique or a material; "None" and "Plain" have none. */
internal fun optionTip(engine: GameEngine, s: UiState.Playing, slot: RecipeSlot, o: OptionUi, onRestock: (MaterialId?) -> Unit): TipUi? {
    val id = o.id ?: return null
    return when (slot) {
        RecipeSlot.WEAPON -> engine.familyTip(s.state, WeaponFamilyId(id), tag = "forge_tray_fight")
        RecipeSlot.TECHNIQUE -> Technique.entries.firstOrNull { it.name == id }?.let { techniqueTip(it, "forge_tray_fight") }
        else -> engine.materialTip(s.state, MaterialId(id), s.shop.threat, o.blocked, onRestock = { onRestock(MaterialId(id)) }, tag = "forge_tray_fight")
    }
}
