package com.example.blacksmithproject.ui.shopday

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.blacksmithproject.ui.EffectKind
import com.example.blacksmithproject.ui.Sprites
import com.example.blacksmithproject.ui.StatDelta
import com.example.blacksmithproject.ui.StatRow
import com.example.blacksmithproject.ui.theme.Gold
import com.example.blacksmithproject.ui.theme.SceneDeep
import com.example.blacksmithproject.ui.theme.SceneInk
import com.example.blacksmithproject.ui.theme.SceneWood1
import com.example.blacksmithproject.ui.theme.Space
import com.tinyblacksmith.core.model.WeaponSnapshot

/** The small capital line that says which part of the day a card belongs to. */
@Composable
internal fun Overline(text: String, modifier: Modifier = Modifier, strong: Boolean = false) {
    Text(
        text.uppercase(), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold,
        color = if (strong) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant, modifier = modifier,
    )
}

@Composable
internal fun CardTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.titleLarge, color = Gold, modifier = modifier.semantics { heading() })
}

/**
 * Label and value rows, the value right-aligned; the total is set off by a rule. Each row is one node for a screen
 * reader ("Listed price, 108 gold"). The rows come straight from the Sale record or the day's ledger.
 */
@Composable
fun ReceiptRows(rows: List<ReceiptRow>, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().testTag("shopday_receipt")) {
        rows.forEach { row ->
            if (row.total && rows.size > 1) HorizontalDivider(Modifier.padding(vertical = 2.dp), color = MaterialTheme.colorScheme.outline)
            StatRow(row.label, row.value, valueColor = if (row.total) Gold else null)
        }
    }
}

/**
 * What the day has earned up to this card, with what it stood at before: "96 → 228 gold ▲". Shown whole at once; the
 * numbers are sums of the day's Sale records.
 */
@Composable
internal fun EarnedToday(before: Int, after: Int, modifier: Modifier = Modifier) {
    StatRow("Earned today", "$after gold", modifier.testTag("shopday_earned"), delta = StatDelta("$before", EffectKind.BUFF))
}

/** A blade as a small ink tile; decorative here, the text beside it names it. */
@Composable
internal fun BladeTile(blade: WeaponSnapshot, modifier: Modifier = Modifier) {
    Box(modifier.background(SceneDeep).border(1.dp, SceneWood1).padding(2.dp).clearAndSetSemantics {}) {
        PixelSprite(Sprites.weapon(blade.familyId, blade.coreId, blade.element, blade.rarity, signature = blade.signatureId != null), wholeScale(56, 40.dp))
    }
}

/** A face and a name that opens the hero's sheet; at least 48 dp tall. A visitor who is not a hero is not clickable. */
@Composable
internal fun PersonChip(face: FaceUi, onOpenHero: (FaceUi) -> Unit, modifier: Modifier = Modifier, note: String? = null) {
    Row(
        modifier.heightIn(min = 48.dp)
            .then(if (face.heroId != null) Modifier.clickable(onClickLabel = "Open ${face.name}", role = Role.Button) { onOpenHero(face) } else Modifier)
            .padding(end = Space.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PortraitTile(face, scale = wholeScale(64, 44.dp), modifier = Modifier.clearAndSetSemantics {})
        Spacer(Modifier.width(Space.sm))
        Column {
            Text(face.name, style = MaterialTheme.typography.titleSmall)
            if (note != null) Text(note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** A blade and its name that opens the blade's sheet; at least 48 dp tall. */
@Composable
internal fun BladeChip(blade: WeaponSnapshot, onOpenBlade: (WeaponSnapshot) -> Unit, modifier: Modifier = Modifier, title: String = blade.name, note: String? = null) {
    Row(
        modifier.heightIn(min = 48.dp).clickable(onClickLabel = "Open ${blade.name}", role = Role.Button) { onOpenBlade(blade) }.padding(end = Space.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BladeTile(blade)
        Spacer(Modifier.width(Space.sm))
        Column {
            Text(title, style = MaterialTheme.typography.titleSmall)
            if (!note.isNullOrEmpty()) Text(note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/**
 * One customer's visit, whole on its first frame, in one order for every outcome: the result as a badge (a gold band with
 * the coin it brought, or a red-edged "No sale": the word always, the colour with it), one headline, the reason under it,
 * the recognition as an aside, then the receipt as separate rows and the blades they weighed. Who they are is on the name
 * plate of the scene above. Every number is the visit's own record: the day is already saved, and nothing here adds to the purse.
 */
@Composable
fun VisitCard(visit: VisitUi, onOpenBlade: (WeaponSnapshot) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Space.sm)) {
        if (visit.sold) {
            Column(verticalArrangement = Arrangement.spacedBy(Space.xs)) {
                Row(
                    Modifier.fillMaxWidth().background(Gold, MaterialTheme.shapes.extraSmall).padding(horizontal = Space.sm, vertical = Space.xs).semantics(mergeDescendants = true) {},
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.sm),
                ) {
                    Text(visit.banner.uppercase(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = SceneInk, modifier = Modifier.weight(1f).testTag("shopday_outcome_chip"))
                    Text("+${visit.coin} gold", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = SceneInk, modifier = Modifier.testTag("shopday_coin"))
                }
                // Straight under the band, so the rise is on screen without a scroll.
                if (visit.coin > 0) EarnedToday(visit.earnedBefore, visit.earnedAfter)
            }
        } else {
            Text(
                visit.banner.uppercase(), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = EffectKind.FLAW.color,
                modifier = Modifier.background(EffectKind.FLAW.color.copy(alpha = 0.14f), MaterialTheme.shapes.extraSmall).border(1.dp, EffectKind.FLAW.color, MaterialTheme.shapes.extraSmall)
                    .padding(horizontal = Space.sm, vertical = Space.xs).testTag("shopday_outcome_chip"),
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(Space.xs)) {
            if (visit.sold) CardTitle(visit.outcome, Modifier.testTag("shopday_outcome"))
            else Text(visit.outcome, style = MaterialTheme.typography.titleLarge, modifier = Modifier.testTag("shopday_outcome").semantics { heading() })
            visit.decision?.let { Text(it, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.testTag("shopday_decision")) }
            visit.recognition?.let { Text(it, style = MaterialTheme.typography.bodyMedium, fontStyle = FontStyle.Italic, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.testTag("shopday_recognition")) }
        }
        // The blade heads its own receipt, the way a bill names what was bought.
        if (visit.purchased != null || visit.receipt.isNotEmpty()) Column {
            visit.purchased?.let { Text(it.name, style = MaterialTheme.typography.titleSmall) }
            if (visit.receipt.isNotEmpty()) ReceiptRows(visit.receipt)
        }
        if (visit.looked.isNotEmpty()) Column {
            Overline("Looked at")
            visit.looked.forEach { item ->
                if (item.blade != null) BladeChip(item.blade, onOpenBlade, Modifier.fillMaxWidth(), title = item.title, note = item.factors)
                else Text(item.title + if (item.factors.isEmpty()) "" else ": ${item.factors}", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
