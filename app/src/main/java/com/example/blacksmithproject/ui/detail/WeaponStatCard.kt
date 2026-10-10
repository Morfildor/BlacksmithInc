package com.example.blacksmithproject.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.blacksmithproject.ui.BladeRulesUi
import com.example.blacksmithproject.ui.EffectKind
import com.example.blacksmithproject.ui.EffectRow
import com.example.blacksmithproject.ui.FramedPanel
import com.example.blacksmithproject.ui.Labels
import com.example.blacksmithproject.ui.PixelImage
import com.example.blacksmithproject.ui.Secondary
import com.example.blacksmithproject.ui.SectionHeader
import com.example.blacksmithproject.ui.Sprites
import com.example.blacksmithproject.ui.StatBar
import com.example.blacksmithproject.ui.StatDelta
import com.example.blacksmithproject.ui.StatRow
import com.example.blacksmithproject.ui.theme.Bronze
import com.example.blacksmithproject.ui.theme.BronzeDeep
import com.example.blacksmithproject.ui.theme.CreamMuted
import com.example.blacksmithproject.ui.theme.ForgeSlot
import com.example.blacksmithproject.ui.theme.Gold
import com.example.blacksmithproject.ui.theme.Space
import com.tinyblacksmith.core.content.Element
import com.tinyblacksmith.core.model.Rarity

/** The colour a blade's name is written in. Never the only sign of rarity: the word stands under the name. All 8:1 or more on a panel. */
fun rarityColor(r: Rarity): Color = when (r) {
    Rarity.COMMON -> Color(0xFFC9CED6)
    Rarity.UNCOMMON -> Color(0xFF7FD68A)
    Rarity.RARE -> Color(0xFF7DB8FF)
    Rarity.EPIC -> Color(0xFFCE9BFF)
    Rarity.LEGENDARY -> Color(0xFFFFB347)
}

/** The colour an element's name is written in; 7.5:1 or more on a panel. */
fun elementColor(e: Element): Color = when (e) {
    Element.FIRE -> Color(0xFFFF8A5C)
    Element.FROST -> Color(0xFF8FD0FF)
    Element.STORM -> Color(0xFFC9B6FF)
    Element.GRAVE -> Color(0xFFB9C2A8)
    Element.VERDANT -> Color(0xFF8FDC8C)
    Element.SUN -> Color(0xFFFFD76A)
}

/**
 * A blade in one wrapping line, for a row of the shelf or the storeroom: the rarity in words, then the same [Stat]s the
 * card shows as "Power 24", "Quality 52", then each buff ("+", green) and each flaw ("−", red) by name. Numbers, not
 * bars: a row is one line of reading.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WeaponStatLine(rarity: Rarity, stats: List<Stat>, buffs: List<String>, flaws: List<String>, modifier: Modifier = Modifier) {
    FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
        Text(Labels.rarity(rarity), style = MaterialTheme.typography.bodySmall, color = CreamMuted)
        stats.forEach { s ->
            Row {
                Text("${s.label} ", style = MaterialTheme.typography.bodySmall, color = CreamMuted)
                Text("${s.value}", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
            }
        }
        buffs.forEach { Text("${EffectKind.BUFF.sign} $it", style = MaterialTheme.typography.bodySmall, color = EffectKind.BUFF.color) }
        flaws.forEach { Text("${EffectKind.FLAW.sign} $it", style = MaterialTheme.typography.bodySmall, color = EffectKind.FLAW.color) }
    }
}

private class Cell(val label: String, val value: String, val color: Color? = null, val delta: StatDelta? = null)

/**
 * A blade as an item card: the sprite in its slot, the name in the rarity's colour over the rarity and kind in words,
 * the numbers core holds (bars where core bounds them), then what helps ("+", green) and what hurts ("−", red).
 * Every number is a field of [detail]; a "was → now" appears only where [Stat.was] carries the visit snapshot's value.
 * Nothing in it can be tapped. [overSprite] draws over the slot (the forge's burst).
 */
@Composable
fun WeaponStatCard(
    detail: ItemDetail,
    modifier: Modifier = Modifier,
    title: String? = null,
    overSprite: @Composable BoxScope.() -> Unit = {},
) {
    FramedPanel(title, modifier.fillMaxWidth()) { WeaponStatBody(detail, overSprite) }
}

/**
 * What [WeaponStatCard] says of a blade, without the plate around it: for a caller that frames or scrolls it itself.
 * [detailAlpha] fades everything under the sprite, name and rarity (the forge's reveal); the layout never changes with it.
 */
@Composable
fun ColumnScope.WeaponStatBody(detail: ItemDetail, overSprite: @Composable BoxScope.() -> Unit = {}, detailAlpha: () -> Float = { 1f }) {
    Row(Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.md)) {
        Box(
            Modifier.size(88.dp).background(ForgeSlot).border(1.dp, Bronze).drawBehind {
                if (detail.signature) drawCircle(Sprites.signatureRing, radius = size.minDimension / 2 - 4.dp.toPx(), style = Stroke(2.dp.toPx()))
            },
            contentAlignment = Alignment.Center,
        ) {
            PixelImage(detail.sprite, 72.dp, description = null)
            PixelImage(detail.badge, 24.dp, description = null, modifier = Modifier.align(Alignment.TopStart).padding(2.dp))
            overSprite()
        }
        Column(Modifier.weight(1f)) {
            Text(detail.name, style = MaterialTheme.typography.titleLarge, color = rarityColor(detail.rarity), modifier = Modifier.semantics { heading() })
            Text("${Labels.rarity(detail.rarity)} · ${detail.kind}", style = MaterialTheme.typography.bodyMedium, color = CreamMuted)
            detail.title?.let { Text("\"$it\"", style = MaterialTheme.typography.bodyMedium, fontStyle = FontStyle.Italic) }
            if (detail.signature) Text("Signature work", style = MaterialTheme.typography.labelMedium, color = Gold)
        }
    }
    Column(Modifier.graphicsLayer { alpha = detailAlpha() }) { WeaponStatDetails(detail) }
}

@Composable
private fun ColumnScope.WeaponStatDetails(detail: ItemDetail) {
    HorizontalDivider(Modifier.padding(vertical = Space.sm), color = BronzeDeep)

    // The numbers without a ceiling, two to a line while the text is small enough; then one bar for each that has one.
    val cells =detail.stats.filter { it.max == null }.map { s ->
        Cell(s.label, s.word?.let { "$it (${s.value})" } ?: s.value.toString(), delta = s.was?.let { StatDelta(it.toString(), if (s.value > it) EffectKind.BUFF else EffectKind.FLAW) })
    } + Cell("Element", detail.element?.let { it.name.lowercase().replaceFirstChar { c -> c.uppercase() } } ?: "None", detail.element?.let { elementColor(it) }) +
        listOfNotNull(detail.stock?.let { Cell("Value", "${it.suggestedPrice} gold", Gold) })
    // On a 360 dp phone half a line cannot hold "Renown" and "Unsung (1)": the label broke mid-word. One to a line there.
    if (LocalDensity.current.fontScale > 1f || LocalConfiguration.current.screenWidthDp < 400) cells.forEach { StatRow(it.label, it.value, valueColor = it.color, delta = it.delta) }
    else cells.chunked(2).forEach { pair ->
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.md)) {
            pair.forEach { StatRow(it.label, it.value, Modifier.weight(1f), it.color, it.delta) }
            if (pair.size == 1) Box(Modifier.weight(1f))
        }
    }
    detail.stats.filter { it.max != null }.forEach { s -> StatBar(s.label, s.value, s.max!!, Modifier.padding(top = Space.xs), word = s.word, was = s.was) }

    SectionHeader("Buffs")
    if (detail.affixes.isEmpty()) Secondary("No buffs.")
    detail.affixes.forEach { EffectRow(EffectKind.BUFF, it.name, it.description) }
    // Asleep, so not a buff: its own gold "◆" row, in core's words, after whatever is awake.
    detail.dormant?.let { EffectRow(EffectKind.NEUTRAL, it, "", Modifier.testTag("card_dormant")) }
    if (detail.flaws.isNotEmpty()) {
        SectionHeader("Flaws")
        detail.flaws.forEach { EffectRow(EffectKind.FLAW, it.name, it.description) }
    }
    detail.rules?.let { FightRules(it) }
}

/**
 * What the blade does in a fight (guild runs): how its family strikes, then each rule in the catalog's own words with the
 * part of the blade it comes from, then the rules it has only on the town's wall.
 */
@Composable
private fun ColumnScope.FightRules(rules: BladeRulesUi) {
    Column(Modifier.fillMaxWidth().testTag("card_fight")) {
        SectionHeader("In a fight")
        // Where these rules are resolved: a townsperson's own outing is still settled by the blade's power alone.
        Secondary("On a guild contract, and for whoever stands on the wall.")
        rules.pattern?.let { Secondary(it) }
        if (rules.road.isEmpty() && rules.homeOnly.isEmpty()) Secondary("No rule of its own beyond the strike.")
        rules.road.forEach { EffectRow(EffectKind.NEUTRAL, "${it.name} · ${it.source}", it.description) }
        if (rules.homeOnly.isNotEmpty()) {
            Text("Only when defending the town", style = MaterialTheme.typography.labelLarge, color = CreamMuted, modifier = Modifier.padding(top = Space.xs))
            rules.homeOnly.forEach { EffectRow(EffectKind.NEUTRAL, "${it.name} · ${it.source}", it.description) }
        }
    }
}
