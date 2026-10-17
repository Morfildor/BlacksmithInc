package com.example.blacksmithproject.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import com.example.blacksmithproject.PartyDraft
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.unit.dp
import com.example.blacksmithproject.ui.theme.Bronze
import com.example.blacksmithproject.ui.theme.BronzeContainer
import com.example.blacksmithproject.ui.theme.BronzeDeep
import com.example.blacksmithproject.ui.theme.Cream
import com.example.blacksmithproject.ui.theme.Ember
import com.example.blacksmithproject.ui.theme.ForgeSlot
import com.example.blacksmithproject.ui.theme.Gold
import com.example.blacksmithproject.ui.theme.Space
import com.tinyblacksmith.core.combat.Posture
import com.tinyblacksmith.core.engine.GameEngine
import com.tinyblacksmith.core.model.GameState
import com.tinyblacksmith.core.model.HeroId

/** One authoritative party draft, with scrollable planning details and a persistent confirmation action. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ContractSheet(
    state: GameState, engine: GameEngine, draft: PartyDraft, busy: Boolean,
    onToggle: (HeroId) -> Unit, onMove: (HeroId, Int) -> Unit, onPosture: (Posture) -> Unit,
    onOpenHero: (HeroId) -> Unit, onReviewEquipment: () -> Unit,
    onSend: () -> Unit, onCancelPlan: () -> Unit, onDismiss: () -> Unit, modifier: Modifier = Modifier,
) {
    val ui = remember(state, draft) { engine.contractUi(state, draft.offerId, draft.members, draft.posture) }
    val changed = draft.equipmentChanges(state)
    val stale = draft.runId != state.runId.value || draft.day != state.day
    val blocked = when {
        stale -> "This draft is from another morning. Close it and choose a quest again."
        ui == null -> "This quest is no longer available. Close this draft and choose another."
        ui.sendBlocked != null -> ui.sendBlocked
        changed.isNotEmpty() -> "Equipment changed. Review the selected members before planning departure."
        else -> null
    }
    val factHeight = LocalConfiguration.current.screenHeightDp.dp * 0.36f
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        dragHandle = { BottomSheetDefaults.DragHandle(width = 48.dp) },
        modifier = modifier.semantics { testTagsAsResourceId = true }.testTag("contract_sheet"),
    ) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(0.95f).navigationBarsPadding().padding(horizontal = Space.md)) {
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).testTag("contract_details")) {
                Text(ui?.offer?.title ?: "Quest unavailable", style = MaterialTheme.typography.titleLarge, color = Cream, modifier = Modifier.semantics { heading() })
                if (ui != null) {
                    SectionHeader("Selected party")
                    Secondary(partyIntro(ui.partyMax, draft.members.isEmpty()))
                    ui.picks.filter { it.order != null }.sortedBy { it.order }.forEach { p ->
                        Column(Modifier.fillMaxWidth().padding(top = Space.sm).forgeRow().padding(Space.sm).testTag("contract_selected_${p.heroId.value}")) {
                            Text("${p.order}. ${p.name}", style = MaterialTheme.typography.titleSmall)
                            MemberLines(p)
                            p.blocked?.let { Text(it, color = Ember, style = MaterialTheme.typography.bodySmall) }
                            if (p.heroId in changed) Text("Equipment changed: ${p.blade}", color = Ember, style = MaterialTheme.typography.bodySmall)
                            Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                                SecondaryActionButton("↑", { onMove(p.heroId, -1) }, Modifier.weight(1f).heightIn(min = 48.dp).semantics { contentDescription = "Move ${p.name} up" }.testTag("contract_up_${p.heroId.value}"), enabled = !busy && p.order != 1)
                                SecondaryActionButton("↓", { onMove(p.heroId, 1) }, Modifier.weight(1f).heightIn(min = 48.dp).semantics { contentDescription = "Move ${p.name} down" }.testTag("contract_down_${p.heroId.value}"), enabled = !busy && p.order != draft.members.size)
                            }
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                                InlineActionButton("Equipment and details", { onOpenHero(p.heroId) }, Modifier.heightIn(min = 48.dp).testTag("contract_equipment_${p.heroId.value}"), enabled = !busy)
                                InlineActionButton("Remove", { onToggle(p.heroId) }, Modifier.heightIn(min = 48.dp).testTag("contract_remove_${p.heroId.value}"), enabled = !busy)
                            }
                        }
                    }
                    if (changed.isNotEmpty()) SecondaryActionButton("Use current equipment", onReviewEquipment, Modifier.fillMaxWidth().testTag("contract_review_equipment"), enabled = !busy)
                    SectionHeader("Roster")
                    // Members as rows, not plates: face, name, class and health, the weapon; why one cannot go stays in sight.
                    ui.picks.filter { it.order == null }.forEachIndexed { i, p ->
                        if (i > 0) RowRule()
                        Column(Modifier.fillMaxWidth().padding(vertical = Space.xs)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                                if (p.portrait != 0) Box(Modifier.background(ForgeSlot).border(1.dp, BronzeDeep)) { PixelImage(p.portrait, 44.dp, description = null) }
                                Column(Modifier.weight(1f)) {
                                    Text(p.name, style = MaterialTheme.typography.titleSmall)
                                    MemberLines(p)
                                }
                            }
                            p.blocked?.let { Text(it, color = Ember, style = MaterialTheme.typography.bodySmall) }
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                                InlineActionButton("Add to party", { onToggle(p.heroId) }, Modifier.heightIn(min = 48.dp).testTag("contract_pick_${p.heroId.value}"), enabled = !busy && p.blocked == null && draft.members.size < ui.partyMax)
                                InlineActionButton("Equipment and details", { onOpenHero(p.heroId) }, Modifier.heightIn(min = 48.dp).testTag("contract_equipment_${p.heroId.value}"), enabled = !busy)
                            }
                        }
                    }
                    SectionHeader("Retreat rule")
                    ui.postures.forEach { o ->
                        Column(
                            Modifier.fillMaxWidth().padding(top = Space.xs).background(if (o.selected) BronzeContainer else androidx.compose.ui.graphics.Color.Transparent, MaterialTheme.shapes.extraSmall)
                                .border(1.dp, if (o.selected) Bronze else BronzeDeep, MaterialTheme.shapes.extraSmall)
                                .selectable(o.selected, enabled = !busy, role = Role.RadioButton) { onPosture(o.posture) }
                                .heightIn(min = 48.dp).padding(Space.sm).testTag("contract_posture_${o.posture.name.lowercase()}"),
                        ) {
                            Text(o.name + if (o.selected) " · chosen" else "", style = MaterialTheme.typography.titleSmall, color = if (o.selected) Gold else Cream)
                            Secondary(o.line)
                        }
                    }
                    Disclosure("How retreat works", key = "contract_retreat_note", summary = "When the rule is checked and what a retreat costs") {
                        Secondary(ui.retreatNote)
                    }
                    Statements("Works together", ui.together, EffectKind.BUFF, "contract_together")
                    Statements("Watch out", ui.watchOut, EffectKind.FLAW, "contract_watch")
                    SectionHeader("At home")
                    Text(ui.atHome, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.testTag("contract_home"))
                    SectionHeader("The quest")
                    Text(ui.offer.description, style = MaterialTheme.typography.bodyMedium)
                    ui.offer.why?.let { Secondary(it) }
                    ui.offer.modifier?.let { Text(it, modifier = Modifier.testTag("contract_modifier")) }
                    ui.offer.stake?.let { Text(it, color = Ember, modifier = Modifier.testTag("contract_stake")) }
                    ui.offer.stages.forEach { StageLines(it, Modifier.padding(top = Space.sm)) }
                    // What to bring is a condition and failing a cost: each with its sign, as on the quest's card.
                    Box(Modifier.padding(top = Space.sm)) {
                        TipSectionBlock(TipSection(null, listOf(TipLine("Bring: ${ui.offer.prep}", TipTone.CONDITION), TipLine("If it fails: ${ui.offer.failure}", TipTone.DRAWBACK))))
                    }
                    Secondary(ui.offer.expiry)
                    Secondary(ui.offer.timingNote)
                    Secondary(ui.offer.fee)
                    if (ui.planned) SecondaryActionButton("Cancel the plan", onCancelPlan, Modifier.fillMaxWidth().padding(top = Space.sm).testTag("contract_cancel"), enabled = !busy, detail = "The fee comes back")
                }
                SecondaryActionButton("Close", onDismiss, Modifier.fillMaxWidth().padding(vertical = Space.sm).testTag("sheet_close"))
            }
            HorizontalDivider(color = BronzeDeep)
            // At extreme text sizes the facts scroll independently, while the confirmation button stays reachable.
            Column(Modifier.fillMaxWidth().heightIn(max = factHeight).verticalScroll(rememberScrollState()).padding(top = Space.xs).testTag("contract_confirmation")) {
                // What planning commits to, in one line: who goes, when they leave and are back, the fee. A missed siege is its own warning.
                ui?.let {
                    Box(Modifier.fillMaxWidth().testTag("contract_party_summary")) { FactLine(
                        listOf(
                            Fact("${draft.members.size} of ${it.partyMax}", "Party"), Fact("End Day today", "Leaves"), Fact("day ${it.offer.returnDay}", "Back"),
                            if (it.offer.feeGold > 0) Fact("${it.offer.feeGold} gold", "Fee") else Fact("No fee"),
                        ),
                        Modifier.fillMaxWidth().testTag("contract_returns"), emphasis = true,
                    ) }
                    it.offer.missesSiege?.let { warning -> FactLine(listOf(Fact(warning, tone = FactTone.WARN)), Modifier.fillMaxWidth().padding(top = 2.dp).testTag("contract_siege_warning")) }
                }
                blocked?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Ember, modifier = Modifier.testTag("contract_blocked")) }
            }
            PrimaryActionButton(
                if (ui?.planned == true) "Change the plan" else "Plan departure", onSend,
                Modifier.fillMaxWidth().padding(top = Space.xs, bottom = Space.sm).testTag("contract_send"), enabled = !busy && blocked == null,
            )
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

/** A member's class, level and health in one line, and the weapon they would carry in the next. */
@Composable
private fun MemberLines(p: PickUi) {
    Secondary(listOf(p.sub, p.health).filter { it.isNotEmpty() }.joinToString(" · "))
    Text(p.blade, style = MaterialTheme.typography.bodySmall, color = Cream)
}

/** One stage of a quest as separate rows: when and what it secures, then the objective, the reward and the risk. */
@Composable
private fun StageLines(st: StageUi, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(st.label, style = MaterialTheme.typography.titleSmall, color = Cream)
        Text("Objective: ${st.objective}.", style = MaterialTheme.typography.bodyMedium)
        Text("Reward: ${st.reward}.", style = MaterialTheme.typography.bodyMedium)
        Secondary(st.danger)
        EnemyLines(st.enemies)
    }
}
