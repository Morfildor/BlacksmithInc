package com.tinyblacksmith.core.text

/**
 * Hidden random odds in words (owner decision A, 2026-10-14, extended to previews and tooltips the same day). A chance is
 * never shown as a percentage or a count ("3 in 10" states the exact odds too), and no word promises an outcome: the
 * top of each scale still allows a miss. A chance of zero is a rule, not odds, and is said plainly. Wording bands only:
 * they pick a phrase and decide nothing.
 */
object Odds {
    /** How often something happens over many forges, as an adverb: "never", "rarely", "now and then", "often", "more often than not". */
    fun often(chance: Double): String = when {
        chance <= 0.0 -> "never"
        chance < 0.1 -> "rarely"
        chance < 0.25 -> "now and then"
        chance < 0.5 -> "often"
        else -> "more often than not"
    }

    /** The chance of one attempt, as a noun phrase: "no chance", "a slim chance", "a fair chance", "a good chance", "a strong chance". */
    fun chance(chance: Double): String = when {
        chance <= 0.0 -> "no chance"
        chance < 0.2 -> "a slim chance"
        chance < 0.4 -> "a fair chance"
        chance < 0.6 -> "a good chance"
        else -> "a strong chance"
    }
}
