package com.example.blacksmithproject.ui.shopday

import android.view.accessibility.AccessibilityManager
import com.example.blacksmithproject.data.ShopDaySpeed
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.coerceAtLeast
import androidx.compose.ui.unit.coerceIn
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import com.example.blacksmithproject.R
import com.tinyblacksmith.core.shopday.AftermathKind
import com.example.blacksmithproject.ui.Sprites
import com.example.blacksmithproject.ui.FramedPanel
import com.example.blacksmithproject.ui.LocalHaptics
import com.example.blacksmithproject.ui.Moment
import com.example.blacksmithproject.ui.theme.Space
import com.tinyblacksmith.core.model.BlessingId
import com.tinyblacksmith.core.model.CustomerSnapshot
import com.tinyblacksmith.core.model.HeroId
import com.tinyblacksmith.core.model.WeaponId
import com.tinyblacksmith.core.model.WeaponSnapshot
import kotlinx.coroutines.delay

/**
 * The shop day, one beat at a time: the counter with real customers, the tally, the till, what happened beyond the
 * door, then tomorrow's lead (or the blessing, or the fall). Stateless: [position] indexes `model.beats` and every
 * change is an event. A beat is whole on its first frame; motion is decoration and never a gate.
 *
 * Nothing advances by itself at [ShopDaySpeed.TAP], with [reducedMotion], while [paused] (a sheet, the Gazette or a
 * replay is open), with TalkBack exploring, or while the activity is not resumed. A tap anywhere is Next, except on
 * the cards that end the day, where only the wide button acts (Begin day, See the legacy, Decide later); Back steps
 * one beat and never closes the day; Skip day is one tap. [onWatchFight] passes the replay's event ID, null for the siege.
 * [coach] is the first-run line that says so, shown above the controls while the caller passes it.
 */
@Composable
fun ShopDayScreen(
    model: ShopDayUiModel,
    position: Int,
    speed: ShopDaySpeed,
    reducedMotion: Boolean,
    paused: Boolean,
    onNext: () -> Unit,
    onBack: () -> Unit,
    onSkipDay: () -> Unit,
    onSpeedChange: (ShopDaySpeed) -> Unit,
    onOpenHero: (HeroId, CustomerSnapshot?) -> Unit,
    onOpenBlade: (WeaponId, WeaponSnapshot?) -> Unit,
    onChooseBlessing: (BlessingId) -> Unit,
    onOpenGazette: () -> Unit,
    onWatchFight: (String?) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    coach: String? = null,
) {
    val at = position.coerceIn(0, model.beats.lastIndex)
    val beat = model.beats[at]
    val next by rememberUpdatedState(onNext)
    val ending by rememberUpdatedState(beat.isEnding)

    val haptics = LocalHaptics.current
    LaunchedEffect(at) { if (beat is Beat.Visit && beat.visit.sold) haptics.play(Moment.SALE) }

    // The opt-in timer. Keyed on everything that stops it, so a pause restarts the beat's wait instead of resuming it.
    val resumed = LocalLifecycleOwner.current.lifecycle.currentStateAsState().value.isAtLeast(Lifecycle.State.RESUMED)
    val context = LocalContext.current
    val exploring = remember(at) { context.getSystemService(AccessibilityManager::class.java)?.isTouchExplorationEnabled == true }
    val runs = speed != ShopDaySpeed.TAP && !reducedMotion && !paused && resumed && !exploring && beat.millis > 0
    LaunchedEffect(at, speed, runs) {
        if (runs) { delay((beat.millis / speed.divisor).toLong()); next() }
    }

    val openHero: (FaceUi) -> Unit = { face -> face.heroId?.let { onOpenHero(it, face.snapshot) } }
    val openBlade: (WeaponSnapshot) -> Unit = { onOpenBlade(it.weaponId, it) }

    BoxWithConstraints(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        // A low screen or large text: the scene gives height to the card, so the outcome and the receipt stay in view.
        val short = maxHeight < 700.dp || LocalDensity.current.fontScale > 1.15f
        // The evening has one card and the whole screen for it: the dawn grows with the room a tall screen leaves.
        val dawn = if (short) 112.dp else (maxHeight * 0.26f).coerceIn(112.dp, 232.dp)
        // Text above 1.3: the strip and the plate are already twice their height, so the painted room gives up most of its own.
        val large = LocalDensity.current.fontScale > 1.3f
        val low = maxHeight < 700.dp
        Column(Modifier.fillMaxSize().statusBarsPadding().pointerInput(Unit) { detectTapGestures { if (!ending) next() } }) {
            ShopDayTopBar(model.day, beat.progress, speed, reducedMotion, canSkip = at < model.endingIndex, onSpeedChange = onSpeedChange, onSkipDay = onSkipDay)
            when (beat) {
                is Beat.Open, is Beat.Visit, is Beat.Tally, is Beat.Close, is Beat.Quiet -> {
                    val visit = (beat as? Beat.Visit)?.visit
                    // With nobody at the counter the plate says what is left on the shelf; the part of the day is on the strip above.
                    val left = model.shelf.count { it.blade.weaponId !in beat.gone }
                    CounterScene(
                        plate = visit?.face?.name ?: when (left) { 0 -> if (model.shelf.isEmpty()) "Nobody at the counter" else "Every weapon is sold"; 1 -> "1 weapon on the shelf"; else -> "$left weapons on the shelf" }, detail = visit?.detail, customer = visit?.face, customerKey = visit?.seq,
                        reducedMotion = reducedMotion, onOpenHero = openHero, backdropHeight = if (large) 56.dp else if (short) 104.dp else 140.dp,
                    )
                    ShelfBand(
                        model.shelf, gone = beat.gone, looking = visit?.lookedIds ?: emptySet(), sold = visit?.purchased?.weaponId?.takeIf { visit.sold },
                        reducedMotion = reducedMotion, onOpenBlade = { onOpenBlade(it.blade.weaponId, it.blade) },
                    )
                }
                is Beat.Fallen -> StageBanner("The forge has fallen", R.drawable.art_day_fallen_forge, vignette = true)
                is Beat.Aftermath ->
                    // A siege is not one more thing beyond the door: its own name, and a stage half as tall again.
                    when (val siege = beat.card.siege) {
                        null -> StageBanner("Beyond the door", R.drawable.bg_title_workshop_night)
                        else -> StageBanner("Siege · day ${siege.day}", if (siege.held) R.drawable.art_day_siege_victory else Sprites.siegeWall(damaged = true), height = if (short) 112.dp else 168.dp, vignette = true)
                    }
                is Beat.Blessing -> StageBanner("The town's thanks", R.drawable.bg_title_workshop_night)
                // Still the evening of the day just watched (the strip says so); the next day is named once here and once on its button.
                is Beat.Tomorrow -> StageBanner("Tomorrow: day ${beat.day}", R.drawable.art_day_new_dawn, height = dawn, vignette = true)
            }
            key(at) {
                val scroll = rememberScrollState()
                BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                    // The room under the stage, less this column's and the frame's padding: the evening card is at least that tall.
                    val room = (maxHeight - 12.dp - Space.md * 3).coerceAtLeast(0.dp)
                    Column(
                        Modifier.fillMaxSize().verticalScroll(scroll).padding(start = Space.md, end = Space.md, top = 12.dp, bottom = Space.md)
                            .testTag("shopday_card").semantics { liveRegion = LiveRegionMode.Polite },
                    ) {
                        FramedPanel(modifier = Modifier.fillMaxWidth()) {
                            when (beat) {
                                is Beat.Open -> ShopOpenCard(beat)
                                is Beat.Visit -> VisitCard(beat.visit, openBlade)
                                is Beat.Tally -> TallyCard(beat, openHero)
                                is Beat.Close -> ShopCloseCard(beat)
                                is Beat.Quiet -> QuietDayCard(beat, openHero)
                                is Beat.Aftermath ->
                                    if (beat.card.siege != null) SiegeOutcomeCard(beat, beat.card.siege, openHero, onWatchFight = { onWatchFight(it.eventId) })
                                    else AftermathCard(beat, openHero, openBlade, onWatchFight = { onWatchFight(it.eventId) }, onOpenGazette = onOpenGazette)
                                is Beat.Blessing -> BlessingChoices(beat.choices, onChooseBlessing, siege = beat.siege)
                                is Beat.Tomorrow -> TomorrowCard(beat, onOpenGazette = onOpenGazette, modifier = Modifier.heightIn(min = room))
                                is Beat.Fallen -> FallenCard(beat, onOpenGazette = onOpenGazette)
                            }
                        }
                    }
                    // A card that runs on under the controls is cut on purpose: it fades out there, and the fade goes once its end is in view.
                    if (scroll.canScrollForward) Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(Space.lg).background(Brush.verticalGradient(listOf(Color.Transparent, MaterialTheme.colorScheme.background))))
                }
            }
            // Not on a low screen with large text: there the lines of the hint would leave the card no room at all.
            // The first-run line is an aside, not a control: a muted italic under a small label, and a tap on it is a tap anywhere.
            coach?.takeIf { !(large && low) }?.let {
                Row(Modifier.fillMaxWidth().padding(horizontal = Space.md).padding(top = Space.xs), horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                    Text("TIP", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 2.dp))
                    Text(it, style = MaterialTheme.typography.bodySmall, fontStyle = FontStyle.Italic, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f).testTag("shopday_coach"))
                }
            }
            // One wide button is always the way forward; on the last cards it is the choice that ends the day.
            ShopDayControls(
                canBack = at > 0,
                nextLabel = when (beat) {
                    is Beat.Tomorrow -> "Begin day ${beat.day}"
                    is Beat.Fallen -> "See the legacy"
                    is Beat.Blessing -> "Decide later"
                    is Beat.Close, is Beat.Quiet -> "Continue"
                    else -> "Next"
                },
                onBack = onBack, onNext = if (beat is Beat.Tomorrow || beat is Beat.Fallen) onClose else onNext, modifier = Modifier.navigationBarsPadding(),
                nextTag = when (beat) { is Beat.Tomorrow, is Beat.Fallen -> "shopday_close"; is Beat.Blessing -> "shopday_later"; else -> "shopday_next" },
            )
        }
    }
}
