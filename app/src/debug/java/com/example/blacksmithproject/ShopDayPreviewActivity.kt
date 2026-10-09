package com.example.blacksmithproject

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import com.example.blacksmithproject.ui.shopday.Beat
import com.example.blacksmithproject.ui.shopday.ReplayOverlay
import com.example.blacksmithproject.ui.shopday.ResumePrompt
import com.example.blacksmithproject.ui.shopday.ShopDayScreen
import com.example.blacksmithproject.data.ShopDaySpeed
import com.example.blacksmithproject.ui.shopday.ShopDayUiModel
import com.example.blacksmithproject.ui.shopday.toUi
import com.example.blacksmithproject.ui.theme.BlacksmithProjectTheme
import com.tinyblacksmith.core.engine.Command
import com.tinyblacksmith.core.engine.CommandOutcome
import com.tinyblacksmith.core.model.CombatReplay
import com.tinyblacksmith.core.model.GameState
import com.tinyblacksmith.core.model.ReplayKind
import com.tinyblacksmith.core.model.VisitKind
import com.tinyblacksmith.core.shopday.ShopDay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Debug builds only. Shows the shop-day screen over a day the real engine resolved, so every card can be looked at on
 * a device before the session side is wired in.
 *
 *     adb shell am start -n com.example.blacksmithproject/.ShopDayPreviewActivity --es case purchase
 *
 * `case` is one of [ShopDayFixtures.cases] (default `purchase`); the screen opens on the beat the case is about.
 * `--es at open|visit|tally|close|aftermath|ending|resume` or `--ei pos N` picks another beat, `--ez reduced true`
 * turns reduced motion on, `--es speed x1|x2` starts with a timer. The hero, blade and Gazette sheets are stand-ins.
 */
class ShopDayPreviewActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val case = intent.getStringExtra("case") ?: "purchase"
        val at = intent.getStringExtra("at")
        val pos = intent.getIntExtra("pos", -1)
        val reduced = intent.getBooleanExtra("reduced", false)
        val startSpeed = ShopDaySpeed.entries.firstOrNull { it.name.equals(intent.getStringExtra("speed"), ignoreCase = true) } ?: ShopDaySpeed.TAP
        setContent {
            BlacksmithProjectTheme {
                Surface(Modifier.fillMaxSize().semantics { testTagsAsResourceId = true }) {
                    val day by produceState<Pair<Boolean, ShopDayFixtures.Day?>>(false to null) { value = true to withContext(Dispatchers.Default) { ShopDayFixtures.find(case) } }
                    val found = day.second
                    if (found == null) {
                        Box(Modifier.fillMaxSize().safeDrawingPadding(), contentAlignment = Alignment.Center) {
                            Text(if (day.first) "The engine produced no '$case' day on seeds 42 to 53." else "Playing the days...", modifier = Modifier.testTag("preview_status"))
                        }
                        return@Surface
                    }
                    var state by remember { mutableStateOf(found.state) }
                    val model = remember(state) { ShopDay.script(found.resolution, state, found.engine.content, found.engine.config).toUi(state, found.engine.content, found.engine.config) }
                    var position by remember { mutableIntStateOf(if (pos >= 0) pos else start(model, case, at)) }
                    var speed by remember { mutableStateOf(startSpeed) }
                    var sheet by remember { mutableStateOf<String?>(null) }
                    var replay by remember { mutableStateOf<CombatReplay?>(null) }
                    var resume by remember { mutableStateOf(at == "resume") }
                    if (resume) {
                        Box(Modifier.fillMaxSize().safeDrawingPadding(), contentAlignment = Alignment.Center) {
                            ResumePrompt(model.day, model.took, onResume = { resume = false }, onSkip = { resume = false; position = model.endingIndex })
                        }
                        return@Surface
                    }
                    ShopDayScreen(
                        model = model, position = position, speed = speed, reducedMotion = reduced, paused = sheet != null || replay != null,
                        onNext = { position = (position + 1).coerceAtMost(model.beats.lastIndex) },
                        onBack = { position = (position - 1).coerceAtLeast(0) },
                        onSkipDay = { position = model.endingIndex },
                        onSpeedChange = { speed = it },
                        onOpenHero = { id, snapshot -> sheet = "Hero sheet: ${snapshot?.name ?: state.heroes[id]?.fullName ?: id.value}" },
                        onOpenBlade = { id, snapshot -> sheet = "Blade sheet: ${snapshot?.name ?: id.value}" },
                        onChooseBlessing = { id -> (found.engine.handle(state, Command.ChooseBlessing(id)) as? CommandOutcome.Accepted)?.let { state = it.state } },
                        onOpenGazette = { sheet = "The Gazette of day ${model.day}" },
                        onWatchFight = { eventId -> replay = found.resolution.replays.firstOrNull { if (eventId == null) it.kind == ReplayKind.SIEGE else it.eventId == eventId } },
                        onClose = { sheet = "Closed: the session would move on from day ${model.day}" },
                    )
                    sheet?.let { text ->
                        AlertDialog(
                            onDismissRequest = { sheet = null }, title = { Text(text) },
                            text = { Text("A stand-in for the preview. The real sheet is another task's.", style = MaterialTheme.typography.bodyMedium) },
                            confirmButton = { TextButton(onClick = { sheet = null }) { Text("Close") } },
                        )
                    }
                    replay?.let { ReplayOverlay(it, onClose = { replay = null }) }
                }
            }
        }
    }

    /** The beat a case is about: the sale of `purchase`, the refusal of `refusal`, the tally of `busy`, and so on. */
    private fun start(model: ShopDayUiModel, case: String, at: String?): Int {
        fun first(match: (Beat) -> Boolean) = model.beats.indexOfFirst(match).coerceAtLeast(0)
        return when (at ?: case) {
            "open" -> 0
            "visit" -> first { it is Beat.Visit }
            "purchase" -> first { it is Beat.Visit && it.visit.sold && it.visit.kind == VisitKind.BROWSE && it.visit.receipt.size <= 2 }
            "tradein" -> first { it is Beat.Visit && it.visit.receipt.any { r -> r.label.startsWith("Trade-in") } }
            "refusal", "notbetter" -> first { it is Beat.Visit && !it.visit.sold }
            "commission" -> first { it is Beat.Visit && it.visit.kind == VisitKind.COMMISSION }
            "busy", "tally" -> first { it is Beat.Tally }
            "close" -> first { it is Beat.Close }
            "siege", "aftermath" -> first { it is Beat.Aftermath }
            "blessing", "fallen", "ending" -> model.endingIndex
            "tomorrow" -> model.beats.lastIndex
            else -> if (case.startsWith("aftermath_")) first { it is Beat.Aftermath && "aftermath_${it.card.kind.name.lowercase()}" == case } else 0
        }
    }
}
