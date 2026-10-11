package com.example.blacksmithproject.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.unit.dp
import com.example.blacksmithproject.ui.theme.BronzeDeep
import com.example.blacksmithproject.ui.theme.Cream
import com.example.blacksmithproject.ui.theme.ForgeSlot
import com.example.blacksmithproject.ui.theme.Gold
import com.example.blacksmithproject.ui.theme.Space
import com.tinyblacksmith.core.engine.GameEngine
import com.tinyblacksmith.core.model.GameState
import com.tinyblacksmith.core.model.HeroId

/** One who signs with a package: who they are and the defining trait it gives them, in the catalog's words. */
@Immutable data class OpeningSignerUi(val heroId: HeroId, val portrait: Int, val name: String, val sub: String, val trait: String)

/** One opening package, whole: everything that decides the choice is on its card. [brings] is its stock and its weapon, a line each. */
@Immutable
data class OpeningUi(
    val id: String, val name: String, val build: String, val pitch: String, val signers: List<OpeningSignerUi>,
    val relic: String, val relicDescription: String, val brings: List<String>, val drawback: String, val plan: String,
)

/** The first choice of a guild run. Present only while the save still holds the offer (`GuildRunState.openingOffer`). */
@Immutable data class OpeningChooserUi(val charter: String, val openings: List<OpeningUi>)

/** The packages offered on the first morning of [state]; null on a classic run and once one is taken. Pure: reads only. */
fun GameEngine.openingChooser(state: GameState): OpeningChooserUi? {
    val g = state.guild ?: return null
    val cat = content.guild ?: return null
    if (g.openingOffer.isEmpty()) return null
    return OpeningChooserUi(
        charter = cat.charter(g.charterId)?.name.orEmpty(),
        openings = g.openingOffer.mapNotNull { offer ->
            val def = cat.opening(offer.defId) ?: return@mapNotNull null
            val relic = content.relic(def.relicId)
            OpeningUi(
                id = def.id, name = def.name, build = def.build, pitch = def.pitch,
                signers = offer.heroIds.mapIndexedNotNull { i, id ->
                    val h = state.heroes[id] ?: return@mapIndexedNotNull null
                    val trait = offer.traitIds.getOrNull(i)?.let(cat::trait)
                    OpeningSignerUi(h.id, Sprites.portrait(h, small = true), h.fullName, "${content.heroClass(h.classId).name} level ${h.level}", trait?.let { "${it.name}: ${it.description}" } ?: "No trait of note")
                },
                relic = relic?.name ?: def.relicId, relicDescription = relic?.description.orEmpty(),
                brings = listOfNotNull(
                    def.materials.entries.joinToString(", ") { "${it.value} ${content.material(it.key).name}" }.ifEmpty { null },
                    def.weapon?.let { w ->
                        val flaw = content.affixById[w.flaw]
                        "${content.material(w.coreId).name} ${content.family(w.familyId).name.lowercase()} with ${content.material(w.augmentId).name}, quality ${w.quality}. " +
                            (flaw?.let { "Flaw, ${it.name}: ${it.description}" } ?: "It has a flaw.")
                    },
                ),
                drawback = def.drawback, plan = def.plan,
            )
        },
    )
}

/**
 * The first morning of a guild run, in place of the workshop until one package is taken: End Day waits for this choice, so
 * nothing else is offered beside it. Three cards in one scrolling column. A tap asks once more, then sends the command.
 * Nothing here is kept but which card is being asked about: the offer itself is in the save.
 */
@Composable
fun OpeningChooser(ui: OpeningChooserUi, enabled: Boolean, onChoose: (String) -> Unit, modifier: Modifier = Modifier) {
    var asking by rememberSaveable { mutableStateOf<String?>(null) }
    Column(modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(horizontal = Space.md, vertical = Space.md).testTag("opening_chooser")) {
        Text("How the guild begins", style = MaterialTheme.typography.headlineMedium, color = Gold, modifier = Modifier.semantics { heading() })
        if (ui.charter.isNotEmpty()) Text(ui.charter, style = MaterialTheme.typography.titleSmall, color = Cream)
        Secondary("Choose one of these. It stays for the whole run, and the day cannot end before you choose.", Modifier.padding(top = Space.xs))
        ui.openings.forEach { o ->
            FramedPanel(modifier = Modifier.fillMaxWidth().padding(top = Space.md).testTag("opening_${o.id}")) {
                Text(o.name, style = MaterialTheme.typography.titleLarge, color = Gold, modifier = Modifier.semantics { heading() })
                Text("Build: ${o.build}", style = MaterialTheme.typography.labelLarge, color = Cream)
                Text(o.pitch, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = Space.xs))
                SectionHeader("Who signs")
                o.signers.forEach { m ->
                    Row(Modifier.fillMaxWidth().padding(top = Space.xs).semantics(mergeDescendants = true) {}, verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Box(Modifier.background(ForgeSlot).border(1.dp, BronzeDeep)) { PixelImage(m.portrait, 44.dp, description = null) }
                        Column(Modifier.weight(1f)) {
                            Text(m.name, style = MaterialTheme.typography.titleSmall)
                            Secondary(m.sub)
                            Text(m.trait, style = MaterialTheme.typography.bodySmall, color = Cream)
                        }
                    }
                }
                SectionHeader("It brings")
                EffectRow(EffectKind.NEUTRAL, "Relic · ${o.relic}", o.relicDescription)
                o.brings.forEach { Text("${EffectKind.BUFF.sign} $it", style = MaterialTheme.typography.bodySmall, color = EffectKind.BUFF.color, modifier = Modifier.padding(top = Space.xs)) }
                Text("${EffectKind.FLAW.sign} ${o.drawback}", style = MaterialTheme.typography.bodySmall, color = EffectKind.FLAW.color, modifier = Modifier.padding(top = Space.xs).testTag("opening_drawback_${o.id}"))
                SectionHeader("The first days")
                Secondary(o.plan)
                PrimaryActionButton("Begin with ${o.name}", { asking = o.id }, Modifier.fillMaxWidth().padding(top = Space.md).testTag("opening_choose_${o.id}"), enabled = enabled)
            }
        }
    }
    ui.openings.firstOrNull { it.id == asking }?.let { o ->
        AlertDialog(
            modifier = Modifier.semantics { testTagsAsResourceId = true }.testTag("opening_confirm"),
            onDismissRequest = { asking = null },
            title = { Text("Begin with ${o.name}?") },
            text = { Text("${o.signers.joinToString(" and ") { it.name }} sign today and ${o.relic} goes up in the workshop. ${o.drawback} This cannot be taken back.") },
            confirmButton = { InlineActionButton("Begin", { asking = null; onChoose(o.id) }, Modifier.testTag("opening_confirm_yes"), enabled = enabled) },
            dismissButton = { InlineActionButton("Not yet", { asking = null }, Modifier.testTag("opening_confirm_no")) },
        )
    }
}
