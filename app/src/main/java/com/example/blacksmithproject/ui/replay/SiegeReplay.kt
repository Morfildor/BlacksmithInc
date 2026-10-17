package com.example.blacksmithproject.ui.replay

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import com.example.blacksmithproject.ui.FramedPanel
import com.example.blacksmithproject.ui.InlineActionButton
import com.example.blacksmithproject.ui.PrimaryActionButton
import com.example.blacksmithproject.ui.SecondaryActionButton
import com.example.blacksmithproject.ui.theme.Bronze
import com.example.blacksmithproject.ui.theme.BronzeDeep
import com.example.blacksmithproject.ui.theme.BuffGreen
import com.example.blacksmithproject.ui.theme.Cream
import com.example.blacksmithproject.ui.theme.CreamMuted
import com.example.blacksmithproject.ui.theme.Ember
import com.example.blacksmithproject.ui.theme.FlawRed
import com.example.blacksmithproject.ui.theme.ForgeNight
import com.example.blacksmithproject.ui.theme.ForgePanel
import com.example.blacksmithproject.ui.theme.ForgePanelRaised
import com.example.blacksmithproject.ui.theme.ForgeSlot
import com.example.blacksmithproject.ui.theme.Gold
import com.example.blacksmithproject.ui.theme.Space
import com.tinyblacksmith.core.combat.ActorState
import com.tinyblacksmith.core.combat.FightAccounting
import com.tinyblacksmith.core.combat.FightReplay
import com.tinyblacksmith.core.combat.FightResult
import com.tinyblacksmith.core.combat.FightRules
import com.tinyblacksmith.core.combat.Frame
import com.tinyblacksmith.core.combat.FrameKind
import com.tinyblacksmith.core.combat.Pop
import com.tinyblacksmith.core.combat.PopKind
import com.tinyblacksmith.core.combat.Replay
import com.tinyblacksmith.core.combat.ReplayActor
import com.tinyblacksmith.core.combat.ReplayTimeline
import com.tinyblacksmith.core.combat.Side
import com.tinyblacksmith.core.combat.StepKind
import com.tinyblacksmith.core.content.CombatContent
import com.tinyblacksmith.core.content.ContentCatalog
import com.tinyblacksmith.core.text.Terms
import com.tinyblacksmith.core.model.GameState
import com.tinyblacksmith.core.model.HeroId
import com.tinyblacksmith.core.model.WeaponId
import com.example.blacksmithproject.ui.shopday.SiegeOutcomeUi
import kotlinx.coroutines.delay

/**
 * The siege as it was fought, played back from its record (`SiegeReport.fight`): defenders at the foot, the besiegers
 * at the head, each with a face, a health bar, its states and a lane above it for what just happened to it. Then the
 * report. It is a window onto a value: it dispatches nothing, draws nothing and saves nothing, so opening it, skipping
 * it, replaying it or leaving it halfway changes no outcome and gives nothing. Its position survives a rotation.
 *
 * A record older than replays, or one that does not add up, opens straight on the report, which then says what the
 * save holds and shows the fight as text.
 */
@Composable
fun SiegeReplayDialog(
    fight: FightResult,
    siege: SiegeOutcomeUi,
    state: GameState,
    content: ContentCatalog,
    rules: FightRules,
    reducedMotion: Boolean,
    startOnReport: Boolean,
    onOpenHero: (HeroId) -> Unit,
    onOpenBlade: (WeaponId) -> Unit,
    onOpenGazette: () -> Unit,
    onClose: () -> Unit,
) {
    val replay = remember(fight, rules) { FightReplay.of(fight, rules) }
    val account = remember(fight) { FightAccounting.of(fight) }
    val faces = remember(replay, state) { replay?.let { replayFaces(it, state, content) }.orEmpty() }
    var report by rememberSaveable(fight.title) { mutableStateOf(startOnReport || replay == null) }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize().semantics { testTagsAsResourceId = true }.testTag("replay"), color = ForgeNight, contentColor = Cream) {
            Column(Modifier.fillMaxSize().safeDrawingPadding()) {
                if (report || replay == null) {
                    SiegeReport(
                        fight, replay != null, account, siege, state, faces, onWatch = if (replay != null) ({ report = false }) else null,
                        onOpenHero = onOpenHero, onOpenBlade = onOpenBlade, onOpenGazette = onOpenGazette, onClose = onClose,
                    )
                } else {
                    ReplayStage(replay, faces, state, content, rules, reducedMotion, onReport = { report = true }, onOpenHero = onOpenHero, onOpenBlade = onOpenBlade, onClose = onClose)
                }
            }
        }
    }
}

/** The playback itself: the two lines of fighters, what is happening now, the last few actions, and the controls. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReplayStage(
    replay: Replay, faces: Map<String, ReplayFace>, state: GameState, content: ContentCatalog, rules: FightRules, reducedMotion: Boolean,
    onReport: () -> Unit, onOpenHero: (HeroId) -> Unit, onOpenBlade: (WeaponId) -> Unit, onClose: () -> Unit,
) {
    var short by rememberSaveable { mutableStateOf(false) }
    var fast by rememberSaveable { mutableStateOf(false) }
    var playing by rememberSaveable { mutableStateOf(true) }
    var at by rememberSaveable { mutableIntStateOf(0) }
    var inspecting by rememberSaveable { mutableStateOf<String?>(null) }
    val frames = remember(replay, short) { ReplayTimeline.frames(replay, short) }
    val index = at.coerceIn(0, frames.lastIndex)
    val frame = frames[index]
    val step = replay.steps[frame.step]
    val now = ReplayTimeline.state(replay, frame)
    val labels = ReplayTimeline.labels(replay, frame)

    // The clock. It stops for a pause, an open portrait, or the app going to the background, and restarts the frame's wait.
    val resumed = LocalLifecycleOwner.current.lifecycle.currentStateAsState().value.isAtLeast(Lifecycle.State.RESUMED)
    LaunchedEffect(index, playing, fast, short, inspecting, resumed) {
        if (!playing || inspecting != null || !resumed) return@LaunchedEffect
        delay(ReplayTimeline.millis(frame.kind, fast).toLong())
        if (index < frames.lastIndex) at = index + 1 else { playing = false; onReport() }
    }
    fun switchShort(to: Boolean) {
        if (to == short) return
        // The same moment in the other version: the step being shown, or the next one the short version keeps.
        val next = ReplayTimeline.frames(replay, to)
        at = ReplayTimeline.frameOf(next, frame.step)
        short = to
    }

    val density = LocalDensity.current
    // The lane holds a pop's two short lines and its note at any text size: the faces under it never move.
    val lane = with(density) { 70.sp.toDp() }
    val party = replay.actors.filter { it.side == Side.PARTY && now[it.key]?.present == true }
    val enemies = replay.actors.filter { it.side == Side.ENEMY && now[it.key]?.present == true }
    val current = frame.pop
    val activeHolder = current?.takeIf { it.rule != null }?.source

    Column(Modifier.fillMaxSize()) {
        // The head: what this is and where in it we are.
        Row(Modifier.fillMaxWidth().padding(start = Space.md, end = Space.xs, top = Space.xs), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(replay.title, style = MaterialTheme.typography.titleMedium, color = Gold, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.semantics { heading() })
                Text(
                    listOf("Round ${step.round} of ${replay.rounds}", if (short) "Highlights" else "Full fight", if (fast) "2x" else "1x").joinToString(" · "),
                    style = MaterialTheme.typography.labelMedium, color = CreamMuted, modifier = Modifier.testTag("replay_position"),
                )
            }
            InlineActionButton("Close", onClose, Modifier.testTag("replay_close"))
        }
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = Space.md)) {
            SideRow("Attackers", enemies, faces, now, labels, step.actor, activeHolder, current, frame, lane, reducedMotion, towardBelow = true) { inspecting = it; playing = false }
            NowBanner(replay, frame, Modifier.padding(vertical = Space.sm))
            SideRow("On the wall", party, faces, now, labels, step.actor, activeHolder, current, frame, lane, reducedMotion, towardBelow = false) { inspecting = it; playing = false }
            RecentStrip(replay, frames, index, Modifier.padding(top = Space.sm))
        }
        // The controls are never covered: the inspected fighter opens over the stage, above them.
        inspecting?.let { key ->
            replay.actor(key)?.let { a ->
                Inspector(a, now[key], replay, content, rules, state, onOpenHero, onOpenBlade, onDismiss = { inspecting = null }, Modifier.padding(horizontal = Space.md).heightIn(max = 360.dp))
            }
        }
        FlowRow(
            Modifier.fillMaxWidth().padding(horizontal = Space.md, vertical = Space.sm),
            horizontalArrangement = Arrangement.spacedBy(Space.sm, Alignment.CenterHorizontally), verticalArrangement = Arrangement.spacedBy(Space.xs),
        ) {
            PrimaryActionButton(if (playing) "Pause" else "Play", { if (index >= frames.lastIndex && !playing) at = 0; playing = !playing; inspecting = null }, Modifier.testTag("replay_play"))
            SecondaryActionButton(if (fast) "Speed 2x" else "Speed 1x", { fast = !fast }, Modifier.testTag("replay_speed"))
            SecondaryActionButton(if (short) "Full fight" else "Highlights", { switchShort(!short) }, Modifier.testTag("replay_mode"))
            SecondaryActionButton("Replay", { at = 0; playing = true; inspecting = null }, Modifier.testTag("replay_restart"))
            SecondaryActionButton("Skip to result", { playing = false; onReport() }, Modifier.testTag("replay_skip"))
        }
    }
}

/** One side of the field, up to four fighters, each a column of the same height so nothing jumps as numbers change. */
@Composable
private fun SideRow(
    label: String, actors: List<ReplayActor>, faces: Map<String, ReplayFace>, now: Map<String, ActorState>, labels: Map<String, Pop>,
    acting: String?, ruleHolder: String?, current: Pop?, frame: Frame, lane: Dp, reducedMotion: Boolean, towardBelow: Boolean, onInspect: (String) -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, color = CreamMuted, modifier = Modifier.padding(top = Space.xs))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.xs, Alignment.CenterHorizontally)) {
            actors.forEach { a ->
                ActorTile(
                    a, faces[a.key], now.getValue(a.key), labels[a.key], acting = a.key == acting && frame.kind != FrameKind.ROUND,
                    ruleSource = a.key == ruleHolder, bladeSource = a.key == ruleHolder && current?.weapon == true, struck = current?.target == a.key && current.kind == PopKind.DAMAGE, frame = frame,
                    lane = lane, reducedMotion = reducedMotion, towardBelow = towardBelow, onInspect = { onInspect(a.key) },
                    modifier = Modifier.weight(1f).widthIn(max = 120.dp),
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ActorTile(
    a: ReplayActor, face: ReplayFace?, s: ActorState, label: Pop?, acting: Boolean, ruleSource: Boolean, bladeSource: Boolean, struck: Boolean, frame: Frame,
    lane: Dp, reducedMotion: Boolean, towardBelow: Boolean, onInspect: () -> Unit, modifier: Modifier = Modifier,
) {
    // A restrained lunge toward the other line while the actor steps up, and a short shake on a blow; neither under reduced motion.
    val lunge by animateDpAsState(if (acting && frame.kind == FrameKind.INTRO && !reducedMotion) 6.dp else 0.dp, if (reducedMotion) snap<Dp>() else tween<Dp>(160), label = "lunge")
    val shake = remember { Animatable(0f) }
    LaunchedEffect(frame, struck) {
        if (struck && !reducedMotion) { shake.snapTo(0f); for (x in listOf(4f, -4f, 3f, -2f, 0f)) shake.animateTo(x, tween<Float>(40)) }
    }
    val share = if (a.maxHealth > 0) s.health.toFloat() / a.maxHealth else 0f
    val bar by animateFloatAsState(share, if (reducedMotion) snap<Float>() else tween<Float>(260), label = "health")
    // Whose rule is landing is ringed: gold for a blade's rule, ivory for any other; the one who moves is ringed in ember.
    val ring = when {
        bladeSource -> Gold
        ruleSource -> Cream
        acting -> Ember
        struck -> FlawRed
        else -> BronzeDeep
    }
    val stats = STAT_ORDER.mapNotNull { st -> s.stats[st]?.takeIf { it > 0 }?.let { st to it } }
    val said = buildString {
        append(a.name); append(", ")
        append(if (s.downed) "down" else "${s.health} of ${a.maxHealth} health")
        stats.forEach { (st, n) -> append(", ${Terms.stat(st)} $n") }
        if (acting) append(", acting")
        if (bladeSource) append(", their weapon's effect triggers") else if (ruleSource) append(", their effect triggers")
        label?.let { append(". ${it.text}${shortNote(it.note)?.let { n -> " $n" }.orEmpty()}") }
    }
    Column(
        modifier.clickable(onClickLabel = "Show ${a.name}", role = Role.Button, onClick = onInspect).semantics(mergeDescendants = true) { contentDescription = said }
            .testTag("replay_actor_${a.key}"),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // The lane: what just happened to this fighter, above the face, never over it.
        Box(Modifier.fillMaxWidth().height(lane), contentAlignment = Alignment.BottomCenter) {
            label?.let { PopPlate(it, fresh = it == frame.pop) }
        }
        Box(
            Modifier.padding(vertical = 6.dp).offset(x = shake.value.dp, y = if (towardBelow) lunge else -lunge).size(60.dp)
                .border(if (acting || ruleSource) 3.dp else 1.5.dp, ring, RoundedCornerShape(6.dp)).padding(3.dp)
                .alpha(if (s.downed) 0.45f else 1f),
            contentAlignment = Alignment.Center,
        ) {
            if (face?.res != null) {
                val bmp = ImageBitmap.imageResource(face.res)
                Image(
                    BitmapPainter(bmp, filterQuality = FilterQuality.Low), contentDescription = null, modifier = Modifier.fillMaxSize(),
                    colorFilter = if (s.downed) ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) }) else null,
                )
            } else {
                Box(Modifier.fillMaxSize().background(ForgePanelRaised, RoundedCornerShape(4.dp)), contentAlignment = Alignment.Center) {
                    Text(face?.initial ?: a.name.take(1), style = MaterialTheme.typography.titleLarge, color = CreamMuted)
                }
            }
        }
        Text(a.name, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center, color = if (acting) Gold else Cream)
        // Health: the bar and the number, or the word when down. Party green, besiegers red, and the number always says it.
        Box(Modifier.fillMaxWidth().padding(horizontal = 2.dp, vertical = 2.dp).height(8.dp).background(ForgeSlot, RoundedCornerShape(2.dp)).border(1.dp, BronzeDeep, RoundedCornerShape(2.dp))) {
            Box(Modifier.fillMaxHeight().fillMaxWidth(bar.coerceIn(0f, 1f)).background(if (a.side == Side.PARTY) BuffGreen else FlawRed, RoundedCornerShape(2.dp)))
        }
        Text(if (s.downed) "Down" else "${s.health}/${a.maxHealth}", style = MaterialTheme.typography.labelMedium, color = if (s.downed) FlawRed else Cream, maxLines = 1)
        // States, with their counts: a word and a number, never a colour alone. The row keeps its height when empty.
        FlowRow(
            Modifier.fillMaxWidth().heightIn(min = 22.dp).padding(top = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterHorizontally), verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            stats.forEach { (st, n) ->
                Text(
                    "${Terms.stat(st)} $n", style = MaterialTheme.typography.labelSmall, color = statColor(st), maxLines = 1,
                    modifier = Modifier.background(ForgePanel, RoundedCornerShape(3.dp)).border(1.dp, statColor(st).copy(alpha = 0.6f), RoundedCornerShape(3.dp)).padding(horizontal = 3.dp),
                )
            }
        }
    }
}

/** A pop: an opaque plate with a strong edge, the big line and the kind of it in words. The one landing now is raised. */
@Composable
private fun PopPlate(p: Pop, fresh: Boolean) {
    val color = popColor(p.kind)
    Column(
        Modifier.background(if (fresh) ForgePanelRaised else ForgePanel, RoundedCornerShape(4.dp)).border(if (fresh) 2.dp else 1.dp, color.copy(alpha = if (fresh) 1f else 0.55f), RoundedCornerShape(4.dp))
            .padding(horizontal = 4.dp, vertical = 2.dp).alpha(if (fresh) 1f else 0.8f),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(p.text, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = color, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
        shortNote(p.note)?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = Cream, textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis) }
    }
}

/**
 * What is happening now, between the two lines: who moves and with what, and the rule landing at this moment with whose
 * it is, a blade's rule named with its blade. Read out politely by a screen reader as it changes.
 */
@Composable
private fun NowBanner(replay: Replay, frame: Frame, modifier: Modifier = Modifier) {
    val step = replay.steps[frame.step]
    val pop = frame.pop
    val first = step.title
    val second = when {
        frame.kind == FrameKind.END -> null
        pop?.rule != null -> {
            val holder = replay.actor(pop.source)
            // A metal's or an element's effect is named after its source: what it does is said beside it.
            val named = step.uses.firstOrNull { it.holder == pop.source && it.name == pop.rule }?.let { CombatContent.label(it.ruleId, it.name) } ?: pop.rule
            if (pop.weapon) "Weapon · ${holder?.weapon ?: "weapon"}: $named" else listOfNotNull(holder?.name, named).joinToString(": ")
        }
        frame.kind == FrameKind.INTRO && step.uses.isNotEmpty() -> step.uses.joinToString(", ") { u -> CombatContent.label(u.ruleId, u.name).let { if (u.weapon) "Weapon: $it" else it } }
        step.kind == StepKind.TICK -> step.pops.firstNotNullOfOrNull { it.tick }?.let { s ->
            if (s == com.tinyblacksmith.core.combat.Stat.REGEN) "${Terms.stat(s)} heals" else "${Terms.stat(s)} deals damage"
        } ?: "A status takes effect"
        else -> null
    }
    Column(
        modifier.fillMaxWidth().heightIn(min = 64.dp).background(ForgePanel, RoundedCornerShape(4.dp)).border(1.dp, if (pop?.weapon == true) Gold else Bronze, RoundedCornerShape(4.dp))
            .padding(horizontal = Space.sm, vertical = Space.xs).semantics { liveRegion = LiveRegionMode.Polite }.testTag("replay_now"),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(first, style = MaterialTheme.typography.titleMedium, color = if (frame.kind == FrameKind.END) Gold else Cream)
        second?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = if (pop?.weapon == true) Gold else CreamMuted) }
    }
}

/** The last three steps played, newest first, each in one line of the record's own numbers. The whole log is on the report. */
@Composable
private fun RecentStrip(replay: Replay, frames: List<Frame>, index: Int, modifier: Modifier = Modifier) {
    val done = frames.take(index).map { it.step }.distinct().filter { it != frames[index].step }
        .map { replay.steps[it] }.filter { it.kind != StepKind.ROUND && it.kind != StepKind.END }.takeLast(3).asReversed()
    Column(modifier.fillMaxWidth().testTag("replay_recent"), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text("RECENT", style = MaterialTheme.typography.labelSmall, color = CreamMuted)
        if (done.isEmpty()) Text("Nothing yet.", style = MaterialTheme.typography.bodySmall, color = CreamMuted)
        done.forEachIndexed { i, s ->
            Text(ReplayTimeline.summary(replay, s), style = MaterialTheme.typography.bodySmall, color = if (i == 0) Cream else CreamMuted, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

/**
 * One fighter, opened by a tap (the playback pauses): health, the blade and every rule it brought with what each does,
 * and the states on them now with what each does by the fight's own numbers. A defender's blade and person open their
 * own sheets.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Inspector(
    a: ReplayActor, s: ActorState?, replay: Replay, content: ContentCatalog, rules: FightRules, state: GameState,
    onOpenHero: (HeroId) -> Unit, onOpenBlade: (WeaponId) -> Unit, onDismiss: () -> Unit, modifier: Modifier = Modifier,
) {
    val descriptions = remember(content) {
        content.combat?.let { c -> (c.allEffects + c.kits.flatMap { it.kit.passives } + c.units.flatMap { it.effects + it.kit.passives }).associate { it.id to it.description } }.orEmpty()
    }
    FramedPanel(modifier = modifier.fillMaxWidth().testTag("replay_inspector"), contentPadding = androidx.compose.foundation.layout.PaddingValues(Space.sm)) {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(Space.xs)) {
            Text(listOfNotNull(a.name, a.kit).joinToString(" · "), style = MaterialTheme.typography.titleMedium, color = Gold, modifier = Modifier.semantics { heading() })
            s?.let { Text(if (it.downed) "Down" else "Health ${it.health} of ${a.maxHealth}", style = MaterialTheme.typography.bodyMedium) }
            a.weapon?.let { Text("Weapon: $it", style = MaterialTheme.typography.bodyMedium) }
            a.weaponRules.forEach { id ->
                Text("${replay.rules[id] ?: id}: ${descriptions[id] ?: "an effect of this weapon."}", style = MaterialTheme.typography.bodySmall, color = CreamMuted)
            }
            val stats = STAT_ORDER.mapNotNull { st -> s?.stats?.get(st)?.takeIf { it > 0 }?.let { st to it } }
            if (stats.isNotEmpty()) {
                Text("Now on ${a.name}", style = MaterialTheme.typography.labelMedium, color = CreamMuted)
                stats.forEach { (st, n) -> Text("${Terms.stat(st)} $n: ${statMeaning(st, rules)}", style = MaterialTheme.typography.bodySmall) }
            } else if (s?.downed != true) Text("No statuses on ${a.name} now.", style = MaterialTheme.typography.bodySmall, color = CreamMuted)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.xs), verticalArrangement = Arrangement.spacedBy(Space.xs)) {
                val bladeId = a.weaponId?.let { WeaponId(it) }?.takeIf { state.weapons.containsKey(it) }
                if (a.side == Side.PARTY && bladeId != null) SecondaryActionButton("Weapon record", { onOpenBlade(bladeId) }, Modifier.testTag("replay_open_blade"))
                if (a.side == Side.PARTY && state.heroes.containsKey(HeroId(a.key))) SecondaryActionButton("Hero", { onOpenHero(HeroId(a.key)) }, Modifier.testTag("replay_open_hero"))
                InlineActionButton("Back to the fight", onDismiss, Modifier.testTag("replay_inspector_close"))
            }
        }
    }
}
