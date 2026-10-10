package com.example.blacksmithproject.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import com.example.blacksmithproject.ui.theme.Gold
import com.example.blacksmithproject.ui.theme.Space
import com.tinyblacksmith.core.shopday.LeadKind

/** Where the lead's button goes; null when the thing to act on is the next block of the Shop itself (a request to answer). */
fun leadActionLabel(kind: LeadKind): String? = when (kind) {
    LeadKind.FIRST_BLADE, LeadKind.FORGE_FOR_REQUEST, LeadKind.FORGE_STOCK, LeadKind.ARM_DEFENDERS, LeadKind.FORGE_FOR_BUYERS -> "Go to the forge"
    LeadKind.CHOOSE_BLESSING -> "See the blessings"
    LeadKind.LIST_STOCK -> "Open storage"
    LeadKind.PRICES_TOO_HIGH -> "Open the cheapest blade"
    LeadKind.ANSWER_REQUEST, LeadKind.ANSWER_WANT -> null
}

/**
 * The one lead of the day and its reason, the same words as the Tomorrow card of the day before (`Advice.lead`), with
 * one button that goes where the lead is acted on.
 */
@Composable
fun LeadCard(lead: LeadUi, onAct: () -> Unit, modifier: Modifier = Modifier) {
    FramedPanel(modifier = modifier.fillMaxWidth()) {
        Text("WORTH DOING FIRST", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = Gold)
        Text(lead.action, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = Space.xs).semantics { heading() }.testTag("shop_lead"))
        lead.reason?.let { Text(it, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = Space.xs).testTag("shop_lead_reason")) }
        leadActionLabel(lead.kind)?.let { label ->
            PrimaryActionButton(label, onAct, Modifier.fillMaxWidth().padding(top = Space.sm).testTag("shop_lead_action"))
        }
    }
}
