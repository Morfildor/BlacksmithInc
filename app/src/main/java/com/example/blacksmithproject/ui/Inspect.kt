package com.example.blacksmithproject.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionOnScreen
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.toSize
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import com.example.blacksmithproject.R
import com.example.blacksmithproject.ui.theme.Bronze
import com.example.blacksmithproject.ui.theme.BronzeDeep
import com.example.blacksmithproject.ui.theme.Cream
import com.example.blacksmithproject.ui.theme.CreamMuted
import com.example.blacksmithproject.ui.theme.Ember
import com.example.blacksmithproject.ui.theme.ForgeSlot
import com.example.blacksmithproject.ui.theme.Gold
import com.example.blacksmithproject.ui.theme.NUMBERS
import com.example.blacksmithproject.ui.theme.Space
import kotlin.math.roundToInt

/*
 * Inspection (V3, docs/UI_V2_LEDGER.md M7). The main screen is for playing, an inspect card is for understanding, a
 * detail sheet is for studying. One card at a time over the whole app: opening another replaces it. Tapping a thing
 * still chooses it; the ringed "i" beside it (or a long press) inspects it. The card sits by what was inspected when it
 * fits there and docks at the foot of the screen when it does not; Back, a tap outside or Close dismisses it.
 * A card only says what the game already knows: every line comes from the catalog, the save or the engine's outlook.
 */

/** How a line of a card reads: a gain (+, green), a cost (−, red), a condition (◇), a chance (?, never promised) or plain. */
enum class TipTone(val sign: String, val color: Color) {
    BONUS(EffectKind.BUFF.sign, EffectKind.BUFF.color), DRAWBACK(EffectKind.FLAW.sign, EffectKind.FLAW.color),
    CONDITION("◇", Gold), CHANCE("?", CreamMuted), PLAIN("·", CreamMuted),
    /** A rule the thing carries in a fight: a gold diamond, as on the weapon sheet. */
    ABILITY(EffectKind.NEUTRAL.sign, EffectKind.NEUTRAL.color),
}

/** One short stat of a card's head: "Owned 1", "Quality 30 to 55". [tone] marks one that changes the decision. */
@Immutable data class TipStat(val label: String, val value: String, val tone: FactTone = FactTone.PLAIN)

/**
 * One line of a section. With a [title] it is an ability: its name, then [trigger] (when it fires), the [text] (what
 * it does) and [limit] (how often), each on its own line, so a rule reads like a skill and not a sentence to parse.
 */
@Immutable data class TipLine(
    val text: String, val tone: TipTone = TipTone.PLAIN, val title: String? = null, val source: String? = null,
    val trigger: String? = null, val limit: String? = null,
)

@Immutable data class TipSection(val title: String?, val lines: List<TipLine>)

/** A status a line names, with what it does in the fight's own numbers (`statMeaning`). */
@Immutable data class TipKeyword(val name: String, val meaning: String, val color: Color)

/** An action at the foot of a card. The card closes first, so a sheet it opens is never under it. */
@Immutable data class TipAction(val label: String, val tag: String, val onClick: () -> Unit)

/**
 * Everything one inspect card says. [icon] is a drawable (pixel art); [nameColor] carries rarity where there is one.
 * [kind] is the type line ("Augment · Frost"), [stats] the few numbers that decide, [sections] the abilities, gains,
 * costs and conditions, [keywords] the statuses those name, [note] at most a short sentence, [warning] what stands in
 * the way (shown in ember, never hidden), and [actions] at most two ways on (full details, a practice fight).
 */
@Immutable data class TipUi(
    val name: String, val kind: String, val icon: Int? = null, val nameColor: Color = Cream, val meta: String? = null,
    /** A weapon drawn in its layers in place of [icon] (the forge's recipe). */
    val look: WeaponLook? = null,
    val stats: List<TipStat> = emptyList(), val sections: List<TipSection> = emptyList(), val keywords: List<TipKeyword> = emptyList(),
    val note: String? = null, val warning: String? = null, val actions: List<TipAction> = emptyList(), val tag: String = "inspect",
    /** The colour of the card's frame and name band (an element, a rarity); null: the name's rarity colour, else bronze. */
    val accent: Color? = null,
    /** The one number that matters most, shown large under the name ("20 Power"); it is not repeated in [stats]. */
    val headline: TipStat? = null,
)

/** The one open card of the app, or none. */
@Stable
class Inspector {
    internal var open by mutableStateOf<Pair<TipUi, Rect?>?>(null)
        private set

    /** Opens [tip] by [anchor] (screen coordinates), replacing any card that is open. */
    fun show(tip: TipUi, anchor: Rect?) { open = tip to anchor }
    fun close() { open = null }
}

val LocalInspector = staticCompositionLocalOf<Inspector?> { null }

/** Gives [content] the app's one inspector and draws its card over everything, sheets included. */
@Composable
fun ProvideInspector(content: @Composable () -> Unit) {
    val inspector = remember { Inspector() }
    CompositionLocalProvider(LocalInspector provides inspector) {
        content()
        inspector.open?.let { (tip, anchor) -> InspectHost(tip, anchor, inspector::close) }
    }
}

/** Where a thing that can be inspected is on screen, kept without recomposing; the card is placed by it. */
class InspectAnchor { var rect: Rect? = null }

fun Modifier.inspectAnchor(anchor: InspectAnchor): Modifier = onGloballyPositioned { anchor.rect = Rect(it.positionOnScreen(), it.size.toSize()) }

/**
 * A tile that is chosen by a tap and inspected by a long press (TalkBack offers "Inspect" as an action). Long press is
 * never the only way: put an [InfoButton] on the tile too.
 */
@OptIn(ExperimentalFoundationApi::class)
fun Modifier.inspectable(anchor: InspectAnchor, inspector: Inspector?, tip: () -> TipUi, enabled: Boolean = true, role: Role = Role.Button, onClick: () -> Unit): Modifier =
    inspectAnchor(anchor).combinedClickable(
        enabled = enabled, role = role, onLongClickLabel = "Inspect",
        onLongClick = inspector?.let { i -> { i.show(tip(), anchor.rect) } }, onClick = onClick,
    )

/** The ringed "i" that opens a card: 48 dp to touch, quiet until used. [name] is said: "Inspect Frost Bloom". */
@Composable
fun InfoButton(name: String, tip: () -> TipUi, modifier: Modifier = Modifier, anchor: InspectAnchor? = null, tag: String? = null, tint: Color = CreamMuted) {
    val inspector = LocalInspector.current ?: return
    val own = remember { InspectAnchor() }
    Box(
        modifier.size(48.dp).inspectAnchor(own).clip(CircleShape)
            .clickable(role = Role.Button, onClickLabel = "Inspect") { inspector.show(tip(), (anchor ?: own).rect) }
            .semantics { contentDescription = "Inspect $name" }.let { if (tag != null) it.testTag(tag) else it },
        contentAlignment = Alignment.Center,
    ) { Icon(painterResource(R.drawable.ic_info), contentDescription = null, tint = tint, modifier = Modifier.size(20.dp)) }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun InspectHost(tip: TipUi, anchor: Rect?, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        // A lighter dim than a dialog's: the thing inspected stays readable beside its card.
        (LocalView.current.parent as? DialogWindowProvider)?.window?.setDimAmount(0.5f)
        // A dialog is its own window: it does not inherit the root's resource-id exposure that the emulator scripts rely on.
        Box(Modifier.fillMaxSize().semantics { testTagsAsResourceId = true }) {
            Box(Modifier.fillMaxSize().clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss).clearAndSetSemantics {})
            Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) { TipPlacement(anchor) { TipCard(tip, onDismiss) } }
        }
    }
}

/**
 * Under the anchor when the card fits there, over it when it fits there instead, otherwise docked at the foot as a
 * compact sheet (a narrow screen, large text, a long card). Never wider than reads well, never off screen.
 */
@Composable
private fun TipPlacement(anchor: Rect?, card: @Composable () -> Unit) {
    Layout(card, Modifier.fillMaxSize()) { measurables, c ->
        val margin = 12.dp.roundToPx()
        val gap = 8.dp.roundToPx()
        val width = minOf(c.maxWidth - 2 * margin, 400.dp.roundToPx())
        val p = measurables.first().measure(Constraints(maxWidth = width, maxHeight = (c.maxHeight - 2 * margin).coerceAtLeast(0)))
        layout(c.maxWidth, c.maxHeight) {
            val origin = coordinates?.positionOnScreen() ?: Offset.Zero
            val a = anchor?.translate(-origin)
            val x = a?.let { (it.center.x - p.width / 2f).roundToInt().coerceIn(margin, (c.maxWidth - margin - p.width).coerceAtLeast(margin)) } ?: ((c.maxWidth - p.width) / 2)
            val below = a?.let { (it.bottom).roundToInt() + gap }
            val above = a?.let { it.top.roundToInt() - gap - p.height }
            when {
                below != null && below + p.height <= c.maxHeight - margin -> p.place(x, below)
                above != null && above >= margin -> p.place(x, above)
                else -> p.place((c.maxWidth - p.width) / 2, c.maxHeight - margin - p.height)
            }
        }
    }
}

private val CardShape = RoundedCornerShape(3.dp)
private val CardTop = Color(0xFF17130F)
private val CardGround = Color(0xFF0C0A09)

/** The colour that frames a card: its own accent, else the rarity colour of its name, else the forge's bronze. */
private fun TipUi.frame(): Color = accent ?: nameColor.takeIf { it != Cream } ?: Bronze

/**
 * The card itself, cut like an item tooltip: a near-black plate with a fine frame in the thing's colour and a jewel at
 * each corner, a tinted band behind the name, an optional headline number, ornamented dividers, and its lines as affixes
 * (a diamond, the name bright, numbers picked out of the text). It only presents: every word comes from the [TipUi].
 */
@Composable
fun TipCard(tip: TipUi, onClose: () -> Unit, modifier: Modifier = Modifier) {
    val large = LocalDensity.current.fontScale > 1.3f
    val frame = tip.frame()
    Box(
        modifier.shadow(20.dp, CardShape).clip(CardShape)
            .background(Brush.verticalGradient(listOf(CardTop, CardGround)))
            .drawWithContent {
                drawContent()
                val px = 1.dp.toPx()
                // Two fine rules, the outer in the card's colour, then a jewel on each corner.
                drawRect(frame.copy(alpha = 0.55f), style = Stroke(px))
                val inset = 3.dp.toPx()
                drawRect(frame.copy(alpha = 0.18f), topLeft = Offset(inset, inset), size = Size(size.width - 2 * inset, size.height - 2 * inset), style = Stroke(px))
                val r = 3.5.dp.toPx()
                listOf(Offset(r + px, r + px), Offset(size.width - r - px, r + px), Offset(r + px, size.height - r - px), Offset(size.width - r - px, size.height - r - px)).forEach { c ->
                    drawPath(Path().apply { moveTo(c.x, c.y - r); lineTo(c.x + r, c.y); lineTo(c.x, c.y + r); lineTo(c.x - r, c.y); close() }, frame)
                }
            }
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
            .semantics { paneTitle = tip.name }.testTag(tip.tag),
    ) {
        // The thing's art, name, kind and what is held. With very large text it scrolls with the body and Close floats
        // over the corner, so the card's room goes to what it says, not to its title.
        val pinHead = LocalDensity.current.fontScale <= 1.5f
        val body = rememberScrollState()
        val close: @Composable () -> Unit = {
            Box(
                Modifier.size(48.dp).clip(CircleShape).clickable(role = Role.Button, onClick = onClose).semantics { contentDescription = "Close" }.testTag("${tip.tag}_close"),
                contentAlignment = Alignment.Center,
            ) { Icon(painterResource(R.drawable.ic_close), contentDescription = null, tint = CreamMuted, modifier = Modifier.size(18.dp)) }
        }
        Column {
            val head: @Composable (Modifier) -> Unit = { m ->
                Row(m, verticalAlignment = Alignment.Top) {
                    tip.icon?.let { icon ->
                        val slot = if (pinHead) 52.dp else 40.dp
                        Box(
                            Modifier.size(slot).background(Brush.radialGradient(listOf(frame.copy(alpha = 0.22f), ForgeSlot)), RoundedCornerShape(2.dp))
                                .border(1.dp, frame.copy(alpha = 0.5f), RoundedCornerShape(2.dp)),
                            contentAlignment = Alignment.Center,
                        ) { tip.look?.layers?.forEach { PixelIcon(it, if (pinHead) 40.dp else 32.dp) } ?: PixelIcon(icon, if (pinHead) 40.dp else 32.dp) }
                        Spacer(Modifier.width(12.dp))
                    }
                    Column(Modifier.weight(1f).padding(top = 2.dp)) {
                        Text(tip.name, style = MaterialTheme.typography.titleLarge, color = tip.nameColor, modifier = Modifier.semantics { heading() })
                        // The type line in the card's colour, as an item's "Rare Sword" under its name.
                        Text(tip.kind, style = MaterialTheme.typography.bodyMedium, color = if (frame == Bronze) CreamMuted else frame.copy(alpha = 0.9f))
                        tip.meta?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = CreamMuted, modifier = Modifier.padding(top = 2.dp)) }
                    }
                    if (pinHead) close()
                }
            }
            // The band of colour behind the name fades into the plate; with very large text it scrolls with the name.
            val band = listOf(frame.copy(alpha = 0.24f), Color.Transparent)
            if (pinHead) head(Modifier.fillMaxWidth().background(Brush.verticalGradient(band)).padding(start = Space.md, top = Space.md, end = Space.xs, bottom = Space.sm))
            // Only the body scrolls: the way out stays in view on a short screen. A body taller than its room fades at its
            // foot, and its last line clears the actions by a full gap.
            Column(
                Modifier.weight(1f, fill = false).bottomFade(body.canScrollForward, CardGround).verticalScroll(body)
                    .then(if (pinHead) Modifier else Modifier.background(Brush.verticalGradient(band, endY = with(LocalDensity.current) { 120.dp.toPx() })))
                    .padding(horizontal = Space.md).padding(top = if (pinHead) 0.dp else Space.md, bottom = Space.md).testTag("${tip.tag}_body"),
                verticalArrangement = Arrangement.spacedBy(Space.sm),
            ) {
                // Clear of Close, which floats over the corner.
                if (!pinHead) head(Modifier.fillMaxWidth().padding(end = 36.dp))
                TipDivider(frame)
                tip.warning?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = Ember, modifier = Modifier.testTag("${tip.tag}_warning")) }
                tip.headline?.let { TipHeadline(it) }
                if (tip.stats.isNotEmpty()) Column(verticalArrangement = Arrangement.spacedBy(2.dp)) { tip.stats.forEach { TipStatRow(it) } }
                val sections = tip.sections.filter { it.lines.isNotEmpty() }
                if (sections.isNotEmpty() && (tip.stats.isNotEmpty() || tip.headline != null)) TipDivider(frame)
                sections.forEach { s -> TipSectionBlock(s, frame) }
                if (tip.keywords.isNotEmpty()) { TipDivider(frame); TipKeywords(tip.keywords, rule = false) }
                tip.note?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = CreamMuted.copy(alpha = 0.85f)) }
            }
            // The ways on are quiet links under an ornamented rule, never buttons that outrank the screen's own action.
            if (tip.actions.isNotEmpty()) {
                TipDivider(frame, Modifier.padding(horizontal = Space.md))
                if (large) Column(Modifier.fillMaxWidth().padding(horizontal = Space.xs), horizontalAlignment = Alignment.End) { tip.actions.forEach { a -> TipLink(a, onClose) } }
                else Row(Modifier.fillMaxWidth().padding(horizontal = Space.xs), horizontalArrangement = Arrangement.End) { tip.actions.forEach { a -> TipLink(a, onClose) } }
            } else Spacer(Modifier.height(Space.xs))
        }
        // Over scrolled text the way out keeps a disc of the plate behind it.
        if (!pinHead) Box(Modifier.align(Alignment.TopEnd).padding(top = Space.xs, end = Space.xs).drawBehind { if (body.canScrollBackward) drawCircle(CardTop.copy(alpha = 0.92f), 18.dp.toPx()) }) { close() }
    }
}

/** A rule across the card that fades at both ends, with a small jewel at its middle. Decorative. */
@Composable
private fun TipDivider(color: Color, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(9.dp).clearAndSetSemantics {}.drawBehind {
        val y = size.height / 2
        drawLine(Brush.horizontalGradient(listOf(Color.Transparent, color.copy(alpha = 0.45f), color.copy(alpha = 0.45f), Color.Transparent)), Offset(0f, y), Offset(size.width, y), 1.dp.toPx())
        val r = 3.dp.toPx(); val c = Offset(size.width / 2, y)
        drawPath(Path().apply { moveTo(c.x, c.y - r); lineTo(c.x + r, c.y); lineTo(c.x, c.y + r); lineTo(c.x - r, c.y); close() }, color.copy(alpha = 0.85f))
    })
}

/** The one number that matters most, large, with what it is beside it ("20 Power", "30 to 55 Likely quality"). */
@Composable
private fun TipHeadline(s: TipStat) {
    val color = when (s.tone) { FactTone.GOOD -> EffectKind.BUFF.color; FactTone.WARN -> EffectKind.FLAW.color; FactTone.PLAIN -> Cream }
    FlowRow(Modifier.semantics(mergeDescendants = true) {}, itemVerticalAlignment = Alignment.Bottom) {
        Text(s.value, style = MaterialTheme.typography.headlineMedium.copy(fontFeatureSettings = "lnum, tnum"), color = color, modifier = Modifier.padding(end = Space.sm))
        Text(s.label, style = MaterialTheme.typography.bodyMedium, color = CreamMuted, modifier = Modifier.padding(bottom = 4.dp))
    }
}

/**
 * A stat as an item's line: what it is, muted, then its value bright with figures that line up. Wraps as words do. A
 * value that helps or warns is coloured only: its label already says which ("Weak to", "Danger"), and a sign before a
 * word ("− Lethal") reads as arithmetic.
 */
@Composable
private fun TipStatRow(s: TipStat) {
    val color = when (s.tone) { FactTone.GOOD -> EffectKind.BUFF.color; FactTone.WARN -> EffectKind.FLAW.color; FactTone.PLAIN -> Cream }
    Text(buildAnnotatedString {
        withStyle(SpanStyle(color = CreamMuted)) { append(s.label) }
        append("  ")
        withStyle(SpanStyle(color = color, fontWeight = FontWeight.Medium, fontFeatureSettings = NUMBERS)) { append(s.value) }
    }, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.semantics(mergeDescendants = true) {})
}

/** [text] with its numbers picked out (bright, a step heavier) from the muted words around them, as on an item's affix. */
private fun numbered(text: String, words: Color, numbers: Color = Cream): AnnotatedString = buildAnnotatedString {
    var last = 0
    NUMBER.findAll(text).forEach { m ->
        withStyle(SpanStyle(color = words)) { append(text.substring(last, m.range.first)) }
        withStyle(SpanStyle(color = numbers, fontWeight = FontWeight.Medium, fontFeatureSettings = NUMBERS)) { append(m.value) }
        last = m.range.last + 1
    }
    withStyle(SpanStyle(color = words)) { append(text.substring(last)) }
}

private val NUMBER = Regex("[+−-]?\\d+(?:[.,]\\d+)?%?")

private val TipSectionTitle @Composable get() = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "smcp, lnum", letterSpacing = 0.6.sp)

@Composable
internal fun TipSectionBlock(s: TipSection, frame: Color = Bronze) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Space.sm)) {
        s.title?.let { Text(it, style = TipSectionTitle, color = frame.copy(alpha = 0.85f).takeIf { frame != Bronze } ?: CreamMuted, modifier = Modifier.semantics { heading() }) }
        // An unmarked line among marked ones keeps their text column, so the block reads as one aligned list.
        val column = s.lines.any { it.tone != TipTone.PLAIN }
        s.lines.forEach { l -> if (l.title != null) TipAbility(l) else TipPlainLine(l, column) }
    }
}

/** The mark that leads a line: a small drawn diamond for a rule, the line's sign for a gain, cost, condition or chance. */
@Composable
private fun TipMark(tone: TipTone, modifier: Modifier = Modifier) {
    if (tone == TipTone.ABILITY) Box(modifier.width(18.dp).height(20.dp).clearAndSetSemantics {}.drawBehind {
        val r = 3.5.dp.toPx(); val c = Offset(r + 1.dp.toPx(), size.height / 2)
        drawPath(Path().apply { moveTo(c.x, c.y - r); lineTo(c.x + r, c.y); lineTo(c.x, c.y + r); lineTo(c.x - r, c.y); close() }, tone.color)
    })
    // A sign grows with the text; it keeps a gap from its words at any size.
    else Text(tone.sign, style = MaterialTheme.typography.bodyMedium, color = tone.color, modifier = modifier.widthIn(min = 18.dp).padding(end = Space.xs))
}

@Composable
private fun TipPlainLine(l: TipLine, column: Boolean = false) {
    Row(Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}, verticalAlignment = Alignment.Top) {
        // A plain line needs no mark: only gains, costs, conditions, chances and rules carry one.
        if (l.tone != TipTone.PLAIN) TipMark(l.tone) else if (column) Spacer(Modifier.width(18.dp))
        val words = when (l.tone) { TipTone.CHANCE, TipTone.PLAIN -> CreamMuted; else -> Cream.copy(alpha = 0.92f) }
        Text(numbered(l.text, words), style = MaterialTheme.typography.bodyMedium)
    }
}

/** An ability as an affix: its mark and name bright, when and how often beside it, then what it does with its numbers lit. */
@Composable
private fun TipAbility(l: TipLine) {
    Row(Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}, verticalAlignment = Alignment.Top) {
        if (l.tone != TipTone.PLAIN) TipMark(l.tone)
        Column(Modifier.weight(1f)) {
            // A short trigger and the limit sit beside the name ("On hit · once per round"); a trigger only the rule's own
            // sentence can say keeps a line of its own, whole.
            val shortTrigger = l.trigger?.takeIf { it.length <= SHORT_TRIGGER }
            val meta = listOfNotNull(shortTrigger, l.limit?.let { if (shortTrigger != null) it.replaceFirstChar { c -> c.lowercase() } else it }).joinToString(" · ")
            FlowRow(itemVerticalAlignment = Alignment.CenterVertically) {
                Text(l.title.orEmpty(), style = MaterialTheme.typography.titleSmall, color = if (l.tone == TipTone.CHANCE) CreamMuted else Cream, modifier = Modifier.padding(end = Space.sm))
                // What kind of line it is (Ability, Passive, Trait, where a weapon's effect comes from): a quiet word, never
                // spaced capitals that compete with the name.
                l.source?.let { Text(it.replaceFirstChar { c -> c.uppercase() }, style = MaterialTheme.typography.labelMedium, color = CreamMuted.copy(alpha = 0.8f), modifier = Modifier.padding(end = Space.sm)) }
                if (meta.isNotEmpty()) Text(meta, style = MaterialTheme.typography.labelMedium, color = CreamMuted)
            }
            if (shortTrigger == null) l.trigger?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = CreamMuted) }
            if (l.text.isNotBlank()) Text(numbered(l.text, if (l.tone == TipTone.CHANCE) CreamMuted else CreamMuted), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

private const val SHORT_TRIGGER = 24

/** A way on from a card: a quiet text link with its chevron, 48 dp tall. The card closes first. */
@Composable
private fun TipLink(a: TipAction, onClose: () -> Unit) {
    Row(
        Modifier.heightIn(min = 48.dp).clip(RoundedCornerShape(6.dp)).clickable(role = Role.Button) { onClose(); a.onClick() }.padding(horizontal = Space.sm).testTag(a.tag),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(a.label, style = MaterialTheme.typography.labelLarge, color = Cream)
        Chevron(ChevronWay.RIGHT, tint = CreamMuted, size = 18.dp)
    }
}

/** The statuses a card's rules name, each in its own colour with what it does: a glossary read once, below the rules. */
@Composable
internal fun TipKeywords(keywords: List<TipKeyword>, rule: Boolean = true) {
    if (keywords.isEmpty()) return
    Column(
        Modifier.fillMaxWidth().then(if (rule) Modifier.drawBehind { drawLine(BronzeDeep, Offset.Zero, Offset(size.width, 0f), 1.dp.toPx()) }.padding(top = Space.sm) else Modifier),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        keywords.forEach { k ->
            Text(buildAnnotatedString {
                withStyle(SpanStyle(color = k.color, fontWeight = FontWeight.Medium)) { append(k.name) }
                append("  ")
                append(numbered(k.meaning, CreamMuted))
            }, style = MaterialTheme.typography.bodySmall, color = CreamMuted)
        }
    }
}
