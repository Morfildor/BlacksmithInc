package com.example.blacksmithproject.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.blacksmithproject.GameViewModel
import com.example.blacksmithproject.UiState
import com.example.blacksmithproject.ui.detail.ItemDetail
import com.example.blacksmithproject.ui.detail.PriceEditor
import com.example.blacksmithproject.ui.detail.WeaponStatBody
import com.example.blacksmithproject.ui.detail.itemDetail
import com.example.blacksmithproject.ui.detail.shelfFullLine
import com.example.blacksmithproject.ui.theme.BronzeDeep
import com.example.blacksmithproject.ui.theme.Cream
import com.example.blacksmithproject.ui.theme.PaperInk
import com.example.blacksmithproject.ui.theme.PaperInkMuted
import com.example.blacksmithproject.ui.theme.PaperRule
import com.example.blacksmithproject.ui.theme.Space
import com.tinyblacksmith.core.engine.Command
import com.tinyblacksmith.core.gazette.Gazette
import com.tinyblacksmith.core.gazette.GazetteDigest
import com.tinyblacksmith.core.model.CombatReplay
import com.tinyblacksmith.core.model.DayResolution
import com.tinyblacksmith.core.model.GameState
import com.tinyblacksmith.core.model.HeroId
import com.tinyblacksmith.core.model.Rarity
import com.tinyblacksmith.core.model.ReplayKind
import com.tinyblacksmith.core.model.WeaponId
import kotlinx.coroutines.delay

/**
 * Item card after an atomic forge, with the price chosen on it. The reveal is decorative; the weapon already exists in
 * the saved state, in storage. Closing the card any way but "List at" leaves it there, and the workshop says so.
 */
@Composable
fun ForgeResultDialog(s: UiState.Playing, weaponId: WeaponId, vm: GameViewModel, reducedMotion: Boolean) {
    val w = s.state.weapons[weaponId] ?: run { vm.dismissReveal(); return }
    val content = vm.engine.content
    val haptics = LocalHaptics.current
    LaunchedEffect(weaponId) { haptics.play(if (w.signatureId != null || w.rarity >= Rarity.EPIC) Moment.FORGE_SIGNATURE else Moment.FORGE_STRIKE) }
    val detail = remember(s.state, weaponId) { vm.engine.itemDetail(s.state, weaponId) } ?: return
    val request = remember(s.state, weaponId, s.draft.commissionId) { vm.engine.forgedFor(s.state, weaponId, s.draft.commissionId) }
    // The card draws under the system bars and the keyboard and pads itself clear of both, so the price and the
    // buttons stay above the keyboard.
    Dialog(onDismissRequest = vm::storeForged, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        ForgeResultCard(
            detail, "${content.family(w.familyId).name} of ${content.material(w.coreId).name} and ${content.material(w.augmentId).name}.", request,
            enabled = !s.busy, onStore = vm::storeForged, onList = { vm.listForged(w.id, it) },
            modifier = Modifier.safeDrawingPadding().padding(horizontal = Space.md, vertical = Space.sm).semantics { testTagsAsResourceId = true }.heightIn(max = (LocalConfiguration.current.screenHeightDp * 0.92f).dp),
            reveal = !reducedMotion,
            overSprite = { if (w.signatureId != null || w.rarity >= Rarity.EPIC) MilestoneBurst(88.dp, reducedMotion) },
            learning = s.learning,
        )
    }
}

/** The card of a blade just made: a number without a ceiling that is still zero and never was anything else (renown) says nothing yet. */
internal fun ItemDetail.withoutEmptyStats(): ItemDetail = copy(stats = stats.filter { it.max != null || it.value != 0 || it.was != null })

// The reveal's two lengths at normal motion: how long the sprite and name stand alone, and the fade that follows.
private const val REVEAL_HOLD_MS = 700L
private const val REVEAL_FADE_MS = 250

/**
 * The forge result without its window. The blade's card scrolls; the price and the two ways on are pinned under it, so
 * "List at" is on screen without a scroll and stays above the keyboard. With text larger than 1.3 the pinned part would
 * leave the blade no room, so the whole card is one scrolling column again. A blade forged for a request says so first.
 * [detail] carries the price facts (its `Stock`); [onList] is given the chosen price. [enabled] greys "List at" while a
 * command is being saved; Store issues no command and is never greyed.
 *
 * With [reveal] the blade's sprite, name and rarity stand alone for a moment before its numbers, buffs and recipe fade
 * in under them; a tap anywhere on the card brings them at once. Nothing moves and the price and both buttons are there
 * and live from the first frame, so the reveal never costs a tap or a wait. Without it (reduced motion) the card is
 * whole at once. A brand-new blade has no renown to show, so the zero is left out here; the blade's sheet keeps it.
 */
@Composable
fun ForgeResultCard(
    detail: ItemDetail,
    recipe: String,
    request: ForgedForUi?,
    enabled: Boolean,
    onStore: () -> Unit,
    onList: (Int) -> Unit,
    modifier: Modifier = Modifier,
    reveal: Boolean = false,
    overSprite: @Composable BoxScope.() -> Unit = {},
    learning: ForgeLearningUi? = null,
) {
    var revealed by rememberSaveable(detail.weaponId.value) { mutableStateOf(!reveal) }
    LaunchedEffect(detail.weaponId) { if (!revealed) { delay(REVEAL_HOLD_MS); revealed = true } }
    val shown by animateFloatAsState(if (revealed) 1f else 0f, tween(if (reveal) REVEAL_FADE_MS else 0), label = "reveal")
    val fresh = remember(detail) { detail.withoutEmptyStats() }
    val blade: @Composable ColumnScope.() -> Unit = {
        request?.let { r ->
            Column(Modifier.padding(bottom = Space.sm).testTag("reveal_request")) {
                Text(r.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                Secondary(r.terms)
                EffectRow(if (r.fits) EffectKind.BUFF else EffectKind.FLAW, r.fit, r.handover)
                // The engine sets no blade aside (`Commissions.pick`): say what End Day does instead of promising this one.
                if (r.accepted) Secondary("At End Day, the buyer takes a matching weapon from storage first, then the shelf. Ordinary orders don't reserve a weapon.")
            }
        }
        WeaponStatBody(fresh, overSprite, detailAlpha = { shown })
        // What this forge added to the notebook; absent on a result reopened later, where "new" could no longer be told.
        learning?.let { LearningCard(it, Modifier.padding(top = Space.sm)) }
        Secondary(recipe, Modifier.padding(top = Space.md).graphicsLayer { alpha = shown })
        HorizontalDivider(Modifier.padding(vertical = Space.sm), color = BronzeDeep)
    }
    val price: @Composable ColumnScope.() -> Unit = price@{
        val stock = detail.stock?.takeIf { it.listedPrice == null }
        if (stock == null) {
            // Reopened after the blade was listed or left the shop: there is nothing left to choose here.
            Secondary(detail.stock?.listedPrice?.let { "Already on the shelf, asking $it gold." } ?: "This weapon is no longer in storage.")
            SecondaryActionButton("Close", onStore, Modifier.fillMaxWidth().padding(top = Space.sm).heightIn(min = 52.dp).testTag("reveal_store"))
            return@price
        }
        var priceText by rememberSaveable(detail.weaponId.value) { mutableStateOf(stock.suggestedPrice.toString()) }
        PriceEditor(priceText, { priceText = it }, stock.suggestedPrice, stock.funds, "reveal") { chosen ->
            // The engine refuses a listing on a full shelf; say so here and leave Store as the way on.
            val full = stock.shelfFree <= 0
            if (full) Text("${shelfFullLine(stock.slots)} Store this weapon for now. Remove another from the shelf to make room.", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = Space.sm).testTag("reveal_shelf_full"))
            Row(Modifier.fillMaxWidth().padding(top = Space.sm), horizontalArrangement = Arrangement.spacedBy(Space.sm), verticalAlignment = Alignment.CenterVertically) {
                SecondaryActionButton("Store", onStore, Modifier.heightIn(min = 52.dp).testTag("reveal_store"))
                PrimaryActionButton(
                    chosen?.let { "List at $it" } ?: "List", { chosen?.let(onList) },
                    Modifier.weight(1f).testTag("reveal_list").semantics { if (full) contentDescription = "List, unavailable: ${shelfFullLine(stock.slots)}" },
                    enabled = enabled && chosen != null && !full,
                )
            }
        }
    }
    val large = LocalDensity.current.fontScale > 1.3f
    // While the keyboard is up the pinned card is only its price: on a short screen the title and the blade would
    // otherwise leave the buttons under the keyboard.
    val typing = !large && WindowInsets.ime.getBottom(LocalDensity.current) > 0
    // No semantics on the tap: it only ends a moment's fade, and the buttons under it keep their own taps.
    FramedPanel("Fresh from the forge".takeIf { !typing }, modifier.fillMaxWidth().pointerInput(Unit) { detectTapGestures { revealed = true } }) {
        if (large) Column(Modifier.verticalScroll(rememberScrollState())) { blade(); price() }
        else {
            if (!typing) Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) { blade() }
            // Measured before the card above it, so it has the room it needs; it scrolls only when the keyboard leaves less.
            Column(Modifier.verticalScroll(rememberScrollState())) { price() }
        }
    }
}

/**
 * The Gazette as a newspaper: the masthead, then the day's briefing (what changed, the shop, before tomorrow), then
 * All details and Battle replays as two closed disclosures. Every line derives from real event records. The outcome is
 * on screen before any animation, the replay is decoration and skippable (GDD 11), and the disclosures are a reading
 * preference only: they start closed every time and nothing here touches the saved game.
 */
@Composable
fun DayReportDialog(state: GameState, r: DayResolution, vm: GameViewModel, reducedMotion: Boolean) {
    val totalSteps = r.replays.sumOf { it.rounds.size + 1 }
    var replaysOpen by remember(r.commandId) { mutableStateOf(false) }
    var detailsOpen by remember(r.commandId) { mutableStateOf(false) }
    var shown by remember(r.commandId) { mutableIntStateOf(if (reducedMotion) totalSteps else 0) }
    LaunchedEffect(r.commandId, reducedMotion, replaysOpen) {
        if (reducedMotion) { shown = totalSteps; return@LaunchedEffect }
        if (!replaysOpen) return@LaunchedEffect
        while (shown < totalSteps) { delay(700); shown += 1 }
    }
    val digest = remember(r.commandId) { GazetteDigest.of(state, r.day, vm.engine.content, vm.engine.config) }
    // The Gazette is read over the shop day: closing it (the button, back or a tap outside) never acknowledges the day.
    Dialog(onDismissRequest = { vm.back() }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            color = Color.Transparent,
            contentColor = PaperInk,
            shape = MaterialTheme.shapes.large,
            modifier = Modifier.fillMaxWidth().heightIn(max = (LocalConfiguration.current.screenHeightDp * 0.92f).dp).padding(horizontal = Space.md).paperBackground().semantics { testTagsAsResourceId = true },
        ) {
            Column(Modifier.padding(horizontal = Space.lg, vertical = Space.md)) {
                // The paper's name large, the date as a dateline under it.
                Column(Modifier.fillMaxWidth().semantics(mergeDescendants = true) { heading() }, horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(Gazette.PAPER, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
                    Text(Gazette.dateline(r.day), style = MaterialTheme.typography.labelMedium, color = PaperInkMuted)
                }
                PaperRuleLine(top = Space.sm, bottom = 2.dp)
                PaperRuleLine(top = 0.dp, bottom = Space.sm)
                Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).testTag("report_body")) {
                    DigestBody(digest)
                    PaperDisclosure("All details", detailsOpen, { detailsOpen = !detailsOpen }, "report_details") { EditionBody(digest.details, Modifier.padding(bottom = Space.sm)) }
                    if (r.replays.isNotEmpty()) PaperDisclosure("Battle replays", replaysOpen, { replaysOpen = !replaysOpen }, "report_replays") {
                        r.replays.firstOrNull()?.takeIf { it.kind == ReplayKind.SIEGE }?.let { replay -> ReplayStage(replay, shown, state, vm, reducedMotion) }  // the siege first; fights are text only
                        if (shown < totalSteps) TextButton(onClick = { shown = totalSteps }, modifier = Modifier.testTag("report_skip"), colors = ButtonDefaults.textButtonColors(contentColor = PaperInk)) { Text("Show all rounds") }
                        var step = 0
                        r.replays.forEach { replay ->
                            var open by remember(r.commandId, replay.title) { mutableStateOf(false) }
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Text(replay.title, fontFamily = FontFamily.Serif, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f).padding(top = Space.sm))
                                TextButton(onClick = { open = !open }, colors = ButtonDefaults.textButtonColors(contentColor = PaperInkMuted)) {
                                    Text(if (open) "Hide rounds" else "${replay.rounds.size} rounds")
                                }
                            }
                            replay.rounds.forEachIndexed { i, round ->
                                if (open && step < shown) Text("${i + 1}. ${round.attacker} ${round.note} (${round.damage})", style = MaterialTheme.typography.bodySmall, color = PaperInkMuted, modifier = Modifier.padding(top = 2.dp))
                                step += 1
                            }
                            if (step < shown) Text(replay.outcome, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = Space.xs))
                            step += 1
                        }
                    }
                }
                PrimaryActionButton("Close", vm::closeGazette, Modifier.fillMaxWidth().padding(top = Space.md).testTag("report_close"))
            }
        }
    }
}

/**
 * The concise paper under a masthead: the stories that matter, the shop in a line or two and what to do before
 * tomorrow. A section with nothing to say is not drawn. [onOpenNotebook] gives a story about a recipe its button.
 */
@Composable
fun DigestBody(digest: GazetteDigest.Digest, modifier: Modifier = Modifier, onOpenNotebook: (() -> Unit)? = null) {
    var moreOpen by remember(digest) { mutableStateOf(false) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Space.md)) {
        if (digest.isQuiet) Text(GazetteDigest.QUIET, fontFamily = FontFamily.Serif, style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
        digest.stories.forEach { StoryBlock(it, onOpenNotebook) }
        if (digest.more.isNotEmpty()) {
            PaperDisclosure("${digest.more.size} more ${if (digest.more.size == 1) "change" else "changes"}", moreOpen, { moreOpen = !moreOpen }, "report_more") {
                Column(Modifier.padding(top = Space.xs), verticalArrangement = Arrangement.spacedBy(Space.md)) { digest.more.forEach { StoryBlock(it, onOpenNotebook) } }
            }
        }
        if (digest.shop.isNotEmpty()) PaperSection("The shop", digest.shop)
        if (digest.before.isNotEmpty()) PaperSection("Before tomorrow", digest.before)
    }
}

@Composable
private fun StoryBlock(story: GazetteDigest.Story, onOpenNotebook: (() -> Unit)?) {
    Column(Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}) {
        Text(story.heading, fontFamily = FontFamily.Serif, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.semantics { heading() })
        if (story.body.isNotEmpty()) Text(story.body, fontFamily = FontFamily.Serif, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 2.dp))
        story.details.forEach { Text(it, fontFamily = FontFamily.Serif, style = MaterialTheme.typography.bodySmall, color = PaperInkMuted, modifier = Modifier.padding(top = 2.dp)) }
    }
    if (story.opensNotebook && onOpenNotebook != null) {
        TextButton(onClick = onOpenNotebook, colors = ButtonDefaults.textButtonColors(contentColor = PaperInk), modifier = Modifier.testTag("report_open_notebook")) { Text("Open notebook") }
    }
}

/** A labelled run of short lines: the label runs into a rule to the edge of the column. */
@Composable
private fun PaperSection(title: String, lines: List<String>) {
    val rule = LocalContentColor.current.copy(alpha = 0.3f)
    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
            Text(title.uppercase(), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp, modifier = Modifier.semantics { heading() })
            HorizontalDivider(Modifier.weight(1f), color = rule)
        }
        lines.forEach { Text(it, fontFamily = FontFamily.Serif, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 6.dp)) }
    }
}

/** A closed-by-default section of the paper: a full-width 48 dp target saying what it holds and whether it is open. */
@Composable
internal fun PaperDisclosure(title: String, open: Boolean, onToggle: () -> Unit, tag: String, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = Space.sm)) {
        HorizontalDivider(color = PaperRule, thickness = 1.dp)
        Row(
            Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(role = Role.Button, onClick = onToggle).testTag(tag)
                .semantics { contentDescription = "$title, ${if (open) "open" else "closed"}. Tap to ${if (open) "close" else "open"}." },
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(title, fontFamily = FontFamily.Serif, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            Text(if (open) "▴" else "▾", style = MaterialTheme.typography.titleMedium)
        }
        if (open) content()
    }
}

/** One day's full record below the briefing: the lede, the tally line, then each section as a bulleted list. */
@Composable
fun EditionBody(edition: Gazette.Edition, modifier: Modifier = Modifier) {
    val muted = LocalContentColor.current.copy(alpha = 0.7f)
    Column(modifier) {
        if (edition.lede.isEmpty() && edition.sections.isEmpty() && edition.tally.isEmpty()) Text("Nothing else was recorded.", fontFamily = FontFamily.Serif, style = MaterialTheme.typography.bodyMedium)
        val rule = LocalContentColor.current.copy(alpha = 0.3f)
        // The headline, then a second one in italics under it: a paper's head and its strap.
        edition.lede.forEachIndexed { i, h ->
            Text(h, fontFamily = FontFamily.Serif, style = if (i == 0) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleSmall, fontStyle = if (i == 0) null else FontStyle.Italic, modifier = Modifier.padding(bottom = Space.sm))
        }
        // The day's tally as the deck: one line over a hairline, and under one when a headline stands above it.
        if (edition.tally.isNotEmpty()) {
            if (edition.lede.isNotEmpty()) HorizontalDivider(color = rule)
            Text(edition.tally.joinToString("  ·  "), style = MaterialTheme.typography.labelMedium, color = muted, modifier = Modifier.padding(top = if (edition.lede.isEmpty()) 0.dp else 6.dp, bottom = 6.dp))
            HorizontalDivider(color = rule)
        }
        edition.sections.forEach { section ->
            // A section label runs into a rule to the edge of the column.
            Row(Modifier.padding(top = Space.md, bottom = 2.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                Text(section.title.uppercase(), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp, modifier = Modifier.semantics { heading() })
                HorizontalDivider(Modifier.weight(1f), color = rule)
            }
            section.lines.forEach { line ->
                Row(Modifier.padding(top = 6.dp)) {
                    Text("•", fontFamily = FontFamily.Serif, style = MaterialTheme.typography.bodyMedium, color = muted, modifier = Modifier.width(14.dp))
                    Text(line, fontFamily = FontFamily.Serif, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun PaperRuleLine(top: Dp, bottom: Dp) {
    HorizontalDivider(color = PaperRule, thickness = 1.dp, modifier = Modifier.padding(top = top, bottom = bottom))
}

@Composable
fun BlessingDialog(s: UiState.Playing, vm: GameViewModel) {
    val content = vm.engine.content
    AlertDialog(
        modifier = Modifier.semantics { testTagsAsResourceId = true },
        // Back means "Decide later"; a tap beside the offer does nothing.
        onDismissRequest = vm::dismissBlessingOffer,
        properties = DialogProperties(dismissOnClickOutside = false),
        title = { Text("The town offers a blessing") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Space.sm)) {
                s.state.pendingBlessingOffer.forEach { id ->
                    val b = content.blessing(id)
                    BlessingOption(b.name, b.description, { vm.dispatch(Command.ChooseBlessing(id)) }, Modifier.testTag("blessing_${id.value}")) {
                        Sprites.blessing(id)?.let { PixelImage(it, 32.dp, description = null); Spacer(Modifier.width(12.dp)) }
                    }
                }
            }
        },
        confirmButton = { InlineActionButton("Decide later", vm::dismissBlessingOffer, Modifier.testTag("blessing_later")) },
    )
}

@Composable
fun ErrorDialog(message: String, onDismiss: () -> Unit) {
    val haptics = LocalHaptics.current
    LaunchedEffect(message) { haptics.play(Moment.REJECTED) }
    AlertDialog(
        modifier = Modifier.semantics { testTagsAsResourceId = true },
        onDismissRequest = onDismiss,
        title = { Text("The forge says") },
        text = { Text(message) },
        confirmButton = { PrimaryActionButton("Alright", onDismiss, Modifier.testTag("error_ok")) },
    )
}

/** Poses for the siege diorama derive from the replay step being shown; the stage never changes outcomes. */
@Composable
private fun ReplayStage(replay: CombatReplay, shown: Int, state: GameState, vm: GameViewModel, reducedMotion: Boolean) {
    val current = (shown - 1).coerceIn(-1, replay.rounds.size)  // -1 = nothing yet, size = outcome shown
    val currentRound = replay.rounds.getOrNull(current)
    val faction = vm.engine.content.factions.firstOrNull { f -> replay.rounds.any { it.attacker == f.siegeName || it.defender == f.siegeName } }
    val heroes = replay.rounds.mapNotNull { round -> round.attackerId?.let { state.heroes[HeroId(it)] } }.distinctBy { it.id }.take(3)
    val raidersAttack = currentRound != null && faction != null && currentRound.attacker == faction.siegeName
    val lost = current >= replay.rounds.size && replay.outcome != "Town held"
    val defenders = heroes.map { h ->
        StageActor({ pose, tick -> Sprites.heroFrame(h.classId, pose, tick) }, if (currentRound?.attackerId == h.id.value) Sprites.Pose.ATTACK else Sprites.Pose.IDLE, flipped = false)
    }
    val raiders = faction?.let { f ->
        listOf(false, true).mapIndexed { i, elite ->
            val pose = when {
                raidersAttack -> Sprites.Pose.ATTACK
                currentRound?.attackerId != null && i == 0 -> Sprites.Pose.HIT
                else -> Sprites.Pose.IDLE
            }
            StageActor({ p, tick -> Sprites.monsterFrame(f.id, elite, p, tick) }, pose, flipped = false)
        }
    } ?: emptyList()
    SiegeStage(defenders, raiders, damaged = lost, reducedMotion = reducedMotion, modifier = Modifier.padding(bottom = Space.md))
}
