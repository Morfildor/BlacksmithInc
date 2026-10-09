package com.tinyblacksmith.core.model

/** The one order for serial IDs wherever an order or a tie-break is needed. */
object IdOrder {
    /** By prefix, then by serial number, so `h2` comes before `h10` and `c2` before `c10`; an ID without a number sorts by its text. */
    val numeric: Comparator<String> = compareBy<String> { it.takeWhile { c -> !c.isDigit() } }
        .thenBy { it.dropWhile { c -> !c.isDigit() }.toLongOrNull() ?: Long.MAX_VALUE }
        .thenBy { it }
}
