package com.example.blacksmithproject.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.blacksmithproject.ui.theme.BronzeDeep
import com.example.blacksmithproject.ui.theme.Cream
import com.example.blacksmithproject.ui.theme.CreamMuted
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
 * nothing else is offered beside it. Three selectable cards, each short enough to compare with the next, every one
 * showing its cost before anything is committed. The chosen card opens to who signs, what it brings and the first days.
 * One pinned action, "Begin with X", sends the command: no second question. Only which card is marked is kept here; the
 * offer itself is in the save. [firstGame] is the note a player who has the guided first day ahead of them reads first.
 */
@Composable
fun OpeningChooser(ui: OpeningChooserUi, enabled: Boolean, onChoose: (String) -> Unit, modifier: Modifier = Modifier, firstGame: (@Composable () -> Unit)? = null) {
    var marked by rememberSaveable { mutableStateOf<String?>(null) }
    val chosen = ui.openings.firstOrNull { it.id == marked }
    Column(modifier.fillMaxSize().safeDrawingPadding().testTag("opening_chooser")) {
        val scroll = rememberScrollState()
        Column(Modifier.weight(1f).verticalScroll(scroll).bottomFade(scroll.canScrollForward).padding(horizontal = Space.md, vertical = Space.md), verticalArrangement = Arrangement.spacedBy(Space.sm)) {
            Text("How the guild begins", style = MaterialTheme.typography.headlineMedium, color = Gold, modifier = Modifier.semantics { heading() })
            Text(
                if (ui.charter.isNotEmpty()) "${ui.charter} · pick one of three starts. It stays for the whole run." else "Pick one of three starts. It stays for the whole run.",
                style = MaterialTheme.typography.bodyMedium, color = Cream,
            )
            firstGame?.invoke()
            ui.openings.forEach { o -> OpeningCard(o, o.id == marked, enabled, onMark = { marked = o.id }) }
        }
        PrimaryActionButton(
            chosen?.let { "Begin with ${it.name}" } ?: "Pick a start above", { chosen?.let { onChoose(it.id) } },
            Modifier.fillMaxWidth().padding(horizontal = Space.md, vertical = Space.sm).testTag("opening_begin"), enabled = enabled && chosen != null,
        )
    }
}

/** One package as a selectable card: name, build, who signs and its cost always; the detail once it is the chosen card. */
@Composable
private fun OpeningCard(o: OpeningUi, chosen: Boolean, enabled: Boolean, onMark: () -> Unit) {
    var plan by rememberSaveable(o.id) { mutableStateOf(false) }
    ChoiceRow(chosen, onMark, Modifier.testTag("opening_choose_${o.id}"), enabled = enabled) {
        Text(o.name, style = MaterialTheme.typography.titleMedium, color = if (chosen) Gold else Cream, modifier = Modifier.semantics { heading() })
        Text("${o.build}: ${o.pitch}", style = MaterialTheme.typography.bodySmall, color = CreamMuted)
        Text("Signs: ${o.signers.joinToString(", ") { "${it.name} (${it.sub})" }}", style = MaterialTheme.typography.bodySmall, color = Cream, modifier = Modifier.padding(top = Space.xs))
        Text("Relic: ${o.relic}", style = MaterialTheme.typography.bodySmall, color = Cream)
        // The cost of the choice is read before it is made, in body text as the gains are: never behind the selection.
        Text("${EffectKind.FLAW.sign} ${o.drawback}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = EffectKind.FLAW.color, modifier = Modifier.padding(top = Space.xs).testTag("opening_drawback_${o.id}"))
        if (chosen) {
            Caps("Who signs", Modifier.padding(top = Space.sm))
            o.signers.forEach { m ->
                Row(Modifier.fillMaxWidth().padding(top = Space.xs).semantics(mergeDescendants = true) {}, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                    Box(Modifier.background(ForgeSlot).border(1.dp, BronzeDeep)) { PixelImage(m.portrait, 36.dp, description = null) }
                    Column(Modifier.weight(1f)) {
                        Text("${m.name} · ${m.sub}", style = MaterialTheme.typography.titleSmall, color = Cream)
                        Text(m.trait, style = MaterialTheme.typography.bodySmall, color = CreamMuted)
                    }
                }
            }
            Caps("It brings", Modifier.padding(top = Space.sm))
            Text("${o.relic}: ${o.relicDescription}", style = MaterialTheme.typography.bodySmall, color = Cream, modifier = Modifier.padding(top = Space.xs))
            o.brings.forEach { Text("${EffectKind.BUFF.sign} $it", style = MaterialTheme.typography.bodySmall, color = EffectKind.BUFF.color, modifier = Modifier.padding(top = Space.xs)) }
            Text(
                if (plan) "The first days ▴" else "The first days ▾", style = MaterialTheme.typography.labelLarge, color = Gold,
                modifier = Modifier.fillMaxWidth().padding(top = Space.xs).clickable(role = Role.Button) { plan = !plan }.heightIn(min = 48.dp).wrapContentHeight().testTag("opening_plan_${o.id}"),
            )
            if (plan) Text(o.plan, style = MaterialTheme.typography.bodySmall, color = Cream)
        }
    }
}

/** A short heading inside a card: small capitals in gold, the one way a card's parts are named. */
@Composable
internal fun Caps(text: String, modifier: Modifier = Modifier) {
    Text(text.uppercase(), style = MaterialTheme.typography.labelSmall, color = Gold, modifier = modifier.semantics { heading() })
}
