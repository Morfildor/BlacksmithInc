package com.tinyblacksmith.core

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * E02: the resolvers hold no gameplay number of their own. A source scan of the six resolver files: every numeric
 * literal in their code (comments and string text aside) is either one of the [neutral] values, an event record's
 * priority, or a line named in [allowed] with its reason. A new literal fails here until it moves to `BalanceConfig`
 * or is listed on purpose.
 */
class ConstantsTest {
    private val root = "src/main/kotlin/com/tinyblacksmith/core/"
    private val resolvers = listOf("heroes/Heroes.kt", "market/Market.kt", "battle/Battle.kt", "battle/Power.kt", "engine/WorldEvents.kt", "legacy/Legacy.kt")

    /** Nothing, one thing, a neutral multiplier, and the top of the health, condition, pressure and percent scales. */
    private val neutral = setOf("0", "1", "0.0", "1.0", "100", "100.0")

    /** A literal that stays in a resolver: the file, the literal, a piece of the line it stands on, and why it is not a balance number. */
    private data class Allowed(val file: String, val literal: String, val onLine: String, val why: String)

    private val allowed = listOf(
        // The middle of a roll, and the arithmetic that spreads a roll around it.
        Allowed("market/Market.kt", "0.5", "evaluate(ctx, hero, ctx.equippedWeapon(hero.id), weapon, 0.5)", "the middle of the noise roll: a judgement without luck"),
        Allowed("market/Market.kt", "0.5", "evaluate(ctx, it, ctx.equippedWeapon(it.id), offer, 0.5)", "the middle of the noise roll"),
        Allowed("market/Market.kt", "0.5", "(noiseRoll - 0.5) * 2", "centres the roll on zero"),
        Allowed("market/Market.kt", "2", "(noiseRoll - 0.5) * 2", "and spreads it over -1..1; the size is utilityNoise"),
        Allowed("battle/Battle.kt", "0.5", "Market.evaluate(ctx, it, ctx.equippedWeapon(it.id), blade, 0.5)", "the middle of the noise roll"),
        Allowed("battle/Battle.kt", "2", "rng.nextDouble() * 2 - 1", "spreads a roll over -1..1; the size is encounterVariance"),
        // The shape of a record, not a rule.
        Allowed("market/Market.kt", "3", "const val MAX_CONSIDERED", "how many weighed blades a visit record keeps"),
        Allowed("legacy/Legacy.kt", "12", "const val STORY_MAX", "how many lines of a blade's story the Legend Board keeps"),
        Allowed("battle/Battle.kt", "5", "if (weapon != null) 5 else 3", "the priority of an event record, chosen by a condition"),
        Allowed("battle/Battle.kt", "3", "if (weapon != null) 5 else 3", "the priority of an event record"),
        // Wording bands: they pick a phrase and decide nothing.
        Allowed("battle/Battle.kt", "0.65", "winProbability >= 0.65", "replay wording"),
        Allowed("battle/Battle.kt", "0.35", "winProbability <= 0.35", "replay wording"),
        Allowed("battle/Battle.kt", "1.15", "SiegeOdds.STRONG", "the forecast's word for the odds"),
        Allowed("battle/Battle.kt", "0.8", "SiegeOdds.OUTMATCHED", "the forecast's word for the odds"),
        Allowed("battle/Battle.kt", "80", "overwhelming threat", "the word for a pressure"),
        Allowed("battle/Battle.kt", "60", "grave threat", "the word for a pressure"),
        Allowed("battle/Battle.kt", "40", "rising threat", "the word for a pressure"),
        Allowed("battle/Battle.kt", "20", "restless", "the word for a pressure"),
        // Text handling.
        Allowed("engine/WorldEvents.kt", "3", "key.substring(3)", "the length of the journal key prefix"),
        Allowed("legacy/Legacy.kt", "10", "prev % 10 == 0 && next % 10 == 0", "wording: forges in 10 or in 100"),
        Allowed("legacy/Legacy.kt", "10", "val scale = if (tens) 10 else 1", "wording: forges in 10 or in 100"),
    )

    /** The world-event table is a catalogue (content): a row's weight, its limit a run and its cooldown are listed there, its effect sizes are in `WorldEventConfig`. */
    private val catalogueRow = Regex("""weight = [\d.]+|maxPerRun = \d+|cooldownDays = \d+|pressureEvent\("\w+", "[^"]+", "\w+", [\d.]+""")

    /** Code only: comments dropped and string text blanked, the code inside `${}` templates kept; line breaks stay where they were. */
    private fun codeOnly(src: String): String {
        val out = StringBuilder()
        val templates = ArrayDeque<Int>()  // brace depth outside each open template expression
        var depth = 0
        var inString = false
        var i = 0
        while (i < src.length) {
            val c = src[i]
            if (inString) {
                when {
                    c == '\\' -> { i += 2; continue }
                    c == '"' -> inString = false
                    c == '$' && src.getOrNull(i + 1) == '{' -> { templates.addLast(depth); depth = 0; inString = false; out.append(' '); i += 2; continue }
                    c == '\n' -> out.append('\n')
                }
                i++
                continue
            }
            when {
                src.startsWith("//", i) -> { while (i < src.length && src[i] != '\n') i++; continue }
                src.startsWith("/*", i) -> {
                    val end = src.indexOf("*/", i + 2).let { if (it < 0) src.length else it + 2 }
                    out.append(src.substring(i, end).filter { it == '\n' })
                    i = end
                    continue
                }
                c == '\'' -> { i += if (src[i + 1] == '\\') 4 else 3; continue }
                c == '"' -> inString = true
                c == '{' -> { depth++; out.append(c) }
                c == '}' -> if (depth == 0 && templates.isNotEmpty()) { depth = templates.removeLast(); inString = true } else { depth--; out.append(c) }
                else -> out.append(c)
            }
            i++
        }
        return out.toString()
    }

    private val number = Regex("""(?<!\w)\d+(?:\.\d+)?(?!\w)""")
    private val eventPriority = Regex("""EventType\.\w+, \d+""")

    /** Every literal the scan does not wave through, as "file:line literal | source line", and the [allowed] entries it used. */
    private fun scan(file: String, source: String): Pair<List<String>, Set<Allowed>> {
        val used = mutableSetOf<Allowed>()
        val original = source.lines()
        val flagged = codeOnly(source).lines().flatMapIndexed { n, code ->
            val line = original[n]
            val stripped = if (file == "engine/WorldEvents.kt") catalogueRow.replace(line, "") else line
            // A catalogue row's numbers are removed from the code by removing the same text from it.
            val scanned = eventPriority.replace(if (stripped == line) code else codeOnly(stripped), "EventType").replace("..", " .. ")
            number.findAll(scanned).map { it.value }.filter { it !in neutral }.mapNotNull { literal ->
                val entry = allowed.firstOrNull { it.file == file && it.literal == literal && it.onLine in line }
                if (entry != null) { used += entry; null } else "$file:${n + 1} $literal | ${line.trim()}"
            }.toList()
        }
        return flagged to used
    }

    @Test
    fun resolversHoldNoUnlistedLiterals() {
        val results = resolvers.map { scan(it, File(root + it).readText().replace("\r\n", "\n")) }
        val flagged = results.flatMap { it.first }
        assertTrue(flagged.isEmpty(), "numbers in a resolver that are neither in BalanceConfig nor listed in this test:\n" + flagged.joinToString("\n"))
        assertEquals(emptyList(), allowed - results.flatMap { it.second }.toSet(), "listed literals that are no longer in the source")
    }

    @Test
    fun theScanSeesAStrayLiteralAndIgnoresTextAndComments() {
        val source = """
            object Sample {
                // a comment with 7 in it
                /** and 8 here */
                fun f(ctx: Ctx) {
                    ctx.reputation += 4
                    ctx.emit(EventType.MILESTONE, 6, "paid 9 gold, ${'$'}{hero.fame + 2} fame", data = mapOf("n" to "5"))
                    val odds = (0.25 + x / 100.0).coerceIn(0.0, 1.0)
                    for (i in 0..11) h1 += 1
                }
            }
        """.trimIndent()
        assertEquals(listOf("4", "2", "0.25", "11"), scan("Sample.kt", source).first.map { it.substringAfter(' ').substringBefore(' ') })
    }
}
