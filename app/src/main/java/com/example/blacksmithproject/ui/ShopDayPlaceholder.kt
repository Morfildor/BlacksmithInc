package com.example.blacksmithproject.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.blacksmithproject.Beat
import com.example.blacksmithproject.GameViewModel
import com.example.blacksmithproject.UiState
import com.example.blacksmithproject.data.ShopDaySpeed
import com.example.blacksmithproject.ui.theme.Space
import com.tinyblacksmith.core.shopday.Lines

/**
 * The shop day as plain text, until the counter screen (ui/shopday) replaces it: every card of the saved position, its
 * lines from [Lines], and the controls the real screen will have. It shows a day; it decides nothing.
 */
@Composable
fun ShopDayPlaceholder(s: UiState.ShopDay, vm: GameViewModel) {
    val content = vm.engine.content
    val script = s.script
    val beat = s.position.beat
    BackHandler(enabled = s.backIsConsumed) { vm.back() }
    Column(Modifier.fillMaxSize().safeDrawingPadding().padding(Space.md)) {
        Text("Day ${script.day}", style = MaterialTheme.typography.headlineSmall)
        if (s.resumed) {
            Text("Day ${script.day} is done and saved." + (script.ledger?.let { " The shop took ${it.income.values.sum()} gold." } ?: ""), modifier = Modifier.padding(vertical = Space.md).testTag("shopday_resume_text"))
            Button(onClick = vm::resumeDay, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("shopday_resume")) { Text("Resume the day") }
            OutlinedButton(onClick = vm::skipDay, modifier = Modifier.fillMaxWidth().padding(top = Space.sm).heightIn(min = 48.dp).testTag("shopday_skip")) { Text("Skip to tomorrow") }
            return@Column
        }
        Secondary("${beat.stage} · card ${s.position.at + 1} of ${s.position.beats.size} · $beat", Modifier.testTag("shopday_beat"))
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(vertical = Space.md), verticalArrangement = Arrangement.spacedBy(Space.sm)) {
            when (beat) {
                Beat.ShopOpens -> {
                    Text("The shop opens.", style = MaterialTheme.typography.titleMedium)
                    if (script.shelf.isEmpty()) Text("Nothing is on the shelf.")
                    script.shelf.forEach { blade -> Text(listOfNotNull(blade.name, script.prices[blade.weaponId]?.let { "$it gold" }).joinToString(", ")) }
                }
                is Beat.Visit -> script.featured[beat.visit].let { visit ->
                    Text(Lines.customer(visit, content), style = MaterialTheme.typography.titleMedium)
                    visit.considered.forEach { Secondary(Lines.considered(it, script)) }
                    Text(Lines.decision(visit, script, content))
                }
                Beat.Tally -> {
                    Text("Others came by.", style = MaterialTheme.typography.titleMedium)
                    script.tally.forEach { group -> Text(Lines.tally(group, script) + ": " + group.visits.joinToString(", ") { it.heroName }) }
                }
                Beat.ShopCloses -> {
                    Text("The shop closes.", style = MaterialTheme.typography.titleMedium)
                    script.ledger?.let { l ->
                        Text("Took ${l.income.values.sum()} gold. The till holds ${l.goldAtClose} gold.")
                        l.income.filterValues { it != 0 }.forEach { (kind, gold) -> Secondary("${kind.name.lowercase().replace('_', ' ')}: $gold gold") }
                    }
                }
                Beat.Quiet -> script.quiet?.let { Text(Lines.quiet(it), style = MaterialTheme.typography.titleMedium) }
                is Beat.Aftermath -> {
                    Text(Lines.aftermath(script.aftermath[beat.card], content))
                    if (script.moreInGazette > 0) Secondary("${script.moreInGazette} more in the Gazette")
                }
                Beat.Fallen -> Text("The forge has fallen on day ${script.day}.", style = MaterialTheme.typography.titleLarge)
                Beat.Blessing -> {
                    Text("The town offers a blessing", style = MaterialTheme.typography.titleMedium)
                    s.state.pendingBlessingOffer.forEach { id ->
                        val b = content.blessing(id)
                        OutlinedButton(onClick = { vm.chooseBlessing(id) }, enabled = !s.busy, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("blessing_${id.value}")) {
                            Column(Modifier.weight(1f)) {
                                Text(b.name, style = MaterialTheme.typography.titleSmall)
                                Text(b.description, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                    TextButton(onClick = vm::decideLater, modifier = Modifier.testTag("blessing_later")) { Text("Decide later") }
                }
                Beat.Tomorrow -> {
                    Text("Day ${s.state.day}", style = MaterialTheme.typography.titleLarge)
                    Secondary("${s.state.gold} gold")
                    script.lead?.let { lead ->
                        val line = Lines.lead(lead, s.state, content, vm.engine.config)
                        Text(line.action, style = MaterialTheme.typography.titleMedium)
                        line.reason?.let { Text(it) }
                    }
                }
            }
            s.lastError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
        if (s.position.isLast) {
            Button(onClick = vm::acknowledge, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("shopday_close")) {
                Text(if (beat == Beat.Fallen) "See the legacy" else "Begin day ${s.state.day}", style = MaterialTheme.typography.titleMedium)
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
            OutlinedButton(onClick = { vm.back() }, enabled = !s.position.isFirst, modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("shopday_back")) { Text("Back") }
            Button(onClick = vm::next, enabled = !s.position.isLast && beat != Beat.Blessing, modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("shopday_next")) { Text("Next") }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
            TextButton(onClick = vm::skipDay, enabled = s.position.at < s.position.ending, modifier = Modifier.heightIn(min = 48.dp).testTag("shopday_skip")) { Text("Skip day") }
            TextButton(onClick = vm::openGazette, modifier = Modifier.heightIn(min = 48.dp).testTag("shopday_gazette")) { Text("Read the Gazette") }
            TextButton(onClick = { vm.setSpeed(ShopDaySpeed.entries[(s.speed.ordinal + 1) % ShopDaySpeed.entries.size]) }, modifier = Modifier.heightIn(min = 48.dp).testTag("shopday_speed")) { Text("Speed: ${s.speed.name}") }
        }
    }
    if (s.gazetteOpen) s.state.lastResolution?.let { day ->
        val reducedMotion by vm.settings.reducedMotion.collectAsStateWithLifecycle(initialValue = false)
        DayReportDialog(s.state, day, vm, reducedMotion)
    }
}
