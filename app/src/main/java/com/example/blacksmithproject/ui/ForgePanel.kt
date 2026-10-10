package com.example.blacksmithproject.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.testTag
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.material3.TextButton
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import com.example.blacksmithproject.RecordsPage
import com.example.blacksmithproject.ui.shopday.Backdrop
import com.example.blacksmithproject.ui.theme.Bronze
import com.example.blacksmithproject.ui.theme.BronzeContainer
import com.example.blacksmithproject.ui.theme.ForgeSlot
import com.example.blacksmithproject.ui.theme.Gold
import com.example.blacksmithproject.ui.theme.GoldBright
import com.example.blacksmithproject.ui.theme.SceneCream
import com.example.blacksmithproject.ui.theme.SceneDeep
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.example.blacksmithproject.ForgeDraft
import com.example.blacksmithproject.GameViewModel
import com.example.blacksmithproject.R
import com.example.blacksmithproject.UiState
import com.example.blacksmithproject.ui.theme.Space
import com.tinyblacksmith.core.content.ContentCatalog
import com.tinyblacksmith.core.content.MaterialCategory
import com.tinyblacksmith.core.crafting.Journal
import com.tinyblacksmith.core.engine.Command
import com.tinyblacksmith.core.engine.GameError
import com.tinyblacksmith.core.engine.Technique
import com.tinyblacksmith.core.model.ForgeMode
import com.tinyblacksmith.core.model.Journal as JournalModel
import com.tinyblacksmith.core.model.KnowledgeState
import com.tinyblacksmith.core.model.MaterialId
import com.tinyblacksmith.core.model.Risk

private const val NO_STEP = "none"

/**
 * Forge flow (GDD 12): the preview and the Forge button stay pinned above the choices, so the player always sees
 * what they are making. Choices are steps; the first unfinished step is open and picking advances to the next,
 * which keeps familiar recipes to three taps. The open step and the scroll position are kept while the player is on
 * another destination. A step scrolls into view only after something the player did (a step or a chip tapped here,
 * "Forge this" elsewhere), never on arriving: the top of the list, with Supplies and Journal, stays where it was left.
 */
@Composable
fun ForgePanel(s: UiState.Playing, vm: GameViewModel, reducedMotion: Boolean, tip: Tips.Tip?, onOpenSupplies: () -> Unit = {}) {
    val content = vm.engine.content
    val config = vm.engine.config
    val st = s.state
    val d = s.draft

    Column(Modifier.fillMaxSize()) {
        // The forge as a place: the painted room at 120 dp (less on a low screen or with large text, where the steps
        // need the height), with the forge's standing and the next siege on a plate along its foot.
        val short = LocalConfiguration.current.screenHeightDp < 700 || LocalDensity.current.fontScale > 1.15f
        Box(Modifier.fillMaxWidth().heightIn(min = if (short) 72.dp else 120.dp).testTag("forge_backdrop")) {
            Backdrop(R.drawable.bg_counter_forge, Modifier.matchParentSize().clearAndSetSemantics {}, sink = 0.2f)
            ThreatLine(s, Modifier.align(Alignment.BottomStart).background(SceneDeep.copy(alpha = 0.88f)))
        }
        ForgeSummary(s, vm)
        // The plate has just said a chosen material has run out: the way to buy more is right under it.
        if (listOfNotNull(d.coreId, d.augmentId, d.catalystId).any { (st.materials[it] ?: 0) == 0 })
            SecondaryActionButton("Open Supplies", onOpenSupplies, Modifier.fillMaxWidth().padding(horizontal = Space.md).testTag("forge_supplies_missing"))

        // null = open the first unfinished step; NO_STEP = everything collapsed; otherwise the step the player opened.
        var opened by rememberSaveable { mutableStateOf<String?>(null) }
        val firstUnfinished = when {
            d.familyId == null -> "family"
            d.coreId == null -> "core"
            d.augmentId == null -> "augment"
            else -> NO_STEP
        }
        val open = opened ?: firstUnfinished
        // Not saved: coming back to the Forge starts with it off, so nothing moves until the player acts.
        var follow by remember { mutableStateOf(false) }
        fun toggle(id: String) { opened = if (open == id) NO_STEP else id; follow = true }
        fun pick(transform: (ForgeDraft) -> ForgeDraft) { vm.updateDraft(transform); opened = null; follow = true }
        // "Forge this" or "Use this recipe" was tapped since this panel last looked: show the step to choose next.
        var revealed by rememberSaveable { mutableIntStateOf(0) }
        LaunchedEffect(s.forgeReveal) {
            if (s.forgeReveal > revealed) { opened = null; follow = true }
            revealed = s.forgeReveal
        }

        val scroll = rememberScrollState()
        Column(Modifier.weight(1f).verticalScroll(scroll).padding(horizontal = Space.md, vertical = Space.sm)) {
            tip?.let { TipBanner(it, vm) }
            Row(horizontalArrangement = Arrangement.spacedBy(Space.sm), modifier = Modifier.fillMaxWidth()) {
                SecondaryActionButton("Supplies", onOpenSupplies, Modifier.weight(1f).testTag("forge_supplies"))
                SecondaryActionButton("Journal", { vm.selectRecords(RecordsPage.JOURNAL) }, Modifier.weight(1f).testTag("forge_journal"))
            }
            Wanted(s, vm)

            Step("mode", "Mode", if (d.mode == ForgeMode.QUICK) "Quick · ${config.quickForgeEnergy} energy" else "Advanced · ${config.advancedForgeEnergy} energy", open, scroll, follow, ::toggle) {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    ForgeMode.entries.forEachIndexed { i, mode ->
                        SegmentedButton(
                            selected = d.mode == mode,
                            onClick = { vm.updateDraft { it.copy(mode = mode, catalystId = it.catalystId?.takeIf { mode == ForgeMode.ADVANCED }, technique = it.technique?.takeIf { mode == ForgeMode.ADVANCED }) } },
                            shape = SegmentedButtonDefaults.itemShape(index = i, count = ForgeMode.entries.size, baseShape = MaterialTheme.shapes.small),
                            modifier = Modifier.heightIn(min = 48.dp),
                        ) { Text(if (mode == ForgeMode.QUICK) "Quick" else "Advanced", maxLines = 1) }
                    }
                }
                Secondary(
                    if (d.mode == ForgeMode.QUICK) "Quick forge costs ${config.quickForgeEnergy} energy and skips catalysts and techniques."
                    else "Advanced forge costs ${config.advancedForgeEnergy} energy and allows a catalyst and a technique.",
                    Modifier.padding(top = Space.sm),
                )
            }

            Step("family", "Family", d.familyId?.let { content.family(it).name }, open, scroll, follow, ::toggle) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.sm), verticalArrangement = Arrangement.spacedBy(Space.xs)) {
                    content.families.forEach { f ->
                        val previewCore = d.coreId ?: content.materials(MaterialCategory.CORE).first().id
                        FilterChip(
                            selected = d.familyId == f.id,
                            onClick = { pick { it.copy(familyId = f.id) } },
                            leadingIcon = { PixelImage(Sprites.weapon(f.id, previewCore, null), 24.dp, description = null) },
                            label = { Text(f.name) },
                            modifier = Modifier.heightIn(min = 48.dp),
                        )
                    }
                }
            }
            Step("core", "Core metal", d.coreId?.let { content.material(it).name }, open, scroll, follow, ::toggle) {
                MaterialChips(MaterialCategory.CORE, d.coreId, s, vm, onOpenSupplies = onOpenSupplies) { id -> pick { it.copy(coreId = id) } }
            }
            Step("augment", "Augment", d.augmentId?.let { content.material(it).name }, open, scroll, follow, ::toggle) {
                MaterialChips(MaterialCategory.AUGMENT, d.augmentId, s, vm, onOpenSupplies = onOpenSupplies) { id -> pick { it.copy(augmentId = id) } }
            }
            if (d.mode == ForgeMode.ADVANCED) {
                Step("catalyst", "Catalyst", d.catalystId?.let { content.material(it).name } ?: "None", open, scroll, follow, ::toggle) {
                    MaterialChips(MaterialCategory.CATALYST, d.catalystId, s, vm, noneLabel = "None", onOpenSupplies = onOpenSupplies) { id -> pick { it.copy(catalystId = id) } }
                }
                Step("technique", "Technique", d.technique?.let { Labels.technique(it) } ?: "Plain", open, scroll, follow, ::toggle) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.sm), verticalArrangement = Arrangement.spacedBy(Space.xs)) {
                        FilterChip(selected = d.technique == null, onClick = { pick { it.copy(technique = null) } }, label = { Text("Plain") }, modifier = Modifier.heightIn(min = 48.dp))
                        Technique.entries.forEach { t ->
                            FilterChip(selected = d.technique == t, onClick = { pick { it.copy(technique = t) } }, label = { Text(Labels.technique(t)) }, modifier = Modifier.heightIn(min = 48.dp))
                        }
                    }
                    d.technique?.let { Secondary(Labels.techniqueExplanation(it), Modifier.padding(top = Space.sm)) }
                }
            }
            Step("risk", "Risk", Labels.risk(d.risk).substringBefore(" —"), open, scroll, follow, ::toggle) {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    Risk.entries.forEachIndexed { i, r ->
                        SegmentedButton(
                            selected = d.risk == r,
                            onClick = { vm.updateDraft { it.copy(risk = r) } },
                            shape = SegmentedButtonDefaults.itemShape(index = i, count = Risk.entries.size, baseShape = MaterialTheme.shapes.small),
                            modifier = Modifier.heightIn(min = 48.dp),
                        ) { Text(Labels.risk(r).substringBefore(" —"), maxLines = 1) }
                    }
                }
                Secondary(riskExplanation(d.risk), Modifier.padding(top = Space.sm))
            }

            if (d.coreId != null && d.augmentId != null) {
                SectionHeader("Journal says")
                AffinityHint(st.legacy.journal, content, JournalModel.coreAugmentKey(d.coreId, d.augmentId))
                if (d.familyId != null) AffinityHint(st.legacy.journal, content, JournalModel.augmentFamilyKey(d.augmentId, d.familyId))
            }

            val stored = st.storedWeapons().size
            Secondary(
                if (stored == 0) "Forged weapons wait in storage until you list them in the Shop."
                else "$stored in storage, waiting to be listed in the Shop.",
                Modifier.padding(top = Space.lg, bottom = Space.lg),
            )
        }
    }
}

/** Forge integrity and the next siege: the run-over condition belongs with the forge, not in the global bar. */
@Composable
private fun ThreatLine(s: UiState.Playing, modifier: Modifier = Modifier) {
    val st = s.state
    // The besieger as things stand (the leader can change day to day) and what bites it or glances off: core's own words.
    val threat = s.shop.threat?.plate ?: "The roads are quiet"
    Row(
        modifier.fillMaxWidth().padding(horizontal = Space.md, vertical = 6.dp).semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        PixelImage(R.drawable.icon_integrity, 18.dp, description = null)
        Text(
            buildAnnotatedString {
                withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append("Forge ${st.town.integrity}") }
                append(" · $threat")
            },
            style = MaterialTheme.typography.labelMedium,
            color = SceneCream,
            modifier = Modifier.testTag("forge_threat"),
        )
    }
}

/**
 * What is asked for, beside the draft: each standing want no listed blade answers, with "Forge this" (sets the family),
 * then every open request with "Forge this", which sets the family (and an augment of the element asked for). The request the draft was started from says so and shows what the shop still lacks for it.
 */
@Composable
private fun Wanted(s: UiState.Playing, vm: GameViewModel) {
    // What a hero left without and nothing listed answers yet: the same lines as the Shop's "Who is buying".
    val wants = s.shop.wants.filter { !it.answered }
    if (wants.isNotEmpty()) SectionHeader("Asked for at the counter")
    wants.forEach { w ->
        Row(Modifier.fillMaxWidth().padding(top = Space.xs).testTag("forge_want_row_${w.heroId.value}"), verticalAlignment = Alignment.CenterVertically) {
            Text(w.line, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            TextButton(onClick = { vm.forgeFamily(w.familyId) }, modifier = Modifier.heightIn(min = 48.dp).testTag("forge_want_${w.heroId.value}")) { Text("Forge this") }
        }
    }
    val requests = s.shop.requests
    if (requests.isEmpty()) return
    SectionHeader("Requests")
    requests.forEach { r ->
        val chosen = s.draft.commissionId == r.id
        Row(Modifier.fillMaxWidth().padding(top = Space.xs).testTag("forge_request_${r.id.value}"), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(r.asks, style = MaterialTheme.typography.bodyMedium, fontWeight = if (chosen) FontWeight.SemiBold else null)
                Secondary("${r.buyer.name} · ${r.terms}")
                r.why?.let { Secondary(it) }
            }
            if (chosen) TextButton(onClick = { vm.updateDraft { it.copy(commissionId = null) } }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Forging this ✓") }
            else TextButton(onClick = { vm.forgeFor(r.id) }, modifier = Modifier.heightIn(min = 48.dp).testTag("forge_this_${r.id.value}")) { Text("Forge this") }
        }
    }
}

/** Pinned above the steps: preview sprite, the recipe in words, the cost and the one primary action. */
@Composable
private fun ForgeSummary(s: UiState.Playing, vm: GameViewModel) {
    val content = vm.engine.content
    val config = vm.engine.config
    val st = s.state
    val d = s.draft
    val family = d.familyId
    val core = d.coreId
    val chosen = family != null && core != null
    val ready = chosen && d.augmentId != null
    val cost = if (d.mode == ForgeMode.QUICK) config.quickForgeEnergy else config.advancedForgeEnergy
    val overworkRoom = config.maxOverworkPerDay - st.overworkToday
    val overwork = (cost - st.energy).coerceAtLeast(0)
    val canAfford = overwork <= overworkRoom
    val title = if (chosen) "${content.material(core!!).name} ${content.family(family!!).name}" else "Nothing on the anvil"
    val recipe = buildList {
        add(if (d.mode == ForgeMode.QUICK) "Quick" else "Advanced")
        d.augmentId?.let { add(content.material(it).name) }
        d.catalystId?.let { add(content.material(it).name) }
        d.technique?.let { add(Labels.technique(it)) }
        add(Labels.risk(d.risk).substringBefore(" —").lowercase())
    }.joinToString(" · ")
    // A material chosen earlier may have run out since; mirror the engine's own rejection instead of discovering it on tap.
    val missing = listOfNotNull(d.coreId, d.augmentId, d.catalystId).firstOrNull { (st.materials[it] ?: 0) == 0 }
    val note = when {
        !ready -> "Choose a family, a core and an augment."
        missing != null -> vm.describe(GameError.MissingMaterial(missing)) + " Pick another or buy more in Supplies."
        !canAfford -> vm.describe(GameError.NotEnoughEnergy(cost, st.energy, overworkRoom)) + " Rest with End Day."
        overwork > 0 -> "Costs $cost energy; $overwork of it is overwork that tires you tomorrow."
        else -> "Costs $cost energy. Ready when you are."
    }

    // The anvil plate: what is being made, then the one gold action across its whole width (a button beside the title
    // had no room for its two words once the text was large), then what it costs or why it cannot be done.
    FramedPanel(modifier = Modifier.fillMaxWidth().padding(horizontal = Space.sm, vertical = Space.xs), contentPadding = PaddingValues(horizontal = Space.md, vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            val element = d.augmentId?.let { content.material(it).element }
            Box(Modifier.background(ForgeSlot).border(1.dp, Bronze).padding(2.dp).size(52.dp).alpha(if (chosen) 1f else 0.35f)) {
                PixelImage(
                    Sprites.weapon(family ?: content.families.first().id, core ?: content.materials(MaterialCategory.CORE).first().id, element),
                    52.dp,
                    description = "Preview: $title",
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = Gold, maxLines = 2)
                Secondary(recipe)
            }
        }
        val enabled = ready && canAfford && missing == null && !s.busy
        PrimaryActionButton(
            "Forge weapon", { vm.dispatch(Command.Forge(d.mode, d.familyId!!, d.coreId!!, d.augmentId!!, d.catalystId, d.risk, d.technique)) },
            // A disabled button says why, so a screen reader is not left with a dead "Forge weapon".
            Modifier.fillMaxWidth().padding(top = Space.sm).testTag("forge_weapon").semantics { if (!enabled) contentDescription = "Forge weapon, unavailable: $note" },
            enabled = enabled,
        )
        Text(note, style = MaterialTheme.typography.bodySmall, color = if (ready && canAfford && missing == null) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
        // The request this draft was started from stays in view while the steps scroll.
        s.shop.requests.firstOrNull { it.id == d.commissionId }?.let { r ->
            Text("For ${r.buyer.name}: ${r.asks}", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = Space.xs).testTag("forge_for"))
            r.readiness?.let { Secondary(it) }
        }
    }
}

/** One step of the recipe: a header that always shows the chosen value, and the choices when open. */
@Composable
private fun Step(id: String, label: String, value: String?, open: String, scroll: ScrollState, follow: Boolean, onToggle: (String) -> Unit, choices: @Composable () -> Unit) {
    val isOpen = open == id
    val requester = remember { BringIntoViewRequester() }
    var height by remember { mutableIntStateOf(0) }
    // Re-requested when the opened choices are laid out. The rect is capped to the viewport so a step taller than
    // it (large fonts, small screens) aligns its header at the top instead of its last chip at the bottom.
    LaunchedEffect(isOpen, height, follow) {
        if (isOpen && follow && height > 0) requester.bringIntoView(Rect(0f, 0f, 0f, minOf(height, scroll.viewportSize - 1).toFloat()))
    }
    Column(Modifier.fillMaxWidth().padding(top = Space.sm).bringIntoViewRequester(requester).onSizeChanged { height = it.height }) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable { onToggle(id) }
                .semantics(mergeDescendants = true) { contentDescription = "$label: ${value ?: "not chosen"}, ${if (isOpen) "open" else "tap to change"}" },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            Text(
                value ?: "Choose",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (value != null) FontWeight.SemiBold else FontWeight.Normal,
                color = if (value != null) Gold else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(if (isOpen) "▴" else "▾", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = Space.sm))
        }
        if (isOpen) Column(Modifier.padding(bottom = Space.sm)) { choices() }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

/** Material chips; out-of-stock ones are disabled and explained with the engine's own error text. */
@Composable
private fun MaterialChips(
    category: MaterialCategory,
    selected: MaterialId?,
    s: UiState.Playing,
    vm: GameViewModel,
    noneLabel: String? = null,
    onOpenSupplies: () -> Unit = {},
    onPick: (MaterialId?) -> Unit,
) {
    val materials = vm.engine.content.materials(category)
    val missing = materials.filter { (s.state.materials[it.id] ?: 0) == 0 }
    // An element the besieger is weak to or resists: its chip carries the sign, and the words stand once under the chips.
    val marks = s.shop.threat?.marks.orEmpty()
    FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.sm), verticalArrangement = Arrangement.spacedBy(Space.xs)) {
        if (noneLabel != null) FilterChip(selected = selected == null, onClick = { onPick(null) }, label = { Text(noneLabel) }, modifier = Modifier.heightIn(min = 48.dp))
        materials.forEach { m ->
            val n = s.state.materials[m.id] ?: 0
            val reason = if (n == 0) vm.describe(GameError.MissingMaterial(m.id)) else null
            val mark = m.element?.let { marks[it] }
            FilterChip(
                selected = selected == m.id,
                enabled = n > 0,
                onClick = { onPick(m.id) },
                leadingIcon = Sprites.material(m.id)?.let { res -> { PixelImage(res, 20.dp, description = null) } },
                trailingIcon = mark?.let { { Text(it.kind.sign, style = MaterialTheme.typography.titleSmall, color = it.kind.color) } },
                label = { Text(if (n > 0) "${m.name} ×$n" else m.name) },
                modifier = Modifier.heightIn(min = 48.dp).semantics {
                    if (reason != null) contentDescription = "${m.name}: $reason"
                    else if (mark != null) contentDescription = "${m.name}, $n in stock. ${mark.label}"
                },
            )
        }
    }
    materials.mapNotNull { m -> m.element?.takeIf { it in marks } }.distinct().forEach { e ->
        val mark = marks.getValue(e)
        Text("${mark.kind.sign} ${e.name.lowercase().replaceFirstChar { it.uppercase() }}: ${mark.label}", style = MaterialTheme.typography.bodySmall, color = mark.kind.color, modifier = Modifier.padding(top = Space.xs).testTag("forge_mark_${e.name.lowercase()}"))
    }
    if (missing.isNotEmpty()) {
        Secondary("Out of stock: ${missing.joinToString { it.name }}. Buy more in Supplies.", Modifier.padding(top = Space.sm))
        // Beside the reason, not only at the top of the list: the recipe and this place are still here after buying.
        SecondaryActionButton("Open Supplies", onOpenSupplies, Modifier.padding(top = Space.xs).testTag("forge_supplies_${category.name.lowercase()}"))
    }
}

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
