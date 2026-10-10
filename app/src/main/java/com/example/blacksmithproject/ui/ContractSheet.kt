package com.example.blacksmithproject.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.blacksmithproject.ui.theme.Bronze
import com.example.blacksmithproject.ui.theme.BronzeContainer
import com.example.blacksmithproject.ui.theme.BronzeDeep
import com.example.blacksmithproject.ui.theme.Cream
import com.example.blacksmithproject.ui.theme.CreamMuted
import com.example.blacksmithproject.ui.theme.Ember
import com.example.blacksmithproject.ui.theme.ForgeSlot
import com.example.blacksmithproject.ui.theme.Gold
import com.example.blacksmithproject.ui.theme.Space
import com.tinyblacksmith.core.combat.Posture
import com.tinyblacksmith.core.engine.GameEngine
import com.tinyblacksmith.core.model.GameState
import com.tinyblacksmith.core.model.HeroId

/**
 * One contract and the party for it: who goes (in the order they act), how they hold themselves, and what the kits and
 * blades of that party say before anybody leaves. Nothing is sent until "Send"; the plan can be changed or cancelled
 * all morning, and the fee comes back with it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContractSheet(
    state: GameState, engine: GameEngine, offerId: String, busy: Boolean,
    onSend: (List<HeroId>, Posture) -> Unit, onCancelPlan: () -> Unit, onDismiss: () -> Unit, modifier: Modifier = Modifier,
) {
    // Opened on today's plan when this contract is the one planned; otherwise on an empty party.
    val planned = state.guild?.planned?.takeIf { it.offerId == offerId }
    var picked by rememberSaveable(offerId) { mutableStateOf(planned?.heroIds.orEmpty().map { it.value }) }
    var posture by rememberSaveable(offerId) { mutableStateOf((planned?.posture ?: Posture.BALANCED).name) }
    val ui = remember(state, offerId, picked, posture) { engine.contractUi(state, offerId, picked.map(::HeroId), Posture.valueOf(posture)) } ?: return
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        dragHandle = { BottomSheetDefaults.DragHandle(width = 48.dp) },
        modifier = modifier.semantics { testTagsAsResourceId = true }.testTag("contract_sheet"),
    ) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(horizontal = Space.md).padding(bottom = Space.lg)) {
            Text(ui.offer.title, style = MaterialTheme.typography.titleLarge, color = Gold, modifier = Modifier.semantics { heading() })
            Text(ui.offer.time, style = MaterialTheme.typography.bodyMedium)
            ui.offer.missesSiege?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = Ember) }
            Secondary(ui.offer.fee)

            SectionHeader("The party")
            Secondary("Up to ${ui.partyMax}. They act in the order you pick them.")
            ui.picks.forEach { p ->
                val full = p.order == null && ui.picks.count { it.order != null } >= ui.partyMax
                val can = p.blocked == null && !full && !busy
                Row(
                    Modifier.fillMaxWidth().padding(top = Space.xs).forgeRow()
                        .toggleable(p.order != null, enabled = can || p.order != null, role = Role.Checkbox) { on -> picked = if (on) picked + p.heroId.value else picked - p.heroId.value }
                        .heightIn(min = 56.dp).padding(horizontal = 12.dp, vertical = Space.xs).alpha(if (p.blocked == null) 1f else 0.6f).testTag("contract_pick_${p.heroId.value}"),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    // The place in the order, in the column where the wall has its place numbers.
                    Text(p.order?.toString() ?: "–", style = MaterialTheme.typography.titleLarge, color = if (p.order != null) Gold else CreamMuted, textAlign = TextAlign.Center, modifier = Modifier.widthIn(min = 20.dp))
                    Box(Modifier.background(ForgeSlot).border(1.dp, BronzeDeep)) { PixelImage(p.portrait, 44.dp, description = null) }
                    Column(Modifier.weight(1f)) {
                        Text(p.name, style = MaterialTheme.typography.titleSmall)
                        Secondary("${p.sub} · ${p.blade}")
                        p.blocked?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Ember) }
                    }
                }
            }

            SectionHeader("Posture")
            ui.postures.forEach { o ->
                Column(
                    Modifier.fillMaxWidth().padding(top = Space.xs).background(if (o.selected) BronzeContainer else androidx.compose.ui.graphics.Color.Transparent, MaterialTheme.shapes.extraSmall)
                        .border(1.dp, if (o.selected) Bronze else BronzeDeep, MaterialTheme.shapes.extraSmall)
                        .selectable(o.selected, enabled = !busy, role = Role.RadioButton) { posture = o.posture.name }
                        .heightIn(min = 48.dp).padding(horizontal = 12.dp, vertical = Space.xs).testTag("contract_posture_${o.posture.name.lowercase()}"),
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(o.name + if (o.selected) " · chosen" else "", style = MaterialTheme.typography.titleSmall, color = if (o.selected) Gold else Cream)
                    Secondary(o.line)
                }
            }

            Statements("Works together", ui.together, EffectKind.BUFF, "contract_together")
            Statements("Watch out", ui.watchOut, EffectKind.FLAW, "contract_watch")
            SectionHeader("At home")
            Text(ui.atHome, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.testTag("contract_home"))
            SectionHeader("Returns")
            Text(ui.returns, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.testTag("contract_returns"))

            SectionHeader("The contract")
            ui.offer.stages.forEach { st ->
                Text(st.label, style = MaterialTheme.typography.labelLarge, color = Gold, modifier = Modifier.padding(top = Space.xs))
                Text(st.reward, style = MaterialTheme.typography.bodyMedium)
                Secondary("${st.objective} · ${st.danger}")
                EnemyLines(st.enemies)
            }
            Secondary("Bring: ${ui.offer.prep}", Modifier.padding(top = Space.sm))
            Secondary("If it fails: ${ui.offer.failure}")

            ui.sendBlocked?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Ember, modifier = Modifier.padding(top = Space.md).testTag("contract_blocked")) }
            PrimaryActionButton(
                if (ui.planned) "Change the plan" else "Send", { onSend(ui.picks.filter { it.order != null }.sortedBy { it.order }.map { it.heroId }, Posture.valueOf(posture)) },
                Modifier.fillMaxWidth().padding(top = Space.sm).testTag("contract_send"), enabled = !busy && ui.sendBlocked == null, detail = "Leaves at End Day",
            )
            if (ui.planned) SecondaryActionButton("Cancel plan", onCancelPlan, Modifier.fillMaxWidth().padding(top = Space.sm).testTag("contract_cancel"), enabled = !busy, detail = "The fee comes back")
            SecondaryActionButton("Close", onDismiss, Modifier.fillMaxWidth().padding(top = Space.sm).heightIn(min = 48.dp).testTag("sheet_close"))
        }
    }
}

/** A headed list of statements with the sign of its kind; nothing at all when there is nothing to say. */
@Composable
private fun Statements(title: String, lines: List<String>, kind: EffectKind, tag: String) {
    if (lines.isEmpty()) return
    SectionHeader(title)
    Column(Modifier.testTag(tag)) { lines.forEach { Text("${kind.sign} $it", style = MaterialTheme.typography.bodyMedium, color = kind.color) } }
}
