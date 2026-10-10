package com.example.blacksmithproject.ui.detail

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.blacksmithproject.ui.Secondary
import com.example.blacksmithproject.ui.theme.Cream
import com.example.blacksmithproject.ui.theme.FlawRed
import com.example.blacksmithproject.ui.theme.Space
import com.tinyblacksmith.core.shopday.Demand

/** What the − and + buttons move the price by: a convenience of the screen, not a rule of the game. */
private const val STEP = 10
private const val MAX_PRICE = 999_999

/** How [price] stands against the going rate, which is always named first so the two numbers cannot be mistaken for each other. */
internal fun priceAgainstSuggested(price: Int?, suggested: Int): String = "Suggested price $suggested gold. " + when {
    price == null -> "Enter your own price."
    price == suggested -> "You're asking the suggested price."
    price < suggested -> "You're asking ${suggested - price} gold less."
    else -> "You're asking ${price - suggested} gold more."
}

/** How many of the living heroes could pay [price] today, by the counter's own rule ([funds] is `Demand.funds`). */
internal fun affordLine(funds: List<Int>, price: Int): String = "${Demand.canAfford(funds, price)} of ${funds.size} heroes in town can afford this price."

internal fun shelfFullLine(slots: Int): String = "The shelf is full ($slots of $slots)."

/**
 * Choosing a price: −10, the number (stepped or typed), +10; under it the suggested price against the chosen one, and
 * how many of the living heroes could pay it ([funds]), counted again on every change. [actions] are the buttons that
 * use the price (null while the field is empty). While the field has focus the whole block, buttons included, is kept
 * in view above the keyboard. Tags are "[tag]_price" and "[tag]_afford".
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PriceEditor(
    priceText: String,
    onPriceText: (String) -> Unit,
    suggested: Int,
    funds: List<Int>,
    tag: String,
    modifier: Modifier = Modifier,
    actions: @Composable ColumnScope.(price: Int?) -> Unit,
) {
    val price = priceText.toIntOrNull()
    val focus = LocalFocusManager.current
    var focused by remember { mutableStateOf(false) }
    val block = remember { BringIntoViewRequester() }
    // Asked again as the keyboard rises and whenever the lines under the field wrap differently, and only once that
    // frame is laid out: a request made earlier is answered for the room there was before.
    val keyboard = WindowInsets.ime.getBottom(LocalDensity.current)
    var height by remember { mutableIntStateOf(0) }
    LaunchedEffect(focused, keyboard, height) { if (focused) { withFrameNanos {}; block.bringIntoView() } }
    Column(modifier.fillMaxWidth().bringIntoViewRequester(block).onSizeChanged { height = it.height }) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.sm), modifier = Modifier.fillMaxWidth()) {
            StepButton("−$STEP", "Lower price by $STEP") { onPriceText(((price ?: 0) - STEP).coerceAtLeast(0).toString()) }
            OutlinedTextField(
                value = priceText,
                onValueChange = { onPriceText(it.filter { c -> c.isDigit() }.take(6)) },
                label = { Text("Your price") },
                suffix = { Text("gold") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { focus.clearFocus() }),
                modifier = Modifier.weight(1f).onFocusChanged { focused = it.isFocused }.testTag("${tag}_price"),
            )
            StepButton("+$STEP", "Raise price by $STEP") { onPriceText(((price ?: 0) + STEP).coerceAtMost(MAX_PRICE).toString()) }
        }
        Secondary(priceAgainstSuggested(price, suggested), Modifier.padding(top = Space.sm).testTag("${tag}_suggested"))
        if (price != null) {
            // Red only when nobody can pay, and the sentence says so with or without the colour.
            val nobody = funds.isNotEmpty() && Demand.canAfford(funds, price) == 0
            Text(
                affordLine(funds, price), style = MaterialTheme.typography.bodyMedium, color = if (nobody) FlawRed else Cream,
                modifier = Modifier.padding(top = Space.xs).semantics { liveRegion = LiveRegionMode.Polite }.testTag("${tag}_afford"),
            )
            Secondary("They may still pass. Price is only part of the decision.")
        }
        actions(price)
    }
}

@Composable
private fun StepButton(label: String, description: String, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        contentPadding = PaddingValues(4.dp),
        shape = MaterialTheme.shapes.small,
        modifier = Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp).semantics { contentDescription = description },
    ) { Text(label) }
}
