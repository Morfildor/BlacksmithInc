package com.tinyblacksmith.core.text

/**
 * Read-only presentation of prose that was authored and stored before the copy remaster (`EventRecord.text`,
 * `HistoryEntry.text`). Nothing here is ever written back: a save keeps its original bytes and this only changes how
 * they are shown.
 *
 * Known old templates are matched whole and rewritten to the new voice. Anything else keeps its content and gets only
 * its punctuation normalised: an em dash or a semicolon between two clauses becomes a sentence break, the next clause
 * starts with a capital and the text ends with one period. A text with neither separator is returned as it is, so a
 * name, a quoted title or an abbreviation is never touched.
 */
object LegacyProse {
    private val emDash = " \u2014 "
    private val semicolon = "; "

    private fun cap(s: String) = s.replaceFirstChar { it.uppercase() }

    /** Old journal and discovery lines with a structure the new copy keeps: subject, then what the journal says. */
    private val known: List<Pair<Regex, (MatchResult) -> String>> = listOf(
        Regex("""^Journal: (.+?) is now understood \u2014 (.+?)\.?$""") to { m -> "Pairing learned. ${m.groupValues[1]}. ${cap(m.groupValues[2])}." },
        Regex("""^Journal: (.+?) is now understood: (.+?)\.?$""") to { m -> "Pairing learned. ${m.groupValues[1]}. ${cap(m.groupValues[2])}." },
        Regex("""^Journal: (.+?) observed \u2014 (.+?)\.?$""") to { m -> "First notes on ${m.groupValues[1]}. ${cap(m.groupValues[2])}." },
        Regex("""^Journal: (.+?) \u2014 (.+?)\.?$""") to { m -> "Notes on ${m.groupValues[1]}. ${cap(m.groupValues[2])}" },
        Regex("""^EMBERFALL GAZETTE \u2014 DAY (\d+)$""") to { m -> "EMBERFALL GAZETTE | DAY ${m.groupValues[1]}" },
    )

    /** [text] as the player reads it. Pure and idempotent: the result of a new emitter passes through unchanged. */
    fun display(text: String): String {
        val source = known.firstNotNullOfOrNull { (re, rewrite) -> re.matchEntire(text.trim())?.let(rewrite) } ?: text
        if (emDash !in source && semicolon !in source) return source
        var out = source
        for (sep in listOf(emDash, semicolon)) {
            out = out.split(sep).mapIndexed { i, part -> if (i == 0) part else cap(part.trimStart()) }.joinToString(". ") { it.trimEnd().trimEnd('.') }
        }
        out = out.trim()
        return if (out.isEmpty() || out.last() == '.' || out.last() == '!' || out.last() == '?' || out.last() == '\'' || out.last() == '"') out else "$out."
    }

    private val wonNew = Regex("""^Emberfall held against (.+?)\. .+ defended the walls\.$""")
    private val wonOld = Regex("""^Emberfall repelled (.+)! Champions: .+\.$""")
    private val lost = Regex("""^(.+?) (?:routed|overran) the defenders \(.*\)\.$""")

    /** The besieger as a siege record's own sentence names it ("the Ashclaw Raiders"), or null when the text is another shape. */
    fun siegeAttacker(text: String): String? {
        val named = wonNew.matchEntire(text)?.groupValues?.get(1) ?: wonOld.matchEntire(text)?.groupValues?.get(1) ?: lost.matchEntire(text)?.groupValues?.get(1)
        return named?.let { if (it.startsWith("The ")) "t" + it.drop(1) else it }
    }
}
