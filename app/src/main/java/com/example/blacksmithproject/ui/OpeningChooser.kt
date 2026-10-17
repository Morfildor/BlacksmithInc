package com.example.blacksmithproject.ui

import androidx.compose.foundation.background
import com.example.blacksmithproject.ui.theme.Bronze
import com.example.blacksmithproject.R
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.size
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
import com.example.blacksmithproject.ui.theme.Space
import com.tinyblacksmith.core.engine.GameEngine
import com.tinyblacksmith.core.model.GameState
import com.tinyblacksmith.core.model.HeroId

/** One who signs with a package: who they are and the defining trait it gives them, in the catalog's words. */
@Immutable data class OpeningSignerUi(val heroId: HeroId, val portrait: Int, val name: String, val sub: String, val trait: String, val firstName: String = name, val className: String = sub, val traitName: String = trait)

/** One opening package, whole: everything that decides the choice is on its card. [brings] is its stock and its weapon, a line each. */
@Immutable
data class OpeningUi(
    val id: String, val name: String, val build: String, val pitch: String, val signers: List<OpeningSignerUi>,
    val relic: String, val relicDescription: String, val brings: List<String>, val drawback: String, val plan: String,
    /** The drawback as short bullets: its note, then the gold, health and militia it costs. */
    val costs: List<String> = listOf(drawback),
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
                    OpeningSignerUi(
                        h.id, Sprites.portrait(h, small = true), h.fullName, classLine(h), trait?.let { "${it.name}: ${it.description}" } ?: "No trait of note",
                        firstName = h.name, className = content.heroClass(h.classId).name, traitName = trait?.name ?: "no trait",
                    )
                },
                relic = relic?.name ?: def.relicId, relicDescription = relic?.description.orEmpty(),
                brings = listOfNotNull(
                    def.materials.entries.joinToString(", ") { "${it.value} ${content.material(it.key).name}" }.ifEmpty { null },
                    def.weapon?.let { w ->
                        val flaw = content.affixById[w.flaw]
                        "${content.material(w.coreId).name} ${content.family(w.familyId).name.lowercase()} with ${content.material(w.augmentId).name}, quality ${w.quality}" +
                            (flaw?.let { ", ${it.name}" } ?: ", flawed")
                    },
                ),
                drawback = def.drawback, plan = def.plan,
                costs = listOfNotNull(
                    def.costNote,
                    def.goldDelta.takeIf { it < 0 }?.let { "Start with ${-it} less gold" },
                    def.integrityDelta.takeIf { it < 0 }?.let { "The forge starts with ${-it} less health" },
                    def.militiaDelta.takeIf { it < 0 }?.let { "${-it} fewer militia on the wall" },
                ).ifEmpty { listOf(def.drawback) },
            )
        },
    )
}

/**
 * The first morning of a guild run, in place of the workshop until one package is taken: End Day waits for this choice, so
 * nothing else is offered beside it. Who signs is the same under every package (the charter's two classes, GuildOps.found),
 * so they are shown once above the cards; a card says only what differs: its playstyle, its benefit, the trait each
 * signer gets, its relic (tap to read it) and its cost. Nothing is chosen until a card is tapped; the chosen card opens to
 * what it brings and the first days. One pinned action, "Begin with X", sends the command: no second question. Only which
 * card is marked is kept here; the offer itself is in the save. [firstGame] is the note a first game reads first.
 */
@Composable
fun OpeningChooser(ui: OpeningChooserUi, enabled: Boolean, onChoose: (String) -> Unit, modifier: Modifier = Modifier, firstGame: (@Composable () -> Unit)? = null) {
    var marked by rememberSaveable { mutableStateOf<String?>(null) }
    val chosen = ui.openings.firstOrNull { it.id == marked }
    val shared = ui.openings.map { o -> o.signers.map { it.heroId } }.distinct().singleOrNull()?.let { ui.openings.first().signers }
    Column(modifier.fillMaxSize().safeDrawingPadding().testTag("opening_chooser")) {
        val scroll = rememberScrollState()
        Column(
            Modifier.weight(1f).verticalScroll(scroll).bottomFade(scroll.canScrollForward).padding(horizontal = Space.md).padding(top = Space.md, bottom = Space.lg),
            verticalArrangement = Arrangement.spacedBy(Space.sm),
        ) {
            Column {
                Text("Choose a starting kit", style = MaterialTheme.typography.headlineMedium, color = Cream, modifier = Modifier.semantics { heading() })
                Text(
                    listOfNotNull(ui.charter.ifEmpty { null }, "One starting kit, kept for the whole era.").joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall, color = CreamMuted, modifier = Modifier.padding(top = Space.xs),
                )
            }
            firstGame?.invoke()
            if (shared != null) Signers(shared)
            ui.openings.forEach { o -> OpeningCard(o, o.id == marked, enabled, showSigners = shared == null, onMark = { marked = o.id }) }
        }
        PrimaryActionButton(
            chosen?.let { "Begin with ${it.name}" } ?: "Pick a starting kit to begin", { chosen?.let { onChoose(it.id) } },
            Modifier.fillMaxWidth().padding(horizontal = Space.md, vertical = Space.sm).testTag("opening_begin"), enabled = enabled && chosen != null,
        )
    }
}

/** The two who sign whichever start is picked: a portrait, the first name and the class, side by side. */
@Composable
private fun Signers(signers: List<OpeningSignerUi>) {
    Column(Modifier.fillMaxWidth().padding(vertical = Space.xs)) {
        Caps("Joining with every starting kit")
        Row(Modifier.fillMaxWidth().padding(top = Space.sm), horizontalArrangement = Arrangement.spacedBy(Space.md)) {
            signers.forEach { m ->
                Row(Modifier.weight(1f).semantics(mergeDescendants = true) {}, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                    Portrait(m.portrait, 44.dp)
                    Column {
                        Text(m.firstName, style = MaterialTheme.typography.titleSmall, color = Cream)
                        Text(m.className, style = MaterialTheme.typography.bodySmall, color = CreamMuted)
                    }
                }
            }
        }
    }
}

@Composable
private fun Portrait(res: Int, size: Dp) {
    Box(Modifier.size(size).background(ForgeSlot, MaterialTheme.shapes.small).border(1.dp, BronzeDeep, MaterialTheme.shapes.small).clip(MaterialTheme.shapes.small), contentAlignment = Alignment.Center) {
        PixelImage(res, size, description = null)
    }
}

/**
 * One package as a selectable card, in the order a choice is read: its playstyle and name, the benefit, the trait each
 * signer gets, the relic, the cost. The relic's effect opens in place on a tap without choosing the card. Chosen, the
 * card adds what it brings and the first days.
 */
@Composable
private fun OpeningCard(o: OpeningUi, chosen: Boolean, enabled: Boolean, showSigners: Boolean, onMark: () -> Unit) {
    var plan by rememberSaveable(o.id) { mutableStateOf(false) }
    var relic by rememberSaveable(o.id + "_relic") { mutableStateOf(false) }
    ChoiceRow(chosen, onMark, Modifier.testTag("opening_choose_${o.id}"), enabled = enabled) {
        Caps(o.build)
        Text(o.name, style = MaterialTheme.typography.titleMedium, color = Cream, modifier = Modifier.padding(top = 2.dp).semantics { heading() })
        Text(o.pitch, style = MaterialTheme.typography.bodyMedium, color = Cream, modifier = Modifier.padding(top = Space.xs))
        // The trait each signer gets: one line, the same trait said once for both.
        val traits = o.signers.groupBy { it.traitName }
        Text(
            buildAnnotatedString {
                if (showSigners) append("${o.signers.joinToString(" and ") { "${it.firstName} (${it.className})" }} ${if (o.signers.size == 1) "joins" else "join"}. ")
                traits.entries.forEachIndexed { i, (trait, who) ->
                    if (i > 0) append(" · ")
                    append(if (who.size == o.signers.size && who.size > 1) "Both gain " else "${who.joinToString(" and ") { it.firstName }} gains ")
                    withStyle(SpanStyle(color = Cream, fontWeight = FontWeight.Medium)) { append(trait) }
                }
            },
            style = MaterialTheme.typography.bodySmall, color = CreamMuted, modifier = Modifier.padding(top = Space.sm),
        )
        Row(
            Modifier.fillMaxWidth().padding(top = Space.xs).heightIn(min = 40.dp).clickable(role = Role.Button, onClickLabel = if (relic) "Hide the relic's effect" else "Read the relic's effect") { relic = !relic }.testTag("opening_relic_${o.id}"),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.sm),
        ) {
            PixelIcon(R.drawable.icon_relic, 24.dp)
            Text(
                buildAnnotatedString { append("Relic  "); withStyle(SpanStyle(color = Cream, fontWeight = FontWeight.Medium)) { append(o.relic) } },
                style = MaterialTheme.typography.bodySmall, color = CreamMuted, modifier = Modifier.weight(1f),
            )
            Chevron(if (relic || chosen) ChevronWay.UP else ChevronWay.DOWN, tint = CreamMuted, size = 18.dp)
        }
        if (relic || chosen) Text(o.relicDescription, style = MaterialTheme.typography.bodySmall, color = CreamMuted, modifier = Modifier.padding(start = 32.dp, bottom = Space.xs))
        // The cost of the choice is read before it is made, on every card, as plainly as the benefit.
        // Chosen, what it brings stands over the costs, gains before costs as on the charters.
        if (chosen) EffectBullets(EffectKind.BUFF, o.brings, Modifier.padding(top = Space.xs))
        EffectBullets(EffectKind.FLAW, o.costs, Modifier.padding(top = Space.xs).testTag("opening_drawback_${o.id}"))
        if (chosen) {
            Caps("Traits", Modifier.padding(top = Space.md))
            o.signers.forEach { m ->
                Text(buildAnnotatedString { withStyle(SpanStyle(color = Cream, fontWeight = FontWeight.Medium)) { append(m.firstName) }; append("  "); append(m.trait) }, style = MaterialTheme.typography.bodySmall, color = CreamMuted, modifier = Modifier.padding(top = Space.xs))
            }
            Text(
                if (plan) "The first days ▴" else "The first days ▾", style = MaterialTheme.typography.labelLarge, color = Cream,
                modifier = Modifier.fillMaxWidth().padding(top = Space.xs).clickable(role = Role.Button) { plan = !plan }.heightIn(min = 48.dp).wrapContentHeight().testTag("opening_plan_${o.id}"),
            )
            if (plan) Text(o.plan, style = MaterialTheme.typography.bodySmall, color = CreamMuted)
        }
    }
}

/** A short heading inside a card: small capitals in brass, the one way a card's parts are named. */
@Composable
internal fun Caps(text: String, modifier: Modifier = Modifier) {
    Text(text.uppercase(), style = MaterialTheme.typography.labelSmall, color = Bronze, modifier = modifier.semantics { heading() })
}
