package com.example.blacksmithproject.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.example.blacksmithproject.ui.shopday.Overline
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
    LeadKind.GUILD_RESCUE, LeadKind.GUILD_CHECKPOINT -> "Open guild contracts"
    LeadKind.DAY_SETTLED -> null
}

/**
 * The one lead of the day and its reason, the same words as the Tomorrow card of the day before (`Advice.lead`), as one
 * row that goes where the lead is acted on. It leads somewhere, so it is a row with an arrow like the Shop's other doors;
 * the gold plate is kept for the thing that is done (Accept, Forge weapon, End Day).
 */
@Composable
fun LeadCard(lead: LeadUi, onAct: () -> Unit, modifier: Modifier = Modifier, tag: String = "shop_lead") {
    val label = leadActionLabel(lead.kind)
    Row(
        modifier.fillMaxWidth().forgeRow().drawBehind { drawRect(Gold, size = Size(3.dp.toPx(), size.height)) }
            .then(if (label != null) Modifier.clickable(onClickLabel = label, role = Role.Button, onClick = onAct) else Modifier)
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
            lead.reason?.let { Secondary(it, Modifier.testTag("${tag}_reason")) }
            // Under the reason, not beside it: "Open the cheapest blade" would leave the lead a few dp.
            if (label != null) Text("$label  ›", style = MaterialTheme.typography.labelLarge, color = Gold, modifier = Modifier.padding(top = Space.xs).testTag("${tag}_action"))
        }
    }
}
