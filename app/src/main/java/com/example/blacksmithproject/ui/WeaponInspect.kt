package com.example.blacksmithproject.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.blacksmithproject.R
import com.example.blacksmithproject.ui.detail.rarityColor
import com.example.blacksmithproject.ui.detail.weaponCard
import com.example.blacksmithproject.ui.theme.BronzeDeep
import com.example.blacksmithproject.ui.theme.Cream
import com.example.blacksmithproject.ui.theme.CreamMuted
import com.example.blacksmithproject.ui.theme.ForgeSlot
import com.example.blacksmithproject.ui.theme.Gold
import com.example.blacksmithproject.ui.theme.NUMBERS
import com.example.blacksmithproject.ui.theme.Space
import com.tinyblacksmith.core.engine.GameEngine
import com.tinyblacksmith.core.model.GameState

/*
 * V3 inspection for the weapons the shop holds (docs/UI_V2_LEDGER.md M7): the card a shelf or storage row opens, and
 * the row itself. The row is for playing (what it is, what it asks, the one stock action that applies); the card is for
 * understanding (every number, each property with what it does, the fight rules); the weapon's sheet is for studying.
 */

/** The test tag of a stock row's card, and the prefix of its parts ("weapon_inspect_details", "weapon_inspect_close"). */
internal const val WEAPON_CARD = "weapon_inspect"

/**
 * A weapon of the shop as an inspect card: the weapon's own card ([weaponTip] over [weaponCard], the same numbers and
 * words as its sheet), then what the shop holds it at (the asking price on the shelf, the suggested price in storage),
 * how the besieger takes its element, and the classes that favour its type. A weapon kept for an order says so first, as
 * what stands in the way. Built with the Shop's content and without actions: the row adds "Full details".
 */
internal fun GameEngine.stockTip(state: GameState, s: StockUi): TipUi {
    val base = weaponTip(weaponCard(state, s.weapon), config, tag = WEAPON_CARD)
    val fans = fans(s.weapon.familyId)
    return base.copy(
        stats = base.stats + listOfNotNull(
            when { s.price != null -> TipStat("Asking", "${s.price} gold"); s.promised == null -> TipStat("Suggested price", "${s.suggested} gold"); else -> null },
            s.threat?.let { TipStat("Against the siege", it.label, if (it.counters) FactTone.GOOD else FactTone.WARN) },
        ),
        sections = base.sections + TipSection("Suits", if (fans.isEmpty()) emptyList() else listOf(TipLine(fans.joinToString(", ")))),
        warning = s.promised,
    )
}

/** A weapon's card with its one way on: "Full details" opens the weapon's sheet ([onDetails]). */
internal fun TipUi.withDetails(onDetails: () -> Unit): TipUi = copy(actions = listOf(TipAction("Full details", "${WEAPON_CARD}_details", onDetails)))

/** What TalkBack says for a weapon's row: its name, summary and price, then how it stands against the besieger and what sleeps in it. */
internal fun StockUi.spoken(tap: Boolean) =
    "${weapon.name}, $summary, ${price?.let { "$it gold" } ?: "in storage"}.${threat?.let { " ${it.label}." } ?: ""}${dormant?.let { " $it" } ?: ""}${if (tap) " Tap for details and price." else ""}"

/**
 * A bronze hairline under a row: lists are divided by lines and spacing, plates are kept for objects (V3). Used by the
 * Shop, Storage, Supplies, the board and the visitor's row; a candidate for `Parts.kt`.
 */
internal fun Modifier.hairline(): Modifier = drawBehind { drawLine(BronzeDeep, Offset(0f, size.height), Offset(size.width, size.height), strokeWidth = 1.dp.toPx()) }

private val SlotShape = RoundedCornerShape(6.dp)

/**
 * A weapon the shop holds, as one row of the shelf or the storeroom: its sprite in a slot, the name in its rarity's
 * colour, the rarity in words with its power and quality, each buff ("+") and flaw ("−") by name, then at the end the
 * price it asks or "In storage", with the quick "List at" a free stored weapon has ([onList]). What else bears on it
 * stays on the row in a line of its own: its element against the besieger, a sleeping property, the order it is kept
 * for, and [note] (how it came back). A tap opens the weapon's sheet ([onOpen]); the ringed "i" or a long press opens
 * its card, whose "Full details" is [onDetails] (none when null). With [selected] set the row is a checkbox and [onOpen]
 * flips it.
 * Rows are divided by hairlines, not plates: the sprite's slot is the object.
 */
@Composable
internal fun WeaponRow(
    stock: StockUi, onOpen: () -> Unit, modifier: Modifier = Modifier,
    tag: String = "stock_${stock.weapon.id.value}", onDetails: (() -> Unit)? = onOpen,
    busy: Boolean = false, onList: ((Int) -> Unit)? = null, selected: Boolean? = null,
    note: String? = null, divider: Boolean = true, promisedTag: String = "stock_promised_${stock.weapon.id.value}",
) {
    val w = stock.weapon
    val inspector = LocalInspector.current
    val anchor = remember { InspectAnchor() }
    val card: (() -> TipUi)? = stock.tip?.let { t -> { onDetails?.let(t::withDetails) ?: t } }
    // Narrow or large text: the price and the action go under the words instead of squeezing them.
    val stack = LocalDensity.current.fontScale > 1.3f || LocalConfiguration.current.screenWidthDp < 340
    // Only a free weapon in storage can go on the shelf from here: one kept for an order cannot be listed at all.
    @Suppress("NAME_SHADOWING") val onList = onList.takeIf { stock.promised == null && stock.price == null }
    val click = when {
        selected != null -> Modifier.inspectAnchor(anchor).toggleable(selected, enabled = stock.promised == null, role = Role.Checkbox, onValueChange = { onOpen() })
        card != null -> Modifier.inspectable(anchor, inspector, card, onClick = onOpen)
        else -> Modifier.clickable(onClickLabel = "Open ${w.name}", role = Role.Button, onClick = onOpen)
    }
    val trade: @Composable (Modifier) -> Unit = { m ->
        Column(m, horizontalAlignment = if (stack) Alignment.Start else Alignment.End) {
            if (stock.price != null) Text("${stock.price} gold", style = MaterialTheme.typography.titleSmall.copy(fontFeatureSettings = NUMBERS), color = Cream)
            else Text("In storage", style = MaterialTheme.typography.bodySmall, color = CreamMuted)
            if (onList != null) SecondaryActionButton("List at ${stock.suggested}", { onList(stock.suggested) }, Modifier.padding(top = Space.xs), enabled = !busy)
        }
    }
    Row(
        modifier.fillMaxWidth()
            .then(if (divider) Modifier.hairline() else Modifier)
            .then(click).testTag(tag).heightIn(min = 64.dp).padding(vertical = Space.sm)
            .semantics(mergeDescendants = true) { contentDescription = stock.spoken(tap = selected == null) },
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (selected != null) Checkbox(checked = selected, onCheckedChange = null, enabled = stock.promised == null)
        Box(Modifier.clip(SlotShape).background(ForgeSlot).border(1.dp, BronzeDeep, SlotShape).padding(2.dp)) { WeaponSprite(w, size = 44.dp) }
        Column(Modifier.weight(1f)) {
            Text(w.name + (w.title?.let { " · \"$it\"" } ?: ""), style = MaterialTheme.typography.titleSmall, color = rarityColor(w.rarity))
            // The facts as plain text: the row's own sentence ([spoken]) is what TalkBack reads for it.
            Text(factText(listOf(Fact(Labels.rarity(w.rarity))) + stock.stats.filter { it.label == "Power" || it.label == "Quality" }.map { Fact("${it.value}", it.label) }), style = MaterialTheme.typography.bodySmall, color = Cream)
            val traits = stock.buffs.map { Fact(it, tone = FactTone.GOOD) } + stock.flaws.map { Fact(it, tone = FactTone.WARN) }
            if (traits.isNotEmpty()) Text(factText(traits), style = MaterialTheme.typography.bodySmall, color = Cream)
            stock.threat?.let { Text("${it.kind.sign} ${it.label}", style = MaterialTheme.typography.bodySmall, color = it.kind.color) }
            stock.dormant?.let { Text("${EffectKind.NEUTRAL.sign} $it", style = MaterialTheme.typography.bodySmall, color = Gold) }
            stock.promised?.let {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    PixelIcon(R.drawable.icon_promised, 20.dp)
                    Text("${EffectKind.NEUTRAL.sign} $it", style = MaterialTheme.typography.bodySmall, color = Gold, modifier = Modifier.testTag(promisedTag))
                }
            }
            note?.let { Secondary(it) }
            if (stack) trade(Modifier.padding(top = Space.xs))
        }
        if (!stack) trade(Modifier)
        if (card != null) InfoButton(w.name, card, anchor = anchor, tag = "${tag}_info")
    }
}
