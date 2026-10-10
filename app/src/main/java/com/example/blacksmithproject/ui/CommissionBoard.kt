package com.example.blacksmithproject.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.unit.dp
import com.example.blacksmithproject.ui.theme.BuffGreen
import com.example.blacksmithproject.ui.theme.Cream
import com.example.blacksmithproject.ui.theme.CreamMuted
import com.example.blacksmithproject.ui.theme.Gold
import com.example.blacksmithproject.ui.theme.Space
import com.tinyblacksmith.core.model.CommissionId
import com.tinyblacksmith.core.model.HeroId
import com.tinyblacksmith.core.model.IdOrder
import com.tinyblacksmith.core.model.WeaponFamilyId

/** The customers who left without a blade of one weapon type. A count of people: never a price, never a promise that they buy. */
@Immutable
data class WantGroupUi(val familyId: WeaponFamilyId, val family: String, val wants: List<WantUi>) {
    val title: String get() = "$family · ${wants.size} ${if (wants.size == 1) "customer" else "customers"}"
    /** How many of them a blade on the shelf already answers. */
    val answered: Int get() = wants.count { it.answered }
}

/**
 * The board behind "Commissions & customers": formal commissions (accepted ones first, the soonest due leading; then
 * offers) and customer wants grouped by weapon type. Two kinds of thing, never mixed; every record of [ShopUi] is here.
 */
@Immutable
data class BoardUi(val commissions: List<RequestUi>, val groups: List<WantGroupUi>) {
    private val wants: Int get() = groups.sumOf { it.wants.size }
    val summary: String
        get() = listOf(
            when (commissions.size) { 0 -> "no commissions"; 1 -> "1 commission"; else -> "${commissions.size} commissions" },
            when (wants) { 0 -> "no customer wants"; 1 -> "1 customer want"; else -> "$wants customer wants" },
        ).joinToString(" · ").replaceFirstChar { it.uppercase() }
    /** The offers still waiting for Accept or Decline, as a count; null when none wait. */
    val pending: String? get() = commissions.count { it.offered }.takeIf { it > 0 }?.let { if (it == 1) "1 awaits your answer" else "$it await your answer" }
}

fun ShopUi.board(): BoardUi = BoardUi(
    commissions = requests.sortedWith(compareBy<RequestUi> { it.offered }.thenBy { it.daysLeft }.thenBy(IdOrder.numeric) { it.id.value }),
    groups = wants.groupBy { it.familyId }.map { (id, list) -> WantGroupUi(id, list.first().family, list) },
)

/** The board as a sheet over the Shop or the Forge: what is under it (the draft, the scroll position) is left as it was. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommissionBoardSheet(
    board: BoardUi, slots: Int, busy: Boolean, chosen: CommissionId?,
    onOpenHero: (HeroId) -> Unit, onAnswer: (CommissionId, Boolean) -> Unit, onForgeThis: (CommissionId) -> Unit, onForgeWant: (WeaponFamilyId) -> Unit, onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        dragHandle = { BottomSheetDefaults.DragHandle(width = 48.dp) },
        modifier = Modifier.semantics { testTagsAsResourceId = true }.testTag("board_sheet"),
    ) { CommissionBoard(board, slots, busy, chosen, onOpenHero, onAnswer, onForgeThis, onForgeWant) }
}

@Composable
fun CommissionBoard(
    board: BoardUi, slots: Int, busy: Boolean, chosen: CommissionId?,
    onOpenHero: (HeroId) -> Unit, onAnswer: (CommissionId, Boolean) -> Unit, onForgeThis: (CommissionId) -> Unit, onForgeWant: (WeaponFamilyId) -> Unit, modifier: Modifier = Modifier,
) {
    // One row open at a time: the commission the Forge is working toward to begin with.
    var open by rememberSaveable { mutableStateOf(chosen?.let { "c:${it.value}" }) }
    LazyColumn(modifier.fillMaxWidth().navigationBarsPadding().testTag("board_list"), contentPadding = PaddingValues(start = Space.md, end = Space.md, bottom = Space.lg)) {
        item(key = "commissions") {
            Column {
                Text("Commissions", style = MaterialTheme.typography.titleLarge, color = Gold, modifier = Modifier.semantics { heading() }.testTag("board_commissions"))
                Secondary(if (board.commissions.isEmpty()) "None open. A hero may ask for a blade at End Day; up to $slots at a time." else "A paid order with a deadline. Accept it, then have a blade that fits in the shop at End Day.")
            }
        }
        items(board.commissions, key = { "c:${it.id.value}" }) { r ->
            val id = "c:${r.id.value}"
            if (open == id) RequestCard(r, busy, onOpenHero, onAnswer, onForgeThis)
            else BoardRow("${r.asks} · ${r.reward} gold", "${r.buyer.name} · ${r.due}", r.status, r.ready, "board_commission_${r.id.value}") { open = id }
        }
        item(key = "wants") {
            Column {
                Text("Customer wants", style = MaterialTheme.typography.titleLarge, color = Gold, modifier = Modifier.padding(top = Space.lg).semantics { heading() }.testTag("board_wants"))
                Secondary(if (board.groups.isEmpty()) "Nobody has left without the blade they came for." else "Heroes who left without a blade. No order and no promise: they may buy one that suits them.")
            }
        }
        board.groups.forEach { g ->
            val id = "w:${g.familyId.value}"
            item(key = id) {
                BoardRow(g.title, if (g.answered == g.wants.size) "The shelf answers them" else if (g.answered > 0) "The shelf answers ${g.answered}" else "Nothing on the shelf for them", if (open == id) "Hide" else "Show", g.answered == g.wants.size, "board_group_${g.familyId.value}") { open = if (open == id) null else id }
            }
            if (open == id) items(g.wants, key = { "want_${it.heroId.value}" }) { WantRow(it, onOpenHero, onForgeWant) }
        }
    }
}

/** One collapsed row of the board: a title, one line under it, and its status at the end. */
@Composable
private fun BoardRow(title: String, detail: String, status: String, good: Boolean, tag: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = Space.xs).forgeRow().clickable(role = Role.Button, onClick = onClick).heightIn(min = 56.dp).padding(horizontal = Space.md, vertical = Space.sm).testTag(tag)
            .semantics(mergeDescendants = true) { contentDescription = "$title. $detail. $status." },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(end = Space.sm)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = Cream)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = CreamMuted)
        }
        Text(status, style = MaterialTheme.typography.labelMedium, color = if (good) BuffGreen else Gold)
    }
}
