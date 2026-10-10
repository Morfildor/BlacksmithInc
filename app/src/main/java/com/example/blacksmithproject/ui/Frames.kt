package com.example.blacksmithproject.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.blacksmithproject.ui.theme.Bronze
import com.example.blacksmithproject.ui.theme.BronzeDeep
import com.example.blacksmithproject.ui.theme.BuffGreen
import com.example.blacksmithproject.ui.theme.Cream
import com.example.blacksmithproject.ui.theme.CreamMuted
import com.example.blacksmithproject.ui.theme.FlawRed
import com.example.blacksmithproject.ui.theme.ForgePanel
import com.example.blacksmithproject.ui.theme.ForgeSlot
import com.example.blacksmithproject.ui.theme.Gold
import com.example.blacksmithproject.ui.theme.GoldBright
import com.example.blacksmithproject.ui.theme.GoldDeep
import com.example.blacksmithproject.ui.theme.Space

/** What a line says about a blade or a number: it helps, it hurts, or it only tells. Never colour alone: each has its own sign. */
enum class EffectKind(val color: Color, val sign: String) {
    BUFF(BuffGreen, "+"), FLAW(FlawRed, "−"), NEUTRAL(Gold, "◆")
}

/** The earlier value of a number that has changed since: "24 → 31". [kind] says whether the change helps (▲) or hurts (▼). */
@Immutable
data class StatDelta(val was: String, val kind: EffectKind)

private fun DrawScope.diamond(center: Offset, r: Float, color: Color) {
    drawPath(Path().apply { moveTo(center.x, center.y - r); lineTo(center.x + r, center.y); lineTo(center.x, center.y + r); lineTo(center.x - r, center.y); close() }, color)
}

/** The plate itself: ground, bronze border, an inner hairline, and a gold bracket and stud in each corner. Drawn, not painted: no image. */
fun Modifier.forgeFrame(ground: Color = ForgePanel): Modifier = drawBehind {
    val edge = 1.5.dp.toPx()
    val inset = 4.dp.toPx()
    val arm = 10.dp.toPx()
    drawRect(ground)
    drawRect(Bronze, Offset(edge / 2, edge / 2), Size(size.width - edge, size.height - edge), style = Stroke(edge))
    drawRect(BronzeDeep, Offset(inset, inset), Size(size.width - 2 * inset, size.height - 2 * inset), style = Stroke(1.dp.toPx()))
    for (x in listOf(0f, size.width)) for (y in listOf(0f, size.height)) {
        val sx = if (x == 0f) 1f else -1f
        val sy = if (y == 0f) 1f else -1f
        drawLine(Gold, Offset(x, y + sy * edge), Offset(x + sx * arm, y + sy * edge), strokeWidth = 2 * edge)
        drawLine(Gold, Offset(x + sx * edge, y), Offset(x + sx * edge, y + sy * arm), strokeWidth = 2 * edge)
        diamond(Offset(x + sx * inset, y + sy * inset), 2.5.dp.toPx(), Gold)
    }
}

/**
 * The dark framed plate every part of the game sits on. [title] is the plate's heading, centred in gold over a rule;
 * leave it null when the content brings its own heading.
 */
@Composable
fun FramedPanel(
    title: String? = null,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(Space.md),
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier.forgeFrame().padding(contentPadding)) {
        CompositionLocalProvider(LocalContentColor provides Cream) {
            if (title != null) {
                Text(
                    title, style = MaterialTheme.typography.titleLarge, color = Gold, textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(bottom = Space.sm).semantics { heading() }.drawBehind {
                        val y = size.height - 1.dp.toPx()
                        drawLine(BronzeDeep, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx())
                        diamond(Offset(size.width / 2, y), 3.dp.toPx(), Gold)
                    }.padding(bottom = Space.sm),
                )
            }
            content()
        }
    }
}

/** The one way forward on a screen: a wide gold plate with a lit top edge and a dark bevel under it. At least 52 dp tall. */
@Composable
fun PrimaryActionButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Box(
        modifier.heightIn(min = 52.dp).clip(MaterialTheme.shapes.small).drawBehind {
            val bevel = 3.dp.toPx()
            if (enabled) {
                drawRect(Brush.verticalGradient(listOf(Color(0xFFF3CC6E), Color(0xFFD29F36))))
                drawRect(GoldBright, size = Size(size.width, 1.5.dp.toPx()))
                drawRect(GoldDeep, Offset(0f, size.height - bevel), Size(size.width, bevel))
            } else {
                drawRect(Color(0xFF2A2E38))
                drawRect(ForgeSlot, Offset(0f, size.height - bevel), Size(size.width, bevel))
            }
        }.border(1.dp, if (enabled) Color(0xFF5A3C0A) else BronzeDeep, MaterialTheme.shapes.small)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick).padding(horizontal = 20.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        // Dark on gold 9.4:1 (6.1:1 at the foot of the gradient); the disabled label 4.4:1 on its grey plate.
        Text(text, style = MaterialTheme.typography.titleMedium, color = if (enabled) Color(0xFF2A1A04) else Color(0xFF9A927E), textAlign = TextAlign.Center)
    }
}

/** A heading inside a panel or a sheet: gold, over a bronze rule. */
@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text, style = MaterialTheme.typography.titleMedium, color = Gold,
        modifier = modifier.fillMaxWidth().padding(top = Space.md, bottom = Space.xs).semantics { heading() }.drawBehind {
            drawLine(BronzeDeep, Offset(0f, size.height), Offset(size.width, size.height), strokeWidth = 1.dp.toPx())
        }.padding(bottom = 2.dp),
    )
}

/**
 * A label and its value, the value at the right edge; stacked once the text is large. With [delta] the value reads
 * "24 → 31 ▲": the earlier number muted, the present one in the colour of the change, and the arrow saying which way.
 */
@Composable
fun StatRow(label: String, value: String, modifier: Modifier = Modifier, valueColor: Color? = null, delta: StatDelta? = null) {
    val row = modifier.fillMaxWidth().padding(vertical = 2.dp).semantics(mergeDescendants = true) { if (delta != null) contentDescription = "$label: was ${delta.was}, now $value" }
    val values: @Composable () -> Unit = {
        if (delta != null) Text("${delta.was} → ", style = MaterialTheme.typography.bodyMedium, color = CreamMuted)
        Text(value, style = MaterialTheme.typography.titleSmall, color = delta?.kind?.color ?: valueColor ?: Cream, textAlign = TextAlign.End)
        if (delta != null && delta.kind != EffectKind.NEUTRAL) Text(if (delta.kind == EffectKind.BUFF) " ▲" else " ▼", style = MaterialTheme.typography.labelSmall, color = delta.kind.color)
    }
    if (LocalDensity.current.fontScale > 1.3f) {
        Column(row) {
            Text(label, style = MaterialTheme.typography.bodyMedium, color = CreamMuted)
            Row(verticalAlignment = Alignment.CenterVertically) { values() }
        }
    } else {
        Row(row, verticalAlignment = Alignment.Top) {
            Text(label, style = MaterialTheme.typography.bodyMedium, color = CreamMuted, modifier = Modifier.weight(1f).padding(end = Space.sm))
            Row(verticalAlignment = Alignment.CenterVertically) { values() }
        }
    }
}

private const val SEGMENTS = 10

/**
 * A number with a known ceiling: the label and "[value]/[max]" over a ten-segment bar, with [word] (the band it falls
 * in) beside the bar. [was] is the earlier value when it differs: what was lost shows red on the bar, what was gained green.
 */
@Composable
fun StatBar(label: String, value: Int, max: Int, modifier: Modifier = Modifier, word: String? = null, was: Int? = null) {
    val change = was?.takeIf { it != value }
    val kind = when { change == null -> EffectKind.NEUTRAL; value > change -> EffectKind.BUFF; else -> EffectKind.FLAW }
    Column(modifier.fillMaxWidth().clearAndSetSemantics {
        contentDescription = "$label $value of $max" + (word?.let { ", $it" } ?: "") + (change?.let { ", was $it" } ?: "")
    }) {
        StatRow(label, "$value/$max", delta = change?.let { StatDelta(it.toString(), kind) })
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
            Canvas(Modifier.weight(1f).height(10.dp)) {
                val gap = 2.dp.toPx()
                val w = (size.width - gap * (SEGMENTS - 1)) / SEGMENTS
                val now = value.coerceIn(0, max).toFloat() / max * SEGMENTS
                val before = (change ?: value).coerceIn(0, max).toFloat() / max * SEGMENTS
                for (i in 0 until SEGMENTS) {
                    val x = i * (w + gap)
                    fun part(upTo: Float) = (upTo - i).coerceIn(0f, 1f) * w
                    drawRect(ForgeSlot, Offset(x, 0f), Size(w, size.height))
                    drawRect(kind.color.copy(alpha = 0.55f), Offset(x, 0f), Size(part(maxOf(now, before)), size.height))
                    drawRect(Gold, Offset(x, 0f), Size(part(minOf(now, before)), size.height))
                    drawRect(BronzeDeep, Offset(x, 0f), Size(w, size.height), style = Stroke(1.dp.toPx()))
                }
            }
            if (word != null) Text(word, style = MaterialTheme.typography.bodySmall, color = Cream)
        }
    }
}

/** One property of a blade: its sign in a small plate ("+" green, "−" red, "◆" gold), its name, and what it does. */
@Composable
fun EffectRow(kind: EffectKind, title: String, detail: String, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().padding(vertical = Space.xs).semantics(mergeDescendants = true) {}, horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
        Box(
            Modifier.defaultMinSize(24.dp, 24.dp).background(kind.color.copy(alpha = 0.14f), MaterialTheme.shapes.extraSmall).border(1.dp, kind.color, MaterialTheme.shapes.extraSmall),
            contentAlignment = Alignment.Center,
        ) { Text(kind.sign, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = kind.color) }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = if (kind == EffectKind.NEUTRAL) Cream else kind.color)
            if (detail.isNotEmpty()) Text(detail, style = MaterialTheme.typography.bodySmall, color = CreamMuted)
        }
    }
}
