package com.example.blacksmithproject.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.example.blacksmithproject.ui.shopday.Overline
import com.example.blacksmithproject.ui.theme.ForgePanel
import com.example.blacksmithproject.ui.theme.Gold
import com.example.blacksmithproject.ui.theme.Space
import com.tinyblacksmith.core.shopday.LeadKind

/** Where the lead's row goes; null when the thing to act on is on the Shop itself (a request to answer, on the board). */
fun leadActionLabel(kind: LeadKind): String? = when (kind) {
    LeadKind.FIRST_BLADE, LeadKind.FORGE_FOR_REQUEST, LeadKind.FORGE_STOCK, LeadKind.ARM_DEFENDERS, LeadKind.FORGE_FOR_BUYERS -> "Go to the forge"
    LeadKind.CHOOSE_BLESSING -> "See the blessings"
    LeadKind.LIST_STOCK -> "Open storage"
    LeadKind.PRICES_TOO_HIGH -> "Review the cheapest weapon"
    LeadKind.ANSWER_WANT -> "Forge this"
    LeadKind.ANSWER_REQUEST -> "Open the board"
    LeadKind.REVIEW_WALL -> "Open the wall"
    LeadKind.GUILD_RESCUE, LeadKind.GUILD_CHECKPOINT -> "Open the quest board"
    LeadKind.DAY_SETTLED -> null
}

/**
 * The one lead of the day and its reason, the same words as the Tomorrow card of the day before (`Advice.lead`), as one
 * row that goes where the lead is acted on. It leads somewhere, so it is a row with an arrow like the Shop's other doors;
 * the gold plate is kept for the thing that is done (Accept, Forge weapon, End Day). V3: the one ground on the Shop, with
 * no frame round it; the gold edge at its side says it comes first.
 */
@Composable
fun LeadCard(lead: LeadUi, onAct: () -> Unit, modifier: Modifier = Modifier, tag: String = "shop_lead") {
    val label = leadActionLabel(lead.kind)
    var more by rememberSaveable(lead.action) { mutableStateOf(false) }
    val folds = label == null && lead.reason?.contains(". ") == true
    Row(
        modifier.fillMaxWidth().background(ForgePanel, RoundedCornerShape(4.dp)).drawBehind { drawRect(Gold, size = Size(3.dp.toPx(), size.height)) }
            .then(
                if (label != null) Modifier.clickable(onClickLabel = label, role = Role.Button, onClick = onAct)
                else if (folds) Modifier.clickable(onClickLabel = if (more) "Show less" else "Show more", role = Role.Button) { more = !more }.semantics { stateDescription = if (more) "Expanded" else "Collapsed" }
                else Modifier,
            )
            .heightIn(min = 56.dp).padding(horizontal = Space.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(vertical = Space.sm)) {
            // The same words over it as on the Tomorrow card of the evening before: it is that card's advice, a day later.
            Overline(when (lead.kind) {
                LeadKind.DAY_SETTLED -> "Today"
                LeadKind.ANSWER_REQUEST, LeadKind.LIST_STOCK, LeadKind.FORGE_STOCK, LeadKind.PRICES_TOO_HIGH,
                LeadKind.ANSWER_WANT, LeadKind.FORGE_FOR_BUYERS -> "Optional today"
                else -> "Worth doing first"
            }, strong = true)
            Text(lead.action, style = MaterialTheme.typography.titleMedium, modifier = Modifier.testTag(tag))
            // V2: a lead with somewhere to go says its whole reason (it is why the step matters). A settled day has
            // nothing to act on: its first sentence stands, and the rest (the caveats) opens on a tap of the card.
            lead.reason?.let { reason ->
                val cut = reason.indexOf(". ")
                if (label != null || cut < 0 || more) Secondary(reason, Modifier.testTag("${tag}_reason"))
                else Secondary(reason.substring(0, cut + 1), Modifier.testTag("${tag}_reason"))
            }
            // Under the reason, not beside it: "Open the cheapest blade" would leave the lead a few dp.
            if (label != null) Text("$label  ›", style = MaterialTheme.typography.labelLarge, color = Gold, modifier = Modifier.padding(top = Space.xs).testTag("${tag}_action"))
            // The fold is said in words, never only by the tap: the caveats of a settled day are one visible "More" away.
            else if (folds) Text(if (more) "Less  ▴" else "More  ▾", style = MaterialTheme.typography.labelLarge, color = Gold, modifier = Modifier.padding(top = Space.xs).testTag("${tag}_more"))
        }
    }
}
