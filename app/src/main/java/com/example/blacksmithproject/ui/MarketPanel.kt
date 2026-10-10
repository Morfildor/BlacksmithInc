package com.example.blacksmithproject.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.blacksmithproject.GameViewModel
import com.example.blacksmithproject.UiState
import com.example.blacksmithproject.ui.theme.Space
import com.tinyblacksmith.core.content.MaterialCategory
import com.tinyblacksmith.core.engine.Command

/** The supplier and the workshop tools, as rows at the foot of the Shop list. */
fun LazyListScope.supplierItems(s: UiState.Playing, vm: GameViewModel) {
    val content = vm.engine.content
    val st = s.state
    val side = Modifier.padding(horizontal = Space.md)
    item(key = "supplier") {
        Column(side) {
            SectionTitle("Supplier")
            Secondary("Buy one at a time. Basic metals are always in stock.")
        }
    }
    MaterialCategory.entries.forEach { category ->
        val group = content.materials(category)
        if (group.isEmpty()) return@forEach
        item(key = "supplier_${category.name}") {
            Text(
                when (category) { MaterialCategory.CORE -> "Cores"; MaterialCategory.AUGMENT -> "Augments"; MaterialCategory.CATALYST -> "Catalysts" },
                style = MaterialTheme.typography.titleSmall,
                modifier = side.padding(top = Space.md, bottom = Space.xs),
            )
        }
        items(group, key = { "material_${it.id.value}" }) { m ->
            val price = vm.engine.materialPrice(st, m.id)
            val stock = st.supplierStock[m.id]
            val have = st.materials[m.id] ?: 0
            Row(side.fillMaxWidth().padding(vertical = Space.xs), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
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
    item(key = "tools") {
        Column(side) {
            SectionTitle("Workshop tools")
            Secondary("Bought with gold; they last until the forge falls.")
        }
    }
    items(content.tools, key = { "tool_${it.id}" }) { t ->
        val cost = vm.engine.toolCost(st, t.id)
        Row(side.fillMaxWidth().padding(vertical = Space.xs), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
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
