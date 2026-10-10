package com.tinyblacksmith.core

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The player-facing voice, guarded by a source scan of every string literal in the production code (the simulator's
 * reports and comments aside): no em or en dash, no semicolon between clauses, and no generic "blade" where the
 * weapon is unspecified. A literal that has to break a rule is listed in [allowed] with its reason.
 */
class ProseGuardTest {
    private val roots = listOf("src/main/kotlin", "../app/src/main/java")
    private val literal = Regex(""""((?:[^"\\]|\\.)*)"""")
    private val template = Regex("""\$\{[^}]*\}|\$[A-Za-z_]\w*""")
    /** A `${...}` template expression, with at most one level of quoted text inside it. */
    private val expression = Regex("""\$\{(?:[^{}"]|"[^"]*")*\}""")
    private val blade = Regex("""\bblades?\b""", RegexOption.IGNORE_CASE)

    /** File name and the text of the literal. */
    private val allowed = setOf(
        "LegacyProse.kt" to "; ",                              // the separator the read-only adapter splits on
        "Signatures.kt" to "A small blade with far too much thunder in it.",   // a dagger has a blade
        "EncounterCatalog.kt" to "blades",                    // the key of an amount in a visitor's record, never shown
    )

    private data class Hit(val file: String, val line: Int, val text: String)

    private fun literals(): List<Hit> {
        val hits = mutableListOf<Hit>()
        for (root in roots) {
            val dir = File(root)
            assertTrue(dir.isDirectory, "source root not found: ${dir.absolutePath}")
            dir.walkTopDown().filter { it.isFile && it.extension == "kt" && "/sim/" !in it.invariantSeparatorsPath }.forEach { f ->
                f.readLines().forEachIndexed { i, raw ->
                    val line = raw.trim()
                    if (line.startsWith("//") || line.startsWith("*") || line.startsWith("/*")) return@forEachIndexed
                    var code = raw
                    var k = code.indexOf("//")
                    while (k >= 0 && code.substring(0, k).count { it == '"' } % 2 == 1) k = code.indexOf("//", k + 2)
                    if (k >= 0) code = code.substring(0, k)
                    literal.findAll(code.replace(expression, "<expr>")).forEach { hits += Hit(f.name, i + 1, it.groupValues[1]) }
                }
            }
        }
        return hits
    }

    @Test
    fun noEmDashEnDashOrSemicolonChainInAnyDisplayedString() {
        val all = literals()
        assertTrue(all.size > 2000, "the scan reached the code (${all.size} literals)")
        val dash = all.filter { '—' in it.text || '–' in it.text }
        assertEquals(emptyList(), dash.map { "${it.file}:${it.line} ${it.text.take(80)}" }, "em or en dash in a string")
        val semicolons = all.filter { "; " in it.text && it.file to it.text !in allowed && !it.text.contains("allowed:") }
        assertEquals(emptyList(), semicolons.map { "${it.file}:${it.line} ${it.text.take(80)}" }, "semicolon between clauses in a string")
    }

    @Test
    fun noGenericBladeInDisplayedText() {
        val offenders = literals().filter { h ->
            val words = h.text.replace(template, " ")
            blade.containsMatchIn(words) && h.file to h.text !in allowed
        }
        assertEquals(emptyList(), offenders.map { "${it.file}:${it.line} ${it.text.take(100)}" }, "say weapon unless the weapon has a blade")
    }
}
