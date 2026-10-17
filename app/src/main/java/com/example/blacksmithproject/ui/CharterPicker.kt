package com.example.blacksmithproject.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.blacksmithproject.GameViewModel
import com.example.blacksmithproject.ui.theme.CreamMuted
import com.example.blacksmithproject.ui.theme.Space
import com.tinyblacksmith.core.content.CharterDef
import com.tinyblacksmith.core.config.BalanceConfig
import com.tinyblacksmith.core.content.ContentCatalog

/** The picker over the ViewModel's own choice: what "New game" and "Begin era" will start under. Nothing at all for content without a guild. */
@Composable
fun CharterPicker(vm: GameViewModel, modifier: Modifier = Modifier) {
    val charters = vm.engine.content.guild?.charters?.takeIf { it.isNotEmpty() } ?: return
    val selected by vm.charter.collectAsStateWithLifecycle()
    CharterPicker(charters, selected, vm::selectCharter, vm.engine.content, modifier)
}

/** The choices in the order the arrows step through them: every charter, then null for the classic shop without a guild. */
fun charterChoices(charters: List<CharterDef>): List<String?> = charters.map { it.id } + null

/** The choice [by] steps from [selected], wrapping at both ends; an id that is not a choice counts as the first. */
fun stepCharter(charters: List<CharterDef>, selected: String?, by: Int): String? {
    val ids = charterChoices(charters)
    return ids[(ids.indexOf(selected).coerceAtLeast(0) + by).mod(ids.size)]
}

/**
 * The charter the next era begins under, every choice in view: one [ChoiceRow] per charter and, last, "No charter" (the
 * classic shop, a null id). Every row says whether it is a guild run, how it plays, what the charter gives and what it
 * costs, chosen or not, so the four can be compared before "Begin" starts the era. [title] heads the list where the
 * screen has no heading of its own.
 */
@Composable
fun CharterPicker(charters: List<CharterDef>, selected: String?, onSelect: (String?) -> Unit, content: ContentCatalog, modifier: Modifier = Modifier, title: String? = "How the era begins") {
    val ids = charterChoices(charters)
    val at = ids[ids.indexOf(selected).coerceAtLeast(0)]
    Column(modifier.fillMaxWidth().testTag("charter_picker"), verticalArrangement = Arrangement.spacedBy(Space.sm)) {
        title?.let { Caps(it) }
        // V2: the kind of run is the first question, asked plainly: the shop alone, or the shop and a guild. Only a
        // guild run shows the charters, each with what it gives and costs, so they can be compared side by side.
        ChoiceRow(at == null, { onSelect(null) }, Modifier.testTag("charter_row_none")) {
            Text("Classic era", style = MaterialTheme.typography.titleMedium, modifier = if (at == null) Modifier.testTag("charter_name") else Modifier)
            Text("Only the shop: forge, price and sell weapons. The heroes are your customers.", style = MaterialTheme.typography.bodySmall, color = CreamMuted)
        }
        ChoiceRow(at != null, { if (at == null) charters.firstOrNull()?.let { onSelect(it.id) } }, Modifier.testTag("setup_mode_guild")) {
            Text("Guild era", style = MaterialTheme.typography.titleMedium)
            Text("The shop and a guild: recruit heroes, lend them weapons and send them on quests. Choose a charter.", style = MaterialTheme.typography.bodySmall, color = CreamMuted)
        }
        if (at != null) Caps("Charter", Modifier.padding(top = Space.sm))
        if (at != null) ids.filterNotNull().forEach { id ->
            val charter = charters.firstOrNull { it.id == id }
            val chosen = id == at
            ChoiceRow(chosen, { onSelect(id) }, Modifier.testTag("charter_row_${id ?: "none"}")) {
                Text(charter?.name ?: "No charter", style = MaterialTheme.typography.titleMedium, modifier = if (chosen) Modifier.testTag("charter_name") else Modifier)
                charter?.let { Text(it.play, style = MaterialTheme.typography.bodySmall, color = CreamMuted) }
                if (charter != null) Column(Modifier.padding(top = Space.sm).let { if (chosen) it.testTag("charter_details") else it }, verticalArrangement = Arrangement.spacedBy(Space.xs)) {
                    EffectBullets(EffectKind.BUFF, charterGains(charter, content))
                    EffectBullets(EffectKind.FLAW, charterCosts(charter))
                }
            }
        }
    }
}

/**
 * What [c] gives, a few words a line, read from its numbers so the card cannot drift from the rules. [feePercent] is the
 * share of the recruitment fee the charter's offered class costs on day 1 (`GuildConfig.openingFeePercent`).
 */
fun charterGains(c: CharterDef, content: ContentCatalog, feePercent: Int = BalanceConfig.DEFAULT.guild.openingFeePercent): List<String> = listOfNotNull(
    "${c.startClasses.joinToString(" and ") { content.heroClass(it).name }} join on day 1",
    "${content.heroClass(c.offeredClass).name} for hire on day 1 at ${if (feePercent == 50) "half" else "$feePercent% of"} the recruitment fee",
    c.alsoOffered?.let { "${content.heroClass(it).name} for hire on day 1 at the full fee" },
    c.militiaDelta.takeIf { it > 0 }?.let { "$it extra militia on the wall" },
    c.reputationDelta.takeIf { it > 0 }?.let { "A known name: $it more reputation" },
    c.extraOffers.takeIf { it > 0 }?.let { if (it == 1) "1 more quest on the board each day" else "$it more quests on the board each day" },
    c.materials.takeIf { it.isNotEmpty() }?.entries?.joinToString(", ") { "${it.value} ${content.material(it.key).name}" },
)

/** What [c] costs, the same way. */
fun charterCosts(c: CharterDef): List<String> = listOfNotNull(
    c.goldDelta.takeIf { it < 0 }?.let { "Start with ${-it} less gold" },
    c.militiaDelta.takeIf { it < 0 }?.let { "${-it} fewer militia on the wall" },
    c.earlyDanger.takeIf { it }?.let { "Every kind of quest from day 1, the dangerous ones included" },
    c.smithGoldPercent.takeIf { it < 100 }?.let { "The forge keeps only $it% of the quest gold left after the members take their shares" },
)
