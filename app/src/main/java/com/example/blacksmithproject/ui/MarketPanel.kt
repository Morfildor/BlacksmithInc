package com.example.blacksmithproject.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.blacksmithproject.GameViewModel
import com.example.blacksmithproject.Sheet
import com.example.blacksmithproject.UiState
import com.example.blacksmithproject.ui.theme.Space
import com.tinyblacksmith.core.content.ContentCatalog
import com.tinyblacksmith.core.content.MaterialCategory
import com.tinyblacksmith.core.engine.Command
import com.tinyblacksmith.core.model.CommissionStatus
import com.tinyblacksmith.core.model.VisitReason
import com.tinyblacksmith.core.model.Weapon

/** Market flow (GDD 12): inspect -> price -> list -> review customers/commissions. Prices are typed by the player. */
@Composable
fun MarketPanel(s: UiState.Playing, vm: GameViewModel) {
    val content = vm.engine.content
    val st = s.state
    val listed = st.listedWeapons()
    val slots = vm.engine.shelfSlots(st)

    SectionTitle("Shelves (${listed.size}/$slots)", Modifier.padding(top = Space.sm))
    if (listed.isEmpty()) Secondary("Nothing on display. Heroes browse at dawn.")
    listed.forEach { w ->
        WeaponListing(w, s, vm, listed = true)
    }
    val empty = slots - listed.size
    if (empty > 0 && listed.isNotEmpty()) Secondary(if (empty == 1) "1 empty shelf" else "$empty empty shelves", Modifier.padding(vertical = Space.sm))

    SectionTitle("Storage (${st.storedWeapons().size})")
    if (st.storedWeapons().isEmpty()) Secondary("Nothing in storage. Forge something, then list it here.")
    st.storedWeapons().forEach { w -> WeaponListing(w, s, vm, listed = false) }

    st.lastResolution?.takeIf { it.visits.isNotEmpty() }?.let { res ->
        SectionTitle("Yesterday's customers")
        res.visits.forEach { v ->
            val bought = v.purchasedWeaponId?.let { id -> st.weapons[id]?.name ?: "a weapon" }
            Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                Text(bought?.let { "${v.heroName} bought $it" } ?: "${v.heroName} left", style = MaterialTheme.typography.bodyMedium)
                Secondary(reasonLabel(v.reason).replaceFirstChar { it.uppercase() })
            }
        }
    }

    SectionTitle("Commissions")
    val open = st.commissions.values.filter { it.status == CommissionStatus.OFFERED || it.status == CommissionStatus.ACCEPTED }.sortedBy { it.deadlineDay }
    if (open.isEmpty()) Secondary("No requests today.")
    open.forEach { c ->
        val buyer = st.heroes[c.buyerId]?.fullName ?: "Someone"
        Card(Modifier.fillMaxWidth().padding(vertical = Space.xs)) {
            Column(Modifier.padding(Space.md)) {
                Text("${Labels.request(c, content, vm.engine.config)} for $buyer", style = MaterialTheme.typography.titleSmall)
                Secondary("${c.reward} gold · due day ${c.deadlineDay}", Modifier.padding(top = 2.dp))
                // Each blade of the family in the shop, by the engine's own rule: "fits" or the one thing it lacks.
                (st.storedWeapons() + listed).filter { it.familyId == c.familyId }.forEach { w -> Secondary("${w.name}: ${Labels.fit(w, c, content)}") }
                if (c.status == CommissionStatus.OFFERED) {
                    Row(horizontalArrangement = Arrangement.spacedBy(Space.sm), modifier = Modifier.padding(top = Space.sm)) {
                        Button(onClick = { vm.dispatch(Command.AcceptCommission(c.id)) }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Accept") }
                        OutlinedButton(onClick = { vm.dispatch(Command.DeclineCommission(c.id)) }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Decline") }
                    }
                } else Secondary("Accepted. ${Labels.readiness(c, st.weapons.values, content, vm.engine.config)}", Modifier.padding(top = Space.sm))
            }
        }
    }

    SectionTitle("Supplier")
    Secondary("Buy one at a time. Basic metals are always in stock.")
    MaterialCategory.entries.forEach { category ->
        val group = content.materials(category)
        if (group.isEmpty()) return@forEach
        Text(
            when (category) { MaterialCategory.CORE -> "Cores"; MaterialCategory.AUGMENT -> "Augments"; MaterialCategory.CATALYST -> "Catalysts" },
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(top = Space.md, bottom = Space.xs),
        )
        group.forEach { m ->
            val price = vm.engine.materialPrice(st, m.id)
            val stock = st.supplierStock[m.id]
            val have = st.materials[m.id] ?: 0
            Row(Modifier.fillMaxWidth().padding(vertical = Space.xs), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                Sprites.material(m.id)?.let { PixelImage(it, 28.dp, description = null) }
                Column(Modifier.weight(1f)) {
                    Text("${m.name} · $price gold", style = MaterialTheme.typography.bodyMedium)
                    Secondary("You have $have" + (stock?.let { " · $it left today" } ?: ""))
                }
                OutlinedButton(
                    enabled = (stock == null || stock > 0) && st.gold >= price && !s.busy,
                    onClick = { vm.dispatch(Command.BuyMaterial(m.id, 1)) },
                    modifier = Modifier.heightIn(min = 48.dp).semantics { contentDescription = "Buy ${m.name} for $price gold" },
                ) { Text("Buy") }
            }
        }
    }

    SectionTitle("Workshop tools")
    Secondary("Bought with gold; they last until the forge falls.")
    content.tools.forEach { t ->
        val cost = vm.engine.toolCost(st, t.id)
        Row(Modifier.fillMaxWidth().padding(vertical = Space.xs), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
            Column(Modifier.weight(1f)) {
                Text("${t.name} · level ${st.tools[t.id] ?: 0}/${t.maxLevel}", style = MaterialTheme.typography.bodyMedium)
                Secondary(t.description)
            }
            if (cost == null) Secondary("Maxed")
            else OutlinedButton(
                enabled = st.gold >= cost && !s.busy,
                onClick = { vm.dispatch(Command.BuyTool(t.id)) },
                modifier = Modifier.heightIn(min = 48.dp).semantics { contentDescription = "Buy ${t.name} for $cost gold" },
            ) { Text("Buy · $cost g") }
        }
    }
}

/** Which hero classes favour this family: a content read, never a sale prediction. */
private fun demandHint(familyId: com.tinyblacksmith.core.model.WeaponFamilyId, content: ContentCatalog): String? {
    val fans = content.classes.filter { familyId in it.preferredFamilies }.map { it.name + "s" }
    return if (fans.isEmpty()) null else "${fans.joinToString(" and ")} favour the ${content.family(familyId).name.lowercase()}"
}

/**
 * One weapon on a shelf or in storage: sprite, name, what it is, who wants it, and its price. Tapping the row opens
 * the blade's sheet, where it is priced, listed, unlisted, salvaged, honed or given to the watch.
 */
@Composable
private fun WeaponListing(w: Weapon, s: UiState.Playing, vm: GameViewModel, listed: Boolean) {
    val content = vm.engine.content
    val suggested = vm.engine.suggestedPrice(w)
    val priceLabel = if (listed) "${w.listedPrice ?: 0} g" else "List at $suggested"
    Surface(
        tonalElevation = 1.dp,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth().padding(vertical = Space.xs),
    ) {
        Row(
            Modifier.fillMaxWidth().clickable { vm.openSheet(Sheet.Item(w.id)) }.testTag("stock_${w.id.value}").padding(horizontal = 12.dp, vertical = 10.dp)
                .semantics(mergeDescendants = true) { contentDescription = "${w.name}, ${Labels.weaponSummary(w, content)}, ${if (listed) "${w.listedPrice ?: 0} gold" else "in storage"}. Tap for details and price." },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            WeaponSprite(w, size = 48.dp)
            Column(Modifier.weight(1f)) {
                Text(w.name, style = MaterialTheme.typography.titleSmall)
                Secondary(Labels.weaponSummary(w, content))
                demandHint(w.familyId, content)?.let { Secondary(it) }
            }
            if (listed) Text(priceLabel, style = MaterialTheme.typography.titleMedium)
            else Button(onClick = { vm.dispatch(Command.ToggleShelf(w.id, true, suggested)) }, enabled = !s.busy, modifier = Modifier.heightIn(min = 48.dp)) { Text(priceLabel, maxLines = 1) }
        }
    }
}

fun reasonLabel(reason: VisitReason): String = when (reason) {
    VisitReason.GREAT_FIT -> "a great fit"
    VisitReason.GOOD_ENOUGH -> "good enough for their purse"
    VisitReason.WORN_OUT -> "their own blade was worn out"
    VisitReason.EMPTY_SHELVES -> "nothing on the shelves"
    VisitReason.TOO_EXPENSIVE -> "could not afford anything"
    VisitReason.NOT_BETTER -> "nothing better than their own gear"
    VisitReason.OVERPRICED -> "found the prices too steep"
    VisitReason.NOT_SUITED -> "nothing suited their style"
    VisitReason.UNDECIDED -> "undecided"
    VisitReason.COMMISSION_DELIVERED -> "collected their commission"
    VisitReason.COLLECTOR_PURCHASE -> "paid a collector's price"
    VisitReason.TASTE_MATCH, VisitReason.PRIZED, VisitReason.STORIED, VisitReason.COUNTERS_THREAT, VisitReason.RESISTED -> com.tinyblacksmith.core.shopday.Lines.reason(reason)
}
