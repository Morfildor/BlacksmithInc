package com.example.blacksmithproject.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.blacksmithproject.GameViewModel
import com.example.blacksmithproject.UiState
import com.example.blacksmithproject.ui.theme.Space
import com.tinyblacksmith.core.content.ContentCatalog
import com.tinyblacksmith.core.content.MaterialCategory
import com.tinyblacksmith.core.engine.Command
import com.tinyblacksmith.core.model.CommissionStatus
import com.tinyblacksmith.core.model.Weapon

/** Market flow (GDD 12): inspect -> price -> list -> review customers/commissions. Prices are typed by the player. */
@Composable
fun MarketPanel(s: UiState.Playing, vm: GameViewModel) {
    val content = vm.engine.content
    val st = s.state
    val listed = st.listedWeapons()
    val slots = vm.engine.config.shelfSlots

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
                Text("${Labels.quality(c.minQuality).replaceFirstChar { it.uppercase() }} ${content.family(c.familyId).name} for $buyer", style = MaterialTheme.typography.titleSmall)
                Secondary("${c.reward} gold · due day ${c.deadlineDay}", Modifier.padding(top = 2.dp))
                if (c.status == CommissionStatus.OFFERED) {
                    Row(horizontalArrangement = Arrangement.spacedBy(Space.sm), modifier = Modifier.padding(top = Space.sm)) {
                        Button(onClick = { vm.dispatch(Command.AcceptCommission(c.id)) }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Accept") }
                        OutlinedButton(onClick = { vm.dispatch(Command.DeclineCommission(c.id)) }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Decline") }
                    }
                } else Secondary("Accepted. A matching weapon in storage or on the shelf is delivered at End Day.", Modifier.padding(top = Space.sm))
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
}

/** Which hero classes favour this family: a content read, never a sale prediction. */
private fun demandHint(familyId: com.tinyblacksmith.core.model.WeaponFamilyId, content: ContentCatalog): String? {
    val fans = content.classes.filter { familyId in it.preferredFamilies }.map { it.name + "s" }
    return if (fans.isEmpty()) null else "${fans.joinToString(" and ")} favour the ${content.family(familyId).name.lowercase()}"
}

/**
 * One weapon on a shelf or in storage: sprite, name, what it is, who wants it, and its price. Tapping the row opens
 * the price editor; listing from storage uses the suggested price unless the player changes it.
 */
@Composable
private fun WeaponListing(w: Weapon, s: UiState.Playing, vm: GameViewModel, listed: Boolean) {
    val content = vm.engine.content
    val suggested = vm.engine.suggestedPrice(w)
    var expanded by rememberSaveable(w.id.value) { mutableStateOf(false) }
    var priceText by rememberSaveable(w.id.value, w.listedPrice) { mutableStateOf((w.listedPrice ?: suggested).toString()) }
    val price = priceText.toIntOrNull() ?: 0
    val priceLabel = if (listed) "${w.listedPrice ?: 0} g" else "List at $suggested"
    Surface(
        tonalElevation = 1.dp,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth().padding(vertical = Space.xs),
    ) {
        Column(Modifier.fillMaxWidth()) {
            Row(
                Modifier.fillMaxWidth().clickable { expanded = !expanded }.padding(horizontal = 12.dp, vertical = 10.dp)
                    .semantics(mergeDescendants = true) { contentDescription = "${w.name}, ${Labels.weaponSummary(w, content)}, ${if (listed) "${w.listedPrice ?: 0} gold" else "in storage"}. Tap to set a price." },
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
            AnimatedVisibility(visible = expanded) {
                Column(Modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp)) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.padding(bottom = Space.sm))
                    Secondary("Suggested price $suggested gold.")
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.sm), modifier = Modifier.padding(top = Space.sm)) {
                        StepButton("−10", "Lower price by 10") { priceText = (price - 10).coerceAtLeast(0).toString() }
                        OutlinedTextField(
                            value = priceText,
                            onValueChange = { priceText = it.filter { c -> c.isDigit() }.take(6) },
                            label = { Text("Price") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.width(112.dp),
                        )
                        StepButton("+10", "Raise price by 10") { priceText = (price + 10).coerceAtMost(999999).toString() }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(Space.sm), modifier = Modifier.padding(top = Space.sm)) {
                        if (listed) {
                            Button(onClick = { vm.dispatch(Command.SetPrice(w.id, price)); expanded = false }, enabled = !s.busy, modifier = Modifier.heightIn(min = 48.dp)) { Text("Set price") }
                            OutlinedButton(onClick = { vm.dispatch(Command.ToggleShelf(w.id, false)) }, enabled = !s.busy, modifier = Modifier.heightIn(min = 48.dp)) { Text("Unlist") }
                        } else {
                            Button(onClick = { vm.dispatch(Command.ToggleShelf(w.id, true, price)) }, enabled = !s.busy, modifier = Modifier.heightIn(min = 48.dp)) { Text("List at $price") }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StepButton(label: String, description: String, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        contentPadding = PaddingValues(4.dp),
        modifier = Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp).semantics { contentDescription = description },
    ) { Text(label) }
}

fun reasonLabel(reason: String): String = when (reason) {
    "GREAT_FIT" -> "a great fit"
    "GOOD_ENOUGH" -> "good enough for their purse"
    "EMPTY_SHELVES" -> "nothing on the shelves"
    "TOO_EXPENSIVE" -> "could not afford anything"
    "NOT_BETTER" -> "nothing better than their own gear"
    "OVERPRICED" -> "found the prices too steep"
    "NOT_SUITED" -> "nothing suited their style"
    else -> "undecided"
}
