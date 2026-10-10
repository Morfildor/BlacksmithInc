package com.example.blacksmithproject.ui.shopday

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.blacksmithproject.GameViewModel
import com.example.blacksmithproject.Sheet
import com.example.blacksmithproject.UiState
import com.example.blacksmithproject.ui.DayReportDialog
import com.example.blacksmithproject.ui.ErrorDialog
import com.example.blacksmithproject.ui.Tips
import com.example.blacksmithproject.ui.detail.HeroDetailSheet
import com.example.blacksmithproject.ui.detail.ItemDetailSheet
import com.example.blacksmithproject.ui.detail.customerSnapshot
import com.example.blacksmithproject.ui.detail.heroDetail
import com.example.blacksmithproject.ui.detail.itemDetail
import com.example.blacksmithproject.ui.detail.weaponSnapshot
import com.tinyblacksmith.core.model.HeroId
import com.tinyblacksmith.core.model.ReplayKind
import com.example.blacksmithproject.Beat as Card

/**
 * The shop day bound to the session: the saved position picks the card, every tap is a ViewModel event, and the sheets,
 * the Gazette and a fight replay open over it. It shows a resolved day; it decides nothing and draws nothing.
 */
@Composable
fun ShopDayHost(s: UiState.ShopDay, vm: GameViewModel, onMainMenu: () -> Unit = {}) {
    val engine = vm.engine
    val reducedMotion by vm.settings.reducedMotion.collectAsStateWithLifecycle(initialValue = false)
    val model = s.model
    // A fight being watched is the screen's own state, like the motion inside a card: null, or the replay's event ID ("" = the siege).
    var watching by rememberSaveable(s.script.day) { mutableStateOf<String?>(null) }
    val day = s.state.lastResolution
    // The controls are explained once (GDD 3.3 onboarding): under the first customer the player ever watches, and the
    // line counts as seen when they move on from that card. Seen until settings have loaded, so it never flashes.
    val seenTips by vm.settings.seenTips.collectAsStateWithLifecycle(initialValue = Tips.ALL)
    val coaching = Tips.COUNTER.id !in seenTips
    val atFirstVisit = !s.resumed && s.position.at == model.beats.indexOfFirst { it is Beat.Visit }
    var coached by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(coaching, atFirstVisit) { if (coaching && atFirstVisit) coached = true else if (coaching && coached) vm.dismissTip(Tips.COUNTER.id) }
    val replay = watching?.let { id -> day?.replays?.firstOrNull { if (id.isEmpty()) it.kind == ReplayKind.SIEGE else it.eventId == id } }

    // Back closes what is open, else steps back one card. On the first card and on the Resume prompt it opens the main
    // menu: the day stays unwatched at the card it was left on, and "Continue run" comes back to it.
    BackHandler { if (replay != null) watching = null else if (!vm.back()) onMainMenu() }

    if (s.resumed) {
        Box(Modifier.fillMaxSize().safeDrawingPadding(), contentAlignment = Alignment.Center) {
            ResumePrompt(model.day, model.took, onResume = vm::resumeDay, onSkip = vm::skipDay)
        }
        return
    }
    ShopDayScreen(
        model = model, position = s.position.at, speed = s.speed, reducedMotion = reducedMotion,
        paused = s.sheet != null || s.gazetteOpen || replay != null || s.busy,
        onNext = { if (s.position.beat == Card.Blessing) vm.decideLater() else vm.next() },
        onBack = { vm.back() },
        onSkipDay = vm::skipDay,
        onSpeedChange = { vm.setSpeed(it) },
        onOpenHero = { id, _ -> vm.openSheet(Sheet.Hero(id)) },
        onOpenBlade = { id, _ -> vm.openSheet(Sheet.Item(id)) },
        onChooseBlessing = { if (!s.busy) vm.chooseBlessing(it) },
        onOpenGazette = vm::openGazette,
        onWatchFight = { watching = it ?: "" },
        onClose = vm::acknowledge,
        coach = Tips.COUNTER.body.takeIf { coaching && atFirstVisit },
    )
    // Read-only here: the day is over and tomorrow's stock is planned on the Shop screen. The counter snapshot comes first.
    val openHero = { id: HeroId -> vm.openSheet(Sheet.Hero(id)) }
    when (val sheet = s.sheet) {
        is Sheet.Hero -> {
            val detail = remember(s.state, sheet) { engine.heroDetail(s.state, sheet.id, day?.customerSnapshot(sheet.id)) }
            if (detail == null) LaunchedEffect(sheet) { vm.closeSheet() }
            else HeroDetailSheet(detail, openHero, onOpenItem = { vm.openSheet(Sheet.Item(it)) }, onDismiss = vm::closeSheet)
        }
        is Sheet.Item -> {
            val detail = remember(s.state, sheet) { engine.itemDetail(s.state, sheet.id, day?.weaponSnapshot(sheet.id)) }
            if (detail == null) LaunchedEffect(sheet) { vm.closeSheet() }
            else ItemDetailSheet(detail, planning = false, openHero, onStock = {}, onDismiss = vm::closeSheet, enabled = false)
        }
        null -> Unit
    }
    if (s.gazetteOpen && day != null) DayReportDialog(s.state, day, vm, reducedMotion)
    replay?.let { ReplayOverlay(it, onClose = { watching = null }) }
    s.lastError?.let { ErrorDialog(it, vm::dismissError) }
}
