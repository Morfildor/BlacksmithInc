package com.example.blacksmithproject.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.blacksmithproject.GameViewModel
import com.example.blacksmithproject.UiState
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

    SectionTitle("Shelves (${listed.size}/$slots)")
    (0 until slots).chunked(4).forEach { rowSlots ->
        Row(Modifier.fillMaxWidth().padding(bottom = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            rowSlots.forEach { i -> ShelfSlot(listed.getOrNull(i), i + 1, Modifier.weight(1f)) }
        }
    }
    if (listed.isEmpty()) Text("Nothing on display. Heroes browse at dawn.", style = MaterialTheme.typography.bodySmall)
    listed.forEach { w ->
        WeaponPriceRow(w, initialPrice = w.listedPrice ?: 0, vm = vm, actionLabel = "Unlist",
            onPrice = { vm.dispatch(Command.SetPrice(w.id, it)) },
            onAction = { vm.dispatch(Command.ToggleShelf(w.id, false)) })
    }

    SectionTitle("Storage (${st.storedWeapons().size})")
    if (st.storedWeapons().isEmpty()) Text("Nothing in storage. Forge something, then list it here.", style = MaterialTheme.typography.bodySmall)
    st.storedWeapons().forEach { w ->
        WeaponPriceRow(w, initialPrice = vm.engine.suggestedPrice(w), vm = vm, actionLabel = "List",
            onPrice = null,
            onAction = { price -> vm.dispatch(Command.ToggleShelf(w.id, true, price)) })
    }

    st.lastResolution?.takeIf { it.visits.isNotEmpty() }?.let { res ->
        SectionTitle("Yesterday's customers")
        res.visits.forEach { v ->
            val bought = v.purchasedWeaponId?.let { id -> st.weapons[id]?.name ?: "a weapon" }
            Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(bought?.let { "${v.heroName} bought $it" } ?: "${v.heroName} left", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                ReasonChip(reasonLabel(v.reason), bought != null)
            }
        }
    }

    SectionTitle("Commissions")
    val open = st.commissions.values.filter { it.status == CommissionStatus.OFFERED || it.status == CommissionStatus.ACCEPTED }.sortedBy { it.deadlineDay }
    if (open.isEmpty()) Text("No requests today.", style = MaterialTheme.typography.bodySmall)
    open.forEach { c ->
        val buyer = st.heroes[c.buyerId]?.fullName ?: "Someone"
        Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
            Column(Modifier.padding(12.dp)) {
                Text("$buyer wants a ${Labels.quality(c.minQuality)} ${content.family(c.familyId).name} by day ${c.deadlineDay} for ${c.reward} gold.")
                if (c.status == CommissionStatus.OFFERED) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                        Button(onClick = { vm.dispatch(Command.AcceptCommission(c.id)) }) { Text("Accept") }
                        OutlinedButton(onClick = { vm.dispatch(Command.DeclineCommission(c.id)) }) { Text("Decline") }
                    }
                } else Text("Accepted — a matching weapon in storage or on the shelf is delivered at End Day.", style = MaterialTheme.typography.bodySmall)
            }
        }
    }

    SectionTitle("Supplier")
    content.materials.forEach { m ->
        val price = vm.engine.materialPrice(st, m.id)
        val stock = st.supplierStock[m.id]
        Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Sprites.material(m.id)?.let { PixelImage(it, 28.dp, description = null) }
            Column(Modifier.weight(1f)) {
                Text("${m.name} · you have ${st.materials[m.id] ?: 0}")
                Text("$price gold" + (stock?.let { " · $it left today" } ?: " · always stocked"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            OutlinedButton(enabled = (stock == null || stock > 0) && st.gold >= price && !s.busy, onClick = { vm.dispatch(Command.BuyMaterial(m.id, 1)) }) { Text("Buy") }
        }
    }
}

/** One of the eight shelf slots: a weapon on display, or a labelled empty slot. */
@Composable
private fun ShelfSlot(w: Weapon?, number: Int, modifier: Modifier) {
    val description = w?.let { "Slot $number: ${it.name}, ${it.listedPrice ?: 0} gold" } ?: "Slot $number: empty"
    OutlinedCard(
        modifier = modifier.height(84.dp).semantics(mergeDescendants = true) { contentDescription = description },
        border = BorderStroke(1.dp, if (w != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline),
        colors = CardDefaults.outlinedCardColors(containerColor = if (w != null) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.fillMaxWidth().padding(4.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            if (w != null) {
                WeaponSprite(w, size = 40.dp)
                Text("${w.listedPrice ?: 0} g", style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            } else {
                Box(Modifier.height(40.dp), contentAlignment = Alignment.Center) { Text("·", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.outline) }
                Text("Empty", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            }
        }
    }
}

@Composable
private fun ReasonChip(label: String, sold: Boolean) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = if (sold) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
        contentColor = if (sold) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
    ) { Text(label, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) }
}

@Composable
private fun WeaponPriceRow(w: Weapon, initialPrice: Int, vm: GameViewModel, actionLabel: String, onPrice: ((Int) -> Unit)?, onAction: (Int) -> Unit) {
    var priceText by rememberSaveable(w.id.value, w.listedPrice) { mutableStateOf(initialPrice.toString()) }
    val price = priceText.toIntOrNull() ?: 0
    Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                WeaponSprite(w, size = 44.dp)
                Column {
                    Text(w.name, style = MaterialTheme.typography.titleSmall)
                    Text(Labels.weaponSummary(w, vm.engine.content), style = MaterialTheme.typography.bodySmall)
                }
            }
            FlowRow(verticalArrangement = Arrangement.spacedBy(4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    StepButton("−10", "Lower price by 10") { priceText = (price - 10).coerceAtLeast(0).toString() }
                    OutlinedTextField(
                        value = priceText,
                        onValueChange = { priceText = it.filter { c -> c.isDigit() }.take(6) },
                        label = { Text("Price") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.width(104.dp),
                    )
                    StepButton("+10", "Raise price by 10") { priceText = (price + 10).coerceAtMost(999999).toString() }
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.height(56.dp)) {
                    if (onPrice != null) OutlinedButton(onClick = { onPrice(price) }) { Text("Set") }
                    Button(onClick = { onAction(price) }) { Text(actionLabel) }
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
