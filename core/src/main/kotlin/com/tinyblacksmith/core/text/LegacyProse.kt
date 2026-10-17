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
        if (emDash !in source && semicolon !in source) return if (source === text) source else closed(source)
        var out = source
        for (sep in listOf(emDash, semicolon)) {
            out = out.split(sep).mapIndexed { i, part -> if (i == 0) part else cap(part.trimStart()) }.joinToString(". ") { it.trimEnd().trimEnd('.') }
        }
        return closed(out.trim())
    }

    private fun closed(out: String): String =
        if (out.isEmpty() || out.last() in ".!?'\"") out else "$out."

    private val wonNew = Regex("""^Emberfall held against (.+?)\. .+ defended the walls\.$""")
    private val wonOld = Regex("""^Emberfall repelled (.+)! Champions: .+\.$""")
    private val lost = Regex("""^(.+?) (?:routed|overran) the defenders \(.*\)\.$""")

    /** The besieger as a siege record's own sentence names it ("the Ashclaw Raiders"), or null when the text is another shape. */
    fun siegeAttacker(text: String): String? {
        val named = wonNew.matchEntire(text)?.groupValues?.get(1) ?: wonOld.matchEntire(text)?.groupValues?.get(1) ?: lost.matchEntire(text)?.groupValues?.get(1)
        return named?.let { if (it.startsWith("The ")) "t" + it.drop(1) else it }
    }

    // ---- stored fight records (terminology overhaul V2, 2026-10-17): effect and ability names and the log's own templates ----

    /** Effects renamed in the overhaul, by the ID a record stores them under: the name a record holds, and today's. */
    private val renamedRules: Map<String, Pair<String, String>> = mapOf(
        "core_iron" to ("Iron holds" to "Iron"), "core_bronze" to ("Bronze rings back" to "Bronze"), "core_silver" to ("Silver holds a charge" to "Silver"),
        "core_obsidian" to ("Obsidian cracks" to "Obsidian"), "core_starsteel" to ("Starsteel finds the Mark" to "Starsteel"), "core_moonsteel" to ("Moonsteel bites the cold" to "Moonsteel"),
        "element_fire" to ("Ember" to "Fire"), "element_frost" to ("Rime" to "Frost"), "element_storm" to ("Arc" to "Storm"),
        "element_grave" to ("Grave chill" to "Grave"), "element_verdant" to ("Sap" to "Verdant"), "element_sun" to ("Noon light" to "Sun"),
        "charge_burst" to ("Charge burst" to "Discharge"), "spear_reach" to ("Reach" to "Armor penetration"), "dagger_flurry" to ("Off-hand cut" to "Follow-up attack"),
        "catalyst_capacitor" to ("Capacitor" to "Conduit"), "catalyst_runed" to ("Rune of answer" to "Rune of Retaliation"),
        "catalyst_spreading" to ("Dragon oil" to "Dragon oil (old forging)"), "catalyst_converting" to ("Void ink" to "Void ink (old forging)"),
        "affix_sentient_home" to ("Sentient (at home)" to "Sentient (siege only)"), "affix_sentient_road" to ("Sentient (on the road)" to "Sentient (quests only)"),
        "ashclaw_rout" to ("Rout" to "Demoralized"), "trait_loyal" to ("Loyal" to "Avenger"),
        // The status Guard is called Armor since the same overhaul.
        "catalyst_converted_guard" to ("Void ink (Guard)" to "Void ink (Armor)"), "sig_dawnbrand_guard" to ("Dawnbrand (Guard)" to "Dawnbrand (Armor)"),
    )

    /** Old names in a log line's own words, by the name: the weapon's rules (always a part of the blade), then the rest. */
    private val oldWeaponRules: Map<String, String> = renamedRules.filterKeys { it.startsWith("core_") || it.startsWith("element_") || it in setOf("spear_reach", "dagger_flurry", "catalyst_capacitor", "catalyst_runed") }
        .values.associate { it }
    private val oldOtherRules: Map<String, String> = mapOf("Charge burst" to "Discharge", "Rout" to "Demoralized", "Loyal" to "Avenger")
    private val oldMoves: Map<String, String> = mapOf(
        "Cleave" to "Heavy swing", "Cut and run" to "Backstab", "Raise the fallen" to "Summon shambler", "Grave weapon" to "Undying blow", "Rend" to "Scale armor", "Shield wall" to "Raise shield",
    )

    /** A rule's name as a fight record stored it ([stored], under [id]), in today's words. Only the exact old name of that very rule changes. */
    fun ruleName(id: String, stored: String): String = renamedRules[id]?.takeIf { it.first == stored }?.second ?: statusWords(stored)

    /** A record's whole rule-name map (`FightResult.rules`) through [ruleName]. */
    fun ruleNames(rules: Map<String, String>): Map<String, String> = rules.mapValues { (id, name) -> ruleName(id, name) }

    private fun alt(names: Collection<String>) = names.sortedByDescending { it.length }.joinToString("|") { Regex.escape(it) }
    private val ownerVerbs = "hits|puts|gives|spends|removes|cancels|stores|deals|uses up|heals"
    private val ownedWeaponRule = Regex("""^([^'.]+)'s (${alt(oldWeaponRules.keys)}) ($ownerVerbs) """)
    private val ownedOtherRule = Regex("""^([^'.]+)'s (${alt(oldOtherRules.keys)}) ($ownerVerbs) """)
    private val gainedFrom = Regex("""^(.+) (gains \d+ [A-Za-z]+|heals \d+) from (${alt(oldWeaponRules.keys + oldOtherRules.keys)})( \(now \d+\))?\.$""")
    private val logTemplates: List<Pair<Regex, (MatchResult) -> String>> = listOf(
        Regex("""^(.+) hits the Mark on (.+) for (\d+) extra damage\.$""") to { m -> "${m.groupValues[1]} uses up the Mark on ${m.groupValues[2]} for ${m.groupValues[3]} extra damage." },
        Regex("""^(.+) hits (.+) for (\d+)\.$""") to { m -> "${m.groupValues[1]} deals ${m.groupValues[3]} damage to ${m.groupValues[2]}." },
        Regex("""^(.+) loses 1 Chill: this attack deals (\d+)% less damage\.$""") to { m -> "${m.groupValues[1]} uses up 1 Chill, so this attack deals ${m.groupValues[2]}% less damage." },
        Regex("""^(.+) strikes the Mark on (.+)\.$""") to { m -> "${m.groupValues[1]} uses up the Mark on ${m.groupValues[2]}." },
        Regex("""^(.+) is slowed by the cold\.$""") to { m -> "${m.groupValues[1]} uses up 1 Chill, so this attack deals less damage." },
        Regex("""^(.+) bleeds for (\d+)\.$""") to { m -> "${m.groupValues[1]} takes ${m.groupValues[2]} damage from Bleed." },
        Regex("""^The party is beaten\.$""") to { _ -> "The party is defeated." },
        Regex("""^(.+): (${alt(oldMoves.keys)})\.$""") to { m -> "${m.groupValues[1]}: ${oldMoves.getValue(m.groupValues[2])}." },
    )

    /** The status word as a whole token ("gains 3 Guard", "Guard breaks", "Void ink (Guard)"), never "Guardian". */
    private val guardWord = Regex("""\bGuard\b(?!ian)""")
    /** The one log sentence that names a weapon: its name is left as it is, whatever it holds. */
    private val cracks = Regex("""^[^']+'s .+ cracks\.$""")

    /**
     * One sentence of a stored fight log (`FightEvent.text`) in today's words: the old effect and ability names where the
     * log names a rule ("Mira's Arc hits", "from Silver holds a charge", "Name: Cleave.") and the old templates, each
     * matched whole, and the status Guard as Armor. Anything else, a weapon's name in "Mira's Ember Sword cracks." among
     * it, is returned as it is.
     */
    fun fightLine(text: String): String {
        if (cracks.matches(text)) return text
        return statusWords(renamed(text))
    }

    private fun statusWords(s: String): String = guardWord.replace(s, "Armor")

    private fun renamed(text: String): String {
        var s = text
        ownedWeaponRule.find(s)?.let { m -> s = "${m.groupValues[1]}'s weapon (${oldWeaponRules.getValue(m.groupValues[2])}) ${m.groupValues[3]} " + s.substring(m.range.last + 1) }
            ?: ownedOtherRule.find(s)?.let { m -> s = "${m.groupValues[1]}'s ${oldOtherRules.getValue(m.groupValues[2])} ${m.groupValues[3]} " + s.substring(m.range.last + 1) }
        gainedFrom.matchEntire(s)?.let { m ->
            val old = m.groupValues[3]
            val from = oldWeaponRules[old]?.let { "the weapon ($it)" } ?: oldOtherRules.getValue(old)
            s = "${m.groupValues[1]} ${m.groupValues[2]} from $from${m.groupValues[4]}."
        }
        return logTemplates.firstNotNullOfOrNull { (re, rewrite) -> re.matchEntire(s)?.let(rewrite) } ?: s
    }

    /** A stored highlight ("Round 3: sentence. sentence.") with each of its sentences through [fightLine]. */
    fun fightHighlight(text: String): String {
        val head = Regex("""^Round \d+: """).find(text)?.value.orEmpty()
        return head + text.removePrefix(head).split(Regex("""(?<=\.) """)).joinToString(" ") { fightLine(it) }
    }

    private val strikesWith = Regex("""^strikes with (.+)$""")
    private val roundHit = Regex("""^(.+ )?hits (.+) for (\d+)$""")

    /**
     * A classic or a guild siege's stored replay note (`CombatRound.note`, read after the attacker's name) in today's
     * words: "strikes with" a weapon, and a guild fight's hit line as its siege replay kept it. The weapon's name is never touched.
     */
    fun roundNote(note: String): String {
        strikesWith.matchEntire(note)?.let { return "attacks with ${it.groupValues[1]}" }
        roundHit.matchEntire(note)?.let { m ->
            val who = m.groupValues[1].trimEnd().let { r -> oldWeaponRules[r]?.let { "weapon ($it) " } ?: oldOtherRules[r]?.let { "$it " } ?: if (r.isEmpty()) "" else "$r " }
            return "${who}deals ${m.groupValues[3]} damage to ${m.groupValues[2]}"
        }
        return note
    }
}
