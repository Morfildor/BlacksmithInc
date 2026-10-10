package com.example.blacksmithproject.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
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
import androidx.compose.ui.semantics.heading
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
 * which keeps familiar recipes to three taps.
 */
@Composable
fun ForgePanel(s: UiState.Playing, vm: GameViewModel, reducedMotion: Boolean, tip: Tips.Tip?) {
    val content = vm.engine.content
    val config = vm.engine.config
    val st = s.state
    val d = s.draft

    Column(Modifier.fillMaxSize()) {
        ForgeScene(heat = st.energy / config.baseDailyEnergy.toFloat(), reducedMotion = reducedMotion, height = 52.dp)
        ThreatLine(s, vm)
        ForgeSummary(s, vm)
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        // null = open the first unfinished step; NO_STEP = everything collapsed; otherwise the step the player opened.
        var opened by rememberSaveable { mutableStateOf<String?>(null) }
        val firstUnfinished = when {
            d.familyId == null -> "family"
            d.coreId == null -> "core"
            d.augmentId == null -> "augment"
            else -> NO_STEP
        }
        val open = opened ?: firstUnfinished
        fun toggle(id: String) { opened = if (open == id) NO_STEP else id }
        fun pick(transform: (ForgeDraft) -> ForgeDraft) { vm.updateDraft(transform); opened = null }

        val scroll = remember { ScrollState(0) }
        Column(Modifier.weight(1f).verticalScroll(scroll).padding(horizontal = Space.md, vertical = Space.sm)) {
            tip?.let { TipBanner(it, vm) }

            Step("mode", "Mode", if (d.mode == ForgeMode.QUICK) "Quick · ${config.quickForgeEnergy} energy" else "Advanced · ${config.advancedForgeEnergy} energy", open, scroll, ::toggle) {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    ForgeMode.entries.forEachIndexed { i, mode ->
                        SegmentedButton(
                            selected = d.mode == mode,
                            onClick = { vm.updateDraft { it.copy(mode = mode, catalystId = it.catalystId?.takeIf { mode == ForgeMode.ADVANCED }, technique = it.technique?.takeIf { mode == ForgeMode.ADVANCED }) } },
                            shape = SegmentedButtonDefaults.itemShape(index = i, count = ForgeMode.entries.size),
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

            Step("family", "Family", d.familyId?.let { content.family(it).name }, open, scroll, ::toggle) {
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
            Step("core", "Core metal", d.coreId?.let { content.material(it).name }, open, scroll, ::toggle) {
                MaterialChips(MaterialCategory.CORE, d.coreId, s, vm) { id -> pick { it.copy(coreId = id) } }
            }
            Step("augment", "Augment", d.augmentId?.let { content.material(it).name }, open, scroll, ::toggle) {
                MaterialChips(MaterialCategory.AUGMENT, d.augmentId, s, vm) { id -> pick { it.copy(augmentId = id) } }
            }
            if (d.mode == ForgeMode.ADVANCED) {
                Step("catalyst", "Catalyst", d.catalystId?.let { content.material(it).name } ?: "None", open, scroll, ::toggle) {
                    MaterialChips(MaterialCategory.CATALYST, d.catalystId, s, vm, noneLabel = "None") { id -> pick { it.copy(catalystId = id) } }
                }
                Step("technique", "Technique", d.technique?.let { Labels.technique(it) } ?: "Plain", open, scroll, ::toggle) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.sm), verticalArrangement = Arrangement.spacedBy(Space.xs)) {
                        FilterChip(selected = d.technique == null, onClick = { pick { it.copy(technique = null) } }, label = { Text("Plain") }, modifier = Modifier.heightIn(min = 48.dp))
                        Technique.entries.forEach { t ->
                            FilterChip(selected = d.technique == t, onClick = { pick { it.copy(technique = t) } }, label = { Text(Labels.technique(t)) }, modifier = Modifier.heightIn(min = 48.dp))
                        }
                    }
                    d.technique?.let { Secondary(Labels.techniqueExplanation(it), Modifier.padding(top = Space.sm)) }
                }
            }
            Step("risk", "Risk", Labels.risk(d.risk).substringBefore(" —"), open, scroll, ::toggle) {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    Risk.entries.forEachIndexed { i, r ->
                        SegmentedButton(
                            selected = d.risk == r,
                            onClick = { vm.updateDraft { it.copy(risk = r) } },
                            shape = SegmentedButtonDefaults.itemShape(index = i, count = Risk.entries.size),
                            modifier = Modifier.heightIn(min = 48.dp),
                        ) { Text(Labels.risk(r).substringBefore(" —"), maxLines = 1) }
                    }
                }
                Secondary(riskExplanation(d.risk), Modifier.padding(top = Space.sm))
            }

            if (d.coreId != null && d.augmentId != null) {
                Text("Journal says", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = Space.lg, bottom = Space.xs).semantics { heading() })
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
private fun ThreatLine(s: UiState.Playing, vm: GameViewModel) {
    val st = s.state
    val faction = st.factions.values.maxByOrNull { it.pressure }
    val daysLeft = st.town.nextSiegeDay - st.day
    val siege = when {
        daysLeft <= 0 -> "siege today"
        daysLeft == 1 -> "siege tomorrow"
        else -> "siege in $daysLeft days"
    }
    // The leader can change day to day; "lead" says it is a standing, not a promise. Town carries the descriptor.
    val pressure = faction?.let { "${vm.engine.content.faction(it.id).name} lead" } ?: "the roads are quiet"
    Row(
        Modifier.fillMaxWidth().padding(horizontal = Space.md, vertical = 6.dp).semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        PixelImage(R.drawable.icon_integrity, 18.dp, description = null)
        Text(
            buildAnnotatedString {
                withStyle(SpanStyle(fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)) { append("Forge ${st.town.integrity}") }
                append(" · ${siege.replaceFirstChar { it.uppercase() }} · $pressure")
            },
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
        )
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
        missing != null -> vm.describe(GameError.MissingMaterial(missing)) + " Pick another or buy more in the Shop."
        !canAfford -> vm.describe(GameError.NotEnoughEnergy(cost, st.energy, overworkRoom)) + " Rest with End Day."
        overwork > 0 -> "Costs $cost energy; $overwork of it is overwork that tires you tomorrow."
        else -> "Costs $cost energy. Ready when you are."
    }

    Surface(tonalElevation = 2.dp) {
        Column(Modifier.fillMaxWidth().padding(horizontal = Space.md, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val element = d.augmentId?.let { content.material(it).element }
                Box(Modifier.size(56.dp).alpha(if (chosen) 1f else 0.35f)) {
                    PixelImage(
                        Sprites.weapon(family ?: content.families.first().id, core ?: content.materials(MaterialCategory.CORE).first().id, element),
                        56.dp,
                        description = "Preview: $title",
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 2)
                    Secondary(recipe)
                }
                Spacer(Modifier.width(Space.sm))
                val enabled = ready && canAfford && missing == null && !s.busy
                Button(
                    enabled = enabled,
                    onClick = { vm.dispatch(Command.Forge(d.mode, d.familyId!!, d.coreId!!, d.augmentId!!, d.catalystId, d.risk, d.technique)) },
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = Space.sm),
                    // A disabled button says why, so a screen reader is not left with a dead "Forge weapon".
                    modifier = Modifier.width(116.dp).heightIn(min = 56.dp).testTag("forge_weapon").semantics { if (!enabled) contentDescription = "Forge weapon, unavailable: $note" },
                ) { Text("Forge weapon", style = MaterialTheme.typography.titleSmall, textAlign = TextAlign.Center, maxLines = 2) }
            }
            Text(note, style = MaterialTheme.typography.bodySmall, color = if (ready && canAfford && missing == null) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
        }
    }
}

/** One step of the recipe: a header that always shows the chosen value, and the choices when open. */
@Composable
private fun Step(id: String, label: String, value: String?, open: String, scroll: ScrollState, onToggle: (String) -> Unit, choices: @Composable () -> Unit) {
    val isOpen = open == id
    val requester = remember { BringIntoViewRequester() }
    var height by remember { mutableIntStateOf(0) }
    // Re-requested when the opened choices are laid out. The rect is capped to the viewport so a step taller than
    // it (large fonts, small screens) aligns its header at the top instead of its last chip at the bottom.
    LaunchedEffect(isOpen, height) {
        if (isOpen && height > 0) requester.bringIntoView(Rect(0f, 0f, 0f, minOf(height, scroll.viewportSize - 1).toFloat()))
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
                color = if (value != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
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
    onPick: (MaterialId?) -> Unit,
) {
    val materials = vm.engine.content.materials(category)
    val missing = materials.filter { (s.state.materials[it.id] ?: 0) == 0 }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.sm), verticalArrangement = Arrangement.spacedBy(Space.xs)) {
        if (noneLabel != null) FilterChip(selected = selected == null, onClick = { onPick(null) }, label = { Text(noneLabel) }, modifier = Modifier.heightIn(min = 48.dp))
        materials.forEach { m ->
            val n = s.state.materials[m.id] ?: 0
            val reason = if (n == 0) vm.describe(GameError.MissingMaterial(m.id)) else null
            FilterChip(
                selected = selected == m.id,
                enabled = n > 0,
                onClick = { onPick(m.id) },
                leadingIcon = Sprites.material(m.id)?.let { res -> { PixelImage(res, 20.dp, description = null) } },
                label = { Text(if (n > 0) "${m.name} ×$n" else m.name) },
                modifier = Modifier.heightIn(min = 48.dp).semantics { if (reason != null) contentDescription = "${m.name}: $reason" },
            )
        }
    }
    if (missing.isNotEmpty()) {
        Secondary("Out of stock: ${missing.joinToString { it.name }}. Buy more in the Shop.", Modifier.padding(top = Space.sm))
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
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(32.dp)) {
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
