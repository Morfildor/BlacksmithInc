package com.example.blacksmithproject.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.blacksmithproject.GameViewModel
import com.example.blacksmithproject.UiState
import com.tinyblacksmith.core.content.ContentCatalog
import com.tinyblacksmith.core.content.MaterialCategory
import com.tinyblacksmith.core.crafting.Journal
import com.tinyblacksmith.core.engine.Command
import com.tinyblacksmith.core.engine.Technique
import com.tinyblacksmith.core.model.ForgeMode
import com.tinyblacksmith.core.model.Journal as JournalModel
import com.tinyblacksmith.core.model.KnowledgeState
import com.tinyblacksmith.core.model.MaterialId
import com.tinyblacksmith.core.model.Risk

/** Forge flow (GDD 12): mode -> family -> core -> augment -> optional catalyst -> hints -> risk -> cost -> Forge. */
@Composable
fun ForgePanel(s: UiState.Playing, vm: GameViewModel) {
    val content = vm.engine.content
    val config = vm.engine.config
    val st = s.state
    val d = s.draft

    GroupCard("Mode") {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = d.mode == ForgeMode.QUICK, onClick = { vm.updateDraft { it.copy(mode = ForgeMode.QUICK, catalystId = null) } }, label = { Text("Quick · ${config.quickForgeEnergy} energy") })
            FilterChip(selected = d.mode == ForgeMode.ADVANCED, onClick = { vm.updateDraft { it.copy(mode = ForgeMode.ADVANCED) } }, label = { Text("Advanced · ${config.advancedForgeEnergy} energy") })
        }
    }

    GroupCard("Weapon family") {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            content.families.forEach { f ->
                FilterChip(selected = d.familyId == f.id, onClick = { vm.updateDraft { it.copy(familyId = f.id) } }, label = { Text(f.name) })
            }
        }
    }

    GroupCard("Core metal") { MaterialChips(MaterialCategory.CORE, d.coreId, s, vm) { id -> copy(coreId = id) } }
    GroupCard("Augment") { MaterialChips(MaterialCategory.AUGMENT, d.augmentId, s, vm) { id -> copy(augmentId = id) } }

    if (d.mode == ForgeMode.ADVANCED) {
        GroupCard("Catalyst (optional)") {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = d.catalystId == null, onClick = { vm.updateDraft { it.copy(catalystId = null) } }, label = { Text("None") })
                MaterialChips(MaterialCategory.CATALYST, d.catalystId, s, vm) { id -> copy(catalystId = id) }
            }
        }
        GroupCard("Technique (optional)") {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = d.technique == null, onClick = { vm.updateDraft { it.copy(technique = null) } }, label = { Text("Plain") })
                Technique.entries.forEach { t ->
                    FilterChip(selected = d.technique == t, onClick = { vm.updateDraft { it.copy(technique = t) } }, label = { Text(Labels.technique(t)) })
                }
            }
            d.technique?.let { Text(Labels.techniqueExplanation(it), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp)) }
        }
    }

    // Preview and journal hints sit below the chips so their text never shadows a chip label.
    Card(Modifier.fillMaxWidth().padding(vertical = 6.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val family = d.familyId
                val core = d.coreId
                val label = if (family != null && core != null) "${content.material(core).name} ${content.family(family).name}" else "no weapon chosen yet"
                Box(Modifier.size(64.dp).alpha(if (family != null && core != null) 1f else 0.35f)) {
                    d.augmentId?.let { content.material(it).element }?.let { Sprites.overlay(it) }?.let { PixelImage(it, 64.dp, description = null, modifier = Modifier.alpha(0.85f)) }
                    PixelImage(Sprites.weapon(family ?: content.families.first().id, core ?: content.materials(MaterialCategory.CORE).first().id), 64.dp, description = "Preview: $label")
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("Preview", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        if (family != null && core != null) "Forging: $label" + (d.augmentId?.let { " with ${content.material(it).name}" } ?: "") else "Choose a family and core metal",
                        style = MaterialTheme.typography.titleSmall,
                    )
                }
            }
            if (d.coreId != null && d.augmentId != null) {
                Text("Journal says", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 10.dp).semantics { heading() })
                AffinityHint(st.legacy.journal, content, JournalModel.coreAugmentKey(d.coreId, d.augmentId))
                if (d.familyId != null) AffinityHint(st.legacy.journal, content, JournalModel.augmentFamilyKey(d.augmentId, d.familyId))
            }
        }
    }

    SectionTitle("Risk")
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Risk.entries.forEach { r ->
            FilterChip(selected = d.risk == r, onClick = { vm.updateDraft { it.copy(risk = r) } }, label = { Text(Labels.risk(r).substringBefore(" —")) })
        }
    }
    Text(riskExplanation(d.risk), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))

    val cost = if (d.mode == ForgeMode.QUICK) config.quickForgeEnergy else config.advancedForgeEnergy
    val overworkRoom = config.maxOverworkPerDay - st.overworkToday
    val needsOverwork = cost > st.energy
    val ready = d.familyId != null && d.coreId != null && d.augmentId != null
    Text(
        when {
            !ready -> "Choose a family, core and augment."
            needsOverwork && cost - st.energy <= overworkRoom -> "Costs $cost energy; ${cost - st.energy} of it is overwork and will tire you tomorrow."
            needsOverwork -> "Not enough energy today. Rest with End Day."
            else -> "Costs $cost energy."
        },
        style = MaterialTheme.typography.bodySmall,
        modifier = Modifier.padding(top = 10.dp),
    )
    Button(
        enabled = ready && !s.busy && (!needsOverwork || cost - st.energy <= overworkRoom),
        onClick = { vm.dispatch(Command.Forge(d.mode, d.familyId!!, d.coreId!!, d.augmentId!!, d.catalystId, d.risk, d.technique)) },
        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).padding(top = 8.dp),
    ) { Text("Forge weapon", style = MaterialTheme.typography.titleMedium) }

    SectionTitle("In storage (${st.storedWeapons().size})")
    if (st.storedWeapons().isEmpty()) Text("Nothing stored. Forged weapons wait here until you list them.", style = MaterialTheme.typography.bodySmall)
    st.storedWeapons().forEach { w ->
        Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            WeaponSprite(w, size = 36.dp)
            Text("${w.name} — ${Labels.weaponSummary(w, content)}", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun GroupCard(title: String, content: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(bottom = 2.dp).semantics { heading() })
            content()
        }
    }
}

@Composable
private fun MaterialChips(
    category: MaterialCategory,
    selected: MaterialId?,
    s: UiState.Playing,
    vm: GameViewModel,
    pick: com.example.blacksmithproject.ForgeDraft.(MaterialId) -> com.example.blacksmithproject.ForgeDraft,
) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        vm.engine.content.materials(category).forEach { m ->
            val n = s.state.materials[m.id] ?: 0
            FilterChip(
                selected = selected == m.id,
                enabled = n > 0,
                onClick = { vm.updateDraft { it.pick(m.id) } },
                leadingIcon = Sprites.material(m.id)?.let { res -> { PixelImage(res, 20.dp, description = null) } },
                label = { Text("${m.name} ×$n") },
            )
        }
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
        Modifier.padding(top = 4.dp).semantics(mergeDescendants = true) { contentDescription = "$subject, $stateLabel: $hint" },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(32.dp)) {
            Box(contentAlignment = Alignment.Center) { Text(glyph, style = MaterialTheme.typography.titleSmall) }
        }
        Spacer(Modifier.width(10.dp))
        Column {
            Text("$subject: $hint", style = MaterialTheme.typography.bodyMedium)
            Text(stateLabel, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun riskExplanation(r: Risk): String = when (r) {
    Risk.SAFE -> "Safe: steady work with few surprises. Flaws are rare, but so are brilliant results."
    Risk.BALANCED -> "Balanced: the usual gamble. Some flaws, some brilliance, mostly honest work."
    Risk.RECKLESS -> "Reckless: push the metal hard. Brilliance comes more often, and so do flaws. Every forge still yields a usable weapon."
}
