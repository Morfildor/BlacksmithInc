package com.example.blacksmithproject.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.blacksmithproject.GameViewModel
import com.example.blacksmithproject.Panel
import com.example.blacksmithproject.UiState
import com.example.blacksmithproject.ui.theme.Space
import com.tinyblacksmith.core.battle.Battle
import com.tinyblacksmith.core.gazette.Gazette
import com.tinyblacksmith.core.model.CommissionStatus
import kotlin.math.roundToInt

/**
 * Home: the dashboard the workshop opens on. One block per concern, the most urgent first (a siege within two days
 * or against the odds, then commissions waiting for an answer, then a pending blessing), each tapping through to the
 * panel where the action happens. Every line is a read of GameState or a read-only engine API; nothing is computed
 * here (GDD 13.2). No line may start with a tab name ("Forge", "Market", "Town"): the emulator scripts tap the first
 * text that starts with one.
 */
@Composable
fun HomePanel(s: UiState.Playing, vm: GameViewModel) {
    val st = s.state
    val outlook = vm.engine.siegeForecast(st)
    val daysLeft = st.town.nextSiegeDay - st.day
    val siegeUrgent = daysLeft <= 2 || outlook?.odds == Battle.SiegeOdds.OUTMATCHED || outlook?.odds == Battle.SiegeOdds.DIRE
    val offered = st.commissions.values.count { it.status == CommissionStatus.OFFERED }

    if (siegeUrgent) SiegeBlock(s, vm, outlook, daysLeft)
    if (offered > 0) CommissionsBlock(s, vm, offered)
    if (st.pendingBlessingOffer.isNotEmpty()) {
        Button(onClick = vm::reopenBlessingOffer, modifier = Modifier.fillMaxWidth().padding(top = Space.sm).heightIn(min = 48.dp)) { Text("Choose a blessing") }
    }
    TodayBlock(s, vm)
    if (!siegeUrgent) SiegeBlock(s, vm, outlook, daysLeft)
    ShelfBlock(s, vm)
    if (offered == 0) CommissionsBlock(s, vm, offered)
    HeroesBlock(s, vm)
    YesterdayBlock(s, vm)
    MaterialsBlock(s, vm)
}

/** One tappable block: a title, then one to four lines; the whole surface opens [panel]. */
@Composable
private fun HomeBlock(title: String, lines: List<String>, panel: Panel, vm: GameViewModel) {
    Surface(tonalElevation = 1.dp, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth().padding(top = Space.sm)) {
        Column(
            Modifier.fillMaxWidth().clickable(role = Role.Button) { vm.selectPanel(panel) }.padding(Space.md)
                .semantics(mergeDescendants = true) { contentDescription = (listOf(title) + lines).joinToString(". ") { it.trimEnd('.') } + ". Tap to open ${panelName(panel)}." },
        ) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            lines.forEach { Text(it, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = Space.xs)) }
        }
    }
}

@Composable
private fun TodayBlock(s: UiState.Playing, vm: GameViewModel) {
    val st = s.state
    val lines = listOfNotNull(
        "Day ${st.day} · era ${st.era} · ${st.world.name}",
        "Energy ${st.energy} · ${st.gold} gold · forge integrity ${st.town.integrity} · reputation ${st.reputation}",
        if (st.overworkToday > 0) "Tomorrow starts ${st.overworkToday} energy short" else null,
    )
    HomeBlock("Today", lines, Panel.FORGE, vm)
}

/** Worded exactly as the Town panel words the siege, so the two never disagree. */
@Composable
private fun SiegeBlock(s: UiState.Playing, vm: GameViewModel, outlook: Battle.SiegeOutlook?, daysLeft: Int) {
    val st = s.state
    val title = when { daysLeft <= 0 -> "Siege today"; daysLeft == 1 -> "Siege tomorrow"; else -> "Siege in $daysLeft days" }
    val armory = "armory ${st.town.armory}/${vm.engine.config.armoryMax}"
    val lines = if (outlook == null) listOf("No threat · $armory") else {
        val def = outlook.faction
        val odds = when (outlook.odds) {
            Battle.SiegeOdds.STRONG -> "the town should hold"
            Battle.SiegeOdds.EVEN -> "evenly matched"
            Battle.SiegeOdds.OUTMATCHED -> "the walls are outmatched"
            Battle.SiegeOdds.DIRE -> "grave danger"
        }
        listOfNotNull(
            "Day ${st.town.nextSiegeDay} · ${def.name}, ${Battle.describePressure(outlook.factionState.pressure)}" + (def.weakTo?.let { " · weak to ${it.name.lowercase()}" } ?: ""),
            "Outlook: $odds · defense ${outlook.townDefense.roundToInt()} vs raid ${outlook.raidPower.roundToInt()} · $armory",
            if (outlook.warlord) "${def.warlordName} leads them" else null,
        )
    }
    HomeBlock(title, lines, Panel.TOWN, vm)
}

@Composable
private fun ShelfBlock(s: UiState.Playing, vm: GameViewModel) {
    val st = s.state
    val visits = st.lastResolution?.takeIf { it.day == st.day - 1 }?.visits.orEmpty()
    val bought = visits.filter { it.purchasedWeaponId != null }
    val left = visits.filter { it.purchasedWeaponId == null }
    val lines = listOfNotNull(
        "Listed ${st.listedWeapons().size} of ${vm.engine.shelfSlots(st)} · ${st.storedWeapons().size} in storage",
        if (st.day > 1 && visits.isEmpty()) "No visitors yesterday" else null,
        if (bought.isNotEmpty()) "Bought yesterday: " + bought.joinToString { v -> "${v.heroName} (${v.purchasedWeaponId?.let { st.weapons[it]?.name } ?: "a weapon"})" } else null,
        if (left.isNotEmpty()) "Left: " + left.joinToString("; ") { "${it.heroName} ${Gazette.visitReason(it.reason)}" } else null,
    )
    HomeBlock("Shelf", lines, Panel.MARKET, vm)
}

@Composable
private fun CommissionsBlock(s: UiState.Playing, vm: GameViewModel, offered: Int) {
    val st = s.state
    val content = vm.engine.content
    val open = st.commissions.values.filter { it.status == CommissionStatus.OFFERED || it.status == CommissionStatus.ACCEPTED }.sortedBy { it.deadlineDay }
    val lines = open.map { c ->
        val buyer = st.heroes[c.buyerId]?.fullName ?: "Someone"
        val left = c.deadlineDay - st.day
        // A commission still counts on its deadline day: it lapses at that End Day if nothing was delivered.
        val due = when { left <= 0 -> "due today"; left == 1 -> "due tomorrow"; else -> "due day ${c.deadlineDay}, in $left days" }
        "${Labels.request(c, content, vm.engine.config)} for $buyer · ${c.reward} gold · $due · " +
            if (c.status == CommissionStatus.OFFERED) "needs an answer" else "accepted · ${Labels.readiness(c, st.weapons.values, content, vm.engine.config)}"
    }
    HomeBlock(if (offered > 0) "Commissions · $offered to answer" else "Commissions", lines.ifEmpty { listOf("No requests today.") }, Panel.MARKET, vm)
}

/** The same champions and words as the Town panel. */
@Composable
private fun HeroesBlock(s: UiState.Playing, vm: GameViewModel) {
    val st = s.state
    val champions = st.town.championIds.mapNotNull { st.heroes[it] }
    val alive = st.aliveHeroes()
    val wounded = alive.count { Labels.health(it) == "wounded" || Labels.health(it) == "grave" }
    val lines = champions.map { h ->
        val w = st.equippedWeapon(h.id)
        "${h.fullName} · ${w?.let { "wields ${it.name}" } ?: "unarmed"} · ${Labels.health(h)}"
    }.ifEmpty { listOf("No hero stands at the walls yet.") } + "${alive.size} alive · $wounded wounded"
    HomeBlock("Heroes", lines, Panel.TOWN, vm)
}

/** Yesterday's edition as the Gazette archive folds it: the lede (or the first line) and the tally. */
@Composable
private fun YesterdayBlock(s: UiState.Playing, vm: GameViewModel) {
    val st = s.state
    val edition = remember(st.events, st.day) {
        val heroNames = st.heroes.values.associate { it.id.value to it.fullName }
        val res = st.lastResolution?.takeIf { it.day == st.day - 1 }
        Gazette.edition(Gazette.dayRecords(st, st.day - 1), heroNames, res?.visits ?: emptyList(), res?.ledger, res?.field ?: emptyList())
    }
    val first = edition.lede.firstOrNull() ?: edition.sections.firstOrNull()?.lines?.firstOrNull()
    val lines = if (first == null) listOf("No news yet.") else listOfNotNull(first, edition.tally.takeIf { it.isNotEmpty() }?.joinToString(" · "))
    HomeBlock("Yesterday", lines, Panel.GAZETTE, vm)
}

@Composable
private fun MaterialsBlock(s: UiState.Playing, vm: GameViewModel) {
    val st = s.state
    // The engine leaves a zero entry behind when the last unit is used, so these are the kinds the player has run out of.
    val out = st.materials.filter { it.value == 0 }.keys.map { vm.engine.content.material(it).name }.sorted()
    val line = "${st.materials.count { it.value > 0 }} kinds on hand" + if (out.isEmpty()) "" else " · out of ${out.joinToString()}"
    HomeBlock("Materials", listOf(line), Panel.FORGE, vm)
}
